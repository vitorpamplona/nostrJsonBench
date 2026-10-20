# nostrJsonBench report

Corpus `corpus/nostr-2024-startup.ndjson.gz` — 31,161 unique events, fingerprint `f995f42b68b5386e`, raw bytes as received.

| harness | runtime | host | settings |
|---|---|---|---|
| bun | Bun 1.4.3 (JavaScriptCore, WebKit 0c06faadf65b) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| cpp | c++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, -O3 -march=native, C++23, single-threaded | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| deno | Deno 2.9.6 (V8 15.0.245.2-rusty) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| go | go1.24.7 linux/amd64, GOAMD64=v1, GOGC=100 (default), GOMEMLIMIT=off (default), GOMAXPROCS=4, CGO_ENABLED=1, one child process per parser | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| jvm | Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu, GC G1, -Xms2g -Xmx2g -XX:+UseG1GC --add-modules=jdk.incubator.vector, one JVM per parser | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
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
| 1 | C/C++ | `simdjson-ondemand-typed` | string→bytes-padded | 1,363 | 635 | 8.79× | 1,249 | 14.4 | – | 1,288 | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-typed` | string | 1,279 | 677 | 8.24× | 1,677 | 15.7 | – | 1,281 | Apache-2.0 |
| 3 | C/C++ | `glaze-typed` | string | 1,201 | 721 | 7.74× | 1,328 | 15.0 | – | 1,408 | MIT |
| 4 | Rust | `jiter-typed` | string | 1,194 | 726 | 7.70× | 1,644 | 15.5 | – | 1,281 | MIT |
| 5 | Rust | `serde_json-typed` | string | 1,177 | 736 | 7.59× | 1,644 | 15.5 | – | 1,281 | MIT OR Apache-2.0 |
| 6 | Rust | `simd_json_derive-typed` | bytes-copy | 1,176 | 737 | 7.58× | 2,705 | 15.3 | – | 1,109 | Apache-2.0 OR MIT |
| 7 | Go | `sonic-typed` | bytes→string | 1,174 | 738 | 7.57× | 1,529 | 8.1 | 144 / 14 ms | 1,431 | Apache-2.0 |
| 8 | C/C++ | `yyjson-typed` | string | 1,124 | 770 | 7.25× | 4,323 | 12.1 | – | 1,262 | MIT |
| 9 | Rust | `simd_json-typed` | bytes-copy | 1,117 | 776 | 7.20× | 2,705 | 15.3 | – | 1,109 | Apache-2.0 OR MIT |
| 10 | Rust | `simdjson_rust-typed` | bytes-copy | 1,079 | 803 | 6.96× | 973 | 14.0 | – | 1,109 | Apache-2.0 |
| 11 | Rust | `jiter-serde-typed` | string | 1,070 | 810 | 6.90× | 1,644 | 15.5 | – | 1,281 | MIT |
| 12 | Kotlin/Java (JVM) | `jsoniter-scala` | string (readFromString) | 1,000 | 866 | 6.45× | 2,772 | – | 4 / 9 ms | 1,381 | MIT |
| 13 | Go | `goccy-typed` | bytes-copy | 924 | 938 | 5.96× | 1,231 | 5.1 | 91 / 8 ms | 1,201 | MIT |
| 14 | JavaScript (Deno) | `json-parse-string` | string | 877 | 988 | 5.65× | 1,229 | – | – | 1,229 | BSD-3-Clause |
| 15 | C/C++ | `rapidjson-sax-typed` | string | 860 | 1,008 | 5.54× | 1,249 | 14.4 | – | 1,289 | MIT |
| 16 | Go | `fiatjaf-nostr-typed` | bytes | 832 | 1,041 | 5.37× | 1,169 | 10.0 | 81 / 7 ms | 1,140 | Unlicense |
| 17 | Go | `sonic-typed-std` | bytes→string | 824 | 1,052 | 5.31× | 2,123 | 18.1 | 143 / 13 ms | 1,107 | Apache-2.0 |
| 18 | Python | `msgspec-typed` | string | 822 | 1,054 | 5.30× | – | – | 0 / 0 ms | 1,712 | BSD-3-Clause |
| 19 | Kotlin/Java (JVM) | `fastjson2-reflect-string` | string | 793 | 1,092 | 5.11× | 3,701 | – | 5 / 13 ms | 1,389 | Apache-2.0 |
| 20 | Go | `jx-typed` | bytes | 780 | 1,111 | 5.03× | 1,689 | 14.4 | 110 / 9 ms | 1,193 | MIT |
| 21 | Go | `go-nostr-typed` | bytes | 764 | 1,134 | 4.92× | 1,338 | 13.0 | 86 / 8 ms | 1,310 | MIT |
| 22 | JavaScript (Node) | `json-parse-string` | string | 763 | 1,135 | 4.92× | – | – | 128 / 32 ms | 1,234 | BSD-3-Clause |
| 23 | Python | `orjson` | string | 762 | 1,136 | 4.92× | – | – | 0 / 0 ms | 1,840 | MPL-2.0 AND (Apache-2.0 OR MIT) |
| 24 | Go | `fastjson-typed` | bytes-copy | 760 | 1,140 | 4.90× | 1,058 | 12.8 | 66 / 5 ms | 1,058 | MIT |
| 25 | Rust | `miniserde-typed` | string | 756 | 1,146 | 4.87× | 2,162 | 21.2 | – | 1,281 | MIT OR Apache-2.0 |
| 26 | Go | `easyjson-typed` | bytes | 746 | 1,161 | 4.81× | 1,223 | 13.5 | 75 / 8 ms | 1,168 | MIT |
| 27 | Kotlin/Java (JVM) | `fastjson2-reader` | string→bytes | 740 | 1,171 | 4.77× | 3,096 | – | 4 / 10 ms | 1,389 | Apache-2.0 |
| 28 | Python | `msgspec-generic` | string | 737 | 1,175 | 4.75× | – | – | 0 / 0 ms | 1,896 | BSD-3-Clause |
| 29 | JavaScript (Bun) | `bun-jsonl-string` | string | 735 | 1,178 | 4.74× | – | – | – | 1,031 | MIT |
| 30 | JavaScript (Bun) | `json-parse-string` | string | 696 | 1,245 | 4.49× | – | – | – | 1,031 | BSD-2-Clause |
| 31 | Kotlin/Java (JVM) | `fastjson2-reflect-bytes` | string→bytes | 678 | 1,279 | 4.37× | 4,480 | – | 5 / 21 ms | 1,389 | Apache-2.0 |
| 32 | Python | `pysimdjson-recursive` | string | 672 | 1,289 | 4.33× | – | – | 0 / 0 ms | 2,074 | MIT |
| 33 | Rust | `sonic_rs-lazy` | string | 660 | 1,313 | 4.25× | 2,019 | 24.2 | – | 1,281 | Apache-2.0 |
| 34 | Kotlin/Java (JVM) | `dsljson-bytes` | string→bytes | 656 | 1,321 | 4.23× | 2,542 | – | 2 / 5 ms | 1,381 | BSD-3-Clause |
| 35 | Python | `jiter` | string→bytes | 654 | 1,325 | 4.22× | – | – | 0 / 0 ms | 1,604 | MIT |
| 36 | Kotlin/Java (JVM) | `jackson-streaming-bytes` | string→bytes | 632 | 1,370 | 4.08× | 3,322 | – | 3 / 8 ms | 1,370 | Apache-2.0 |
| 37 | Rust | `hifijson-serde-typed` | string | 613 | 1,413 | 3.95× | 1,655 | 15.7 | – | 1,281 | MIT |
| 38 | Kotlin/Java (JVM) | `dsljson-reflect` | string→bytes | 609 | 1,422 | 3.93× | 2,716 | – | 2 / 5 ms | 1,381 | BSD-3-Clause |
| 39 | JavaScript (Node) | `json-parse-buffer` | bytes | 606 | 1,430 | 3.91× | – | – | 186 / 37 ms | 1,234 | BSD-3-Clause |
| 40 | Kotlin/Java (JVM) | `jsoniter-iterator` | string→bytes | 597 | 1,452 | 3.85× | 2,886 | – | 2 / 5 ms | 1,381 | MIT |
| 41 | Python | `yyjson` | string→bytes | 596 | 1,453 | 3.84× | – | – | 0 / 0 ms | 2,089 | MIT |
| 42 | C/C++ | `boostjson-typed` | string | 588 | 1,474 | 3.79× | 2,438 | 28.3 | – | 1,260 | BSL-1.0 |
| 43 | JavaScript (Deno) | `json-parse-buffer` | bytes | 581 | 1,491 | 3.75× | 2,185 | – | – | 1,229 | BSD-3-Clause |
| 44 | Kotlin/Java (JVM) | `quartz-loop-plain` | string | 570 | 1,520 | 3.67× | 2,254 | – | 2 / 6 ms | 1,370 | Apache-2.0 AND MIT |
| 45 | JavaScript (Bun) | `bun-jsonl-bytes` | string→bytes | 569 | 1,523 | 3.67× | – | – | – | 1,031 | MIT |
| 46 | Kotlin/Java (JVM) | `jackson-streaming-string` | string | 567 | 1,528 | 3.66× | 2,076 | – | 1 / 3 ms | 1,370 | Apache-2.0 |
| 47 | JavaScript (Bun) | `json-parse-buffer` | bytes | 541 | 1,601 | 3.49× | – | – | – | 1,031 | BSD-2-Clause |
| 48 | Go | `jsoniter-typed-compat` | bytes | 489 | 1,771 | 3.15× | 1,717 | 19.0 | 69 / 6 ms | 1,080 | MIT |
| 49 | Go | `jsoniter-typed` | bytes | 486 | 1,783 | 3.13× | 1,717 | 19.0 | 70 / 6 ms | 1,080 | MIT |
| 50 | Kotlin/Java (JVM) | `moshi-streaming` | string (okio writeUtf8) | 455 | 1,903 | 2.94× | 3,067 | – | 2 / 6 ms | 1,370 | Apache-2.0 |
| 51 | Kotlin/Java (JVM) | `kotlinx-typed` | string | 453 | 1,910 | 2.92× | 2,943 | – | 2 / 4 ms | 1,370 | Apache-2.0 |
| 52 | Python | `ujson` | string | 446 | 1,944 | 2.87× | – | – | 0 / 0 ms | 2,129 | BSD-3-Clause AND TCL |
| 53 | Kotlin/Java (JVM) | `quartz-kotlinx` | string | 439 | 1,975 | 2.83× | 4,619 | – | 3 / 8 ms | 1,407 | Apache-2.0 AND MIT |
| 54 | Kotlin/Java (JVM) | `quartz-loop-nointern` | string | 436 | 1,988 | 2.81× | 2,731 | – | 1 / 3 ms | 1,407 | Apache-2.0 AND MIT |
| 55 | Python | `python-rapidjson` | string | 429 | 2,020 | 2.77× | – | – | 0 / 0 ms | 2,127 | MIT |
| 56 | Kotlin/Java (JVM) | `kotlinx-stream` | string→bytes | 417 | 2,078 | 2.69× | 4,467 | – | 3 / 8 ms | 1,382 | Apache-2.0 |
| 57 | Go | `jsonv2-typed` | bytes | 385 | 2,248 | 2.48× | 1,242 | 13.7 | 39 / 3 ms | 926 | BSD-3-Clause |
| 58 | Go | `segmentio-typed` | bytes | 366 | 2,368 | 2.36× | 1,728 | 14.0 | 54 / 6 ms | 1,578 | MIT |
| 59 | Rust | `nanoserde-typed` | string | 366 | 2,369 | 2.36× | 2,310 | 39.4 | – | 1,497 | MIT OR Apache-2.0 |
| 60 | JavaScript (Bun) | `simdjson-parse` | string | 348 | 2,489 | 2.24× | – | – | – | 1,260 | Apache-2.0 |
| 61 | Kotlin/Java (JVM) | `parsson-streaming` | string | 320 | 2,709 | 2.06× | 2,039 | – | 1 / 2 ms | 1,381 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| 62 | Go | `jsonparser-typed` | bytes | 317 | 2,731 | 2.05× | 1,419 | 13.4 | 38 / 4 ms | 1,191 | MIT |
| 63 | Python | `json-stdlib` | string | 314 | 2,761 | 2.02× | – | – | 0 / 0 ms | 2,127 | PSF-2.0 |
| 64 | Python | `simplejson` | string | 282 | 3,073 | 1.82× | – | – | 0 / 0 ms | 2,127 | MIT OR AFL-2.1 |
| 65 | JavaScript (Deno) | `simdjson-parse` | string | 268 | 3,228 | 1.73× | 1,709 | – | – | 1,701 | Apache-2.0 |
| 66 | Kotlin/Java (JVM) | `gson-streaming` | string | 258 | 3,358 | 1.66× | 5,127 | – | 2 / 5 ms | 1,381 | Apache-2.0 |
| 67 | Rust | `struson-typed` | string | 250 | 3,464 | 1.61× | 2,181 | 26.0 | – | 1,408 | MIT OR Apache-2.0 |
| 68 | Kotlin/Java (JVM) | `gson-reflect` | string | 244 | 3,546 | 1.58× | 5,397 | – | 2 / 7 ms | 1,382 | Apache-2.0 |
| 69 | C/C++ | `nlohmann-sax-typed` | string | 166 | 5,205 | 1.07× | 3,964 | 38.2 | – | 1,803 | MIT |
| 70 | Kotlin/Java (JVM) | `quartz-loop` | string | 162 | 5,354 | 1.04× | 2,731 | – | 0 / 0 ms | 916 | Apache-2.0 AND MIT |
| 71 | Go | `stdlib-typed` | bytes | 161 | 5,391 | 1.04× | 1,660 | 23.4 | 22 / 2 ms | 1,082 | BSD-3-Clause |
| 72 | Go | `gjson-typed` | bytes | 157 | 5,504 | 1.01× | 1,832 | 12.3 | 25 / 2 ms | 1,337 | MIT |
| 73 | Kotlin/Java (JVM) | `quartz-jackson` | string | 155 | 5,585 | 1.00× | 2,803 | – | 0 / 0 ms | 916 | Apache-2.0 AND MIT |
| 74 | C/C++ | `nlohmann-typed` | string | 140 | 6,167 | 0.91× | 5,840 | 67.2 | – | 1,268 | MIT |
| 75 | JavaScript (Bun) | `streamparser-json` | string | 109 | 7,980 | 0.70× | – | – | – | 1,561 | MIT |
| 76 | JavaScript (Node) | `streamparser-json` | string | 105 | 8,257 | 0.68× | – | – | 178 / 55 ms | 1,701 | MIT |
| 77 | JavaScript (Deno) | `streamparser-json` | string | 75 | 11,482 | 0.49× | 12,047 | – | – | 1,701 | MIT |
| 78 | Kotlin/Java (JVM) | `simdjson-java-schema` | string→bytes-padded | 21 | 41,112 | 0.14× | 63,935 | – | 7 / 15 ms | 1,384 | Apache-2.0 |

## Borrowed: Event whose strings point into the input buffer — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-ondemand-borrowed` | string→bytes-padded | 2,005 | 432 | 12.93× | 320 | 8.0 | – | – | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-borrowed` | string | 1,747 | 496 | 11.27× | 1,102 | 5.7 | – | 705 | Apache-2.0 |
| 3 | Rust | `simd_json_derive-borrowed` | bytes-copy | 1,535 | 564 | 9.89× | 1,906 | 4.9 | – | – | Apache-2.0 OR MIT |
| 4 | C/C++ | `yyjson-borrowed` | string | 1,519 | 570 | 9.79× | 3,476 | 5.7 | – | 3,636 | MIT |
| 5 | Rust | `simd_json-borrowed-struct` | bytes-copy | 1,509 | 574 | 9.73× | 1,906 | 4.9 | – | – | Apache-2.0 OR MIT |
| 6 | Rust | `serde_json-borrowed` | string | 1,460 | 594 | 9.41× | 1,068 | 5.5 | – | 705 | MIT OR Apache-2.0 |
| 7 | Rust | `simdjson_rust-borrowed` | bytes-copy | 1,406 | 616 | 9.06× | 173 | 3.7 | – | – | Apache-2.0 |
| 8 | Rust | `jiter-serde-borrowed` | string | 1,349 | 642 | 8.70× | 1,068 | 5.5 | – | 705 | MIT |
| 9 | C/C++ | `glaze-borrowed` | string | 1,344 | 644 | 8.67× | 1,051 | 12.0 | – | 1,946 | MIT |
| 10 | Go | `segmentio-borrowed` | bytes | 411 | 2,108 | 2.65× | 1,140 | 6.2 | 39 / 5 ms | 1,907 | MIT |

## DOM: the library's generic tree (not converted into an Event) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-dom` | string→bytes-padded | 3,199 | 271 | 20.63× | 0 | 0.0 | – | – | Apache-2.0 OR MIT |
| 2 | C/C++ | `yyjson-dom-pool` | string | 2,246 | 386 | 14.48× | 0 | 0.0 | – | – | MIT |
| 3 | Rust | `sonic_rs-value` | string | 2,037 | 425 | 13.13× | 1,857 | 3.4 | – | 1,873 | Apache-2.0 |
| 4 | C/C++ | `yyjson-insitu` | bytes-copy | 2,026 | 428 | 13.06× | 2,432 | 1.0 | – | – | MIT |
| 5 | Go | `fastjson-dom` | bytes-copy | 1,965 | 441 | 12.67× | 0 | 0.0 | 0 / 0 ms | – | MIT |
| 6 | C/C++ | `yyjson-dom` | string | 1,945 | 445 | 12.54× | 3,302 | 2.0 | – | 3,318 | MIT |
| 7 | Rust | `simd_json-tape` | bytes-copy | 1,883 | 460 | 12.14× | 0 | 0.0 | – | – | Apache-2.0 OR MIT |
| 8 | Python | `pysimdjson` | string | 1,679 | 516 | 10.83× | – | – | 0 / 0 ms | – | MIT |
| 9 | Python | `cysimdjson` | string | 1,586 | 546 | 10.23× | – | – | 0 / 0 ms | – | Apache-2.0 |
| 10 | C/C++ | `rapidjson-insitu` | bytes-copy | 1,534 | 565 | 9.89× | 66,668 | 6.0 | – | – | MIT |
| 11 | C/C++ | `boostjson-dom-pool` | string | 1,456 | 595 | 9.39× | 0 | 0.0 | – | – | BSL-1.0 |
| 12 | C/C++ | `sajson-dom` | string | 1,345 | 644 | 8.67× | 11,116 | 3.0 | – | 9,284 | MIT |
| 13 | Rust | `jiter-value-scratch` | string | 1,286 | 674 | 8.29× | 1,053 | 9.6 | – | 1,085 | MIT |
| 14 | Rust | `jiter-value` | string | 1,136 | 763 | 7.32× | 1,811 | 11.2 | – | 1,309 | MIT |
| 15 | Rust | `serde_json_borrow-value` | string | 1,129 | 767 | 7.28× | 1,889 | 7.5 | – | 1,183 | MIT |
| 16 | C/C++ | `rapidjson-dom-pool` | string | 1,108 | 782 | 7.14× | 0 | 0.0 | – | – | MIT |
| 17 | C/C++ | `jsmn-tokens` | string | 1,107 | 782 | 7.14× | 0 | 0.0 | – | – | MIT |
| 18 | Rust | `simd_json-borrowed` | bytes-copy | 1,066 | 813 | 6.87× | 2,425 | 10.6 | – | – | Apache-2.0 OR MIT |
| 19 | Rust | `simdjson_rust-dom` | bytes-copy | 1,025 | 846 | 6.61× | 1,172 | 6.0 | – | – | Apache-2.0 |
| 20 | Kotlin/Java (JVM) | `simdjson-java-dom` | string→bytes-padded | 885 | 979 | 5.70× | 2,390 | – | 3 / 8 ms | – | Apache-2.0 |
| 21 | C/C++ | `boostjson-dom` | string | 876 | 989 | 5.65× | 1,417 | 18.2 | – | 1,626 | BSL-1.0 |
| 22 | C/C++ | `rapidjson-dom` | string | 869 | 997 | 5.60× | 67,693 | 8.9 | – | 65,792 | MIT |
| 23 | Go | `simdjson-go-dom` | bytes | 820 | 1,056 | 5.29× | 16 | 1.0 | 1 / 1 ms | – | Apache-2.0 |
| 24 | Rust | `serde_json-value` | string | 788 | 1,099 | 5.08× | 2,461 | 23.5 | – | 1,978 | MIT OR Apache-2.0 |
| 25 | Rust | `simd_json-owned-value` | bytes-copy | 755 | 1,148 | 4.87× | 3,206 | 27.9 | – | 1,498 | Apache-2.0 OR MIT |
| 26 | Kotlin/Java (JVM) | `fastjson2-dom` | string→bytes | 699 | 1,239 | 4.51× | 3,549 | – | 4 / 10 ms | 1,893 | Apache-2.0 |
| 27 | Rust | `json-dom` | string | 687 | 1,260 | 4.43× | 3,993 | 16.4 | – | 2,344 | MIT OR Apache-2.0 |
| 28 | Kotlin/Java (JVM) | `jackson-tree` | string→bytes | 648 | 1,338 | 4.17× | 4,316 | – | 4 / 10 ms | 2,245 | Apache-2.0 |
| 29 | Rust | `hifijson-value` | string | 615 | 1,408 | 3.97× | 1,731 | 7.4 | – | 1,259 | MIT |
| 30 | Go | `sonic-ast` | bytes→string | 581 | 1,491 | 3.75× | 2,823 | 9.9 | 135 / 11 ms | 2,153 | Apache-2.0 |
| 31 | C/C++ | `glaze-dom` | string | 536 | 1,617 | 3.45× | 2,404 | 18.0 | – | 1,973 | MIT |
| 32 | Kotlin/Java (JVM) | `kotlinx-element` | string | 474 | 1,827 | 3.06× | 3,877 | – | 3 / 8 ms | 2,774 | Apache-2.0 |
| 33 | Kotlin/Java (JVM) | `jsonsmart-dom` | string | 353 | 2,454 | 2.28× | 2,463 | – | 1 / 3 ms | 2,237 | Apache-2.0 |
| 34 | Kotlin/Java (JVM) | `minimaljson-dom` | string | 346 | 2,501 | 2.23× | 4,841 | – | 2 / 6 ms | 2,491 | MIT |
| 35 | Kotlin/Java (JVM) | `gson-tree` | string | 282 | 3,073 | 1.82× | 6,227 | – | 3 / 9 ms | 2,731 | Apache-2.0 |
| 36 | C/C++ | `jsonc-dom` | string | 232 | 3,735 | 1.50× | 4,021 | 46.6 | – | 2,928 | MIT |
| 37 | Rust | `tinyjson-dom` | string | 174 | 4,986 | 1.12× | 4,076 | 52.7 | – | 2,591 | MIT |
| 38 | C/C++ | `nlohmann-dom` | string | 154 | 5,633 | 0.99× | 4,813 | 57.1 | – | 2,199 | MIT |
| 39 | Go | `stdlib-map` | bytes | 148 | 5,838 | 0.96× | 2,249 | 55.9 | 28 / 2 ms | 1,605 | BSD-3-Clause |
| 40 | C/C++ | `jansson-dom` | string | 96 | 9,042 | 0.62× | 3,452 | 52.7 | – | 2,595 | MIT |
| 41 | Kotlin/Java (JVM) | `orgjson-dom` | string | 48 | 17,906 | 0.31× | 6,026 | – | 0 / 0 ms | 2,331 | LicenseRef-Public-Domain |
| 42 | JavaScript (Deno) | `simdjson-lazy` | string | 1 | 1,385,613 | 0.00× | 1,780 | – | – | – | Apache-2.0 |

