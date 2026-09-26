"""Round 8 targeted witnesses (owner-approved: R8-1 and R8-2 only), plus the eighth re-reviewer's probe mutations P1
and P2 re-applied to the fixed code, which must now turn the matrix red through its OFFENDERS assertion (R8-5).
Strict protocol: focused green baseline; surefire reports DELETED before the mutated run; a <failure> (not <error>,
not a compile failure) at the named test whose message carries the label; SHA-256 restore; clean `git status -- src`;
green re-run. For every run the matrix's own firing assertion is recorded."""
import subprocess, glob, os, sys, hashlib, re, xml.etree.ElementTree as ET
ROOT = os.getcwd(); REPORTS = os.path.join(ROOT, 'target', 'surefire-reports')
F = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java'
T = 'CoveragePerNodeLevelTest,ControlAddressAndScopeTest,PerNodeLevelChangesTest'
MATRIX = 'ControlAddressAndScopeTest#noBranchOfTheSentencePresumesAProcessor'
OFFENDERS = 'R-C/R5/R6/R7: a branch presumes'
REACH = 'a branch of the sentence the matrix no longer reaches'
def clear():
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')) + glob.glob(os.path.join(REPORTS, '*.txt')):
        if os.path.dirname(os.path.abspath(p)) == REPORTS: os.remove(p)
def run():
    clear()
    r = subprocess.run(['mvn', '-q', '-o', '-Dtest=' + T, '-Dsurefire.failIfNoSpecifiedTests=false', 'test'], cwd=ROOT, capture_output=True, text=True)
    res = {}
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')):
        for tc in ET.parse(p).getroot().iter('testcase'):
            k = tc.get('classname').split('.')[-1] + '#' + re.sub(r'\(.*\)$', '', tc.get('name'))
            f, e = tc.find('failure'), tc.find('error')
            res[k] = ('failure', f.get('message') or '') if f is not None else ('error', e.get('message') or '') if e is not None else ('pass', '')
    return ('COMPILATION ERROR' not in r.stdout + r.stderr), res
def clean(): return subprocess.run(['git', 'status', '--porcelain', '--', 'src'], cwd=ROOT, capture_output=True, text=True).stdout.strip() == ''
def matrix_fired(res):
    kind, msg = res.get(MATRIX, ('missing', ''))
    if kind == 'pass': return 'green'
    return 'offenders' if msg.startswith(OFFENDERS) else 'reach' if msg.startswith(REACH) else kind + ':' + msg[:40]
W = [
 ("WITNESS R8-1: e5541d5b's unbounded later-run clause restored",
  '''                        .append("whether the level survived into it: for the records in view after that marker")
                        .append(end2 == null ? "" : " and before " + end2).append(", in ").append(scope)
                        .append(", those lines are absent only if ").append(all(later));''',
  '''                        .append("whether the level survived into it: for the records in view from record ")
                        .append(boundary + 1).append(" on, those lines are absent only if ").append(all(later));''',
  'ControlAddressAndScopeTest#theLaterRunIsBoundedAndTheAnnotationStopsAtTheSecondMarker', 'R8-1 M'),
 ("WITNESS R8-2: e5541d5b's reach past the second marker restored",
  '''            int stop = m2 == null ? until : Math.min(until, m2);''',
  '''            int stop = until;''',
  'ControlAddressAndScopeTest#theLaterRunIsBoundedAndTheAnnotationStopsAtTheSecondMarker', 'R8-2 P'),
 ("PROBE P1 (reviewer's): the definite bound ignores the first marker when a closer comes later",
  '''            String end1 = closer != null && (m1 == null || closer < m1) ? "record " + (closer + 1)''',
  '''            String end1 = closer != null ? "record " + (closer + 1)''',
  MATRIX, OFFENDERS),
 ("PROBE P2 (reviewer's): the wholly-after bound loses its end (end2 dropped)",
  '''                        .append(end2 == null ? "" : " and before " + end2).append(in)''',
  '''                        .append(in)''',
  MATRIX, OFFENDERS),
]
if not clean(): sys.exit("git status -- src is not clean before starting")
ok = True
for name, old, new, named, label in W:
    c, base = run(); baseline = c and base and all(v[0] == 'pass' for v in base.values()) and named in base
    p = os.path.join(ROOT, F); orig = open(p, 'rb').read(); h = hashlib.sha256(orig).hexdigest(); s = orig.decode()
    if s.count(old) != 1: print(f"BAD  {name}: anchor count {s.count(old)}"); ok = False; continue
    open(p, 'wb').write(s.replace(old, new).encode())
    try: c2, res = run(); kind, msg = res.get(named, ('missing', ''))
    finally: open(p, 'wb').write(orig)
    restored = hashlib.sha256(open(p, 'rb').read()).hexdigest() == h; cl = clean()
    c3, again = run(); green = c3 and again and all(v[0] == 'pass' for v in again.values())
    own = msg.startswith(label)
    others = sorted(k.split('#')[1] for k, v in res.items() if v[0] != 'pass' and k != named)
    good = baseline and c2 and kind == 'failure' and own and restored and cl and green; ok &= good
    print(f"{'OK  ' if good else 'BAD '} {name}: baseline={'green' if baseline else 'NOT'} compiled={c2} "
          f"{named.split('#')[1]}->{kind} assertion='{msg[:100]}' own-assertion={own} matrix-fired={matrix_fired(res)} "
          f"others-failing={others} sha-restored={restored} git-src-clean={cl} green-after={green}")
print("ALL HOLD" if ok else "SOMETHING DID NOT HOLD")
