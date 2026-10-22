# nostrJsonBench report

Corpus `corpus/nostr-2024-startup.ndjson.gz` — 31,161 unique events, fingerprint `f995f42b68b5386e`, raw bytes as received.

| harness | runtime | host | settings |
|---|---|---|---|
| bun | Bun 1.4.3 (JavaScriptCore, WebKit 0c06faadf65b) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| cpp | c++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0, -O3 -march=native, C++23, single-threaded | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| deno | Deno 2.9.6 (V8 15.0.245.2-rusty) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| go | go1.24.7 linux/amd64, GOAMD64=v1, GOGC=100 (default), GOMEMLIMIT=off (default), GOMAXPROCS=4, CGO_ENABLED=1, one child process per parser | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| jvm | JetBrains s.r.o. OpenJDK 64-Bit Server VM 21.0.11+10-b1163.116, GC G1, -Xmx2g -XX:+UseG1GC --add-modules=jdk.incubator.vector, one JVM per parser, Quartz 2.1.0 | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| node | Node.js 22.22.0 (V8 12.4.254.21-node.33) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| python | CPython 3.13.16 (CPython, GCC 13.3.0) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |
| rust | rustc 1.97.0 (2d8144b78 2026-07-07), -C target-cpu=native, release (lto=fat, codegen-units=1) | Intel(R) Xeon(R) Processor ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |

