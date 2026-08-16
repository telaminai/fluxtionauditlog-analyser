#!/bin/bash
# Drive the running analyser over its localhost REST transport.
#   ./drive.sh <verb> '<json params>'
set -euo pipefail
EP=$(cat ~/.fluxtion-analyser/rest-endpoint)
URL=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['url'])" "$EP")
TOK=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['token'])" "$EP")
curl -s -X POST "$URL/action" -H 'Content-Type: application/json' -H "X-Analyser-Token: $TOK" \
  -d "{\"v\":1,\"action\":\"$1\",\"params\":${2:-\{\}}}"
echo
