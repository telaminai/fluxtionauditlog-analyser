"""Mechanical scorer for one G14 grid run. Reads only the run's files and the live analyser instance.

Usage: score_g14.py <base> <run-id>     prints one JSON object.

S1 acquisition: an acquired ZIP whose sha256 equals the pristine download.
S2 design:      the Spring XML declares exactly one new node bean, constructed from riskCheck, listed in nodeBeans.
S3 compiled:    the generated GraphML has that node with an edge from riskCheck, and the generated processor
                dispatches it (auditInvocation of the new node).
S4 behaviour:   an exported audit log has the new node logging, for all five symbols, a value within 0.01 of
                price*volume, and a boolean that is true for NVDA only.
S5 canvas:      the analyser holds a saved graph whose series reads the new node, with finite points, and a
                read of that series carries no identityNote (no file-identity mark).
S6 key hygiene: keyscan.json clean.
PASS = S1..S6. The subject's final answer is scored separately (ANSWER) and never decides a check above.
"""
import hashlib, json, re, sys, urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

PRISTINE_SHA = '9004bbeae6218dcf3b6e857fb45f26b895d84448e56557171dd62ded00c79d23'
TRUTH = {'AAPL': 234360.0, 'MSFT': 328080.0, 'GOOG': 233625.0, 'AMZN': 114060.8, 'NVDA': 1901760.0}
ORIGINAL_BEANS = {'rootNode', 'riskCheck', 'csvRejections'}


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def project_root(project):
    hits = sorted(project.rglob('src/main/fluxtion/designer/application-context.xml'))
    return hits[0].parents[4] if hits else None


def s2_design(root):
    xml = root / 'src/main/fluxtion/designer/application-context.xml'
    beans = ET.parse(xml).getroot()
    ns = '{http://www.springframework.org/schema/beans}'
    top = {b.get('id'): b for b in beans.findall(ns + 'bean') if b.get('id')}
    listed = set()
    for b in beans.iter(ns + 'bean'):
        if (b.get('class') or '').endswith('FluxtionSpringConfig'):
            for prop in b.findall(ns + 'property'):
                if prop.get('name') == 'nodeBeans':
                    listed = {v.text.strip() for v in prop.iter(ns + 'value')}
    new = sorted(listed - ORIGINAL_BEANS)
    ok_one = len(new) == 1
    node = new[0] if ok_one else None
    from_risk = False
    if node and node in top:
        refs = [c.get('ref') for c in top[node] if c.tag in (ns + 'constructor-arg', ns + 'property')]
        from_risk = 'riskCheck' in refs
    return {'newNodes': new, 'node': node, 'constructedFromRiskCheck': from_risk,
            'ok': bool(ok_one and from_risk and ORIGINAL_BEANS <= listed)}, node


def s3_compiled(root, node):
    out = {'graphmlEdgeFromRiskCheck': False, 'processorDispatches': False, 'ok': False}
    if not node:
        return out
    for gm in root.rglob('src/main/resources/**/generated/*.graphml'):
        t = gm.read_text(errors='replace')
        ids = dict(re.findall(r'<node id="([^"]+)"[^>]*>.*?<data key="[^"]*">([^<]*)</data>', t, re.S))
        edges = re.findall(r'<edge[^>]*source="([^"]+)"[^>]*target="([^"]+)"', t)
        name = lambda i: ids.get(i, i)
        if any(name(s) == 'riskCheck' and name(d) == node for s, d in edges) or \
                any('riskCheck' in s and node in d for s, d in edges):
            out['graphmlEdgeFromRiskCheck'] = True
    for jp in root.rglob('src/main/java/**/generated/*.java'):
        if re.search(r'auditInvocation\(\s*%s\b' % re.escape(node), jp.read_text(errors='replace')):
            out['processorDispatches'] = True
    out['ok'] = out['graphmlEdgeFromRiskCheck'] and out['processorDispatches']
    return out


def s4_behaviour(root, node):
    out = {'logs': [], 'symbolsMatched': [], 'flagTrueFor': None, 'ok': False}
    if not node:
        return out
    for log in sorted(root.rglob('logs/*.yaml')):
        entries = re.findall(r'-\s*%s:\s*\{([^}]*)\}' % re.escape(node), log.read_text(errors='replace'))
        if not entries:
            continue
        out['logs'].append(str(log.relative_to(root)))
        text = log.read_text(errors='replace')
        records = text.split('\n---')
        matched, flags = set(), {}
        for rec in records:
            sym = re.search(r'symbol=([A-Z]+)', rec)
            mine = re.findall(r'-\s*%s:\s*\{([^}]*)\}' % re.escape(node), rec)
            if not sym or not mine:
                continue
            s = sym.group(1)
            body = ' '.join(mine)
            nums = [float(x) for x in re.findall(r':\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*(?:,|$)', body)]
            if s in TRUTH and any(abs(n - TRUTH[s]) < 0.01 for n in nums):
                matched.add(s)
            bools = re.findall(r'(\w+):\s*(true|false)\b', body)
            for k, v in bools:
                if k not in ('triggerFired', 'forkedExecution'):
                    flags.setdefault(k, {})[s] = v == 'true'
        good_flag = [k for k, m in flags.items() if set(m) >= set(TRUTH) and
                     all(m[s] == (s == 'NVDA') for s in TRUTH)]
        out['symbolsMatched'] = sorted(matched)
        out['flagKeysCorrect'] = good_flag
        out['ok'] = matched == set(TRUTH) and bool(good_flag)
        if out['ok']:
            break
    return out


