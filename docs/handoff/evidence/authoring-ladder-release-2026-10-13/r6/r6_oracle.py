"""Rung-6 oracle: a node with two trigger parents, a DATA-only reference and a new sink, XML-first.
Usage: r6_oracle.py <subject project root>   prints JSON; exit 0 on PASS.

X1 (design, XML-first) the design XML (`fluxtion-authoring.json`'s `xml`, default
   src/main/fluxtion/designer/application-context.xml) declares a bean of class com.example.myapp.node.TurnoverTracker
   (id: the author's choice, called T below) whose constructor-arg refs include rootNode, riskCheck and csvRejections;
   T is in fluxtionSpringConfig's nodeBeans; a ReferenceBinding owner=T referenced=csvRejections has mode DATA, and no
   binding owner=T makes rootNode or riskCheck DATA; and a SinkBinding under `sinks` named `turnover` lists T.
   A sink wired only in Java or server-config.yml, with no SinkBinding, fails here.
X2 (dispatch, from the exported audit log logs/*.yaml) for the LAST record of each of the five accepted symbols, T's
   entry is present and comes after both rootNode's and riskCheck's entries in that record; and the fixture's
   rejected row produced at least one CsvRejection record with csvRejections in it and T absent from EVERY
   CsvRejection record (a DATA parent's update does not trigger T).
X3 (output) data/turnover.txt (the server's `turnover` destination, named in the task) ends with exactly the five
   expected records for the fixture input, in order, and its line count is a multiple of five (the sink appends across
   runs, one block per run). A record line must name the symbol and carry the notional (within 0.01) and the
   rejected-row count so far as numbers.
"""
import json, re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

CLS = 'com.example.myapp.node.TurnoverTracker'
EXPECT = [('AAPL', 234360.0, 0), ('MSFT', 328080.0, 0), ('GOOG', 233625.0, 0), ('AMZN', 114060.8, 1), ('NVDA', 1901760.0, 1)]
NS = '{http://www.springframework.org/schema/beans}'
NUM = re.compile(r'(?<![\w.])-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?')


def tag(e):
    return e.tag.replace(NS, '')


def props(bean):
    return {p.get('name'): p for p in bean if tag(p) == 'property'}


def value_of(prop):
    if prop.get('value') is not None:
        return prop.get('value')
    v = next((c for c in prop if tag(c) == 'value'), None)
    return v.text.strip() if v is not None and v.text else None


def list_values(prop):
    lst = next((c for c in prop if tag(c) == 'list'), None)
    return [] if lst is None else [c for c in lst]


def design(root):
    xml_rel = 'src/main/fluxtion/designer/application-context.xml'
    try:
        xml_rel = json.loads((root / 'fluxtion-authoring.json').read_text()).get('xml') or xml_rel
    except (OSError, ValueError):
        pass
    res = {'xml': xml_rel}
    try:
        doc = ET.parse(root / xml_rel).getroot()
    except (OSError, ET.ParseError) as exc:
        res.update({'ok': False, 'reason': f'design XML unreadable: {exc}'}); return res, None
    beans = [b for b in doc.iter() if tag(b) == 'bean']
    t = next((b for b in beans if b.get('class') == CLS and b.get('id')), None)
    if t is None:
        res.update({'ok': False, 'reason': f'no bean of class {CLS}'}); return res, None
    tid = t.get('id'); res['nodeId'] = tid
    refs = {c.get('ref') for c in t if tag(c) == 'constructor-arg' and c.get('ref')}
    res['constructorRefs'] = sorted(refs)
    cfg = next((b for b in beans if (b.get('class') or '').endswith('FluxtionSpringConfig')), None)
    if cfg is None:
        res.update({'ok': False, 'reason': 'no fluxtionSpringConfig'}); return res, tid
    cp = props(cfg)
    node_beans = [c.text.strip() for c in list_values(cp['nodeBeans'])] if 'nodeBeans' in cp else []
    modes = {}
    for b in (list_values(cp['referenceBindings']) if 'referenceBindings' in cp else []):
        bp = props(b)
        if bp.get('ownerBean') is not None and value_of(bp['ownerBean']) == tid and bp.get('referencedBean') is not None:
            modes[value_of(bp['referencedBean'])] = (value_of(bp['mode']) if 'mode' in bp else None)
    res['bindingModes'] = modes
    sinks = []
    for b in (list_values(cp['sinks']) if 'sinks' in cp else []):
        bp = props(b)
        if (b.get('class') or '').endswith('SinkBinding') and 'name' in bp:
            nb = [c.text.strip() for c in list_values(bp['nodeBeans'])] if 'nodeBeans' in bp else []
            sinks.append({'name': value_of(bp['name']), 'nodeBeans': nb})
    res['sinks'] = sinks
    checks = {
        'parents': {'rootNode', 'riskCheck', 'csvRejections'} <= refs,
        'inNodeBeans': tid in node_beans,
        'dataRef': (modes.get('csvRejections') or '').upper() == 'DATA',
        'triggerParentsNotData': all((modes.get(p) or 'TRIGGER').upper() != 'DATA' for p in ('rootNode', 'riskCheck')),
        'sinkBinding': any(s['name'] == 'turnover' and tid in s['nodeBeans'] for s in sinks),
    }
    res['checks'] = checks
    res['ok'] = all(checks.values())
    return res, tid


