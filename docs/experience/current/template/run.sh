#!/usr/bin/env bash
# Build, generate, test and run. target/classes alone is NOT enough on the classpath —
# the runtime jar must be there too, which is what build-classpath is for.
set -euo pipefail
cd "$(dirname "$0")"
mvn -q clean process-classes      # regenerates AppProcessor from AppGraphBuilder
mvn -q test
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" com.acme.app.Main "${1:-logs/audit.yaml}"
