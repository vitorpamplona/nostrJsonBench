// nostrJsonBench JavaScript harness: one script for Node, Bun and Deno.
// Contract: ../HARNESS.md. Run through ./run.sh.
//
// Without --child the script is the orchestrator: it re-spawns itself once per parser
// (fresh process = fresh JIT profile and heap) and merges the rows into one results file.

import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Buffer } from 'node:buffer';
import { PerformanceObserver, performance } from 'node:perf_hooks';
import v8 from 'node:v8';

const SCRIPT = fileURLToPath(import.meta.url);
const HERE = path.dirname(SCRIPT);
const BENCH = path.resolve(HERE, '..');
const require = createRequire(import.meta.url);

// ---------------------------------------------------------------- runtime

const RT = globalThis.Bun ? 'bun' : globalThis.Deno ? 'deno' : 'node';

function runtimeInfo() {
  if (RT === 'bun') {
    return {
      label: `Bun ${Bun.version} (JavaScriptCore, WebKit ${process.versions.webkit.slice(0, 12)})`,
      engine: `JavaScriptCore (WebKit ${process.versions.webkit.slice(0, 12)}, Bun ${Bun.version})`,
      engineLicense: 'BSD-2-Clause',
    };
  }
  if (RT === 'deno') {
    return {
      label: `Deno ${Deno.version.deno} (V8 ${Deno.version.v8})`,
      engine: `V8 ${Deno.version.v8} (Deno ${Deno.version.deno})`,
      engineLicense: 'BSD-3-Clause',
    };
  }
  return {
    label: `Node.js ${process.versions.node} (V8 ${process.versions.v8})`,
    engine: `V8 ${process.versions.v8} (Node.js ${process.versions.node})`,
    engineLicense: 'BSD-3-Clause',
  };
}

function pkgMeta(name) {
  const p = JSON.parse(readFileSync(path.join(HERE, 'node_modules', name, 'package.json'), 'utf8'));
  return { version: p.version, license: p.license };
}

// ---------------------------------------------------------------- parsers
//
// Every parser starts from what a WebSocket text frame is in JS: a `string`
// (MessageEvent.data in browsers, Node 22's global WebSocket, Bun, Deno). Each parser:
// id, library, mode, input (HARNESS.md vocabulary: 'string', 'string→bytes', 'bytes'),
// notes, runtimes (where it is listed), meta() -> {version, license}, load() -> parse fn,
// and optionally frame ('buffer': the extra Node `ws` rows), fold(result),
// fields(result) (dom results), verify ('text' | 'bytes' for baselines),
// retained: false (results not measurable on the JS heap) and exactAllocs (the runtimes
// whose allocation tracker counts this parse exactly, see allocTracker).

const td = new TextDecoder('utf-8');
const te = new TextEncoder();
const foldEvent = (e) => e.kind + e.tags.length + e.content.length;

// string -> UTF-8 bytes for the parsers that only read bytes. encodeInto a reused scratch
// buffer is the fastest encode on all three engines (all workload, ns/event: Node 22
// 465 vs 2737 for TextEncoder.encode and 1170 for Buffer.from; Bun 111 vs 252 / 331;
// Deno 802 vs 3645 / 1254). The parser gets a view of the scratch buffer, valid until the
// next call: fine for decoders that copy every value out (jsonjoy, Bun.JSONL).
let scratch = new Uint8Array(1 << 16);
function encodeUtf8(s) {
  if (s.length * 3 > scratch.length) scratch = new Uint8Array(s.length * 3);
  return scratch.subarray(0, te.encodeInto(s, scratch).written);
}

const WS_EXTRA = 'EXTRA, not the text-frame path: the Node `ws` package delivers every message, text frames included, as a Buffer; the browser, Node 22 global, Bun and Deno WebSocket deliver a string. ';