def records(text):
    if text.lstrip().startswith('eventLogRecords:'):
        text = re.sub(r'\n  - eventTime:', '\n---\n  eventTime:', text)
    return [r for r in text.split('\n---') if 'nodeLogs' in r]


def order(rec):
    return re.findall(r'^\s*-\s*([A-Za-z_]\w*):\s*\{', rec, flags=re.M)


def dispatch(root, tid):
    res = {'logs': []}
    best = None
    for log in sorted(root.rglob('logs/*.yaml')):
        recs = records(log.read_text(errors='replace'))
        last, rejections = {}, []
        for r in recs:
            ev = re.search(r'^\s*event:\s*(\S+)', r, flags=re.M)
            ev = ev.group(1) if ev else ''
            if ev == 'CsvRejection':
                rejections.append(order(r))
            m = re.search(r'PriceEvent\{symbol=([A-Z]+)', r)
            if ev == 'PriceEvent' and m:
                last[m.group(1)] = order(r)
        per = {}
        for sym, _, _ in EXPECT:
            o = last.get(sym)
            per[sym] = bool(o) and tid in o and 'rootNode' in o and 'riskCheck' in o \
                and o.index(tid) > o.index('rootNode') and o.index(tid) > o.index('riskCheck')
        rej_ok = bool(rejections) and all('csvRejections' in o and tid not in o for o in rejections)
        entry = {'log': str(log.relative_to(root)), 'afterBothParents': per, 'csvRejectionRecords': len(rejections),
                 'notTriggeredByData': rej_ok}
        entry['ok'] = all(per.values()) and rej_ok
        res['logs'].append(entry)
        if entry['ok'] and best is None:
            best = entry
    res['ok'] = best is not None
    return res


def output(root):
    f = root / 'data/turnover.txt'
    res = {'file': 'data/turnover.txt'}
    if not f.exists():
        res.update({'ok': False, 'reason': 'no data/turnover.txt'}); return res
    lines = [l for l in f.read_text(errors='replace').splitlines() if l.strip()]
    res['lines'] = len(lines)
    tail = lines[-5:]
    per = []
    for (sym, notional, rejected), line in zip(EXPECT, tail):
        nums = [float(n) for n in NUM.findall(line)]
        per.append(bool(re.search(r'(?<![A-Z])' + sym + r'(?![A-Z])', line))
                   and any(abs(n - notional) < 0.01 for n in nums) and any(n == rejected for n in nums))
    res['lastBlock'] = per
    res['ok'] = len(lines) >= 5 and len(lines) % 5 == 0 and len(per) == 5 and all(per)
    if not res['ok']:
        res['tail'] = tail
    return res


def main(arg):
    root = Path(arg)
    x1, tid = design(root)
    out = {'X1': x1}
    out['X2'] = dispatch(root, tid) if tid else {'ok': False, 'reason': 'no node id'}
    out['X3'] = output(root)
    out['ok'] = x1['ok'] and out['X2']['ok'] and out['X3']['ok']
    print(json.dumps(out, default=str))
    return 0 if out['ok'] else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
