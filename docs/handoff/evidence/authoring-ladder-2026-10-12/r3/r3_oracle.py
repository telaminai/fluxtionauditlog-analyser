"""Rung-3 oracle over a subject's exported audit log. Node and key names are the author's choice.

PASS when:
  F1  some node logs, on each of the five price records, a value within 0.01 of price*volume, and a boolean that is
      true exactly for MSFT and NVDA (limit 300,000 from the TradeLimits service; AAPL 234,360, GOOG 233,625 and
      AMZN 114,060.8 are below it). A missing service gives no flags or NVDA alone, so this also proves the binding.
  F2  some OTHER node appears ONLY in the MSFT and NVDA records, carrying an integer 1 then 2: it ran only for large
      trades (conditional propagation) and counted them.
Usage: r3_oracle.py <project root or log file>   prints JSON; exit 0 on PASS.
"""
import json, re, sys
from pathlib import Path

TRUTH = {'AAPL': 234360.0, 'MSFT': 328080.0, 'GOOG': 233625.0, 'AMZN': 114060.8, 'NVDA': 1901760.0}
LARGE = {'MSFT', 'NVDA'}


def records(text):
    if text.lstrip().startswith('eventLogRecords:'):
        text = re.sub(r'\n  - eventTime:', '\n---\n  eventTime:', text)
    return [r for r in text.split('\n---') if 'nodeLogs' in r]


def entries(rec):
    out = {}
    for node, body in re.findall(r'-\s*([A-Za-z_]\w*):\s*\{([^}]*)\}', rec):
        kv = dict((k, v.strip()) for k, v in re.findall(r'(\w+):\s*([^,]+)', body))
        out.setdefault(node, {}).update(kv)
    return out


def check(log):
    by_symbol = {}
    for rec in records(log.read_text(errors='replace')):
        m = re.search(r'symbol=([A-Z]+)', rec)
        if m and m.group(1) in TRUTH:
            by_symbol[m.group(1)] = entries(rec)
    result = {'log': str(log), 'symbols': sorted(by_symbol)}
    if set(by_symbol) != set(TRUTH):
        result['ok'] = False; result['reason'] = 'not all five price records present'
        return result
    flag_node = None
    for node in {n for e in by_symbol.values() for n in e}:
        nums_ok = all(any(re.fullmatch(r'-?\d+(\.\d+)?', v) and abs(float(v) - TRUTH[s]) < 0.01
                          for v in by_symbol[s].get(node, {}).values()) for s in TRUTH)
        if not nums_ok:
            continue
        keys = {k for s in TRUTH for k, v in by_symbol[s].get(node, {}).items() if v in ('true', 'false')}
        for k in keys - {'triggerFired', 'forkedExecution'}:
            if all(by_symbol[s].get(node, {}).get(k) == ('true' if s in LARGE else 'false') for s in TRUTH):
                flag_node = (node, k)
    result['F1'] = {'flagNode': flag_node, 'ok': flag_node is not None}
    counter = None
    for node in {n for e in by_symbol.values() for n in e} - ({flag_node[0]} if flag_node else set()):
        where = {s for s in TRUTH if node in by_symbol[s]}
        if where != LARGE:
            continue
        ints = {s: [int(v) for v in by_symbol[s][node].values() if re.fullmatch(r'\d+', v)] for s in LARGE}
        if 1 in ints['MSFT'] and 2 in ints['NVDA']:
            counter = node
    result['F2'] = {'counterNode': counter, 'ok': counter is not None}
    result['ok'] = result['F1']['ok'] and result['F2']['ok']
    return result


def main(arg):
    p = Path(arg)
    logs = [p] if p.is_file() else sorted(p.rglob('logs/*.yaml'))
    results = [check(l) for l in logs]
    best = next((r for r in results if r.get('ok')), results[0] if results else {'ok': False, 'reason': 'no exported log'})
    print(json.dumps(best))
    return 0 if best.get('ok') else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
