# nostrJsonBench report

Corpus `corpus/nostr-2024-startup.ndjson.gz` — 31,161 unique events, fingerprint `f995f42b68b5386e`, raw bytes as received.

| harness | runtime | host | settings |
|---|---|---|---|
| bun | Bun 1.4.3 (JavaScriptCore, WebKit 0c06faadf65b) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| cpp | c++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, -O3 -march=native, C++23, single-threaded | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| deno | Deno 2.9.6 (V8 15.0.245.2-rusty) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| go | go1.24.7 linux/amd64, GOAMD64=v1, GOGC=100 (default), GOMEMLIMIT=off (default), GOMAXPROCS=4, CGO_ENABLED=1, one child process per parser | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| jvm | Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu, GC G1, -Xms2g -Xmx2g -XX:+UseG1GC --add-modules=jdk.incubator.vector, one JVM per parser, Quartz local amethyst@df1faea1 | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| node | Node.js 22.22.0 (V8 12.4.254.21-node.33) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| python | CPython 3.13.16 (CPython, GCC 13.3.0) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| rust | rustc 1.97.0 (2d8144b78 2026-07-07), -C target-cpu=native, release (lto=fat, codegen-units=1) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |

MB/s is input bytes per second of median round (higher is better). Single thread. `× quartz` is speed relative to Amethyst's production parser (`jvm/quartz-jackson`, Jackson + Quartz's EventDeserializer) on the same workload. Alloc is heap bytes and allocation count per parsed event; GC is collections / total GC time during measurement.

## Workloads

| workload | events | MB | avg bytes | avg tags | what |
|---|---:|---:|---:|---:|---|
| all | 31,161 | 27.00 | 866 | 2.7 | every event in the corpus: the real kind mix a client downloads |
| notes | 3,568 | 3.23 | 904 | 4.7 | kind 1 text notes: short UTF-8 content, a few e/p tags |
| reactions | 2,259 | 1.26 | 559 | 3.1 | kind 7 reactions: tiny events, 3 tags, 1-char content |
| dms | 9,600 | 6.15 | 640 | 1.3 | kind 4 NIP-04 DMs: base64 ciphertext content |
| giftwraps | 1,614 | 2.74 | 1701 | 1.2 | kind 1059 NIP-59 gift wraps: one long base64 NIP-44 payload |
| metadata | 268 | 0.23 | 876 | 0.3 | kind 0 profiles: content is an escaped JSON object |
| zaps | 1,270 | 2.71 | 2134 | 5.3 | kind 9735 zap receipts: escaped JSON zap request inside a tag |
| lists | 40 | 0.34 | 8529 | 25.2 | contact/mute/relay/bookmark lists (3, 1000x, 3000x): tag-heavy |

