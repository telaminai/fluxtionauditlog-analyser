"""Collect each A/B run's files into the evidence folder (home dir redacted to ~): score, levers, warm-cache,
precondition, meta, keyscan, config, reads, the token_split tables (buckets/categories/phases), content reads and the
early/delegation report (early_t.py). No transcript. Usage: collect_ab.py <evidence dir>"""
import glob, json, os, subprocess, sys
from pathlib import Path

E = Path(sys.argv[1]); R = E / 'results'; R.mkdir(exist_ok=True); HOME = str(Path.home())
L = Path(__file__).resolve().parent
for base in sorted(glob.glob('/private/tmp/fx-ab-r*')):
    rid = os.path.basename(base)[3:]; arch = rid.split('-VOID')[0]
    out = R / rid; out.mkdir(exist_ok=True)
    srcs = {'score.json': f'{base}/score.json', 'levers.json': f'{base}/levers.json', 'warm-cache.json': f'{base}/warm-cache.json',
            'precondition.json': f'{base}/precondition.json', 'content-reads.json': f'{base}/content-reads.json'}
    for n in ('meta.json', 'keyscan.json', 'config.json'):
        srcs[n] = f'{base}/{arch}/{n}'
    for n in ('buckets.json', 'categories.json', 'phases.json'):
        srcs[n] = f'{base}/split/{n}'
    for n, s in srcs.items():
        if os.path.exists(s):
            (out / n).write_text(Path(s).read_text().replace(HOME, '~'))
    try:
        (out / 'reads.json').write_text(json.dumps(json.loads((out / 'score.json').read_text()).get('reads', {})) + '\n')
    except (OSError, ValueError):
        pass
    if (out / 'phases.json').exists() and (Path(base) / arch / 'raw.jsonl').exists() and 'VOID' not in rid:
        r = subprocess.run([sys.executable, '-B', str(L / 'early_t.py'), rid], capture_output=True, text=True)
        if r.returncode == 0:
            (out / 'delegation.json').write_text(r.stdout.replace(HOME, '~'))
print('collected', len(list(R.iterdir())))
