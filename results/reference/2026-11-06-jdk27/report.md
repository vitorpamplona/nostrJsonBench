# nostrJsonBench report

Corpus `corpus/nostr-2024-startup.ndjson.gz` — 31,161 unique events, fingerprint `f995f42b68b5386e`, raw bytes as received.

| harness | runtime | host | settings |
|---|---|---|---|
| jvm | Eclipse Adoptium OpenJDK 64-Bit Server VM 27.0.1.0.2+1, GC G1, -Xmx2g -XX:+UseG1GC --add-modules=jdk.incubator.vector, one JVM per parser, Quartz 2.2.0 | Intel(R) Xeon(R) Processor @ 2.10GHz ×4 | warmup 1000 ms, ≥2000 ms, ≥5 rounds |

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
| 1 | Kotlin/Java (JVM) | `quartz-kite` | string | 1,318 | 657 | 1.00× | 1,294 | 21.3 | 21.3 | 0.0 | 5 / 8 ms | 1,258 | MIT |
| 2 | Kotlin/Java (JVM) | `jsoniter-scala` | string (readFromString) | 961 | 902 | 0.73× | 2,720 | 30.8 | 25.8 | 5.0 | 6 / 11 ms | 1,335 | MIT |
| 3 | Kotlin/Java (JVM) | `fastjson2-reader` | string→bytes | 702 | 1,234 | 0.53× | 3,034 | 33.4 | 26.3 | 7.1 | 7 / 11 ms | 1,342 | Apache-2.0 |
| 4 | Kotlin/Java (JVM) | `jackson-streaming-bytes` | string→bytes | 605 | 1,433 | 0.46× | 3,166 | 41.2 | 25.3 | 15.9 | 12 / 18 ms | 1,323 | Apache-2.0 |
| 5 | Kotlin/Java (JVM) | `dsljson-reflect` | string→bytes | 560 | 1,548 | 0.42× | 2,654 | 37.2 | 25.8 | 11.4 | 8 / 13 ms | 1,335 | BSD-3-Clause |
| 6 | Kotlin/Java (JVM) | `quartz-jackson` | string | 526 | 1,646 | 0.40× | 2,217 | 43.1 | 25.5 | 17.6 | 7 / 10 ms | 1,333 | Apache-2.0 AND MIT |
| 7 | Kotlin/Java (JVM) | `jsoniter-iterator` | string→bytes | 525 | 1,649 | 0.40× | 2,794 | 44.7 | 25.8 | 18.9 | 9 / 12 ms | 1,335 | MIT |
| 8 | Kotlin/Java (JVM) | `quartz-kotlinx` | string | 497 | 1,744 | 0.38× | 4,217 | 115.2 | 25.5 | 89.7 | 9 / 15 ms | 1,333 | Apache-2.0 AND MIT |
| 9 | Kotlin/Java (JVM) | `parsson-streaming` | string | 466 | 1,860 | 0.35× | 1,937 | 55.3 | 25.8 | 29.5 | 5 / 7 ms | 1,335 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| 10 | Kotlin/Java (JVM) | `kotlinx-typed` | string | 466 | 1,860 | 0.35× | 2,804 | 54.9 | 25.4 | 29.5 | 9 / 13 ms | 1,324 | Apache-2.0 |
| 11 | Kotlin/Java (JVM) | `moshi-streaming` | string (okio writeUtf8) | 446 | 1,943 | 0.34× | 2,959 | 69.1 | 25.3 | 43.8 | 6 / 9 ms | 1,323 | Apache-2.0 |
| 12 | Kotlin/Java (JVM) | `gson-reflect` | string | 277 | 3,123 | 0.21× | 5,313 | 60.5 | 25.8 | 34.6 | 9 / 13 ms | 1,335 | Apache-2.0 |
| 13 | Kotlin/Java (JVM) | `simdjson-java-schema` | string→bytes-padded | 21 | 42,189 | 0.02× | 41,188 | 1,178.6 | 25.8 | 1,152.8 | 19 / 29 ms | 1,336 | Apache-2.0 |

