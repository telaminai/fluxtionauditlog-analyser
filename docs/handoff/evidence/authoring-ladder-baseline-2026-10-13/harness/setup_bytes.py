"""Post-seal (2026-10-13, review of PR #131 finding 5): for each first-ladder Haiku run, the number of tool calls whose
command names setup.sh and the bytes of their tool results. A coarse measure: a call that also reads docs counts whole.
Usage: setup_bytes.py   (reads the preserved first-ladder transcripts under /private/tmp/fx-lad-r?-h55-?)  prints JSON lines"""
import json, glob, os
for d in sorted(glob.glob('/private/tmp/fx-lad-r?-h55-?')):
    rid = os.path.basename(d)[3:]; names = {}; b = n = 0
    for l in open(f'{d}/{rid}/raw.jsonl'):
        x = json.loads(l)
        if x.get('type') == 'assistant':
            for c in x['message'].get('content', []):
                if c.get('type') == 'tool_use':
                    names[c['id']] = str(c['input'].get('command', ''))
        elif x.get('type') == 'user' and isinstance(x['message'].get('content'), list):
            for c in x['message']['content']:
                if isinstance(c, dict) and c.get('type') == 'tool_result' and 'setup.sh' in names.get(c['tool_use_id'], ''):
                    t = c['content'] if isinstance(c['content'], str) else json.dumps(c['content']); b += len(t); n += 1
    print(json.dumps({'run': rid, 'cache': 'warm' if os.path.exists(d + '/warm-cache.json') else 'cold',
                      'setupCalls': n, 'setupResultBytes': b}))
