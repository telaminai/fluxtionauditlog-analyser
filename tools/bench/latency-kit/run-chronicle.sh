#!/bin/bash
SP=${KIT_OUT:?set KIT_OUT to the directory holding tclasses/ and tvendor/}
RT=$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.15-SNAPSHOT/fluxtion-runtime-1.0.15-SNAPSHOT.jar
MG=$HOME/.m2/repository/com/telamin/mongoose/1.0.28/mongoose-1.0.28.jar
DEPS=$(cat "$SP/chronicle-cp.txt")
JH=${JAVA_HOME:?set JAVA_HOME to a JDK 25}
exec "$JH/bin/java" \
  --enable-native-access=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
  --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  -Dchronicle.announcer.disable=true -Dchronicle.analytics.disable=true \
  "$@" -cp "$SP/tclasses:$SP/tvendor:$RT:$MG:$DEPS" app.BenchChronicle
