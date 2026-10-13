"""Lever probe for one baseline run (supplements score_ladder.py; does not affect PASS). Prints JSON.

Usage: probe_levers.py <base> <run-id>

From the transcript (raw.jsonl):
  runExportCalls      tool calls whose command starts, exports from or stops a run: run.sh, run-server.sh,
                      export-audit.sh, run-and-export.sh, stop-server.sh, BundleLifecycle, or `java -jar target/...`.
                      Defined for the bundle rungs (3, 5); a Spring-rung subject that drives its own java class is not counted.
  runAndExportCalls   of those, calls naming run-and-export.sh (the shipped one-command lever)
  noReconcileCalls    calls whose command contains --no-reconcile
  reconcileConflicts  tool results containing SPRING_RECONCILE_CONFLICT
  generateCalls       calls whose command runs generate.sh
  withdrawCalls       calls whose command runs the starter's `withdraw` command (fluxtion-starter-core ... withdraw)
  subagentCalls       tool calls of the subagent tool (Agent, or its alias Task)
  serviceRegisteredWrites  Write/Edit calls whose new text contains `@ServiceRegistered` or `ServiceRegistered` (a
                      hand-wired service or sink callback)
From the final project:
  exportedLogs        logs/*.yaml under the project: per file, the record count and the count of `annotation:` keys
  annotationInExport  True when some exported log carries at least one `annotation:` key; None when none was exported
  auditTextFiles      other text files (any name) holding audit records (`eventLogRecord`), with `annotation:` counts
  generatorVersion    the `target generator version` line of each generated processor
"""
import json, re, sys
from pathlib import Path

RUN = re.compile(r'((?<![\w-])run\.sh|run-server\.sh|export-audit\.sh|run-and-export\.sh|stop-server\.sh|BundleLifecycle|java\s+[^|;&]*-jar\s+\S*target/)')


def transcript(raw):
    names, out = {}, {'runExportCalls': 0, 'runAndExportCalls': 0, 'noReconcileCalls': 0, 'reconcileConflicts': 0,
                      'generateCalls': 0, 'withdrawCalls': 0, 'subagentCalls': 0, 'serviceRegisteredWrites': 0}
    for line in raw.open(errors='replace'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('type') == 'assistant':
            for c in d['message'].get('content', []):
                if c.get('type') == 'tool_use':
                    cmd = str((c.get('input') or {}).get('command') or '')
                    if c.get('name') in ('Agent', 'Task'):
                        out['subagentCalls'] += 1
                    if c.get('name') in ('Write', 'Edit') and 'ServiceRegistered' in json.dumps(c.get('input') or {}):
                        out['serviceRegisteredWrites'] += 1
                    if re.search(r'starter-core[^|;&]*\bwithdraw\b|generate\.sh[^|;&]*\bwithdraw\b', cmd):
                        out['withdrawCalls'] += 1
                    names[c['id']] = cmd
                    if RUN.search(cmd):
                        out['runExportCalls'] += 1
                    if 'run-and-export.sh' in cmd:
                        out['runAndExportCalls'] += 1
                    if '--no-reconcile' in cmd:
                        out['noReconcileCalls'] += 1
                    if 'generate.sh' in cmd:
                        out['generateCalls'] += 1
        elif d.get('type') == 'user' and isinstance(d['message'].get('content'), list):
            for c in d['message']['content']:
                if c.get('type') == 'tool_result':
                    t = c.get('content')
                    t = t if isinstance(t, str) else json.dumps(t)
                    if 'SPRING_RECONCILE_CONFLICT' in t:
                        out['reconcileConflicts'] += 1
    return out


def project(root):
    out = {'exportedLogs': {}, 'auditTextFiles': {}, 'generatorVersion': {}}
    if not root.exists():
        return out
    for p in sorted(root.rglob('*')):
        if not p.is_file() or p.stat().st_size > 8 * 1024 * 1024 or '/.fluxtion/' in str(p) or '/m2/' in str(p):
            continue
        rel = str(p.relative_to(root))
        if p.suffix == '.java' and '/generated/' in '/' + rel:
            m = re.search(r'target generator version\s*:\s*(\S+)', p.read_text(errors='replace'))
            if m:
                out['generatorVersion'][rel] = m.group(1)
            continue
        if p.suffix not in ('.yaml', '.yml', '.log', '.txt', '.out'):
            continue
        text = p.read_text(errors='replace')
        if 'eventLogRecord' not in text:
            continue
        stat = {'records': len(re.findall(r'eventLogRecord', text)), 'annotationKeys': len(re.findall(r'\bannotation:', text))}
        if p.parent.name == 'logs' and p.suffix == '.yaml':
            out['exportedLogs'][rel] = stat
        else:
            out['auditTextFiles'][rel] = stat
    logs = out['exportedLogs'].values()
    out['annotationInExport'] = any(s['annotationKeys'] > 0 for s in logs) if logs else None
    return out


def main(base, rid):
    base = Path(base)
    raw = base / rid / 'raw.jsonl'
    res = {'run': rid}
    res.update(transcript(raw) if raw.exists() else {})
    res.update(project(base / 'project'))
    print(json.dumps(res, default=str))


if __name__ == '__main__':
    main(*sys.argv[1:3])
