"""What fills the context past 100k (post-hoc analysis of the release ladder, 2026-10-13; reporting only, no effect on
PASS). Usage: past100k.py <evidence results dir> <run base glob>   prints JSON lines, one per run.

From the committed token_split tables (buckets.json requests: prompt, over100k, phase; categories.json calls: kind,
bytes, request) and, for the file reads, the run's transcript (raw.jsonl, never committed):
  crossing        index of the first request whose prompt exceeds 100,000 tokens (None if none), its phase, its prompt
  base            the first request's prompt (the base context every request re-pays)
  carriedByKind   tool-result bytes by kind returned before the crossing request (so carried in its prompt)
  reads           each tool call that read one of the named files (whole or in part): file, call bytes, request index,
                  phase, and whether the call also read other files (compound, so bytes are an upper bound)
  avoidedIfAbsent per file group, the over-100k requests that would have stayed at or under 100k had those calls'
                  bytes been absent: a request after the read is counted when prompt - bytes/4 <= 100,000.
                  INFERRED: 4 bytes per token is an assumption (it varies with content), and a run without the read
                  might have taken other turns.
"""
import glob, json, os, re, sys
from pathlib import Path

GROUPS = {
    'serverLog': re.compile(r'logs/server\.log'),
    'runbookMd': re.compile(r'(?<![\w/])RUNBOOK\.md'),
    'projectMd': re.compile(r'(?<![\w/])PROJECT\.md'),
    'runMongooseSkill': re.compile(r'run-mongoose-server/SKILL\.md'),
    'claudeMd': re.compile(r'(?<![\w/])CLAUDE\.md'),
    'readmeMd': re.compile(r'(?<![\w/])README\.md'),
    'addANodeSkill': re.compile(r'add-a-node/SKILL\.md'),
    'loadAuditLogSkill': re.compile(r'load-audit-log/SKILL\.md'),
}
READ_VERB = re.compile(r'\b(cat|head|tail|sed|less|more|awk|grep|nl)\b')


def tool_calls(raw):
    calls = []
    for line in open(raw, errors='replace'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('type') == 'assistant':
            for c in d['message'].get('content', []):
                if c.get('type') == 'tool_use':
                    calls.append((c.get('name'), c.get('input') or {}))
    return calls


def main(results, base_glob):
    for base in sorted(glob.glob(base_glob)):
        rid = os.path.basename(base)[3:]
        d = Path(results) / rid
        try:
            b = json.loads((d / 'buckets.json').read_text()); cat = json.loads((d / 'categories.json').read_text())
        except OSError:
            continue
        reqs = b['requests']; ccalls = cat['calls']
        raw = Path(base) / rid / 'raw.jsonl'
        tc = tool_calls(raw) if raw.exists() else []
        aligned = len(tc) == len(ccalls)
        cross = next((r for r in reqs if r['over100k']), None)
        out = {'run': rid, 'requests': len(reqs), 'over100k': sum(r['over100k'] for r in reqs), 'base': reqs[0]['prompt'] if reqs else None,
               'crossing': {'request': cross['n'], 'phase': cross['phase'], 'prompt': cross['prompt']} if cross else None,
               'callsAligned': aligned}
        if cross:
            kinds = {}
            for c in ccalls:
                if c['request'] is not None and c['request'] < cross['n']:
                    kinds[c['kind']] = kinds.get(c['kind'], 0) + c['bytes']
            out['carriedByKind'] = dict(sorted(kinds.items(), key=lambda kv: -kv[1]))
            out['carriedTotal'] = sum(kinds.values())
        reads = []
        if aligned:
            for (name, inp), c in zip(tc, ccalls):
                target = str(inp.get('command') or inp.get('file_path') or '')
                if name == 'Bash' and not READ_VERB.search(target):
                    continue
                if name not in ('Bash', 'Read', 'Grep'):
                    continue
                hit = [g for g, rx in GROUPS.items() if rx.search(target)]
                if not hit:
                    continue
                phase = next((r['phase'] for r in reqs if r['n'] == c['request']), None)
                files_named = len(re.findall(r'\.(md|log|ya?ml|java|xml|json|sh|txt)\b', target))
                reads.append({'files': hit, 'tool': name, 'bytes': c['bytes'], 'request': c['request'], 'phase': phase,
                              'compound': files_named > 1})
        out['reads'] = reads
        avoided = {}
        for g in GROUPS:
            rs = [x for x in reads if g in x['files']]
            if not rs:
                continue
            n = 0
            for r in reqs:
                if not r['over100k']:
                    continue
                removed = sum(x['bytes'] for x in rs if x['request'] is not None and x['request'] < r['n'])
                if removed and r['prompt'] - removed / 4 <= 100_000:
                    n += 1
            avoided[g] = {'reads': len(rs), 'bytes': sum(x['bytes'] for x in rs), 'over100kAvoided': n,
                          'anyCompound': any(x['compound'] for x in rs)}
        out['avoidedIfAbsent'] = avoided
        print(json.dumps(out))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
