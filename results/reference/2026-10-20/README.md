# Reference run, 2026-10-20

Results summary: the [root README](../../../README.md#results). Every table:
[report.md](report.md). Raw rows: `*.json`.

- **Settings:** `./run.sh` defaults (1 s warm-up, ≥ 2 s and ≥ 5 rounds per parser and
  workload), one harness at a time on an idle machine.
- **Machine:** cloud VM, Intel Xeon (AVX2 + AVX-512), 4 cores, Linux 6.18.
- **Runtimes:** OpenJDK 21.0.12 (HotSpot, G1, 2 GB fixed heap), rustc 1.97 (`-C
  target-cpu=native`, fat LTO), GCC 13.3 (`-O3 -march=native`), Go 1.24.7, Node 22.22,
  Bun 1.4.3, Deno 2.9.6, CPython 3.13.16.
- **Corpus:** `corpus/nostr-2024-startup.ndjson.gz`, fingerprint `f995f42b68b5386e`.
- **Code:** the Rust, C/C++, Go, JS and Python rows come from Amethyst's `jsonBench/` tree
  at `c8c46fb3`, the code this repo was imported from. The JVM rows were re-run the same
  day against Quartz at Amethyst `main` `df1faea1` (`QUARTZ_DIR`), which includes
  amethyst#4583; each row records that commit. On Quartz 1.19.0 `quartz-jackson` ran at
  5,585 ns/event (916 B retained).

## Where Quartz's time goes

`notes` workload (more rounds, less noise than `all`):

| row | ns/event | retained B/event |
|---|---:|---:|
| `quartz-jackson`: databind → `EventDeserializer` → `EventFactory` | 1,689 | 1,705 |
| `quartz-loop`: without databind's `readValue` | 1,655 | 1,705 |
| `quartz-loop-plain`: without `EventFactory` | 1,588 | 1,697 |
| `quartz-loop-intern`: with Quartz 1.19.0's parse-time `String.intern()` | 3,762 | 1,030 |
| `quartz-jackson-cacheshare`: parse + LocalCache's sharing, every event | 3,238 | 1,030 |

- Interning costs 0.6–2.1 µs per event on small kinds and 4–6 µs on zaps and lists, and
  saves 6–46% of a kept event's memory depending on kind (35% on `all`). Since amethyst#4583 only kept events pay it;
  through Amethyst's own `EventCache` on the full dump with duplicates, a received event
  went from 15.4 to 12.2 µs with the same retained heap (87.9 MB, 102.5 MB without
  interning).
- `EventFactory` costs ~70–130 ns per event (~400 ns on lists); `readValue` is within noise.