const PARSERS = [
  {
    id: 'json-parse-string',
    library: 'JSON.parse',
    mode: 'typed',
    input: 'string',
    notes: 'JSON.parse(frame string), the web-client path; result is a plain object',
    meta: () => engineMeta(),
    load: () => (s) => JSON.parse(s),
    exactAllocs: ['node', 'deno'],
  },
  {
    id: 'json-parse-buffer',
    library: 'Buffer.toString + JSON.parse',
    mode: 'typed',
    input: 'bytes',
    frame: 'buffer',
    variant: true, // starts from a Buffer, not the string a text frame is: never a library's headline row
    notes: WS_EXTRA + 'node:buffer Buffer -> toString("utf8") -> JSON.parse; the decode is timed',
    meta: () => engineMeta(),
    load: () => (b) => JSON.parse(b.toString('utf8')),
    exactAllocs: ['node'], // Deno's Buffer is a JS polyfill
  },
  {
    id: 'baseline-buffer-tostring',
    library: 'Buffer.toString',
    mode: 'baseline',
    input: 'bytes',
    frame: 'buffer',
    notes: WS_EXTRA + 'Buffer.toString("utf8") only (no parse): the decode json-parse-buffer pays',
    meta: () => engineMeta(),
    load: () => (b) => b.toString('utf8'),
    fold: (s) => s.length,
    verify: 'text',
  },
  {
    id: 'baseline-utf8-encodeinto',
    library: 'TextEncoder.encodeInto',
    mode: 'baseline',
    input: 'string→bytes',
    notes: 'TextEncoder.encodeInto a reused scratch Uint8Array only (no parse): the string -> bytes step every string→bytes row pays',
    meta: () => engineMeta(),
    load: () => encodeUtf8,
    fold: (b) => b.length,
    verify: 'bytes',
  },
  {
    id: 'baseline-utf8-encode',
    library: 'TextEncoder.encode',
    mode: 'baseline',
    input: 'string→bytes',
    notes: 'TextEncoder.encode (a fresh Uint8Array per frame) only, for comparison with encodeInto',
    meta: () => engineMeta(),
    load: () => (s) => te.encode(s),
    fold: (b) => b.length,
    verify: 'bytes',
  },
  {
    id: 'bun-jsonl-string',
    library: 'Bun.JSONL.parse',
    mode: 'typed',
    input: 'string',
    runtimes: ['bun'],
    notes: 'Bun-native NDJSON parser fed the frame string; returns [event]',
    meta: () => ({ version: `Bun ${Bun.version}`, license: 'MIT' }),
    load: () => (s) => Bun.JSONL.parse(s)[0],
  },
  {
    id: 'bun-jsonl-bytes',
    library: 'Bun.JSONL.parse',
    mode: 'typed',
    input: 'string→bytes',
    runtimes: ['bun'],
    notes: 'Bun.JSONL.parse(Uint8Array) after TextEncoder.encodeInto a reused scratch buffer (timed); returns [event]',
    meta: () => ({ version: `Bun ${Bun.version}`, license: 'MIT' }),
    load: () => (s) => Bun.JSONL.parse(encodeUtf8(s))[0],
  },
  {
    id: 'simdjson-parse',
    library: 'simdjson (npm, luizperes/simdjson_nodejs)',
    mode: 'typed',
    input: 'string',
    notes: 'native N-API addon bundling simdjson 0.9.1; simdjson.parse(string) is its only entry point (it copies the string out as UTF-8 itself) and builds the full JS object through node-addon-api',
    meta: () => pkgMeta('simdjson'),
    load: () => {
      const s = require('simdjson');
      return (str) => s.parse(str);
    },
    exactAllocs: ['node', 'deno'],
  },
  {
    id: 'simdjson-lazy',
    library: 'simdjson (npm, luizperes/simdjson_nodejs)',
    mode: 'dom',
    input: 'string',
    retained: false,
    notes: 'lazyParse(string) keeps the simdjson DOM native; the timed loop reads kind and tags via valueForKeyPath (tags materializes the array: the binding has no length accessor). Retained: null, the document lives in native memory the JS heap reading does not see. Under Node 22 the process dies with SIGSEGV (reproduced with a bare lazyParse() loop, cause not traced into the 2021 addon); runs on Bun and Deno',
    meta: () => pkgMeta('simdjson'),
    load: () => {
      const s = require('simdjson');
      return (str) => s.lazyParse(str);
    },
    fold: (d) => d.valueForKeyPath('kind') + d.valueForKeyPath('tags').length,
    fields: (d) => ({
      id: d.valueForKeyPath('id'),
      pubkey: d.valueForKeyPath('pubkey'),
      created_at: d.valueForKeyPath('created_at'),
      kind: d.valueForKeyPath('kind'),
      tags: d.valueForKeyPath('tags'),
      content: d.valueForKeyPath('content'),
      sig: d.valueForKeyPath('sig'),
    }),
  },
  {
    id: 'jsonjoy-decoder',
    library: '@jsonjoy.com/json-pack JsonDecoder',
    mode: 'typed',
    input: 'string→bytes',
    notes: 'pure-JS JSON decoder that only reads a Uint8Array (json-joy): TextEncoder.encodeInto a reused scratch buffer, then decode (both timed); one decoder reused. Does not decode \\uXXXX escapes (its unescape regex only knows \\b \\f \\n \\r \\t \\" \\/ \\\\), so events with escaped emoji fail the digest',
    meta: () => pkgMeta('@jsonjoy.com/json-pack'),
    load: () => {
      const { JsonDecoder } = require('@jsonjoy.com/json-pack/lib/json/JsonDecoder.js');
      const dec = new JsonDecoder();
      return (s) => dec.decode(encodeUtf8(s));
    },
  },
  {
    id: 'streamparser-json',
    library: '@streamparser/json JSONParser',
    mode: 'typed',
    input: 'string',
    notes: 'streaming tokenizer + token parser, one parser reused with separator "" and paths ["$"]; each frame written whole through write(string), which TextEncoder.encodes it internally',
    meta: () => pkgMeta('@streamparser/json'),
    load: async () => {
      const { JSONParser } = await import('@streamparser/json');
      const p = new JSONParser({ paths: ['$'], separator: '' });
      let last;
      p.onValue = ({ value }) => {
        last = value;
      };
      p.onError = (err) => {
        throw err;
      };
      return (s) => {
        last = undefined;
        p.write(s);
        return last;
      };
    },
  },
];

function engineMeta() {
  const ri = runtimeInfo();
  return { version: ri.engine, license: ri.engineLicense };
}

function listedParsers() {
  return PARSERS.filter((p) => !p.runtimes || p.runtimes.includes(RT));
}

// ---------------------------------------------------------------- args

