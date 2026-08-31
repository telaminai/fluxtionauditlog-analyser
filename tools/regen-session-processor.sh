#!/usr/bin/env bash
# Refresh the M45 vocabulary FIXTURES — the one part of regeneration Maven does not express well.
#
#   mvn -Pregen process-classes                  regenerates the processor. Use this for normal work.
#   tools/regen-session-processor.sh             ALSO re-captures the three vocabulary fixtures.
#
# What this script no longer does, because the build does it: stripping the generator's attribution
# line. That is a `maven-antrun-plugin` execution inside the `regen` profile, bound after the scan goal.
# It was a manual step for four regenerations and was forgotten on three, and a guard test caught each
# one — which is the guard working and the step being in the wrong place.
#
# What is left here is a loop over three emission modes, capturing each into a committed fixture. Maven
# can express one profile per mode, but not "run the same goal three times with a different system
# property and keep each output", and three near-identical profiles would be three things to drift.
#
# Requires a Fluxtion API key (~/.fluxtion/fluxtion.apiKeyFile) and a locally installed builder carrying
# the fluxtion.* vocabulary. Without the latter the fixtures are left alone and only the processor is
# regenerated — the committed processor must stay regenerable from a released builder.
set -euo pipefail
cd "$(dirname "$0")/.."

GRAPHML=src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml
FIXTURES=src/test/resources/topology/vocabulary
VOCAB_VERSION=1.0.65-SNAPSHOT

if [ -f "$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-builder/$VOCAB_VERSION/fluxtion-builder-$VOCAB_VERSION.jar" ]; then
  echo "→ re-capturing the M45 fixtures with builder $VOCAB_VERSION"
  cp pom.xml /tmp/regen-pom.bak
  python3 - "$VOCAB_VERSION" <<'PY'
import sys
s = open('pom.xml').read()
open('pom.xml', 'w').write(
    s.replace('<fluxtion.builder.version>1.0.64</fluxtion.builder.version>',
              '<fluxtion.builder.version>%s</fluxtion.builder.version>' % sys.argv[1]))
PY
  mkdir -p "$FIXTURES"
  for mode in PARALLEL AGGREGATED OFF; do
    if [ "$mode" = OFF ]; then
      mvn -q -Pregen process-classes
      cp "$GRAPHML" "$FIXTURES/session-processor-off-new-builder.graphml"
    else
      mvn -q -Pregen process-classes "-Dfluxtion.graphml.metadata=$mode"
      cp "$GRAPHML" "$FIXTURES/session-processor-$(echo "$mode" | tr 'A-Z' 'a-z').graphml"
    fi
    echo "   $mode"
  done
  cp /tmp/regen-pom.bak pom.xml
else
  echo "→ skipping the fixtures: builder $VOCAB_VERSION is not installed locally"
fi

echo "→ regenerating the committed processor with the pinned released builder"
mvn -q -Pregen process-classes
git checkout -- dependency-reduced-pom.xml 2>/dev/null || true
rm -f src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/*.failed
echo "→ done (the build stripped the attribution line). Now: mvn test"
