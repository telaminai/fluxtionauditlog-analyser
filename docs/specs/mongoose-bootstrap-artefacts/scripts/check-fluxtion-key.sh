#!/usr/bin/env bash
# REVIEW COPY — source: mongoose-hosted-fluxtion/check-fluxtion-key.sh, 2026-08-28.
# Preflight your Fluxtion remote-compiler API key BEFORE a full build.
# This project compiles in-process (Fluxtion.compile at startup), which calls the Fluxtion
# source-gen API on RapidAPI — so it needs a valid, *subscribed* API key.
#
#   Get a key:  https://rapidapi.com/hub   (subscribe to the Fluxtion source-gen API)
#   Configure (any one):
#     - ${HOME}/.fluxtion/fluxtion.apiKeyFile   ->  apiKey=YOUR_KEY     [preferred]
#     - export FLUXTION_API_KEY=YOUR_KEY             (this script reads it)
#     - ./mvnw ... -Dfluxtion.apiKey=YOUR_KEY           (at build / run time)
set -uo pipefail

HOST="fluxtion-source-gen.p.rapidapi.com"
KEYFILE="${HOME}/.fluxtion/fluxtion.apiKeyFile"

# Resolve the key: FLUXTION_API_KEY wins, else the apiKey= line in the config file.
KEY="${FLUXTION_API_KEY:-}"
if [ -z "$KEY" ] && [ -f "$KEYFILE" ]; then
  KEY="$(grep -E '^apiKey=' "$KEYFILE" | head -1 | cut -d= -f2- | tr -d '[:space:]')"
fi

if [ -z "$KEY" ] || [ "$KEY" = "MISSING_KEY" ]; then
  echo "✗ No Fluxtion API key found."
  echo "  Looked at: \$FLUXTION_API_KEY and $KEYFILE (apiKey=)"
  echo "  Get one:   https://rapidapi.com/hub   (subscribe to the Fluxtion source-gen API)"
  echo "  Then set:  apiKey=YOUR_KEY   in $KEYFILE"
  exit 1
fi

echo "→ Checking key against https://$HOST ..."
# RapidAPI authenticates at the gateway, so even a bare GET reveals key/subscription state:
#   401 = invalid key, 403 = valid but not subscribed, anything else = passed the gateway.
CODE="$(curl -s -o /dev/null -w '%{http_code}' \
  -H "X-RapidAPI-Key: $KEY" -H "X-RapidAPI-Host: $HOST" \
  "https://$HOST/" 2>/dev/null)"

case "$CODE" in
  000) echo "✗ Network error reaching $HOST — check connectivity / proxy."; exit 2 ;;
  401) echo "✗ 401 Invalid API key — rejected by RapidAPI. Re-copy it from https://rapidapi.com/hub"; exit 1 ;;
  403) echo "✗ 403 — key invalid or not subscribed to the Fluxtion source-gen API."; echo "  Get a key / subscribe at https://rapidapi.com/hub"; exit 1 ;;
  *)   echo "✓ Key accepted by the RapidAPI gateway (HTTP $CODE) — you're good to build."; exit 0 ;;
esac
