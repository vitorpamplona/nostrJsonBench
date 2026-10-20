# nostrJsonBench

How fast do JSON parsers turn a Nostr event into an object? ~150 parsers in six languages,
31k real events, every output verified. Method: [HARNESS.md](HARNESS.md). All results:
[report.md](results/reference/2026-10-20/report.md).

## Results

Best parser per runtime, ns per event (2026-10-20, 4-core Xeon, single thread):

| runtime | parser | ns/event | × quartz |
|---|---|---:|---:|
| C++ | simdjson | 635 | 2.6× |
| Rust | sonic-rs | 677 | 2.4× |
| Go | sonic | 738 | 2.2× |
| JVM | jsoniter-scala ¹ | 836 | 1.9× |
| Deno | `JSON.parse` | 988 | 1.6× |
| Python | msgspec | 1,054 | 1.5× |
| Node | `JSON.parse` | 1,135 | 1.4× |
| Bun | `Bun.JSONL.parse` | 1,178 | 1.4× |
| JVM | **Quartz (`quartz-jackson`)** | **1,620** | 1.0× |

JVM, each library's fastest path from a `String`:

| library | ns/event | library | ns/event |
|---|---:|---|---:|
| jsoniter-scala ¹ | 836 | **Quartz (Jackson)** | **1,620** |
| fastjson2 | 905 | Quartz (kotlinx) | 1,643 |
| Jackson (via bytes) | 1,338 | kotlinx.serialization | 1,871 |
| jsoniter (Java) | 1,345 | Moshi | 1,936 |
| DSL-JSON | 1,354 | Gson | 3,309 |

¹ Not on Android (Scala runtime).

- Quartz's time is almost all Jackson's tokenizer; switching tokenizer (fastjson2, Jackson
  fed bytes) is where the remaining gain is.
- Failing verification: cJSON, ojg (Go), `@jsonjoy.com/json-pack`, the `simdjson` npm addon.

## Run

```bash
./run.sh            # all installed toolchains, ~1 h
./run.sh --quick    # smoke test
./run.sh --only jvm # one harness; QUARTZ_DIR=../amethyst for an unreleased Quartz
```
