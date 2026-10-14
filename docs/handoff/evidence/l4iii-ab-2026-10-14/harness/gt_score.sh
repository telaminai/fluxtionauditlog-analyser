#!/bin/bash
B=/private/tmp/fx-ab14; R=/private/tmp/fx-rel13; export MONGOOSE_SERVERS_DIR=$B/mongoose-servers; mkdir -p $B/checks
cd $B
for r in 2 3 4 5; do python3 -B gt_check.py $r $B/gt-r$r > checks/gt-r$r.json; python3 -c "import json;d=json.load(open('checks/gt-r$r.json'));print('gt-r$r VALID',d['VALID'],'ORACLE',d['ORACLE']['ok'])"; done
for r in 2 4; do C=$B/ctl-r$r; mkdir -p $C/tmp; cp -a $B/r$r/fixture $C/project; cp -a $R/seed-spring $C/tmp/m2; chmod -R u+w $C/tmp/m2; echo "<settings/>" > $C/tmp/settings-empty.xml
  (cd $C/project && source $B/gtenv.sh $C/tmp/m2 && ./setup.sh >/dev/null 2>&1); python3 -B gt_check.py $r $C > checks/ctl-r$r.json; python3 -c "import json;d=json.load(open('checks/ctl-r$r.json'));print('ctl-r$r (unsolved) VALID',d['VALID'],'ORACLE',d['ORACLE']['ok'])"; done
python3 -B r3/r3_oracle.py r5/baseline/logs/audit-marketProcessor.yaml > checks/ctl-r3-noop.json; python3 -c "import json;d=json.load(open('checks/ctl-r3-noop.json'));print('ctl-r3 (as shipped) ORACLE',d['ok'])"
C=$B/ctl-r5; mkdir -p $C; cp -a $B/r5/fixture $C/project; (cd $C/project && source $B/gtenv.sh $B/gt-r3/tmp/m2 && export MONGOOSE_SERVERS_DIR=$B/mongoose-servers && ./setup.sh >/dev/null 2>&1 && ./run-and-export.sh > ../run.out 2>&1); python3 -B r5/r5_oracle.py $C/project > checks/ctl-r5-noop.json; python3 -c "import json;d=json.load(open('checks/ctl-r5-noop.json'));print('ctl-r5 (unintegrated) ORACLE',d['ok'],'I1',d['I1'],'I2',d['I2'],'I3',d['I3'])"
