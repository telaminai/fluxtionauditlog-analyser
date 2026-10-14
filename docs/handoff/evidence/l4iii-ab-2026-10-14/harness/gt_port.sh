#!/bin/bash
# Port the release ladder's ground truths (hand-authored files) onto today's control fixtures; regenerate; run.
B=/private/tmp/fx-ab14; R=/private/tmp/fx-rel13; export MONGOOSE_SERVERS_DIR=$B/mongoose-servers
port() { r=$1; shift; G=$B/gt-$r/project; mkdir -p $B/gt-$r/tmp; cp -a $B/$r/fixture $G; echo "<settings/>" > $B/gt-$r/tmp/settings-empty.xml
  s=spring; case $r in r3|r5) s=bundle;; esac; cp -a $R/seed-$s $B/gt-$r/tmp/m2; chmod -R u+w $B/gt-$r/tmp/m2
  for f in "$@"; do mkdir -p $(dirname $G/$f); cp -a $R/gt-$r/project/$f $G/$f; done; }
port r2 src/main/fluxtion/designer/application-context.xml src/main/java/com/example/myapp/event/Checked.java src/main/java/com/example/myapp/node/Child.java src/main/java/com/example/myapp/node/RootNode.java fluxtion-authoring.json
port r4 src/main/fluxtion/designer/application-context.xml src/main/java/com/example/myapp/event/VwapUpdate.java src/main/java/com/example/myapp/node/TradeIntake.java src/main/java/com/example/myapp/node/VwapCalc.java fluxtion-authoring.json
port r3 src/main/fluxtion/designer/application-context.xml src/main/java/com/example/myapp/node/AlertCounter.java src/main/java/com/example/myapp/node/LargeTradeFlag.java src/main/java/com/example/myapp/service fluxtion-authoring.json config/server-config.yml
port r5 src/main/fluxtion/designer/application-context.xml config/server-config.yml pom.xml
for r in r2 r4 r3 r5; do
  cd $B/gt-$r/project; source $B/gtenv.sh $B/gt-$r/tmp/m2; export MONGOOSE_SERVERS_DIR=$B/mongoose-servers
  ./setup.sh > ../setup.out 2>&1; s=$?; ./generate.sh > ../generate.out 2>&1; g=$?
  case $r in r2|r4) ./run.sh > ../run.out 2>&1;; *) rm -f target/audit-analyser-bundle-1.0.0-SNAPSHOT.jar; ./run-and-export.sh > ../run.out 2>&1;; esac; x=$?
  echo "$r setup=$s generate=$g run=$x gen=$(grep -ho 'target generator version *: *[0-9.]*' src/main/java/com/example/myapp/generated/*.java | head -1)"
done
