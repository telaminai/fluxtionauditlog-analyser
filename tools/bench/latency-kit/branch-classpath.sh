#!/usr/bin/env bash
# Build a classpath that is provably the BRANCH under test, and refuse if it is not.
#
# Why this exists: every bench in this kit ran for a whole session against ~/.m2 snapshot jars rather
# than the worktrees whose code was being measured. Those jars happened to be current, so nothing
# looked wrong - which is the problem. A measurement that cannot say which build produced it is how
# stale figures survive, and "the snapshot in m2" is not an answer.
#
# Two rules:
#   1. Branch module target/classes come FIRST, so no jar can shadow the code under test.
#   2. Every com/telamin/fluxtion jar is DROPPED from the resolved dependency list. Not reordered -
#      removed. A fluxtion jar on this classpath has no legitimate purpose.
#
# Then it verifies by loading each key class and asking where it actually came from, because
# constructing a classpath correctly and having it resolve correctly are different claims.
#
# Usage:  eval "$(tools/bench/latency-kit/branch-classpath.sh)"   -> exports BENCH_CP
#         tools/bench/latency-kit/branch-classpath.sh --verify    -> prints the resolution table
set -euo pipefail
WT=${WORKTREES:-$HOME/IdeaProjects/telamin/worktrees}
CORE=${CORE_WT:-$WT/core-baseline}
COMP=${COMP_WT:-$WT/compiler-baseline}
JH=${JAVA_HOME:?set JAVA_HOME}
CACHE=${CP_CACHE:-/tmp/cp_branch.txt}

BRANCH_DIRS=(
  "$COMP/fluxtion-generator-cpp/target/classes"
  "$COMP/fluxtion-generator-core/target/classes"
  "$COMP/fluxtion-builder/target/classes"
  "$CORE/fluxtion-runtime/target/classes"
  "$CORE/fluxtion-builder-api/target/classes"
)
for d in "${BRANCH_DIRS[@]}"; do
  [ -d "$d" ] || { echo "MISSING branch build: $d - run mvn install in core-baseline then compiler-baseline" >&2; exit 1; }
done

if [ ! -s "$CACHE" ] || [ "${REFRESH:-0}" = "1" ]; then
  ( cd "$COMP" && mvn -o -q -pl fluxtion-generator-cpp dependency:build-classpath \
      -Dmdep.outputFile=/tmp/cp_deps.txt -DincludeScope=runtime >/dev/null 2>&1 )
  THIRD=$(tr ':' '\n' < /tmp/cp_deps.txt | grep -v 'com/telamin/fluxtion' | paste -sd: -)
  printf '%s:%s\n' "$(IFS=:; echo "${BRANCH_DIRS[*]}")" "$THIRD" > "$CACHE"
fi
CP=$(cat "$CACHE")

# A fluxtion jar here would defeat the whole point, and has before.
if tr ':' '\n' <<< "$CP" | grep -q 'com/telamin/fluxtion.*\.jar'; then
  echo "REFUSED: a fluxtion jar is on the classpath and would shadow the branch build:" >&2
  tr ':' '\n' <<< "$CP" | grep 'com/telamin/fluxtion.*\.jar' >&2
  exit 1
fi

if [ "${1:-}" = "--verify" ]; then
  D=$(mktemp -d)
  cat > "$D/CpVerify.java" <<'JAVA'
import java.security.CodeSource;
public class CpVerify {
    public static void main(String[] a) throws Exception {
        int bad = 0;
        for (String cls : new String[]{
                "com.telamin.fluxtion.runtime.time.Clock",
                "com.telamin.fluxtion.runtime.audit.EventLogger",
                "com.telamin.fluxtion.runtime.audit.BinaryLogRecord",
                "com.telamin.fluxtion.builder.generation.config.EventProcessorConfig",
                "com.telamin.fluxtion.sourcegenerator.modelgenerator.SimpleEventProcessorModel",
                "com.telamin.fluxtion.sourcegenerator.cppsrc.CppDslEmitter",
                "com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory"}) {
            CodeSource src = Class.forName(cls).getProtectionDomain().getCodeSource();
            String loc = src == null ? "<jdk>" : src.getLocation().toString();
            boolean branch = !loc.endsWith(".jar");
            if (!branch) { bad++; }
            System.out.printf("  %-14s %-34s %s%n", branch ? "branch-classes" : "JAR <-- STALE",
                    cls.substring(cls.lastIndexOf('.') + 1), loc.replaceAll(".*/worktrees/", "worktrees/"));
        }
        System.exit(bad == 0 ? 0 : 1);
    }
}
JAVA
  "$JH/bin/javac" -nowarn -d "$D" -cp "$CP" "$D/CpVerify.java"
  "$JH/bin/java" -cp "$D:$CP" CpVerify
  exit $?
fi
echo "export BENCH_CP='$CP'"
