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

import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import java.lang.reflect.Method
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

private fun QuartzEvent.toBench() = Event(id, pubKey, createdAt, kind, tags, content, sig)

/**
 * The production path: Quartz's EventDeserializer on jackson-databind + EventFactory. Quartz
 * 1.19.0 and earlier also interned id, pubkey and tag values here; later versions intern when
 * LocalCache keeps the event instead (see [QuartzJacksonCacheShare]).
 */
class QuartzJackson : StringInputParser() {
    override fun decode(input: String) = JacksonMapper.fromJson(input).toBench()

    override fun keep(input: String): Any = JacksonMapper.fromJson(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = JacksonMapper.fromJson(s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

/** Quartz's kotlinx.serialization path (the one native/iOS targets use). */
class QuartzKotlinx : StringInputParser() {
    override fun decode(input: String) = KotlinSerializationMapper.fromJson(input).toBench()

    override fun keep(input: String): Any = KotlinSerializationMapper.fromJson(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = KotlinSerializationMapper.fromJson(s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

/**
 * The production path followed by what Amethyst's LocalCache does to an event it keeps
 * (Note.loadEvent -> Event.shareStrings, Amethyst #4583): id and pubkey are swapped for the
 * cache's own keys (free: here the event's own instances) and tag values are interned. The
 * timed loop does this for every event, i.e. the worst case where every event is new and kept.
 * keep() interns id and pubkey too, leaving the same heap as sharing them with the cache keys.
 *
 * Event.shareStrings is newer than Quartz 1.19.0, so it is called through reflection, resolved
 * once; against a Quartz without it this row reports an error instead of breaking the build.
 */
class QuartzJacksonCacheShare : StringInputParser() {
    private val intern: (String) -> String = { it.intern() }

    private val shareStrings: Method =
        try {
            QuartzEvent::class.java.getMethod("shareStrings", String::class.java, String::class.java, Function1::class.java)
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("needs a Quartz with Event.shareStrings (newer than 1.19.0); run with QUARTZ_DIR=<amethyst checkout>", e)
        }

    private fun parseAndShare(
        input: String,
        sharedIdAndPubKey: Boolean,
    ): QuartzEvent {
        val e = JacksonMapper.fromJson(input)
        if (sharedIdAndPubKey) {
            shareStrings.invoke(e, e.id.intern(), e.pubKey.intern(), intern)
        } else {
            shareStrings.invoke(e, e.id, e.pubKey, intern)
        }
        return e
    }

    override fun decode(input: String) = parseAndShare(input, false).toBench()

    override fun keep(input: String): Any = parseAndShare(input, true)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = parseAndShare(s, false)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