function parseArgs(argv) {
  const o = {
    corpus: path.join(BENCH, '.corpus'),
    out: null,
    workloads: null,
    parsers: null,
    warmupMs: 1000,
    minTimeMs: 2000,
    minRounds: 5,
    burst: 64,
    list: false,
    child: null,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const val = () => {
      if (i + 1 >= argv.length) throw new Error(`missing value for ${a}`);
      return argv[++i];
    };
    switch (a) {
      case '--corpus': o.corpus = path.resolve(val()); break;
      case '--out': o.out = path.resolve(val()); break;
      case '--workloads': o.workloads = val().split(',').filter(Boolean); break;
      case '--parsers': o.parsers = val().split(',').filter(Boolean); break;
      case '--warmup-ms': o.warmupMs = Number(val()); break;
      case '--min-time-ms': o.minTimeMs = Number(val()); break;
      case '--min-rounds': o.minRounds = Number(val()); break;
      case '--burst': o.burst = Number(val()); break;
      case '--quick': o.warmupMs = 200; o.minTimeMs = 300; o.minRounds = 3; break;
      case '--list': o.list = true; break;
      case '--child': o.child = val(); break;
      default: throw new Error(`unknown argument: ${a}`);
    }
  }
  if (!Number.isInteger(o.burst) || o.burst < 1) throw new Error(`--burst must be an integer >= 1, got ${o.burst}`);
  if (!o.out) o.out = path.join(BENCH, 'results', `${RT}.json`);
  return o;
}

// ---------------------------------------------------------------- digest

const US = '\x1f', RS = '\x1e', GS = '\x1d', FS = '\x1c';

function intStr(n, name) {
  if (typeof n === 'bigint') return n.toString();
  if (typeof n !== 'number' || !Number.isSafeInteger(n) || n < 0) throw new Error(`${name} is not a non-negative integer: ${n}`);
  return String(n);
}

function str(s, name) {
  if (typeof s !== 'string') throw new Error(`${name} is not a string (${typeof s})`);
  return s;
}

// The separators are ASCII, so hashing the UTF-8 of the concatenation equals hashing
// each piece's UTF-8 in turn (the digest_event order in corpus/prepare.py).
function digestText(e) {
  let s = str(e.id, 'id') + US + str(e.pubkey, 'pubkey') + US + intStr(e.created_at, 'created_at') + US +
    intStr(e.kind, 'kind') + US;
  const tags = e.tags;
  if (!Array.isArray(tags)) throw new Error('tags is not an array');
  for (const t of tags) {
    if (!Array.isArray(t)) throw new Error('tag is not an array');
    for (const v of t) s += str(v, 'tag element') + US;
    s += RS;
  }
  s += GS + str(e.content, 'content') + US + str(e.sig, 'sig') + US + FS;
  return s;
}

// ---------------------------------------------------------------- child: one parser

function loadWorkload(corpus, w) {
  const buf = readFileSync(path.join(corpus, w.file));
  const lines = [];
  let start = 0;
  for (let i = 0; i < buf.length; i++) {
    if (buf[i] === 0x0a) {
      if (i > start) lines.push(new Uint8Array(buf.subarray(start, i))); // own copy per line
      start = i + 1;
    }
  }
  if (start < buf.length) lines.push(new Uint8Array(buf.subarray(start)));
  return lines;
}

function percentile(sorted, p) {
  const k = Math.max(0, Math.ceil(p * sorted.length) - 1);
  return sorted[k];
}

function median(sorted) {
  const n = sorted.length;
  return n % 2 ? sorted[(n - 1) >> 1] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
}

// Monotonic clocks, in ns. The timed one is read twice per burst (a 64-event burst is
// ~50-100 µs), so it is the cheapest ns clock of each runtime; all three tick in 1 ns
// steps. Per call (min of runs on the bench host): Bun.nanoseconds() ~36 ns, an
// integer (performance.now ~40, process.hrtime.bigint ~53); Node and Deno
// globalThis.performance.now(), a double in ms with ns digits, ~40 ns on Node
// (process.hrtime.bigint ~86) and ~86 ns on Deno (the node:perf_hooks export ~138,
// process.hrtime.bigint ~234). nowNs is only for deadlines.
const nowNs = () => Number(process.hrtime.bigint());
const burstClockNs = RT === 'bun'
  ? () => Bun.nanoseconds()
  : (() => {
      const perf = globalThis.performance;
      return () => perf.now() * 1e6;
    })();
const BURST_CLOCK = RT === 'bun' ? 'Bun.nanoseconds()' : 'performance.now()';

function allocCounter() {
  // V8 >= ~13 exposes a cumulative total_allocated_bytes (Deno 2.9 has it, Node 22's V8 12.4
  // does not). Bun's node:v8 shim reports a field of that name that does not track JS heap
  // allocation, so it is not used there.
  if (RT === 'bun') return null;
  const s = v8.getHeapStatistics();
  if (typeof s.total_allocated_bytes !== 'number') return null;
  return () => v8.getHeapStatistics().total_allocated_bytes;
}

