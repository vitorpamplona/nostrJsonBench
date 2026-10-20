# nostrJsonBench

Which JSON parser, in any language, turns Nostr events into usable objects fastest and
with the least garbage and retained memory? nostrJsonBench runs the same real events through
~150 parser variants in six harnesses (JVM, Rust, C/C++, Go, JS on Node/Bun/Deno,
Python), proves every parser decoded every event identically, and ranks them in one
report against what [Amethyst](https://github.com/vitorpamplona/amethyst) ships today
(`quartz-jackson`: Jackson + Quartz's `EventDeserializer`).

The latest published run is in [`results/reference/`](results/reference/).

```bash
./run.sh                       # every harness whose toolchain is installed (~1-1.5 h)
./run.sh --quick               # smoke run, minutes, noisy numbers
./run.sh --only jvm,rust       # a subset
./run.sh --workloads all,notes # any HARNESS.md flag goes to every harness
CORPUS_SOURCE=dump.ndjson.gz ./run.sh --rebuild-corpus   # your own events
QUARTZ_DIR=../amethyst ./run.sh --only jvm               # an unreleased Quartz from an Amethyst checkout
```

Results: `results/<harness>.json`, merged into `results/report.md`
(`python3 report/report.py` regenerates it). Harnesses run one after another:
running two at once steals cores from both.

## What is measured

- **The task.** One WebSocket text frame's event JSON → one in-memory event with all
  seven NIP-01 fields decoded (content unescaped, tags as string arrays). Single thread.
- **The input.** Each language starts from what its usual WebSocket client hands an app
  for a text frame: JVM `String` (OkHttp), Rust `&str`, C++ `std::string`, Go `[]byte`,
  JS string, Python `str`. Anything a parser needs on top (encode to bytes, pad, copy)
  is inside the timed loop. See [HARNESS.md](HARNESS.md).
- **Speed:** median round over the whole workload → MB/s and ns per event.
- **Garbage:** bytes (and, where the runtime can count them, allocations) per parsed
  event, plus GC collections/time during measurement.
- **Retained memory:** parse the whole workload again, keep every result alive (as a
  client cache does), and report the heap still held per event. This is where
  de-duplication like Quartz's `String.intern()` pays off.
- **Correctness:** every parser's output is folded into a SHA-256 digest that must match
  the reference (Python's `json`) on every workload; mismatches are reported and excluded
  from rankings. Several libraries fail it (see the report).

Modes: `typed` (the language's own event struct with owned strings, the headline),
`borrowed` (strings point into the frame or a buffer), `dom` (the library's generic
tree), `baseline` (not a parser, e.g. a UTF-8 encode).

## Corpus

`corpus/nostr-2024-startup.ndjson.gz` holds 31,161 unique real events (27 MB
uncompressed) from an Amethyst startup session in 2024, one per line, raw bytes as
received (de-duplicated from Quartz's test fixture `nostr_vitor_startup_data.json.gz`).
`corpus/prepare.py` slices them into workloads by event shape and computes each
workload's reference digest:

| workload | events | avg bytes | what |
|---|---:|---:|---|
| all | 31,161 | 866 | the real kind mix |
| notes | 3,568 | 904 | kind 1 |
| reactions | 2,259 | 558 | kind 7 |
| dms | 9,600 | 640 | kind 4, base64 content |
| giftwraps | 1,614 | 1,700 | kind 1059, one long NIP-44 payload |
| metadata | 268 | 876 | kind 0, escaped JSON content |
| zaps | 1,270 | 2,134 | kind 9735, escaped JSON in a tag |
| lists | 40 | 8,529 | contact/mute/relay lists, tag-heavy |

## Layout

| dir | harness | notes |
|---|---|---|
| `jvm/` | standalone Gradle project (Kotlin, one JVM fork per parser), Quartz from Maven Central or a local Amethyst checkout (`QUARTZ_DIR=…`) | Quartz's own paths + Jackson, kotlinx, Gson, Moshi, fastjson2, DSL-JSON, simdjson-java, jsoniter (Java/Scala), Parsson, minimal-json, json-smart, org.json; `quartz-loop*` rows break `quartz-jackson` down |
| `rust/` | Cargo | serde_json, simd-json, sonic-rs, jiter, simdjson-rust, miniserde, nanoserde, … |
| `cpp/` | CMake, deps fetched into `.cache/` | simdjson, yyjson, RapidJSON, glaze, Boost.JSON, nlohmann, sajson, cJSON, json-c, Jansson, jsmn |
| `go/` | Go module, one process per parser | stdlib, json/v2, sonic, goccy, easyjson, jsoniter, segmentio, fastjson, simdjson-go, go-nostr, fiatjaf.com/nostr, … |
| `node/` | one script for Node, Bun and Deno | `JSON.parse`, Bun.JSONL, simdjson addon, … |
| `python/` | venv | json, orjson, msgspec, ujson, pysimdjson, jiter, yyjson, … |

Each directory's README lists its parsers with versions and licenses. Licenses are
permissive except Parsson (EPL-2.0 / GPL-2.0 with Classpath exception) in the JVM
harness. Toolchains used for the reference run: JDK 21, Rust 1.97, GCC 13 + CMake,
Go 1.24, Node 22 (Bun and Deno come from npm), Python 3.13.

## Caveats

- Numbers are from a server CPU with HotSpot, not ART on a phone. Rankings between JVM
  libraries usually carry over; ratios may not. simdjson-java (needs the JDK Vector API)
  and jsoniter-scala (needs the Scala runtime) cannot ship on Android anyway.
- The workload line is a bare event, not the `["EVENT", sub, {…}]` relay frame; the
  wrapper adds ~30 bytes and one array level.
- Retained memory depends on how much the kept set repeats itself (same authors, same
  tagged ids); the `all` workload is a real client session, so it is the realistic case.
