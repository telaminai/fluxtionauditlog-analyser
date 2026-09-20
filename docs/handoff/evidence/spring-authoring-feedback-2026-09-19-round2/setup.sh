#!/usr/bin/env bash
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/.demo-env.sh"
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
# Provision once while online; validation and reconciliation then work offline.
./mvnw -q --version
mkdir -p .fluxtion
# The version is pinned by the downloaded authoring record.
STARTER_VERSION="$(sed -n 's/.*"starterVersion"[[:space:]]*:[[:space:]]*"\([0-9A-Za-z.-]*\)".*/\1/p' fluxtion-authoring.json)"
test -n "$STARTER_VERSION" || { echo 'Missing starterVersion in fluxtion-authoring.json' >&2; exit 2; }
if ! ./mvnw -q dependency:copy "-Dartifact=com.telamin.fluxtion:fluxtion-starter-core:$STARTER_VERSION:jar:all" -Dmdep.stripVersion=true -Dmdep.stripClassifier=true -DoutputDirectory=.fluxtion; then
  echo "Cannot provision Spring authoring: com.telamin.fluxtion:fluxtion-starter-core:$STARTER_VERSION:jar:all is unavailable. This workflow requires the compiler release that publishes that coordinate. Check publication and repository access before retrying; curated examples use their separate released BOM." >&2
  exit 2
fi
./mvnw -q dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=.fluxtion/classpath