## Baselines (not parsers) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | Rust | `std-from_utf8` | bytes | 13,371 | 65 | 86.21× | 0 | 0.0 | – | – | MIT OR Apache-2.0 |
| 2 | JavaScript (Bun) | `baseline-utf8-encodeinto` | string→bytes | 8,816 | 98 | 56.84× | – | – | – | – | BSD-2-Clause |
| 3 | Python | `baseline-utf8-encode` | string→bytes | 6,214 | 139 | 40.06× | – | – | 0 / 0 ms | – | PSF-2.0 |
| 4 | JavaScript (Node) | `baseline-utf8-encodeinto` | string→bytes | 5,798 | 149 | 37.38× | – | – | 76 / 25 ms | – | BSD-3-Clause |
| 5 | Go | `go-bytes-to-string` | bytes→string | 5,121 | 169 | 33.01× | 917 | 1.0 | 378 / 35 ms | 917 | BSD-3-Clause |
| 6 | JavaScript (Deno) | `baseline-utf8-encodeinto` | string→bytes | 4,533 | 191 | 29.23× | 120 | – | – | – | BSD-3-Clause |
| 7 | Kotlin/Java (JVM) | `baseline-utf8-decode` | bytes | 4,197 | 206 | 27.06× | 1,204 | – | 8 / 20 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 8 | Kotlin/Java (JVM) | `baseline-utf8-encode` | string | 4,120 | 210 | 26.56× | 1,118 | – | 8 / 19 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 9 | JavaScript (Bun) | `baseline-utf8-encode` | string→bytes | 3,851 | 225 | 24.83× | – | – | – | – | BSD-2-Clause |
| 10 | JavaScript (Node) | `baseline-buffer-tostring` | bytes | 3,367 | 257 | 21.71× | – | – | 445 / 95 ms | – | BSD-3-Clause |
| 11 | JavaScript (Bun) | `baseline-buffer-tostring` | bytes | 3,018 | 287 | 19.46× | – | – | – | – | BSD-2-Clause |
| 12 | JavaScript (Deno) | `baseline-buffer-tostring` | bytes | 2,182 | 397 | 14.07× | 956 | – | – | – | BSD-3-Clause |
| 13 | JavaScript (Node) | `baseline-utf8-encode` | string→bytes | 612 | 1,415 | 3.95× | – | – | 117 / 597 ms | – | BSD-3-Clause |
| 14 | JavaScript (Deno) | `baseline-utf8-encode` | string→bytes | 154 | 5,621 | 0.99× | 201 | – | – | – | BSD-3-Clause |