def act(ep, action, params):
    req = urllib.request.Request(ep['url'] + '/action', data=json.dumps({'v': 1, 'action': action,
                                 'params': params}).encode(), headers={'Content-Type': 'application/json',
                                 'X-Analyser-Token': ep['token']})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b'{}')


def s5_canvas(base, node):
    out = {'graphs': [], 'ok': False}
    epf = base / 'tmp/home/.fluxtion-analyser/rest-endpoint'
    if not node or not epf.exists():
        out['reason'] = 'no node' if not node else 'no live analyser endpoint'
        return out
    ep = json.loads(epf.read_text())
    ctx = act(ep, 'context', {}).get('context', {})
    scopes = {g['name']: g for g in ctx.get('graphScopes', [])}
    for g in ctx.get('savedGraphs', []):
        exprs = list(g.get('series', [])) + list(g.get('expressions', []))
        reads = [e for e in exprs if isinstance(e, str) and node in e]
        if not reads:
            continue
        pts = scopes.get(g['name'], {}).get('finiteSeriesPoints', 0)
        marked = 'identityNote' in json.dumps(act(ep, 'series', {'expr': reads[0]}))
        out['graphs'].append({'name': g['name'], 'series': reads, 'points': pts, 'identityMark': marked})
    out['ok'] = any(x['points'] > 0 and not x['identityMark'] for x in out['graphs'])
    return out


def usage(archive):
    reqs, result = {}, {}
    for line in (archive / 'raw.jsonl').read_text(errors='replace').splitlines():
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('type') == 'assistant':
            u = d['message'].get('usage', {})
            reqs[d['message']['id']] = u.get('input_tokens', 0) + u.get('cache_creation_input_tokens', 0) + \
                u.get('cache_read_input_tokens', 0)
        if d.get('type') == 'result':
            result = d
    return {'requests': len(reqs), 'maxPrompt': max(reqs.values() or [0]),
            'over100k': sum(v > 100_000 for v in reqs.values()), 'costUsd': result.get('total_cost_usd'),
            'turns': result.get('num_turns'), 'isError': result.get('is_error'),
            'answer': (result.get('result') or '')[-1500:]}


def main(base, rid):
    base = Path(base)
    archive = base / rid
    meta = json.loads((archive / 'meta.json').read_text()) if (archive / 'meta.json').exists() else {}
    project = base / 'project'
    root = project_root(project)
    acquired = meta.get('acquired', [])
    s1 = {'acquired': acquired, 'ok': any(a['sha256'] == PRISTINE_SHA for a in acquired)}
    if root:
        s2, node = s2_design(root)
        s3 = s3_compiled(root, node)
        s4 = s4_behaviour(root, node)
    else:
        node, s2, s3, s4 = None, {'ok': False, 'reason': 'no project found'}, {'ok': False}, {'ok': False}
    s5 = s5_canvas(base, node)
    scan = json.loads((archive / 'keyscan.json').read_text()) if (archive / 'keyscan.json').exists() else {}
    s6 = {'clean': scan.get('clean'), 'ok': scan.get('clean') is True}
    u = usage(archive) if (archive / 'raw.jsonl').exists() else {}
    ans = u.get('answer', '')
    answer = {'mentionsNVDA': 'NVDA' in ans,
              'otherSymbolsMentioned': [s for s in TRUTH if s != 'NVDA' and s in ans]}
    checks = {'S1': s1, 'S2': s2, 'S3': s3, 'S4': s4, 'S5': s5, 'S6': s6}
    print(json.dumps({'run': rid, 'model': meta.get('requestedModel'), 'PASS': all(c['ok'] for c in checks.values()),
                      'checks': {k: v['ok'] for k, v in checks.items()}, 'detail': checks,
                      'wallSeconds': meta.get('clock', {}).get('elapsedSeconds'), 'capped': meta.get('capped', False),
                      'usage': {k: v for k, v in u.items() if k != 'answer'}, 'answer': answer}, default=str))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