## Typed: JSON → the language's own Event with owned strings (what a client keeps) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-ondemand-typed` | string→bytes-padded | 1,363 | 635 | 2.55× | 1,249 | 14.4 | – | 1,288 | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-typed` | string | 1,279 | 677 | 2.39× | 1,677 | 15.7 | – | 1,281 | Apache-2.0 |
| 3 | C/C++ | `glaze-typed` | string | 1,201 | 721 | 2.25× | 1,328 | 15.0 | – | 1,408 | MIT |
| 4 | Rust | `jiter-typed` | string | 1,194 | 726 | 2.23× | 1,644 | 15.5 | – | 1,281 | MIT |
| 5 | Rust | `serde_json-typed` | string | 1,177 | 736 | 2.20× | 1,644 | 15.5 | – | 1,281 | MIT OR Apache-2.0 |
| 6 | Rust | `simd_json_derive-typed` | bytes-copy | 1,176 | 737 | 2.20× | 2,705 | 15.3 | – | 1,109 | Apache-2.0 OR MIT |
| 7 | Go | `sonic-typed` | bytes→string | 1,174 | 738 | 2.19× | 1,529 | 8.1 | 144 / 14 ms | 1,431 | Apache-2.0 |
| 8 | C/C++ | `yyjson-typed` | string | 1,124 | 770 | 2.10× | 4,323 | 12.1 | – | 1,262 | MIT |
| 9 | Rust | `simd_json-typed` | bytes-copy | 1,117 | 776 | 2.09× | 2,705 | 15.3 | – | 1,109 | Apache-2.0 OR MIT |
| 10 | Rust | `simdjson_rust-typed` | bytes-copy | 1,079 | 803 | 2.02× | 973 | 14.0 | – | 1,109 | Apache-2.0 |
| 11 | Kotlin/Java (JVM) | `jsoniter-scala` | string (readFromString) | 1,036 | 836 | 1.94× | 2,772 | – | 5 / 12 ms | 1,381 | MIT |
| 12 | Kotlin/Java (JVM) | `fastjson2-reflect-string` | string | 957 | 905 | 1.79× | 3,701 | – | 6 / 13 ms | 1,389 | Apache-2.0 |
| 13 | Go | `goccy-typed` | bytes-copy | 924 | 938 | 1.73× | 1,231 | 5.1 | 91 / 8 ms | 1,201 | MIT |
| 14 | JavaScript (Deno) | `json-parse-string` | string | 877 | 988 | 1.64× | 1,229 | – | – | 1,229 | BSD-3-Clause |
| 15 | C/C++ | `rapidjson-sax-typed` | string | 860 | 1,008 | 1.61× | 1,249 | 14.4 | – | 1,289 | MIT |
| 16 | Go | `fiatjaf-nostr-typed` | bytes | 832 | 1,041 | 1.56× | 1,169 | 10.0 | 81 / 7 ms | 1,140 | Unlicense |
| 17 | Python | `msgspec-typed` | string | 822 | 1,054 | 1.54× | – | – | 0 / 0 ms | 1,712 | BSD-3-Clause |
| 18 | Go | `jx-typed` | bytes | 780 | 1,111 | 1.46× | 1,689 | 14.4 | 110 / 9 ms | 1,193 | MIT |
| 19 | Go | `go-nostr-typed` | bytes | 764 | 1,134 | 1.43× | 1,338 | 13.0 | 86 / 8 ms | 1,310 | MIT |
| 20 | JavaScript (Node) | `json-parse-string` | string | 763 | 1,135 | 1.43× | – | – | 128 / 32 ms | 1,234 | BSD-3-Clause |
| 21 | Python | `orjson` | string | 762 | 1,136 | 1.43× | – | – | 0 / 0 ms | 1,840 | MPL-2.0 AND (Apache-2.0 OR MIT) |
| 22 | Go | `fastjson-typed` | bytes-copy | 760 | 1,140 | 1.42× | 1,058 | 12.8 | 66 / 5 ms | 1,058 | MIT |
| 23 | Rust | `miniserde-typed` | string | 756 | 1,146 | 1.41× | 2,162 | 21.2 | – | 1,281 | MIT OR Apache-2.0 |
| 24 | Go | `easyjson-typed` | bytes | 746 | 1,161 | 1.40× | 1,223 | 13.5 | 75 / 8 ms | 1,168 | MIT |
| 25 | JavaScript (Bun) | `bun-jsonl-string` | string | 735 | 1,178 | 1.37× | – | – | – | 1,031 | MIT |
| 26 | JavaScript (Bun) | `json-parse-string` | string | 696 | 1,245 | 1.30× | – | – | – | 1,031 | BSD-2-Clause |
| 27 | Python | `pysimdjson-recursive` | string | 672 | 1,289 | 1.26× | – | – | 0 / 0 ms | 2,074 | MIT |
| 28 | Python | `jiter` | string→bytes | 654 | 1,325 | 1.22× | – | – | 0 / 0 ms | 1,604 | MIT |
| 29 | Kotlin/Java (JVM) | `jackson-streaming-bytes` | string→bytes | 647 | 1,338 | 1.21× | 3,282 | – | 2 / 5 ms | 1,370 | Apache-2.0 |
| 30 | Kotlin/Java (JVM) | `jsoniter-iterator` | string→bytes | 644 | 1,345 | 1.20× | 2,886 | – | 2 / 5 ms | 1,381 | MIT |
| 31 | Kotlin/Java (JVM) | `dsljson-reflect` | string→bytes | 640 | 1,354 | 1.20× | 2,716 | – | 2 / 6 ms | 1,381 | BSD-3-Clause |
| 32 | Rust | `hifijson-serde-typed` | string | 613 | 1,413 | 1.15× | 1,655 | 15.7 | – | 1,281 | MIT |
| 33 | Python | `yyjson` | string→bytes | 596 | 1,453 | 1.11× | – | – | 0 / 0 ms | 2,089 | MIT |
| 34 | C/C++ | `boostjson-typed` | string | 588 | 1,474 | 1.10× | 2,438 | 28.3 | – | 1,260 | BSL-1.0 |
| 35 | Kotlin/Java (JVM) | `quartz-jackson` | string | 535 | 1,620 | 1.00× | 2,332 | – | 0 / 0 ms | 1,376 | Apache-2.0 AND MIT |
| 36 | Kotlin/Java (JVM) | `quartz-kotlinx` | string | 527 | 1,643 | 0.99× | 4,476 | – | 4 / 8 ms | 1,376 | Apache-2.0 AND MIT |
| 37 | Go | `jsoniter-typed-compat` | bytes | 489 | 1,771 | 0.91× | 1,717 | 19.0 | 69 / 6 ms | 1,080 | MIT |
| 38 | Kotlin/Java (JVM) | `kotlinx-typed` | string | 463 | 1,871 | 0.87× | 2,943 | – | 1 / 3 ms | 1,370 | Apache-2.0 |
| 39 | Kotlin/Java (JVM) | `moshi-streaming` | string (okio writeUtf8) | 447 | 1,936 | 0.84× | 3,067 | – | 2 / 9 ms | 1,370 | Apache-2.0 |
| 40 | Python | `ujson` | string | 446 | 1,944 | 0.83× | – | – | 0 / 0 ms | 2,129 | BSD-3-Clause AND TCL |
| 41 | Python | `python-rapidjson` | string | 429 | 2,020 | 0.80× | – | – | 0 / 0 ms | 2,127 | MIT |
| 42 | Go | `jsonv2-typed` | bytes | 385 | 2,248 | 0.72× | 1,242 | 13.7 | 39 / 3 ms | 926 | BSD-3-Clause |
| 43 | Go | `segmentio-typed` | bytes | 366 | 2,368 | 0.68× | 1,728 | 14.0 | 54 / 6 ms | 1,578 | MIT |
| 44 | Rust | `nanoserde-typed` | string | 366 | 2,369 | 0.68× | 2,310 | 39.4 | – | 1,497 | MIT OR Apache-2.0 |
| 45 | JavaScript (Bun) | `simdjson-parse` | string | 348 | 2,489 | 0.65× | – | – | – | 1,260 | Apache-2.0 |
| 46 | Kotlin/Java (JVM) | `parsson-streaming` | string | 339 | 2,558 | 0.63× | 2,039 | – | 1 / 3 ms | 1,381 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| 47 | Go | `jsonparser-typed` | bytes | 317 | 2,731 | 0.59× | 1,419 | 13.4 | 38 / 4 ms | 1,191 | MIT |
| 48 | Python | `json-stdlib` | string | 314 | 2,761 | 0.59× | – | – | 0 / 0 ms | 2,127 | PSF-2.0 |
| 49 | Python | `simplejson` | string | 282 | 3,073 | 0.53× | – | – | 0 / 0 ms | 2,127 | MIT OR AFL-2.1 |
| 50 | JavaScript (Deno) | `simdjson-parse` | string | 268 | 3,228 | 0.50× | 1,709 | – | – | 1,701 | Apache-2.0 |
| 51 | Kotlin/Java (JVM) | `gson-streaming` | string | 262 | 3,309 | 0.49× | 5,127 | – | 1 / 3 ms | 1,381 | Apache-2.0 |
| 52 | Rust | `struson-typed` | string | 250 | 3,464 | 0.47× | 2,181 | 26.0 | – | 1,408 | MIT OR Apache-2.0 |
| 53 | C/C++ | `nlohmann-sax-typed` | string | 166 | 5,205 | 0.31× | 3,964 | 38.2 | – | 1,803 | MIT |
| 54 | Go | `stdlib-typed` | bytes | 161 | 5,391 | 0.30× | 1,660 | 23.4 | 22 / 2 ms | 1,082 | BSD-3-Clause |
| 55 | Go | `gjson-typed` | bytes | 157 | 5,504 | 0.29× | 1,832 | 12.3 | 25 / 2 ms | 1,337 | MIT |
| 56 | JavaScript (Bun) | `streamparser-json` | string | 109 | 7,980 | 0.20× | – | – | – | 1,561 | MIT |
| 57 | JavaScript (Node) | `streamparser-json` | string | 105 | 8,257 | 0.20× | – | – | 178 / 55 ms | 1,701 | MIT |
| 58 | JavaScript (Deno) | `streamparser-json` | string | 75 | 11,482 | 0.14× | 12,047 | – | – | 1,701 | MIT |
| 59 | Kotlin/Java (JVM) | `simdjson-java-schema` | string→bytes-padded | 21 | 41,481 | 0.04× | 62,759 | – | 7 / 17 ms | 1,383 | Apache-2.0 |

## Borrowed: Event whose strings point into the input buffer — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-ondemand-borrowed` | string→bytes-padded | 2,005 | 432 | 3.75× | 320 | 8.0 | – | – | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-borrowed` | string | 1,747 | 496 | 3.27× | 1,102 | 5.7 | – | 705 | Apache-2.0 |
| 3 | Rust | `simd_json_derive-borrowed` | bytes-copy | 1,535 | 564 | 2.87× | 1,906 | 4.9 | – | – | Apache-2.0 OR MIT |
| 4 | C/C++ | `yyjson-borrowed` | string | 1,519 | 570 | 2.84× | 3,476 | 5.7 | – | 3,636 | MIT |
| 5 | Rust | `simd_json-borrowed-struct` | bytes-copy | 1,509 | 574 | 2.82× | 1,906 | 4.9 | – | – | Apache-2.0 OR MIT |
| 6 | Rust | `serde_json-borrowed` | string | 1,460 | 594 | 2.73× | 1,068 | 5.5 | – | 705 | MIT OR Apache-2.0 |
| 7 | Rust | `simdjson_rust-borrowed` | bytes-copy | 1,406 | 616 | 2.63× | 173 | 3.7 | – | – | Apache-2.0 |
| 8 | Rust | `jiter-serde-borrowed` | string | 1,349 | 642 | 2.52× | 1,068 | 5.5 | – | 705 | MIT |
| 9 | C/C++ | `glaze-borrowed` | string | 1,344 | 644 | 2.51× | 1,051 | 12.0 | – | 1,946 | MIT |
| 10 | Go | `segmentio-borrowed` | bytes | 411 | 2,108 | 0.77× | 1,140 | 6.2 | 39 / 5 ms | 1,907 | MIT |