MB/s is input bytes per second of median round (higher is better). Single thread. `× quartz` is speed relative to Amethyst's production parser (`jvm/quartz-kite`, Quartz's hand-written Kite reader) on the same workload. Alloc is heap bytes per parsed event; objects created is allocations per parse, objects kept is what each kept event still holds alive, garbage the difference; GC is collections / total GC time during measurement.

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

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-ondemand-typed` | string→bytes-padded | 1,428 | 607 | 1.30× | 1,249 | 14.4 | 11.1 | 3.3 | – | 1,288 | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-typed` | string | 1,354 | 640 | 1.23× | 1,677 | 15.7 | 14.0 | 1.7 | – | 1,281 | Apache-2.0 |
| 3 | C/C++ | `glaze-typed` | string | 1,208 | 717 | 1.10× | 1,328 | 15.0 | 11.7 | 3.3 | – | 1,408 | MIT |
| 4 | Rust | `simd_json_derive-typed` | bytes-copy | 1,202 | 721 | 1.09× | 2,705 | 15.2 | 14.0 | 1.2 | – | 1,109 | Apache-2.0 OR MIT |
| 5 | Rust | `serde_json-typed` | string | 1,201 | 722 | 1.09× | 1,644 | 15.4 | 14.0 | 1.4 | – | 1,281 | MIT OR Apache-2.0 |
| 6 | Rust | `jiter-typed` | string | 1,198 | 723 | 1.09× | 1,644 | 15.4 | 14.0 | 1.4 | – | 1,281 | MIT |
| 7 | Go | `sonic-typed` | bytes→string | 1,182 | 733 | 1.07× | 1,529 | 8.1 | 6.0 | 2.1 | 120 / 11 ms | 1,427 | Apache-2.0 |
| 8 | C/C++ | `yyjson-typed` | string | 1,143 | 758 | 1.04× | 4,323 | 12.1 | 11.1 | 1.0 | – | 1,262 | MIT |
| 9 | Rust | `simdjson_rust-typed` | bytes-copy | 1,129 | 767 | 1.02× | 973 | 14.0 | 14.0 | 0.0 | – | 1,109 | Apache-2.0 |
| 10 | Rust | `simd_json-typed` | bytes-copy | 1,124 | 770 | 1.02× | 2,705 | 15.2 | 14.0 | 1.2 | – | 1,109 | Apache-2.0 OR MIT |
| 11 | Kotlin/Java (JVM) | `jsoniter-scala` | string (readFromString) | 1,118 | 775 | 1.01× | 2,772 | 30.8 | 25.8 | 5.0 | 19 / 20 ms | 1,381 | MIT |
| 12 | Kotlin/Java (JVM) | `quartz-kite` | string | 1,102 | 786 | 1.00× | 1,321 | 21.3 | 21.3 | 0.0 | 17 / 22 ms | 1,288 | MIT |
| 13 | Go | `goccy-typed` | bytes-copy | 938 | 924 | 0.85× | 1,232 | 5.1 | 4.9 | 0.2 | 78 / 7 ms | 1,201 | MIT |
| 14 | Kotlin/Java (JVM) | `fastjson2-reader` | string→bytes | 938 | 924 | 0.85× | 3,096 | 33.4 | 26.3 | 7.1 | 21 / 23 ms | 1,389 | Apache-2.0 |
| 15 | JavaScript (Deno) | `json-parse-string` | string | 884 | 980 | 0.80× | 1,230 | 15.4 | 15.4 | 0.0 | – | 1,229 | BSD-3-Clause |
| 16 | C/C++ | `rapidjson-sax-typed` | string | 872 | 994 | 0.79× | 1,249 | 14.4 | 11.1 | 3.3 | – | 1,289 | MIT |
| 17 | Python | `msgspec-typed` | string | 871 | 994 | 0.79× | – | – | 20.1 | – | 0 / 0 ms | 1,712 | BSD-3-Clause |
| 18 | JavaScript (Bun) | `bun-jsonl-string` | string | 855 | 1,014 | 0.78× | – | – | 13.7 | – | – | 1,031 | MIT |
| 19 | JavaScript (Bun) | `json-parse-string` | string | 842 | 1,028 | 0.76× | – | – | 13.7 | – | – | 1,031 | BSD-2-Clause |
| 20 | Go | `fiatjaf-nostr-typed` | bytes | 829 | 1,046 | 0.75× | 1,169 | 10.0 | 9.0 | 1.0 | 69 / 7 ms | 1,140 | Unlicense |
| 21 | Rust | `miniserde-typed` | string | 820 | 1,057 | 0.74× | 2,162 | 21.1 | 14.0 | 7.1 | – | 1,281 | MIT OR Apache-2.0 |
| 22 | Go | `jx-typed` | bytes | 815 | 1,063 | 0.74× | 1,689 | 14.4 | 12.1 | 2.3 | 100 / 10 ms | 1,193 | MIT |
| 23 | Python | `orjson` | string | 795 | 1,090 | 0.72× | – | – | 21.1 | – | 0 / 0 ms | 1,840 | MPL-2.0 AND (Apache-2.0 OR MIT) |
| 24 | Go | `go-nostr-typed` | bytes | 787 | 1,101 | 0.71× | 1,338 | 13.0 | 12.0 | 1.0 | 76 / 7 ms | 1,310 | MIT |
| 25 | Kotlin/Java (JVM) | `dsljson-bytes` | string→bytes | 768 | 1,127 | 0.70× | 2,542 | 30.7 | 25.8 | 4.9 | 26 / 22 ms | 1,381 | BSD-3-Clause |
| 26 | Go | `easyjson-typed` | bytes | 752 | 1,152 | 0.68× | 1,223 | 13.5 | 12.0 | 1.5 | 66 / 5 ms | 1,168 | MIT |
| 27 | JavaScript (Node) | `json-parse-string` | string | 744 | 1,165 | 0.67× | – | 15.7 | 15.6 | 0.0 | 96 / 23 ms | 1,234 | BSD-3-Clause |
| 28 | Go | `fastjson-typed` | bytes-copy | 740 | 1,171 | 0.67× | 1,058 | 12.8 | 11.9 | 0.9 | 55 / 5 ms | 1,058 | MIT |
| 29 | Kotlin/Java (JVM) | `jackson-streaming-bytes` | string→bytes | 717 | 1,209 | 0.65× | 3,282 | 41.2 | 25.3 | 15.9 | 33 / 31 ms | 1,370 | Apache-2.0 |
| 30 | Python | `pysimdjson-recursive` | string | 697 | 1,244 | 0.63× | – | – | 25.9 | – | 0 / 0 ms | 2,074 | MIT |
| 31 | Python | `jiter` | string→bytes | 684 | 1,266 | 0.62× | – | – | 16.2 | – | 0 / 0 ms | 1,604 | MIT |
| 32 | Kotlin/Java (JVM) | `jsoniter-iterator` | string→bytes | 675 | 1,283 | 0.61× | 2,886 | 44.7 | 25.8 | 18.9 | 26 / 26 ms | 1,381 | MIT |
| 33 | Python | `yyjson` | string→bytes | 621 | 1,395 | 0.56× | – | – | 28.1 | – | 0 / 0 ms | 2,089 | MIT |
| 34 | Rust | `hifijson-serde-typed` | string | 618 | 1,401 | 0.56× | 1,655 | 15.5 | 14.0 | 1.5 | – | 1,281 | MIT |
| 35 | C/C++ | `boostjson-typed` | string | 609 | 1,423 | 0.55× | 2,438 | 28.3 | 11.1 | 17.2 | – | 1,260 | BSL-1.0 |
| 36 | Kotlin/Java (JVM) | `quartz-jackson` | string | 544 | 1,593 | 0.49× | 2,332 | 43.1 | 25.5 | 17.6 | 14 / 14 ms | 1,376 | Apache-2.0 AND MIT |
| 37 | Kotlin/Java (JVM) | `parsson-streaming` | string | 501 | 1,728 | 0.45× | 2,048 | 54.3 | 25.8 | 28.5 | 14 / 14 ms | 1,381 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| 38 | Go | `jsoniter-typed-compat` | bytes | 477 | 1,818 | 0.43× | 1,717 | 19.0 | 12.1 | 6.9 | 61 / 5 ms | 1,080 | MIT |
| 39 | Kotlin/Java (JVM) | `kotlinx-stream` | string→bytes | 474 | 1,826 | 0.43× | 4,467 | 65.4 | 25.8 | 39.6 | 31 / 26 ms | 1,384 | Apache-2.0 |
| 40 | Python | `python-rapidjson` | string | 453 | 1,911 | 0.41× | – | – | 25.9 | – | 0 / 0 ms | 2,127 | MIT |
| 41 | Kotlin/Java (JVM) | `moshi-streaming` | string (okio writeUtf8) | 443 | 1,957 | 0.40× | 3,067 | 51.4 | 25.3 | 26.1 | 18 / 17 ms | 1,370 | Apache-2.0 |
| 42 | Python | `ujson` | string | 437 | 1,983 | 0.40× | – | – | 25.9 | – | 0 / 0 ms | 2,129 | BSD-3-Clause AND TCL |
| 43 | Kotlin/Java (JVM) | `quartz-kotlinx` | string | 398 | 2,175 | 0.36× | 4,476 | 115.2 | 25.5 | 89.7 | 28 / 26 ms | 1,377 | Apache-2.0 AND MIT |
| 44 | Go | `segmentio-typed` | bytes | 384 | 2,259 | 0.35× | 1,728 | 14.0 | 12.0 | 2.1 | 52 / 6 ms | 1,578 | MIT |
| 45 | JavaScript (Bun) | `simdjson-parse` | string | 383 | 2,263 | 0.35× | – | – | 17.5 | – | – | 1,260 | Apache-2.0 |
| 46 | Go | `jsonv2-typed` | bytes | 372 | 2,329 | 0.34× | 1,242 | 13.7 | 8.9 | 4.8 | 35 / 3 ms | 926 | BSD-3-Clause |
| 47 | Rust | `nanoserde-typed` | string | 367 | 2,364 | 0.33× | 2,310 | 30.3 | 14.2 | 16.1 | – | 1,497 | MIT OR Apache-2.0 |
| 48 | Python | `json-stdlib` | string | 318 | 2,724 | 0.29× | – | – | 25.9 | – | 0 / 0 ms | 2,127 | PSF-2.0 |
| 49 | Go | `jsonparser-typed` | bytes | 313 | 2,767 | 0.28× | 1,419 | 13.4 | 12.0 | 1.4 | 35 / 3 ms | 1,191 | MIT |
| 50 | Python | `simplejson` | string | 295 | 2,934 | 0.27× | – | – | 25.9 | – | 0 / 0 ms | 2,127 | MIT OR AFL-2.1 |
| 51 | JavaScript (Deno) | `simdjson-parse` | string | 277 | 3,130 | 0.25× | 1,710 | 17.6 | 16.5 | 1.0 | – | 1,701 | Apache-2.0 |
| 52 | Kotlin/Java (JVM) | `gson-streaming` | string | 250 | 3,467 | 0.23× | 5,127 | 51.3 | 25.8 | 25.5 | 19 / 18 ms | 1,382 | Apache-2.0 |
| 53 | Rust | `struson-typed` | string | 238 | 3,634 | 0.22× | 2,181 | 25.9 | 14.0 | 11.9 | – | 1,408 | MIT OR Apache-2.0 |
| 54 | C/C++ | `nlohmann-sax-typed` | string | 168 | 5,150 | 0.15× | 3,964 | 38.2 | 11.1 | 27.1 | – | 1,803 | MIT |
| 55 | Go | `stdlib-typed` | bytes | 159 | 5,465 | 0.14× | 1,660 | 23.4 | 12.3 | 11.2 | 22 / 2 ms | 1,082 | BSD-3-Clause |
| 56 | Go | `gjson-typed` | bytes | 156 | 5,551 | 0.14× | 1,832 | 12.3 | 9.7 | 2.6 | 25 / 3 ms | 1,337 | MIT |
| 57 | JavaScript (Bun) | `streamparser-json` | string | 121 | 7,145 | 0.11× | – | – | 21.1 | – | – | 1,561 | MIT |
| 58 | JavaScript (Node) | `streamparser-json` | string | 110 | 7,854 | 0.10× | – | – | 16.5 | – | 179 / 46 ms | 1,701 | MIT |
| 59 | JavaScript (Deno) | `streamparser-json` | string | 80 | 10,858 | 0.07× | 12,041 | – | 16.5 | – | – | 1,701 | MIT |
| 60 | Kotlin/Java (JVM) | `simdjson-java-schema` | string→bytes-padded | 20 | 43,020 | 0.02× | 62,759 | 1,959.8 | 25.8 | 1,933.9 | 57 / 69 ms | 1,384 | Apache-2.0 |

