#!/bin/bash
# usage: score-cell.sh <cell-dir> <label>
set -u
W="$1"; LABEL="$2"
D=/Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser/docs/experience/runs/round-48
cd "$W" || exit 1
mvn -q -o dependency:build-classpath -Dmdep.outputFile=cp.txt >/dev/null 2>&1
rm -f sc-audit.txt sc-alerts.txt
java -cp "target/classes:lib/contracts.jar:lib/marketdata.jar:lib/pricing.jar:lib/liquidity.jar:lib/risk.jar:lib/capital.jar:$(cat cp.txt 2>/dev/null)" \
  com.acme.app.Main "$D/scoring-scenario.txt" sc-audit.txt sc-alerts.txt >/dev/null 2>&1
python3 - "$LABEL" "$W" <<'PY'
import re,sys,os
label,W=sys.argv[1],sys.argv[2]
D="/Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser/docs/experience/runs/round-48"
def pf(p):
    if not os.path.exists(p): return []
    rows=[]
    for b in re.split(r'(?=eventLogRecord:)', open(p).read()):
        m=re.search(r'^\s*event: (\S+)',b,re.M)
        if not m: continue
        ev=m.group(1).split('$')[-1].lower()
        if ev not in {"config","tick","rate","trade"}: continue
        rows.append((ev,[(s,float(v)) for s,v in re.findall(r'stage: ([\w.]+),?\s*\n?\s*value: ([-\d.eE+]+)',b)]))
    return rows
exp=pf(f"{D}/expected.txt"); got=pf(f"{W}/sc-audit.txt")
al=[l.strip() for l in open(f"{W}/sc-alerts.txt")] if os.path.exists(f"{W}/sc-alerts.txt") else []
eal=[l.strip() for l in open(f"{D}/expected.alerts")]
def c(rows,k): return [round(dict(st)[k],4) for ev,st in rows if k in dict(st)]
KEYS=("capital.breachCount","capital.alertCount","risk.streak","marketdata.ewma","marketdata.vol",
      "pricing.spread","risk.exposure","capital.fee","capital.charge","liquidity.score","capital.buffer")
bad=[k for k in KEYS if c(exp,k)!=c(got,k)]
ok = (len(exp)==len(got)) and not bad and [a for a in al if a]==[e for e in eal if e]
print(f"  {label:<10} {'PASS' if ok else 'FAIL'}   events {len(got)}/{len(exp)}   alerts {len([a for a in al if a])}/3", end="")
print(f"   wrong: {bad}" if bad else "")
PY