## DOM: the library's generic tree (not converted into an Event) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-dom` | string→bytes-padded | 3,199 | 271 | 5.98× | 0 | 0.0 | – | – | Apache-2.0 OR MIT |
| 2 | C/C++ | `yyjson-dom-pool` | string | 2,246 | 386 | 4.20× | 0 | 0.0 | – | – | MIT |
| 3 | Rust | `sonic_rs-value` | string | 2,037 | 425 | 3.81× | 1,857 | 3.4 | – | 1,873 | Apache-2.0 |
| 4 | Go | `fastjson-dom` | bytes-copy | 1,965 | 441 | 3.67× | 0 | 0.0 | 0 / 0 ms | – | MIT |
| 5 | Rust | `simd_json-tape` | bytes-copy | 1,883 | 460 | 3.52× | 0 | 0.0 | – | – | Apache-2.0 OR MIT |
| 6 | Python | `pysimdjson` | string | 1,679 | 516 | 3.14× | – | – | 0 / 0 ms | – | MIT |
| 7 | Python | `cysimdjson` | string | 1,586 | 546 | 2.97× | – | – | 0 / 0 ms | – | Apache-2.0 |
| 8 | C/C++ | `rapidjson-insitu` | bytes-copy | 1,534 | 565 | 2.87× | 66,668 | 6.0 | – | – | MIT |
| 9 | C/C++ | `boostjson-dom-pool` | string | 1,456 | 595 | 2.72× | 0 | 0.0 | – | – | BSL-1.0 |
| 10 | C/C++ | `sajson-dom` | string | 1,345 | 644 | 2.51× | 11,116 | 3.0 | – | 9,284 | MIT |
| 11 | Rust | `jiter-value-scratch` | string | 1,286 | 674 | 2.40× | 1,053 | 9.6 | – | 1,085 | MIT |
| 12 | Rust | `serde_json_borrow-value` | string | 1,129 | 767 | 2.11× | 1,889 | 7.5 | – | 1,183 | MIT |
| 13 | C/C++ | `jsmn-tokens` | string | 1,107 | 782 | 2.07× | 0 | 0.0 | – | – | MIT |
| 14 | Rust | `simdjson_rust-dom` | bytes-copy | 1,025 | 846 | 1.92× | 1,172 | 6.0 | – | – | Apache-2.0 |
| 15 | Go | `simdjson-go-dom` | bytes | 820 | 1,056 | 1.53× | 16 | 1.0 | 1 / 1 ms | – | Apache-2.0 |
| 16 | Kotlin/Java (JVM) | `simdjson-java-dom` | string→bytes-padded | 805 | 1,076 | 1.50× | 2,390 | – | 2 / 6 ms | – | Apache-2.0 |
| 17 | Rust | `serde_json-value` | string | 788 | 1,099 | 1.47× | 2,461 | 23.5 | – | 1,978 | MIT OR Apache-2.0 |
| 18 | Kotlin/Java (JVM) | `fastjson2-dom` | string→bytes | 757 | 1,144 | 1.42× | 3,549 | – | 4 / 10 ms | 1,893 | Apache-2.0 |
| 19 | Rust | `json-dom` | string | 687 | 1,260 | 1.29× | 3,993 | 16.4 | – | 2,344 | MIT OR Apache-2.0 |
| 20 | Kotlin/Java (JVM) | `jackson-tree` | string→bytes | 625 | 1,386 | 1.17× | 4,316 | – | 4 / 10 ms | 2,245 | Apache-2.0 |
| 21 | Rust | `hifijson-value` | string | 615 | 1,408 | 1.15× | 1,731 | 7.4 | – | 1,259 | MIT |
| 22 | Go | `sonic-ast` | bytes→string | 581 | 1,491 | 1.09× | 2,823 | 9.9 | 135 / 11 ms | 2,153 | Apache-2.0 |
| 23 | C/C++ | `glaze-dom` | string | 536 | 1,617 | 1.00× | 2,404 | 18.0 | – | 1,973 | MIT |
| 24 | Kotlin/Java (JVM) | `kotlinx-element` | string | 479 | 1,807 | 0.90× | 3,877 | – | 2 / 6 ms | 2,774 | Apache-2.0 |
| 25 | Kotlin/Java (JVM) | `jsonsmart-dom` | string | 467 | 1,853 | 0.87× | 2,463 | – | 1 / 3 ms | 2,237 | Apache-2.0 |
| 26 | Kotlin/Java (JVM) | `minimaljson-dom` | string | 351 | 2,467 | 0.66× | 4,841 | – | 2 / 5 ms | 2,491 | MIT |
| 27 | Kotlin/Java (JVM) | `gson-tree` | string | 300 | 2,886 | 0.56× | 6,227 | – | 2 / 5 ms | 2,731 | Apache-2.0 |
| 28 | C/C++ | `jsonc-dom` | string | 232 | 3,735 | 0.43× | 4,021 | 46.6 | – | 2,928 | MIT |
| 29 | Rust | `tinyjson-dom` | string | 174 | 4,986 | 0.32× | 4,076 | 52.7 | – | 2,591 | MIT |
| 30 | C/C++ | `nlohmann-dom` | string | 154 | 5,633 | 0.29× | 4,813 | 57.1 | – | 2,199 | MIT |
| 31 | Go | `stdlib-map` | bytes | 148 | 5,838 | 0.28× | 2,249 | 55.9 | 28 / 2 ms | 1,605 | BSD-3-Clause |
| 32 | C/C++ | `jansson-dom` | string | 96 | 9,042 | 0.18× | 3,452 | 52.7 | – | 2,595 | MIT |
| 33 | Kotlin/Java (JVM) | `orgjson-dom` | string | 49 | 17,714 | 0.09× | 6,026 | – | 0 / 0 ms | 2,331 | LicenseRef-Public-Domain |
| 34 | JavaScript (Deno) | `simdjson-lazy` | string | 1 | 1,385,613 | 0.00× | 1,780 | – | – | – | Apache-2.0 |

## Baselines (not parsers) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | Rust | `std-from_utf8` | bytes | 13,371 | 65 | 25.00× | 0 | 0.0 | – | – | MIT OR Apache-2.0 |
| 2 | JavaScript (Bun) | `baseline-utf8-encodeinto` | string→bytes | 8,816 | 98 | 16.48× | – | – | – | – | BSD-2-Clause |
| 3 | Python | `baseline-utf8-encode` | string→bytes | 6,214 | 139 | 11.62× | – | – | 0 / 0 ms | – | PSF-2.0 |
| 4 | JavaScript (Node) | `baseline-utf8-encodeinto` | string→bytes | 5,798 | 149 | 10.84× | – | – | 76 / 25 ms | – | BSD-3-Clause |
| 5 | Go | `go-bytes-to-string` | bytes→string | 5,121 | 169 | 9.57× | 917 | 1.0 | 378 / 35 ms | 917 | BSD-3-Clause |
| 6 | JavaScript (Deno) | `baseline-utf8-encodeinto` | string→bytes | 4,533 | 191 | 8.48× | 120 | – | – | – | BSD-3-Clause |
| 7 | Kotlin/Java (JVM) | `baseline-utf8-decode` | bytes | 4,530 | 191 | 8.47× | 1,204 | – | 9 / 24 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 8 | Kotlin/Java (JVM) | `baseline-utf8-encode` | string | 4,495 | 193 | 8.40× | 1,118 | – | 8 / 20 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 9 | JavaScript (Bun) | `baseline-utf8-encode` | string→bytes | 3,851 | 225 | 7.20× | – | – | – | – | BSD-2-Clause |
| 10 | JavaScript (Node) | `baseline-buffer-tostring` | bytes | 3,367 | 257 | 6.29× | – | – | 445 / 95 ms | – | BSD-3-Clause |
| 11 | JavaScript (Bun) | `baseline-buffer-tostring` | bytes | 3,018 | 287 | 5.64× | – | – | – | – | BSD-2-Clause |
| 12 | JavaScript (Deno) | `baseline-buffer-tostring` | bytes | 2,182 | 397 | 4.08× | 956 | – | – | – | BSD-3-Clause |
| 13 | JavaScript (Node) | `baseline-utf8-encode` | string→bytes | 612 | 1,415 | 1.14× | – | – | 117 / 597 ms | – | BSD-3-Clause |
| 14 | JavaScript (Deno) | `baseline-utf8-encode` | string→bytes | 154 | 5,621 | 0.29× | 201 | – | – | – | BSD-3-Clause |

## Every workload (MB/s)

Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative to `quartz-jackson` — one number per parser that doesn't let the big `all` workload dominate.

