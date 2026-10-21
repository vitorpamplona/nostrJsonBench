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
package com.vitorpamplona.jsonbench.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

/**
 * PROTOTYPE: a hand-written reader for one Nostr event, straight off the frame's `String`.
 * No tokens, no field-name strings: it dispatches on the key's length and characters, takes
 * a substring for every value without escapes, and only builds through a StringBuilder when
 * a value has one. Unknown fields are skipped.
 */
class NostrEventCharReader {
    private var s = CharArray(4096)
    private var i = 0
    private var n = 0
    private val sb = StringBuilder(128)
    private var tagScratch = arrayOfNulls<String>(8)
    private var tagsScratch = arrayOfNulls<Array<String>>(16)

    fun parse(json: String): QuartzEvent {
        n = json.length
        if (s.size < n) s = CharArray(maxOf(n, s.size * 2))
        json.toCharArray(s, 0, 0, n)
        i = 0
        return readEvent()
    }

    private fun startsWith(
        word: String,
        at: Int,
    ): Boolean {
        if (at + word.length > n) return false
        for (k in word.indices) if (s[at + k] != word[k]) return false
        return true
    }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at offset $i")

    private fun ws() {
        while (i < n) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++ else return
        }
    }

    private fun expect(c: Char) {
        ws()
        if (i >= n || s[i] != c) fail("expected '$c'")
        i++
    }

    private fun readEvent(): QuartzEvent {
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tags = EMPTY_TAGS
        var content = ""
        var sig = ""

        expect('{')
        ws()
        if (i < n && s[i] == '}') {
            i++
        } else {
            while (true) {
                ws()
                if (i >= n || s[i] != '"') fail("expected a field name")
                i++
                val field = readKey()
                expect(':')
                ws()
                if (i >= n) fail("unexpected end of input")
                when (field) {
                    F_ID -> id = readString()
                    F_PUBKEY -> pubKey = readString()
                    F_CREATED_AT -> createdAt = readLong()
                    F_KIND -> kind = readLong().toInt()
                    F_TAGS -> tags = readTags()
                    F_CONTENT -> content = readString()
                    F_SIG -> sig = readString()
                    else -> skipValue()
                }
                ws()
                if (i >= n) fail("unexpected end of input inside an object")
                val c = s[i++]
                if (c == ',') continue
                if (c == '}') break
                fail("expected ',' or '}'")
            }
        }

        if (pubKey.isEmpty()) throw IllegalArgumentException("Event not found")
        return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig)
    }

    /** Reads a field name (i is just past its opening quote) and returns which field it is. */
    private fun readKey(): Int {
        val start = i
        var j = i
        while (j < n) {
            val c = s[j]
            if (c == '"' || c == '\\') break
            j++
        }
        if (j >= n) fail("unexpected end of input in a field name")
        if (s[j] == '\\') return fieldOf(readStringBody())
        i = j + 1
        return when (j - start) {
            2 -> if (s[start] == 'i' && s[start + 1] == 'd') F_ID else F_OTHER
            3 -> if (startsWith("sig", start)) F_SIG else F_OTHER
            4 -> if (startsWith("kind", start)) F_KIND else if (startsWith("tags", start)) F_TAGS else F_OTHER
            6 -> if (startsWith("pubkey", start)) F_PUBKEY else F_OTHER
            7 -> if (startsWith("content", start)) F_CONTENT else F_OTHER
            10 -> if (startsWith("created_at", start)) F_CREATED_AT else F_OTHER
            else -> F_OTHER
        }
    }

    private fun fieldOf(key: String): Int =
        when (key) {
            "id" -> F_ID
            "pubkey" -> F_PUBKEY
            "created_at" -> F_CREATED_AT
            "kind" -> F_KIND
            "tags" -> F_TAGS
            "content" -> F_CONTENT
            "sig" -> F_SIG
            else -> F_OTHER
        }

    private fun readString(): String {
        if (s[i] != '"') fail("expected a string")
        i++
        return readStringBody()
    }

    /** i is just past the opening quote; returns the decoded value and leaves i past the closing one. */
    private fun readStringBody(): String {
        val start = i
        var j = i
        while (j < n) {
            val c = s[j]
            if (c == '"') {
                i = j + 1
                return String(s, start, j - start)
            }
            if (c == '\\') break
            j++
        }
        if (j >= n) fail("unexpected end of input in a string")
        sb.setLength(0)
        sb.append(s, start, j - start)
        i = j
        while (true) {
            if (i >= n) fail("unexpected end of input in a string")
            val c = s[i]
            if (c == '"') {
                i++
                return sb.toString()
            }
            if (c == '\\') {
                if (i + 1 >= n) fail("unexpected end of input in an escape")
                when (s[i + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 6 > n) fail("unexpected end of input in a \\u escape")
                        val v = (hex(s[i + 2]) shl 12) or (hex(s[i + 3]) shl 8) or (hex(s[i + 4]) shl 4) or hex(s[i + 5])
                        sb.append(v.toChar())
                        i += 4
                    }
                    else -> fail("bad escape")
                }
                i += 2
            } else {
                var k = i + 1
                while (k < n) {
                    val d = s[k]
                    if (d == '"' || d == '\\') break
                    k++
                }
                sb.append(s, i, k - i)
                i = k
            }
        }
    }

    private fun hex(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> fail("bad hex digit in a \\u escape")
        }

    private fun readLong(): Long {
        val start = i
        var neg = false
        if (s[i] == '-') {
            neg = true
            i++
        }
        var v = 0L
        var digits = 0
        while (i < n) {
            val c = s[i]
            if (c < '0' || c > '9') break
            v = v * 10 + (c - '0')
            i++
            digits++
        }
        if (digits == 0) fail("expected a number")
        if (i < n && (s[i] == '.' || s[i] == 'e' || s[i] == 'E')) {
            // Rare: a fraction or exponent. Coerce like Jackson's longValue does.
            while (i < n && s[i] in "0123456789.eE+-") i++
            return String(s, start, i - start).toDouble().toLong()
        }
        if (digits > 18) return String(s, start, i - start).toLong()
        return if (neg) -v else v
    }

    private fun readTags(): Array<Array<String>> {
        if (s[i] != '[') fail("expected the tags array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAGS
        }
        var outer = tagsScratch
        var count = 0
        while (true) {
            ws()
            val tag = readTag()
            if (count == outer.size) {
                outer = outer.copyOf(count * 2)
                tagsScratch = outer
            }
            outer[count++] = tag
            ws()
            if (i >= n) fail("unexpected end of input in tags")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in tags")
        }
        val result = arrayOfNulls<Array<String>>(count)
        System.arraycopy(outer, 0, result, 0, count)
        java.util.Arrays.fill(outer, 0, count, null)
        @Suppress("UNCHECKED_CAST")
        return result as Array<Array<String>>
    }

    private fun readTag(): Array<String> {
        if (i >= n || s[i] != '[') fail("expected a tag array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAG
        }
        var buf = tagScratch
        var count = 0
        while (true) {
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            val v =
                when (s[i]) {
                    '"' -> readString()
                    'n' -> literal("null", "")
                    't' -> literal("true", "true")
                    'f' -> literal("false", "false")
                    else -> numberText()
                }
            if (count == buf.size) {
                buf = buf.copyOf(count * 2)
                tagScratch = buf
            }
            buf[count++] = v
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in a tag")
        }
        val result = arrayOfNulls<String>(count)
        System.arraycopy(buf, 0, result, 0, count)
        java.util.Arrays.fill(buf, 0, count, null)
        @Suppress("UNCHECKED_CAST")
        return result as Array<String>
    }

    private fun literal(
        word: String,
        value: String,
    ): String {
        if (!startsWith(word, i)) fail("expected $word")
        i += word.length
        return value
    }

    private fun numberText(): String {
        val start = i
        while (i < n && s[i] in "0123456789.eE+-") i++
        if (i == start) fail("unexpected character in a tag")
        return String(s, start, i - start)
    }

    private fun skipValue() {
        when (s[i]) {
            '"' -> skipString()
            '{', '[' -> skipContainer()
            't' -> literal("true", "")
            'f' -> literal("false", "")
            'n' -> literal("null", "")
            else -> numberText()
        }
    }

    private fun skipString() {
        i++
        while (i < n) {
            val c = s[i]
            if (c == '"') {
                i++
                return
            }
            i += if (c == '\\') 2 else 1
        }
        fail("unexpected end of input in a string")
    }

    private fun skipContainer() {
        var depth = 0
        while (i < n) {
            when (s[i]) {
                '"' -> {
                    skipString()
                    continue
                }
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) {
                        i++
                        return
                    }
                }
            }
            i++
        }
        fail("unexpected end of input in a value")
    }

    companion object {
        private const val F_OTHER = 0
        private const val F_ID = 1
        private const val F_PUBKEY = 2
        private const val F_CREATED_AT = 3
        private const val F_KIND = 4
        private const val F_TAGS = 5
        private const val F_CONTENT = 6
        private const val F_SIG = 7
        private val EMPTY_TAGS = emptyArray<Array<String>>()
        private val EMPTY_TAG = emptyArray<String>()
    }
}

