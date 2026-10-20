# Reference run, 2026-10-20

Full run (`./run.sh`, defaults: 1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
workload), one harness at a time on an otherwise idle machine. **[report.md](report.md)**
has every ranking, the per-workload matrix, the interning breakdown, the failures and
the library list; the `*.json` files are the raw rows.

- **Machine:** cloud VM, Intel Xeon (AVX2 + AVX-512), 4 cores, Linux 6.18. Single-threaded.
- **Runtimes:** OpenJDK 21.0.12 (HotSpot, G1, 2 GB fixed heap), rustc 1.97 (`-C
  target-cpu=native`, fat LTO), GCC 13.3 (`-O3 -march=native`), Go 1.24.7, Node 22.22,
  Bun 1.4.3, Deno 2.9.6, CPython 3.13.16.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`.
- **Code:** produced from Amethyst's `jsonBench/` tree at commit `c8c46fb3` (the code this
  repo was imported from); the JVM rows used Quartz built from that tree, equivalent to
  the Quartz 1.19.0 release the standalone JVM harness now depends on.

## Headline: typed parse of the `all` workload (31,161 real events)

Best parser per runtime that builds an event with owned strings, starting from what a
WebSocket text frame is in that language (conversions timed). × is speed relative to
Amethyst's production parser.

| runtime | parser | MB/s | ns/event | × quartz-jackson | retained B/event |
|---|---|---:|---:|---:|---:|
| C++ | simdjson on-demand | 1,363 | 635 | 8.8× | 1,288 |
| Rust | sonic-rs | 1,279 | 677 | 8.2× | 1,281 |
| Go | sonic | 1,174 | 738 | 7.6× | 1,431 |
| JVM | jsoniter-scala | 1,000 | 866 | 6.5× | 1,381 |
| Deno | `JSON.parse` | 877 | 988 | 5.7× | 1,229 |
| Python | msgspec (typed Struct) | 822 | 1,054 | 5.3× | 1,712 |
| Node | `JSON.parse` | 763 | 1,135 | 4.9× | 1,234 |
| Bun | `Bun.JSONL.parse` | 735 | 1,178 | 4.7× | 1,031 |
| **JVM** | **`quartz-jackson` (Amethyst today)** | **155** | **5,585** | **1.0×** | **916** |

## JVM, the libraries Amethyst could use

| parser | input | MB/s | ns/event | × quartz | alloc B/event | retained B/event |
|---|---|---:|---:|---:|---:|---:|
| jsoniter-scala ¹ | `readFromString` | 1,000 | 866 | 6.5× | 2,772 | 1,381 |
| fastjson2, reflection | String | 793 | 1,092 | 5.1× | 3,701 | 1,389 |
| fastjson2, hand loop | String → bytes | 740 | 1,171 | 4.8× | 3,096 | 1,389 |
| DSL-JSON, hand loop | String → bytes | 656 | 1,321 | 4.2× | 2,542 | 1,381 |
| Jackson streaming | String → bytes | 632 | 1,370 | 4.1× | 3,322 | 1,370 |
| Jackson streaming | String | 567 | 1,528 | 3.7× | 2,076 | 1,370 |
| Moshi streaming | okio `writeUtf8` | 455 | 1,903 | 2.9× | 3,067 | 1,370 |
| kotlinx.serialization | String | 453 | 1,910 | 2.9× | 2,943 | 1,370 |
| Quartz kotlinx path | String | 439 | 1,975 | 2.8× | 4,619 | 1,407 |
| Gson streaming | String | 258 | 3,358 | 1.7× | 5,127 | 1,381 |
| **Quartz Jackson path** | String | **155** | **5,585** | 1.0× | 2,803 | **916** |

¹ Needs the Scala runtime. simdjson-java (DOM only, 885 MB/s from a String) needs the
JDK Vector API, absent on Android/ART.

## Where Quartz's time goes (JVM)

`quartz-loop*` rows run Quartz's `EventDeserializer` loop on a bare jackson-core
parser, removing one cost at a time:

| `all` workload | ns/event | retained B/event |
|---|---:|---:|
| `quartz-jackson` (production) | 5,585 | 916 |
| − `ObjectMapper.readValue` (`quartz-loop`) | 5,354 | 916 |
| − `String.intern()` (`quartz-loop-nointern`) | 1,988 | 1,407 |
| − `EventFactory` (`quartz-loop-plain`) | 1,520 | 1,370 |

- **`String.intern()` is ~60% of the parse** (3.4 µs/event) and buys a **35% smaller
  retained heap** (916 vs 1,407 B/event): repeated pubkeys, ids and tag values become
  one object. Its cost grows with the size of the JVM string table — 0.8–2 µs/event on
  the small per-kind workloads, 3.4 µs on the full set — so a long-running client pays
  the high end. De-duplicating only what is kept (when an event enters the cache)
  would keep the memory win without taxing every parse.
- **`EventFactory` is cheap except for zaps**: 6.5 µs per kind-9735 receipt
  (3,334 → 9,891 ns/event on `zaps`), i.e. the zap event's construction does heavy work.
- **databind's `readValue` costs ~230 ns/event**; it is not the problem.

## Other findings

- **Borrowing and DOMs:** if strings may point into the frame, simdjson reaches
  2.0 GB/s (C++) and sonic-rs 1.7 GB/s (Rust); generic trees reach 3.2 GB/s
  (simdjson DOM, reused parser). Python's pysimdjson DOM does 1.7 GB/s.
- **Retained memory of an owned event** is ~1.1–1.4 KB in every compiled language and
  the JVM; V8 is 1.23 KB, JSC 1.03 KB, CPython 1.6–2.1 KB. Only interning (Quartz) or
  hex-decoding ids into bytes (Go's fiatjaf.com/nostr, 1,140) goes lower.
- **Libraries that fail verification** (excluded from rankings): cJSON truncates at an
  escaped `\u0000`; ojg (Go) turns each half of an escaped emoji into U+FFFD;
  `@jsonjoy.com/json-pack` does not decode `\uXXXX`; the `simdjson` npm addon leaks
  native memory on Node 22 (OOM-killed at 7 GB) and its lazy mode segfaults there.
- **Go's ecosystem** spans 7× (sonic 1,174 MB/s vs `encoding/json` 161 MB/s); the
  Nostr-specific decoders (fiatjaf.com/nostr 832, go-nostr 764) beat most generic ones.
- **JS engines:** `JSON.parse` on Deno (V8 15) is 15% faster than on Node 22 (V8 12.4);
  `TextEncoder.encode` costs more than `JSON.parse` itself (Node 1,415 vs 1,135 ns/event,
  Deno 5,621 vs 988), so never route a text frame through bytes there.