function gcRecorder() {
  // Bun: JSC exposes no GC events or counters to JS. Deno 2.9 lists 'gc' as a supported
  // entry type but never delivers an entry (checked: 0 entries over ~70 MB of garbage).
  if (RT !== 'node') return null;
  if (!PerformanceObserver.supportedEntryTypes?.includes('gc')) return null;
  const entries = [];
  const obs = new PerformanceObserver((list) => {
    for (const e of list.getEntries()) entries.push({ start: e.startTime, duration: e.duration });
  });
  obs.observe({ entryTypes: ['gc'] });
  return {
    async collect(t0, t1) {
      await new Promise((r) => setTimeout(r, 100)); // entries are delivered asynchronously
      for (const e of obs.takeRecords?.() ?? []) entries.push({ start: e.startTime, duration: e.duration });
      const inside = entries.filter((e) => e.start >= t0 && e.start <= t1);
      entries.length = 0;
      return { count: inside.length, ms: inside.reduce((a, e) => a + e.duration, 0) };
    },
  };
}

// Retained heap (HARNESS.md "Retained heap"): a full, synchronous collection and the JS
// heap in use. Node and Deno need --expose-gc (childCommand passes it); Bun.gc(true) is
// a synchronous full JSC collection and bun:jsc heapStats().heapSize the live JSC heap.
// Both see the JS heap only: memory a native addon holds (simdjson's documents) or a
// Buffer's external backing store is invisible to them.
async function retainedProbe() {
  if (RT === 'bun') {
    const jsc = await import('bun:jsc');
    return { gc: () => Bun.gc(true), used: () => jsc.heapStats().heapSize };
  }
  if (typeof globalThis.gc !== 'function') return null;
  return { gc: () => globalThis.gc(), used: () => process.memoryUsage().heapUsed };
}

// Parses every event once more from a fresh frame (as a socket would hand it over, so a
// result that pins its input is charged for it), keeps every result alive, and returns
// the median over 3 passes of the heap growth per event. The result array is allocated
// before the first reading, so the container is excluded. Everything alive across the
// readings is touched afterwards: V8's liveness analysis lets the GC free a local that
// is never read again, which would otherwise skew the readings.
function measureRetained(probe, parse, raw, makeFrame) {
  const n = raw.length;
  const full = () => {
    for (let i = 0; i < 4; i++) probe.gc();
  };
  const vals = [];
  let touch = 0;
  for (let rep = 0; rep < 3; rep++) {
    let keep = new Array(n).fill(null);
    full();
    const before = probe.used();
    for (let i = 0; i < n; i++) keep[i] = parse(makeFrame(raw[i]));
    full();
    const after = probe.used();
    vals.push((after - before) / n);
    touch += keep.length + (keep[n - 1] ? 1 : 0) + raw.length;
    keep = null;
  }
  vals.sort((a, b) => a - b);
  return { perEvent: vals[1], touch };
}

// ---------------------------------------------------------------- object counts
//
// HARNESS.md "Objects created, kept and thrown away".
//
// Kept (retained_objects_per_event), V8 (Node, Deno): nodes of a heap snapshot, which V8
// takes after its own full collection. Counted: every node whose type is a heap object
// a JS value owns (COUNTED_V8 below: objects and arrays, the backing stores of their
// elements and out-of-object properties, strings, heap numbers, closures, ...) and whose
// self_size is > 0. Skipped: code, hidden, object shape (maps and descriptors, shared by
// every object of a shape), synthetic roots and native (embedder) nodes. The size test
// drops the size-0 "smi number" nodes (and their value strings) Deno's snapshots add for
// every small integer: those are not heap objects. Bun: bun:jsc heapStats().objectCount,
// every marked cell after Bun.gc(true), JSC butterflies (element/property storage)
// included; a string's characters live outside the cell heap and are not a second
// object there.
const COUNTED_V8 = new Set(['object', 'array', 'string', 'concatenated string', 'sliced string', 'number', 'closure', 'regexp', 'symbol', 'bigint']);

const asText = () => {
  const dec = new TextDecoder();
  return (c) => (typeof c === 'string' ? c : dec.decode(c, { stream: true }));
};

// Streams a snapshot and counts its nodes by type, reading only the header and the
// "nodes" array (the snapshot of the `all` workload with every event kept is ~60 MB of
// JSON; nothing is materialized but one chunk at a time).
async function countSnapshotNodes(chunks) {
  const text = asText();
  let head = '';
  let started = false, done = false;
  let types, counted, typeField, sizeField, nf;
  let field = 0, cur = 0, inNum = false, nodeType = 0;
  const byType = {};
  let total = 0;
  for await (const raw of chunks) {
    if (done) continue; // drain the stream
    let chunk = text(raw);
    if (!started) {
      head += chunk;
      const k = head.indexOf('"nodes":[');
      if (k < 0) continue;
      const meta = JSON.parse(head.slice(0, head.lastIndexOf(',', k)) + '}').snapshot.meta;
      nf = meta.node_fields.length;
      typeField = meta.node_fields.indexOf('type');
      sizeField = meta.node_fields.indexOf('self_size');
      // the type is read first and applied when the node's self_size arrives
      if (typeField < 0 || sizeField < typeField) throw new Error('unexpected heap snapshot node_fields');
      types = meta.node_types[0];
      counted = types.map((t) => COUNTED_V8.has(t));
      for (const t of types) if (COUNTED_V8.has(t)) byType[t] = 0;
      chunk = head.slice(k + 9);
      head = null;
      started = true;
    }
    for (let p = 0; p < chunk.length; p++) {
      const c = chunk.charCodeAt(p);
      if (c >= 48 && c <= 57) {
        cur = cur * 10 + (c - 48);
        inNum = true;
      } else if (c === 44 || c === 93) { // ',' or ']'
        if (inNum) {
          if (field === typeField) nodeType = cur;
          else if (field === sizeField && cur > 0 && counted[nodeType]) {
            byType[types[nodeType]]++;
            total++;
          }
          field = field + 1 === nf ? 0 : field + 1;
          cur = 0;
          inNum = false;
        }
        if (c === 93) {
          done = true;
          break;
        }
      }
    }
  }
  return { total, byType };
}