## DOM: the library's generic tree (not converted into an Event) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | Kotlin/Java (JVM) | `simdjson-java-dom` | string→bytes-padded | 829 | 1,045 | 0.63× | 3,346 | 8.1 | – | – | 5 / 12 ms | – | Apache-2.0 |
| 2 | Kotlin/Java (JVM) | `fastjson2-dom` | string→bytes | 673 | 1,288 | 0.51× | 3,433 | 42.9 | 39.4 | 3.6 | 8 / 12 ms | 1,793 | Apache-2.0 |
| 3 | Kotlin/Java (JVM) | `jackson-tree` | string→bytes | 553 | 1,566 | 0.42× | 4,112 | 68.8 | 53.5 | 15.3 | 5 / 8 ms | 2,110 | Apache-2.0 |
| 4 | Kotlin/Java (JVM) | `kotlinx-element` | string | 432 | 2,004 | 0.33× | 3,679 | 87.7 | 72.5 | 15.2 | 9 / 14 ms | 2,621 | Apache-2.0 |
| 5 | Kotlin/Java (JVM) | `jsonsmart-dom` | string | 412 | 2,102 | 0.31× | 2,307 | 57.2 | 52.5 | 4.7 | 5 / 7 ms | 2,103 | Apache-2.0 |
| 6 | Kotlin/Java (JVM) | `minimaljson-dom` | string | 315 | 2,751 | 0.24× | 4,740 | 79.2 | 70.0 | 9.1 | 11 / 16 ms | 2,402 | MIT |
| 7 | Kotlin/Java (JVM) | `gson-tree` | string | 252 | 3,433 | 0.19× | 6,134 | 85.9 | 75.0 | 10.9 | 10 / 14 ms | 2,634 | Apache-2.0 |
| 8 | Kotlin/Java (JVM) | `orgjson-dom` | string | 48 | 18,064 | 0.04× | 5,718 | 129.6 | 57.1 | 72.6 | 2 / 3 ms | 2,175 | LicenseRef-Public-Domain |

## Baselines (not parsers) — `all` workload

| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | objects created/event | objects kept/event | garbage objects/event | GC | retained B/event | license |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| 1 | Kotlin/Java (JVM) | `baseline-utf8-encode` | string | 3,952 | 219 | 3.00× | 1,113 | 1.1 | – | – | 5 / 13 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |
| 2 | Kotlin/Java (JVM) | `baseline-utf8-decode` | bytes | 3,745 | 231 | 2.84× | 1,199 | 3.1 | – | – | 8 / 13 ms | – | GPL-2.0-only WITH Classpath-exception-2.0 |

## Every workload (MB/s)

Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative to `quartz-kite` — one number per parser that doesn't let the big `all` workload dominate.

| language | parser | mode | all | notes | reactions | dms | giftwraps | metadata | zaps | lists | geomean × quartz |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Kotlin/Java (JVM) | `quartz-kite` | typed | 1,318 | 1,187 | 1,393 | 2,290 | 3,427 | 749 | 1,255 | 3,438 | 1.00× |
| Kotlin/Java (JVM) | `jsoniter-scala` | typed | 961 | 804 | 1,190 | 1,702 | 1,669 | 573 | 842 | 1,658 | 0.66× |
| Kotlin/Java (JVM) | `fastjson2-reader` | typed | 702 | 589 | 1,030 | 1,524 | 1,495 | 429 | 661 | 1,524 | 0.54× |
| Kotlin/Java (JVM) | `simdjson-java-dom` | dom | 829 | 677 | 771 | 1,060 | 1,162 | 567 | 874 | 1,293 | 0.53× |
| Kotlin/Java (JVM) | `fastjson2-dom` | dom | 673 | 550 | 831 | 1,114 | 1,284 | 425 | 586 | 1,420 | 0.48× |
| Kotlin/Java (JVM) | `jackson-streaming-bytes` | typed | 605 | 512 | 596 | 746 | 1,019 | 518 | 739 | 1,094 | 0.42× |
| Kotlin/Java (JVM) | `jackson-tree` | dom | 553 | 465 | 558 | 643 | 924 | 473 | 657 | 923 | 0.38× |
| Kotlin/Java (JVM) | `jsoniter-iterator` | typed | 525 | 495 | 685 | 847 | 760 | 413 | 486 | 873 | 0.37× |
| Kotlin/Java (JVM) | `dsljson-reflect` | typed | 560 | 448 | 735 | 902 | 850 | 319 | 462 | 900 | 0.37× |
| Kotlin/Java (JVM) | `quartz-kotlinx` | typed | 497 | 455 | 477 | 691 | 1,026 | 380 | 484 | 1,272 | 0.37× |
| Kotlin/Java (JVM) | `quartz-jackson` | typed | 526 | 492 | 528 | 639 | 861 | 451 | 569 | 748 | 0.36× |
| Kotlin/Java (JVM) | `parsson-streaming` | typed | 466 | 452 | 496 | 548 | 703 | 369 | 391 | 795 | 0.31× |
| Kotlin/Java (JVM) | `kotlinx-typed` | typed | 466 | 373 | 416 | 570 | 679 | 371 | 515 | 604 | 0.30× |
| Kotlin/Java (JVM) | `moshi-streaming` | typed | 446 | 447 | 424 | 550 | 711 | 280 | 412 | 680 | 0.29× |
| Kotlin/Java (JVM) | `kotlinx-element` | dom | 432 | 401 | 485 | 520 | 653 | 327 | 454 | 609 | 0.29× |
| Kotlin/Java (JVM) | `jsonsmart-dom` | dom | 412 | 351 | 478 | 624 | 544 | 257 | 315 | 707 | 0.27× |
| Kotlin/Java (JVM) | `minimaljson-dom` | dom | 315 | 296 | 313 | 352 | 362 | 257 | 310 | 364 | 0.19× |
| Kotlin/Java (JVM) | `gson-tree` | dom | 252 | 266 | 251 | 260 | 343 | 228 | 328 | 345 | 0.17× |
| Kotlin/Java (JVM) | `gson-reflect` | typed | 277 | 239 | 252 | 253 | 315 | 232 | 285 | 363 | 0.17× |
| Kotlin/Java (JVM) | `orgjson-dom` | dom | 48 | 56 | 52 | 55 | 50 | 49 | 53 | 49 | 0.03× |
| Kotlin/Java (JVM) | `simdjson-java-schema` | typed | 21 | 22 | 14 | 17 | 41 | 21 | 48 | 174 | 0.02× |