| language | parser | mode | all | notes | reactions | dms | giftwraps | metadata | zaps | lists | geomean × quartz |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C/C++ | `simdjson-dom` | dom | 3,199 | 2,160 | 2,763 | 3,625 | 6,371 | 2,293 | 2,671 | 6,677 | 5.33× |
| Rust | `sonic_rs-value` | dom | 2,037 | 1,828 | 1,969 | 2,361 | 4,450 | 1,476 | 2,363 | 6,992 | 4.02× |
| Go | `fastjson-dom` | dom | 1,965 | 1,828 | 1,681 | 2,439 | 5,202 | 1,676 | 2,121 | 5,291 | 3.88× |
| C/C++ | `yyjson-dom-pool` | dom | 2,246 | 1,625 | 2,012 | 2,370 | 2,872 | 2,372 | 2,548 | 3,430 | 3.73× |
| C/C++ | `simdjson-ondemand-borrowed` | borrowed | 2,005 | 1,242 | 1,368 | 2,680 | 5,258 | 2,259 | 1,973 | 3,116 | 3.53× |
| Rust | `simd_json-tape` | dom | 1,883 | 1,755 | 1,710 | 2,052 | 3,589 | 1,751 | 2,117 | 4,072 | 3.50× |
| Rust | `sonic_rs-borrowed` | borrowed | 1,747 | 1,440 | 1,754 | 2,841 | 4,721 | 1,240 | 1,727 | 4,226 | 3.43× |
| Python | `pysimdjson` | dom | 1,679 | 1,428 | 1,550 | 1,997 | 3,512 | 1,159 | 2,109 | 5,918 | 3.28× |
| Python | `cysimdjson` | dom | 1,586 | 1,350 | 1,398 | 1,790 | 3,377 | 1,141 | 2,072 | 5,650 | 3.11× |
| C/C++ | `rapidjson-insitu` | dom | 1,534 | 1,178 | 1,203 | 1,555 | 3,746 | 1,312 | 2,067 | 5,409 | 3.01× |
| Rust | `simd_json_derive-borrowed` | borrowed | 1,535 | 1,273 | 1,360 | 1,788 | 3,353 | 1,590 | 1,892 | 2,962 | 2.91× |
| Rust | `simd_json-borrowed-struct` | borrowed | 1,509 | 1,250 | 1,335 | 1,773 | 3,196 | 1,599 | 1,867 | 2,934 | 2.86× |
| Rust | `serde_json-borrowed` | borrowed | 1,460 | 1,221 | 1,485 | 2,238 | 3,897 | 959 | 1,468 | 3,426 | 2.82× |
| Rust | `simdjson_rust-borrowed` | borrowed | 1,406 | 1,160 | 1,295 | 1,744 | 3,378 | 1,456 | 1,777 | 3,146 | 2.79× |
| C/C++ | `boostjson-dom-pool` | dom | 1,456 | 1,169 | 1,154 | 1,934 | 4,077 | 921 | 1,435 | 4,074 | 2.72× |
| Rust | `jiter-serde-borrowed` | borrowed | 1,349 | 1,030 | 1,432 | 2,205 | 4,378 | 781 | 1,487 | 3,687 | 2.72× |
| C/C++ | `yyjson-borrowed` | borrowed | 1,519 | 1,083 | 1,254 | 1,650 | 2,435 | 1,805 | 2,013 | 2,369 | 2.67× |
| Rust | `sonic_rs-typed` | typed | 1,279 | 977 | 1,286 | 2,228 | 3,736 | 1,192 | 1,467 | 2,307 | 2.58× |
| C/C++ | `simdjson-ondemand-typed` | typed | 1,363 | 859 | 991 | 1,695 | 3,460 | 1,777 | 1,574 | 2,263 | 2.51× |
| C/C++ | `glaze-borrowed` | borrowed | 1,344 | 968 | 1,203 | 2,115 | 3,108 | 1,512 | 1,375 | 1,782 | 2.47× |
| Rust | `jiter-value-scratch` | dom | 1,286 | 1,043 | 1,060 | 1,606 | 3,446 | 1,030 | 1,561 | 2,973 | 2.46× |
| Rust | `jiter-typed` | typed | 1,194 | 913 | 1,394 | 2,099 | 3,866 | 781 | 1,453 | 2,500 | 2.44× |
| Rust | `simd_json_derive-typed` | typed | 1,176 | 878 | 1,057 | 1,515 | 2,717 | 1,486 | 1,644 | 1,958 | 2.30× |
| Rust | `serde_json-typed` | typed | 1,177 | 944 | 1,191 | 1,823 | 3,081 | 903 | 1,316 | 2,142 | 2.26× |
| Rust | `serde_json_borrow-value` | dom | 1,129 | 960 | 1,031 | 1,634 | 3,144 | 887 | 1,238 | 2,539 | 2.21× |
| C/C++ | `sajson-dom` | dom | 1,345 | 1,039 | 1,197 | 1,435 | 1,927 | 1,026 | 1,261 | 2,647 | 2.21× |
| Rust | `simd_json-typed` | typed | 1,117 | 857 | 962 | 1,396 | 2,675 | 1,438 | 1,542 | 1,947 | 2.19× |
| Rust | `simdjson_rust-typed` | typed | 1,079 | 856 | 1,007 | 1,398 | 2,750 | 1,315 | 1,526 | 1,850 | 2.16× |
| C/C++ | `glaze-typed` | typed | 1,201 | 811 | 908 | 1,613 | 2,703 | 1,304 | 1,191 | 1,815 | 2.11× |
| Rust | `simdjson_rust-dom` | dom | 1,025 | 801 | 842 | 1,238 | 2,643 | 1,224 | 1,411 | 2,207 | 2.05× |
| Kotlin/Java (JVM) | `jsoniter-scala` | typed | 1,036 | 870 | 1,411 | 1,836 | 1,850 | 653 | 863 | 2,058 | 1.91× |
| Kotlin/Java (JVM) | `fastjson2-reflect-string` | typed | 957 | 845 | 1,041 | 1,441 | 2,095 | 579 | 839 | 3,342 | 1.87× |
| C/C++ | `yyjson-typed` | typed | 1,124 | 549 | 590 | 1,244 | 1,967 | 1,493 | 1,525 | 1,814 | 1.84× |
| C/C++ | `jsmn-tokens` | dom | 1,107 | 870 | 874 | 1,164 | 1,572 | 1,089 | 1,280 | 1,141 | 1.75× |
| JavaScript (Bun) | `json-parse-string` | typed | 696 | 596 | 960 | 1,399 | 2,290 | 671 | 1,008 | 2,467 | 1.72× |
| JavaScript (Deno) | `json-parse-string` | typed | 877 | 727 | 797 | 1,157 | 2,256 | 589 | 1,019 | 2,633 | 1.72× |
| Go | `sonic-typed` | typed | 1,174 | 715 | 690 | 1,231 | 1,677 | 843 | 920 | 2,019 | 1.69× |
| JavaScript (Bun) | `bun-jsonl-string` | typed | 735 | 562 | 925 | 1,359 | 2,156 | 677 | 951 | 2,334 | 1.67× |
| C/C++ | `rapidjson-sax-typed` | typed | 860 | 630 | 710 | 1,069 | 2,077 | 917 | 1,062 | 1,648 | 1.63× |
| Kotlin/Java (JVM) | `simdjson-java-dom` | dom | 805 | 738 | 917 | 1,131 | 1,388 | 664 | 969 | 1,894 | 1.57× |
| Rust | `serde_json-value` | dom | 788 | 671 | 714 | 1,059 | 2,025 | 704 | 1,059 | 1,691 | 1.57× |
| Go | `simdjson-go-dom` | dom | 820 | 760 | 687 | 843 | 1,805 | 738 | 1,097 | 1,681 | 1.54× |
| Go | `goccy-typed` | typed | 924 | 685 | 628 | 1,073 | 1,516 | 845 | 936 | 1,687 | 1.54× |
| Python | `msgspec-typed` | typed | 822 | 608 | 752 | 1,196 | 1,797 | 526 | 1,068 | 1,867 | 1.53× |
| Kotlin/Java (JVM) | `fastjson2-dom` | dom | 757 | 655 | 983 | 1,389 | 1,550 | 454 | 629 | 2,305 | 1.49× |
| JavaScript (Node) | `json-parse-string` | typed | 763 | 652 | 646 | 919 | 1,657 | 703 | 1,038 | 1,854 | 1.49× |
| Python | `orjson` | typed | 762 | 593 | 633 | 813 | 1,398 | 665 | 1,218 | 1,929 | 1.44× |
| Rust | `miniserde-typed` | typed | 756 | 643 | 759 | 1,065 | 1,581 | 714 | 914 | 1,206 | 1.43× |
| Python | `jiter` | typed | 654 | 503 | 622 | 862 | 1,735 | 418 | 1,010 | 2,932 | 1.39× |
| Go | `fiatjaf-nostr-typed` | typed | 832 | 643 | 570 | 1,020 | 1,472 | 630 | 732 | 1,581 | 1.36× |
| Python | `pysimdjson-recursive` | typed | 672 | 524 | 516 | 732 | 1,476 | 559 | 1,040 | 2,308 | 1.32× |
| Go | `fastjson-typed` | typed | 760 | 583 | 512 | 894 | 1,357 | 616 | 721 | 1,723 | 1.29× |
| Go | `go-nostr-typed` | typed | 764 | 600 | 525 | 896 | 1,414 | 601 | 710 | 1,521 | 1.28× |
| Kotlin/Java (JVM) | `jackson-streaming-bytes` | typed | 647 | 587 | 658 | 859 | 1,213 | 593 | 824 | 1,341 | 1.25× |
| Go | `easyjson-typed` | typed | 746 | 565 | 498 | 894 | 1,400 | 591 | 681 | 1,506 | 1.24× |
| Rust | `json-dom` | dom | 687 | 586 | 646 | 881 | 1,179 | 599 | 761 | 1,170 | 1.23× |
| Kotlin/Java (JVM) | `jackson-tree` | dom | 625 | 596 | 630 | 800 | 1,102 | 583 | 843 | 1,268 | 1.21× |
| Go | `jx-typed` | typed | 780 | 540 | 589 | 970 | 1,208 | 513 | 592 | 1,333 | 1.20× |
| Kotlin/Java (JVM) | `dsljson-reflect` | typed | 640 | 597 | 837 | 1,022 | 1,055 | 464 | 569 | 1,246 | 1.19× |
| Python | `yyjson` | typed | 596 | 510 | 480 | 604 | 1,081 | 574 | 978 | 1,682 | 1.16× |
| C/C++ | `boostjson-typed` | typed | 588 | 437 | 479 | 671 | 1,542 | 612 | 822 | 1,280 | 1.14× |
| Kotlin/Java (JVM) | `jsoniter-iterator` | typed | 644 | 574 | 719 | 923 | 947 | 497 | 589 | 1,098 | 1.13× |
| Rust | `hifijson-value` | dom | 615 | 534 | 599 | 796 | 1,197 | 456 | 652 | 1,062 | 1.10× |
| Rust | `hifijson-serde-typed` | typed | 613 | 518 | 643 | 843 | 1,207 | 454 | 656 | 953 | 1.10× |
| Kotlin/Java (JVM) | `quartz-kotlinx` | typed | 527 | 489 | 506 | 697 | 1,147 | 421 | 605 | 1,612 | 1.06× |
| C/C++ | `glaze-dom` | dom | 536 | 423 | 394 | 643 | 1,322 | 740 | 731 | 1,001 | 1.05× |
| Kotlin/Java (JVM) | `quartz-jackson` | typed | 535 | 535 | 525 | 658 | 976 | 534 | 626 | 861 | 1.00× |
| Go | `sonic-ast` | dom | 581 | 415 | 300 | 465 | 830 | 435 | 834 | 1,470 | 0.93× |
| Kotlin/Java (JVM) | `jsonsmart-dom` | dom | 467 | 388 | 763 | 962 | 723 | 287 | 339 | 1,488 | 0.92× |
| C/C++ | `cjson-dom` | dom |  |  | 376 | 505 | 819 | 540 | 622 | 773 | 0.86× |
| Kotlin/Java (JVM) | `kotlinx-element` | dom | 479 | 449 | 478 | 570 | 701 | 404 | 571 | 740 | 0.84× |
| Python | `python-rapidjson` | typed | 429 | 369 | 342 | 451 | 802 | 398 | 690 | 1,159 | 0.83× |
| Kotlin/Java (JVM) | `moshi-streaming` | typed | 447 | 428 | 456 | 605 | 857 | 312 | 477 | 835 | 0.82× |
| Kotlin/Java (JVM) | `kotlinx-typed` | typed | 463 | 422 | 448 | 565 | 710 | 398 | 550 | 682 | 0.81× |
| Python | `ujson` | typed | 446 | 400 | 357 | 439 | 728 | 412 | 635 | 971 | 0.81× |
| Kotlin/Java (JVM) | `parsson-streaming` | typed | 339 | 444 | 476 | 605 | 806 | 381 | 436 | 862 | 0.81× |
| Go | `segmentio-borrowed` | borrowed | 411 | 297 | 372 | 748 | 1,086 | 248 | 322 | 1,398 | 0.79× |
| Go | `jsoniter-typed-compat` | typed | 489 | 346 | 392 | 639 | 681 | 359 | 354 | 792 | 0.75× |
| JavaScript (Bun) | `simdjson-parse` | typed | 348 | 309 | 313 | 401 | 878 | 430 | 582 | 816 | 0.74× |
| Go | `ojg-dom` | dom |  |  |  | 397 | 631 |  |  | 776 | 0.71× |
| Go | `jsonv2-typed` | typed | 385 | 295 | 288 | 483 | 713 | 310 | 357 | 793 | 0.66× |
| Go | `segmentio-typed` | typed | 366 | 262 | 316 | 576 | 755 | 235 | 301 | 1,005 | 0.65× |
| Rust | `nanoserde-typed` | typed | 366 | 332 | 336 | 380 | 533 | 399 | 427 | 514 | 0.63× |
| Python | `json-stdlib` | typed | 314 | 292 | 251 | 307 | 585 | 225 | 415 | 877 | 0.58× |
| Kotlin/Java (JVM) | `minimaljson-dom` | dom | 351 | 357 | 384 | 421 | 415 | 278 | 340 | 410 | 0.57× |
| Go | `jsonparser-typed` | typed | 317 | 257 | 312 | 519 | 551 | 246 | 200 | 787 | 0.56× |
| JavaScript (Deno) | `simdjson-parse` | typed | 268 | 212 | 197 | 290 | 650 | 338 | 469 | 730 | 0.55× |
| Python | `simplejson` | typed | 282 | 271 | 231 | 274 | 564 | 186 | 352 | 880 | 0.52× |
| Kotlin/Java (JVM) | `gson-tree` | dom | 300 | 299 | 298 | 323 | 374 | 275 | 335 | 381 | 0.50× |
| JavaScript (Bun) | `jsonjoy-decoder` | typed |  |  |  | 226 | 467 |  |  | 581 | 0.48× |
| Rust | `struson-typed` | typed | 250 | 222 | 224 | 271 | 347 | 259 | 305 | 338 | 0.43× |
| Kotlin/Java (JVM) | `gson-streaming` | typed | 262 | 261 | 265 | 279 | 307 | 243 | 281 | 291 | 0.43× |
| JavaScript (Node) | `jsonjoy-decoder` | typed |  |  |  | 218 | 343 |  |  | 331 | 0.35× |
| JavaScript (Deno) | `jsonjoy-decoder` | typed |  |  |  | 215 | 303 |  |  | 306 | 0.33× |
| Rust | `tinyjson-dom` | dom | 174 | 161 | 153 | 172 | 225 | 184 | 206 | 227 | 0.29× |
| C/C++ | `nlohmann-sax-typed` | typed | 166 | 138 | 146 | 164 | 197 | 180 | 191 | 202 | 0.27× |
| Go | `stdlib-typed` | typed | 161 | 137 | 128 | 167 | 216 | 166 | 175 | 229 | 0.26× |
| C/C++ | `nlohmann-dom` | dom | 154 | 139 | 132 | 152 | 194 | 172 | 189 | 198 | 0.26× |
| Go | `stdlib-map` | dom | 148 | 130 | 110 | 141 | 199 | 147 | 165 | 234 | 0.24× |
| Go | `gjson-typed` | typed | 157 | 146 | 142 | 157 | 156 | 156 | 175 | 144 | 0.24× |
| C/C++ | `jsonc-dom` | dom | 232 | 187 | 55 | 232 | 174 | 98 | 110 | 231 | 0.23× |
| JavaScript (Node) | `streamparser-json` | typed | 105 | 98 | 80 | 118 | 223 | 86 | 126 | 283 | 0.20× |
| JavaScript (Bun) | `streamparser-json` | typed | 109 | 82 | 84 | 128 | 253 | 79 | 116 | 226 | 0.19× |
| C/C++ | `jansson-dom` | dom | 96 | 98 | 93 | 103 | 123 | 104 | 111 | 121 | 0.17× |
| JavaScript (Deno) | `streamparser-json` | typed | 75 | 69 | 53 | 80 | 164 | 62 | 86 | 216 | 0.14× |
| JavaScript (Deno) | `simdjson-lazy` | dom | 1 | 97 | 84 | 107 | 191 | 90 | 151 | 335 | 0.11× |
| Kotlin/Java (JVM) | `orgjson-dom` | dom | 49 | 55 | 54 | 54 | 55 | 55 | 55 | 55 | 0.08× |
| Kotlin/Java (JVM) | `simdjson-java-schema` | typed | 21 | 21 | 13 | 15 | 41 | 21 | 49 | 182 | 0.05× |

