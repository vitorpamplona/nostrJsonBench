# Reference run, 2026-10-22

Results summary: the [root README](../../../README.md#results). Every table:
[report.md](report.md). Raw rows: `*.json`.

This run re-measures the JVM against Quartz 2.1.0 and adds `quartz-kite`, Quartz's
hand-written reader, which Amethyst now ships. The other runtimes' rows are the
[2026-10-21 reference](../2026-10-21/README.md)'s, copied unchanged.

- **Settings:** `./run.sh` defaults: 1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
  workload, frames built fresh in bursts of 64 (`--burst`). One harness at a time on an
  idle machine.
- **Machine:** cloud VM, Intel Xeon (AVX2 + AVX-512), 4 cores, Linux 6.18, the same host
  type and kernel as the 2026-10-21 run.
- **Runtimes:**
  - JVM: JetBrains Runtime 21.0.11 (b1163.116), the JDK Amethyst Desktop ships on Linux,
    with its flags (`-Xmx2g`, G1). Three timed JVMs per parser, median kept. Ran 2026-10-22.
  - Everything else as in the 2026-10-21 reference (ran 2026-10-20): rustc 1.97
    (`-C target-cpu=native`, fat LTO), GCC 13.3 (`-O3 -march=native`), Go 1.24.7,
    Node 22.22, Bun 1.4.3, Deno 2.9.6, CPython 3.13.16.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`.
- **Code:** the harnesses at this commit. Quartz is the 2.1.0 release from Maven Central;
  each JVM row records it.
- **Baseline:** `× quartz` in the report is now relative to `quartz-kite`, Amethyst's
  production parser. It was `quartz-jackson` in earlier runs.

The JVM rows whose code didn't change moved by a few percent between the two runs (median
of the typed `all` rows: 2.0%); `jsonsmart-dom` (+21%) and `gson-tree` (+11%) moved most.

## Quartz's parsers

`all` workload:

| row | ns/event | alloc B/event | objects created/event | objects kept/event | retained B/event |
|---|---:|---:|---:|---:|---:|
| `quartz-kite`: Kite, `OptimizedJsonMapper` under `JsonEngine.KITE` (what Amethyst ships) | 786 | 1,321 | 21.3 | 21.3 | 1,288 |
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` (Quartz's JVM default) | 1,593 | 2,332 | 43.1 | 25.5 | 1,376 |
| `quartz-kotlinx`: kotlinx.serialization (Quartz's native default) | 2,175 | 4,476 | 115.2 | 25.5 | 1,377 |

- Kite parses about twice as fast as Quartz's Jackson path, allocates 43% fewer bytes, and
  creates no garbage: every object it allocates is part of the event it returns.
- Events parsed by Kite also keep less memory (1,288 vs 1,376 B and 21.3 vs 25.5 objects per
  event). Kite hands out one shared `String` for each one-character tag value, such as the
  `e` and `p` tag names, and one shared empty array for events without tags; Jackson makes
  new ones. The corpus has 2.12 one-character tag values per event, and Kite's events keep
  2.12 fewer `String`s (and their `byte[]`s) than Jackson's.

## Where Quartz's Jackson time goes

`notes` workload:

| row | ns/event | retained B/event | objects kept/event |
|---|---:|---:|---:|
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` | 1,807 | 1,705 | 37.1 |
| `quartz-loop`: without databind's `readValue` | 1,681 | 1,705 | 37.1 |
| `quartz-loop-plain`: without `EventFactory` | 1,554 | 1,697 | 37.1 |
| `quartz-loop-intern`: with Quartz 1.19.0's parse-time `String.intern()` | 4,001 | 1,030 | 16.2 |
| `quartz-kite`, for comparison | 913 | 1,513 | 28.3 |

- Parse-time `String.intern()`, which Quartz 1.19.0 did, still costs about 2.3 µs per note
  for 40% less retained memory.
- `EventFactory` costs about 130 ns per note; `readValue` about 125 ns.
