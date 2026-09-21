#!/usr/bin/env bash
# Build the vendor jar against fluxtion-runtime ONLY - no builder, no Spring, no knowledge of the customer.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
source ../../.demo-env.sh
VERSION="${1:-1.0.0}"
RUNTIME="$(tr ':' '\n' < ../../.fluxtion/classpath | grep 'fluxtion-runtime-' | head -1)"
rm -rf out && mkdir -p out dist
javac --release 21 -cp "$RUNTIME" -d out $(find src -name '*.java')
jar --create --file "dist/acme-risk-$VERSION.jar" -C out .
shasum -a 256 "dist/acme-risk-$VERSION.jar"
