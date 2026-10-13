"""S1' (disclosed after sealing, applied to every run alike): acquisition by CONTENT, not digest.

Why: the published scaffold is generated per request, so two fetches seconds apart differ in sha256 while
unpacking to identical files (measured 2026-10-12). The sealed S1 (digest equality) therefore cannot hold for
any run. S1' passes when some ZIP the run fetched, anywhere under its base except the Maven repository,
unpacks to exactly the pristine copy's files, byte for byte.

Usage: score_s1_content.py <base>   prints JSON.
"""
import hashlib, io, json, sys, zipfile
from pathlib import Path

PRISTINE = Path('/private/tmp/fx-g14-grid/pristine/bundle.zip')


def tree(zpath):
    with zipfile.ZipFile(zpath) as z:
        return {i.filename: hashlib.sha256(z.read(i)).hexdigest() for i in z.infolist() if not i.is_dir()}


def main(base):
    base = Path(base)
    want = tree(PRISTINE)
    found = []
    for z in sorted(base.rglob('*')):
        if '/tmp/m2/' in str(z) or not z.is_file() or z.stat().st_size > 50_000_000:
            continue
        try:
            if not zipfile.is_zipfile(z):
                continue
            got = tree(z)
        except Exception as exc:  # noqa: BLE001
            found.append({'path': str(z.relative_to(base)), 'error': str(exc)})
            continue
        found.append({'path': str(z.relative_to(base)), 'files': len(got), 'identical': got == want,
                      'missing': sorted(set(want) - set(got))[:5], 'extra': sorted(set(got) - set(want))[:5],
                      'differing': sorted(k for k in set(want) & set(got) if want[k] != got[k])[:5]})
    print(json.dumps({'pristineFiles': len(want), 'zips': found,
                      'ok': any(f.get('identical') for f in found)}))


if __name__ == '__main__':
    main(sys.argv[1])
