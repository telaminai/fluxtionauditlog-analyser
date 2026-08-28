#!/bin/bash
# REVIEW COPY — source: mongoose-hosted-fluxtion/run-server.sh, 2026-08-28.
# Boot the Mongoose server. Builds the fat jar on first run. Web console: http://127.0.0.1:8181.
# --add-opens flags are required: Agrona/Aeron (via Mongoose) access jdk.internal.misc.Unsafe.
# (The jar manifest also carries Add-Opens; explicit flags keep the script correct if the
# jar is rebuilt without the manifest entry or run through a wrapper.)
# The config file rides in as -DmongooseServer.config.file so services that re-read the
# descriptor (e.g. the web admin console) resolve the same yaml.
set -e
cd "$(dirname "${BASH_SOURCE[0]}")"
JAR="target/mongoose-hosted-fluxtion-1.0.0-SNAPSHOT.jar"
[ -f "$JAR" ] || ./mvnw -q package
exec java \
  -DmongooseServer.config.file=config/server-config.yml \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.util=ALL-UNNAMED \
  -jar "$JAR"
