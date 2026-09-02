#!/usr/bin/env bash
# Build, generate, test and run. The pom does the sequencing; there is no two-step dance.
# target/classes alone is NOT enough on the classpath — the runtime jar must be there too.
set -euo pipefail
cd "$(dirname "$0")"
mvn -q clean test
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" com.acme.app.Main "${1:-logs/audit.yaml}"