## Borrowed: Event whose strings point into the input buffer — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-ondemand-borrowed` | string→bytes-padded | 2,136 | 406 | 1.94× | 320 | 8.0 | – | – | – | – | Apache-2.0 OR MIT |
| 2 | Rust | `sonic_rs-borrowed` | string | 1,828 | 474 | 1.66× | 1,102 | 5.6 | 4.0 | 1.7 | – | 705 | Apache-2.0 |
| 3 | Rust | `simd_json_derive-borrowed` | bytes-copy | 1,604 | 540 | 1.46× | 1,906 | 4.9 | – | – | – | – | Apache-2.0 OR MIT |
| 4 | C/C++ | `yyjson-borrowed` | string | 1,572 | 551 | 1.43× | 3,476 | 5.7 | 6.7 | 0.0 | – | 3,636 | MIT |
| 5 | Rust | `simd_json-borrowed-struct` | bytes-copy | 1,563 | 554 | 1.42× | 1,906 | 4.9 | – | – | – | – | Apache-2.0 OR MIT |
| 6 | Rust | `serde_json-borrowed` | string | 1,492 | 581 | 1.35× | 1,068 | 5.4 | 4.0 | 1.4 | – | 705 | MIT OR Apache-2.0 |
| 7 | Rust | `simdjson_rust-borrowed` | bytes-copy | 1,438 | 602 | 1.30× | 173 | 3.7 | – | – | – | – | Apache-2.0 |
| 8 | Rust | `jiter-serde-borrowed` | string | 1,379 | 628 | 1.25× | 1,068 | 5.4 | 4.0 | 1.4 | – | 705 | MIT |
| 9 | C/C++ | `glaze-borrowed` | string | 1,372 | 631 | 1.25× | 1,051 | 12.0 | 9.7 | 2.3 | – | 1,946 | MIT |
| 10 | Go | `segmentio-borrowed` | bytes | 431 | 2,011 | 0.39× | 1,140 | 6.2 | 6.0 | 0.2 | 37 / 3 ms | 1,907 | MIT |

