"""Round 7 targeted witnesses (owner-approved, R7-1..R7-4 only). Strict protocol: focused green baseline; surefire
reports DELETED before the mutated run; a <failure> (not <error>, not a compile failure) at the named test whose
MESSAGE carries the named assertion's label; SHA-256 restore; clean `git status -- src`; green re-run."""
import subprocess, glob, os, sys, hashlib, re, xml.etree.ElementTree as ET
ROOT = os.getcwd(); REPORTS = os.path.join(ROOT, 'target', 'surefire-reports')
F = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java'
T = 'CoveragePerNodeLevelTest,ControlAddressAndScopeTest,PerNodeLevelChangesTest'
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
W = [
 ("R7-1: the premise branch's conclusion unbounded again",
  '''                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");''',
  '''                    : ". If " + all(premises) + ", " + lines + "; otherwise this change explains nothing here");''',
  'ControlAddressAndScopeTest#theConclusionIsBoundedByTheChangeTheWindowAndTheGrouping', 'R7-1 A'),
 ("R7-2: the bound starts at the beginning of the log, in every grouping (round 6's form)",
  '''        String span = "after record " + (c.row() + 1) + (end1 == null ? "" : " and before " + end1) + in;''',
  '''        String span = (end1 == null ? "" : "before " + end1 + ", ") + lines;''',
  'ControlAddressAndScopeTest#theConclusionIsBoundedByTheChangeTheWindowAndTheGrouping', 'R7-2 D'),
 ("R7-3: the plain closer's ', so' conclusion unbounded again",
  '''            s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + span)
                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");''',
  '''            s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + lines)
                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");''',
  'ControlAddressAndScopeTest#theConclusionIsBoundedByTheChangeTheWindowAndTheGrouping', 'R7-3 I'),
 ("R7-4: R-B's rule extended across a marker again",
  '''                && (crosses || c.groupId() != null && !c.groupId().equals(next.groupId()));''',
  '''                && (c.groupId() != null && !c.groupId().equals(next.groupId()));''',
  'ControlAddressAndScopeTest#anUngroupedCloserAcrossAMarkerIsOpen', 'R7-4 L'),
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
    own = msg.startswith(label) or (': ' + label) in msg or msg.find(label) >= 0
    others = sorted(k.split('#')[1] for k, v in res.items() if v[0] != 'pass' and k != named)
    good = baseline and c2 and kind == 'failure' and own and restored and cl and green; ok &= good
    print(f"{'OK  ' if good else 'BAD '} {name}: baseline={'green' if baseline else 'NOT'} compiled={c2} "
          f"{named.split('#')[1]}->{kind} assertion='{msg[:90]}' own-assertion={own} others-failing={others} "
          f"sha-restored={restored} git-src-clean={cl} green-after={green}")
print("ALL WITNESSES HOLD" if ok else "A WITNESS DID NOT HOLD")
