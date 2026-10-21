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

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.lang.ref.Reference
import javax.management.ObjectName

/**
 * One parser under test. Every concrete parser is its own final class, so the hot loop in
 * [parseAll] calls exactly one library entry point (monomorphic for the JIT), and each one
 * runs in a child JVM of its own (see Main.kt).
 *
 * @param I the input form the parser consumes (HARNESS.md "Input forms").
 */
abstract class BenchParser<I : Any> {
    /** Converts the raw lines into this parser's input form. Untimed. */
    abstract fun prepareAll(lines: List<ByteArray>): Array<I>

    /**
     * Builds one fresh frame from a line's UTF-8 bytes, as the WebSocket client hands it to
     * the app (HARNESS.md "Arrival"). Untimed; the timed rounds call it before every burst.
     */
    abstract fun frame(line: ByteArray): I

    /**
     * Parses one input and returns it as an [Event], for the digest. Untimed. Returns null
     * for baselines, which are not parsers.
     */
    abstract fun decode(input: I): Event?

    /**
     * Parses one input into the object an app would keep (the parser's own result, e.g.
     * Quartz's Event subclass), for the retained-memory measurement. Untimed. Defaults to
     * [decode]; null for baselines.
     */
    open fun keep(input: I): Any? = decode(input)

    /**
     * The timed loop: parses every input once, in order, and folds something from every
     * result into the returned sink so the JIT cannot drop the work.
     */
    abstract fun parseAll(inputs: Array<I>): Long
}

/**
 * Every parser starts from a [String]: that is what a WebSocket text frame is on the JVM
 * (OkHttp's `onMessage(webSocket, text: String)`), so whatever a parser needs to turn the
 * String into its own input is part of the parse and runs inside the timed loop.
 */
abstract class StringInputParser : BenchParser<String>() {
    override fun prepareAll(lines: List<ByteArray>): Array<String> = Array(lines.size) { frame(lines[it]) }

    /** What OkHttp's `onMessage(webSocket, text)` hands the app: a just-decoded String. */
    final override fun frame(line: ByteArray): String = String(line, Charsets.UTF_8)
}

/**
 * A parser that reads UTF-8 bytes. It is handed the frame's String like everyone else and
 * encodes it with [utf8] inside its timed loop, the adaptation an app on OkHttp would make.
 */
abstract class BytesInputParser : StringInputParser() {
    /** Parses one already-encoded input and returns it as an [Event], for the digest. */
    abstract fun decodeBytes(input: ByteArray): Event?

    final override fun decode(input: String): Event? = decodeBytes(utf8(input))

    protected fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)
}

/** A line encoded into a buffer with [PADDING] spare bytes after it; [len] is the JSON length. */
class Padded(
    val buf: ByteArray,
    val len: Int,
)

/** A SIMD parser that needs [PADDING] readable bytes past the JSON: encodes, then pads. */
abstract class PaddedInputParser : StringInputParser() {
    abstract fun decodePadded(input: Padded): Event?

    final override fun decode(input: String): Event? = decodePadded(padded(input))

    protected fun padded(s: String): Padded {
        val raw = s.toByteArray(Charsets.UTF_8)
        return Padded(raw.copyOf(raw.size + PADDING), raw.size)
    }

    companion object {
        const val PADDING = 64
    }
}

/** Raw UTF-8 lines, for the one baseline that measures what decoding a frame into a String costs. */
abstract class RawBytesInputParser : BenchParser<ByteArray>() {
    override fun prepareAll(lines: List<ByteArray>): Array<ByteArray> = Array(lines.size) { frame(lines[it]) }

    /** A fresh copy of the frame's bytes, as a socket read produces. */
    final override fun frame(line: ByteArray): ByteArray = line.copyOf()
}

class Settings(
    val warmupMs: Long,
    val minTimeMs: Long,
    val minRounds: Int,
    /** Frames built (untimed) before each timed burst (HARNESS.md "Arrival"). */
    val burst: Int,
) {
    companion object {
        const val MAX_ROUNDS = 10_000
    }
}

class Verification(
    val digest: String?,
    val errors: Int,
    val firstError: String?,
)

/**
 * The retained-heap pass: [bytes] still in use for all kept results, and the live objects
 * they add per class ([objects], JVM class names as the histogram prints them; null when
 * the histogram could not be taken).
 */
class Retained(
    val bytes: Long,
    val objects: Map<String, Long>?,
) {
    val totalObjects: Long? get() = objects?.values?.sum()

    /** The [n] classes that add the most live instances, per event, with readable names. */
    fun topClasses(
        events: Int,
        n: Int = 5,
    ): List<Map<String, Any?>>? =
        objects
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(n)
            ?.map { linkedMapOf("class" to javaName(it.key), "per_event" to it.value / events.toDouble()) }

    companion object {
        /** `[B` -> `byte[]`, `[[Ljava.lang.String;` -> `java.lang.String[][]`. */
        fun javaName(jvm: String): String {
            var dims = 0
            while (dims < jvm.length && jvm[dims] == '[') dims++
            if (dims == 0) return jvm
            val element =
                when (val rest = jvm.substring(dims)) {
                    "B" -> "byte"
                    "C" -> "char"
                    "S" -> "short"
                    "I" -> "int"
                    "J" -> "long"
                    "F" -> "float"
                    "D" -> "double"
                    "Z" -> "boolean"
                    else -> if (rest.startsWith("L") && rest.endsWith(";")) rest.substring(1, rest.length - 1) else rest
                }
            return element + "[]".repeat(dims)
        }
    }
}