## DOM: the library's generic tree (not converted into an Event) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | C/C++ | `simdjson-dom` | string→bytes-padded | 3,357 | 258 | 3.05× | 0 | 0.0 | – | – | – | – | Apache-2.0 OR MIT |
| 2 | C/C++ | `yyjson-dom-pool` | string | 2,395 | 362 | 2.17× | 0 | 0.0 | – | – | – | – | MIT |
| 3 | Rust | `sonic_rs-value` | string | 2,187 | 396 | 1.98× | 1,857 | 3.4 | 3.4 | 0.0 | – | 1,873 | Apache-2.0 |
| 4 | Rust | `simd_json-tape` | bytes-copy | 1,963 | 441 | 1.78× | 0 | 0.0 | – | – | – | – | Apache-2.0 OR MIT |
| 5 | Go | `fastjson-dom` | bytes-copy | 1,953 | 443 | 1.77× | 0 | 0.0 | – | – | 0 / 0 ms | – | MIT |
| 6 | Python | `pysimdjson` | string | 1,805 | 480 | 1.64× | – | – | – | – | 0 / 0 ms | – | MIT |
| 7 | Python | `cysimdjson` | string | 1,689 | 513 | 1.53× | – | – | – | – | 0 / 0 ms | – | Apache-2.0 |
| 8 | C/C++ | `rapidjson-insitu` | bytes-copy | 1,666 | 520 | 1.51× | 66,668 | 6.0 | – | – | – | – | MIT |
| 9 | C/C++ | `boostjson-dom-pool` | string | 1,470 | 589 | 1.33× | 0 | 0.0 | – | – | – | – | BSL-1.0 |
| 10 | C/C++ | `sajson-dom` | string | 1,384 | 626 | 1.26× | 11,116 | 3.0 | 3.0 | 0.0 | – | 9,284 | MIT |
| 11 | Rust | `jiter-value-scratch` | string | 1,332 | 650 | 1.21× | 1,053 | 9.6 | 9.6 | 0.0 | – | 1,085 | MIT |
| 12 | Rust | `serde_json_borrow-value` | string | 1,126 | 770 | 1.02× | 1,889 | 7.4 | 5.0 | 2.4 | – | 1,183 | MIT |
| 13 | C/C++ | `jsmn-tokens` | string | 1,110 | 781 | 1.01× | 0 | 0.0 | – | – | – | – | MIT |
| 14 | Rust | `simdjson_rust-dom` | bytes-copy | 1,027 | 844 | 0.93× | 1,172 | 6.0 | – | – | – | – | Apache-2.0 |
| 15 | Kotlin/Java (JVM) | `simdjson-java-dom` | string→bytes-padded | 963 | 900 | 0.87× | 2,390 | 36.5 | – | – | 22 / 23 ms | – | Apache-2.0 |
| 16 | Go | `simdjson-go-dom` | bytes | 844 | 1,026 | 0.77× | 16 | 1.0 | – | – | 0 / 0 ms | – | Apache-2.0 |
| 17 | Kotlin/Java (JVM) | `fastjson2-dom` | string→bytes | 837 | 1,035 | 0.76× | 3,549 | 42.9 | 39.4 | 3.6 | 22 / 22 ms | 1,893 | Apache-2.0 |
| 18 | Rust | `serde_json-value` | string | 818 | 1,059 | 0.74× | 2,461 | 23.4 | 22.0 | 1.4 | – | 1,978 | MIT OR Apache-2.0 |
| 19 | Rust | `json-dom` | string | 697 | 1,244 | 0.63× | 3,993 | 16.3 | 10.3 | 6.1 | – | 2,344 | MIT OR Apache-2.0 |
| 20 | Kotlin/Java (JVM) | `jackson-tree` | string→bytes | 690 | 1,255 | 0.63× | 4,316 | 68.8 | 53.5 | 15.3 | 24 / 24 ms | 2,245 | Apache-2.0 |
| 21 | Rust | `hifijson-value` | string | 626 | 1,385 | 0.57× | 1,731 | 7.1 | 5.0 | 2.2 | – | 1,259 | MIT |
| 22 | Go | `sonic-ast` | bytes→string | 615 | 1,409 | 0.56× | 2,822 | 9.9 | 5.2 | 4.7 | 128 / 11 ms | 2,154 | Apache-2.0 |
| 23 | C/C++ | `glaze-dom` | string | 530 | 1,635 | 0.48× | 2,404 | 18.0 | 12.7 | 5.3 | – | 1,973 | MIT |
| 24 | Kotlin/Java (JVM) | `kotlinx-element` | string | 514 | 1,685 | 0.47× | 3,877 | 87.7 | 72.5 | 15.2 | 28 / 27 ms | 2,774 | Apache-2.0 |
| 25 | Kotlin/Java (JVM) | `jsonsmart-dom` | string | 406 | 2,133 | 0.37× | 2,463 | 57.2 | 52.4 | 4.8 | 13 / 14 ms | 2,237 | Apache-2.0 |
| 26 | Kotlin/Java (JVM) | `minimaljson-dom` | string | 402 | 2,157 | 0.36× | 4,841 | 78.2 | 70.0 | 8.1 | 16 / 19 ms | 2,493 | MIT |
| 27 | Kotlin/Java (JVM) | `gson-tree` | string | 271 | 3,194 | 0.25× | 6,227 | 84.9 | 75.0 | 9.9 | 17 / 21 ms | 2,732 | Apache-2.0 |
| 28 | C/C++ | `jsonc-dom` | string | 233 | 3,718 | 0.21× | 4,021 | 42.9 | 33.9 | 9.0 | – | 2,928 | MIT |
| 29 | Rust | `tinyjson-dom` | string | 175 | 4,958 | 0.16× | 4,076 | 43.5 | 22.0 | 21.5 | – | 2,591 | MIT |
| 30 | JavaScript (Bun) | `simdjson-lazy` | string | 165 | 5,258 | 0.15× | – | – | – | – | – | – | Apache-2.0 |
| 31 | C/C++ | `nlohmann-dom` | string | 151 | 5,737 | 0.14× | 4,813 | 57.1 | 33.6 | 23.5 | – | 2,199 | MIT |
| 32 | Go | `stdlib-map` | bytes | 144 | 6,036 | 0.13× | 2,249 | 55.9 | 31.7 | 24.2 | 25 / 3 ms | 1,605 | BSD-3-Clause |
| 33 | C/C++ | `jansson-dom` | string | 89 | 9,779 | 0.08× | 3,452 | 52.7 | 40.0 | 12.7 | – | 2,595 | MIT |
| 34 | Kotlin/Java (JVM) | `orgjson-dom` | string | 54 | 16,107 | 0.05× | 6,026 | 129.6 | 58.0 | 71.6 | 7 / 8 ms | 2,333 | LicenseRef-Public-Domain |
| 35 | JavaScript (Deno) | `simdjson-lazy` | string | 5 | 165,072 | 0.00× | 1,785 | – | – | – | – | – | Apache-2.0 |

## Baselines (not parsers) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | Rust | `std-from_utf8` | bytes | 16,840 | 51 | 15.28× | 0 | 0.0 | – | – | – | – | MIT OR Apache-2.0 |
| 2 | JavaScript (Bun) | `baseline-utf8-encodeinto` | string→bytes | 11,423 | 76 | 10.36× | – | – | – | – | – | – | BSD-2-Clause |
| 3 | Python | `baseline-utf8-encode` | string→bytes | 6,746 | 128 | 6.12× | – | – | – | – | 0 / 0 ms | – | PSF-2.0 |
| 4 | JavaScript (Node) | `baseline-utf8-encodeinto` | string→bytes | 6,723 | 129 | 6.10× | – | – | – | – | 31 / 10 ms | – | BSD-3-Clause |
| 5 | Go | `go-bytes-to-string` | bytes→string | 6,544 | 132 | 5.94× | 917 | 1.0 | 1.0 | 0.0 | 220 / 21 ms | 917 | BSD-3-Clause |
| 6 | Kotlin/Java (JVM) | `baseline-utf8-encode` | string | 5,899 | 147 | 5.35× | 1,118 | 1.1 | – | – | 21 / 23 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 7 | Kotlin/Java (JVM) | `baseline-utf8-decode` | bytes | 5,896 | 147 | 5.35× | 1,204 | 2.1 | – | – | 34 / 34 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 8 | JavaScript (Bun) | `baseline-utf8-encode` | string→bytes | 5,299 | 163 | 4.81× | – | – | – | – | – | – | BSD-2-Clause |
| 9 | JavaScript (Deno) | `baseline-utf8-encodeinto` | string→bytes | 4,596 | 188 | 4.17× | 121 | – | – | – | – | – | BSD-3-Clause |
| 10 | JavaScript (Node) | `baseline-buffer-tostring` | bytes | 3,707 | 234 | 3.36× | – | – | – | – | 220 / 41 ms | – | BSD-3-Clause |
| 11 | JavaScript (Bun) | `baseline-buffer-tostring` | bytes | 3,473 | 249 | 3.15× | – | – | – | – | – | – | BSD-2-Clause |
| 12 | JavaScript (Deno) | `baseline-buffer-tostring` | bytes | 1,205 | 719 | 1.09× | 958 | – | – | – | – | – | BSD-3-Clause |
| 13 | JavaScript (Node) | `baseline-utf8-encode` | string→bytes | 374 | 2,316 | 0.34× | – | – | – | – | 54 / 278 ms | – | BSD-3-Clause |
| 14 | JavaScript (Deno) | `baseline-utf8-encode` | string→bytes | 332 | 2,610 | 0.30× | 202 | – | – | – | – | – | BSD-3-Clause |

