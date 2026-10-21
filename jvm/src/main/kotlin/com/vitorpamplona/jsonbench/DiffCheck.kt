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

import com.vitorpamplona.jsonbench.parsers.NostrEventReader
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.io.File
import kotlin.random.Random
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

/**
 * Differential check: the hand-written reader against Quartz's Jackson path
 * (`JacksonMapper.fromJson`), on the corpus, hand-written edge cases, truncations and random
 * mutations. Prints every disagreement category with examples.
 *
 *   java -cp "jvm/build/install/jsonbench/lib/(all jars)" com.vitorpamplona.jsonbench.DiffCheckKt .corpus
 */
private sealed class Outcome {
    class Ok(
        val e: QuartzEvent,
    ) : Outcome()

    class Err(
        val t: Throwable,
    ) : Outcome()
}

private fun jackson(s: String): Outcome =
    try {
        Outcome.Ok(JacksonMapper.fromJson(s))
    } catch (t: Throwable) {
        Outcome.Err(t)
    }

private val reader = NostrEventReader()

private fun hand(s: String): Outcome =
    try {
        Outcome.Ok(reader.parse(s))
    } catch (t: Throwable) {
        Outcome.Err(t)
    }

/** Null when equal, else what differs. */
private fun diff(
    a: QuartzEvent,
    b: QuartzEvent,
): String? {
    val d = ArrayList<String>()
    if (a.javaClass != b.javaClass) d += "class ${a.javaClass.simpleName} vs ${b.javaClass.simpleName}"
    if (a.id != b.id) d += "id"
    if (a.pubKey != b.pubKey) d += "pubkey"
    if (a.createdAt != b.createdAt) d += "created_at ${a.createdAt} vs ${b.createdAt}"
    if (a.kind != b.kind) d += "kind ${a.kind} vs ${b.kind}"
    if (!a.tags.contentDeepEquals(b.tags)) d += "tags"
    if (a.content != b.content) d += "content"
    if (a.sig != b.sig) d += "sig"
    return if (d.isEmpty()) null else d.joinToString(", ")
}

private val dump: File? = System.getenv("DIFF_DUMP")?.let { File(it).also { f -> f.writeText("") } }

private class Tally(
    val name: String,
) {
    var total = 0
    val counts = LinkedHashMap<String, Int>()
    val examples = LinkedHashMap<String, MutableList<String>>()

    fun add(
        category: String,
        input: String,
        detail: String = "",
    ) {
        counts[category] = (counts[category] ?: 0) + 1
        if (category != "same event" && category != "both reject") dump?.appendText("[$name] $category\n$input\n\n")
        val ex = examples.getOrPut(category) { ArrayList() }
        if (ex.size < 2) ex += (if (detail.isEmpty()) (if (input.length > 160) input.take(160) + "…" else input) else detail)
    }

    fun check(input: String) {
        total++
        val j = jackson(input)
        val h = hand(input)
        when {
            j is Outcome.Ok && h is Outcome.Ok -> {
                val d = diff(j.e, h.e)
                if (d == null) add("same event", input) else add("DIFFERENT EVENT", input, d)
            }
            j is Outcome.Err && h is Outcome.Err -> add("both reject", input)
            j is Outcome.Ok && h is Outcome.Err -> {
                val msg = h.t.message ?: ""
                val off = Regex("offset (\\d+)").find(msg)?.groupValues?.get(1)?.toInt()
                add("only Jackson accepts: " + msg.replace(Regex(" at offset \\d+"), ""), input, "near: " + around(input, off))
            }
            else -> {
                val t = (j as Outcome.Err).t
                val msg = t.message?.lineSequence()?.first() ?: ""
                val col = (t as? com.fasterxml.jackson.core.JsonProcessingException)?.location?.columnNr
                val key = t.javaClass.simpleName + ": " + msg.replace(Regex("\\(.*?\\)"), "(…)").replace(Regex("'[^']*'"), "'…'").replace(Regex("\\d+"), "N").take(90)
                add("ONLY HAND ACCEPTS: $key", input, "near: " + around(input, col?.minus(1)))
            }
        }
    }

    fun print() {
        println("== $name: $total inputs")
        for ((k, v) in counts) {
            println("  %-44s %8d".format(k, v))
            if (k != "same event" && k != "both reject") examples[k]!!.forEach { println("      e.g. $it") }
        }
    }
}

