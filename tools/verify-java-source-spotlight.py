#!/usr/bin/env python3
"""Destructive-to-working-copy mutation witnesses; restores exact bytes in finally.
Run alone in a disposable feature worktree with a real display (Linux: xvfb-run -a).
Each witness requires a passing unmutated named test, then a JUnit assertion failure in that test,
not a compile error or a skip. A failed baseline is recorded without applying the mutation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/'
FRAME = BASE + 'ui/MainFrame.java'
PANE = BASE + 'ui/SourcePanel.java'
GEOMETRY = BASE + 'ui/JavaLineGeometry.java'
JAVA = 'JavaSourceSpotlightFrameTest'
WITNESSES = [
    ('negative-cache', JAVA, 'realEntranceRereadsJarMissAndHitButNewJarNeedsReconfiguration', [
        (BASE+'source/MavenSourceResolver.java', '        cache.remove(fqn);', '        // mutation: retain negative cache')]),
    ('ticket', JAVA, 'blockedArchiveReadLeavesEdtFreeAndClearConfigurationAndNewRequestSupersedeIt', [
        (FRAME, 'ticket != javaSpotlightTicket || !sourceService.isCurrent(lookup)', 'false || !sourceService.isCurrent(lookup)')]),
    ('read-on-edt', JAVA, 'blockedArchiveReadLeavesEdtFreeAndClearConfigurationAndNewRequestSupersedeIt', [
        (BASE+'source/SourceService.java', 'if (javax.swing.SwingUtilities.isEventDispatchThread())', 'if (false)'),
        (FRAME, '() -> JavaSpotlightPlan.read(lookup, asked.requests(), retained), plan -> {',
         '''() -> {
                    var read = new java.util.concurrent.atomic.AtomicReference<JavaSpotlightPlan>();
                    try { SwingUtilities.invokeAndWait(() -> read.set(JavaSpotlightPlan.read(lookup, asked.requests(), retained))); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                    return read.get();
                }, plan -> {''')]),
    ('selected-model', JAVA, 'changedSelectedProcessorUpdatesModelAndBadBatchDoesNotReveal', [
        (FRAME, 'sourceService.acceptSpotlightModel(lookup, prepared.target().sourceFqn(), prepared.model());', '// mutation: do not update the selected model')]),
    ('raw-caret', JAVA, 'graphAndJavaUseOneVisibleDestinationInEitherOrder', [
        (PANE, 'return JavaLineGeometry.band(first, last, view);', 'return first.getBounds();')]),
    ('no-clipping', JAVA, 'wrappedLogicalLineMeasuresAllRowsClipsAndReportsPartial', [
        (GEOMETRY, 'Rectangle clipped = band.intersection(viewport);', 'Rectangle clipped = new Rectangle(band);')]),
    ('first-wrapped-row', JAVA, 'wrappedLogicalLineMeasuresAllRowsClipsAndReportsPartial', [
        (PANE, 'last = text.modelToView2D(end);', 'last = text.modelToView2D(start);')]),
    ('design-containment', 'DesignSpotlightFrameTest', 'viewportMovementExtinguishesPartiallyVisibleDesignBand', [
        (BASE+'ui/DesignSourcePanel.java', '!text.getVisibleRect().contains(at)', '!text.getVisibleRect().intersects(at)')]),
    ('viewport-listeners', JAVA, 'viewportHooksRemeasureWithoutAnotherRequestAndBindingsNeverRetarget', [
        (FRAME, 'sourcePanel.onSourceViewChanged(this::sourceViewportChanged);', 'sourcePanel.onSourceViewChanged(null);'),
        (FRAME, 'topologyPanel.sourceViewer().onSourceViewChanged(this::sourceViewportChanged);', 'topologyPanel.sourceViewer().onSourceViewChanged(null);')]),
    ('revision-binding', JAVA, 'viewportHooksRemeasureWithoutAnotherRequestAndBindingsNeverRetarget', [
        (PANE, 'if (!pane.isShowing() || pane.snapshot == null || !pane.snapshot.equals(anchor.document()))', 'if (!pane.isShowing())')]),
    ('reveal-before-read', JAVA, 'invalidJavaBeforeRecordRevealKeepsSelectionAndFilter', [
        (FRAME, 'long ticket = ++javaSpotlightTicket;', 'revealRows.run();\n        long ticket = ++javaSpotlightTicket;'),
        (FRAME, '                        revealRows.run();', '                        // mutation: rows already revealed')]),
    ('missing-band-partial', JAVA, 'missingBandDoesNotClaimPartialDuringApply', [
        (FRAME, 'binding.viewer().javaBounds(binding.anchor(), binding.line())\n                            .ifPresent(band -> one.put("partial", band.partial()));',
         'one.put("partial", binding.viewer().javaBounds(binding.anchor(), binding.line()).map(SourcePanel.JavaBand::partial).orElse(true));')]),
    ('deadline-late-publication', JAVA, 'preparationDeadlineRefusesBeforeReadReturnsAndLateCompletionCannotLight', [
        (FRAME, 'if (javaSpotlightTicket == ticket) javaSpotlightTicket++;', '// mutation: expiry does not invalidate ticket'),
        (FRAME, '                    if (result.isDone()) return;', '                    // mutation: ignore terminal reply'),
        (FRAME, '                    if (System.nanoTime() - deadlineNanos >= 0) { expire.run(); return; }', '                    // mutation: no deadline check before apply')]),
]


def run_test(suite, method, log):
    report = ROOT/'target/surefire-reports'/f'TEST-telamin.fluxtion.audit.analyser.analyser.ui.{suite}.xml'
    report.unlink(missing_ok=True)
    command = ['mvn', '-q', 'test', '-Dtest='+suite+'#'+method,
               '-Djava.awt.headless=false', '-DargLine=-Djava.awt.headless=false']
    with log.open('w') as output:
        code = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=180).returncode
    result = {'command': command, 'exitCode': code, 'green': False, 'assertionFailure': False}
    if report.exists():
        xml = ET.parse(report).getroot()
        cases = [t for t in xml.findall('testcase') if t.get('name','').split('(')[0] == method]
        failures = [t.find('failure') for t in cases if t.find('failure') is not None]
        counts = {k: int(xml.get(k, '0')) for k in ('tests', 'failures', 'errors', 'skipped')}
        one_test = len(cases) == 1 and counts['tests'] == 1
        no_error_or_skip = counts['errors'] == counts['skipped'] == 0
        result.update(counts=counts, matchedTests=len(cases),
                      failures=[{'message': f.get('message'), 'text': f.text} for f in failures])
        result['green'] = code == 0 and one_test and no_error_or_skip and counts['failures'] == 0 and not failures
        result['assertionFailure'] = code != 0 and one_test and no_error_or_skip and counts['failures'] == 1 and len(failures) == 1
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', help='one witness name')
    parser.add_argument('--out', type=Path, default=ROOT/'target/java-spotlight-mutations')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    selected = [w for w in WITNESSES if not args.only or w[0] == args.only]
    if not selected:
        parser.error('unknown witness')
    results = []
    for name, suite, method, edits in selected:
        originals = {ROOT/p: (ROOT/p).read_bytes() for p, _, _ in edits}
        result = {'mutation': name, 'test': f'{suite}#{method}', 'sourceSites': [],
                  'baselineGreen': False, 'seenRed': False}
        try:
            result['baseline'] = run_test(suite, method, args.out/(name+'.baseline.log'))
            result['baselineGreen'] = result['baseline']['green']
            if result['baselineGreen']:
                for p, old, new in edits:
                    path = ROOT/p
                    text = path.read_text()
                    if text.count(old) != 1:
                        raise RuntimeError(f'{name}: expected exactly one source site in {p}: {old!r}')
                    result['sourceSites'].append({'file': p, 'line': text[:text.index(old)].count('\n')+1, 'before': old, 'after': new})
                    path.write_text(text.replace(old, new, 1))
                mutated = run_test(suite, method, args.out/(name+'.log'))
                result.update(command=mutated['command'], exitCode=mutated['exitCode'],
                              failures=mutated.get('failures', []), seenRed=mutated['assertionFailure'])
        finally:
            for path, data in originals.items():
                path.write_bytes(data)
            result['restored'] = all(path.read_bytes() == data for path, data in originals.items())
            result['sourceHashes'] = {str(path.relative_to(ROOT)): hashlib.sha256(data).hexdigest() for path, data in originals.items()}
            (args.out/(name+'.json')).write_text(json.dumps(result, indent=2)+'\n')
        print(name+': '+('BASELINE GREEN; SEEN RED; restored' if result['baselineGreen'] and result['seenRed'] and result['restored'] else 'NOT PROVEN; inspect log'), flush=True)
        results.append(result)
    (args.out/'summary.json').write_text(json.dumps(results, indent=2)+'\n')
    return 0 if all(r['baselineGreen'] and r['seenRed'] and r['restored'] for r in results) else 1

if __name__ == '__main__':
    raise SystemExit(main())