## Every workload (MB/s)

Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative to `quartz-kite` — one number per parser that doesn't let the big `all` workload dominate.

| language | parser | mode | all | notes | reactions | dms | giftwraps | metadata | zaps | lists | geomean × quartz |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| C/C++ | `simdjson-dom` | dom | 3,357 | 2,993 | 3,689 | 4,523 | 7,645 | 2,263 | 2,770 | 6,567 | 2.85× |
| Rust | `sonic_rs-value` | dom | 2,187 | 1,938 | 1,934 | 2,455 | 5,422 | 1,482 | 2,725 | 7,172 | 2.01× |
| C/C++ | `yyjson-dom-pool` | dom | 2,395 | 2,216 | 2,670 | 2,855 | 3,338 | 2,238 | 2,705 | 3,422 | 1.98× |
| C/C++ | `simdjson-ondemand-borrowed` | borrowed | 2,136 | 1,572 | 1,770 | 3,382 | 6,199 | 2,233 | 2,148 | 3,104 | 1.88× |
| Rust | `simd_json-tape` | dom | 1,963 | 1,825 | 1,684 | 2,123 | 3,796 | 1,688 | 2,217 | 4,021 | 1.67× |
| Python | `pysimdjson` | dom | 1,805 | 1,604 | 1,653 | 2,160 | 4,268 | 1,171 | 2,302 | 5,941 | 1.66× |
| Rust | `sonic_rs-borrowed` | borrowed | 1,828 | 1,430 | 1,801 | 2,955 | 5,316 | 1,205 | 1,726 | 4,100 | 1.64× |
| Go | `fastjson-dom` | dom | 1,953 | 1,522 | 1,313 | 2,095 | 3,861 | 1,600 | 1,855 | 4,958 | 1.58× |
| C/C++ | `rapidjson-insitu` | dom | 1,666 | 1,461 | 1,446 | 1,813 | 4,282 | 1,312 | 2,161 | 5,578 | 1.56× |
| Python | `cysimdjson` | dom | 1,689 | 1,487 | 1,479 | 1,933 | 3,943 | 1,151 | 2,192 | 5,828 | 1.56× |
| Rust | `simd_json_derive-borrowed` | borrowed | 1,604 | 1,297 | 1,385 | 1,995 | 3,797 | 1,645 | 1,966 | 2,999 | 1.43× |
| Rust | `simd_json-borrowed-struct` | borrowed | 1,563 | 1,255 | 1,287 | 1,858 | 3,635 | 1,584 | 1,934 | 2,957 | 1.38× |
| Rust | `serde_json-borrowed` | borrowed | 1,492 | 1,229 | 1,440 | 2,282 | 4,208 | 887 | 1,445 | 3,319 | 1.32× |
| Rust | `simdjson_rust-borrowed` | borrowed | 1,438 | 1,170 | 1,273 | 1,711 | 3,609 | 1,383 | 1,853 | 3,111 | 1.31× |
| C/C++ | `yyjson-borrowed` | borrowed | 1,572 | 1,164 | 1,441 | 1,802 | 2,604 | 1,740 | 2,065 | 2,372 | 1.31× |
| C/C++ | `boostjson-dom-pool` | dom | 1,470 | 1,330 | 1,293 | 1,875 | 4,186 | 930 | 1,426 | 3,752 | 1.30× |
| Rust | `jiter-serde-borrowed` | borrowed | 1,379 | 1,042 | 1,410 | 2,218 | 4,473 | 773 | 1,475 | 3,620 | 1.27× |
| C/C++ | `simdjson-ondemand-typed` | typed | 1,428 | 967 | 1,141 | 1,957 | 3,796 | 1,770 | 1,695 | 2,133 | 1.26× |
| Rust | `sonic_rs-typed` | typed | 1,354 | 1,014 | 1,270 | 2,191 | 3,873 | 1,109 | 1,467 | 2,271 | 1.21× |
| Rust | `jiter-value-scratch` | dom | 1,332 | 1,057 | 1,087 | 1,648 | 3,861 | 991 | 1,605 | 2,998 | 1.18× |
| C/C++ | `glaze-borrowed` | borrowed | 1,372 | 987 | 1,218 | 2,145 | 3,274 | 1,462 | 1,389 | 1,674 | 1.16× |
| C/C++ | `sajson-dom` | dom | 1,384 | 1,252 | 1,488 | 1,698 | 2,251 | 1,037 | 1,291 | 2,745 | 1.15× |
| Rust | `jiter-typed` | typed | 1,198 | 912 | 1,268 | 1,925 | 3,740 | 731 | 1,423 | 2,448 | 1.10× |
| Rust | `simd_json_derive-typed` | typed | 1,202 | 904 | 1,036 | 1,579 | 2,941 | 1,464 | 1,653 | 1,919 | 1.09× |
| Rust | `serde_json-typed` | typed | 1,201 | 943 | 1,133 | 1,816 | 3,410 | 838 | 1,337 | 2,081 | 1.06× |
| Rust | `simdjson_rust-typed` | typed | 1,129 | 870 | 1,004 | 1,441 | 2,906 | 1,253 | 1,594 | 1,839 | 1.03× |
| Rust | `simd_json-typed` | typed | 1,124 | 855 | 926 | 1,419 | 2,859 | 1,409 | 1,576 | 1,889 | 1.03× |
| C/C++ | `glaze-typed` | typed | 1,208 | 882 | 1,037 | 1,605 | 2,711 | 1,282 | 1,329 | 1,720 | 1.02× |
| Kotlin/Java (JVM) | `quartz-kite` | typed | 1,102 | 990 | 1,031 | 1,497 | 2,796 | 686 | 1,120 | 3,327 | 1.00× |
| Rust | `serde_json_borrow-value` | dom | 1,126 | 899 | 953 | 1,563 | 3,185 | 825 | 1,210 | 2,412 | 0.99× |
| Rust | `simdjson_rust-dom` | dom | 1,027 | 805 | 817 | 1,231 | 2,792 | 1,164 | 1,446 | 2,156 | 0.96× |
| Kotlin/Java (JVM) | `jsoniter-scala` | typed | 1,118 | 944 | 1,514 | 2,015 | 1,995 | 649 | 922 | 2,078 | 0.95× |
| C/C++ | `yyjson-typed` | typed | 1,143 | 628 | 561 | 1,351 | 2,101 | 1,448 | 1,557 | 1,638 | 0.88× |
| C/C++ | `jsmn-tokens` | dom | 1,110 | 988 | 1,028 | 1,241 | 1,582 | 1,084 | 1,262 | 1,147 | 0.86× |
| JavaScript (Bun) | `json-parse-string` | typed | 842 | 678 | 999 | 1,425 | 2,497 | 627 | 1,028 | 2,509 | 0.85× |
| Kotlin/Java (JVM) | `fastjson2-reader` | typed | 938 | 738 | 1,251 | 2,071 | 1,925 | 487 | 696 | 2,295 | 0.83× |
| JavaScript (Deno) | `json-parse-string` | typed | 884 | 750 | 797 | 1,206 | 2,396 | 591 | 1,038 | 2,823 | 0.83× |
| JavaScript (Bun) | `bun-jsonl-string` | typed | 855 | 674 | 935 | 1,315 | 2,198 | 596 | 1,008 | 2,408 | 0.81× |
| Kotlin/Java (JVM) | `simdjson-java-dom` | dom | 963 | 819 | 921 | 1,185 | 1,658 | 724 | 1,111 | 1,896 | 0.81× |
| C/C++ | `rapidjson-sax-typed` | typed | 872 | 652 | 711 | 1,077 | 2,306 | 924 | 1,065 | 1,535 | 0.77× |
| Go | `sonic-typed` | typed | 1,182 | 696 | 655 | 1,199 | 1,666 | 702 | 978 | 2,036 | 0.77× |
| Kotlin/Java (JVM) | `fastjson2-dom` | dom | 837 | 680 | 1,067 | 1,524 | 1,681 | 437 | 660 | 2,315 | 0.74× |
| Rust | `serde_json-value` | dom | 818 | 669 | 681 | 1,029 | 2,102 | 686 | 1,053 | 1,663 | 0.73× |
| Python | `msgspec-typed` | typed | 871 | 605 | 746 | 1,190 | 1,828 | 531 | 1,092 | 1,904 | 0.73× |
| JavaScript (Node) | `json-parse-string` | typed | 744 | 654 | 635 | 929 | 1,737 | 694 | 1,058 | 1,883 | 0.70× |
| Python | `jiter` | typed | 684 | 551 | 681 | 946 | 1,910 | 426 | 1,047 | 2,987 | 0.69× |
| Python | `orjson` | typed | 795 | 595 | 621 | 838 | 1,524 | 672 | 1,275 | 1,911 | 0.69× |
| Rust | `miniserde-typed` | typed | 820 | 659 | 755 | 1,110 | 1,684 | 682 | 969 | 1,202 | 0.69× |
| Go | `simdjson-go-dom` | dom | 844 | 734 | 624 | 822 | 1,583 | 715 | 1,021 | 1,523 | 0.68× |
| Go | `goccy-typed` | typed | 938 | 641 | 612 | 1,064 | 1,585 | 747 | 917 | 1,216 | 0.67× |
| Python | `pysimdjson-recursive` | typed | 697 | 540 | 533 | 759 | 1,627 | 570 | 1,083 | 2,324 | 0.64× |
| Kotlin/Java (JVM) | `dsljson-bytes` | typed | 768 | 672 | 1,005 | 1,280 | 1,194 | 481 | 601 | 1,265 | 0.63× |
| Kotlin/Java (JVM) | `jackson-streaming-bytes` | typed | 717 | 610 | 670 | 856 | 1,311 | 608 | 884 | 1,292 | 0.61× |
| Kotlin/Java (JVM) | `jackson-tree` | dom | 690 | 608 | 642 | 815 | 1,260 | 593 | 891 | 1,306 | 0.60× |
| Go | `fiatjaf-nostr-typed` | typed | 829 | 600 | 555 | 963 | 1,494 | 589 | 697 | 1,106 | 0.59× |
| Rust | `json-dom` | dom | 697 | 583 | 615 | 865 | 1,183 | 591 | 762 | 1,157 | 0.57× |
| Kotlin/Java (JVM) | `jsoniter-iterator` | typed | 675 | 598 | 782 | 939 | 1,037 | 495 | 607 | 1,098 | 0.55× |
| Go | `fastjson-typed` | typed | 740 | 557 | 490 | 847 | 1,289 | 572 | 672 | 1,167 | 0.55× |
| Python | `yyjson` | typed | 621 | 505 | 474 | 601 | 1,127 | 555 | 988 | 1,693 | 0.54× |
| Go | `go-nostr-typed` | typed | 787 | 534 | 487 | 850 | 1,346 | 549 | 660 | 1,086 | 0.54× |
| Go | `easyjson-typed` | typed | 752 | 532 | 464 | 862 | 1,369 | 563 | 643 | 1,041 | 0.54× |
| Go | `jx-typed` | typed | 815 | 524 | 563 | 965 | 1,173 | 415 | 589 | 964 | 0.52× |
| C/C++ | `boostjson-typed` | typed | 609 | 461 | 470 | 673 | 1,496 | 614 | 828 | 950 | 0.52× |
| Rust | `hifijson-value` | dom | 626 | 532 | 592 | 798 | 1,204 | 457 | 667 | 1,074 | 0.52× |
| Rust | `hifijson-serde-typed` | typed | 618 | 512 | 633 | 834 | 1,188 | 459 | 661 | 941 | 0.51× |
| C/C++ | `glaze-dom` | dom | 530 | 418 | 383 | 619 | 1,326 | 714 | 740 | 954 | 0.48× |
| Kotlin/Java (JVM) | `quartz-jackson` | typed | 544 | 500 | 516 | 629 | 984 | 485 | 591 | 838 | 0.45× |
| Go | `sonic-ast` | dom | 615 | 415 | 307 | 474 | 865 | 375 | 804 | 1,240 | 0.42× |
| Kotlin/Java (JVM) | `parsson-streaming` | typed | 501 | 462 | 477 | 624 | 846 | 400 | 473 | 856 | 0.41× |
| Kotlin/Java (JVM) | `kotlinx-element` | dom | 514 | 460 | 485 | 585 | 731 | 403 | 571 | 722 | 0.40× |
| Python | `python-rapidjson` | typed | 453 | 381 | 346 | 453 | 839 | 400 | 724 | 1,160 | 0.40× |
| C/C++ | `cjson-dom` | dom |  |  | 390 | 530 | 821 | 540 | 639 | 740 | 0.40× |
| Kotlin/Java (JVM) | `kotlinx-stream` | typed | 474 | 407 | 476 | 620 | 933 | 328 | 534 | 615 | 0.38× |
| Kotlin/Java (JVM) | `moshi-streaming` | typed | 443 | 433 | 447 | 604 | 856 | 307 | 465 | 812 | 0.38× |
| Python | `ujson` | typed | 437 | 377 | 353 | 431 | 741 | 406 | 630 | 952 | 0.37× |
| Go | `segmentio-borrowed` | borrowed | 431 | 297 | 377 | 740 | 1,022 | 244 | 314 | 1,336 | 0.36× |
| JavaScript (Bun) | `simdjson-parse` | typed | 383 | 331 | 323 | 419 | 882 | 404 | 635 | 719 | 0.35× |
| Kotlin/Java (JVM) | `jsonsmart-dom` | dom | 406 | 340 | 535 | 639 | 538 | 270 | 313 | 785 | 0.33× |
| Kotlin/Java (JVM) | `quartz-kotlinx` | typed | 398 | 354 | 348 | 416 | 629 | 346 | 500 | 651 | 0.32× |
| Go | `jsoniter-typed-compat` | typed | 477 | 338 | 379 | 597 | 657 | 299 | 341 | 571 | 0.32× |
| Kotlin/Java (JVM) | `minimaljson-dom` | dom | 402 | 399 | 423 | 476 | 494 | 287 | 378 | 478 | 0.30× |
| Go | `segmentio-typed` | typed | 384 | 263 | 309 | 575 | 757 | 230 | 299 | 771 | 0.30× |
| Rust | `nanoserde-typed` | typed | 367 | 325 | 335 | 385 | 527 | 399 | 436 | 491 | 0.29× |
| Go | `jsonv2-typed` | typed | 372 | 283 | 283 | 464 | 668 | 293 | 353 | 558 | 0.29× |
| Python | `json-stdlib` | typed | 318 | 285 | 247 | 306 | 590 | 227 | 422 | 871 | 0.27× |
| JavaScript (Deno) | `simdjson-parse` | typed | 277 | 218 | 196 | 288 | 675 | 320 | 447 | 657 | 0.26× |
| Python | `simplejson` | typed | 295 | 278 | 236 | 289 | 581 | 189 | 355 | 868 | 0.25× |
| Go | `jsonparser-typed` | typed | 313 | 256 | 300 | 490 | 541 | 240 | 195 | 613 | 0.25× |
| Kotlin/Java (JVM) | `gson-tree` | dom | 271 | 280 | 286 | 300 | 348 | 266 | 321 | 360 | 0.22× |
| Go | `ojg-dom` | dom |  |  |  | 393 | 601 |  |  | 588 | 0.22× |
| Kotlin/Java (JVM) | `gson-streaming` | typed | 250 | 251 | 254 | 267 | 296 | 238 | 277 | 296 | 0.19× |
| Rust | `struson-typed` | typed | 238 | 216 | 212 | 259 | 342 | 252 | 293 | 319 | 0.19× |
| JavaScript (Bun) | `jsonjoy-decoder` | typed |  |  |  | 257 | 482 |  |  | 596 | 0.17× |
| Rust | `tinyjson-dom` | dom | 175 | 161 | 153 | 174 | 224 | 185 | 206 | 228 | 0.14× |
| C/C++ | `nlohmann-sax-typed` | typed | 168 | 143 | 149 | 165 | 196 | 179 | 188 | 192 | 0.13× |
| C/C++ | `jsonc-dom` | dom | 233 | 199 | 177 | 233 | 155 | 84 | 111 | 210 | 0.12× |
| JavaScript (Node) | `jsonjoy-decoder` | typed |  |  |  | 220 | 339 |  |  | 335 | 0.12× |
| Go | `stdlib-typed` | typed | 159 | 135 | 122 | 162 | 212 | 163 | 172 | 214 | 0.12× |
| C/C++ | `nlohmann-dom` | dom | 151 | 139 | 135 | 155 | 192 | 174 | 189 | 193 | 0.12× |
| JavaScript (Deno) | `jsonjoy-decoder` | typed |  |  |  | 219 | 305 |  |  | 302 | 0.11× |
| Go | `stdlib-map` | dom | 144 | 127 | 107 | 141 | 195 | 140 | 163 | 209 | 0.11× |
| Go | `gjson-typed` | typed | 156 | 145 | 139 | 155 | 154 | 146 | 172 | 134 | 0.11× |
| JavaScript (Bun) | `streamparser-json` | typed | 121 | 93 | 90 | 139 | 266 | 86 | 130 | 318 | 0.10× |
| JavaScript (Node) | `streamparser-json` | typed | 110 | 98 | 79 | 119 | 242 | 88 | 132 | 297 | 0.10× |
| C/C++ | `jansson-dom` | dom | 89 | 89 | 89 | 98 | 117 | 98 | 102 | 117 | 0.07× |
| JavaScript (Deno) | `streamparser-json` | typed | 80 | 73 | 57 | 85 | 181 | 62 | 88 | 219 | 0.07× |
| JavaScript (Deno) | `simdjson-lazy` | dom | 5 | 93 | 85 | 102 | 184 | 137 | 165 | 339 | 0.07× |
| Kotlin/Java (JVM) | `orgjson-dom` | dom | 54 | 53 | 53 | 54 | 54 | 55 | 54 | 55 | 0.04× |
| JavaScript (Bun) | `simdjson-lazy` | dom | 165 | 23 | 20 | 21 | 29 | 23 | 30 | 85 | 0.03× |
| Kotlin/Java (JVM) | `simdjson-java-schema` | typed | 20 | 21 | 13 | 15 | 39 | 20 | 47 | 181 | 0.02× |