## Variants — `all` workload

Other ways the same library was run (another entry point or input path) and rows a harness flags as variants (breakdowns, an input that isn't the frame type). Each library's headline row above is its fastest; × is this row's speed relative to it, – when the row has no headline row of its own.

| language | library | mode | headline row | variant | input | ns/event | × headline |
|---|---|---|---|---|---|---:|---:|
| Kotlin/Java (JVM) | dsl-json | typed | `dsljson-reflect` | `dsljson-bytes` | string→bytes | 1,555 | 1.00× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reader` | `fastjson2-reflect-string` | string | 1,421 | 0.87× |
| Kotlin/Java (JVM) | fastjson2 | typed | `fastjson2-reader` | `fastjson2-reflect-bytes` | string→bytes | 1,505 | 0.82× |
| Kotlin/Java (JVM) | gson | typed | `gson-reflect` | `gson-streaming` | string | 3,190 | 0.98× |
| Kotlin/Java (JVM) | jackson-core | typed | `jackson-streaming-bytes` | `jackson-streaming-string` | string | 1,544 | 0.93× |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-plain` | string | 1,472 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop` | string | 1,608 | – |
| Kotlin/Java (JVM) | jackson-core + Quartz's EventDeserializer loop | typed | – | `quartz-loop-intern` | string | 3,586 | – |
| Kotlin/Java (JVM) | kotlinx-serialization-json | typed | `kotlinx-typed` | `kotlinx-stream` | string→bytes | 2,029 | 0.92× |

## Interning: parse cost vs. memory kept (JVM)

`quartz-jackson` is Quartz's Jackson parse (databind → `EventDeserializer` → `EventFactory`). `quartz-loop` is the same loop on a bare jackson-core parser and `quartz-loop-plain` also drops `EventFactory`. `quartz-loop-intern` adds the `String.intern()` on id, pubkey and every tag value that Quartz 1.19.0 and earlier did while parsing. *Retained* is the heap still held after parsing the whole workload and keeping every event.

| workload | quartz-jackson ns/ev | loop ns/ev | loop + intern ns/ev | plain ns/ev | retained B/ev (no sharing) | retained B/ev (interned) | interning saves |
|---|---:|---:|---:|---:|---:|---:|---:|
| all | 1,646 | 1,608 | 3,586 | 1,472 | 1,333 | 883 | 33.7% |
| notes | 1,838 | 1,600 | 3,334 | 1,712 | 1,645 | 1,018 | 38.1% |
| reactions | 1,059 | 1,100 | 2,196 | 953 | 1,000 | 552 | 44.8% |
| dms | 1,002 | 850 | 1,779 | 804 | 891 | 620 | 30.5% |
| giftwraps | 1,974 | 1,972 | 2,808 | 1,934 | 1,956 | 1,792 | 8.4% |
| metadata | 1,943 | 1,855 | 2,315 | 1,845 | 1,213 | 1,138 | 6.2% |
| zaps | 3,751 | 4,003 | 7,275 | 3,500 | 2,805 | 2,216 | 21.0% |
| lists | 11,409 | 12,686 | 17,404 | 12,277 | 11,123 | 8,791 | 21.0% |

## Libraries

| language | library | version | license |
|---|---|---|---|
| Kotlin/Java (JVM) | Eclipse Parsson (Jakarta JSON-P) | 1.1.9 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 |
| Kotlin/Java (JVM) | JDK | 27.0.1.0.2+1 | GPL-2.0-only WITH Classpath-exception-2.0 |
| Kotlin/Java (JVM) | Quartz JacksonMapper (jackson-databind + EventDeserializer) | 2.2.0 | Apache-2.0 AND MIT |
| Kotlin/Java (JVM) | Quartz Kite (hand-written reader, JsonEngine.KITE) | 2.2.0 | MIT |
| Kotlin/Java (JVM) | Quartz KotlinSerializationMapper (kotlinx-serialization-json) | 2.2.0 | Apache-2.0 AND MIT |
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
| Kotlin/Java (JVM) | simdjson-java | 0.4.0 | Apache-2.0 |
