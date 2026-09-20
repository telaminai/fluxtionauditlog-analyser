#!/usr/bin/env bash
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/.demo-env.sh"
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
test -f .fluxtion/fluxtion-starter-core.jar || { echo 'Run ./setup.sh first.' >&2; exit 2; }
exec java -jar .fluxtion/fluxtion-starter-core.jar validate "$@"
