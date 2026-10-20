/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.jsonbench

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import com.google.monitoring.runtime.instrumentation.ConstructorCallback
import com.google.monitoring.runtime.instrumentation.ConstructorInstrumenter
import com.google.monitoring.runtime.instrumentation.Sampler
import java.io.File

/**
 * The parent's side of allocation counting: where the agent is and how to start the
 * counting JVM. Kept apart from [AllocCount], which links against the agent's classes and
 * so can only be loaded in a JVM that runs with it.
 */
object AllocAgent {
    /** Warm-up passes over the whole workload before the counted ones: class loading and one-time init. */
    const val WARMUP_PASSES = 3

    /** Counted passes; the result is their minimum (see [AllocCount.perEvent]). */
    const val COUNTED_PASSES = 3

    /** JVM flags for the counting child, besides the agent; see [childJvmArgs]. */
    private val DISABLED_INTRINSICS =
        listOf(
            // C2 replaces these JDK methods with inline code that allocates the result itself,
            // so their (instrumented) bytecode would stop running once the caller is compiled.
            "_copyOf",
            "_copyOfRange",
            "_newArray",
            "_clone",
            "_toBytesStringU",
            "_allocateUninitializedArray",
        )

    /** Extra JVM arguments for the counting child. */
    fun childJvmArgs(agentJar: File): List<String> =
        listOf(
            "-javaagent:${agentJar.path}",
            // The agent appends itself to the boot class path, which turns class-data sharing
            // off for the app class path anyway; off entirely, the JVM doesn't warn about it.
            "-Xshare:off",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:DisableIntrinsic=${DISABLED_INTRINSICS.joinToString(",")}",
            // C2 rewrites `new StringBuilder().append(…).toString()` chains into one String
            // allocation of its own, skipping both the counted site and String's constructor.
            "-XX:-OptimizeStringConcat",
        )

    /**
     * Finds the agent jar: `-Djsonbench.allocAgent`, else `agent/` next to the installed
     * `lib/` (where installDist puts it). Null when it isn't there.
     */
    fun agentJar(): File? {
        System.getProperty("jsonbench.allocAgent")?.let { p -> File(p).takeIf { it.isFile }?.let { return it } }
        System.getenv("JSONBENCH_ALLOC_AGENT")?.let { p -> File(p).takeIf { it.isFile }?.let { return it } }
        val codeSource =
            try {
                File(AllocAgent::class.java.protectionDomain.codeSource.location.toURI())
            } catch (_: Exception) {
                return null
            }
        return File(codeSource.parentFile?.parentFile, "agent/java-allocation-instrumenter.jar").takeIf { it.isFile }
    }

    /** True when the agent's recorder is on the boot class path, i.e. the JVM runs with the agent. */
    fun agentLoaded(): Boolean =
        try {
            Class.forName("com.google.monitoring.runtime.instrumentation.AllocationRecorder", false, null)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
}

/**
 * Counts the objects one parse creates (`allocs_per_event`). HotSpot has no allocation
 * counter, so this runs in a child JVM of its own, started by the parent after the timed
 * child with java-allocation-instrumenter as `-javaagent`: the agent rewrites the bytecode
 * of every class (the JDK's included) so each `new`, `newarray`, `anewarray`,
 * `multianewarray`, `clone()` and reflective array creation calls the [Sampler] below.
 * Only allocations on the thread that runs the parse loop, while it runs, are counted.
 *
 * The count is bytecode-level: it sees every allocation the bytecode asks for, before the
 * JIT's escape analysis removes or scalar-replaces any, so it can be higher than what the
 * compiled code actually allocates (`alloc_bytes_per_event`, from the timed run, is the
 * JIT's real number).
 *
 * Strings are counted differently: the agent also rewrites `java.lang.String`'s own
 * constructors, and each String is counted once when constructed instead of at its `new`
 * site. A site inside a class the agent cannot rewrite is invisible to it: hidden classes,
 * i.e. lambda proxies, method-handle stubs, string-concatenation stubs. fastjson2 builds
 * every String that way (a LambdaMetafactory function over the JDK's package-private
 * `String(byte[], byte)` constructor), so a site count would miss nearly all of its
 * Strings. Other objects allocated in hidden classes (a capturing lambda's instance, say)
 * and allocations made by native code are still not seen.
 *
 * Only this object references the agent's API, and only the counting child loads it.
 */
object AllocCount {
    /** Counts allocations made on [target] while it is set. Allocates nothing itself. */
    private object Counter : Sampler {
        @Volatile
        @JvmField
        var target: Thread? = null

