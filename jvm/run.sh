#!/usr/bin/env bash
#
# nostrJsonBench JVM harness: builds the Gradle project in jvm/ and runs it with the HARNESS.md flags.
#
#   ./jvm/run.sh                      # everything -> results/jvm.json
#   ./jvm/run.sh --quick              # smoke run
#   ./jvm/run.sh --parsers quartz-jackson,fastjson2-reader --workloads all,notes
#   ./jvm/run.sh --list
#
# The installed `jsonbench` script carries the JVM flags (-Xmx2g -XX:+UseG1GC
# --add-modules jdk.incubator.vector); the parent process forks one child JVM per parser
# with those same flags. Extra JVM flags can be passed through JAVA_OPTS.
#
# BENCH_JAVA_HOME=<jdk> runs the benchmark on that JDK (Gradle still builds on JAVA_HOME).
# The reference run uses JetBrains Runtime 21, the JDK Amethyst Desktop ships on Linux
# (Temurin 21 on macOS and Windows).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

# QUARTZ_DIR=<vitorpamplona/quartz checkout> benchmarks that checkout's Quartz instead of the release.
QUARTZ_ARGS=()
if [[ -n "${QUARTZ_DIR:-}" ]]; then
    QUARTZ_ARGS=("-PquartzDir=$(cd "$QUARTZ_DIR" && pwd)")
fi
(cd "$HERE" && ./gradlew -q installDist "${QUARTZ_ARGS[@]+"${QUARTZ_ARGS[@]}"}")

# Defaults are absolute so the launcher works from any directory; flags given on the
# command line come later and win.
if [[ -n "${BENCH_JAVA_HOME:-}" ]]; then
    export JAVA_HOME="$BENCH_JAVA_HOME"
fi
exec "$HERE/build/install/jsonbench/bin/jsonbench" \
    --corpus "$ROOT/.corpus" \
    --out "$ROOT/results/jvm.json" \
    "$@"
