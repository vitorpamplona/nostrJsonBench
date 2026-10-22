# nostrJsonBench: JVM harness

Which JSON parser on the JVM decodes Nostr events fastest, with the least garbage, and
how does that compare with what Amethyst ships (`quartz-kite`)? This is the `jvm`
harness of [nostrJsonBench](../HARNESS.md); it follows that contract (flags, digest, timing,
output schema).

## Run

```bash
python3 corpus/prepare.py        # once: builds .corpus/
./jvm/run.sh                     # full run -> results/jvm.json
./jvm/run.sh --quick             # smoke run (seconds per parser, noisy numbers)
./jvm/run.sh --parsers quartz-kite,quartz-jackson,fastjson2-reader --workloads all,notes
./jvm/run.sh --list              # parser ids
```

`run.sh` builds this directory's standalone Gradle project (`./gradlew installDist`) and
runs `build/install/jsonbench/bin/jsonbench`. Flags: `--corpus`, `--out`, `--workloads`,
`--parsers`, `--warmup-ms`, `--min-time-ms`, `--min-rounds`, `--burst` (default 64; `--quick`
keeps it), `--quick`, `--list`.
Extra JVM flags go through `JAVA_OPTS`.

## How it measures

- **One JVM per parser.** The process you start only orchestrates: for each parser it
  forks a child with the same `java`, classpath and JVM flags (`-Xmx2g
  -XX:+UseG1GC --add-modules jdk.incubator.vector`). `-Xmx2g` and G1 are what Amethyst
  Desktop runs with; the heap starts small and grows, as in the app. The child verifies and times that
  parser on every selected workload and hands its rows back through a temp file.
- **Three timed JVMs per parser** (`--forks N`, 1 with `--quick`). The JIT sometimes
  compiles a parser into a faster or slower shape in one JVM than in the next, so each row
  is the fork with the median ns/event, and `fork_ns_per_event` lists all of them. A
  second child, started only after the first has exited, counts that parser's
  allocations (see "Objects created" below), so the agent never runs where timing does. The
  harness's own manifest/result JSON goes through a tiny hand-written reader/writer
  (`MiniJson.kt`), so no measured library is loaded or JIT-profiled before its turn.
- **Monomorphic hot loop.** Each parser is its own class whose `parseAll(inputs)` holds
  the loop over a burst; the sink folds `kind + tags.size + content.length` (DOM
  parsers: `kind + tag count`).
- **Arrival.** A round walks the workload in bursts of `--burst` frames (HARNESS.md
  "Arrival"). Per burst, untimed, the harness builds fresh frames from the lines' UTF-8
  bytes with `new String(bytes, UTF_8)`, what OkHttp's `onMessage(webSocket, text)` hands
  the app. Then `System.nanoTime` times the burst's parses, one per frame. The next burst
  overwrites the frame buffer, so the old frames become garbage. A round's time is the sum
  of its bursts; warm-up runs the same rounds. Byte parsers still encode the String inside
  the timed parse. `baseline-utf8-decode` gets a fresh copy of the line's bytes instead.
  Verification, the retained pass and the allocation counter still use Strings preloaded
  once, untimed.
- **Verification first.** Every event is decoded once into an `Event` and fed to the
  SHA-256 digest of HARNESS.md; DOM results are converted to an `Event` for this step
  only. A parser that throws is not timed.
- **Memory.** `alloc_bytes_per_event` covers the timed parses only: the harness reads
  `ThreadMXBean.getCurrentThreadAllocatedBytes` just before and after each burst's timed
  part and sums the deltas, so the frame Strings are not counted. A read costs ~17 ns and
  allocates nothing; two per burst is under 0.1% of a 64-frame burst (~50 µs), and they
  sit outside the timed part. `System.gc()` runs once between warm-up and measurement.
  `gc_count`/`gc_ms` also cover the parses only. They come from a separate untimed pass
  after measurement: as many rounds as were measured, a tight loop over the preloaded
  Strings (no frames built), after a `System.gc()`. They are the summed
  `GarbageCollectorMXBean` deltas over that pass. Each row's `notes` says so.
- **Retained heap.** After timing, the same child parses every event once more with the
  parser's `keep` (its own result type: Quartz's `Event` subclass, a `JsonNode`…) and
  keeps them all in one array. `retained_bytes_per_event` is the used-heap growth between
  full collections (median of three such passes). One more pass counts the live objects:
  a class histogram (the `gcClassHistogram` operation of the
  `com.sun.management:type=DiagnosticCommand` MBean, i.e. `jcmd GC.class_histogram`,
  which runs a full GC and counts live instances only) before and after keeping every
  result. `retained_objects_per_event` is the total instance delta / events, and
  `retained_top_classes` the five classes that add the most instances
  (`[{"class": "java.lang.String", "per_event": 15.2}, …]`). The array that holds the
  results exists before the first histogram, so it is not counted; a pair of histograms
  with nothing kept in between is subtracted class by class to cancel what taking a
  histogram leaves behind.
