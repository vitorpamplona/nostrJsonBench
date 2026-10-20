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
- **Code:** the Rust, C/C++, Go, JS and Python rows come from Amethyst's `jsonBench/` tree at
  commit `c8c46fb3` (the code this repo was imported from). The **JVM rows were re-run
  later the same day** with this repo's JVM harness against Quartz at Amethyst `main`
  `df1faea1` (`QUARTZ_DIR=../amethyst`), which includes
  [amethyst#4583](https://github.com/vitorpamplona/amethyst/pull/4583): Quartz no longer
  interns while parsing, LocalCache does it when it keeps an event. Each JVM row records
  that commit as its Quartz version.
- **Noise:** at the default settings the 27 MB `all` workload gets only ~15–40 rounds per
  JVM parser, and a few rows move ±30% between runs (`jackson-streaming-string`: 1,528 ns
  in the first run, 2,159 in this one). Use the per-kind workloads in report.md for fine
  comparisons; they run hundreds of rounds.

## Headline: typed parse of the `all` workload (31,161 real events)

Best parser per runtime that builds an event with owned strings, starting from what a
WebSocket text frame is in that language (conversions timed). × is speed relative to
Amethyst's production parser.

| runtime | parser | MB/s | ns/event | × quartz-jackson | retained B/event |
|---|---|---:|---:|---:|---:|
| C++ | simdjson on-demand | 1,363 | 635 | 2.6× | 1,288 |
| Rust | sonic-rs | 1,279 | 677 | 2.4× | 1,281 |
| Go | sonic | 1,174 | 738 | 2.2× | 1,431 |
| JVM | jsoniter-scala | 1,036 | 836 | 1.9× | 1,381 |
| Deno | `JSON.parse` | 877 | 988 | 1.6× | 1,229 |
| Python | msgspec (typed Struct) | 822 | 1,054 | 1.5× | 1,712 |
| Node | `JSON.parse` | 763 | 1,135 | 1.4× | 1,234 |
| Bun | `Bun.JSONL.parse` | 735 | 1,178 | 1.4× | 1,031 |
| **JVM** | **`quartz-jackson` (Amethyst `main`)** | **535** | **1,620** | **1.0×** | **1,376**, 900 once LocalCache shares its strings |
| JVM | `quartz-jackson` as in Quartz 1.19.0 (first run) | 155 | 5,585 | 0.3× | 916 |

## JVM, the libraries Amethyst could use

| parser | input | MB/s | ns/event | × quartz | alloc B/event | retained B/event |
|---|---|---:|---:|---:|---:|---:|
| jsoniter-scala ¹ | `readFromString` | 1,036 | 836 | 1.9× | 2,772 | 1,381 |
| fastjson2, reflection | String | 957 | 905 | 1.8× | 3,701 | 1,389 |
| fastjson2, hand loop | String → bytes | 791 | 1,096 | 1.5× | 3,096 | 1,389 |
| Jackson streaming | String → bytes | 647 | 1,338 | 1.2× | 3,282 | 1,370 |
| DSL-JSON, hand loop | String → bytes | 629 | 1,377 | 1.2× | 2,542 | 1,381 |
| **Quartz Jackson path** | String | **535** | **1,620** | 1.0× | 2,332 | **1,376** |
| Quartz kotlinx path | String | 527 | 1,643 | 1.0× | 4,476 | 1,376 |
| kotlinx.serialization | String | 463 | 1,871 | 0.9× | 2,943 | 1,370 |
| Moshi streaming | okio `writeUtf8` | 447 | 1,936 | 0.8× | 3,067 | 1,370 |
| Jackson streaming | String | 401 | 2,159 | 0.8× | 2,076 | 1,370 |
| Gson streaming | String | 262 | 3,309 | 0.5× | 5,127 | 1,381 |

¹ Needs the Scala runtime. simdjson-java (DOM only, 885 MB/s from a String) needs the
JDK Vector API, absent on Android/ART.

## Where Quartz's time goes (JVM)

`quartz-loop*` rows run Quartz's `EventDeserializer` loop on a bare jackson-core parser,
one cost at a time; `quartz-jackson-cacheshare` adds what LocalCache does to an event it
keeps (`Event.shareStrings`: id and pubkey shared with the cache keys, tag values
interned), applied to every event. Shown on `notes` (3,568 kind-1 events, ~250 rounds
each):

| `notes` workload | ns/event | retained B/event |
|---|---:|---:|
| `quartz-jackson`: production parse (databind → `EventDeserializer` → `EventFactory`) | 1,689 | 1,705 |
| `quartz-loop`: the same loop without databind's `readValue` | 1,655 | 1,705 |
| `quartz-loop-plain`: without `EventFactory` either | 1,588 | 1,697 |
| `quartz-loop-intern`: with the parse-time `String.intern()` of Quartz 1.19.0 | 3,762 | 1,030 |
| `quartz-jackson-cacheshare`: parse + LocalCache's sharing, every event | 3,238 | 1,030 |

- **Interning is still worth ~40% of the memory a kept note holds** (1,705 → 1,030 B;
  35% on `all`). It costs 0.6–2.1 µs per event on small kinds and 3.9–5.6 µs on zaps and
  lists, whose long tag values (embedded zap requests, bolt11 invoices, hundreds of `p`
  tags) all get hashed. Since amethyst#4583 only events the cache keeps pay it, at
  roughly the same price (0.3–1.6 µs on small kinds, ~3.8 µs on zaps, ~5.8 µs on lists);
  the duplicates and dropped events that are most of what relays send (87% of the 2024
  startup dump) parse at the plain rate. Measured end to end through Amethyst's own
  `EventCache` on the full dump with duplicates, that took a received event from 15.4 to
  12.2 µs with the same retained heap (87.9 MB either way; 102.5 MB without interning).
- **`EventFactory` costs ~70–130 ns per event** (~400 ns on tag-heavy lists) now that the
  zap receipt's embedded request is parsed lazily; it cost 6.5 µs per zap in the first run.
- **databind's `readValue` is within noise** of the bare jackson-core loop (−180 to +60
  ns/event); it is not where the time goes.

