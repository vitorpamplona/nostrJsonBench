# Nostr JSON Parsing Benchmark

How fast can a JSON parser turn a Nostr JSON event into an object? And how much memory does it
use?

We ran 72 libraries in six languages over the same 31k real events. Counting each
library's entry points and its DOM and borrowed modes, that is 138 configurations. Every
output is checked against a reference digest.

How it works: [HARNESS.md](HARNESS.md). Every table, including DOM, borrowed, per-workload
results and failures: [report.md](results/reference/2026-10-21/report.md).

## Results

Each row is one library, using its fastest path from what a WebSocket delivers (a JVM
`String`, a Rust `&str`, …) to an event with owned strings.

Measured on the `all` workload, 2026-10-21, a 4-core Xeon, single thread.

Each parse starts the way it does in a running app. The parser is warm, and the frame is
new: built just before the parse, as the socket would hand it over.

The JVM is JetBrains Runtime 21 with Amethyst Desktop's flags (`-Xmx2g`, G1). Each JVM
row is the median of three JVMs, because the JIT can compile a parser faster in one JVM
than in the next.

| # | runtime | parser | ns/event | alloc B/event | objects created/event | objects kept/event | garbage objects/event | retained B/event |
|---:|---|---|---:|---:|---:|---:|---:|---:|
| 1 | C/C++ | `simdjson-ondemand-typed` | 607 | 1,249 | 14.4 | 11.1 | 3.3 | 1,288 |
| 2 | Rust | `sonic_rs-typed` | 640 | 1,677 | 15.7 | 14.0 | 1.7 | 1,281 |
| 3 | C/C++ | `glaze-typed` | 717 | 1,328 | 15.0 | 11.7 | 3.3 | 1,408 |
| 4 | Rust | `simd_json_derive-typed` | 721 | 2,705 | 15.2 | 14.0 | 1.2 | 1,109 |
| 5 | Rust | `serde_json-typed` | 722 | 1,644 | 15.4 | 14.0 | 1.4 | 1,281 |
| 6 | Rust | `jiter-typed` | 723 | 1,644 | 15.4 | 14.0 | 1.4 | 1,281 |
| 7 | Go | `sonic-typed` | 733 | 1,529 | 8.1 | 6.0 | 2.1 | 1,427 |
| 8 | C/C++ | `yyjson-typed` | 758 | 4,323 | 12.1 | 11.1 | 1.0 | 1,262 |
| 9 | JVM | `jsoniter-scala` | 766 | 2,772 | 30.8 | 25.8 | 5.0 | 1,381 |
| 10 | Rust | `simdjson_rust-typed` | 767 | 973 | 14.0 | 14.0 | 0.0 | 1,109 |
| 11 | Rust | `simd_json-typed` | 770 | 2,705 | 15.2 | 14.0 | 1.2 | 1,109 |
| 12 | Go | `goccy-typed` | 924 | 1,232 | 5.1 | 4.9 | 0.2 | 1,201 |
| 13 | JVM | `fastjson2-reader` | 959 | 3,096 | 33.4 | 26.3 | 7.1 | 1,389 |
| 14 | Deno | `json-parse-string` | 980 | 1,230 | 15.4 | 15.4 | 0.0 | 1,229 |
| 15 | C/C++ | `rapidjson-sax-typed` | 994 | 1,249 | 14.4 | 11.1 | 3.3 | 1,289 |
| 16 | Python | `msgspec-typed` | 994 | – | – | 20.1 | – | 1,712 |
| 17 | Bun | `bun-jsonl-string` | 1,014 | – | – | 13.7 | – | 1,031 |
| 18 | Bun | `json-parse-string` | 1,028 | – | – | 13.7 | – | 1,031 |
| 19 | Go | `fiatjaf-nostr-typed` | 1,046 | 1,169 | 10.0 | 9.0 | 1.0 | 1,140 |
| 20 | Rust | `miniserde-typed` | 1,057 | 2,162 | 21.1 | 14.0 | 7.1 | 1,281 |
| 21 | Go | `jx-typed` | 1,063 | 1,689 | 14.4 | 12.1 | 2.3 | 1,193 |
| 22 | Python | `orjson` | 1,090 | – | – | 21.1 | – | 1,840 |
| 23 | Go | `go-nostr-typed` | 1,101 | 1,338 | 13.0 | 12.0 | 1.0 | 1,310 |
| 24 | Go | `easyjson-typed` | 1,152 | 1,223 | 13.5 | 12.0 | 1.5 | 1,168 |
| 25 | JVM | `dsljson-bytes` | 1,153 | 2,542 | 30.7 | 25.8 | 4.9 | 1,381 |
| 26 | Node | `json-parse-string` | 1,165 | – | 15.7 | 15.6 | 0.0 | 1,234 |
| 27 | Go | `fastjson-typed` | 1,171 | 1,058 | 12.8 | 11.9 | 0.9 | 1,058 |
| 28 | JVM | `jackson-streaming-bytes` | 1,209 | 3,322 | 41.2 | 25.3 | 15.9 | 1,370 |
| 29 | Python | `pysimdjson-recursive` | 1,244 | – | – | 25.9 | – | 2,074 |
| 30 | Python | `jiter` | 1,266 | – | – | 16.2 | – | 1,604 |
| 31 | JVM | `jsoniter-iterator` | 1,278 | 2,886 | 44.7 | 25.8 | 18.9 | 1,381 |
| 32 | Python | `yyjson` | 1,395 | – | – | 28.1 | – | 2,089 |
| 33 | Rust | `hifijson-serde-typed` | 1,401 | 1,655 | 15.5 | 14.0 | 1.5 | 1,281 |
| 34 | C/C++ | `boostjson-typed` | 1,423 | 2,438 | 28.3 | 11.1 | 17.2 | 1,260 |
| 35 | JVM | `quartz-jackson` | 1,514 | 2,332 | 43.1 | 25.5 | 17.6 | 1,376 |
| 36 | JVM | `parsson-streaming` | 1,709 | 2,038 | 54.3 | 25.8 | 28.5 | 1,381 |
| 37 | JVM | `kotlinx-typed` | 1,793 | 2,943 | 54.9 | 25.4 | 29.5 | 1,370 |
| 38 | Go | `jsoniter-typed-compat` | 1,818 | 1,717 | 19.0 | 12.1 | 6.9 | 1,080 |
| 39 | JVM | `moshi-streaming` | 1,875 | 3,067 | 51.4 | 25.3 | 26.1 | 1,370 |
| 40 | Python | `python-rapidjson` | 1,911 | – | – | 25.9 | – | 2,127 |
| 41 | Python | `ujson` | 1,983 | – | – | 25.9 | – | 2,129 |
| 42 | JVM | `quartz-kotlinx` | 2,139 | 4,476 | 115.2 | 25.5 | 89.7 | 1,378 |
| 43 | Go | `segmentio-typed` | 2,259 | 1,728 | 14.0 | 12.0 | 2.1 | 1,578 |
| 44 | Bun | `simdjson-parse` | 2,263 | – | – | 17.5 | – | 1,260 |
| 45 | Go | `jsonv2-typed` | 2,329 | 1,242 | 13.7 | 8.9 | 4.8 | 926 |
| 46 | Rust | `nanoserde-typed` | 2,364 | 2,310 | 30.3 | 14.2 | 16.1 | 1,497 |
| 47 | Python | `json-stdlib` | 2,724 | – | – | 25.9 | – | 2,127 |
| 48 | Go | `jsonparser-typed` | 2,767 | 1,419 | 13.4 | 12.0 | 1.4 | 1,191 |
| 49 | Python | `simplejson` | 2,934 | – | – | 25.9 | – | 2,127 |
| 50 | Deno | `simdjson-parse` | 3,130 | 1,710 | 17.6 | 16.5 | 1.0 | 1,701 |
| 51 | JVM | `gson-streaming` | 3,362 | 5,127 | 51.3 | 25.8 | 25.5 | 1,383 |
| 52 | Rust | `struson-typed` | 3,634 | 2,181 | 25.9 | 14.0 | 11.9 | 1,408 |
| 53 | C/C++ | `nlohmann-sax-typed` | 5,150 | 3,964 | 38.2 | 11.1 | 27.1 | 1,803 |
| 54 | Go | `stdlib-typed` | 5,465 | 1,660 | 23.4 | 12.3 | 11.2 | 1,082 |
| 55 | Go | `gjson-typed` | 5,551 | 1,832 | 12.3 | 9.7 | 2.6 | 1,337 |
| 56 | Bun | `streamparser-json` | 7,145 | – | – | 21.1 | – | 1,561 |
| 57 | Node | `streamparser-json` | 7,854 | – | – | 16.5 | – | 1,701 |
| 58 | Deno | `streamparser-json` | 10,858 | 12,041 | – | 16.5 | – | 1,701 |
| 59 | JVM | `simdjson-java-schema` | 40,835 | 63,935 | 1,959.8 | 25.8 | 1,933.9 | 1,383 |

Objects created are allocations per parse. Rust, C++ and Go count every one. The JVM counts
every `new` in the bytecode, before the JIT removes some, so its created and garbage
numbers are upper bounds. Node and Deno count them only for `JSON.parse` and simdjson.
Bun and Python can't count them, shown as –.

Objects kept are what each parsed event holds alive. Garbage is created minus kept. Each
runtime counts objects its own way: C++ keeps short strings inline, Go packs small strings
together, and a JVM `String` is two objects. Compare object counts within a runtime, not
across runtimes.

Four libraries failed verification and are not ranked: cJSON, ojg (Go),
`@jsonjoy.com/json-pack` and the `simdjson` npm addon.

## Run

```bash
./run.sh            # all installed toolchains, ~3 h
./run.sh --quick    # smoke test
./run.sh --only jvm # one harness; QUARTZ_DIR=../quartz for an unreleased Quartz,
                    # BENCH_JAVA_HOME=<jdk> to pick the JDK
```