class QuartzHandChars : StringInputParser() {
    private val reader = NostrEventCharReader()

    override fun decode(input: String): Event = reader.parse(input).let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = reader.parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = reader.parse(s)
            sink += (e.kind + e.tags.size + e.content.length).toLong()
        }
        return sink
    }
}

/**
 * PROTOTYPE v3: the hand-written reader working on the frame `String` itself. Every string
 * value's end is found with `String.indexOf('"')`, an intrinsic on HotSpot and ART, and the
 * reader tracks the next backslash in the frame (also found with indexOf) so a value without
 * escapes is one `substring`. Structural characters are read with `charAt`.
 */
class NostrEventReader {
    private var s = ""
    private var i = 0
    private var n = 0

    /** Position of the next backslash at or after [i], or [n] when there is none. */
    private var nextEscape = 0
    private val sb = StringBuilder(128)
    private var tagScratch = arrayOfNulls<String>(8)
    private var tagsScratch = arrayOfNulls<Array<String>>(16)

    fun parse(json: String): QuartzEvent {
        s = json
        n = json.length
        i = 0
        nextEscape = json.indexOf('\\').let { if (it < 0) n else it }
        try {
            return readEvent()
        } finally {
            s = ""
        }
    }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at offset $i")

    private fun ws() {
        while (i < n) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++ else return
        }
    }

    private fun expect(c: Char) {
        ws()
        if (i >= n || s[i] != c) fail("expected '$c'")
        i++
    }

    private fun readEvent(): QuartzEvent {
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tags = EMPTY_TAGS
        var content = ""
        var sig = ""

        expect('{')
        ws()
        if (i < n && s[i] == '}') {
            i++
        } else {
            while (true) {
                ws()
                if (i >= n || s[i] != '"') fail("expected a field name")
                i++
                val field = readKey()
                expect(':')
                ws()
                if (i >= n) fail("unexpected end of input")
                when (field) {
                    F_ID -> id = readString()
                    F_PUBKEY -> pubKey = readString()
                    F_CREATED_AT -> createdAt = readLong()
                    F_KIND -> kind = readLong().toInt()
                    F_TAGS -> tags = readTags()
                    F_CONTENT -> content = readString()
                    F_SIG -> sig = readString()
                    else -> skipValue()
                }
                ws()
                if (i >= n) fail("unexpected end of input inside an object")
                val c = s[i++]
                if (c == ',') continue
                if (c == '}') break
                fail("expected ',' or '}'")
            }
        }

        if (pubKey.isEmpty()) throw IllegalArgumentException("Event not found")
        return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig)
    }

    /** Closing quote of the string body starting at [from], or -1 when the input ends first. */
    private fun quoteAfter(from: Int): Int = s.indexOf('"', from)

    /** Reads a field name (i is just past its opening quote) and returns which field it is. */
    private fun readKey(): Int {
        val start = i
        val q = quoteAfter(start)
        if (q < 0) fail("unexpected end of input in a field name")
        if (nextEscape < q) return fieldOf(readStringBody())
        i = q + 1
        return when (q - start) {
            2 -> if (s[start] == 'i' && s[start + 1] == 'd') F_ID else F_OTHER
            3 -> if (s.startsWith("sig", start)) F_SIG else F_OTHER
            4 -> if (s.startsWith("kind", start)) F_KIND else if (s.startsWith("tags", start)) F_TAGS else F_OTHER
            6 -> if (s.startsWith("pubkey", start)) F_PUBKEY else F_OTHER
            7 -> if (s.startsWith("content", start)) F_CONTENT else F_OTHER
            10 -> if (s.startsWith("created_at", start)) F_CREATED_AT else F_OTHER
            else -> F_OTHER
        }
    }

    private fun fieldOf(key: String): Int =
        when (key) {
            "id" -> F_ID
            "pubkey" -> F_PUBKEY
            "created_at" -> F_CREATED_AT
            "kind" -> F_KIND
            "tags" -> F_TAGS
            "content" -> F_CONTENT
            "sig" -> F_SIG
            else -> F_OTHER
        }

    private fun readString(): String {
        if (s[i] != '"') fail("expected a string")
        i++
        return readStringBody()
    }

    /** i is just past the opening quote; returns the decoded value and leaves i past the closing one. */
    private fun readStringBody(): String {
        val start = i
        val q = quoteAfter(start)
        if (q < 0) fail("unexpected end of input in a string")
        if (q < nextEscape) {
            i = q + 1
            return s.substring(start, q)
        }
        // Escapes: decode from the first backslash, run by run.
        sb.setLength(0)
        sb.append(s, start, nextEscape)
        i = nextEscape
        while (true) {
            if (i >= n) fail("unexpected end of input in a string")
            val c = s[i]
            if (c == '"') {
                i++
                break
            }
            if (c == '\\') {
                if (i + 1 >= n) fail("unexpected end of input in an escape")
                when (s[i + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 6 > n) fail("unexpected end of input in a \\u escape")
                        val v = (hex(s[i + 2]) shl 12) or (hex(s[i + 3]) shl 8) or (hex(s[i + 4]) shl 4) or hex(s[i + 5])
                        sb.append(v.toChar())
                        i += 4
                    }
                    else -> fail("bad escape")
                }
                i += 2
            } else {
                // A run up to the next quote or backslash.
                val qq = quoteAfter(i)
                if (qq < 0) fail("unexpected end of input in a string")
                var bs = s.indexOf('\\', i)
                if (bs < 0) bs = n
                val k = if (qq < bs) qq else bs
                sb.append(s, i, k)
                i = k
            }
        }
        nextEscape = s.indexOf('\\', i).let { if (it < 0) n else it }
        return sb.toString()
    }

    private fun hex(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> fail("bad hex digit in a \\u escape")
        }

    private fun readLong(): Long {
        val start = i
        var neg = false
        if (s[i] == '-') {
            neg = true
            i++
        }
        var v = 0L
        var digits = 0
        while (i < n) {
            val c = s[i]
            if (c < '0' || c > '9') break
            v = v * 10 + (c - '0')
            i++
            digits++
        }
        if (digits == 0) fail("expected a number")
        if (i < n && (s[i] == '.' || s[i] == 'e' || s[i] == 'E')) {
            // Rare: a fraction or exponent. Coerce like Jackson's longValue does.
            while (i < n && s[i] in "0123456789.eE+-") i++
            return s.substring(start, i).toDouble().toLong()
        }
        if (digits > 18) return s.substring(start, i).toLong()
        return if (neg) -v else v
    }

    private fun readTags(): Array<Array<String>> {
        if (s[i] != '[') fail("expected the tags array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAGS
        }
        var outer = tagsScratch
        var count = 0
        while (true) {
            ws()
            val tag = readTag()
            if (count == outer.size) {
                outer = outer.copyOf(count * 2)
                tagsScratch = outer
            }
            outer[count++] = tag
            ws()
            if (i >= n) fail("unexpected end of input in tags")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in tags")
        }
        val result = arrayOfNulls<Array<String>>(count)
        System.arraycopy(outer, 0, result, 0, count)
        java.util.Arrays.fill(outer, 0, count, null)
        @Suppress("UNCHECKED_CAST")
        return result as Array<Array<String>>
    }

    private fun readTag(): Array<String> {
        if (i >= n || s[i] != '[') fail("expected a tag array")
        i++
        ws()
        if (i < n && s[i] == ']') {
            i++
            return EMPTY_TAG
        }
        var buf = tagScratch
        var count = 0
        while (true) {
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            val v =
                when (s[i]) {
                    '"' -> readString()
                    'n' -> literal("null", "")
                    't' -> literal("true", "true")
                    'f' -> literal("false", "false")
                    else -> numberText()
                }
            if (count == buf.size) {
                buf = buf.copyOf(count * 2)
                tagScratch = buf
            }
            buf[count++] = v
            ws()
            if (i >= n) fail("unexpected end of input in a tag")
            val c = s[i++]
            if (c == ',') continue
            if (c == ']') break
            fail("expected ',' or ']' in a tag")
        }
        val result = arrayOfNulls<String>(count)
        System.arraycopy(buf, 0, result, 0, count)
        java.util.Arrays.fill(buf, 0, count, null)
        @Suppress("UNCHECKED_CAST")
        return result as Array<String>
    }

    private fun literal(
        word: String,
        value: String,
    ): String {
        if (!s.startsWith(word, i)) fail("expected $word")
        i += word.length
        return value
    }

    private fun numberText(): String {
        val start = i
        while (i < n && s[i] in "0123456789.eE+-") i++
        if (i == start) fail("unexpected character in a tag")
        return s.substring(start, i)
    }

    private fun skipValue() {
        when (s[i]) {
            '"' -> {
                i++
                readStringBody()
            }
            '{', '[' -> skipContainer()
            't' -> literal("true", "")
            'f' -> literal("false", "")
            'n' -> literal("null", "")
            else -> numberText()
        }
    }

    private fun skipContainer() {
        var depth = 0
        while (i < n) {
            when (s[i]) {
                '"' -> {
                    i++
                    readStringBody()
                    continue
                }
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) {
                        i++
                        return
                    }
                }
            }
            i++
        }
        fail("unexpected end of input in a value")
    }

    companion object {
        private const val F_OTHER = 0
        private const val F_ID = 1
        private const val F_PUBKEY = 2
        private const val F_CREATED_AT = 3
        private const val F_KIND = 4
        private const val F_TAGS = 5
        private const val F_CONTENT = 6
        private const val F_SIG = 7
        private val EMPTY_TAGS = emptyArray<Array<String>>()
        private val EMPTY_TAG = emptyArray<String>()
    }
}

