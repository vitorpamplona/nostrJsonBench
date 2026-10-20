# nostrJsonBench

How fast can a JSON parser turn a Nostr event into an object? And how much memory does it
use?

We ran 72 libraries in six languages over the same 31k real events. Counting each
library's entry points and its DOM and borrowed modes, that is 138 configurations. Every
output is checked against a reference digest.

How it works: [HARNESS.md](HARNESS.md). Every table, including DOM, borrowed, per-workload
results and failures: [report.md](results/reference/2026-10-20/report.md).

## Results

Each row is one library, using its fastest path from what a WebSocket delivers (a JVM
`String`, a Rust `&str`, …) to an event with owned strings.

Measured on the `all` workload, 2026-10-20, a 4-core Xeon, single thread.

What the memory columns mean:

- **alloc**: bytes one parse allocates, garbage and the event together.
- **allocs**: the number of allocations, where the runtime can count them.
- **retained**: bytes each parsed event keeps alive.

Rust, C++ and Go count allocations. The JVM and Deno report bytes only. Node, Bun and
Python report neither, shown as –.

| # | runtime | parser | ns/event | alloc B/event | allocs/event | retained B/event |
|---:|---|---|---:|---:|---:|---:|
| 1 | C/C++ | `simdjson-ondemand-typed` | 635 | 1,249 | 14.4 | 1,288 |
| 2 | Rust | `sonic_rs-typed` | 677 | 1,677 | 15.7 | 1,281 |
| 3 | C/C++ | `glaze-typed` | 721 | 1,328 | 15.0 | 1,408 |
| 4 | Rust | `jiter-typed` | 726 | 1,644 | 15.5 | 1,281 |
| 5 | Rust | `serde_json-typed` | 736 | 1,644 | 15.5 | 1,281 |
| 6 | Rust | `simd_json_derive-typed` | 737 | 2,705 | 15.3 | 1,109 |
| 7 | Go | `sonic-typed` | 738 | 1,529 | 8.1 | 1,431 |
| 8 | C/C++ | `yyjson-typed` | 770 | 4,323 | 12.1 | 1,262 |
| 9 | Rust | `simd_json-typed` | 776 | 2,705 | 15.3 | 1,109 |
| 10 | Rust | `simdjson_rust-typed` | 803 | 973 | 14.0 | 1,109 |
| 11 | JVM | `jsoniter-scala` | 836 | 2,772 | – | 1,381 |
| 12 | JVM | `fastjson2-reflect-string` | 905 | 3,701 | – | 1,389 |
| 13 | Go | `goccy-typed` | 938 | 1,231 | 5.1 | 1,201 |
| 14 | Deno | `json-parse-string` | 988 | 1,229 | – | 1,229 |
| 15 | C/C++ | `rapidjson-sax-typed` | 1,008 | 1,249 | 14.4 | 1,289 |
| 16 | Go | `fiatjaf-nostr-typed` | 1,041 | 1,169 | 10.0 | 1,140 |
| 17 | Python | `msgspec-typed` | 1,054 | – | – | 1,712 |
| 18 | Go | `jx-typed` | 1,111 | 1,689 | 14.4 | 1,193 |
| 19 | Go | `go-nostr-typed` | 1,134 | 1,338 | 13.0 | 1,310 |
| 20 | Node | `json-parse-string` | 1,135 | – | – | 1,234 |
| 21 | Python | `orjson` | 1,136 | – | – | 1,840 |
| 22 | Go | `fastjson-typed` | 1,140 | 1,058 | 12.8 | 1,058 |
| 23 | Rust | `miniserde-typed` | 1,146 | 2,162 | 21.2 | 1,281 |
| 24 | Go | `easyjson-typed` | 1,161 | 1,223 | 13.5 | 1,168 |
| 25 | Bun | `bun-jsonl-string` | 1,178 | – | – | 1,031 |
| 26 | Bun | `json-parse-string` | 1,245 | – | – | 1,031 |
| 27 | Python | `pysimdjson-recursive` | 1,289 | – | – | 2,074 |
| 28 | Python | `jiter` | 1,325 | – | – | 1,604 |
| 29 | JVM | `jackson-streaming-bytes` | 1,338 | 3,282 | – | 1,370 |
| 30 | JVM | `jsoniter-iterator` | 1,345 | 2,886 | – | 1,381 |
| 31 | JVM | `dsljson-reflect` | 1,354 | 2,716 | – | 1,381 |
| 32 | Rust | `hifijson-serde-typed` | 1,413 | 1,655 | 15.7 | 1,281 |
| 33 | Python | `yyjson` | 1,453 | – | – | 2,089 |
| 34 | C/C++ | `boostjson-typed` | 1,474 | 2,438 | 28.3 | 1,260 |
| 35 | JVM | `quartz-jackson` | 1,620 | 2,332 | – | 1,376 |
| 36 | JVM | `quartz-kotlinx` | 1,643 | 4,476 | – | 1,376 |
| 37 | Go | `jsoniter-typed-compat` | 1,771 | 1,717 | 19.0 | 1,080 |
| 38 | JVM | `kotlinx-typed` | 1,871 | 2,943 | – | 1,370 |
| 39 | JVM | `moshi-streaming` | 1,936 | 3,067 | – | 1,370 |
| 40 | Python | `ujson` | 1,944 | – | – | 2,129 |
| 41 | Python | `python-rapidjson` | 2,020 | – | – | 2,127 |
| 42 | Go | `jsonv2-typed` | 2,248 | 1,242 | 13.7 | 926 |
| 43 | Go | `segmentio-typed` | 2,368 | 1,728 | 14.0 | 1,578 |
| 44 | Rust | `nanoserde-typed` | 2,369 | 2,310 | 39.4 | 1,497 |
| 45 | Bun | `simdjson-parse` | 2,489 | – | – | 1,260 |
| 46 | JVM | `parsson-streaming` | 2,558 | 2,039 | – | 1,381 |
| 47 | Go | `jsonparser-typed` | 2,731 | 1,419 | 13.4 | 1,191 |
| 48 | Python | `json-stdlib` | 2,761 | – | – | 2,127 |
| 49 | Python | `simplejson` | 3,073 | – | – | 2,127 |
| 50 | Deno | `simdjson-parse` | 3,228 | 1,709 | – | 1,701 |
| 51 | JVM | `gson-streaming` | 3,309 | 5,127 | – | 1,381 |
| 52 | Rust | `struson-typed` | 3,464 | 2,181 | 26.0 | 1,408 |
| 53 | C/C++ | `nlohmann-sax-typed` | 5,205 | 3,964 | 38.2 | 1,803 |
| 54 | Go | `stdlib-typed` | 5,391 | 1,660 | 23.4 | 1,082 |
| 55 | Go | `gjson-typed` | 5,504 | 1,832 | 12.3 | 1,337 |
| 56 | Bun | `streamparser-json` | 7,980 | – | – | 1,561 |
| 57 | Node | `streamparser-json` | 8,257 | – | – | 1,701 |
| 58 | Deno | `streamparser-json` | 11,482 | 12,047 | – | 1,701 |
| 59 | JVM | `simdjson-java-schema` | 41,481 | 62,759 | – | 1,383 |

Quartz (`quartz-jackson`) spends almost all its time in Jackson's tokenizer. A faster
tokenizer is where the remaining gain is: fastjson2, or Jackson fed bytes.

Four libraries failed verification and are not ranked: cJSON, ojg (Go),
`@jsonjoy.com/json-pack` and the `simdjson` npm addon.

## Run

```bash
./run.sh            # all installed toolchains, ~1 h
./run.sh --quick    # smoke test
./run.sh --only jvm # one harness; QUARTZ_DIR=../amethyst for an unreleased Quartz
```
