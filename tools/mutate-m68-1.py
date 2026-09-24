#!/usr/bin/env python3
"""M68.1 regression closure: every correction must be load-bearing ON ITS OWN, at a named test.

For each mutation: record the file's SHA-256, apply the mutation, run the named suites, restore the file, and
CHECK the SHA-256 again — the run aborts if a restore is not byte-identical. A mutation passes only when the named
test fails. Three rules keep the harness from reporting what it did not see (review R4, re-review set 1 P4):

  1. A GREEN BASELINE first. Every targeted test must pass before any mutation, or the run stops: one
     pre-existing failure would otherwise make every mutation read as guarded.
  2. ONLY THIS RUN'S REPORTS. The report directory is emptied before each run, and a report is counted only if it
     was written after that run started.
  3. A RUN THAT DID NOT RUN SAYS SO. A mutation that stops the code compiling, or yields no reports, is reported
     NOT RUN, never as green and never as red.

    python3 tools/mutate-m68-1.py            # headless mutations
    python3 tools/mutate-m68-1.py --frame    # also the display test; opens windows, needs a real display

Uses your JAVA_HOME; Java 21 required.
"""
import argparse, glob, hashlib, os, re, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/'
HEADLESS = ['EvidenceIntegrityCoverageTest', 'CoveragePolicyEvidenceTest', 'EntryPointAuthorshipTest',
            'ReportRendererTest', 'SessionPairingScopeTest', 'PairingScopeSurfacesTest', 'MismatchWordingTest',
            'UserVisibleWordingGuardTest', 'TopologyStatusTooltipTest']
FRAME = 'PairingDuringLoadFrameTest'
PARITY = FRAME + '.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession'

