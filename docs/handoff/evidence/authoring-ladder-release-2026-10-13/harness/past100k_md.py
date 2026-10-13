"""Render TOKEN-SPLIT.md's 'what fills the context past 100k' tables from the committed files only.
Usage: past100k_md.py <evidence dir>   (reads results/<run>/{buckets,phases}.json, past100k.jsonl, service-failures.jsonl)"""
import json, sys
from pathlib import Path

E = Path(sys.argv[1]); R = E / 'results'
P = {json.loads(l)['run']: json.loads(l) for l in (E / 'part-a/past100k.jsonl').read_text().splitlines()}
S = {json.loads(l)['run']: json.loads(l) for l in (E / 'part-a/service-failures.jsonl').read_text().splitlines()}
KINDS = ['design', 'docs', 'build', 'analyser', 'source', 'library', 'auditLog', 'generated', 'server', 'other']
k = lambda b: f'{b / 1000:.1f}'

print('### Where each run first crosses 100k\n')
print('Prompt = input + cache-read + cache-write tokens of one request. Carried bytes = tool-result bytes returned '
      'before the crossing request, so inside its prompt, by L0 kind (kB).\n')
print('| run | requests | >100k | base prompt | first >100k request | its phase | its prompt | carried kB total | ' + ' | '.join(KINDS) + ' |')
print('|' + '---|' * (8 + len(KINDS)))
for rid, d in P.items():
    c = d['crossing']
    if not c:
        print(f"| {rid} | {d['requests']} | 0 | {d['base']} | — | — | — | — |" + ' — |' * len(KINDS)); continue
    ck = d.get('carriedByKind', {})
    print(f"| {rid} | {d['requests']} | {d['over100k']} | {d['base']} | {c['request']} | {c['phase']} | {c['prompt']} | "
          f"{k(d['carriedTotal'])} | " + ' | '.join(k(ck.get(x, 0)) for x in KINDS) + ' |')

print('\n### Over-100k requests by phase, and the lever each points to (INFERRED)\n')
print('| run | design | run | diagnosis | design tool kB | lever the split points to (INFERRED) |')
print('|---|---|---|---|---|---|')
for rid in P:
    ph = json.loads((R / rid / 'phases.json').read_text())['phases']
    o = {p: ph.get(p, {}).get('over100k', 0) for p in ('design', 'run', 'diagnosis')}
    db = ph.get('design', {}).get('toolResultBytes', 0)
    tot = sum(o.values())
    if not tot:
        lever = 'none needed: stayed under 100k'
    elif o['diagnosis'] * 2 > tot:
        lever = 'fresh context / fewer diagnosis turns (a strict majority of over-100k requests are in diagnosis)'
    elif o['diagnosis'] >= max(o['design'], o['run']) and o['diagnosis'] > 0:
        lever = f"fresh context, in part (diagnosis is the largest phase, {o['diagnosis']} of {tot}, but not a majority)"
    elif o['design'] >= o['run']:
        lever = 'fewer orientation reads / smaller base (crossed during design)'
    else:
        lever = 'fewer run/diagnosis turns after the export (crossed in the run phase)'
    print(f"| {rid} | {o['design']} | {o['run']} | {o['diagnosis']} | {k(db)} | {lever} |")

print('\n### Named file reads: bytes, request, phase, and over-100k requests each would have avoided if absent (INFERRED)\n')
print('A read is a Bash call whose read verb (cat, head, tail, sed, grep, awk, nl, less, more) names the file, or a '
      'Read/Grep of it. A compound call reads other files too, so its bytes are an upper bound for the file. "Avoided" '
      'counts later over-100k requests whose prompt minus those bytes / 4 would be at or under 100,000. The 4 bytes per '
      'token, and the assumption that nothing else would have been read instead, are INFERRED.\n')
groups = ['serverLog', 'runbookMd', 'projectMd', 'runMongooseSkill', 'addANodeSkill', 'loadAuditLogSkill', 'claudeMd', 'readmeMd']
print('| run | ' + ' | '.join(f'{g}: reads / kB / avoided' for g in groups) + ' |')
print('|' + '---|' * (1 + len(groups)))
tot = {g: [0, 0, 0] for g in groups}
for rid, d in P.items():
    a = d['avoidedIfAbsent']; cells = []
    for g in groups:
        if g in a:
            x = a[g]; cells.append(f"{x['reads']} / {k(x['bytes'])}{'*' if x['anyCompound'] else ''} / {x['over100kAvoided']}")
            tot[g][0] += x['reads']; tot[g][1] += x['bytes']; tot[g][2] += x['over100kAvoided']
        else:
            cells.append('—')
    print(f'| {rid} | ' + ' | '.join(cells) + ' |')
print('| **total** | ' + ' | '.join(f'{t[0]} / {k(t[1])} / {t[2]}' for t in tot.values()) + ' |')
print('\n`*` = at least one compound call.\n')

print('### Event-processing failures during authoring (un-injected service)\n')
print('| run | failure | un-injected service | diagnosis window (calls) | requests | tool kB | server.log reads / kB | over-100k in window | window cost $ | phase |')
print('|---|---|---|---|---|---|---|---|---|---|')
for rid, d in S.items():
    if not d['eventProcessingFailure']:
        continue
    w = d['window']
    print(f"| {rid} | yes | {d['uninjectedService']} | {w['firstCall']}–{w['lastCall']} ({w['toolCalls']}) | {w['requests']} | {k(w['toolResultBytes'])} | "
          f"{w['serverLogReads']} / {k(w['serverLogBytes'])} | {w['over100kRequests']} | {w['costUsd']} | {', '.join(w['phases'])} |")
n = sum(1 for d in S.values() if d['eventProcessingFailure'])
print(f'\n{n} of {len(S)} runs hit an event-processing failure; the other {len(S) - n} show none.')
