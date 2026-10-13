"""One-cell early report (counts only). Usage: early.py <run-id>"""
import json, sys, subprocess
from pathlib import Path
rid = sys.argv[1]; base = Path(f'/private/tmp/fx-{rid}')
s = json.loads((base / 'score.json').read_text()); l = json.loads((base / 'levers.json').read_text())
m = json.loads((base / rid / 'meta.json').read_text()); k = json.loads((base / rid / 'keyscan.json').read_text())
u = s['usage']; r = s.get('reads', {})
T = Path('/private/tmp/fx-rel13/early-split') / rid; T.mkdir(parents=True, exist_ok=True)
ts = subprocess.run([sys.executable, '-B', str(Path.home() / 'IdeaProjects/telamin/worktrees/analyser-ladder-release/tools/token_split.py'),
                     'split', str(base / rid / 'raw.jsonl'), '--out', str(T), '--run', rid], capture_output=True, text=True).stdout.strip()
print(json.dumps({'run': rid, 'PASS': s['PASS'], 'ORACLE': s['ORACLE']['ok'], 'VALID': s['VALID']['ok'], 'CHART': s['CHART']['ok'],
  'S6prime': s['S6prime']['ok'], 'costUsd': round(u['costUsd'], 4), 'turns': u['turns'], 'wallSeconds': round(s['wallSeconds']),
  'maxPrompt': u['maxPrompt'], 'over100k': u['over100k'], 'generatedReads': r.get('generatedReads'), 'toolResultBytes': r.get('toolResultBytes'),
  'runAndExportCalls': l.get('runAndExportCalls'), 'runExportCalls': l.get('runExportCalls'), 'annotationInExport': l.get('annotationInExport'),
  'auditTextFilesWithAnnotation': sum(1 for v in l.get('auditTextFiles', {}).values() if v['annotationKeys']),
  'generatorVersions': sorted(set(l.get('generatorVersion', {}).values())), 'withdrawCalls': l.get('withdrawCalls'),
  'noReconcileCalls': l.get('noReconcileCalls'), 'subagentCalls': l.get('subagentCalls'), 'subagentOffered': m.get('subagentOffered'),
  'keyscan': {x: k.get(x) for x in ('clean', 'secretOccurrences', 'tokenOccurrences', 'keyPathReads', 'tokenPathReads')},
  'capped': m.get('capped', False), 'reaped': m.get('processGroupReaped'), 'strays': len(m['strayProcesses']['swept']),
  'oracleDetail': str(s['ORACLE'].get('detail'))[:300], 'chart': s['CHART'], 'validTail': s['VALID'].get('tail', '')[-200:],
  'tokenSplit': ts}, indent=1))