        @JvmField
        var count: Long = 0

        /** -Djsonbench.allocClasses=true: also count by class, printed to stderr (debugging only). */
        @JvmField
        val byClass: HashMap<String, Long>? = if (java.lang.Boolean.getBoolean("jsonbench.allocClasses")) HashMap() else null

        override fun sampleAllocation(
            count: Int,
            desc: String?,
            newObj: Any?,
            size: Long,
        ) {
            if (Thread.currentThread() !== target) return
            // The agent derives a reflectively created array's descriptor with
            // String.replace just before calling us: that String is the agent's, not the
            // parser's, so take back the count StringCounter gave it.
            if (desc != null && desc === StringCounter.last) {
                this.count--
                StringCounter.last = null
                byClass?.let { add(it, "java/lang/String (constructed)", -1) }
            }
            // Strings are counted by StringCounter instead; the iterator is the one the
            // agent's ConstructorInstrumenter allocates to call StringCounter.
            if (count < 0 && (desc == STRING || desc == AGENT_ITERATOR)) return
            this.count++
            byClass?.let { add(it, if (count >= 0) "$desc[]" else desc ?: "?") }
        }

        /** Debugging only; the boxing and the key's String are counted when called from StringCounter. */
        fun add(
            map: HashMap<String, Long>,
            key: String,
            n: Long = 1,
        ) {
            map[key] = (map[key] ?: 0L) + n
        }

        private const val STRING = "java/lang/String"
        private const val AGENT_ITERATOR = "java/util/concurrent/CopyOnWriteArrayList\$COWIterator"
    }

    /** Counts Strings constructed on [Counter.target], from inside String's constructors. */
    private object StringCounter : ConstructorCallback<String> {
        /** The String last counted on the target thread (see [Counter.sampleAllocation]). */
        @JvmField
        var last: String? = null

        override fun sample(newObj: String) {
            if (Thread.currentThread() === Counter.target) {
                last = newObj
                Counter.count++
                Counter.byClass?.let { Counter.add(it, "java/lang/String (constructed)") }
            }
        }
    }

    private var installed = false

    /**
     * Runs [parser]'s timed loop over [inputs] [AllocAgent.WARMUP_PASSES] times, then
     * [AllocAgent.COUNTED_PASSES] times while counting, and returns the fewest objects one
     * counted pass created, per event. A pass can only gain spurious allocations, never
     * lose real ones: when the JIT hits a not-yet-loaded class (an uncommon trap) the parse
     * thread loads it mid-pass, and the class loader and the agent's own rewriting of that
     * class allocate on that thread.
     * Throws when the agent isn't loaded.
     */
    fun <I : Any> perEvent(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Double {
        if (!installed) {
            AllocationRecorder.addSampler(Counter)
            ConstructorInstrumenter.instrumentClass(String::class.java, StringCounter)
            installed = true
        }
        var sink = 0L
        repeat(AllocAgent.WARMUP_PASSES) { sink += parser.parseAll(inputs) }

        val passes = LongArray(AllocAgent.COUNTED_PASSES)
        var fewest: Map<String, Long>? = null
        for (p in passes.indices) {
            Counter.count = 0
            Counter.byClass?.clear()
            Counter.target = Thread.currentThread()
            sink += parser.parseAll(inputs)
            Counter.target = null
            StringCounter.last = null
            passes[p] = Counter.count
            if (Counter.byClass != null && passes[p] == passes.take(p + 1).min()) fewest = HashMap(Counter.byClass)
        }
        Runner.blackhole += sink

        val counted = passes.min()
        if (passes.any { it != counted }) {
            System.err.println("    counted passes differ (${passes.joinToString()} objects); taking the fewest")
        }
        fewest?.entries?.sortedByDescending { it.value }?.take(25)?.forEach {
            System.err.println(String.format(java.util.Locale.ROOT, "    %10.3f /ev  %s", it.value / inputs.size.toDouble(), it.key))
        }
        return counted / inputs.size.toDouble()
    }
}
