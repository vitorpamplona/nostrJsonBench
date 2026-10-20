# nostrJsonBench: JavaScript harness (Node, Bun, Deno)

One ESM script, `bench.mjs`, no build step, run by three engines. It follows
[`../HARNESS.md`](../HARNESS.md): same flags, digest, timing and output schema.

```bash
node/run.sh                         # Node -> results/node.json
node/run.sh --runtime bun --quick   # Bun  -> results/bun.json
node/run.sh --runtime all           # node, bun, deno in turn
node/run.sh --runtime deno --parsers json-parse-string --workloads notes
```

`run.sh` runs `npm ci` on first use. Node is the system `node` (22.x); Bun and Deno come
from npm as pinned devDependencies (`bun@1.4.3`, `deno@2.9.6`), so `package-lock.json`
pins every engine except Node. With `--runtime all`, `--out` is refused (each runtime
writes `results/<runtime>.json`).

The script re-spawns itself once per parser (`--child <id>`), so each parser gets a fresh
JIT profile and heap. Results: `harness` = `node` / `bun` / `deno`, `runtime` = engine
and version.

## Input: the text frame

A WebSocket text frame reaches a JS app as a `string` (`MessageEvent.data` in browsers,
Node 22's global `WebSocket`, Bun, Deno), so every parser starts from a string, prepared
before timing with `TextDecoder` (a flat, sequential string, like the one a socket hands
over). `input` follows [`HARNESS.md`](../HARNESS.md):

- `string`: the parser takes the string itself.
- `string→bytes`: the parser only reads bytes, so the string is UTF-8 encoded inside the
  timed loop with `TextEncoder.encodeInto` into a reused scratch `Uint8Array`. That is the
  fastest encode on all three engines (`all` workload, ns/event: Node 22 465 against 2737
  for `TextEncoder.encode` and 1170 for `Buffer.from`; Bun 111 / 252 / 331; Deno
  802 / 3645 / 1254). The parser gets a view of the scratch buffer, which is fine for
  decoders that copy every value out.
- `bytes`: only for the two extra rows marked EXTRA below. The Node `ws` package delivers
  every message, text frames included, as a `Buffer`, so those rows start from a Buffer.
  They are not the text-frame path and should not be ranked against the others.

The old `json-parse-bytes` (`TextDecoder` + `JSON.parse` from a `Uint8Array`) and
`baseline-utf8-decode` rows were dropped: no WebSocket client hands a text frame over as a
`Uint8Array`. The `ws` Buffer path stays as an extra because `ws` is what most Node relays
and bots use.

## Parsers

| id | library | version | license | mode | input | runtimes |
|---|---|---|---|---|---|---|
| `json-parse-string` | `JSON.parse` | engine | V8: BSD-3-Clause, JSC: BSD-2-Clause | typed | string | all |
| `json-parse-buffer` | EXTRA: `Buffer.toString('utf8')` + `JSON.parse` | engine | as above | typed | bytes (`ws` Buffer) | all |
| `baseline-buffer-tostring` | EXTRA: `Buffer.toString('utf8')` | engine | as above | baseline | bytes (`ws` Buffer) | all |
| `baseline-utf8-encodeinto` | `TextEncoder.encodeInto` (scratch buffer) | engine | as above | baseline | string→bytes | all |
| `baseline-utf8-encode` | `TextEncoder.encode` | engine | as above | baseline | string→bytes | all |
| `bun-jsonl-string` | `Bun.JSONL.parse(string)` | Bun 1.4.3 | MIT | typed | string | bun |
| `bun-jsonl-bytes` | `encodeInto` + `Bun.JSONL.parse(Uint8Array)` | Bun 1.4.3 | MIT | typed | string→bytes | bun |
| `simdjson-parse` | [`simdjson`](https://github.com/luizperes/simdjson_nodejs) N-API addon, `parse(string)` | 0.9.2 (simdjson 0.9.1) | Apache-2.0 | typed | string | all |
| `simdjson-lazy` | same, `lazyParse(string)` + `valueForKeyPath` | 0.9.2 | Apache-2.0 | dom | string | all (crashes on Node) |
| `jsonjoy-decoder` | `encodeInto` + `@jsonjoy.com/json-pack` `JsonDecoder` | 18.30.0 | Apache-2.0 | typed | string→bytes | all (fails digest) |
| `streamparser-json` | `@streamparser/json` `JSONParser.write(string)` | 0.0.26 | MIT | typed | string | all |

`json-parse-string` is the realistic client path: the frame string goes straight into
`JSON.parse`. The baselines are the conversion cost alone: `baseline-utf8-encodeinto` is
what every `string→bytes` row pays, `baseline-utf8-encode` shows how much slower the
allocating `TextEncoder.encode` is (on Node 22 it costs more than `JSON.parse` itself).
`simdjson` and `@streamparser/json` take the string natively; internally the addon copies
it out as UTF-8 and `@streamparser/json` runs `TextEncoder.encode` on it, both timed as
part of the parse.

Known failures, kept in the results with `digest_ok: false`:

- `simdjson-lazy` on Node 22: the child dies with SIGSEGV (a bare `lazyParse()` loop
  reproduces it). The same addon runs on Bun and Deno. The package was last published
  in 2021.
- `jsonjoy-decoder`: it does not decode `\uXXXX` escapes. Its unescape regex only knows
  `\b \f \n \r \t \" \/ \\`, so events whose content has escaped emoji come back with a
  literal `\uD83E\uDD8A` and fail the digest. It passes only the workloads with no `\u`
  escapes: `lists`, `dms` and `giftwraps` (ciphertext content).

The `simdjson` addon is slow everywhere because it builds every JS value through
node-addon-api and allocates a new `dom::parser` per call. On Node 22 it is slower still,
about 10x behind Bun on the same addon. On Deno, `simdjson-lazy` collapses: over 100 µs
per event on `notes` and about 1.4 ms on the 31k-event `all`, against about 6 µs on Bun.
That is probably a backlog of native finalizers; it was not investigated.

## Memory and GC

| | `gc_count` / `gc_ms` | `alloc_bytes_per_event` | `retained_bytes_per_event` |
|---|---|---|---|
| Node 22 | `PerformanceObserver` `gc` entries that start inside the measured window | null: V8 12.4 has no cumulative allocation counter | `process.memoryUsage().heapUsed`, children run with `--expose-gc` |
| Deno 2.9 | null: lists `gc` as supported but delivers no entries | `v8.getHeapStatistics().total_allocated_bytes` delta (V8 15 cumulative counter), V8 heap only: excludes native memory such as the simdjson addon's buffers | as Node, children run with `--v8-flags=--expose-gc` |
| Bun 1.4 | null: JSC exposes no GC events | null: the `node:v8` shim's `total_allocated_bytes` does not track JS allocation | `bun:jsc` `heapStats().heapSize` after `Bun.gc(true)` |

**Objects kept and created** (`retained_objects_per_event`, `allocs_per_event`, how in
each row's `objects_note`, which also breaks the kept count down by type):

| | `retained_objects_per_event` | `allocs_per_event` |
|---|---|---|
| Node 22, Deno 2.9 | nodes of a V8 heap snapshot (`node:v8` `getHeapSnapshot`, streamed: only the header and the `nodes` array are read) of type object (JS objects and arrays), array (V8 backing stores: elements, out-of-object properties), string, heap number, closure, regexp, symbol, bigint with `self_size > 0`; not code, hidden, object shape (maps), synthetic or native nodes, nor the size-0 `smi number` nodes Deno adds for every small integer | V8 allocation tracker (`node:inspector`, `HeapProfiler.startTrackingHeapObjects` with `trackAllocations`) over one untimed round of the prepared frames, minus an empty tracked round. Only for `json-parse-string`, `simdjson-parse` and (Node) `json-parse-buffer`, null elsewhere |
| Bun 1.4 | `bun:jsc` `heapStats().objectCount` after `Bun.gc(true)`: every marked JSC cell, butterflies (element and out-of-line property storage) included; a string's characters are a malloc block outside the cell heap | null: JSC has no allocation counter (`objectCount` only counts what the last collection marked) |

Kept objects use every event of the workload, from fresh frames like the retained-bytes
pass: one throwaway reading, a reading, a pass that parses every event and drops the
result, a reading, the pass that keeps every result, a reading. The value is the keep
pass's growth minus the drop pass's growth, per event, so lazily built engine state and
the readings' own objects cancel. The throwaway reading is there because on Deno the first
snapshot after a workload change still counted the previous workload's frame strings.
A full `all` snapshot with every event kept is about 60 MB of JSON and takes about 2 s.

The tracker records every heap allocation, but optimized JS allocates differently: escape
analysis drops objects and allocation folding turns several objects into one allocation
(an object literal holding an array counted 1 per call instead of 3). It is exact only
where the parse allocates from engine C++ code (`JSON.parse`, Node's `Buffer.toString`,
N-API calls), checked on `{"a":"<long>","n":<int>,"b":[["x","<long>"]]}`: 7.00 per parse
on both engines. So the JS-implemented parsers (`jsonjoy-decoder`, `streamparser-json`),
Deno's JS `Buffer` polyfill and the baselines report null.

Checks on `notes` (4.69 tags and 16.1 string values per event):

- `JSON.parse` keeps 1 event object + 5.69 arrays (`tags` and one per tag) + 5.65 backing
  stores (an empty `tags` shares V8's empty array) + strings. V8 shares empty and
  one-character strings, which predicts 10.75 strings per event: `simdjson-parse` and
  `streamparser-json` keep exactly that. `JSON.parse` keeps fewer (Node 9.92, Deno 8.90)
  because it also internalizes some short values. Node and Deno run V8 without pointer
  compression, so `created_at` is a Smi, not a heap number.
- Created minus kept is the garbage: about 0 for `JSON.parse` (22.28 against 22.27 on
  Node), +1 for `json-parse-buffer` (the decoded string), about +1 for `simdjson-parse`.
- JSC's `JSON.parse` reuses recently parsed string values, so Bun keeps 6.0 strings per
  `notes` event, and its 6.69 butterflies are the 5.69 arrays' storage plus the event
  object's out-of-line property storage (7 properties).

**Retained heap.** After timing, each parser parses every event once more, from a fresh
frame string per event (as a socket hands them over, so a result that kept its input
alive would be charged for it), and keeps all results in an array allocated before the
first reading (so the container is not counted). Before and after, a full synchronous
collection runs four times (`gc()` / `Bun.gc(true)`). The heap growth divided by the event
count is the row's value, the median of three passes. Checks behind the method:

- Bun's `heapSize` is honest for this: it includes JSC's string storage (dropping 31k kept
  frame strings frees about 970 B each for 863-character lines), and `JSON.parse`
  results come out about 17% below V8's (1031 against 1234 B/event on `all`).
- V8 `JSON.parse` copies string values (`%DebugPrint` shows `SEQ_ONE_BYTE_STRING`, not
  sliced strings), so results never pin the frame.
- Everything alive across the two readings is read again afterwards. V8's liveness
  analysis lets the GC free a local that is never used again, and an earlier draft that
  missed this reported 89 B/event for `JSON.parse` on `all`.

Null for baselines, for `simdjson-lazy` (its document lives in native addon memory the
JS heap reading cannot see) and for rows that failed the digest. Retained bytes count the
JS heap only; for the `ws` extras the Buffer is garbage once parsed, so it does not matter.

## Licensing notes

Every package used is permissive. One oddity: npm lists `@jsonjoy.com/json-equal`
(a transitive dependency of `json-pack`, not loaded by `JsonDecoder`) as
`AGPL-3.0-only` in its `package.json`, but the `LICENSE` file it ships is Apache-2.0.
It is a dev-only benchmark dependency and never ships in an Amethyst artifact.