## Every workload (MB/s)

Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative to `quartz-jackson` — one number per parser that doesn't let the big `all` workload dominate.

| language | parser | mode | all | notes | reactions | dms | giftwraps | metadata | zaps | lists | geomean × quartz |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C/C++ | `simdjson-dom` | dom | 3,199 | 2,160 | 2,763 | 3,625 | 6,371 | 2,293 | 2,671 | 6,677 | 11.95× |
| Rust | `sonic_rs-value` | dom | 2,037 | 1,828 | 1,969 | 2,361 | 4,450 | 1,476 | 2,363 | 6,992 | 9.01× |
| Go | `fastjson-dom` | dom | 1,965 | 1,828 | 1,681 | 2,439 | 5,202 | 1,676 | 2,121 | 5,291 | 8.72× |
| C/C++ | `yyjson-dom-pool` | dom | 2,246 | 1,625 | 2,012 | 2,370 | 2,872 | 2,372 | 2,548 | 3,430 | 8.36× |
| C/C++ | `simdjson-ondemand-borrowed` | borrowed | 2,005 | 1,242 | 1,368 | 2,680 | 5,258 | 2,259 | 1,973 | 3,116 | 7.92× |
| Rust | `simd_json-tape` | dom | 1,883 | 1,755 | 1,710 | 2,052 | 3,589 | 1,751 | 2,117 | 4,072 | 7.86× |
| Rust | `sonic_rs-borrowed` | borrowed | 1,747 | 1,440 | 1,754 | 2,841 | 4,721 | 1,240 | 1,727 | 4,226 | 7.70× |
| C/C++ | `yyjson-insitu` | dom | 2,026 | 1,501 | 1,721 | 2,125 | 2,846 | 2,098 | 2,341 | 3,390 | 7.69× |
| C/C++ | `yyjson-dom` | dom | 1,945 | 1,446 | 1,758 | 2,051 | 2,696 | 1,977 | 2,220 | 3,330 | 7.42× |
| Python | `pysimdjson` | dom | 1,679 | 1,428 | 1,550 | 1,997 | 3,512 | 1,159 | 2,109 | 5,918 | 7.37× |
| Python | `cysimdjson` | dom | 1,586 | 1,350 | 1,398 | 1,790 | 3,377 | 1,141 | 2,072 | 5,650 | 6.97× |
| C/C++ | `rapidjson-insitu` | dom | 1,534 | 1,178 | 1,203 | 1,555 | 3,746 | 1,312 | 2,067 | 5,409 | 6.75× |
| Rust | `simd_json_derive-borrowed` | borrowed | 1,535 | 1,273 | 1,360 | 1,788 | 3,353 | 1,590 | 1,892 | 2,962 | 6.52× |
| Rust | `simd_json-borrowed-struct` | borrowed | 1,509 | 1,250 | 1,335 | 1,773 | 3,196 | 1,599 | 1,867 | 2,934 | 6.42× |
| Rust | `serde_json-borrowed` | borrowed | 1,460 | 1,221 | 1,485 | 2,238 | 3,897 | 959 | 1,468 | 3,426 | 6.33× |
| Rust | `simdjson_rust-borrowed` | borrowed | 1,406 | 1,160 | 1,295 | 1,744 | 3,378 | 1,456 | 1,777 | 3,146 | 6.25× |
| C/C++ | `boostjson-dom-pool` | dom | 1,456 | 1,169 | 1,154 | 1,934 | 4,077 | 921 | 1,435 | 4,074 | 6.10× |
| Rust | `jiter-serde-borrowed` | borrowed | 1,349 | 1,030 | 1,432 | 2,205 | 4,378 | 781 | 1,487 | 3,687 | 6.09× |
| C/C++ | `yyjson-borrowed` | borrowed | 1,519 | 1,083 | 1,254 | 1,650 | 2,435 | 1,805 | 2,013 | 2,369 | 5.99× |
| Rust | `sonic_rs-typed` | typed | 1,279 | 977 | 1,286 | 2,228 | 3,736 | 1,192 | 1,467 | 2,307 | 5.78× |
| C/C++ | `simdjson-ondemand-typed` | typed | 1,363 | 859 | 991 | 1,695 | 3,460 | 1,777 | 1,574 | 2,263 | 5.62× |
| C/C++ | `glaze-borrowed` | borrowed | 1,344 | 968 | 1,203 | 2,115 | 3,108 | 1,512 | 1,375 | 1,782 | 5.54× |
| Rust | `jiter-value-scratch` | dom | 1,286 | 1,043 | 1,060 | 1,606 | 3,446 | 1,030 | 1,561 | 2,973 | 5.53× |
| Rust | `jiter-typed` | typed | 1,194 | 913 | 1,394 | 2,099 | 3,866 | 781 | 1,453 | 2,500 | 5.48× |
| Rust | `simd_json_derive-typed` | typed | 1,176 | 878 | 1,057 | 1,515 | 2,717 | 1,486 | 1,644 | 1,958 | 5.15× |
| Rust | `serde_json-typed` | typed | 1,177 | 944 | 1,191 | 1,823 | 3,081 | 903 | 1,316 | 2,142 | 5.07× |
| Rust | `jiter-value` | dom | 1,136 | 881 | 1,066 | 1,665 | 3,475 | 750 | 1,329 | 2,688 | 4.99× |
| Rust | `serde_json_borrow-value` | dom | 1,129 | 960 | 1,031 | 1,634 | 3,144 | 887 | 1,238 | 2,539 | 4.97× |
| C/C++ | `sajson-dom` | dom | 1,345 | 1,039 | 1,197 | 1,435 | 1,927 | 1,026 | 1,261 | 2,647 | 4.96× |
| Rust | `simd_json-typed` | typed | 1,117 | 857 | 962 | 1,396 | 2,675 | 1,438 | 1,542 | 1,947 | 4.92× |
| Rust | `jiter-serde-typed` | typed | 1,070 | 811 | 1,159 | 1,758 | 3,498 | 749 | 1,338 | 2,286 | 4.89× |
| Rust | `simdjson_rust-typed` | typed | 1,079 | 856 | 1,007 | 1,398 | 2,750 | 1,315 | 1,526 | 1,850 | 4.85× |
| C/C++ | `glaze-typed` | typed | 1,201 | 811 | 908 | 1,613 | 2,703 | 1,304 | 1,191 | 1,815 | 4.73× |
| C/C++ | `rapidjson-dom-pool` | dom | 1,108 | 942 | 979 | 1,204 | 2,405 | 920 | 1,205 | 3,217 | 4.71× |
| Rust | `simd_json-borrowed` | dom | 1,066 | 871 | 833 | 1,193 | 2,408 | 1,253 | 1,453 | 2,150 | 4.60× |
| Rust | `simdjson_rust-dom` | dom | 1,025 | 801 | 842 | 1,238 | 2,643 | 1,224 | 1,411 | 2,207 | 4.60× |
| Kotlin/Java (JVM) | `jsoniter-scala` | typed | 1,000 | 890 | 1,405 | 1,755 | 1,833 | 640 | 847 | 2,019 | 4.23× |
| C/C++ | `yyjson-typed` | typed | 1,124 | 549 | 590 | 1,244 | 1,967 | 1,493 | 1,525 | 1,814 | 4.13× |
| C/C++ | `jsmn-tokens` | dom | 1,107 | 870 | 874 | 1,164 | 1,572 | 1,089 | 1,280 | 1,141 | 3.93× |
| JavaScript (Bun) | `json-parse-string` | typed | 696 | 596 | 960 | 1,399 | 2,290 | 671 | 1,008 | 2,467 | 3.86× |
| JavaScript (Deno) | `json-parse-string` | typed | 877 | 727 | 797 | 1,157 | 2,256 | 589 | 1,019 | 2,633 | 3.85× |
| Go | `sonic-typed` | typed | 1,174 | 715 | 690 | 1,231 | 1,677 | 843 | 920 | 2,019 | 3.80× |
| C/C++ | `boostjson-dom` | dom | 876 | 727 | 701 | 1,016 | 2,242 | 734 | 1,091 | 2,204 | 3.78× |
| Kotlin/Java (JVM) | `fastjson2-reflect-string` | typed | 793 | 762 | 1,014 | 1,362 | 1,834 | 529 | 679 | 3,101 | 3.75× |
| JavaScript (Bun) | `bun-jsonl-string` | typed | 735 | 562 | 925 | 1,359 | 2,156 | 677 | 951 | 2,334 | 3.75× |
| Kotlin/Java (JVM) | `simdjson-java-dom` | dom | 885 | 749 | 906 | 1,170 | 1,479 | 711 | 1,019 | 1,917 | 3.68× |
| C/C++ | `rapidjson-sax-typed` | typed | 860 | 630 | 710 | 1,069 | 2,077 | 917 | 1,062 | 1,648 | 3.65× |
| C/C++ | `rapidjson-dom` | dom | 869 | 697 | 685 | 903 | 1,674 | 694 | 1,026 | 2,569 | 3.57× |
| Kotlin/Java (JVM) | `fastjson2-reader` | typed | 740 | 655 | 1,142 | 1,796 | 1,618 | 467 | 622 | 2,245 | 3.53× |
| Rust | `serde_json-value` | dom | 788 | 671 | 714 | 1,059 | 2,025 | 704 | 1,059 | 1,691 | 3.52× |
| Go | `simdjson-go-dom` | dom | 820 | 760 | 687 | 843 | 1,805 | 738 | 1,097 | 1,681 | 3.46× |
| Rust | `simd_json-owned-value` | dom | 755 | 588 | 597 | 959 | 1,905 | 1,028 | 1,144 | 1,541 | 3.45× |
| Go | `goccy-typed` | typed | 924 | 685 | 628 | 1,073 | 1,516 | 845 | 936 | 1,687 | 3.45× |
| Python | `msgspec-typed` | typed | 822 | 608 | 752 | 1,196 | 1,797 | 526 | 1,068 | 1,867 | 3.44× |
| JavaScript (Node) | `json-parse-string` | typed | 763 | 652 | 646 | 919 | 1,657 | 703 | 1,038 | 1,854 | 3.33× |
| Kotlin/Java (JVM) | `fastjson2-dom` | dom | 699 | 633 | 1,005 | 1,413 | 1,528 | 448 | 617 | 2,191 | 3.28× |
| Python | `orjson` | typed | 762 | 593 | 633 | 813 | 1,398 | 665 | 1,218 | 1,929 | 3.23× |
| Rust | `miniserde-typed` | typed | 756 | 643 | 759 | 1,065 | 1,581 | 714 | 914 | 1,206 | 3.21× |
| Python | `jiter` | typed | 654 | 503 | 622 | 862 | 1,735 | 418 | 1,010 | 2,932 | 3.11× |
| Python | `msgspec-generic` | typed | 737 | 552 | 653 | 950 | 1,563 | 490 | 1,025 | 1,849 | 3.10× |
| Kotlin/Java (JVM) | `fastjson2-reflect-bytes` | typed | 678 | 616 | 889 | 1,212 | 1,407 | 427 | 596 | 2,189 | 3.08× |
| Go | `fiatjaf-nostr-typed` | typed | 832 | 643 | 570 | 1,020 | 1,472 | 630 | 732 | 1,581 | 3.06× |
| Rust | `sonic_rs-lazy` | typed | 660 | 531 | 618 | 1,060 | 2,135 | 623 | 702 | 1,490 | 3.04× |
| Python | `pysimdjson-recursive` | typed | 672 | 524 | 516 | 732 | 1,476 | 559 | 1,040 | 2,308 | 2.97× |
| Go | `fastjson-typed` | typed | 760 | 583 | 512 | 894 | 1,357 | 616 | 721 | 1,723 | 2.89× |
| JavaScript (Bun) | `bun-jsonl-bytes` | typed | 569 | 452 | 674 | 930 | 1,678 | 459 | 853 | 1,949 | 2.88× |
| Go | `go-nostr-typed` | typed | 764 | 600 | 525 | 896 | 1,414 | 601 | 710 | 1,521 | 2.87× |
| JavaScript (Bun) | `json-parse-buffer` | typed | 541 | 504 | 746 | 920 | 1,457 | 509 | 715 | 1,793 | 2.83× |
| Kotlin/Java (JVM) | `dsljson-bytes` | typed | 656 | 613 | 965 | 1,178 | 1,074 | 480 | 580 | 1,247 | 2.81× |
| Go | `easyjson-typed` | typed | 746 | 565 | 498 | 894 | 1,400 | 591 | 681 | 1,506 | 2.79× |
| Kotlin/Java (JVM) | `jackson-streaming-bytes` | typed | 632 | 586 | 632 | 837 | 1,191 | 607 | 829 | 1,331 | 2.78× |
| Rust | `json-dom` | dom | 687 | 586 | 646 | 881 | 1,179 | 599 | 761 | 1,170 | 2.76× |
| Kotlin/Java (JVM) | `jackson-tree` | dom | 648 | 592 | 641 | 805 | 1,180 | 586 | 818 | 1,265 | 2.75× |
| Go | `jx-typed` | typed | 780 | 540 | 589 | 970 | 1,208 | 513 | 592 | 1,333 | 2.69× |
| JavaScript (Node) | `json-parse-buffer` | typed | 606 | 510 | 544 | 762 | 1,305 | 516 | 872 | 1,536 | 2.68× |
| Python | `yyjson` | typed | 596 | 510 | 480 | 604 | 1,081 | 574 | 978 | 1,682 | 2.60× |
| C/C++ | `boostjson-typed` | typed | 588 | 437 | 479 | 671 | 1,542 | 612 | 822 | 1,280 | 2.57× |
| Kotlin/Java (JVM) | `dsljson-reflect` | typed | 609 | 577 | 841 | 1,011 | 977 | 426 | 513 | 1,227 | 2.55× |
| Go | `sonic-typed-std` | typed | 824 | 532 | 461 | 815 | 1,038 | 695 | 762 | 857 | 2.55× |
| Kotlin/Java (JVM) | `jsoniter-iterator` | typed | 597 | 566 | 752 | 908 | 927 | 492 | 586 | 1,077 | 2.50× |
| JavaScript (Deno) | `json-parse-buffer` | typed | 581 | 515 | 562 | 732 | 1,179 | 431 | 711 | 1,389 | 2.48× |
| Rust | `hifijson-value` | dom | 615 | 534 | 599 | 796 | 1,197 | 456 | 652 | 1,062 | 2.47× |
| Rust | `hifijson-serde-typed` | typed | 613 | 518 | 643 | 843 | 1,207 | 454 | 656 | 953 | 2.47× |
| Kotlin/Java (JVM) | `quartz-loop-plain` | typed | 570 | 564 | 610 | 746 | 1,016 | 556 | 640 | 815 | 2.37× |
| C/C++ | `glaze-dom` | dom | 536 | 423 | 394 | 643 | 1,322 | 740 | 731 | 1,001 | 2.36× |
| Kotlin/Java (JVM) | `jackson-streaming-string` | typed | 567 | 539 | 574 | 715 | 998 | 551 | 633 | 852 | 2.33× |
| Go | `sonic-ast` | dom | 581 | 415 | 300 | 465 | 830 | 435 | 834 | 1,470 | 2.08× |
| Kotlin/Java (JVM) | `quartz-kotlinx` | typed | 439 | 469 | 481 | 662 | 1,116 | 411 | 239 | 1,555 | 2.00× |
| Kotlin/Java (JVM) | `kotlinx-element` | dom | 474 | 457 | 489 | 580 | 708 | 417 | 566 | 740 | 1.91× |
| Kotlin/Java (JVM) | `quartz-loop-nointern` | typed | 436 | 522 | 539 | 667 | 966 | 517 | 216 | 845 | 1.90× |
| Kotlin/Java (JVM) | `moshi-streaming` | typed | 455 | 437 | 454 | 606 | 875 | 333 | 481 | 870 | 1.88× |
| Python | `python-rapidjson` | typed | 429 | 369 | 342 | 451 | 802 | 398 | 690 | 1,159 | 1.86× |
| Python | `ujson` | typed | 446 | 400 | 357 | 439 | 728 | 412 | 635 | 971 | 1.82× |
| Kotlin/Java (JVM) | `kotlinx-typed` | typed | 453 | 413 | 440 | 567 | 700 | 390 | 545 | 692 | 1.80× |
| C/C++ | `cjson-dom` | dom |  |  | 376 | 505 | 819 | 540 | 622 | 773 | 1.79× |
| Kotlin/Java (JVM) | `parsson-streaming` | typed | 320 | 441 | 467 | 601 | 788 | 384 | 432 | 846 | 1.78× |
| Go | `segmentio-borrowed` | borrowed | 411 | 297 | 372 | 748 | 1,086 | 248 | 322 | 1,398 | 1.77× |
| Go | `jsoniter-typed-compat` | typed | 489 | 346 | 392 | 639 | 681 | 359 | 354 | 792 | 1.69× |
| Go | `jsoniter-typed` | typed | 486 | 349 | 394 | 625 | 694 | 358 | 351 | 788 | 1.69× |
| Kotlin/Java (JVM) | `kotlinx-stream` | typed | 417 | 380 | 460 | 611 | 838 | 302 | 446 | 507 | 1.66× |
| JavaScript (Bun) | `simdjson-parse` | typed | 348 | 309 | 313 | 401 | 878 | 430 | 582 | 816 | 1.65× |
| Kotlin/Java (JVM) | `jsonsmart-dom` | dom | 353 | 336 | 547 | 626 | 531 | 266 | 315 | 802 | 1.55× |
| Go | `jsonv2-typed` | typed | 385 | 295 | 288 | 483 | 713 | 310 | 357 | 793 | 1.48× |
| Go | `segmentio-typed` | typed | 366 | 262 | 316 | 576 | 755 | 235 | 301 | 1,005 | 1.47× |
| Rust | `nanoserde-typed` | typed | 366 | 332 | 336 | 380 | 533 | 399 | 427 | 514 | 1.42× |
| Python | `json-stdlib` | typed | 314 | 292 | 251 | 307 | 585 | 225 | 415 | 877 | 1.29× |
| Kotlin/Java (JVM) | `minimaljson-dom` | dom | 346 | 348 | 377 | 418 | 421 | 276 | 345 | 424 | 1.29× |
| Go | `jsonparser-typed` | typed | 317 | 257 | 312 | 519 | 551 | 246 | 200 | 787 | 1.26× |
| Go | `ojg-dom` | dom |  |  |  | 397 | 631 |  |  | 776 | 1.25× |
| JavaScript (Deno) | `simdjson-parse` | typed | 268 | 212 | 197 | 290 | 650 | 338 | 469 | 730 | 1.24× |
| Python | `simplejson` | typed | 282 | 271 | 231 | 274 | 564 | 186 | 352 | 880 | 1.17× |
| Kotlin/Java (JVM) | `gson-tree` | dom | 282 | 285 | 276 | 308 | 359 | 273 | 322 | 377 | 1.08× |
| Kotlin/Java (JVM) | `quartz-loop` | typed | 162 | 245 | 240 | 355 | 672 | 408 | 153 | 540 | 1.07× |
| Kotlin/Java (JVM) | `quartz-jackson` | typed | 155 | 230 | 224 | 322 | 616 | 383 | 143 | 498 | 1.00× |
| Rust | `struson-typed` | typed | 250 | 222 | 224 | 271 | 347 | 259 | 305 | 338 | 0.96× |
| Kotlin/Java (JVM) | `gson-streaming` | typed | 258 | 258 | 265 | 279 | 305 | 246 | 283 | 295 | 0.96× |
| Kotlin/Java (JVM) | `gson-reflect` | typed | 244 | 243 | 247 | 265 | 301 | 239 | 276 | 303 | 0.93× |
| JavaScript (Bun) | `jsonjoy-decoder` | typed |  |  |  | 226 | 467 |  |  | 581 | 0.85× |
| Rust | `tinyjson-dom` | dom | 174 | 161 | 153 | 172 | 225 | 184 | 206 | 227 | 0.65× |
| JavaScript (Node) | `jsonjoy-decoder` | typed |  |  |  | 218 | 343 |  |  | 331 | 0.63× |
| C/C++ | `nlohmann-sax-typed` | typed | 166 | 138 | 146 | 164 | 197 | 180 | 191 | 202 | 0.60× |
| Go | `stdlib-typed` | typed | 161 | 137 | 128 | 167 | 216 | 166 | 175 | 229 | 0.59× |
| JavaScript (Deno) | `jsonjoy-decoder` | typed |  |  |  | 215 | 303 |  |  | 306 | 0.59× |
| C/C++ | `nlohmann-dom` | dom | 154 | 139 | 132 | 152 | 194 | 172 | 189 | 198 | 0.58× |
| Go | `stdlib-map` | dom | 148 | 130 | 110 | 141 | 199 | 147 | 165 | 234 | 0.54× |
| Go | `gjson-typed` | typed | 157 | 146 | 142 | 157 | 156 | 156 | 175 | 144 | 0.54× |
| C/C++ | `nlohmann-typed` | typed | 140 | 121 | 118 | 141 | 186 | 165 | 178 | 183 | 0.53× |
| C/C++ | `jsonc-dom` | dom | 232 | 187 | 55 | 232 | 174 | 98 | 110 | 231 | 0.52× |
| JavaScript (Node) | `streamparser-json` | typed | 105 | 98 | 80 | 118 | 223 | 86 | 126 | 283 | 0.44× |
| JavaScript (Bun) | `streamparser-json` | typed | 109 | 82 | 84 | 128 | 253 | 79 | 116 | 226 | 0.43× |
| C/C++ | `jansson-dom` | dom | 96 | 98 | 93 | 103 | 123 | 104 | 111 | 121 | 0.37× |
| JavaScript (Deno) | `streamparser-json` | typed | 75 | 69 | 53 | 80 | 164 | 62 | 86 | 216 | 0.31× |
| JavaScript (Deno) | `simdjson-lazy` | dom | 1 | 97 | 84 | 107 | 191 | 90 | 151 | 335 | 0.24× |
| Kotlin/Java (JVM) | `orgjson-dom` | dom | 48 | 55 | 54 | 55 | 55 | 55 | 55 | 55 | 0.19× |
| Kotlin/Java (JVM) | `simdjson-java-schema` | typed | 21 | 21 | 13 | 15 | 40 | 20 | 49 | 182 | 0.11× |

