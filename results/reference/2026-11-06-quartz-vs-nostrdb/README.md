# Quartz against nostrdb's parser, 2026-11-06

`quartz-kite` (Quartz's Kite reader, what Amethyst ships) against nostrdb's parser, the one
Damus and Notedeck parse every event with, on one host, with `sonic_rs-typed`, the fastest
Rust typed row, alongside.

- **Code:** nostrJsonBench `3c05750`. Quartz 2.16.0 on Temurin 27.0.1 (`-Xmx2g`, G1, three
  JVMs per parser, median kept); nostrdb-rs `e22e5632a75e` (0.10.0), the revision Notedeck
  pins, in the Rust harness (rustc 1.97, `-C target-cpu=native`, fat LTO; nostrdb's C is built
  by its crate at `-O3`, without `-march=native`).
- **Rows:** `nostrdb-note` parses into one reused buffer (`borrowed`: valid until the next
  parse), giving nostrdb `max(length × 8, 4096)` bytes of it per event, the size its ingester
  allocates. `nostrdb-note-owned` then copies the note into a block of its own, as the ingester
  does before its writer: what an app keeps (`typed`).
- **Settings:** `./run.sh` defaults (1 s warm-up, ≥ 2 s and ≥ 5 rounds, bursts of 64). Three
  rounds, each `quartz-kite` then the Rust rows, with the Go control (`control/`) before and
  after: `goccy-typed` 1,219 and 1,238 ns/event. One host for the whole run (one boot id).

## The workloads

nostrdb can't represent two kinds of corpus events, so on the corpus workloads its rows fail
verification and aren't timed:

- **2,489 events with a `\uXXXX` escape:** its parser rejects them (`nostrdb.c`, `case 'u':
  // these aren't handled yet`).
- **1,547 unsigned events** (`"sig":""`, rumors a client stored): nostrdb accepts them, but its
  note has a fixed 64-byte sig, and it leaves there whatever the buffer held: a different sig
  on every line.

So every parser here ran on `<name>-ndb`, the workload without those events, built by
[`corpus/nostrdb_subset.py`](../../../corpus/nostrdb_subset.py) (`manifest.json` here):
`all-ndb` keeps 27,232 of 31,161 events, `notes-ndb` 2,979 of 3,568, `reactions-ndb` 1,014 of
2,259, `metadata-ndb` 178 of 268, `zaps-ndb` 1,236 of 1,270. `dms`, `giftwraps` and `lists`
have neither and are the corpus's own. Every row matched every workload's digest in every
round.

## Results

ns/event, median of the three rounds (range of the rounds):

| workload | `quartz-kite` | `nostrdb-note` | `nostrdb-note-owned` | `sonic_rs-typed` |
|---|---:|---:|---:|---:|
| all-ndb | **723** (703–724) | 1,734 (1,730–1,746) | 1,773 (1,768–1,788) | 781 (772–786) |
| notes-ndb | **867** (860–893) | 1,910 (1,903–1,915) | 1,942 (1,932–1,943) | 1,062 (1,061–1,068) |
| reactions-ndb | **381** (375–392) | 1,127 (1,125–1,131) | 1,153 (1,149–1,158) | 435 (432–439) |
| dms | **324** (323–326) | 1,164 (1,160–1,183) | 1,199 (1,182–1,208) | 367 (354–370) |
| giftwraps | 560 (539–575) | 2,244 (2,228–2,247) | 2,300 (2,290–2,306) | **534** (533–548) |
| metadata-ndb | 942 (906–947) | 1,748 (1,741–1,791) | 1,771 (1,757–1,821) | **837** (835–865) |
| zaps-ndb | 1,852 (1,826–1,864) | 3,700 (3,684–3,736) | 3,821 (3,798–3,827) | **1,736** (1,714–1,743) |
| lists | **2,674** (2,641–2,704) | 11,584 (11,504–11,589) | 11,813 (11,802–11,912) | 4,512 (4,343–4,579) |

Memory per event on `all-ndb`:

| row | allocated B | objects created | retained B | objects kept |
|---|---:|---:|---:|---:|
| `quartz-kite` | 1,297 | 21.5 | 1,277 | 21.6 |
| `nostrdb-note` | 0 | 0 | – (reused buffer) | – |
| `nostrdb-note-owned` | 668 | 1.0 | 692 | 1.0 |
| `sonic_rs-typed` | 1,713 | 15.6 | 1,308 | 14.0 |

- **Kite parses 2.8× as fast as nostrdb** (geometric mean over the eight workloads; 2.9× for
  `nostrdb-note-owned`): from 1.9× on `metadata-ndb` and 2.0× on `zaps-ndb` to 4.0× on
  `giftwraps` and 4.3× on `lists`, and 2.4× on `all-ndb`.
- **nostrdb falls furthest behind on long strings and many tags:** `giftwraps` (one 1.7 KB
  base64 payload per event), `dms` (base64 content) and `lists` (25 tags per event, up to 48 KB
  per event).
- **The buffer size matters to nostrdb:** in a separate loop over `dms` and `giftwraps`, the
  same parse took 5 to 9 times as long when nostrdb was given a 1 MiB buffer instead of
  `max(length × 8, 4096)` bytes. The rows give it the latter, as its ingester does.
- **nostrdb's note is the most compact event:** one block of 692 B per event when kept, against
  1,277 B in 21.6 objects for Kite, because ids, pubkeys, sigs and 64-hex tag values are stored
  as bytes. Parsing into the reused buffer allocates nothing.
- Kite is level with `sonic_rs-typed`, the fastest Rust typed row, here as on the full corpus
  (geometric mean 1.11× in Kite's favour).
