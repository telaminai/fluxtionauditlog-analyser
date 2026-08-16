#!/bin/bash
# Drive the running analyser over its localhost REST transport.
#   tools/drive-analyser.sh <verb> ['<json params>']
set -euo pipefail
VERB="$1"
PARAMS="${2:-}"
[ -z "$PARAMS" ] && PARAMS='{}'      # a no-arg verb still needs a params object
EP=$(cat ~/.fluxtion-analyser/rest-endpoint)
URL=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['url'])" "$EP")
TOK=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['token'])" "$EP")
curl -s -X POST "$URL/action" -H 'Content-Type: application/json' -H "X-Analyser-Token: $TOK" \
  -d "{\"v\":1,\"action\":\"$VERB\",\"params\":$PARAMS}"
echo
