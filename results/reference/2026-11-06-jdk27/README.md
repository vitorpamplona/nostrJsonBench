# JVM rows on JDK 27, 2026-11-06

Every table: [report.md](report.md). Raw rows: `jvm.json`.

The JVM harness moved from JetBrains Runtime 21 to JDK 27 (Kotlin 2.5.0-Beta2, Java 27
bytecode, Temurin 27.0.1 runtime). This directory holds that harness's run of every JVM row on
every workload, and a same-host A/B against the JDK 21 build. The root README's table keeps
the [2026-10-22](../2026-10-22/README.md) and [2026-10-23](../2026-10-23/README.md) JVM
rows: this run landed on a slower host, so its numbers don't compare with the other
runtimes' rows.

- **Settings:** `./run.sh` defaults: 1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
  workload, frames built fresh in bursts of 64. Three timed JVMs per parser, median kept.
  One parser at a time on an idle machine.
- **Machine:** cloud VM, Intel Xeon @ 2.1 GHz, 4 cores, Linux 6.18, the reference runs' host
  type. One host for the whole run (one boot id, recorded per parser).
- **Host speed:** the Go control (`control/`: `goccy-typed`, `easyjson-typed`,
  `fastjson-dom`, `all` workload) ran `goccy-typed` at 1,017 ns/event at the start, 1,070
  halfway, 1,168 at the end of the JVM pass, and 991 after the A/B, against 924 in the
  reference. This host was 7 to 26% slower than the reference's, and drifted while the pass ran.
- **Runtime:** Temurin 27.0.1 with Amethyst Desktop's flags (`-Xmx2g`, G1), plus
  `--add-modules=jdk.incubator.vector` as before.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`
  (`manifest.json`, unchanged).
- **Code:** nostrJsonBench `af20537`. Quartz 2.2.0, Jackson 2.22.3, kotlinx.serialization
  1.11.0, as on 2026-10-23. Two dependencies changed for JDK 27: simdjson-java 0.3.0 → 0.4.0
  (0.3.0 fails on JDK 27's Vector API) and Scala 3.3.8 → 3.10.0 (3.3.8 can't emit Java 27
  bytecode). The allocation agent, java-allocation-instrumenter 3.3.5, now carries ASM
  9.10.1 in place of its shaded ASM, which can't read Java 27 classes.

## Outcomes

All 256 rows (32 parsers × 8 workloads) matched the reference digest, with no errors. The
JVM rows ranked by ns/event on `all` keep the reference's order at the top: the first four are
the same (`quartz-kite`, `jsoniter-scala`, `simdjson-java-dom`, `fastjson2-reader`). No row
moved more than five places (`dsljson-bytes` and `jsoniter-iterator`, 7th → 12th and 11th →
16th; rows 7 to 16 span only 1,433 to 1,649 ns/event).

## Does JDK 27 parse faster?

No. Three rounds on one host, alternating the JDK 21 build (`a015e75`, on JetBrains Runtime
21.0.11 b1163.116, the reference's runtime) and the JDK 27 build (`ab/`). Seven parsers,
the three Quartz rows and four other libraries' rows, on `all`, `notes` and
`reactions`. Each round is three JVMs per parser, median kept. The median of the three
rounds:

| parser | workload | JBR 21 ns/event (range) | JDK 27 ns/event (range) | change | JBR 21 alloc B/event | JDK 27 alloc B/event |
|---|---|---:|---:|---:|---:|---:|
| `quartz-kite` | all | 650 (632–716) | 606 (603–644) | −6.8% | 1,321 | 1,294 |
| `quartz-kite` | notes | 830 (818–834) | 801 (743–854) | −3.5% | 1,573 | 1,545 |
| `quartz-kite` | reactions | 423 (389–436) | 415 (406–417) | −1.9% | 906 | 879 |
| `quartz-jackson` | all | 1,652 (1,488–1,704) | 1,696 (1,594–1,709) | +2.7% | 2,332 | 2,217 |
| `quartz-kotlinx` | all | 1,818 (1,778–2,334) | 1,779 (1,756–2,352) | −2.2% | 4,476 | 4,217 |
| `fastjson2-reader` | all | 1,199 (1,106–1,202) | 1,215 (1,192–1,268) | +1.3% | 3,096 | 3,034 |
| `dsljson-bytes` | all | 1,459 (1,330–1,483) | 1,592 (1,447–1,679) | +9.2% | 2,542 | 2,490 |
| `jackson-streaming-string` | all | 1,624 (1,572–1,682) | 1,513 (1,482–1,660) | −6.8% | 2,076 | 1,981 |
| `kotlinx-typed` | all | 2,207 (2,069–2,296) | 2,100 (2,050–2,142) | −4.8% | 2,943 | 2,804 |

- **Speed:** within noise. Over all 21 rows the geometric mean is 1.5% in JDK 27's favour,
  but the three rounds disagree on the sign: −3.6%, −3.4%, +3.7%. The two ranges overlap
  on every row but one: `quartz-jackson` on `notes` was slower on JDK 27 in every round
  (1,854–1,991 ns/event against 1,791–1,821, +7.0%). `quartz-kite` was faster in all three rounds on `all` (606, 603, 644 against 632,
  716, 650), by less than the rounds spread.
- **Allocation:** JDK 27 allocates 1.8 to 7.2% fewer bytes per event on every one of the 21
  rows, the same in every round (geometric mean −4.3%). The full pass shows the same against
  the reference: −3.9% on `all`, simdjson-java aside.
- **Objects created** are the same on both JDKs for these seven parsers.

So this run's higher ns/event against the reference (`quartz-kite` 657 against 561 on
`all`) comes from the host, not from JDK 27.

## What else changed in the full pass

- **simdjson-java 0.4.0:** `simdjson-java-dom` creates 8.1 objects per event against 36.5,
  but allocates 3,346 B/event against 2,390. `simdjson-java-schema` still calls
  `ClassResolver.reset()` on every parse and is still last, at 1,179 objects and 41,188 B
  per event against 1,960 and 62,759.
- **One more object per String decoded from bytes.** JDK 27's private
  `String(Charset, byte[], int, int)` decodes into a temporary String and copies it
  (`this(String)`), where JDK 21 filled in its own fields. The agent counts bytecode-level
  allocations, so it now sees two Strings for each `new String(bytes, UTF_8)`. That adds
  exactly one object per event to `baseline-utf8-decode`, `gson-*`, `minimaljson-dom` and
  `parsson-streaming`, and 17.7 to `moshi-streaming`, which decodes every string it reads
  from bytes (on `dms`, the per-class count, `-Djsonbench.allocClasses=true`, puts all 6.6
  extra objects per event on Strings, one per `byte[]` read). The JIT removes the temporary: these rows' `alloc B/event` went down.
- **Retained:** unchanged on every row, except `orgjson-dom`, 58.0 → 57.1 objects kept per
  event.