## Other findings

- **Borrowing and DOMs:** if strings may point into the frame, simdjson reaches
  2.0 GB/s (C++) and sonic-rs 1.7 GB/s (Rust); generic trees reach 3.2 GB/s
  (simdjson DOM, reused parser). Python's pysimdjson DOM does 1.7 GB/s.
- **Retained memory of an owned event** is ~1.1–1.4 KB in every compiled language and
  the JVM; V8 is 1.23 KB, JSC 1.03 KB, CPython 1.6–2.1 KB. Only interning (Amethyst's
  LocalCache, 900 B) or hex-decoding ids into bytes (Go's fiatjaf.com/nostr, 1,140) goes
  lower.
- **Libraries that fail verification** (excluded from rankings): cJSON truncates at an
  escaped `\u0000`; ojg (Go) turns each half of an escaped emoji into U+FFFD;
  `@jsonjoy.com/json-pack` does not decode `\uXXXX`; the `simdjson` npm addon leaks
  native memory on Node 22 (OOM-killed at 7 GB) and its lazy mode segfaults there.
- **Go's ecosystem** spans 7× (sonic 1,174 MB/s vs `encoding/json` 161 MB/s); the
  Nostr-specific decoders (fiatjaf.com/nostr 832, go-nostr 764) beat most generic ones.
- **JS engines:** `JSON.parse` on Deno (V8 15) is 15% faster than on Node 22 (V8 12.4);
  `TextEncoder.encode` costs more than `JSON.parse` itself (Node 1,415 vs 1,135 ns/event,
  Deno 5,621 vs 988), so never route a text frame through bytes there.