class QuartzHand : StringInputParser() {
    private val reader = NostrEventReader()

    override fun decode(input: String): Event = reader.parse(input).let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = reader.parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = reader.parse(s)
            sink += (e.kind + e.tags.size + e.content.length).toLong()
        }
        return sink
    }
}

/** As [QuartzHand], but a new reader per parse, to see what a stateless entry point costs. */
class QuartzHandFresh : StringInputParser() {
    override fun decode(input: String): Event = NostrEventReader().parse(input).let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = NostrEventReader().parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = NostrEventReader().parse(s)
            sink += (e.kind + e.tags.size + e.content.length).toLong()
        }
        return sink
    }
}

/** Quartz's own loop, but on Jackson's UTF-8 byte parser: the frame is encoded first (timed). */
class QuartzLoopBytes : StringInputParser() {
    private val json = JsonFactory()
    private val loop = QuartzEventLoop(intern = false, factory = true)

    private fun parse(input: String): QuartzEvent = json.createParser(input.toByteArray(Charsets.UTF_8)).use { loop.readFrom(it) }

    override fun decode(input: String): Event = parse(input).let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }

    override fun keep(input: String): Any = parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = parse(s)
            sink += (e.kind + e.tags.size + e.content.length).toLong()
        }
        return sink
    }
}
