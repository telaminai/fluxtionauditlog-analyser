#!/usr/bin/env python3
"""Destructive-to-working-copy mutation witnesses; restores exact bytes in finally.
Run alone in a disposable feature worktree with a real display (Linux: xvfb-run -a).
Each witness must be a JUnit assertion failure in the named test, not a compile error or a skip.
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
    ('deadline-late-publication', JAVA, 'preparationDeadlineRefusesBeforeReadReturnsAndLateCompletionCannotLight', [
        (FRAME, 'if (javaSpotlightTicket == ticket) javaSpotlightTicket++;', '// mutation: expiry does not invalidate ticket'),
        (FRAME, '                    if (result.isDone()) return;', '                    // mutation: ignore terminal reply'),
        (FRAME, '                    if (System.nanoTime() - deadlineNanos >= 0) { expire.run(); return; }', '                    // mutation: no deadline check before apply')]),
]


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
        report = ROOT/'target/surefire-reports'/f'TEST-telamin.fluxtion.audit.analyser.analyser.ui.{suite}.xml'
        result = {'mutation': name, 'test': f'{suite}#{method}', 'sourceSites': [], 'seenRed': False}
        try:
            for p, old, new in edits:
                path = ROOT/p
                text = path.read_text()
                if text.count(old) != 1:
                    raise RuntimeError(f'{name}: expected exactly one source site in {p}: {old!r}')
                result['sourceSites'].append({'file': p, 'line': text[:text.index(old)].count('\n')+1, 'before': old, 'after': new})
                path.write_text(text.replace(old, new, 1))
            report.unlink(missing_ok=True)
            command = ['mvn', '-q', 'test', '-Dtest='+suite+'#'+method,
                       '-Djava.awt.headless=false', '-DargLine=-Djava.awt.headless=false']
            result['command'] = command
            with (args.out/(name+'.log')).open('w') as log:
                result['exitCode'] = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=180).returncode
            if report.exists():
                xml = ET.parse(report).getroot()
                failures = [t.find('failure') for t in xml.findall('testcase') if t.get('name','').split('(')[0] == method and t.find('failure') is not None]
                result['seenRed'] = result['exitCode'] != 0 and bool(failures) and int(xml.get('errors','0')) == 0 and int(xml.get('skipped','0')) == 0
                result['failures'] = [{'message': f.get('message'), 'text': f.text} for f in failures]
        finally:
            for path, data in originals.items():
                path.write_bytes(data)
            result['restored'] = all(path.read_bytes() == data for path, data in originals.items())
            result['sourceHashes'] = {str(path.relative_to(ROOT)): hashlib.sha256(data).hexdigest() for path, data in originals.items()}
            (args.out/(name+'.json')).write_text(json.dumps(result, indent=2)+'\n')
        print(name+': '+('SEEN RED; restored' if result['seenRed'] and result['restored'] else 'NOT PROVEN; inspect log'), flush=True)
        results.append(result)
    (args.out/'summary.json').write_text(json.dumps(results, indent=2)+'\n')
    return 0 if all(r['seenRed'] and r['restored'] for r in results) else 1

if __name__ == '__main__':
    raise SystemExit(main())
