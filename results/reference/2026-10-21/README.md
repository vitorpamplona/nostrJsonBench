# Reference run, 2026-10-21

Results summary: the [root README](../../../README.md#results). Every table:
[report.md](report.md). Raw rows: `*.json`.

- **Settings:** `./run.sh` defaults: 1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
  workload, frames built fresh in bursts of 64 (`--burst`). One harness at a time on an
  idle machine.
- **Machine:** cloud VM, Intel Xeon (AVX2 + AVX-512), 4 cores, Linux 6.18.
- **Runtimes:**
  - JVM: JetBrains Runtime 21.0.11 (b1163.116), the JDK Amethyst Desktop ships on Linux,
    with its flags (`-Xmx2g`, G1). Three timed JVMs per parser, median kept.
  - rustc 1.97 (`-C target-cpu=native`, fat LTO), GCC 13.3 (`-O3 -march=native`), Go 1.24.7,
    Node 22.22, Bun 1.4.3, Deno 2.9.6, CPython 3.13.16.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`.
- **Code:** the harnesses at this commit. The JVM ran 2026-10-21, the others 2026-10-20.
  Quartz is Amethyst `main` at `df1faea1` (`QUARTZ_DIR`); each JVM row records it.

## Where Quartz's time goes

`notes` workload:

| row | ns/event | retained B/event | objects kept/event |
|---|---:|---:|---:|
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` | 1,693 | 1,705 | 37.1 |
| `quartz-loop`: without databind's `readValue` | 1,676 | 1,705 | 37.1 |
| `quartz-loop-plain`: without `EventFactory` | 1,528 | 1,697 | 37.1 |
| `quartz-loop-intern`: with Quartz 1.19.0's parse-time `String.intern()` | 3,908 | 1,030 | 16.2 |

- Parse-time `String.intern()`, which Quartz 1.19.0 did, costs 0.7–2.2 µs per event on
  small kinds and 4–7 µs on zaps and lists. It shrinks the parsed events by 6–46%,
  depending on kind (35% on `all`).
- `EventFactory` costs about 150 ns per event; `readValue` is within noise.
