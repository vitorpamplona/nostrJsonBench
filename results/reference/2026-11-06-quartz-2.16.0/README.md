# Quartz 2.2.0 against 2.16.0, 2026-11-06

The JVM harness moved from Quartz 2.2.0 (Maven Central) to Quartz 2.16.0, the JitPack build of
the `v2.16.0` tag: 2.16.0 is not on Maven Central. This directory holds two same-host A/Bs of
the two releases, run before the root README's table was left on its 2026-10-23 Quartz rows.
That table's numbers come from another host, so these can't replace them; what they show is
whether the release changes anything.

- **Settings:** `./run.sh` defaults (1 s warm-up, ≥ 2 s and ≥ 5 rounds, bursts of 64, three
  JVMs per parser, median kept), every workload. Three rounds, each one run of the 2.2.0 build
  followed by one of the 2.16.0 build, with the Go control (`go-control-*`) before and after.
- **Runtime:** Temurin 27.0.1, `-Xmx2g`, G1, as in the [JDK 27 rerun](../2026-11-06-jdk27/README.md).
- **`kite/`:** `quartz-kite`. 2.2.0: nostrJsonBench `af20537`. 2.16.0: the same harness with
  Quartz swapped for the JitPack jar, which reports the version as `v2.16.0`. One host.
- **`others/`:** `quartz-kotlinx`, `quartz-loop`, `quartz-loop-plain`. 2.2.0: nostrJsonBench
  `a805155`. 2.16.0: the commit that moved the harness to it. Another host, one for all three
  rounds.
- Every row on both releases matched the corpus digest, with no errors.

## What changed between the two releases

`quartz-kite` and `quartz-kotlinx` time the same call on both: `OptimizedJsonMapper.fromJson` (on
2.2.0 under `JsonEngine.KITE`; 2.4.0 removed the switch and Kite is the only engine) and
`KotlinSerializationMapper.fromJson`. Kite's event reader is the same code in both releases.
`EventFactory`, which every Quartz row calls to build the kind's class, knows more kinds in
2.16.0 (drss, PressStr); its four `createPart` methods grew to at most 3,995 bytes of bytecode,
against 3,905, still well under the 8,000 bytes above which HotSpot won't compile a method.
`quartz-kotlinx` also runs on kotlinx.serialization 1.12.0, the version 2.16.0 uses, against
1.11.0.

`quartz-jackson` has no 2.16.0 row: Quartz dropped Jackson, and `JacksonMapper` with it, in 2.4.0.

## Results

ns/event, median of the three rounds (range):

| row | workload | 2.2.0 | 2.16.0 | change |
|---|---|---:|---:|---:|
| `quartz-kite` | all | 676 (647–711) | 679 (663–684) | +0.5% |
| `quartz-kite` | notes | 799 (795–840) | 812 (799–820) | +1.7% |
| `quartz-kite` | reactions | 396 (391–417) | 388 (371–409) | −2.2% |
| `quartz-kite` | dms | 307 (302–316) | 301 (299–312) | −1.7% |
| `quartz-kite` | giftwraps | 527 (509–569) | 527 (517–527) | 0.0% |
| `quartz-kite` | metadata | 1,192 (1,189–1,235) | 1,199 (1,162–1,278) | +0.6% |
| `quartz-kite` | zaps | 1,721 (1,700–1,757) | 1,772 (1,763–1,787) | +3.0% |
| `quartz-kite` | lists | 2,599 (2,584–2,615) | 2,581 (2,524–2,638) | −0.7% |
| `quartz-kotlinx` | all | 1,865 (1,761–2,282) | 1,791 (1,783–1,831) | −4.0% |
| `quartz-loop` | all | 1,687 (1,661–1,736) | 1,652 (1,597–1,685) | −2.1% |
| `quartz-loop-plain` | all | 1,711 (1,705–1,727) | 1,651 (1,638–1,668) | −3.5% |

Geometric mean over the eight workloads: `quartz-kite` +0.1%, `quartz-kotlinx` −3.3%,
`quartz-loop` +0.3%, `quartz-loop-plain` −1.7%.

- **No row changed speed beyond noise.** `quartz-loop-plain` sets the noise floor: it never
  calls Quartz, so both builds run the same code, and it still came out 2.6 to 8.6% apart on
  `all`, `metadata` and `zaps` with the rounds' ranges not overlapping. The host drifted too:
  the Go control's `easyjson-typed` ran 1,612 ns/event before `others/` and 1,441 after.
  Against that, Kite's one consistent difference, `zaps` at +3.0% (slower on 2.16.0 in all three
  rounds), is within what identical code shows.
- **`quartz-kotlinx`** −3.3% comes mostly from one slow 2.2.0 round on every workload (−9 to
  −54% in round 1); the other two rounds are within ±4%. Its JVMs spread widely, as in every
  earlier run.
- **Allocation:** identical on 2.16.0 for Kite and the loop rows, on every workload: bytes,
  objects created, objects kept. `quartz-kotlinx` allocates 24 B more per event on every
  workload, with the same objects created and kept.