## Interning: parse cost vs. memory kept (JVM)

`quartz-jackson` is the production path (databind → `EventDeserializer`, `String.intern()` on id, pubkey and every tag value, `EventFactory`). `quartz-loop` is the same loop on a bare jackson-core parser; `quartz-loop-nointern` drops the intern calls; `quartz-loop-plain` also drops `EventFactory`. *Retained* is the heap still held after parsing the whole workload and keeping every event (what a client's cache holds), so its difference is what interning saves; the ns/event difference is what it costs.

| workload | quartz-jackson ns/ev | quartz-loop ns/ev | no-intern ns/ev | plain ns/ev | intern cost ns/ev | retained B/ev (intern) | retained B/ev (no intern) | intern saves B/ev | saves % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| all | 5,585 | 5,354 | 1,988 | 1,520 | 3,365 | 916 | 1,407 | 491 | 34.9% |
| notes | 3,926 | 3,696 | 1,732 | 1,602 | 1,963 | 1,030 | 1,705 | 676 | 39.6% |
| reactions | 2,498 | 2,325 | 1,036 | 916 | 1,289 | 572 | 1,049 | 477 | 45.5% |
| dms | 1,990 | 1,802 | 961 | 858 | 841 | 641 | 922 | 282 | 30.5% |
| giftwraps | 2,759 | 2,530 | 1,760 | 1,674 | 770 | 1,804 | 1,977 | 173 | 8.8% |
| metadata | 2,290 | 2,145 | 1,694 | 1,576 | 451 | 1,153 | 1,232 | 78 | 6.4% |
| zaps | 14,877 | 13,951 | 9,891 | 3,334 | 4,061 | 2,944 | 3,600 | 656 | 18.2% |
| lists | 17,140 | 15,784 | 10,088 | 10,469 | 5,696 | 8,814 | 11,350 | 2,537 | 22.3% |

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
| Kotlin/Java (JVM) | dsl-json | 2.0.2 | BSD-3-Clause |
| Kotlin/Java (JVM) | fastjson2 | 2.0.66 | Apache-2.0 |
| Kotlin/Java (JVM) | gson | 2.14.0 | Apache-2.0 |
| Kotlin/Java (JVM) | jackson-core | 2.22.3 | Apache-2.0 |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | 2.22.3 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | jackson-databind | 2.22.3 | Apache-2.0 |
| Kotlin/Java (JVM) | jackson-databind via Quartz JacksonMapper | 2.22.3 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | json-smart | 2.6.0 | Apache-2.0 |
| Kotlin/Java (JVM) | jsoniter (Java) | 0.9.23 | MIT |
| Kotlin/Java (JVM) | jsoniter-scala | 2.41.6 | MIT |
| Kotlin/Java (JVM) | kotlinx-serialization-json | 1.11.0 | Apache-2.0 |
| Kotlin/Java (JVM) | kotlinx-serialization-json via Quartz KotlinSerializationMapper | 1.11.0 | Apache-2.0 AND MIT |
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
