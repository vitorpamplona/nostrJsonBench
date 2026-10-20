# nostrJsonBench

Which JSON parser decodes Nostr events fastest, with the least garbage and memory? ~150
parser variants in six harnesses (JVM, Rust, C/C++, Go, JS on Node/Bun/Deno, Python) parse
the same 31k real events; every result is verified by digest and compared against
[Amethyst](https://github.com/vitorpamplona/amethyst)'s parser, `quartz-jackson`.

## Results

2026-10-20, 4-core Xeon VM, single thread. Full tables, every workload and the failures:
[report.md](results/reference/2026-10-20/report.md) (run details in its
[README](results/reference/2026-10-20/README.md)).

**Best parser per runtime** (`all` workload, JSON → event with owned strings):

| runtime | parser | ns/event | MB/s | retained B/event | × quartz |
|---|---|---:|---:|---:|---:|
| C++ | simdjson on-demand | 635 | 1,363 | 1,288 | 2.6× |
| Rust | sonic-rs | 677 | 1,279 | 1,281 | 2.4× |
| Go | sonic | 738 | 1,174 | 1,431 | 2.2× |
| JVM | jsoniter-scala | 836 | 1,036 | 1,381 | 1.9× |
| Deno | `JSON.parse` | 988 | 877 | 1,229 | 1.6× |
| Python | msgspec | 1,054 | 822 | 1,712 | 1.5× |
| Node | `JSON.parse` | 1,135 | 763 | 1,234 | 1.4× |
| Bun | `Bun.JSONL.parse` | 1,178 | 735 | 1,031 | 1.4× |
| JVM | **`quartz-jackson`** | **1,620** | **535** | **1,376** | 1.0× |

**JVM**, one row per library: its fastest path from the String a WebSocket delivers
(the library's String API, or encoding to bytes first when that is faster; the encode is
timed). Its other paths are under "Variants" in the report.

| library | path | ns/event | alloc B/event | retained B/event |
|---|---|---:|---:|---:|
| jsoniter-scala ¹ | String API | 836 | 2,772 | 1,381 |
| fastjson2 | String API | 905 | 3,701 | 1,389 |
| Jackson streaming | encode + byte API | 1,338 | 3,282 | 1,370 |
| jsoniter (Java) | encode + byte API | 1,345 | 2,886 | 1,381 |
| DSL-JSON | encode + byte API | 1,354 | 2,716 | 1,381 |
| **Quartz, Jackson path** | String API | **1,620** | 2,332 | 1,376 (900 once LocalCache shares its strings) |
| Quartz, kotlinx path | String API | 1,643 | 4,476 | 1,376 |
| kotlinx.serialization | String API | 1,871 | 2,943 | 1,370 |
| Moshi | okio `writeUtf8` | 1,936 | 3,067 | 1,370 |
| Parsson | String API | 2,558 | 2,039 | 1,381 |
| Gson | String API | 3,309 | 5,127 | 1,381 |

¹ Needs the Scala runtime. simdjson-java (805 MB/s, DOM only) needs the JDK Vector API;
neither ships on Android.

**Findings**

- **Quartz** dropped from 5,585 to 1,620 ns/event when
  [amethyst#4583](https://github.com/vitorpamplona/amethyst/pull/4583) moved
  `String.intern()` from the parser to LocalCache entry. Interning still cuts a kept
  event's memory by 35% (1,376 → 900 B), but only kept events pay for it now.
- **Borrowing and DOMs** go further: simdjson 2.0 GB/s and sonic-rs 1.7 GB/s with strings
  pointing into the frame, simdjson's DOM 3.2 GB/s.
- **Go** spans 7× (sonic 1,174 MB/s vs `encoding/json` 161); the Nostr-specific decoders
  (fiatjaf.com/nostr 832 MB/s, go-nostr 764) beat most generic ones.
- **JS:** Deno's `JSON.parse` is 15% faster than Node 22's. On Node and Deno,
  `TextEncoder.encode` costs more than `JSON.parse` itself; a parser that needs bytes
  should `encodeInto` a reused buffer (~150–190 ns/event).
- **Fail verification:** cJSON (cuts strings at `\u0000`), ojg (mangles escaped emoji),
  `@jsonjoy.com/json-pack` (no `\uXXXX`), the `simdjson` npm addon (leaks and segfaults
  on Node 22).

## Run

```bash
./run.sh                        # every harness whose toolchain is installed (~1–1.5 h)
./run.sh --quick                # smoke run, minutes
./run.sh --only jvm,rust        # a subset; any HARNESS.md flag passes through
QUARTZ_DIR=../amethyst ./run.sh --only jvm             # an unreleased Quartz
CORPUS_SOURCE=dump.ndjson.gz ./run.sh --rebuild-corpus # your own events
```

Results land in `results/<harness>.json` and `results/report.md`. Harnesses run one at a
time; each directory's README lists its parsers, versions and licenses (all permissive
except Parsson, EPL-2.0 / GPL-2.0 with Classpath exception, in the JVM harness).

## Method

- **Task:** one event JSON → one in-memory event with all seven NIP-01 fields decoded.
- **Input:** what the language's WebSocket client hands an app for a text frame (JVM
  `String`, Rust `&str`, C++ `std::string`, Go `[]byte`, JS string, Python `str`); any
  conversion a parser needs is timed.
- **Metrics:** median round time, bytes/allocations per event, GC, and retained heap
  with every parsed event kept alive.
- **Correctness:** each parser's output must reproduce a SHA-256 digest per workload.

Full contract: [HARNESS.md](HARNESS.md).

**Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, 31,161 unique events (27 MB) from an
Amethyst startup session in 2024, raw bytes as received, sliced by `corpus/prepare.py`
into `all`, `notes`, `reactions`, `dms`, `giftwraps`, `metadata`, `zaps` and `lists`.

**Caveats:** HotSpot on a server CPU, not ART on a phone; bare events, not
`["EVENT", sub, …]` frames; at default settings the 27 MB `all` workload is noisy for
some JVM rows, so compare fine differences on the per-kind workloads in the report.
