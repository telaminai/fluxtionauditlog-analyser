"""P15 targeted controls: one per item (D-MA0c PDF / reply / tab, MA-0.5 follow line, MA-8's report path, MA-6.3's three
fixture semantics). Strict protocol: focused green baseline; surefire reports DELETED before the mutated run; a <failure>
(not <error>, not a compile failure) at the named test whose message carries the label; SHA-256 restore; clean
`git status -- src`; green re-run. Run with a display: the frame class needs one."""
import subprocess, glob, os, sys, hashlib, re, xml.etree.ElementTree as ET
ROOT = os.getcwd(); REPORTS = os.path.join(ROOT, 'target', 'surefire-reports')
J = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/'
T = 'FormatConformanceTest,ReportLogFindingsTest,LogFindingsOnEverySurfaceFrameTest'
FRAME = 'LogFindingsOnEverySurfaceFrameTest#anEmptyFollowedFileSaysSoEverywhere_andEverySurfaceClearsWhenARecordArrives'
def clear():
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')) + glob.glob(os.path.join(REPORTS, '*.txt')):
        if os.path.dirname(os.path.abspath(p)) == REPORTS: os.remove(p)
def run():
    clear()
    r = subprocess.run(['mvn', '-q', '-o', '-Dtest=' + T, '-Djava.awt.headless=false',
                        '-Dsurefire.failIfNoSpecifiedTests=false', 'test'], cwd=ROOT, capture_output=True, text=True)
    res = {}
    for p in glob.glob(os.path.join(REPORTS, 'TEST-*.xml')):
        for tc in ET.parse(p).getroot().iter('testcase'):
            k = tc.get('classname').split('.')[-1] + '#' + re.sub(r'\(.*\)$', '', tc.get('name'))
            f, e = tc.find('failure'), tc.find('error')
            res[k] = ('failure', (f.get('message') or '') + (f.text or '')) if f is not None else \
                     ('error', e.get('message') or '') if e is not None else ('pass', '')
    return ('COMPILATION ERROR' not in r.stdout + r.stderr), res
def clean(): return subprocess.run(['git', 'status', '--porcelain', '--', 'src/main'], cwd=ROOT, capture_output=True, text=True).stdout.strip() == ''
W = [
 ("D-MA0c PDF: the LOG FINDINGS callout removed", J + 'report/ReportRenderer.java',
  '''        if (logFindings != null && !logFindings.isClean()) {''',
  '''        if (logFindings != null && !logFindings.isClean() && false) {''',
  'ReportLogFindingsTest#theLogsFindingsAreOnThePdf_damageFirst', 'the page states what the file itself shows'),
 ("D-MA0c reply: the report verb's `producer` removed", J + 'ui/MainFrame.java',
  '''            echo.put("producer", producerDiagnostics.messages());''',
  '''            // echo.put("producer", producerDiagnostics.messages());''',
  FRAME, "D-MA0c: the report verb's reply"),
 ("D-MA0c tab: the Reports tab's LOG FINDINGS banner removed", J + 'ui/ReportsPanel.java',
  '''        if (findings != null && !findings.isClean()) {''',
  '''        if (findings != null && !findings.isClean() && false) {''',
  FRAME, 'D-MA0c: the Reports tab'),
 ("MA-0.5: the follow line drops the warning again", J + 'ui/MainFrame.java',
  '''                    + producerWarning() + trailingPendingNote());''',
  '''                    + trailingPendingNote());''',
  FRAME, 'the status bar, on the line Follow starts with'),
 ("MA-8 report path: the ledger's levelChange removed", J + 'topology/CoverageService.java',
  '''if (note != null && "uncovered".equals(row.get("status"))) row.put("levelChange", note);''',
  '''if (note != null && "uncovered".equals(row.get("status"))) { }''',
  'ReportLogFindingsTest#aReportsCoverageRowCarriesTheLevelChange_andStaysUncovered', 'the row carries the level change'),
 ("MA-6.3 c25: EMPTY_LOG never raised", J + 'parse/ProducerDiagnostics.java',
  '''        if (idx.size() == 0 && !pending) {''',
  '''        if (idx.size() == 0 && !pending && false) {''',
  'FormatConformanceTest#c25_aMarkerDeclaringZeroIsCompleteAndStillAnEmptyLog', 'an empty file raised nothing'),
 ("MA-6.3 c29: noRecordKey never raised", J + 'parse/ProducerDiagnostics.java',
  '''        noRecordKey(idx, rawText).ifPresent(out::add);''',
  '''        // noRecordKey(idx, rawText).ifPresent(out::add);''',
  'FormatConformanceTest#c29_aCompleteFileCanStillHoldADocumentWithNoRecordKey', 'the headerless document is named'),
 ("MA-6.3 c30: the MA-8 control-event parse disabled", J + 'topology/PerNodeLevelChanges.java',
  '''        return telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.isControlEvent(event);''',
  '''        return false;''',
  'FormatConformanceTest#c30_aPerNodeLevelChangeAnnotatesTheSameNodeOnBothPaths', 'the quiet node is annotated with its level'),
]
if not clean(): sys.exit("git status -- src/main is not clean before starting")
ok = True
for name, F, old, new, named, label in W:
    c, base = run(); baseline = c and base and all(v[0] == 'pass' for v in base.values()) and named in base
    p = os.path.join(ROOT, F); orig = open(p, 'rb').read(); h = hashlib.sha256(orig).hexdigest(); s = orig.decode()
    if s.count(old) != 1: print(f"BAD  {name}: anchor count {s.count(old)}"); ok = False; continue
    open(p, 'wb').write(s.replace(old, new).encode())
    try: c2, res = run(); kind, msg = res.get(named, ('missing', ''))
    finally: open(p, 'wb').write(orig)
    restored = hashlib.sha256(open(p, 'rb').read()).hexdigest() == h; cl = clean()
    c3, again = run(); green = c3 and again and all(v[0] == 'pass' for v in again.values())
    own = label in msg
    others = sorted(k.split('#')[1] for k, v in res.items() if v[0] != 'pass' and k != named)
    good = baseline and c2 and kind == 'failure' and own and restored and cl and green; ok &= good
    first = msg.split('\n')[0][:110]
    print(f"{'OK  ' if good else 'BAD '} {name}: baseline={'green' if baseline else 'NOT'} compiled={c2} "
          f"{named.split('#')[1]}->{kind} label-present={own} first-line='{first}' others-failing={others} "
          f"sha-restored={restored} git-src-clean={cl} green-after={green}")
print("ALL HOLD" if ok else "SOMETHING DID NOT HOLD")
