"""One-cell early report for an A/B cell (counts and flags only; no transcript text). Usage: early_t.py <run-id>

Delegation facts come from the transcript's Agent/Task calls and from token_split's phases.json `delegation` block:
  delegated            any Agent/Task call
  calls                each: subagent_type, run_in_background as requested, brief chars, whether the brief names the
                       spec's fields (project/logs/provenance/change/predicted/question)
  foreground           no delegation was backgrounded (token_split `backgrounded` = 0)
  waited               the main context made no request while a subagent ran (`mainRequestsWhileSubagentRan` = 0)
                       AND made no analyser call between the delegation and its reply
  replyQualifications  which of the spec's qualification keys the reply text names: identity, pairing, timeOrder,
                       producer, inFlight, dispatchOrder (or 'not stated')
"""
import json, re, sys
from pathlib import Path

rid = sys.argv[1]; base = Path(f'/private/tmp/fx-{rid}')
s = json.loads((base / 'score.json').read_text()); m = json.loads((base / rid / 'meta.json').read_text())
k = json.loads((base / rid / 'keyscan.json').read_text())
ph = json.loads((base / 'split/phases.json').read_text()); bk = json.loads((base / 'split/buckets.json').read_text())
QUAL = {'identity': r'identity', 'pairing': r'pairing|graphPairing', 'timeOrder': r'time ?order', 'producer': r'producer',
        'inFlight': r'in[- ]?flight', 'dispatchOrder': r'dispatch ?order', 'notStated': r'not stated'}
FIELDS = ('project:', 'logs:', 'provenance:', 'change:', 'predicted:', 'question:')
events = [json.loads(l) for l in open(base / rid / 'raw.jsonl', errors='replace') if l.strip().startswith('{')]
agent, order = [], []
for d in events:
    if d.get('type') == 'assistant' and not d.get('parent_tool_use_id'):
        for c in d['message'].get('content', []):
            if c.get('type') == 'tool_use':
                order.append((c['id'], c['name']))
                if c['name'] in ('Agent', 'Task'):
                    i = c.get('input') or {}
                    p = str(i.get('prompt') or '')
                    agent.append({'id': c['id'], 'subagent_type': i.get('subagent_type'), 'run_in_background': i.get('run_in_background'),
                                  'briefChars': len(p), 'briefFields': [f for f in FIELDS if f in p]})
replies = {}
for d in events:
    if d.get('type') == 'user' and isinstance(d['message'].get('content'), list) and not d.get('parent_tool_use_id'):
        for c in d['message']['content']:
            if isinstance(c, dict) and c.get('type') == 'tool_result' and c['tool_use_id'] in {a['id'] for a in agent}:
                t = c.get('content'); replies[c['tool_use_id']] = t if isinstance(t, str) else json.dumps(t)
    if d.get('type') == 'system' and d.get('subtype') == 'task_notification' and d.get('tool_use_id') in {a['id'] for a in agent}:
        replies[d['tool_use_id']] = replies.get(d['tool_use_id'], '') + str(d.get('summary') or '')
analyser_between = 0
ids = [x[0] for x in order]
for a in agent:
    i = ids.index(a['id'])
    for cid, name in order[i + 1:]:
        if cid in replies or name in ('Agent', 'Task'):
            break
        if name.startswith('mcp__fluxtion-analyser'):
            analyser_between += 1
    t = replies.get(a['id'], '')
    a['replyChars'] = len(t)
    a['replyQualifications'] = [q for q, rx in QUAL.items() if re.search(rx, t, re.I)]
    a['replyCitesRecordIndex'] = bool(re.search(r'recordIndex|record(s)? ?#?\d', t, re.I))
    del a['id']
dg = ph.get('delegation') or {}
u = s['usage']
print(json.dumps({'run': rid, 'PASS': s['PASS'], 'ORACLE': s['ORACLE']['ok'], 'VALID': s['VALID']['ok'], 'CHART': s['CHART']['ok'],
                  'S6prime': s['S6prime']['ok'], 'costUsd': round(u['costUsd'], 4), 'turns': u['turns'], 'wall': round(s['wallSeconds']),
                  'over100k': u['over100k'], 'diagnosisCostUsd': ph['phases']['diagnosis']['costUsd'],
                  'diagnosisOver100k': ph['phases']['diagnosis']['over100k'], 'subagentOffered': m.get('subagentOffered'),
                  'delegated': bool(agent), 'calls': agent,
                  'foreground': dg.get('backgrounded', 0) == 0 if agent else None,
                  'waited': (dg.get('mainRequestsWhileSubagentRan', 0) == 0 and analyser_between == 0) if agent else None,
                  'analyserCallsBetweenDelegationAndReply': analyser_between if agent else None,
                  'delegationBlock': {x: dg.get(x) for x in ('delegations', 'backgrounded', 'mainRequestsWhileSubagentRan', 'subagentRequests',
                                                             'subagentOver100k', 'subagentCostUsd', 'delegateInputChars', 'delegateResultBytes', 'warnings')},
                  'subagentFirstPrompts': [c.get('firstPrompt') for c in bk.get('contexts', []) if c.get('context') != 'main'],
                  'keyscan': {x: k.get(x) for x in ('clean', 'secretOccurrences', 'tokenOccurrences', 'keyPathReads')}}, indent=1))
