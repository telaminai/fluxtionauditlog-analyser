#!/usr/bin/env bash
# Show what actually ran, per event cycle. This is the orchestration, as executed.
#
#   ./trace.sh <scenario-file>
#
# Read the output before reading any source. It answers, per cycle:
#   which nodes ran, and in what order.
set -euo pipefail
cd "$(dirname "$0")"
[ -f cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
MAIN="${MAIN:-com.acme.app.Main}"
java -cp "target/classes:$(cat cp.txt)" "$MAIN" "$1" /tmp/trace-decisions.txt /tmp/trace-audit.yaml >/dev/null
python3 - "$1" <<'PY'
import re,sys,os
log="/tmp/trace-audit.yaml"
if not os.path.exists(log) or os.path.getsize(log)==0:
    print("NO AUDIT LOG. Enable it before anything else:")
    print("  build time, in buildGraph:  cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);")
    print("  runtime, BEFORE init():     flow.setAuditLogProcessor(rec -> lines.add(\"---\\n\"+rec));")
    raise SystemExit
recs=[r for r in open(log).read().split("eventLogRecord")[1:]
      if not re.search(r'LifecycleEvent|EventLogConfig',r)]
src=[l.strip() for l in open(sys.argv[1]) if l.strip() and not l.startswith("#")]
print(f"{'ev':>3}  {'event':<14} nodes that ran, in dispatch order")
for i,r in enumerate(recs,1):
    n=re.findall(r'^\s*- (\w+): \{',r,re.M)
    ev=(src[i-1].split(",")[0] if i<=len(src) else "?")
    print(f"{i:>3}  {ev:<14} {n if n else '(nothing ran)'}")
print("\ndecisions emitted:")
d=open("/tmp/trace-decisions.txt").read().strip()
print("  " + (d.replace("\n","\n  ") if d else "(none)"))
PY
