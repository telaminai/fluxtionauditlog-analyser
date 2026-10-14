#!/bin/bash
# Control fixtures for the L4 (iii) A/B: today's live downloads plus each rung's own modification (as the release ladder).
set -e
B=/private/tmp/fx-ab14; R=/private/tmp/fx-rel13; N=$B/downloads/u
for r in r2 r3 r4 r5; do mkdir -p $B/$r; done
cp -a $N/r2-scaffold/my-app $B/r2/fixture; cp -a $N/r4-scaffold/trade-app $B/r4/fixture
cp -a $N/bundle/audit-analyser-bundle $B/r3/fixture; cp -a $N/bundle/audit-analyser-bundle $B/r5/fixture
sed -i '' 's#<property name="name" value="pause"/><property name="nodeBeans"><list><value>child</value></list>#<property name="name" value="pause"/><property name="nodeBeans"><list><value>childTypo</value></list>#' $B/r2/fixture/src/main/fluxtion/designer/application-context.xml
diff $R/r2/fixture/src/main/fluxtion/designer/application-context.xml $B/r2/fixture/src/main/fluxtion/designer/application-context.xml && echo R2-XML-SAME
cp -a $R/r5/fixture/supplier-repo $B/r5/fixture/; cp $R/r5/fixture/data/fx.csv $B/r5/fixture/data/
for r in 2 3 4 5; do cp $R/r$r/TASK-R$r.txt $B/r$r/; done
cp $R/r2/R2Oracle.java $B/r2/; cp $R/r4/R4Oracle.java $B/r4/; cp $R/r3/r3_oracle.py $B/r3/; cp $R/r5/r5_oracle.py $B/r5/; cp -a $R/r5/supplier-src $B/r5/
for f in score_ladder.py probe_levers.py gt_check.py provenance.py treesha.py gtenv.sh content_reads.py; do cp $R/$f $B/; done
echo copied