# name, file, old, new, the test that must fail, frame-only?
MUT = [
 ('M1 ignore declared authorship', B + 'topology/Scaffolding.java',
  'if (nodeScoped(node) && vocabulary != null && vocabulary.trustedForNodeFacts()) {',
  'if (false && nodeScoped(node) && vocabulary != null && vocabulary.trustedForNodeFacts()) {',
  'EvidenceIntegrityCoverageTest.thePacketGraphGivesThreeAndThree', False),
 ('M2 restore authored-only membership', B + 'topology/CoverageService.java',
  'Set<String> outOfTopology = membership.unknownToTopology();',
  'Set<String> outOfTopology = new LinkedHashSet<>(logged); outOfTopology.removeAll(scope.loggable()); '
  'outOfTopology.removeAll(scope.excluded().keySet());',
  'EvidenceIntegrityCoverageTest.aDeclaredFrameworkNodeThatLogsDoesNotWarn', False),
 ('M3a derive membership from the ratio', B + 'topology/CoverageService.java',
  'member.put("established", !logged.isEmpty());', 'member.put("established", ratioAvailable);',
  'EvidenceIntegrityCoverageTest.zeroPopulationIsNoRatioNotNoMembership', False),
 ('M3b derive no-ratio from no-membership (the brief\'s third)', B + 'topology/CoverageService.java',
  'boolean ratioAvailable = coverage.denominator() > 0;',
  'boolean ratioAvailable = coverage.denominator() > 0 && !logged.isEmpty();',
  'EvidenceIntegrityCoverageTest.noOutputKeepsARatioOfZero', False),
 ('M4 retention reaches a downstream claim', B + 'session/CoveragePolicy.java',
  'if (pairing != null && !pairing.evidenced()) {', 'if (false && pairing != null && !pairing.evidenced()) {',
  'CoveragePolicyEvidenceTest.noNodeOutputIsNotAFit', False),
 ('M5 the PDF drops a table\'s notes again', B + 'report/ReportRenderer.java',
  '                    tableNotes(doc, c, body.notes());', '                    // tableNotes(doc, c, body.notes());',
  'ReportRendererTest.aTablesNotesAreOnThePageAsTheyAreOnScreen', False),
 ('M6 the session verdict loses its scope (R2)', B + 'session/node/Pairing.java',
  'verdict = GraphPairing.of(declared, logged).withScope(sampled, total);', 'verdict = GraphPairing.of(declared, logged);',
  'SessionPairingScopeTest.theSessionVerdictCarriesItsScope', False),
 ('M6f the session verdict loses its scope, seen by the parity test (R1)', B + 'session/node/Pairing.java',
  'verdict = GraphPairing.of(declared, logged).withScope(sampled, total);', 'verdict = GraphPairing.of(declared, logged);',
  PARITY, True),
 ('M7 discovery loses its scope (R2)', B + 'topology/GraphmlDiscovery.java',
  'if (pairing != null && recordsScanned >= 0) pairing = pairing.withScope(recordsScanned, recordsTotal);', '',
  'PairingScopeSurfacesTest.discoveryIsScopedAndCarriesItsFacts', False),
 ('M7f discovery loses its scope, seen by the parity test (R1)', B + 'topology/GraphmlDiscovery.java',
  'if (pairing != null && recordsScanned >= 0) pairing = pairing.withScope(recordsScanned, recordsTotal);', '',
  PARITY, True),
 ('M8 discovery drops the pairing facts (O2)', B + 'topology/GraphmlDiscovery.java',
  '                out.putAll(pairing.facts());', '',
  'PairingScopeSurfacesTest.discoveryIsScopedAndCarriesItsFacts', False),
 ('M9 a whole-log comparison never supersedes (R2)', B + 'topology/PairingQualification.java',
  'wholeLog && publishedPartial);', 'false);',
  'PairingScopeSurfacesTest.aWholeLogComparisonSupersedesTheSample', False),
 ('M10 the finding export concludes a build again (R3)', B + 'topology/MismatchWording.java',
  '"the record and the graph disagree about which nodes exist";', '"the graphml is probably from a different build";',
  'MismatchWordingTest.eachSentenceStatesTheDisagreementAndNoBuildConclusion', False),
 ('M10g …and the repository guard sees it too', B + 'topology/MismatchWording.java',
  '"the record and the graph disagree about which nodes exist";', '"the graphml is probably from a different build";',
  'UserVisibleWordingGuardTest.noJavaStringLiteralDrawsTheBuildConclusion', False),
 ('M11 the help page instructs the conclusion again (R3)', 'src/main/resources/help/help.html',
  'A mismatch says the two disagree about which nodes exist.', 'Treat a mismatch as a version problem.',
  'UserVisibleWordingGuardTest.noHelpPageOrPublishedDocDrawsTheBuildConclusion', False),
 ('M12 the audit label states retention as fit (O3)', B + 'topology/GraphPairing.java',
  'if (!evidenced()) return "keptUnjudged";', 'if (!evidenced()) return "applies";',
  'SessionPairingScopeTest.theAuditLabelSeparatesKeepFromFit', False),
 ('M13 a pairing qualification hides the level caveat again (O4)', B + 'session/CoveragePolicy.java',
  '? " Also: " + levelReason(mostVerboseLevel) : "";', '? "" : "";',
  'CoveragePolicyEvidenceTest.bothQualificationsAreStated', False),
 ('M14 the tooltip loses the full line (O1)', B + 'ui/TopologyPanel.java',
  'return "<html>" + String.join("<br>", escaped.split("   \\u00b7   ")) + "</html>";', 'return line;',
  'TopologyStatusTooltipTest.theTooltipCarriesEveryPartOnItsOwnLine', False),
 ('M16 a sampled note loses its leading scope (set 3)', B + 'topology/GraphPairing.java',
  'String lead = sampled() ? scope() + ": " : "";', 'String lead = "";',
  'PairingScopeSurfacesTest.theNoteLeadsWithItsScope', False),
 ('M17 the panel note leads with the superseded sample again (set 3)', B + 'topology/PairingQualification.java',
  '        if (q.supersedesSample()) {', '        if (false && q.supersedesSample()) {',
  'PairingScopeSurfacesTest.thePanelNoteLeadsWithWhatQualifiesIt', False),
 ('M15f the pairing note goes back to fifth place (O1)', B + 'ui/TopologyPanel.java',
  '        appendPart(sb, pairingPart);      // M35.6 — persistent, because it qualifies everything below\n'
  '        appendPart(sb, statusBase);',
  '        appendPart(sb, statusBase);\n        appendPart(sb, pairingPart);',
  PARITY, True),
]