## Variants — `all` workload

Other ways the same library was run (another entry point or input path) and rows a harness flags as variants (breakdowns, an input that isn't the frame type). Each library's headline row above is its fastest; × is this row's speed relative to it, – when the row has no headline row of its own.

| language | library | mode | headline row | variant | input | ns/event | × headline |
|---|---|---|---|---|---|---:|---:|
| C/C++ | Boost.JSON | dom | `boostjson-dom-pool` | `boostjson-dom` | string | 965 | 0.61× |
| C/C++ | nlohmann/json | typed | `nlohmann-sax-typed` | `nlohmann-typed` | string | 6,106 | 0.84× |
| C/C++ | rapidjson | dom | `rapidjson-insitu` | `rapidjson-dom-pool` | string | 782 | 0.67× |
| C/C++ | rapidjson | dom | `rapidjson-insitu` | `rapidjson-dom` | string | 971 | 0.54× |
| C/C++ | yyjson | dom | `yyjson-dom-pool` | `yyjson-insitu` | bytes-copy | 408 | 0.89× |
| C/C++ | yyjson | dom | `yyjson-dom-pool` | `yyjson-dom` | string | 432 | 0.84× |
| Go | bytedance/sonic | typed | `sonic-typed` | `sonic-typed-std` | bytes→string | 1,063 | 0.69× |
| Go | json-iterator/go | typed | `jsoniter-typed-compat` | `jsoniter-typed` | bytes | 1,913 | 0.95× |
| JavaScript (Bun) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,364 | – |
| JavaScript (Bun) | Bun.JSONL.parse | typed | `bun-jsonl-string` | `bun-jsonl-bytes` | string→bytes | 1,409 | 0.72× |
| JavaScript (Deno) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,612 | – |
| JavaScript (Node) | Buffer.toString + JSON.parse | typed | – | `json-parse-buffer` | bytes | 1,375 | – |
| Kotlin/Java (JVM) | dsl-json | typed | `dsljson-bytes` | `dsljson-reflect` | string→bytes | 1,341 | 0.84× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reader` | `fastjson2-reflect-string` | string | 963 | 0.96× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reader` | `fastjson2-reflect-bytes` | string→bytes | 1,135 | 0.81× |
| Kotlin/Java (JVM) | gson | typed | `gson-streaming` | `gson-reflect` | string | 3,715 | 0.93× |
| Kotlin/Java (JVM) | jackson-core | typed | `jackson-streaming-bytes` | `jackson-streaming-string` | string | 1,359 | 0.89× |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-plain` | string | 1,359 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop` | string | 1,498 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-intern` | string | 3,789 | – |
| Kotlin/Java (JVM) | kotlinx-serialization-json | typed | `kotlinx-stream` | `kotlinx-typed` | string | 1,851 | 0.99× |
| Python | msgspec | typed | `msgspec-typed` | `msgspec-generic` | string | 1,138 | 0.87× |
| Rust | jiter | dom | `jiter-value-scratch` | `jiter-value` | string | 743 | 0.88× |
| Rust | jiter | typed | `jiter-typed` | `jiter-serde-typed` | string | 783 | 0.92× |
| Rust | simd-json | dom | `simd_json-tape` | `simd_json-borrowed` | bytes-copy | 792 | 0.56× |
| Rust | simd-json | dom | `simd_json-tape` | `simd_json-owned-value` | bytes-copy | 1,102 | 0.40× |
| Rust | sonic-rs | typed | `sonic_rs-typed` | `sonic_rs-lazy` | string | 1,247 | 0.51× |

## Interning: parse cost vs. memory kept (JVM)

`quartz-jackson` is Quartz's Jackson parse (databind → `EventDeserializer` → `EventFactory`). `quartz-loop` is the same loop on a bare jackson-core parser and `quartz-loop-plain` also drops `EventFactory`. `quartz-loop-intern` adds the `String.intern()` on id, pubkey and every tag value that Quartz 1.19.0 and earlier did while parsing. *Retained* is the heap still held after parsing the whole workload and keeping every event.

| workload | quartz-jackson ns/ev | loop ns/ev | loop + intern ns/ev | plain ns/ev | retained B/ev (no sharing) | retained B/ev (interned) | interning saves |
|---|---:|---:|---:|---:|---:|---:|---:|
| all | 1,593 | 1,498 | 3,789 | 1,359 | 1,376 | 900 | 34.6% |
| notes | 1,807 | 1,681 | 4,001 | 1,554 | 1,705 | 1,030 | 39.6% |
| reactions | 1,082 | 1,025 | 2,450 | 884 | 1,049 | 572 | 45.5% |
| dms | 1,019 | 950 | 2,003 | 819 | 922 | 641 | 30.5% |
| giftwraps | 1,728 | 1,668 | 2,615 | 1,531 | 1,977 | 1,804 | 8.8% |
| metadata | 1,805 | 1,781 | 2,353 | 1,588 | 1,232 | 1,153 | 6.4% |
| zaps | 3,611 | 3,534 | 8,023 | 3,251 | 2,849 | 2,231 | 21.7% |
| lists | 10,183 | 10,648 | 17,886 | 9,722 | 11,350 | 8,814 | 22.3% |

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
| JavaScript (Node) | `simdjson-parse` | all | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | dms | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | giftwraps | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | lists | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | metadata | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | notes | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | reactions | 0 | child exited with SIGSEGV |
| JavaScript (Node) | `simdjson-parse` | zaps | 0 | child exited with SIGSEGV |

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
| Kotlin/Java (JVM) | JDK | 21.0.11+10-b1163.116 | GPL-2.0-only WITH Classpath-exception-2.0 |
| Kotlin/Java (JVM) | Quartz JacksonMapper (jackson-databind + EventDeserializer) | 2.1.0 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | Quartz Kite (hand-written reader, JsonEngine.KITE) | 2.1.0 | MIT |
| Kotlin/Java (JVM) | Quartz KotlinSerializationMapper (kotlinx-serialization-json) | 2.1.0 | Apache-2.0 AND MIT |
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