async function objectCounter() {
  if (RT === 'bun') {
    const jsc = await import('bun:jsc');
    return {
      live: async () => {
        for (let i = 0; i < 4; i++) Bun.gc(true);
        const h = jsc.heapStats();
        // objectTypeCounts covers JSCells only; the rest of objectCount is auxiliary
        // storage (butterflies)
        const byType = { ...h.objectTypeCounts };
        byType.butterfly = h.objectCount - Object.values(h.objectTypeCounts).reduce((a, v) => a + v, 0);
        return { total: h.objectCount, byType };
      },
    };
  }
  if (typeof globalThis.gc !== 'function' || typeof v8.getHeapSnapshot !== 'function') return null;
  return {
    live: async () => {
      for (let i = 0; i < 4; i++) globalThis.gc();
      return countSnapshotNodes(v8.getHeapSnapshot());
    },
  };
}

// Objects kept per event: like measureRetained, but counted. Three readings around two
// passes over every event of the workload: a pass that parses each event and drops the
// result (whatever survives it — lazily built engine state, the readings' own objects —
// is not the events'), then the pass that keeps every result. Kept per event =
// (keep-pass growth − drop-pass growth) / events. The container is allocated before the
// first reading. One reading is thrown away first: on Deno the first snapshot after a
// workload change still counted the previous workload's 3,568 frame strings, which the
// next one did not (the drop pass then "freed" them and inflated the result by 1.6/event).
async function measureRetainedObjects(counter, parse, raw, makeFrame) {
  const n = raw.length;
  let keep = new Array(n).fill(null);
  const pass = (keepIt) => {
    for (let i = 0; i < n; i++) {
      const r = parse(makeFrame(raw[i]));
      if (keepIt) keep[i] = r;
    }
  };
  await counter.live();
  const c0 = await counter.live();
  pass(false);
  const c1 = await counter.live();
  pass(true);
  const c2 = await counter.live();
  const touch = keep.length + (keep[n - 1] ? 1 : 0) + raw.length;
  keep = null;
  const perEvent = (c2.total - 2 * c1.total + c0.total) / n;
  let byType = null;
  if (c0.byType) {
    byType = {};
    for (const t of new Set([...Object.keys(c0.byType), ...Object.keys(c1.byType), ...Object.keys(c2.byType)])) {
      const v = ((c2.byType[t] ?? 0) - 2 * (c1.byType[t] ?? 0) + (c0.byType[t] ?? 0)) / n;
      if (Math.abs(v) >= 0.005) byType[t] = v;
    }
  }
  return { perEvent, byType, touch };
}

// Objects created per parse (allocs_per_event), V8 only: the inspector's allocation
// tracker (HeapProfiler.startTrackingHeapObjects with trackAllocations), which turns off
// inline allocation and records every heap allocation with its JS stack; the count is
// the sum over the trace tree it returns. One untimed round over the prepared frames
// after the timed rounds, minus a tracked round of nothing (the start/stop overhead).
// Exact only when the parse allocates from engine C++ code, where every object is its
// own allocation (JSON.parse, Node's Buffer.toString, N-API calls): checked on a known
// shape, {"a":"<long>","n":<int>,"b":[["x","<long>"]]} counts 7.00 per parse on Node
// and Deno. Optimized JS code allocates differently: escape analysis drops objects and
// allocation folding makes several objects one allocation (an object literal with a
// nested array counted 1 per call, not 3), so a JS-implemented parser would undercount.
// Parsers opt in with exactAllocs: [runtimes].
async function allocTracker() {
  if (RT === 'bun') return null;
  let inspector;
  try {
    inspector = await import('node:inspector');
  } catch {
    return null;
  }
  const session = new inspector.Session();
  session.connect();
  const post = (m, p) => new Promise((res, rej) => session.post(m, p ?? {}, (e, r) => (e ? rej(e) : res(r))));
  await post('HeapProfiler.enable');
  let chunks = [];
  session.on('HeapProfiler.addHeapSnapshotChunk', (m) => chunks.push(m.params.chunk));
  const tracked = async (work) => {
    chunks = [];
    await post('HeapProfiler.startTrackingHeapObjects', { trackAllocations: true });
    work();
    await post('HeapProfiler.stopTrackingHeapObjects', { reportProgress: false });
    const s = chunks.join('');
    chunks = [];
    const meta = JSON.parse(s.slice(0, s.lastIndexOf(',', s.indexOf('"nodes":['))) + '}').snapshot.meta;
    const a = s.indexOf('"trace_tree":');
    const b = s.indexOf(',\n"samples":', a) >= 0 ? s.indexOf(',\n"samples":', a) : s.indexOf(',"samples":', a);
    if (a < 0 || b < 0) throw new Error('no trace_tree in the tracking snapshot');
    const tree = JSON.parse(s.slice(a + 13, b));
    const tf = meta.trace_node_fields, w = tf.length, ci = tf.indexOf('count'), chi = tf.indexOf('children');
    let count = 0;
    const walk = (arr) => {
      for (let i = 0; i < arr.length; i += w) {
        count += arr[i + ci];
        walk(arr[i + chi]);
      }
    };
    walk(tree);
    return count;
  };
  return {
    async perEvent(parse, inputs) {
      const n = inputs.length;
      let last = null;
      const zero = await tracked(() => {});
      const one = await tracked(() => {
        for (let i = 0; i < n; i++) last = parse(inputs[i]);
      });
      return { perEvent: (one - zero) / n, touch: last ? 1 : 0 };
    },
  };
}