def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()

def run(frame):
    reports = os.path.join(ROOT, 'target', 'surefire-reports')
    for r in glob.glob(reports + '/*'):
        os.remove(r)
    started = time.time()
    tests = HEADLESS + ([FRAME] if frame else [])
    cmd = ['mvn', '-q', '-o', '-Dtest=' + ','.join(tests), '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    if frame:
        cmd[1:1] = ['-Djava.awt.headless=false', '-DargLine=-Djava.awt.headless=false']
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    results = {}
    for rep in glob.glob(reports + '/TEST-*.xml'):
        if os.path.getmtime(rep) < started:
            continue                      # rule 2: never a report this run did not write
        x = open(rep, errors='ignore').read()
        for m in re.finditer(r'<testcase name="([^"(]+)[^"]*" classname="[^"]*\.([A-Za-z0-9]+)"[^>]*?(/>|>(.*?)</testcase>)', x, re.S):
            body = m.group(4) or ''
            results[m.group(2) + '.' + m.group(1)] = 'skip' if '<skipped' in body else \
                'fail' if ('<failure' in body or '<error' in body) else 'pass'
    compiled = 'COMPILATION ERROR' not in proc.stdout + proc.stderr
    return compiled, results

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--frame', action='store_true'); a = ap.parse_args()
    print('baseline…')
    compiled, base = run(a.frame)
    ran = {k: v for k, v in base.items() if v != 'skip'}
    bad = [k for k, v in ran.items() if v == 'fail']
    if not compiled or not ran or bad:
        print(f'BASELINE NOT GREEN — stopping. compiled={compiled} ran={len(ran)} failing={bad}')
        sys.exit(2)
    print(f'baseline GREEN: {len(ran)} tests, {len(base) - len(ran)} skipped')
    missing = [m[4] for m in MUT if (a.frame or not m[5]) and m[4] not in ran]
    if missing:
        print(f'a named test did not run in the baseline, so its mutation could only pass vacuously: {missing}')
        sys.exit(2)
    unguarded = 0
    for name, f, old, new, must_fail, frame_only in MUT:
        if frame_only and not a.frame:
            print(f'{name}: SKIPPED (needs --frame)')
            continue
        path = os.path.join(ROOT, f); before = sha(path); orig = open(path).read()
        assert orig.count(old) == 1, (name, 'anchor not unique or missing')
        open(path, 'w').write(orig.replace(old, new, 1))
        try:
            compiled, res = run(a.frame)
        finally:
            open(path, 'w').write(orig)
        if sha(path) != before:
            print(f'{name}: RESTORE NOT BYTE-IDENTICAL — stopping'); sys.exit(3)
        failing = sorted(k for k, v in res.items() if v == 'fail')
        if not compiled or not res:
            verdict = 'NOT RUN (did not compile, or wrote no reports)'; unguarded += 1
        elif must_fail in failing:
            verdict = f'RED at {must_fail} ({len(failing)} failing)'
        elif failing:
            verdict = f'RED, but NOT at the named test {must_fail}: {failing}'; unguarded += 1
        else:
            verdict = 'STILL GREEN — the suite does not guard this'; unguarded += 1
        print(f'{name}: {verdict}; restored byte-identical')
    sys.exit(1 if unguarded else 0)

if __name__ == '__main__':
    main()
