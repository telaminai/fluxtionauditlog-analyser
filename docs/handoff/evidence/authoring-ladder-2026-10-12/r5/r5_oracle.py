"""Rung-5 oracle (integration only). Usage: r5_oracle.py <subject project root>   prints JSON; exit 0 on PASS.

I1  some node logs, on the five FxQuote records in feed order, the rates EURUSD 1.085, GBPUSD 1.271, EURUSD 1.09,
    USDJPY 149.2, GBPUSD 1.265: the supplier feed, mapper and node were integrated and ran.
I2  the price path is business-unchanged: for each of the five PriceEvent records, every node's business keys (trace
    keys excluded) equal those of the published bundle's own run (BASELINE).
I3  no hand-written Java: src/main/java differs from the fixture only under generated/, and the supplier repository
    is byte-identical to the one supplied.
"""
import hashlib, json, re, sys
from pathlib import Path

FIXTURE = Path('/private/tmp/fx-ladder/r5/fixture')
BASELINE = Path('/private/tmp/fx-g14-dry/project/logs/audit-audit-analyser-bundle.yaml')  # the published bundle, run as shipped
RATES = [('EURUSD', 1.085), ('GBPUSD', 1.271), ('EURUSD', 1.09), ('USDJPY', 149.2), ('GBPUSD', 1.265)]
TRACE = {'thread', 'method', 'annotation', 'forkedExecution', 'asyncMethod', 'triggerFired'}


def records(text):
    if text.lstrip().startswith('eventLogRecords:'):
        text = re.sub(r'\n  - eventTime:', '\n---\n  eventTime:', text)
    return [r for r in text.split('\n---') if 'nodeLogs' in r]


def entries(rec):
    out = {}
    for node, body in re.findall(r'-\s*([A-Za-z_]\w*):\s*\{([^}]*)\}', rec):
        out.setdefault(node, {}).update((k, v.strip()) for k, v in re.findall(r'(\w+):\s*([^,]+)', body))
    return out


def price_business(text):
    out = {}
    for rec in records(text):
        m = re.search(r'PriceEvent\{symbol=([A-Z]+)', rec)
        if m:
            out[m.group(1)] = {n: {k: v for k, v in kv.items() if k not in TRACE} for n, kv in entries(rec).items()}
            out[m.group(1)] = {n: kv for n, kv in out[m.group(1)].items() if kv}
    return out


def tree(root):
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob('*')) if p.is_file()}


def main(root):
    root = Path(root)
    logs = sorted(root.rglob('logs/*.yaml'))
    res = {'logs': [str(l.relative_to(root)) for l in logs]}
    i1 = False
    for log in logs:
        recs = [r for r in records(log.read_text(errors='replace')) if 'FxQuote{' in r]
        seen = []
        for r in recs:
            pair = re.search(r'FxQuote\{pair=([A-Z]+)', r)
            nums = [float(v) for kv in entries(r).values() for v in kv.values() if re.fullmatch(r'-?\d+(\.\d+)?', v)]
            if pair:
                seen.append((pair.group(1), next((n for n in nums if any(abs(n - x) < 1e-9 for _, x in RATES)), None)))
        if seen[:5] == RATES and len(seen) >= 5:
            i1 = True
            res['I1seen'] = seen
            break
    res['I1'] = i1
    base = price_business(BASELINE.read_text())
    got = {}
    for log in logs:
        got = price_business(log.read_text(errors='replace'))
        if len(got) == 5:
            break
    diffs = {s: {'baseline': base.get(s), 'subject': got.get(s)} for s in base if base.get(s) != got.get(s)}
    res['I2'] = len(got) == 5 and not diffs
    if diffs:
        res['I2diffs'] = {s: d for s, d in list(diffs.items())[:2]}
    fj = {k: v for k, v in tree(FIXTURE / 'src/main/java').items() if '/generated/' not in '/' + k}
    sj = {k: v for k, v in tree(root / 'src/main/java').items() if '/generated/' not in '/' + k}
    supplier_same = tree(FIXTURE / 'supplier-repo') == tree(root / 'supplier-repo') if (root / 'supplier-repo').exists() else False
    res['I3'] = fj == sj and supplier_same
    if fj != sj:
        res['I3javaChanged'] = sorted(set(fj.items()) ^ set(sj.items()))[:5]
    res['ok'] = res['I1'] and res['I2'] and res['I3']
    print(json.dumps(res, default=str))
    return 0 if res['ok'] else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
