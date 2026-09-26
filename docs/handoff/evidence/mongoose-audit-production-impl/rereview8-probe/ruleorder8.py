"""Eighth re-review probe (not a witness): does the matrix's OFFENDER assertion — the new bound rule — catch each
mutation on its own? The test asserts reach before offenders, so under witness12 the reach failure masks it. Here
the test file is edited so the offenders assertion runs first; both files are restored by SHA-256 afterwards and a
green run of the three MA-8 classes is required."""
import subprocess, glob, os, sys, hashlib, re, xml.etree.ElementTree as ET
ROOT = os.getcwd(); REPORTS = os.path.join(ROOT, 'target', 'surefire-reports')
F = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java'
TF = 'src/test/java/telamin/fluxtion/audit/analyser/analyser/topology/ControlAddressAndScopeTest.java'
MX = 'ControlAddressAndScopeTest#noBranchOfTheSentencePresumesAProcessor'
def clear():
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')) + glob.glob(os.path.join(REPORTS, '*.txt')): os.remove(p)
def run(tests):
    clear()
    r = subprocess.run(['mvn', '-q', '-o', '-Dtest=' + tests, '-Dsurefire.failIfNoSpecifiedTests=false', 'test'], cwd=ROOT, capture_output=True, text=True)
    res = {}
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')):
        for tc in ET.parse(p).getroot().iter('testcase'):
            k = tc.get('classname').split('.')[-1] + '#' + re.sub(r'\(.*\)$', '', tc.get('name'))
            f, e = tc.find('failure'), tc.find('error')
            res[k] = ('failure', f.get('message') or '') if f is not None else ('error', e.get('message') or '') if e is not None else ('pass', '')
    return ('COMPILATION ERROR' not in r.stdout + r.stderr), res
def clean(): return subprocess.run(['git', 'status', '--porcelain', '--', 'src'], cwd=ROOT, capture_output=True, text=True).stdout.strip() == ''
REACH = '''        assertEquals(java.util.List.of(), reached.entrySet().stream().filter(e -> e.getValue() == 0).map(java.util.Map.Entry::getKey)
                .toList(), "a branch of the sentence the matrix no longer reaches");
'''
M = [
 ("R7-1 mutation", '''                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");''',
  '''                    : ". If " + all(premises) + ", " + lines + "; otherwise this change explains nothing here");'''),
 ("R7-2 mutation", '''        String span = "after record " + (c.row() + 1) + (end1 == null ? "" : " and before " + end1) + in;''',
  '''        String span = (end1 == null ? "" : "before " + end1 + ", ") + lines;'''),
 ("R7-3 mutation", '''            s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + span)
                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");''',
  '''            s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + lines)
                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");'''),
 ("R7-4 mutation", '''                && (crosses || c.groupId() != null && !c.groupId().equals(next.groupId()));''',
  '''                && (c.groupId() != null && !c.groupId().equals(next.groupId()));'''),
 ("P1 bound ignores the first marker", '''            String end1 = closer != null && (m1 == null || closer < m1) ? "record " + (closer + 1)''',
  '''            String end1 = closer != null ? "record " + (closer + 1)'''),
]
if not clean(): sys.exit("src not clean")
tp = os.path.join(ROOT, TF); t_orig = open(tp, 'rb').read(); th = hashlib.sha256(t_orig).hexdigest()
ts = t_orig.decode(); assert ts.count(REACH) == 1
open(tp, 'wb').write(ts.replace(REACH, '').encode())   # reach assertion removed: the offenders assertion is now the only one left
try:
    for name, old, new in M:
        p = os.path.join(ROOT, F); orig = open(p, 'rb').read(); h = hashlib.sha256(orig).hexdigest(); s = orig.decode()
        assert s.count(old) == 1, name
        open(p, 'wb').write(s.replace(old, new).encode())
        try: c, res = run('ControlAddressAndScopeTest#noBranchOfTheSentencePresumesAProcessor')
        finally: open(p, 'wb').write(orig)
        kind, msg = res.get(MX, ('missing', ''))
        rules = sorted(set(re.findall(r'(R5-1|R5-2/R7-4|O5-1|R6-2|R6-1|unnamed condition|R7 bound) [a-zA-Z]', msg)))
        n = len(re.findall(r'(?:R5-1|R5-2/R7-4|O5-1|R6-2|R6-1|unnamed condition|R7 bound) [a-zA-Z]', msg))
        print(f"{name}: compiled={c} matrix->{kind} rules={rules} offenders={n} restored={hashlib.sha256(open(p,'rb').read()).hexdigest()==h}")
        m = re.search(r'R7 bound [^:]+: [^,\]]{0,260}', msg)
        if m: print("   first R7 bound offender: " + m.group(0))
finally:
    open(tp, 'wb').write(t_orig)
print("test file restored", hashlib.sha256(open(tp, 'rb').read()).hexdigest() == th, "git src clean", clean())
c, res = run('CoveragePerNodeLevelTest,ControlAddressAndScopeTest,PerNodeLevelChangesTest')
print("green after:", c and res and all(v[0] == 'pass' for v in res.values()), len(res), "tests")
