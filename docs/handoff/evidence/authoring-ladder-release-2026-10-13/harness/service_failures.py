"""Event-processing failures during authoring, and what diagnosing them cost (post-hoc reporting, 2026-10-13).
Usage: service_failures.py <evidence results dir> <run base glob>   prints JSON lines.

A run has an EVENT-PROCESSING FAILURE when some tool result carries a node exception the run summary did not report:
`event processing failed`, or a NullPointerException / IllegalStateException naming a node field or a service. It is
an UN-INJECTED SERVICE failure when that text names a service that was never delivered (`... is null` on a field
whose name contains the service, `service is not registered`, `not registered`).

The DIAGNOSIS WINDOW runs from the call after the run-or-export call that produced the failing run (the last such call
before the first result showing the failure, whose own summary did not report it) to the last call before the next
run-or-export call whose result shows no failure. Over that window: tool calls, requests (from token_split's
categories.json), tool-result bytes, the server.log reads among them (a read verb taking logs/server.log; a cp of it is not a read) and their bytes,
and the over-100k requests in the window. Only marker text is matched; no result content is reported.
"""
import glob, json, os, re, sys
from pathlib import Path

FAIL = re.compile(r'event processing failed|NullPointerException|IllegalStateException[^\n]{0,120}(service|registered)', re.I)
UNINJ = re.compile(r'(\w*[Ll]imits?\w*|\w*[Ss]ervice\w*)["\']?\s+is null|service is not registered|is not registered|not been registered', re.I)
SLREAD = re.compile(r'\b(cat|head|tail|sed|less|more|grep|awk|wc)\b[^|;&]*logs/server\.log')  # a read, not a cp
RUN = re.compile(r'((?<![\w-])run\.sh|run-server\.sh|export-audit\.sh|run-and-export\.sh)')


def main(results, base_glob):
    for base in sorted(glob.glob(base_glob)):
        rid = os.path.basename(base)[3:]
        raw = Path(base) / rid / 'raw.jsonl'
        cat = json.loads((Path(results) / rid / 'categories.json').read_text())['calls']
        bk = {r['n']: r for r in json.loads((Path(results) / rid / 'buckets.json').read_text())['requests']}
        calls, res = [], {}
        for line in open(raw, errors='replace'):
            d = json.loads(line)
            if d.get('type') == 'assistant':
                for c in d['message'].get('content', []):
                    if c.get('type') == 'tool_use':
                        calls.append((c['id'], c.get('name'), str((c.get('input') or {}).get('command') or (c.get('input') or {}).get('file_path') or '')))
            elif d.get('type') == 'user' and isinstance(d['message'].get('content'), list):
                for c in d['message']['content']:
                    if isinstance(c, dict) and c.get('type') == 'tool_result':
                        t = c.get('content'); res[c['tool_use_id']] = t if isinstance(t, str) else json.dumps(t)
        flags = [bool(FAIL.search(res.get(cid, ''))) for cid, _, _ in calls]
        uninj = any(UNINJ.search(res.get(cid, '')) for cid, _, _ in calls)
        out = {'run': rid, 'eventProcessingFailure': any(flags), 'uninjectedService': uninj}
        if any(flags) and len(cat) == len(calls):
            first = flags.index(True)
            prior = [i for i in range(first) if RUN.search(calls[i][2])]
            start = (prior[-1] + 1) if prior else first
            end = len(calls) - 1
            for i in range(first + 1, len(calls)):
                if RUN.search(calls[i][2]) and not flags[i]:
                    end = i - 1
                    break
            win = range(start, end + 1)
            reqs = sorted({cat[i]['request'] for i in win if cat[i]['request'] is not None})
            sl = [i for i in win if SLREAD.search(calls[i][2])]
            out.update({'window': {'failedRunCall': prior[-1] if prior else None, 'failureFirstSeenCall': first, 'firstCall': start, 'lastCall': end, 'toolCalls': len(win), 'requests': len(reqs),
                                   'toolResultBytes': sum(cat[i]['bytes'] for i in win),
                                   'serverLogReads': len(sl), 'serverLogBytes': sum(cat[i]['bytes'] for i in sl),
                                   'over100kRequests': sum(1 for n in reqs if bk.get(n, {}).get('over100k')),
                                   'costUsd': round(sum(bk.get(n, {}).get('costUsd', 0) for n in reqs), 4),
                                   'phases': sorted({bk.get(n, {}).get('phase') for n in reqs} - {None})}})
        print(json.dumps(out))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