class Measurement(
    val rounds: Int,
    val medianNs: Long,
    val minNs: Long,
    val p90Ns: Long,
    val allocBytes: Long?,
    val gcCount: Long?,
    val gcMs: Long?,
)

object Runner {
    /** Keeps every round's sink alive; printed at the end of the child so nothing is dead code. */
    @JvmStatic
    @Volatile
    var blackhole: Long = 0

    fun <I : Any> verify(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Verification {
        val digest = EventDigest()
        var errors = 0
        var firstError: String? = null
        var baseline = false
        for ((index, input) in inputs.withIndex()) {
            try {
                val event = parser.decode(input)
                if (event == null) {
                    baseline = true
                    break
                }
                digest.update(event)
            } catch (t: Throwable) {
                errors++
                if (firstError == null) firstError = "event #$index: ${t.javaClass.name}: ${t.message?.take(300)}"
            }
        }
        return Verification(if (baseline) null else digest.hex(), errors, firstError)
    }

    /**
     * Heap still in use after parsing every input of the workload and keeping all results
     * alive, as a client keeps the events it shows (HARNESS.md "retained"). This is where
     * de-duplication such as String.intern() pays off: equal pubkeys, ids and tag values
     * are then one object instead of one per event. Median of three runs, each measured
     * between full collections; the array holding the results is not counted. Then one
     * more keep pass counts the live objects (see [retainedObjects]).
     */
    fun <I : Any> retained(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Retained? {
        if (parser.keep(inputs[0]) == null) return null
        val runs = LongArray(3)
        for (r in runs.indices) {
            val kept = arrayOfNulls<Any>(inputs.size)
            fullGc()
            val before = usedHeap()
            for (i in inputs.indices) kept[i] = parser.keep(inputs[i])
            fullGc()
            val after = usedHeap()
            Reference.reachabilityFence(kept)
            runs[r] = after - before
        }
        runs.sort()
        val objects =
            try {
                retainedObjects(parser, inputs)
            } catch (t: Throwable) {
                System.err.println("    retained objects not counted: ${t.javaClass.name}: ${t.message?.take(300)}")
                null
            }
        return Retained(runs[1], objects)
    }

    /**
     * Live objects the kept results add, by class: a class histogram (`GC.class_histogram`,
     * which runs a full collection and counts live instances only) before and after keeping
     * every result. The array holding the results exists before the first histogram, so it
     * is not counted. A calibration pair of histograms with nothing kept in between is
     * subtracted class by class, which removes what taking a histogram leaves behind (the
     * first histogram's own text, MBean bookkeeping).
     */
    private fun <I : Any> retainedObjects(
        parser: BenchParser<I>,
        inputs: Array<I>,
    ): Map<String, Long> {
        classHistogram() // first use builds the platform MBean server; keep that out of both pairs

        val idle = arrayOfNulls<Any>(inputs.size)
        val idleBefore = classHistogram()
        val idleAfter = classHistogram()
        Reference.reachabilityFence(idle)

        val kept = arrayOfNulls<Any>(inputs.size)
        val keptBefore = classHistogram()
        for (i in inputs.indices) kept[i] = parser.keep(inputs[i])
        val keptAfter = classHistogram()
        Reference.reachabilityFence(kept)

        val calibration = histogramDelta(parseHistogram(idleBefore), parseHistogram(idleAfter))
        val delta = histogramDelta(parseHistogram(keptBefore), parseHistogram(keptAfter))
        val out = HashMap<String, Long>()
        for (cls in delta.keys + calibration.keys) {
            val d = (delta[cls] ?: 0) - (calibration[cls] ?: 0)
            if (d != 0L) out[cls] = d
        }
        return out
    }

    private val diagnosticCommand = ObjectName("com.sun.management:type=DiagnosticCommand")

    /** `jcmd <pid> GC.class_histogram` from inside: full GC, then live instances per class, as text. */
    private fun classHistogram(): String =
        ManagementFactory.getPlatformMBeanServer().invoke(
            diagnosticCommand,
            "gcClassHistogram",
            arrayOf<Any>(arrayOf<String>()),
            arrayOf(Array<String>::class.java.name),
        ) as String

    // "   1:         12345        6789012  [B (java.base@21.0.1)"
    private val histogramRow = Regex("""^\s*\d+:\s+(\d+)\s+\d+\s+(\S+)""")

    private fun parseHistogram(text: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (line in text.lineSequence()) {
            val m = histogramRow.find(line) ?: continue
            val cls = m.groupValues[2]
            out[cls] = (out[cls] ?: 0) + m.groupValues[1].toLong()
        }
        return out
    }

    private fun histogramDelta(
        before: Map<String, Long>,
        after: Map<String, Long>,
    ): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (cls in before.keys + after.keys) out[cls] = (after[cls] ?: 0) - (before[cls] ?: 0)
        return out
    }

    private fun usedHeap(): Long = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used

    /** Collects until the used heap stops shrinking, so soft garbage doesn't count. */
    private fun fullGc() {
        var last = Long.MAX_VALUE
        repeat(10) {
            System.gc()
            val used = usedHeap()
            if (used >= last) return
            last = used
        }
    }

    /**
     * The workload as it arrives (HARNESS.md "Arrival"): each round walks the lines in bursts
     * of [Settings.burst] frames. Per burst, untimed, [BenchParser.frame] builds fresh frames
     * from the lines' UTF-8 bytes into a reused buffer, which drops the previous burst's
     * frames as garbage; then the burst's parses are timed. Allocated bytes are read around
     * each timed part only, so the frames themselves are never counted.
     */
    private class Arrival<I : Any>(
        val parser: BenchParser<I>,
        val lines: List<ByteArray>,
        val burst: Int,
        val threads: ThreadMXBean?,
    ) {
        private val n = lines.size
        private val fullSize = minOf(burst, n)
        private val tailSize = n % burst
        private val full: Array<I> = parser.prepareAll(lines.subList(0, fullSize))
        private val tail: Array<I> = if (tailSize == 0 || tailSize == fullSize) full else parser.prepareAll(lines.subList(n - tailSize, n))

        var sink = 0L

        /** Bytes the timed parses allocated so far; -1 when the JVM can't tell. */
        var allocBytes = 0L

        /** Runs one round; returns its time, the sum of its bursts' timed parts. */
        fun round(): Long {
            var ns = 0L
            var i = 0
            while (i < n) {
                val k = minOf(burst, n - i)
                val frames = if (k == fullSize) full else tail
                for (j in 0 until k) frames[j] = parser.frame(lines[i + j])
                val a0 = threads?.currentThreadAllocatedBytes ?: -1
                val t0 = System.nanoTime()
                sink += parser.parseAll(frames)
                val t1 = System.nanoTime()
                val a1 = threads?.currentThreadAllocatedBytes ?: -1
                ns += t1 - t0
                if (allocBytes >= 0) allocBytes = if (a0 >= 0 && a1 >= 0) allocBytes + (a1 - a0) else -1
                i += k
            }
            return ns
        }
    }

    fun <I : Any> measure(
        parser: BenchParser<I>,
        lines: List<ByteArray>,
        inputs: Array<I>,
        settings: Settings,
    ): Measurement {
        val threads = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf { it.isThreadAllocatedMemoryEnabled }
        val arrival = Arrival(parser, lines, settings.burst, threads)

        // Warm-up: the same burst rounds, for at least warmupMs (and at least one round).
        val warmupEnd = System.nanoTime() + settings.warmupMs * 1_000_000
        do {
            arrival.round()
        } while (System.nanoTime() < warmupEnd)

        // Start the measurement from a collected heap, JMH-style.
        System.gc()

        val times = LongArray(Settings.MAX_ROUNDS)
        val minTimeNs = settings.minTimeMs * 1_000_000
        arrival.allocBytes = 0

        var rounds = 0
        val start = System.nanoTime()
        while (rounds < Settings.MAX_ROUNDS) {
            times[rounds++] = arrival.round()
            if (rounds >= settings.minRounds && System.nanoTime() - start >= minTimeNs) break
        }

        val allocBytes = arrival.allocBytes
        blackhole += arrival.sink

        // GC covers the parses only (HARNESS.md "Arrival"): a separate untimed pass of as many
        // rounds as were measured, as a tight loop over the preloaded Strings, so the frame
        // garbage of the timed rounds never shows up in gc_count / gc_ms.
        System.gc()
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val gcCountBefore = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) }
        val gcTimeBefore = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
        var gcSink = 0L
        repeat(rounds) { gcSink += parser.parseAll(inputs) }
        val gcCountAfter = gcBeans.sumOf { it.collectionCount.coerceAtLeast(0) }
        val gcTimeAfter = gcBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
        blackhole += gcSink

        val sorted = times.copyOf(rounds).also { it.sort() }
        val median =
            if (rounds % 2 == 1) {
                sorted[rounds / 2]
            } else {
                (sorted[rounds / 2 - 1] + sorted[rounds / 2]) / 2
            }
        val p90Index = (Math.ceil(0.9 * rounds).toInt() - 1).coerceIn(0, rounds - 1)

        return Measurement(
            rounds = rounds,
            medianNs = median,
            minNs = sorted[0],
            p90Ns = sorted[p90Index],
            allocBytes = if (threads != null && allocBytes >= 0) allocBytes else null,
            gcCount = if (gcBeans.isEmpty()) null else gcCountAfter - gcCountBefore,
            gcMs = if (gcBeans.isEmpty()) null else gcTimeAfter - gcTimeBefore,
        )
    }
}