function objectsNote(P, kept, allocs) {
  const parts = [];
  if (kept) {
    if (RT === 'bun') {
      const terms = Object.entries(kept.byType ?? {})
        .filter(([, v]) => Math.abs(v) >= 0.01)
        .map(([t, v]) => `${v.toFixed(2)} ${t === 'butterfly' ? 'butterfly (element and out-of-line property storage)' : t}`);
      parts.push(
        `retained objects: bun:jsc heapStats().objectCount, every marked JSC cell after Bun.gc(true) x4, every event of the workload; per event ${terms.join(' + ') || '0'} (types from objectTypeCounts; a string's characters are a malloc block outside the cell heap, not a second object)`,
      );
    } else {
      const label = { object: 'object (JS objects and arrays)', array: 'array (V8 backing stores: elements, out-of-object properties)', number: 'heap number' };
      const terms = Object.entries(kept.byType ?? {})
        .filter(([, v]) => Math.abs(v) >= 0.01)
        .map(([t, v]) => `${v.toFixed(2)} ${label[t] ?? t}`);
      parts.push(
        `retained objects: V8 heap snapshot nodes (node:v8 getHeapSnapshot), every event of the workload; per event ${terms.join(' + ') || '0'}` +
          '; code, hidden, object shape (maps), synthetic, native and size-0 nodes are not counted',
      );
    }
    parts.push('kept = growth over a keep-every-result pass minus growth over a drop-every-result pass, so engine state and the readings themselves cancel; the results array is allocated first');
  }
  if (allocs != null) {
    parts.push('allocs: V8 allocation tracker (inspector HeapProfiler.startTrackingHeapObjects, trackAllocations) over one untimed round of the prepared frames: every heap allocation, exact here because this parse allocates from engine C++ code');
  } else if (P.mode !== 'baseline') {
    parts.push(
      RT === 'bun'
        ? 'allocs: null, JSC exposes no allocation counter (heapStats counts only cells marked by the last collection)'
        : 'allocs: null, the only exact V8 count (the allocation tracker) sees optimized JS after escape analysis and allocation folding, which drop and merge objects, so it is reported only where the parse allocates from engine C++ code (JSON.parse, N-API)',
    );
  }
  return parts.length ? parts.join('. ') : null;
}

