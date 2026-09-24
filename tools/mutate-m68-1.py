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
  4. A FAILURE, NOT A CRASH (round 3, O-b). The named test must end in a surefire <failure>: an assertion that
     caught the defect. An <error> there means the mutation only made the test crash, which proves nothing about
     the assertion, and is reported as such. A CONTROL entry plants exactly that and must be recognised.
  5. GREEN AGAIN AFTER EVERY RESTORE (round 3, O-b). The byte-identical check covers the file; re-running the
     suites covers the test, so a frame test that timed out under a mutated run cannot hide a broken baseline.

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
  'wholeLog && publishedPartial, logRecords, logRecords);', 'false, logRecords, logRecords);',
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
  '        if (widest != null && widest.supersedesSample()) {', '        if (false && widest != null && widest.supersedesSample()) {',
  'PairingScopeSurfacesTest.thePanelNoteLeadsWithWhatQualifiesIt', False),
 ('M18 a grown log never makes the whole-log verdict stale (N1)', B + 'topology/PairingQualification.java', '        return logRecordsNow > logRecords;', '        return false;', 'PairingScopeSurfacesTest.aGrownLogMakesTheWholeLogVerdictStale', False),
 ('M18f the same, seen through Follow on a real store (N1)', B + 'topology/PairingQualification.java', '        return logRecordsNow > logRecords;', '        return false;', 'PairingDuringLoadFrameTest.aFollowAppendMakesTheWholeLogVerdictStale', True),
 ('M19f the published pairing is not re-judged after an append (N1, one level down)', B + 'ui/MainFrame.java', '        republishPairingAfterAppend();\n', '', 'PairingDuringLoadFrameTest.aFollowAppendMakesTheWholeLogVerdictStale', True),
 ('M20 the session ignores a change of total (N1, the session copy)', B + 'session/node/OpenLog.java', '                || wasTotal != total || wasSampled != sampled;', '                ;', 'SessionPairingScopeTest.aGrownLogReScopesTheSessionVerdict', False),
 ('M21 rescoping leaves the old total (N1)', B + 'topology/GraphPairing.java', '        if (recordsScanned < 0 || total == recordsTotal) return this;', '        if (true) return this;', 'PairingScopeSurfacesTest.aRescopedPairingCountsTheAppendedRecords', False),
 ('M22 the plain overwrite is back: a narrower comparison replaces the wider (N2)', B + 'topology/PairingQualifications.java', '        narrower = q;\n        return widest == null', '        widest = q;\n        narrower = null;\n        return widest == null', 'PairingScopeSurfacesTest.aNarrowerComparisonNeverReplacesAWiderOne', False),
 ('M23 plant a text block carrying the conclusion (N3)', B + 'topology/MismatchWording.java', '    private MismatchWording() {\n    }', '    private MismatchWording() {\n    }\n\n    static final String PLANTED = """\n            the graphml is probably from a different build\n            """;', 'UserVisibleWordingGuardTest.noJavaStringLiteralDrawsTheBuildConclusion', False),
 ('M24 plant the conclusion split across two literals, neither half matching (N3)', B + 'topology/MismatchWording.java', '    private MismatchWording() {\n    }', '    private MismatchWording() {\n    }\n\n    static final String PLANTED = "the graphml is from a different "\n            + "build, which makes every other figure suspect";', 'UserVisibleWordingGuardTest.noJavaStringLiteralDrawsTheBuildConclusion', False),
 ('M25 plant the conclusion in the in-app assistant prompt (N3)', 'src/main/resources/llm/system-prompt.md', None, 'If the graph does not match, the graphml is probably from a different build.\n', 'UserVisibleWordingGuardTest.noHelpPageOrPublishedDocDrawsTheBuildConclusion', False),
 ('M26 plant the conclusion in a served skill (N3)', 'docs/skills/README.md', None, '\nTreat a mismatch as a version problem.\n', 'UserVisibleWordingGuardTest.noHelpPageOrPublishedDocDrawsTheBuildConclusion', False),
 ('M27 plant the conclusion in an [Unreleased] changelog line (O-a)', 'CHANGELOG.md', '## [Unreleased]\n', '## [Unreleased]\n\n- Treat a mismatch as a version problem.\n', 'UserVisibleWordingGuardTest.noHelpPageOrPublishedDocDrawsTheBuildConclusion', False),
 ('M28f discovery samples differently from the frame and the session (O-c)', B + 'ui/MainFrame.java', '                config.sourceRoots, sample.ids(), store == null ? -1 : sample.scanned(),', '                config.sourceRoots, sample.ids(), store == null ? -1 : sample.scanned() - 1,', 'PairingDuringLoadFrameTest.aSampledPairingAgreesAcrossFrameDiscoveryAndSession', True),
 ('C1 CONTROL: a mutation that only makes the named test crash (O-b)', B + 'topology/PairingQualification.java', '        Map<String, Object> m = (Map<String, Object>) coverageEcho.getOrDefault("membership", Map.of());', '        if (true) throw new IllegalStateException("planted crash");\n        Map<String, Object> m = (Map<String, Object>) coverageEcho.getOrDefault("membership", Map.of());', 'PairingScopeSurfacesTest.aWholeLogComparisonSupersedesTheSample', False, 'control'),
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
                'fail' if '<failure' in body else 'error' if '<error' in body else 'pass'
    compiled = 'COMPILATION ERROR' not in proc.stdout + proc.stderr
    return compiled, results

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--frame', action='store_true'); a = ap.parse_args()
    # every anchor is checked before anything runs: a stale anchor used to surface only when its mutation came up,
    # an hour into a run (round 3, set 5 attempt 1, M9)
    stale = [e[0] for e in MUT if e[2] is not None and open(os.path.join(ROOT, e[1])).read().count(e[2]) != 1]
    if stale:
        print(f'ANCHORS NOT UNIQUE OR MISSING — stopping before the baseline: {stale}'); sys.exit(2)
    print('baseline…')
    compiled, base = run(a.frame)
    ran = {k: v for k, v in base.items() if v != 'skip'}
    bad = [k for k, v in ran.items() if v in ('fail', 'error')]
    if not compiled or not ran or bad:
        print(f'BASELINE NOT GREEN — stopping. compiled={compiled} ran={len(ran)} failing={bad}')
        sys.exit(2)
    print(f'baseline GREEN: {len(ran)} tests, {len(base) - len(ran)} skipped')
    missing = [m[4] for m in MUT if (a.frame or not m[5]) and m[4] not in ran]
    if missing:
        print(f'a named test did not run in the baseline, so its mutation could only pass vacuously: {missing}')
        sys.exit(2)
    unguarded = 0
    for entry in MUT:
        name, f, old, new, must_fail, frame_only = entry[:6]
        control = len(entry) > 6 and entry[6] == 'control'
        if frame_only and not a.frame:
            print(f'{name}: SKIPPED (needs --frame)')
            continue
        path = os.path.join(ROOT, f); before = sha(path); orig = open(path).read()
        if old is None:
            open(path, 'w').write(orig + new)
        else:
            assert orig.count(old) == 1, (name, 'anchor not unique or missing')
            open(path, 'w').write(orig.replace(old, new, 1))
        try:
            compiled, res = run(a.frame)
        finally:
            open(path, 'w').write(orig)
        if sha(path) != before:
            print(f'{name}: RESTORE NOT BYTE-IDENTICAL, stopping'); sys.exit(3)
        outcome = res.get(must_fail)
        failing = sorted(k for k, v in res.items() if v == 'fail')
        if not compiled or not res:
            verdict = 'NOT RUN (did not compile, or wrote no reports)'; guarded = False
        elif outcome == 'fail':
            verdict = f'RED, a <failure> at {must_fail} ({len(failing)} failing)'; guarded = True
        elif outcome == 'error':
            verdict = f'ERROR, not a failure, at {must_fail}: the test crashed, which proves nothing'; guarded = False
        elif failing:
            verdict = f'RED, but NOT at the named test {must_fail}: {failing}'; guarded = False
        else:
            verdict = 'STILL GREEN: the suite does not guard this'; guarded = False
        again_compiled, again = run(a.frame)          # rule 5: green again, or the next result is untrustworthy
        bad = [k for k, v in again.items() if v in ('fail', 'error')]
        if not again_compiled or not again or bad:
            print(f'{name}: {verdict}; NOT GREEN AFTER RESTORE {bad}, stopping'); sys.exit(4)
        if control:
            ok = outcome == 'error'
            print(f'{name}: {verdict}; control {"recognised" if ok else "NOT RECOGNISED"}; green again')
            if not ok: unguarded += 1
            continue
        if not guarded: unguarded += 1
        print(f'{name}: {verdict}; restored byte-identical; green again')
    sys.exit(1 if unguarded else 0)

if __name__ == '__main__':
    main()
