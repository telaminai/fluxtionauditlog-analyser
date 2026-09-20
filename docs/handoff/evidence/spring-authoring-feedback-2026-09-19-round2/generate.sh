#!/usr/bin/env bash
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/.demo-env.sh"
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
test -f .fluxtion/fluxtion-starter-core.jar || { echo 'Run ./setup.sh first.' >&2; exit 2; }
test -f .fluxtion/classpath || { echo 'Run ./setup.sh to resolve the build classpath.' >&2; exit 2; }
java -jar .fluxtion/fluxtion-starter-core.jar regenerate "$@"
exec java -cp ".fluxtion/fluxtion-starter-core.jar:target/classes:$(cat .fluxtion/classpath)" com.telamin.fluxtion.starter.Starter build
