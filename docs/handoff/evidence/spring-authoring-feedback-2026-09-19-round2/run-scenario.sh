#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
source .demo-env.sh
exec java -Djava.awt.headless=true -cp "target/classes:$(cat .fluxtion/classpath)" com.example.myapp.AcceptanceScenario "${1:-evidence/latest}"
