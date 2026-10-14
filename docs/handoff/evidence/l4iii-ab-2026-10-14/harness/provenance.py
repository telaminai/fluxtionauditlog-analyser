"""Provenance check of a Maven local repository. Usage: provenance.py <m2>  prints JSON; exit 0 when clean.
Clean = every artefact file (jar/pom) is listed in its directory's _remote.repositories with a named remote, the
remotes are only central and repsy-fluxtion-public, no SNAPSHOT, and no file lacks provenance."""
import json, sys, re
from pathlib import Path
from collections import Counter
m2 = Path(sys.argv[1]); counts = Counter(); bad = []
ALLOWED = {'central', 'repsy-fluxtion-public'}
for d in sorted({p.parent for p in m2.rglob('*') if p.is_file() and p.suffix in ('.jar', '.pom')}):
    rr = d / '_remote.repositories'
    prov = {}
    if rr.exists():
        for line in rr.read_text().splitlines():
            m = re.match(r'^([^#>][^>]*)>(.*)=$', line.strip())
            if m: prov.setdefault(m.group(1), set()).add(m.group(2))
    for f in d.iterdir():
        if f.suffix not in ('.jar', '.pom'):
            continue
        if 'SNAPSHOT' in str(f):
            bad.append(['snapshot', str(f.relative_to(m2))]); continue
        r = prov.get(f.name)
        if not r:
            bad.append(['no-provenance', str(f.relative_to(m2))]); continue
        if '' in r:
            bad.append(['locally-installed', str(f.relative_to(m2))]); continue
        if not r <= ALLOWED:
            bad.append(['other-remote:' + ','.join(sorted(r)), str(f.relative_to(m2))]); continue
        counts[sorted(r)[0]] += 1
print(json.dumps({'m2': str(m2), 'artefactFilesByRemote': dict(counts), 'problems': bad[:20], 'problemCount': len(bad),
                  'clean': not bad}, indent=1))
sys.exit(0 if not bad else 1)
