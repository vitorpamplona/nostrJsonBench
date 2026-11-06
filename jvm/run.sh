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
# Gradle itself runs on JAVA_HOME; the code is compiled to Java 27 bytecode with a JDK 27
# toolchain and runs on JDK 27: JDK27_HOME=<jdk>, else the first JDK 27 found below.
# BENCH_JAVA_HOME=<jdk> runs the benchmark on another JDK (27 or newer).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

if [[ -z "${JDK27_HOME:-}" ]]; then
    for jdk in /usr/lib/jvm/java-27-openjdk-amd64 /usr/lib/jvm/java-27-openjdk /usr/lib/jvm/temurin-27-jdk-amd64 "$HOME/.jdks/jdk-27"; do
        if [[ -x "$jdk/bin/java" ]]; then
            JDK27_HOME="$jdk"
            break
        fi
    done
fi
if [[ -z "${JDK27_HOME:-}" ]]; then
    echo "jvm: needs a JDK 27 (set JDK27_HOME)" >&2
    exit 1
fi

# QUARTZ_DIR=<vitorpamplona/quartz checkout> benchmarks that checkout's Quartz instead of the release.
QUARTZ_ARGS=()
if [[ -n "${QUARTZ_DIR:-}" ]]; then
    QUARTZ_ARGS=("-PquartzDir=$(cd "$QUARTZ_DIR" && pwd)")
fi
(cd "$HERE" && ./gradlew -q installDist "-Porg.gradle.java.installations.paths=$JDK27_HOME" "${QUARTZ_ARGS[@]+"${QUARTZ_ARGS[@]}"}")

# Defaults are absolute so the launcher works from any directory; flags given on the
# command line come later and win.
export JAVA_HOME="${BENCH_JAVA_HOME:-$JDK27_HOME}"
exec "$HERE/build/install/jsonbench/bin/jsonbench" \
    --corpus "$ROOT/.corpus" \
    --out "$ROOT/results/jvm.json" \
    "$@"