private fun around(
    s: String,
    at: Int?,
): String {
    if (at == null) return "?"
    val p = at.coerceIn(0, s.length)
    return "\u00ab" + s.substring(maxOf(0, p - 40), p) + "\u2588" + s.substring(p, minOf(s.length, p + 25)) + "\u00bb"
}

private const val EV =
    """{"id":"0a5b8e8f2b4b0a6b1f0b64e5d0e9a8f7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a291","pubkey":"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798","created_at":1700000000,"kind":1,"tags":[["e","5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36","wss://relay.example","root"],["p","79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"]],"content":"hello","sig":"908a15e46fb4d8675bab026fc230a0e3542bfade63da02d542fb78b2a8513fcd0092619a2c8c1221e581946e0191f2af505dfdf8657a414dbca329186f009262"}"""

private fun with(
    field: String,
    rawValue: String,
): String {
    val re = Regex("\"$field\":(\"[^\"]*\"|-?[0-9]+|\\[\\[.*?\\]\\])")
    return re.replaceFirst(EV, Regex.escapeReplacement("\"$field\":$rawValue"))
}

private fun edgeCases(): List<String> {
    val l = ArrayList<String>()
    l += EV
    // Escapes and Unicode in every string field.
    val strings =
        listOf(
            "\"a\\\"b\"", "\"a\\\\b\"", "\"a\\/b\"", "\"\\b\\f\\n\\r\\t\"", "\"caf\\u00e9\"", "\"\\u00E9\\u00e9\"",
            "\"\\uD83D\\uDE00 smile\"", "\"lone \\uD800 high\"", "\"lone \\uDC00 low\"", "\"raw é 日本 😀\"",
            "\"raw\ttab\"", "\"raw\nnewline\"", "\"raw\u0001ctl\"", "\"\"", "\"\\\\\"", "\"\\\"\"", "\"end\\\\\"",
            "\"\\u0000nul\"", "\"x\\u\"", "\"x\\u12\"", "\"x\\uZZZZ\"", "\"x\\q\"", "\"x\\", "\"unterminated",
            "\"a\\u0022b\"", "\"\\u005c\"",
        )
    for (f in listOf("id", "pubkey", "content", "sig")) for (v in strings) l += with(f, v)
    for (v in strings) {
        l += with("tags", "[[$v]]")
        l += with("tags", "[[\"e\",$v,\"x\"]]")
    }
    // Non-string values where strings are expected.
    for (f in listOf("id", "pubkey", "content", "sig")) for (v in listOf("null", "1", "-2.5", "true", "false", "[]", "{}", "[\"a\"]")) l += with(f, v)
    // Numbers.
    for (v in listOf("0", "-1", "1700000000", "1.7e9", "1.7E9", "17e8", "1700000000.9", "-0", "9223372036854775807", "9223372036854775808", "99999999999999999999", "\"1700000000\"", "null", "true", "1e400", "01", "+1", "-", "1.", ".5", "1e", "--1")) {
        l += with("created_at", v)
        l += with("kind", v)
    }
    for (v in listOf("2147483647", "2147483648", "-2147483649", "4294967297")) l += with("kind", v)
    // Tags.
    for (v in listOf("[]", "[[]]", "[[],[]]", "[[\"e\"],[]]", "[[null]]", "[[\"a\",null,\"b\"]]", "[[1,2.5,-3]]", "[[true,false]]", "[[\"a\",[\"b\"]]]", "[[\"a\",{\"b\":1}]]", "[\"a\"]", "[null]", "[1]", "{}", "null", "\"tags\"", "[[\"a\",]]", "[[\"a\"],]", "[[\"a\" \"b\"]]", "[ [ \"a\" , \"b\" ] , [ \"c\" ] ]", "[[\"1e5\",1e5,0.0,-0]]")) l += with("tags", v)
    // Unknown fields, nested, with tricky strings.
    val unknown = listOf("1", "\"x\"", "\"a\\\"]}\"", "{\"a\":[1,{\"b\":\"]}\\\"\"}]}", "[[[]]]", "true", "false", "null", "-1.5e3", "{}", "[]", "\"\\u0069d\"")
    for (u in unknown) {
        l += EV.replaceFirst("{", "{\"extra\":$u,")
        l += EV.replaceFirst("\"kind\"", "\"extra\":$u,\"kind\"")
        l += EV.replace(Regex("}$"), ",\"extra\":$u}")
    }
    // Keys: escaped, near-misses, duplicates, case.
    l += EV.replace("\"id\":", "\"\\u0069d\":")
    l += EV.replace("\"kind\":", "\"k\\u0069nd\":")
    l += EV.replace("\"pubkey\":", "\"pubKey\":")
    l += EV.replace("\"pubkey\":", "\"Pubkey\":")
    l += EV.replace("\"content\":\"hello\"", "\"content\":\"hello\",\"content\":\"second\"")
    l += EV.replace("\"kind\":1", "\"kind\":1,\"kind\":7")
    l += EV.replace("\"id\":", "\"jE\":") // same String.hashCode as "id"
    l += EV.replace("\"sig\":", "\"sih\":")
    l += EV.replace("\"tags\":", "\"tag\":")
    // Missing fields.
    for (f in listOf("id", "pubkey", "created_at", "kind", "tags", "content", "sig")) l += EV.replace(Regex("\"$f\":(\"[^\"]*\"|-?[0-9]+|\\[\\[.*?\\]\\]),?"), "").replace(",}", "}")
    // Whitespace everywhere.
    l += EV.replace(":", " :\n\t ").replace(",", " \r\n, ").replace("{", "{ ").replace("}", " }").replace("[", "[ ").replace("]", " ]")
    l += "  \n$EV\n  "
    // Trailing data, other roots.
    for (t in listOf("x", "}", "]", ",{}", "{\"a\":1}", " garbage", "\u0000")) l += EV + t
    for (r in listOf("", " ", "{}", "[]", "null", "1", "\"x\"", "[$EV]", "{\"pubkey\":\"p\"}", "{\"pubkey\":\"p\",}", "{,\"pubkey\":\"p\"}", "{\"pubkey\" \"p\"}", "{\"pubkey\":\"p\" \"kind\":1}", "{\"pubkey\":\"p\"", "{\"pubkey\":")) l += r
    return l
}