async function runChild(opts, manifest, workloads) {
  const P = PARSERS.find((p) => p.id === opts.child);
  if (!P) throw new Error(`unknown parser ${opts.child}`);
  let meta = { version: null, license: null };
  let parse = null;
  let loadError = null;
  try {
    meta = P.meta();
    parse = await P.load();
  } catch (e) {
    loadError = `load failed: ${e?.message ?? e}`;
  }
  const fold = P.fold ?? foldEvent;
  const fields = P.fields ?? ((e) => e);
  const gcRec = gcRecorder();
  const alloc = allocCounter();
  const probe = await retainedProbe();
  const counter = await objectCounter();
  const tracker = P.exactAllocs?.includes(RT) ? await allocTracker() : null;
  const rows = [];
  let sinkAll = 0;

  for (const w of workloads) {
    const row = {
      parser: P.id,
      library: P.library,
      version: meta.version,
      license: meta.license,
      mode: P.mode,
      input: P.input,
      notes: P.notes,
      variant: P.variant === true,
      workload: w.name,
      events: w.events,
      bytes: w.bytes,
      digest_ok: false,
      errors: 0,
      error: null,
      rounds: 0,
      median_ns: null, min_ns: null, p90_ns: null,
      ns_per_event: null, mb_per_s: null, events_per_s: null,
      alloc_bytes_per_event: null, allocs_per_event: null,
      gc_count: null, gc_ms: null,
      retained_bytes_per_event: null,
      retained_objects_per_event: null,
      objects_note: null,
    };
    rows.push(row);
    if (loadError) {
      row.error = loadError;
      continue;
    }
    const raw = loadWorkload(opts.corpus, w);
    if (raw.length !== w.events) {
      row.error = `line count ${raw.length} != manifest events ${w.events}`;
      continue;
    }
    // The frame object, prepared untimed: a JS string (TextDecoder output: a flat,
    // sequential string like the one a WebSocket hands over), or a Buffer for the
    // extra `ws` rows.
    const makeFrame = P.frame === 'buffer' ? (b) => Buffer.from(b) : (b) => td.decode(b);
    const inputs = raw.map(makeFrame);

    // 2. verify (untimed)
    const h = createHash('sha256');
    for (let i = 0; i < inputs.length; i++) {
      try {
        const r = parse(inputs[i]);
        if (P.verify === 'text') {
          // a decode baseline produces the line text: re-parse it with JSON.parse to digest it
          h.update(digestText(JSON.parse(r)), 'utf8');
        } else if (P.verify === 'bytes') {
          // an encode baseline produces the line's UTF-8: decode and parse it to digest it
          h.update(digestText(JSON.parse(td.decode(r))), 'utf8');
        } else {
          h.update(digestText(fields(r)), 'utf8');
        }
      } catch (e) {
        row.errors++;
        if (!row.error) row.error = `event ${i}: ${e?.message ?? e}`;
      }
    }
    const digest = h.digest('hex');
    row.digest_ok = row.errors === 0 && digest === w.digest;
    if (!row.digest_ok) {
      if (!row.error) row.error = `digest mismatch: ${digest} != ${w.digest}`;
      continue;
    }

    const n = inputs.length;

    // One round under HARNESS.md "Arrival": for each burst of --burst frames, build the
    // frames untimed from the lines' UTF-8 bytes with makeFrame (TextDecoder.decode: a
    // new string per call, like MessageEvent.data; Buffer.from for the `ws` rows), time
    // the parses, then drop the frames. Nothing is cached across rounds: every round
    // decodes every frame again. Returns the timed ns (the sum of the bursts); the fold
    // sink goes to roundSink.
    const burst = opts.burst;
    const frames = new Array(burst).fill(null);
    let roundSink = 0;
    const burstRound = () => {
      let s = 0;
      let ns = 0;
      for (let b = 0; b < n; b += burst) {
        const m = Math.min(burst, n - b);
        for (let j = 0; j < m; j++) frames[j] = makeFrame(raw[b + j]);
        const c0 = burstClockNs();
        for (let j = 0; j < m; j++) s += fold(parse(frames[j]));
        ns += burstClockNs() - c0;
        frames.fill(null); // drop: the frames are garbage now, like the socket's
      }
      roundSink += s;
      return ns;
    };
    // The pre-burst loop over the preloaded frame strings, used only by the untimed
    // memory pass below.
    const tightRound = () => {
      let s = 0;
      for (let i = 0; i < n; i++) s += fold(parse(inputs[i]));
      return s;
    };

    // 3. warm up (burst rounds, like the measured ones)
    let sink = 0;
    const wEnd = nowNs() + opts.warmupMs * 1e6;
    do burstRound(); while (nowNs() < wEnd);

    // 4. measure
    const times = [];
    const mEnd = nowNs() + opts.minTimeMs * 1e6;
    while (times.length < 10000) {
      times.push(burstRound());
      if (times.length >= opts.minRounds && nowNs() >= mEnd) break;
    }
    sink += roundSink;

    // 4b. memory pass (untimed): the counters read today (Deno's total_allocated_bytes,
    // Node's gc entries) cannot be taken around each burst without counting the frame
    // strings (decoding allocates them, and their garbage drives collections), so they
    // come from as many rounds as were measured of the old loop over the preloaded
    // frame strings: the parses only.
    let memNote = null;
    if (alloc || gcRec) {
      const t0 = performance.now();
      const a0 = alloc ? alloc() : 0;
      for (let r = 0; r < times.length; r++) sink += tightRound();
      const a1 = alloc ? alloc() : 0;
      const t1 = performance.now();
      if (alloc) row.alloc_bytes_per_event = (a1 - a0) / (times.length * w.events);
      if (gcRec) {
        const g = await gcRec.collect(t0, t1);
        row.gc_count = g.count;
        row.gc_ms = g.ms;
      }
      memNote = `${alloc ? 'alloc_bytes_per_event is' : 'gc_count and gc_ms are'} taken in a separate untimed pass after timing: as many rounds as were measured (${times.length}) of a tight loop over the preloaded frame strings, so they cover the parses only, not the burst frames' decoding` +
        (gcRec ? '; they count every gc entry (minor, major, incremental, weak-callback) that starts inside that pass' : '');
    }
    sinkAll += sink;

    times.sort((a, b) => a - b);
    const med = median(times);
    row.rounds = times.length;
    row.median_ns = Math.round(med);
    row.min_ns = Math.round(times[0]);
    row.p90_ns = Math.round(percentile(times, 0.9));
    row.ns_per_event = med / w.events;
    row.mb_per_s = w.bytes / (med / 1e9) / 1e6;
    row.events_per_s = w.events / (med / 1e9);
    row.notes = `${P.notes}. Timing: bursts of ${burst} frames, each built untimed with ${P.frame === 'buffer' ? 'Buffer.from(line bytes)' : 'TextDecoder.decode(line bytes)'} and dropped after its timed parses; clock ${BURST_CLOCK}` +
      (memNote ? `. ${memNote}` : '');
    // 5. retained heap (untimed), not for baselines or results held outside the JS heap
    if (probe && P.mode !== 'baseline' && P.retained !== false) {
      const r = measureRetained(probe, parse, raw, makeFrame);
      row.retained_bytes_per_event = r.perEvent;
      sink += r.touch + inputs.length;
    }
    // 6. objects kept and created (untimed)
    let kept = null;
    if (counter && P.mode !== 'baseline' && P.retained !== false) {
      kept = await measureRetainedObjects(counter, parse, raw, makeFrame);
      row.retained_objects_per_event = kept.perEvent;
      sink += kept.touch;
    }
    if (tracker) {
      const a = await tracker.perEvent(parse, inputs);
      row.allocs_per_event = a.perEvent;
      sink += a.touch;
    }
    row.objects_note = objectsNote(P, kept, row.allocs_per_event);
    process.stderr.write(
      `  ${RT} ${P.id.padEnd(26)} ${w.name.padEnd(10)} ${row.mb_per_s.toFixed(1).padStart(8)} MB/s ` +
        `${row.ns_per_event.toFixed(0).padStart(7)} ns/ev  rounds=${row.rounds}` +
        (row.retained_bytes_per_event == null ? '' : ` retained=${row.retained_bytes_per_event.toFixed(0)} B/ev`) +
        (row.retained_objects_per_event == null ? '' : ` kept=${row.retained_objects_per_event.toFixed(2)} obj/ev`) +
        (row.allocs_per_event == null ? '' : ` allocs=${row.allocs_per_event.toFixed(2)} obj/ev`) +
        ` sink=${sink}\n`,
    );
  }
  process.stdout.write('@@ROWS ' + JSON.stringify(rows) + '\n');
  process.stderr.write(`  (sink ${sinkAll})\n`);
}

