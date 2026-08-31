#!/usr/bin/env bash
# Regenerate the M44 session processor and the M45 vocabulary fixtures, in one step.
#
# Doing this by hand is a five-command dance, and every command matters: forget the header strip and
# rule 1 fails; forget the fixtures and the M45 tests fail against a graph that has moved; forget to
# restore the POM and the repo depends on an unreleased SNAPSHOT. It has been got wrong three times,
# which is three times more than a script costs.
#
#   tools/regen-session-processor.sh            regenerate everything
#
# Requires a Fluxtion API key (~/.fluxtion/fluxtion.apiKeyFile) and, for the fixtures, a locally
# installed builder carrying the fluxtion.* vocabulary. The fixtures are skipped with a message if
# that build is absent, because the committed processor must still be regenerable without it.
set -euo pipefail
cd "$(dirname "$0")/.."

GRAPHML=src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml
FIXTURES=src/test/resources/topology/vocabulary
VOCAB_VERSION=1.0.65-SNAPSHOT

strip_header() {
  # The generator stamps a copyright line carrying a personal address on an employer domain onto
  # every file it emits. This repo is PUBLIC (rule 1). GeneratedSourceIsPublishableTest fails the
  # build if this is skipped — this is the fix, that is the guard.
  python3 - <<'PY'
import re, pathlib
for p in ["src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java",
          "src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java"]:
    f = pathlib.Path(p)
    if not f.exists():
        continue
    lines = f.read_text().splitlines(keepends=True)
    f.write_text("".join(l for l in lines if not re.match(r"^ \* Copyright: ©", l)))
PY
}

if [ -f "$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-builder/$VOCAB_VERSION/fluxtion-builder-$VOCAB_VERSION.jar" ]; then
  echo "→ refreshing the M45 fixtures with builder $VOCAB_VERSION"
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
  echo "→ SKIPPING the M45 fixtures: builder $VOCAB_VERSION is not installed locally."
  echo "  The committed processor is still regenerated below; the vocabulary fixtures keep their"
  echo "  current contents, which is correct — they are compiler OUTPUT and must not be hand-edited."
fi

echo "→ regenerating the committed processor with the pinned released builder"
mvn -q -Pregen process-classes
strip_header
git checkout -- dependency-reduced-pom.xml 2>/dev/null || true
rm -f src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/*.failed

echo "→ done. Now: mvn test"