private fun truncations(events: List<String>): List<String> {
    val l = ArrayList<String>()
    for (e in events) for (k in 0 until e.length) l += e.substring(0, k)
    return l
}

private val MUTATION_CHARS = "\"\\{}[],: \n\tau0-9.eE+nlftr\u00e9\uD83D"

private fun mutations(
    events: List<String>,
    perEvent: Int,
    rnd: Random,
): List<String> {
    val l = ArrayList<String>()
    for (e in events) {
        repeat(perEvent) {
            val sb = StringBuilder(e)
            repeat(1 + rnd.nextInt(3)) {
                if (sb.isEmpty()) return@repeat
                val p = rnd.nextInt(sb.length)
                when (rnd.nextInt(4)) {
                    0 -> sb.deleteCharAt(p)
                    1 -> sb.insert(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                    2 -> sb.setCharAt(p, MUTATION_CHARS[rnd.nextInt(MUTATION_CHARS.length)])
                    else -> {
                        val q = rnd.nextInt(sb.length)
                        val c = sb[p]
                        sb.setCharAt(p, sb[q])
                        sb.setCharAt(q, c)
                    }
                }
            }
            l += sb.toString()
        }
    }
    return l
}

fun main(args: Array<String>) {
    val corpus = File(args.firstOrNull() ?: ".corpus")
    val files = corpus.listFiles { f -> f.name.endsWith(".ndjson") }!!.sortedBy { it.name }

    val corpusTally = Tally("corpus (every workload file)")
    for (f in files) f.forEachLine { if (it.isNotEmpty()) corpusTally.check(it) }
    corpusTally.print()

    val edge = Tally("edge cases")
    edgeCases().forEach { edge.check(it) }
    edge.print()

    val all = File(corpus, "all.ndjson").readLines().filter { it.isNotEmpty() }
    val rnd = Random(20261021)
    val sample = List(200) { all[rnd.nextInt(all.size)] }.filter { it.length < 3000 }

    val trunc = Tally("truncations (every prefix of ${sample.size} events)")
    truncations(sample).forEach { trunc.check(it) }
    trunc.print()

    val mut = Tally("random mutations (1-3 edits, 200 per event, 2,000 events)")
    mutations(List(2000) { all[rnd.nextInt(all.size)] }, 200, rnd).forEach { mut.check(it) }
    mut.print()
}