// ---------------------------------------------------------------- orchestrator

function childCommand(args) {
  // --expose-gc gives the child a synchronous full gc() for the retained-heap pass. It
  // does not change how V8 collects during the timed rounds.
  if (RT === 'deno') return [Deno.execPath(), ['run', '-A', '--v8-flags=--expose-gc', SCRIPT, ...args]];
  if (RT === 'bun') return [process.execPath, [SCRIPT, ...args]];
  const execArgv = process.execArgv.includes('--expose-gc') ? process.execArgv : [...process.execArgv, '--expose-gc'];
  return [process.execPath, [...execArgv, SCRIPT, ...args]];
}

function hostInfo() {
  let cpu = os.cpus()[0]?.model ?? 'unknown';
  try {
    const m = readFileSync('/proc/cpuinfo', 'utf8').match(/^model name\s*:\s*(.+)$/m);
    if (m) cpu = m[1].trim();
  } catch {}
  return { cpu, cores: os.cpus().length, os: `${os.type()} ${os.release()}` };
}

async function main() {
  const opts = parseArgs(RT === 'deno' ? Deno.args : process.argv.slice(2));
  if (opts.list) {
    for (const p of listedParsers()) console.log(p.id);
    return;
  }
  const manifestPath = path.join(opts.corpus, 'manifest.json');
  if (!existsSync(manifestPath)) throw new Error(`no corpus at ${opts.corpus}: run python3 corpus/prepare.py`);
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  let workloads = manifest.workloads;
  if (opts.workloads) {
    for (const n of opts.workloads) if (!workloads.some((w) => w.name === n)) throw new Error(`unknown workload ${n}`);
    workloads = workloads.filter((w) => opts.workloads.includes(w.name));
  }

  if (opts.child) return runChild(opts, manifest, workloads);

  let parsers = listedParsers();
  if (opts.parsers) {
    for (const n of opts.parsers) if (!parsers.some((p) => p.id === n)) throw new Error(`unknown parser ${n} for ${RT}`);
    parsers = parsers.filter((p) => opts.parsers.includes(p.id));
  }

  const passArgs = [
    '--corpus', opts.corpus,
    '--workloads', workloads.map((w) => w.name).join(','),
    '--warmup-ms', String(opts.warmupMs),
    '--min-time-ms', String(opts.minTimeMs),
    '--min-rounds', String(opts.minRounds),
    '--burst', String(opts.burst),
  ];
  const results = [];
  for (const p of parsers) {
    process.stderr.write(`[${RT}] ${p.id}\n`);
    const [cmd, args] = childCommand([...passArgs, '--child', p.id]);
    const r = spawnSync(cmd, args, { cwd: HERE, stdio: ['ignore', 'pipe', 'inherit'], maxBuffer: 64 << 20, encoding: 'utf8' });
    const line = (r.stdout ?? '').split('\n').find((l) => l.startsWith('@@ROWS '));
    if (r.status === 0 && line) {
      results.push(...JSON.parse(line.slice(7)));
    } else {
      const why = `child exited with ${r.status ?? r.signal}${r.error ? ': ' + r.error.message : ''}`;
      process.stderr.write(`  ${why}\n`);
      for (const w of workloads) {
        results.push({
          parser: p.id, library: p.library, version: null, license: null, mode: p.mode, input: p.input,
          notes: p.notes, variant: p.variant === true, workload: w.name, events: w.events, bytes: w.bytes, digest_ok: false, errors: 0,
          error: why, rounds: 0, median_ns: null, min_ns: null, p90_ns: null, ns_per_event: null, mb_per_s: null,
          events_per_s: null, alloc_bytes_per_event: null, allocs_per_event: null, gc_count: null, gc_ms: null,
          retained_bytes_per_event: null, retained_objects_per_event: null, objects_note: null,
        });
      }
    }
  }

  const doc = {
    harness: RT,
    language: 'JavaScript',
    runtime: runtimeInfo().label,
    host: hostInfo(),
    settings: { warmup_ms: opts.warmupMs, min_time_ms: opts.minTimeMs, min_rounds: opts.minRounds, burst: opts.burst },
    corpus_fingerprint: manifest.fingerprint,
    results,
  };
  mkdirSync(path.dirname(opts.out), { recursive: true });
  writeFileSync(opts.out, JSON.stringify(doc, null, 2) + '\n');
  process.stderr.write(`wrote ${opts.out}\n`);
}

await main();
