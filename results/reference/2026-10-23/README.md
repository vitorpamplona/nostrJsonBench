# Reference run, 2026-10-23

Results summary: the [root README](../../../README.md#results). Every table:
[report.md](report.md). Raw rows: `*.json`.

This run re-measures Quartz's six JVM rows on Quartz 2.2.0. In 2.2.0, `EventFactory.create`
is small enough for HotSpot to compile, Kite's hot paths are small enough to inline, and
one- and two-element tags skip Kite's scratch buffer. Every other row is copied unchanged:
the other JVM rows from the [2026-10-22 reference](../2026-10-22/README.md), the other
runtimes from the [2026-10-21 reference](../2026-10-21/README.md).

- **Settings:** `./run.sh` defaults: 1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
  workload, frames built fresh in bursts of 64 (`--burst`). One harness at a time on an
  idle machine.
- **Machine:** cloud VM, Intel Xeon (AVX2 + AVX-512), 4 cores, Linux 6.18, the same host
  type and kernel as the 2026-10-22 run.
- **Runtimes:** JetBrains Runtime 21.0.11 (b1163.116) with Amethyst Desktop's flags
  (`-Xmx2g`, G1), as on 2026-10-22. Three timed JVMs per parser, median kept. Ran
  2026-10-23. The other runtimes are as in the 2026-10-21 reference.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`.
- **Code:** the harnesses at this commit. Quartz is the 2.2.0 release from Maven Central;
  each JVM row records it. Jackson 2.22.3 and kotlinx.serialization 1.11.0, the versions
  Quartz 2.2.0 uses, as before.
- **Rows replaced:** `quartz-kite`, `quartz-jackson`, `quartz-kotlinx`, `quartz-loop`,
  `quartz-loop-plain` and `quartz-loop-intern`, on every workload. All 48 matched the
  reference digest.

`quartz-loop-plain` doesn't call `EventFactory`, so 2.2.0 doesn't change its code. It moved
by 2% or less on every workload, which puts a bound on how much of the other rows' change
is the day rather than the release.

## Quartz's parsers

`all` workload:

| row | 2.1.0 ns/event | 2.2.0 ns/event | change | alloc B/event | objects created/event | objects kept/event | retained B/event |
|---|---:|---:|---:|---:|---:|---:|---:|
| `quartz-kite`: Kite, `OptimizedJsonMapper` under `JsonEngine.KITE` (what Amethyst ships) | 786 | 561 | −29% | 1,321 | 21.3 | 21.3 | 1,288 |
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` (Quartz's JVM default) | 1,593 | 1,409 | −12% | 2,332 | 43.1 | 25.5 | 1,376 |
| `quartz-kotlinx`: kotlinx.serialization (Quartz's native default) | 2,175 | 1,854 | −15% | 4,476 | 115.2 | 25.5 | 1,378 |

- Kite is now the fastest typed parser in the benchmark, across every runtime: 561 ns/event
  against 607 for `simdjson-ondemand-typed`, the fastest C++ row. Only five rows in borrowed
  mode are faster, because they point their strings into the input instead of copying them.
  The fastest of those is `simdjson-ondemand-borrowed` at 406.
- Kite is 2.5× as fast as Quartz's Jackson path on `all`, and 1.6× to 5.0× across the eight
  workloads (geometric mean 2.9×). Allocations didn't change in any Quartz row.
- Kite gained most on the workloads of small events: `dms` −40%, `giftwraps` −37%,
  `reactions` −34%.
- `quartz-kotlinx`'s three JVMs spread widely on every workload (1,406 to 1,992 on `all`),
  so its change is the least certain of the three.

## Where Quartz's Jackson time goes

`notes` workload:

| row | 2.1.0 ns/event | 2.2.0 ns/event | retained B/event | objects kept/event |
|---|---:|---:|---:|---:|
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` | 1,807 | 1,620 | 1,705 | 37.1 |
| `quartz-loop`: without databind's `readValue` | 1,681 | 1,517 | 1,705 | 37.1 |
| `quartz-loop-plain`: without `EventFactory` | 1,554 | 1,536 | 1,697 | 37.1 |
| `quartz-loop-intern`: with Quartz 1.19.0's parse-time `String.intern()` | 4,001 | 3,772 | 1,030 | 16.2 |
| `quartz-kite`, for comparison | 913 | 684 | 1,513 | 28.3 |

- `EventFactory` cost about 130 ns per note in 2.1.0 (`quartz-loop` against
  `quartz-loop-plain`). In 2.2.0 the two rows are within run-to-run noise of each other: the
  factory had been running in the interpreter, because its one method was over HotSpot's
  8,000-byte compile limit.
- `readValue` still costs about 100 ns per note.
