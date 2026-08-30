#!/usr/bin/env bash
# Stage the bundle that docs/site/tutorial-playground.md is photographed against.
#
# The tutorial's figures are of a REAL generated bundle, not a mock-up and not the demo fixture: the
# page teaches that bundle, so a hand-arranged screenshot would teach something that does not exist.
# This script produces it the way a reader does — download the template, build it, run it, export —
# so the images and the instructions cannot drift apart. They did drift once: the first capture pass
# shot fluxtion-spring-mongoose while the page told the reader to download audit-analyser-bundle.
#
# TWO CONSTRAINTS, both load-bearing, neither obvious from reading capture-docs.py:
#
#   1. NEUTRAL PATH. The project's own path is rendered in the analyser's title bar, Project panel
#      and status bar. Staging under a home directory would put an account name into a PNG — and the
#      four-term privacy sweep cannot read images, so nothing downstream would catch it. This script
#      refuses a destination inside $HOME; capture-docs.py refuses again, independently.
#
#   2. THE PROCESSOR IS COMMITTED. Generation needs a Fluxtion API key; running the bundle does not.
#      That asymmetry is the tutorial's headline claim, so this script generates ONCE with a key and
#      then builds again with the key made UNREACHABLE — if the second build fails, the claim is
#      false and staging stops rather than producing figures that flatter it.
#
#      Making it unreachable takes -Duser.home, NOT HOME. A JVM does not read $HOME: on macOS
#      user.home comes from the passwd entry, so a build launched with HOME=/tmp/... still reads the
#      real ~/.fluxtion/fluxtion.apiKeyFile and the real ~/.m2. The first version of this script made
#      exactly that mistake and "proved" a keyless build while the key sat in plain reach — the same
#      $HOME/user.home divergence already found once during MCP acceptance. Because a claim of
#      isolation is worth nothing unless the isolation is shown to bite, step 3b runs the GENERATING
#      build under the same isolation and requires it to FAIL for want of a key.
#
# Usage:  tools/stage-tutorial-bundle.sh [dest] [path-to-fluxtion-web]
# Then:   tools/capture-docs.py --tutorial
set -euo pipefail

DEST="${1:-/tmp/fluxtion-tutorial}"
WEB="${2:-${FLUXTION_WEB:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../fluxtion-web" 2>/dev/null && pwd || true)}}"
TEMPLATE=analyser-bundle.starter.json
ART=audit-analyser-bundle

case "$DEST" in
  "$HOME"|"$HOME"/*)
    echo "refusing: $DEST is inside $HOME — the bundle's path is rendered in the title bar," >&2
    echo "Project panel and status bar, so an account name would end up in a committed PNG." >&2
    exit 1 ;;
esac
[ -d "${WEB:-}/web" ] || { echo "pass the fluxtion-web checkout: $0 '$DEST' /path/to/fluxtion-web" >&2; exit 1; }

echo "==> 1. download the template a reader is told to download ($TEMPLATE)"
rm -rf "$DEST"; mkdir -p "$DEST/zip"
( cd "$WEB/web" && BUNDLE_ZIP_DIR="$DEST/zip" BUNDLE_TEMPLATE="$TEMPLATE" \
    npx vitest run src/lib/repl/maven-download-bundle.test.ts ) >"$DEST/generate.log" 2>&1 \
  || { echo "generation failed; see $DEST/generate.log" >&2; exit 1; }
( cd "$DEST" && unzip -q zip/*.zip )
P="$DEST/$ART"
[ -d "$P" ] || { echo "expected $P in the zip — the template's artifact name changed" >&2; exit 1; }

echo "==> 2. generate the processor (the one step that needs a key)"
( cd "$P" && ./mvnw -q -Pgenerate-fluxtion package ) >"$DEST/generate-processor.log" 2>&1 \
  || { echo "processor generation failed; see $DEST/generate-processor.log — a key is required here" >&2; exit 1; }

ISO_HOME="$DEST/keyless-home"; mkdir -p "$ISO_HOME"
ISOLATE=(-Duser.home="$ISO_HOME" -Dfluxtion.apiKey=)

echo "==> 3a. build again with the key UNREACHABLE — the tutorial's headline claim"
( cd "$P" && env -u FLUXTION_API_KEY HOME="$ISO_HOME" ./mvnw -q "${ISOLATE[@]}" package ) \
    >"$DEST/build-keyless.log" 2>&1 \
  || { echo "THE KEYLESS BUILD FAILED — the page claims this works; see $DEST/build-keyless.log" >&2; exit 1; }

echo "==> 3b. prove that isolation is real: the GENERATING build must fail without a key"
cp "$P/src/main/java/com/example/myapp/generated/MarketProcessor.java" "$DEST/MarketProcessor.java.bak"
if ( cd "$P" && env -u FLUXTION_API_KEY HOME="$ISO_HOME" ./mvnw -q "${ISOLATE[@]}" \
        -Pgenerate-fluxtion package ) >"$DEST/probe-generate.log" 2>&1; then
  cp "$DEST/MarketProcessor.java.bak" "$P/src/main/java/com/example/myapp/generated/MarketProcessor.java"
  echo "THE ISOLATION DOES NOT BITE: generation SUCCEEDED with the key supposedly out of reach," >&2
  echo "so step 3a proved nothing about a keyless build. See $DEST/probe-generate.log." >&2
  exit 1
fi
cp "$DEST/MarketProcessor.java.bak" "$P/src/main/java/com/example/myapp/generated/MarketProcessor.java"
( cd "$P" && ./mvnw -q package ) >>"$DEST/build-keyless.log" 2>&1   # restore the jar the probe disturbed

echo "==> 4. run, export, stop — all bundle-owned commands, as the page lists them"
REG="$DEST/servers"; mkdir -p "$REG"
( cd "$P" && MONGOOSE_SERVERS_DIR="$REG" nohup ./run-server.sh >"$DEST/server.log" 2>&1 & )
for _ in $(seq 1 60); do [ -f "$REG/$ART" ] && break; sleep 1; done
[ -f "$REG/$ART" ] || { echo "server never published its registry entry; see $DEST/server.log" >&2; exit 1; }
sleep 5   # let the file feed drive its events through before the capture is closed
( cd "$P" && MONGOOSE_SERVERS_DIR="$REG" ./export-audit.sh ) >"$DEST/export.log" 2>&1 \
  || { echo "export failed; see $DEST/export.log" >&2; exit 1; }
( cd "$P" && MONGOOSE_SERVERS_DIR="$REG" ./stop-server.sh ) >"$DEST/stop.log" 2>&1 || true

LOG="$P/logs/audit-$ART.yaml"
[ -s "$LOG" ] || { echo "no exported log at $LOG" >&2; exit 1; }

# The figure that matters shows a node ACTUALLY RUNNING. A bundle whose feed produces no business
# events exports a structurally valid log full of lifecycle records and photographs as an empty
# analyser — which is exactly what shipped once, at coverage 0.0. Fail here instead.
CYCLES=$(grep -c "PriceEvent" "$LOG" || true)
[ "$CYCLES" -gt 0 ] || { echo "the export contains no PriceEvent cycles — the cycle figure would be empty" >&2; exit 1; }

echo
echo "staged: $P"
echo "log:    $LOG ($CYCLES PriceEvent references)"
echo "next:   tools/capture-docs.py --tutorial"