- **Objects created (`allocs_per_event`).** HotSpot cannot count allocations, so after the
  timed child exits the parent starts a **second, untimed child per parser** with
  Google's [java-allocation-instrumenter](https://github.com/google/allocation-instrumenter)
  as `-javaagent`. The agent rewrites the bytecode of every class, the JDK's included, so
  each `new`/`newarray`/`anewarray`/`multianewarray`/`clone()`/`Array.newInstance` reports
  to a sampler; the sampler only counts allocations on the parse thread while a flag is
  set around the parser's own timed loop (`parseAll`). The child runs that loop over the
  whole workload 3 times (class loading, one-time init), then counts 3 more passes and
  reports the fewest objects per event: a pass can only gain spurious objects (a class
  loaded mid-pass after a JIT uncommon trap allocates on the parse thread, and so does
  the agent rewriting it), never lose real ones.
  - **Strings are counted in `String`'s constructors**, not at their `new` sites (the
    agent's `ConstructorInstrumenter`), because a site inside a hidden class (lambda
    proxy, method-handle stub) cannot be rewritten: fastjson2 builds every String through
    a `LambdaMetafactory` function over the JDK's package-private `String(byte[], byte)`
    constructor, so a site count saw ~1 of its ~16 Strings per event. Strings the agent
    makes for itself (the descriptor of a reflectively created array) are taken back out.
  - C2 intrinsics that allocate on their own (`Arrays.copyOf`/`copyOfRange`,
    `Array.newInstance`, `clone`, `StringUTF16.toBytes`,
    `Unsafe.allocateUninitializedArray`) and C2's `StringBuilder` chain rewrite are off in
    that JVM (`-XX:DisableIntrinsic=…`, `-XX:-OptimizeStringConcat`), so the instrumented
    bytecode keeps running once compiled; counts then matched an interpreter-only
    (`-Xint`) run on the parsers checked.
  - The count is **bytecode-level**: it sees every allocation the code asks for *before*
    the JIT's escape analysis removes or scalar-replaces any, so it can be higher than
    what the compiled code really allocates (`alloc_bytes_per_event` is the JIT's real
    number). Still not seen: non-String objects allocated inside hidden classes (a
    capturing lambda's instance) and by native code. Each counted row says how it was
    counted in `objects_note`.
  - Debugging: `JAVA_OPTS=-Djsonbench.allocClasses=true` makes that child print the
    count by class (approximate: the bookkeeping itself is counted for Strings).
- **Garbage per parse** is `allocs_per_event − retained_objects_per_event`; the report
  computes it.

## Parsers

Versions come from `jvm/gradle/libs.versions.toml`. Quartz is Amethyst's Nostr library
(<https://github.com/vitorpamplona/quartz>), pulled from Maven Central
(`com.vitorpamplona.quartz:quartz`). To benchmark an unreleased Quartz, point the build at a
checkout of it: `QUARTZ_DIR=../quartz ./jvm/run.sh` (or `-PquartzDir=…` on Gradle); its
`:quartz` replaces the release through a composite build, and the Quartz rows record the
checkout's commit (`local quartz@<sha>`) as their version. Jackson and
kotlinx.serialization are pinned to the versions that Quartz release uses. Licenses were
checked against each artifact's published POM.

| id | library | version | license | mode | input |
|---|---|---|---|---|---|
| `quartz-kite` | Quartz Kite: `OptimizedJsonMapper.fromJson` with `JsonEngine.KITE`, Quartz's hand-written reader + `EventFactory` (Quartz ≥ 2.1.0; what Amethyst ships) | Quartz | MIT | typed | string |
| `quartz-jackson` | Quartz `JacksonMapper` (jackson-databind + `EventDeserializer` + `EventFactory`; Quartz's JVM default; Quartz ≤ 1.19.0 also interns here) | Quartz | Apache-2.0 (Quartz: MIT) | typed | string |
| `quartz-loop` / `quartz-loop-intern` / `quartz-loop-plain` | breakdown of `quartz-jackson`: its deserializer loop on bare jackson-core (no databind), then with the parse-time `String.intern()` of Quartz ≤ 1.19.0, then with a plain `Event` instead of `EventFactory` | 2.22.3 | Apache-2.0 (Quartz: MIT) | typed | string |
| `quartz-kotlinx` | Quartz `KotlinSerializationMapper` (the iOS/native path) | Quartz | Apache-2.0 (Quartz: MIT) | typed | string |
| `jackson-streaming-bytes` / `-string` | jackson-core `JsonFactory`, hand loop | 2.22.3 | Apache-2.0 | typed | bytes / string |
| `jackson-tree` | jackson-databind `readTree` | 2.22.3 | Apache-2.0 | dom | bytes |
| `kotlinx-typed` / `kotlinx-stream` / `kotlinx-element` | kotlinx-serialization-json | 1.11.0 | Apache-2.0 | typed / typed / dom | string / bytes / string |
| `gson-streaming` / `gson-tree` / `gson-reflect` | Gson | 2.14.0 | Apache-2.0 | typed / dom / typed | string |
| `moshi-streaming` | Moshi `JsonReader` + `Options` over okio | 1.15.2 | Apache-2.0 | typed | bytes |
| `fastjson2-reflect-bytes` / `-string` / `fastjson2-reader` / `fastjson2-dom` | fastjson2 | 2.0.66 | Apache-2.0 | typed / typed / typed / dom | bytes / string / bytes / bytes |
| `dsljson-bytes` / `dsljson-reflect` | DSL-JSON (hand loop / runtime reflection, no annotation processor) | 2.0.2 | BSD-3-Clause | typed | bytes |
| `simdjson-java-dom` / `simdjson-java-schema` | simdjson-java | 0.3.0 | Apache-2.0 | dom / typed | bytes-padded |
| `orgjson-dom` | org.json (JSON-java) | 20260814 | Public domain (`LicenseRef-Public-Domain`) | dom | string |
| `jsoniter-iterator` | jsoniter (Java) iterator API | 0.9.23 | MIT | typed | bytes |
| `jsoniter-scala` | jsoniter-scala, `JsonCodecMaker.make` (Scala 3 macro) | 2.41.6 | MIT (+ Scala 3.3.8 library, Apache-2.0) | typed | bytes |
| `parsson-streaming` | Eclipse Parsson (Jakarta JSON-P) | 1.1.9 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | typed | string |
| `minimaljson-dom` | minimal-json | 0.9.5 | MIT | dom | string |
| `jsonsmart-dom` | json-smart (RFC 4627 mode) | 2.6.0 | Apache-2.0 | dom | string |
| `baseline-utf8-decode` / `-encode` | JDK `String(bytes, UTF_8)` / `encodeToByteArray()` | JDK | GPL-2.0 WITH Classpath-exception | baseline | bytes / string |

Parsson and jakarta.json-api are EPL-2.0 / GPL-2.0-with-Classpath-exception (weak
copyleft); everything else is permissive. None of it ships: this module is never a
dependency of the app.

The allocation counter, `com.google.code.java-allocation-instrumenter:java-allocation-instrumenter`
3.3.5, is Apache-2.0 (its published POM and the jar's `META-INF/LICENSE`); the jar shades
ASM (BSD-3-Clause) and Guava (Apache-2.0). It is a measurement tool, not a parser: the
build only compiles against its `Sampler` API (`compileOnly`) and `installDist` copies the
jar to `build/install/jsonbench/agent/`, outside `lib/`, so it is never on a timed JVM's
class path. Elsewhere, point the harness at the jar with `-Djsonbench.allocAgent=…` or
`JSONBENCH_ALLOC_AGENT`; without it `allocs_per_event` stays null.

## Caveats

- **HotSpot is not ART.** These are C2-compiled numbers on a desktop/server JVM.
  Android runs ART (AOT + JIT, a different GC, no C2); orderings mostly carry over for
  scalar-heavy parsers but absolute numbers do not, and anything that leans on
  HotSpot-only machinery does not apply to the app at all.
- **simdjson-java needs `jdk.incubator.vector`**, which does not exist on Android. It is
  here to show the ceiling. 0.4.0 targets Java 24, so the harness (Java 21) uses 0.3.0,
  whose schema-based parse calls `ClassResolver.reset()` on every document and therefore
  re-resolves the record reflectively per event; that is why `simdjson-java-schema` is
  slow and allocation-heavy. The DOM's `JsonValue` points into a reused tape that the
  next parse overwrites.
- **fastjson2 reflect** generates readers with ASM at runtime; Android has its own
  `fastjson2` artifact variants (`.android5`/`.android8`) that cannot do that.
- **jsoniter-scala** needs the Scala 3 runtime on the classpath; the codec is compiled
  by Gradle's `scala` plugin from `src/main/scala`, and the Kotlin side calls it through
  JDK functional interfaces (`JsoniterScala.kt`).
- **DSL-JSON runtime binding** ignores `@JsonAttribute(name)` on a public field, so the
  POJO's field is literally named `created_at`.
- `orgjson-dom` is the upstream JSON-java library, not the older fork in Android's
  `android.jar`.
- Run on a quiet machine: other processes on the same cores move the medians.