## Variants — `all` workload

Other ways the same library was run (another entry point or input path) and rows a harness flags as variants (breakdowns, an input that isn't the frame type). Each library's headline row above is its fastest; × is this row's speed relative to it, – when the row has no headline row of its own.

| language | library | mode | headline row | variant | input | ns/event | × headline |
|---|---|---|---|---|---|---:|---:|
| C/C++ | Boost.JSON | dom | `boostjson-dom-pool` | `boostjson-dom` | string | 989 | 0.60× |
| C/C++ | nlohmann/json | typed | `nlohmann-sax-typed` | `nlohmann-typed` | string | 6,167 | 0.84× |
| C/C++ | rapidjson | dom | `rapidjson-insitu` | `rapidjson-dom-pool` | string | 782 | 0.72× |
| C/C++ | rapidjson | dom | `rapidjson-insitu` | `rapidjson-dom` | string | 997 | 0.57× |
| C/C++ | yyjson | dom | `yyjson-dom-pool` | `yyjson-insitu` | bytes-copy | 428 | 0.90× |
| C/C++ | yyjson | dom | `yyjson-dom-pool` | `yyjson-dom` | string | 445 | 0.87× |
| Go | bytedance/sonic | typed | `sonic-typed` | `sonic-typed-std` | bytes→string | 1,052 | 0.70× |
| Go | json-iterator/go | typed | `jsoniter-typed-compat` | `jsoniter-typed` | bytes | 1,783 | 0.99× |
| JavaScript (Bun) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,601 | – |
| JavaScript (Bun) | Bun.JSONL.parse | typed | `bun-jsonl-string` | `bun-jsonl-bytes` | string→bytes | 1,523 | 0.77× |
| JavaScript (Deno) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,491 | – |
| JavaScript (Node) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,430 | – |
| Kotlin/Java (JVM) | Quartz JacksonMapper + Event.shareStrings | typed | – | `quartz-jackson-cacheshare` | string | 3,841 | – |
| Kotlin/Java (JVM) | dsl-json | typed | `dsljson-reflect` | `dsljson-bytes` | string→bytes | 1,377 | 0.98× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reflect-string` | `fastjson2-reflect-bytes` | string→bytes | 1,087 | 0.83× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reflect-string` | `fastjson2-reader` | string→bytes | 1,096 | 0.83× |
| Kotlin/Java (JVM) | gson | typed | `gson-streaming` | `gson-reflect` | string | 3,370 | 0.98× |
| Kotlin/Java (JVM) | jackson-core | typed | `jackson-streaming-bytes` | `jackson-streaming-string` | string | 2,159 | 0.62× |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-plain` | string | 1,967 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop` | string | 2,259 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-intern` | string | 4,826 | – |
| Kotlin/Java (JVM) | kotlinx-serialization-json | typed | `kotlinx-typed` | `kotlinx-stream` | string→bytes | 1,994 | 0.94× |
| Python | msgspec | typed | `msgspec-typed` | `msgspec-generic` | string | 1,175 | 0.90× |
| Rust | jiter | dom | `jiter-value-scratch` | `jiter-value` | string | 763 | 0.88× |
| Rust | jiter | typed | `jiter-typed` | `jiter-serde-typed` | string | 810 | 0.90× |
| Rust | simd-json | dom | `simd_json-tape` | `simd_json-borrowed` | bytes-copy | 813 | 0.57× |
| Rust | simd-json | dom | `simd_json-tape` | `simd_json-owned-value` | bytes-copy | 1,148 | 0.40× |
| Rust | sonic-rs | typed | `sonic_rs-typed` | `sonic_rs-lazy` | string | 1,313 | 0.52× |

## Interning: parse cost vs. memory kept (JVM)

`quartz-jackson` is the production parse (databind → `EventDeserializer` → `EventFactory`). `quartz-loop` is the same loop on a bare jackson-core parser and `quartz-loop-plain` also drops `EventFactory`. `quartz-loop-intern` adds the `String.intern()` on id, pubkey and every tag value that Quartz 1.19.0 and earlier did while parsing; `quartz-jackson-cacheshare` adds what LocalCache now does instead to an event it keeps (`Event.shareStrings`), for every event, i.e. the worst case where every event is new and kept. *Retained* is the heap still held after parsing the whole workload and keeping every event.

| workload | quartz-jackson ns/ev | + cache share ns/ev | loop ns/ev | loop + intern ns/ev | plain ns/ev | retained B/ev (no sharing) | retained B/ev (interned) | interning saves |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| all | 1,620 | 3,841 | 2,259 | 4,826 | 1,967 | 1,376 | 900 | 34.6% |
| notes | 1,689 | 3,238 | 1,655 | 3,762 | 1,588 | 1,705 | 1,030 | 39.6% |
| reactions | 1,065 | 1,853 | 1,004 | 2,464 | 910 | 1,049 | 572 | 45.5% |
| dms | 973 | 1,279 | 924 | 1,784 | 839 | 922 | 641 | 30.5% |
| giftwraps | 1,742 | 2,058 | 1,698 | 2,549 | 1,615 | 1,977 | 1,804 | 8.8% |
| metadata | 1,641 | 1,924 | 1,676 | 2,234 | 1,579 | 1,232 | 1,153 | 6.4% |
| zaps | 3,409 | 7,255 | 3,493 | 7,378 | 3,361 | 2,849 | 2,231 | 21.7% |
| lists | 9,901 | 15,712 | 10,078 | 15,719 | 9,665 | 11,350 | 8,814 | 22.3% |

## Failed verification (excluded from rankings)

| language | parser | workload | errors | error |
|---|---|---|---:|---|
| C/C++ | `cjson-dom` | all | 0 | digest mismatch: got 199eb937fc415496194c06a3748a8bf85b7c863b473cbcc39bc40b3b263212ba |
| C/C++ | `cjson-dom` | notes | 0 | digest mismatch: got c5a32a947a9019d412f95f9910ea89d58e4c1eb8a050b836e2f141ae8929ae18 |
| Go | `ojg-dom` | all | 0 | digest mismatch: got c0f97fef77933952f26104cd493407551004039da45ba7e41eb1aacfca6ac857 want 3229b4730242e9187b214e7c2b3942f98318fb03cf65bde3bb9060afe9f6ae08 |
| Go | `ojg-dom` | metadata | 0 | digest mismatch: got dd971a176e9ddebe98d3717d0afa102fd256315b9568dfd9d771c3a3c7aabadd want 3a3ad3324c93bb05217ac54b7c8f281bc6f37ebda072b4eef2747ced47d674b9 |
| Go | `ojg-dom` | notes | 0 | digest mismatch: got f976084f4a8316941df8e27a2934ce2f3f3d555702b79201841bb25d5f7c0727 want 30778a6eb3cc4b07cfb0bab608f7cee7f85441372825de7bb3b619503c37349c |
| Go | `ojg-dom` | reactions | 0 | digest mismatch: got cb83dfdb0a38cb8e66b80d29aa12f0e48cbec8ab7c7bd2936db205e0cb0670c3 want f506853ed4c179de0d32d376df6657d382f3f4854e6536c4b7b06e2c2c72598c |
| Go | `ojg-dom` | zaps | 0 | digest mismatch: got f3102de53bc559ece1f6313727b7796f0de631157928e26dbf918c6cb86786d4 want b709e4bcc31783d5d94e07a0040a58385b67d9d19291641b4db410510f1b394b |
| JavaScript (Bun) | `jsonjoy-decoder` | all | 0 | digest mismatch: fe8d734b797f014d4504bcedbe1d6eda96dcb0d497fe3ee0189ea01fbf0ed5ae != 3229b4730242e9187b214e7c2b3942f98318fb03cf65bde3bb9060afe9f6ae08 |
| JavaScript (Bun) | `jsonjoy-decoder` | metadata | 0 | digest mismatch: ad012351a92b015b905057349fa0bab1d822b1410c05a6341f3a8ae9a51738d2 != 3a3ad3324c93bb05217ac54b7c8f281bc6f37ebda072b4eef2747ced47d674b9 |
| JavaScript (Bun) | `jsonjoy-decoder` | notes | 0 | digest mismatch: 8a25e9578c250cf27d36d110dbadb16f3aecc5a15d6db4bd7c627e6cc6b16f7a != 30778a6eb3cc4b07cfb0bab608f7cee7f85441372825de7bb3b619503c37349c |
| JavaScript (Bun) | `jsonjoy-decoder` | reactions | 0 | digest mismatch: 418371a6abb14e9ee949e64d38e156513be2b946943b0cff9384a0a881e873bd != f506853ed4c179de0d32d376df6657d382f3f4854e6536c4b7b06e2c2c72598c |
| JavaScript (Bun) | `jsonjoy-decoder` | zaps | 0 | digest mismatch: 209350cf979d61f94ee801228749666840062484319eb1f4cfed3abf93d6b696 != b709e4bcc31783d5d94e07a0040a58385b67d9d19291641b4db410510f1b394b |
| JavaScript (Bun) | `simdjson-lazy` | all | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | dms | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | giftwraps | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | lists | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | metadata | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | notes | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | reactions | 0 | child exited with SIGKILL |
| JavaScript (Bun) | `simdjson-lazy` | zaps | 0 | child exited with SIGKILL |
| JavaScript (Deno) | `jsonjoy-decoder` | all | 0 | digest mismatch: fe8d734b797f014d4504bcedbe1d6eda96dcb0d497fe3ee0189ea01fbf0ed5ae != 3229b4730242e9187b214e7c2b3942f98318fb03cf65bde3bb9060afe9f6ae08 |
| JavaScript (Deno) | `jsonjoy-decoder` | metadata | 0 | digest mismatch: ad012351a92b015b905057349fa0bab1d822b1410c05a6341f3a8ae9a51738d2 != 3a3ad3324c93bb05217ac54b7c8f281bc6f37ebda072b4eef2747ced47d674b9 |
| JavaScript (Deno) | `jsonjoy-decoder` | notes | 0 | digest mismatch: 8a25e9578c250cf27d36d110dbadb16f3aecc5a15d6db4bd7c627e6cc6b16f7a != 30778a6eb3cc4b07cfb0bab608f7cee7f85441372825de7bb3b619503c37349c |
| JavaScript (Deno) | `jsonjoy-decoder` | reactions | 0 | digest mismatch: 418371a6abb14e9ee949e64d38e156513be2b946943b0cff9384a0a881e873bd != f506853ed4c179de0d32d376df6657d382f3f4854e6536c4b7b06e2c2c72598c |
| JavaScript (Deno) | `jsonjoy-decoder` | zaps | 0 | digest mismatch: 209350cf979d61f94ee801228749666840062484319eb1f4cfed3abf93d6b696 != b709e4bcc31783d5d94e07a0040a58385b67d9d19291641b4db410510f1b394b |
| JavaScript (Node) | `jsonjoy-decoder` | all | 0 | digest mismatch: fe8d734b797f014d4504bcedbe1d6eda96dcb0d497fe3ee0189ea01fbf0ed5ae != 3229b4730242e9187b214e7c2b3942f98318fb03cf65bde3bb9060afe9f6ae08 |
| JavaScript (Node) | `jsonjoy-decoder` | metadata | 0 | digest mismatch: ad012351a92b015b905057349fa0bab1d822b1410c05a6341f3a8ae9a51738d2 != 3a3ad3324c93bb05217ac54b7c8f281bc6f37ebda072b4eef2747ced47d674b9 |
| JavaScript (Node) | `jsonjoy-decoder` | notes | 0 | digest mismatch: 8a25e9578c250cf27d36d110dbadb16f3aecc5a15d6db4bd7c627e6cc6b16f7a != 30778a6eb3cc4b07cfb0bab608f7cee7f85441372825de7bb3b619503c37349c |
| JavaScript (Node) | `jsonjoy-decoder` | reactions | 0 | digest mismatch: 418371a6abb14e9ee949e64d38e156513be2b946943b0cff9384a0a881e873bd != f506853ed4c179de0d32d376df6657d382f3f4854e6536c4b7b06e2c2c72598c |
| JavaScript (Node) | `jsonjoy-decoder` | zaps | 0 | digest mismatch: 209350cf979d61f94ee801228749666840062484319eb1f4cfed3abf93d6b696 != b709e4bcc31783d5d94e07a0040a58385b67d9d19291641b4db410510f1b394b |
| JavaScript (Node) | `simdjson-lazy` | all | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | dms | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | giftwraps | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | lists | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | metadata | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | notes | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | reactions | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-lazy` | zaps | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | all | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | dms | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | giftwraps | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | lists | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | metadata | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | notes | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | reactions | 0 | child exited with SIGKILL |
| JavaScript (Node) | `simdjson-parse` | zaps | 0 | child exited with SIGKILL |

## Libraries

| language | library | version | license |
|---|---|---|---|
| C/C++ | Boost.JSON | boost-1.92.0 | BSL-1.0 |
| C/C++ | Jansson | v2.15.1 | MIT |
| C/C++ | cJSON | v1.7.19 | MIT |
| C/C++ | glaze | v9.0.0 | MIT |
| C/C++ | jsmn | v1.1.0 | MIT |
| C/C++ | json-c | json-c-0.19-20260627 | MIT |
| C/C++ | nlohmann/json | v3.12.0 | MIT |
| C/C++ | rapidjson | master@24b5e7a8b27f | MIT |
| C/C++ | sajson | master@68fe32ed6bcb | MIT |
| C/C++ | simdjson | v5.0.5 | Apache-2.0 OR MIT |
| C/C++ | yyjson | 0.13.0 | MIT |
| Go | buger/jsonparser | v1.6.1 | MIT |
| Go | bytedance/sonic | v1.15.4 | Apache-2.0 |
| Go | encoding/json | go1.24.7 | BSD-3-Clause |
| Go | fiatjaf.com/nostr | v0.0.0-20251125175014-6367bd71d42a | Unlicense |
| Go | go runtime | go1.24.7 | BSD-3-Clause |
| Go | go-faster/jx | v1.2.0 | MIT |
| Go | go-json-experiment/json | v0.0.0-20250813233538-9b1f9ea2e11b | BSD-3-Clause |
| Go | goccy/go-json | v0.11.2 | MIT |
| Go | json-iterator/go | v1.1.12 | MIT |
| Go | mailru/easyjson | v0.9.2 | MIT |
| Go | minio/simdjson-go | v0.4.5 | Apache-2.0 |
| Go | nbd-wtf/go-nostr | v0.52.3 | MIT |
| Go | ohler55/ojg | v1.28.7 | MIT |
| Go | segmentio/encoding/json | v0.5.4 | MIT |
| Go | tidwall/gjson | v1.20.0 | MIT |
| Go | valyala/fastjson | v1.6.10 | MIT |
| JavaScript (Bun) | @jsonjoy.com/json-pack JsonDecoder | 18.30.0 | Apache-2.0 |
| JavaScript (Bun) | @streamparser/json JSONParser | 0.0.26 | MIT |
| JavaScript (Bun) | Buffer.toString | JavaScriptCore (WebKit 0c06faadf65b, Bun 1.4.3) | BSD-2-Clause |
| JavaScript (Bun) | Buffer.toString + JSON.parse | JavaScriptCore (WebKit 0c06faadf65b, Bun 1.4.3) | BSD-2-Clause |
| JavaScript (Bun) | Bun.JSONL.parse | Bun 1.4.3 | MIT |
| JavaScript (Bun) | JSON.parse | JavaScriptCore (WebKit 0c06faadf65b, Bun 1.4.3) | BSD-2-Clause |
| JavaScript (Bun) | TextEncoder.encode | JavaScriptCore (WebKit 0c06faadf65b, Bun 1.4.3) | BSD-2-Clause |
| JavaScript (Bun) | TextEncoder.encodeInto | JavaScriptCore (WebKit 0c06faadf65b, Bun 1.4.3) | BSD-2-Clause |
| JavaScript (Bun) | simdjson (npm, luizperes/simdjson_nodejs) | 0.9.2 | Apache-2.0 |
| JavaScript (Deno) | @jsonjoy.com/json-pack JsonDecoder | 18.30.0 | Apache-2.0 |
| JavaScript (Deno) | @streamparser/json JSONParser | 0.0.26 | MIT |
| JavaScript (Deno) | Buffer.toString | V8 15.0.245.2-rusty (Deno 2.9.6) | BSD-3-Clause |
| JavaScript (Deno) | Buffer.toString + JSON.parse | V8 15.0.245.2-rusty (Deno 2.9.6) | BSD-3-Clause |
| JavaScript (Deno) | JSON.parse | V8 15.0.245.2-rusty (Deno 2.9.6) | BSD-3-Clause |
| JavaScript (Deno) | TextEncoder.encode | V8 15.0.245.2-rusty (Deno 2.9.6) | BSD-3-Clause |
| JavaScript (Deno) | TextEncoder.encodeInto | V8 15.0.245.2-rusty (Deno 2.9.6) | BSD-3-Clause |
| JavaScript (Deno) | simdjson (npm, luizperes/simdjson_nodejs) | 0.9.2 | Apache-2.0 |
| JavaScript (Node) | @jsonjoy.com/json-pack JsonDecoder | 18.30.0 | Apache-2.0 |
| JavaScript (Node) | @streamparser/json JSONParser | 0.0.26 | MIT |
| JavaScript (Node) | Buffer.toString | V8 12.4.254.21-node.33 (Node.js 22.22.0) | BSD-3-Clause |
| JavaScript (Node) | Buffer.toString + JSON.parse | V8 12.4.254.21-node.33 (Node.js 22.22.0) | BSD-3-Clause |
| JavaScript (Node) | JSON.parse | V8 12.4.254.21-node.33 (Node.js 22.22.0) | BSD-3-Clause |
| JavaScript (Node) | TextEncoder.encode | V8 12.4.254.21-node.33 (Node.js 22.22.0) | BSD-3-Clause |
| JavaScript (Node) | TextEncoder.encodeInto | V8 12.4.254.21-node.33 (Node.js 22.22.0) | BSD-3-Clause |
| JavaScript (Node) | simdjson (npm, luizperes/simdjson_nodejs) |  |  |
| Kotlin/Java (JVM) | Eclipse Parsson (Jakarta JSON-P) | 1.1.9 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| Kotlin/Java (JVM) | JDK | 21.0.12.1+1-1-24.04.4-Ubuntu | GPL-2.0-only WITH Classpath-exception-2.0 |
| Kotlin/Java (JVM) | Quartz JacksonMapper (jackson-databind + EventDeserializer) | local amethyst@df1faea1 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | Quartz JacksonMapper + Event.shareStrings | local amethyst@df1faea1 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | Quartz KotlinSerializationMapper (kotlinx-serialization-json) | local amethyst@df1faea1 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | dsl-json | 2.0.2 | BSD-3-Clause |
| Kotlin/Java (JVM) | fastjson2 | 2.0.66 | Apache-2.0 |
| Kotlin/Java (JVM) | gson | 2.14.0 | Apache-2.0 |
| Kotlin/Java (JVM) | jackson-core | 2.22.3 | Apache-2.0 |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | 2.22.3 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | jackson-databind | 2.22.3 | Apache-2.0 |
| Kotlin/Java (JVM) | json-smart | 2.6.0 | Apache-2.0 |
| Kotlin/Java (JVM) | jsoniter (Java) | 0.9.23 | MIT |
| Kotlin/Java (JVM) | jsoniter-scala | 2.41.6 | MIT |
| Kotlin/Java (JVM) | kotlinx-serialization-json | 1.11.0 | Apache-2.0 |
| Kotlin/Java (JVM) | minimal-json | 0.9.5 | MIT |
| Kotlin/Java (JVM) | moshi | 1.15.2 | Apache-2.0 |
| Kotlin/Java (JVM) | org.json | 20260814 | LicenseRef-Public-Domain |
| Kotlin/Java (JVM) | simdjson-java | 0.3.0 | Apache-2.0 |
| Python | cysimdjson | 26.27 | Apache-2.0 |
| Python | jiter | 0.17.0 | MIT |
| Python | json (stdlib) | CPython 3.13.16 | PSF-2.0 |
| Python | msgspec | 0.22.0 | BSD-3-Clause |
| Python | orjson | 3.13.0 | MPL-2.0 AND (Apache-2.0 OR MIT) |
| Python | pysimdjson | 7.0.2 | MIT |
| Python | python-rapidjson | 1.25 | MIT |
| Python | simplejson | 4.2.0 | MIT OR AFL-2.1 |
| Python | str.encode | CPython 3.13.16 | PSF-2.0 |
| Python | ujson | 6.0.0 | BSD-3-Clause AND TCL |
| Python | yyjson (py_yyjson) | 4.0.6 | MIT |
| Rust | hifijson | 0.5.0 | MIT |
| Rust | jiter | 0.17.0 | MIT |
| Rust | json | 0.12.4 | MIT OR Apache-2.0 |
| Rust | miniserde | 0.1.46 | MIT OR Apache-2.0 |
| Rust | nanoserde | 0.2.1 | MIT OR Apache-2.0 |
| Rust | serde_json | 1.0.154 | MIT OR Apache-2.0 |
| Rust | serde_json_borrow | 0.9.0 | MIT |
| Rust | simd-json | 0.18.1 | Apache-2.0 OR MIT |
| Rust | simd-json-derive | 0.18.0 | Apache-2.0 OR MIT |
| Rust | simdjson-rust | 0.4.0-alpha.2 | Apache-2.0 |
| Rust | sonic-rs | 0.5.10 | Apache-2.0 |
| Rust | std | 1.97.0 | MIT OR Apache-2.0 |
| Rust | struson | 0.7.2 | MIT OR Apache-2.0 |
| Rust | tinyjson | 2.5.1 | MIT |
