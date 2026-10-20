#!/usr/bin/env bash
#
# nostrJsonBench Rust harness: builds the release binary (fat LTO, one codegen unit,
# -C target-cpu=native) and runs it. Every HARNESS.md flag passes through:
#
#   rust/run.sh                        # all workloads, all parsers
#   rust/run.sh --quick --workloads all
#   rust/run.sh --parsers serde_json-typed,sonic_rs-typed
#   rust/run.sh --list
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH="$(dirname "$HERE")"

export RUSTFLAGS="${RUSTFLAGS:--C target-cpu=native}"
cargo build --release --locked --quiet --manifest-path "$HERE/Cargo.toml" >&2

if [[ ! -f "$BENCH/.corpus/manifest.json" ]]; then
    python3 "$BENCH/corpus/prepare.py" >&2
fi

# Defaults first; flags the caller passes come later and win.
exec "$HERE/target/release/jsonbench-rust" \
    --corpus "$BENCH/.corpus" \
    --out "$BENCH/results/rust.json" \
    "$@"
