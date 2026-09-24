#!/usr/bin/env python3
"""Display and mutation gates. Use an isolated worktree and JDK21 with a real display.
Preflight checks all anchors before any Maven run. One shared clean baseline establishes
all selected methods; each mutation must fail its named assertion and restore green.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

CASES = [('follow-hold',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (System.currentTimeMillis() - sayAtMillis >= SAY_HOLD_MILLIS) {',
  'if (true) {',
  'StatusExplanationSurvivesFrameTest#anExplanationSurvivesAnIdleFollowTick'),
 ('report-link-opens-closed',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (graphTabs.selectGraph(gname)) return;',
  'if (true) { graphTabs.selectGraph(gname); return; }',
  'StatusExplanationSurvivesFrameTest#aReportLinkToAClosedChartOpensIt'),
 ('repair-tier-guard',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (!java.util.Objects.equals(tierWhenAsked, tierNow) || !saved.equals(config.savedGraphs)) {',
  'if (false) {',
  'DuplicateChartRepairFrameTest#aListThatChangedWhileTheDialogWasOpenIsRefused'),
 ('repair-carry-unsaved',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (!repairedNames.contains(live.name())) carried.add(live.withOpen(true));',
  '',
  'DuplicateChartRepairFrameTest#aChartMadeDuringTheRefusalSurvivesTheRepair'),
 ('repair-unsaved-name-taken',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/DuplicateChartRepair.java',
  'if (alsoTaken.contains(to)) {',
  'if (false) {',
  'DuplicateChartRepairFrameTest#renamingOntoAnUnsavedChartIsRefusedRatherThanDestroyingIt'),
 ('verb-withheld-only',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ActionExecutor.java',
  'onEdt(() -> graphTabs.isWithheldDefinition(target))',
  'onEdt(() -> graphTabs.hasDefinition(target))',
  'DuplicateChartRepairFrameTest#theAssistantCanEditAChartItCreatedDuringTheRefusal'),
 ('repair-nothing-chosen',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'status.setText("Nothing was chosen, so no chart names were changed.");',
  '',
  'DuplicateChartRepairFrameTest#okWithNothingChosenSaysSo'),
 ('explicit-name',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  '        if (name != null && !name.isBlank()\n'
  '                && (graphNamed(name.trim()) != null || (!restoring && hasDefinition(name)))) return null;',
  '',
  'ChartLifecycleReviewFrameTest#closedNamesAreReservedForCreationAndRename'),
 ('closed-definition',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  'if (java.util.Objects.equals(saved.name(), target) && openSaved(saved))',
  'if (false && java.util.Objects.equals(saved.name(), target) && openSaved(saved))',
  'ChartLifecycleReviewFrameTest#graphActionOnClosedChartPreservesItsDefinition'),
 ('import-before-view',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (store != null) restoreGraphDefinitions(List.copyOf(config.savedGraphs));',
  'onConfigChanged();\n        if (store != null) restoreGraphDefinitions(List.copyOf(config.savedGraphs));',
  'ChartLifecycleReviewFrameTest#importingOverAnOpenChartKeepsIncomingDefinitionAndOpenState'),
 ('stale-confirmation',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  'if (current >= 0 && name.equals(gp.graphName())) deleteConfirmed(current);',
  'deleteConfirmed(i);',
  'ChartLifecycleReviewFrameTest#confirmationCannotDeleteAReplacementTab'),
 ('external-import',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java',
  'spec.withExternal(fixed, fixedMarkers)',
  'new GraphSpec(spec.name(), spec.series(), spec.exprs(), spec.from(), spec.to(), spec.note(), spec.explanation(), '
  'spec.notes(), spec.rightAxis(), spec.guides(), spec.bands(), fixed, fixedMarkers)',
  'GraphProfileMetadataTest#externalPathsKeepStyleAndClosedState'),
 ('duplicate-profile',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java',
  '            SavedGraphMerge.requireUniqueNames(graphs);',
  '',
  'GraphProfileMetadataTest#duplicateNamesRefuseWithoutChangingProfileOrTarget'),
 ('duplicate-saved',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java',
  '        requireUniqueNames(saved);',
  '',
  'SavedGraphMergeTest#aDuplicateNameInTheProfileIsRefusedInsteadOfDiscarded'),
 ('duplicate-tabs',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java',
  '        requireUniqueNames(openTabs);',
  '',
  'GraphProfileMetadataTest#mergeRefusesAmbiguousOpenTabsWithoutChoosingAWinner'),
 ('merge-preservation',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java',
  'merged.add(live != null ? live.withOpen(true) : existing.withOpen(false));',
  'if (live != null) merged.add(live.withOpen(true));',
  'SavedGraphMergeTest#aChartThatIsNoLongerATabIsKeptAndMarkedClosed'),
 ('reserved-names',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  'if (known != null) taken.addAll(known);',
  '',
  'ChartNamesCannotCollideTest#aGeneratedNameSkipsOneAClosedChartStillHolds'),
 ('restore-closed',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  '            if (!g.open()) continue;',
  '',
  'ChartNamesCannotCollideTest#restoreReopensOpenChartsAndLeavesClosedOnesClosed'),
 ('reopen-notifies',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  '        if (opened) fireChanged();',
  '',
  'ChartNamesCannotCollideTest#reopeningASavedChartAsksToBeSaved'),
 ('report-adapter',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (reportsPanel != null) reportsPanel.select(name);',
  '',
  'ChartLifecycleReviewFrameTest#alternatingReportRowsReachesTheRealReportsPanelByIdentity'),
 ('chart-adapter',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'return graphTabs.openSaved(spec);',
  'return false;',
  'ChartLifecycleReviewFrameTest#rowReopenPersistsOpenStateWithoutAnotherEdit'),
 ('rename-modal',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  '        if (takenNames().contains(to)) return false;',
  '        if (takenNames().contains(to)) { JOptionPane.showMessageDialog(this, "Name already used"); return false; }',
  'ChartLifecycleReviewFrameTest#closedNamesAreReservedForCreationAndRename'),
 ('style-dropdown',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphPanel.java',
  '            mutated();\n        });\n        zoomIn',
  '        });\n        zoomIn',
  'StyleDropdownRequestsASaveTest#choosingAStyleFromTheDropdownAsksToBeSaved'),
 ('global-refusal',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'restoreGraphDefinitions(savedGraphs);',
  'graphTabs.restore(savedGraphs);',
  'DuplicateGlobalChartsFrameTest#duplicateGlobalChartsAreWithheldAndLogLoadingCompletes'),
 ('global-preservation',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (store == null || graphTabs.definitionRefusal() != null) return;',
  'if (store == null) return;',
  'DuplicateGlobalChartsFrameTest#duplicateGlobalChartsAreWithheldAndLogLoadingCompletes'),
 ('row-chart',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'return graphTabs.openSaved(spec);',
  'return false;',
  'ProjectPanelChartLifecycleFrameTest#openOnASavedChartRowOpensThatChartInTheFrame'),
 ('row-report',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
  'if (reportsPanel != null) reportsPanel.select(name);',
  '',
  'ProjectPanelChartLifecycleFrameTest#openOnEachReportRowRevealsTheReportsTab'),
 ('modal-cancel',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java',
  'if (!confirmDelete.test(name)) return;',
  'if (!confirmDelete.test(name)) { /* ignore Cancel */ }',
  'ProjectPanelChartLifecycleFrameTest#cancellingDeleteInARealFrameChangesNothing')]
CASES.append(('import-ambiguous-target', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java',
              'SavedGraphMerge.requireUniqueNames(target.savedGraphs);', '',
              'GraphProfileMetadataTest#mergingIntoDuplicateTargetRefusesBeforeAnyCategoryChanges'))
# The graph verb's series shape and style read-back (GraphSeriesShapeAndStyleEchoTest, a headless class).
GRAPH_VERB = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ActionExecutor.java'
SESSION_FACTS = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/SessionFacts.java'
ECHO_TEST = 'GraphSeriesShapeAndStyleEchoTest#'
CASES += [
    ('series-object', GRAPH_VERB, '            if (o instanceof String) continue;',
     '            if (o instanceof String || o instanceof Map<?, ?>) continue;',
     ECHO_TEST + 'anObjectInSeriesIsRefusedAndNothingIsCreated'),
    ('series-null', GRAPH_VERB, '            if (o instanceof String) continue;',
     '            if (o == null || o instanceof String) continue;',
     ECHO_TEST + 'aNullSeriesEntryIsRefused'),
    ('series-not-a-list', GRAPH_VERB,
     '        if (!(series instanceof List<?> list)) {\n            return "series is a list',
     '        if (!(series instanceof List<?> list)) {\n            if (true) return null;\n            return "series is a list',
     ECHO_TEST + 'aSeriesThatIsNotAListIsRefused'),
    ('style-echo', GRAPH_VERB, '            applied.put("style", panel.styleName());', '',
     ECHO_TEST + 'theEchoReportsTheStyleTheChartHas'),
    ('saved-graphs-style', SESSION_FACTS, '            m.put("style", g.style());', '',
     ECHO_TEST + 'savedGraphsShowTheKeyAsSentAndTheStyle'),
    ('saved-graphs-key', SESSION_FACTS, 'map(SessionFacts::displayKey)', 'map(s -> s)',
     ECHO_TEST + 'savedGraphsShowTheKeyAsSentAndTheStyle'),
]


def display_classes(root=Path('.')):
    ci = (root / '.github/workflows/ci.yml').read_text()
    names = re.search(r"-Dtest='([^']+)'", ci).group(1).split(',')
    guard = re.search(r'for c in (.*?); do', ci).group(1).split()
    assert names == guard, 'CI execution and guard lists differ'
    found = {p.stem for p in (root / 'src/test/java').rglob('*FrameTest.java')}
    assert len(names) == len(set(names)) and set(names) == found, (
        'CI frame suites differ from source', sorted(found - set(names)), sorted(set(names) - found))
    return names


def selected_cases(requested, root=Path('.')):
    known = {c[0] for c in CASES}
    assert not set(requested or []) - known, ('unknown mutation', requested)
    cases = [c for c in CASES if not requested or c[0] in requested]
    assert cases, 'no mutation selected'
    for name, site, old, new, test in cases:
        count = (root / site).read_text().count(old)
        assert count == 1, ('mutation anchor', name, count)
    return cases


def run(names):
    command = ['mvn', '-q', 'test', '-Dtest=' + names, '-Djava.awt.headless=false']
    classes = list(dict.fromkeys(n.split('#')[0] for n in names.split(',')))
    for cls in classes:
        for report in Path('target/surefire-reports').glob('TEST-*.' + cls + '.xml'):
            report.unlink()
    proc = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    suites = []
    for cls in classes:
        paths = list(Path('target/surefire-reports').glob('TEST-*.' + cls + '.xml'))
        assert len(paths) == 1, 'missing suite ' + cls
        root = ET.parse(paths[0]).getroot()
        suites.append({'name': cls, **{k: int(root.get(k, '0')) for k in ['tests','failures','errors','skipped']},
            'testNames': [t.get('name') for t in root.findall('testcase')],
            'assertions': [{'test': t.get('name'), 'kind': e.tag, 'message': e.get('message')}
                for t in root.findall('testcase') for e in list(t) if e.tag in ['failure', 'error']]})
    return {'command': command, 'exit': proc.returncode, 'suites': suites, 'output': proc.stdout}


def green(result):
    return result['exit'] == 0 and bool(result['suites']) and all(s['tests'] > 0 and s['skipped'] == s['failures'] == s['errors'] == 0
        for s in result['suites'])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--mode', choices=['preflight', 'display', 'mutations'], required=True)
    parser.add_argument('--case', action='append')
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'mode': args.mode, 'runs': []}
    def save():
        output.write_text(json.dumps(result, indent=2) + '\n')
    if args.mode in ('preflight', 'display'):
        names = display_classes()
        if args.mode == 'preflight':
            cases = selected_cases(args.case)
            result.update({'frameSuites': names, 'anchors': [c[0] for c in cases]})
            save()
            print('preflight:', len(names), 'frame suites;', len(cases), 'anchors')
            return
        r = run(','.join(names))
        result['runs'].append(r)
        save()
        assert green(r), r['output']
        print('display:', sum(s['tests'] for s in r['suites']), 'tests, zero failures/errors/skips', flush=True)
        return
    cases = selected_cases(args.case)  # ALL anchors before the shared baseline or any mutation
    baseline = run(','.join(dict.fromkeys(c[4].split('#')[0] for c in cases)))
    result['baseline'] = baseline
    save()
    assert green(baseline), baseline['output']
    for name, site, old, new, names in cases:
        cls, test = names.split('#')
        assert any(s['name'] == cls and test in s['testNames'] for s in baseline['suites']), ('missing baseline test', names)
        path = Path(site)
        original = path.read_bytes()
        text = original.decode()
        assert text.count(old) == 1, ('anchor changed since preflight', name)
        entry = {'name': name, 'site': site, 'sha256': hashlib.sha256(original).hexdigest(), 'baselineGreen': True}
        try:
            path.write_text(text.replace(old, new))
            bad = run(names)
            entry['mutated'] = bad
            assert bad['exit'] != 0 and any(a['test'] == test and a['kind'] == 'failure'
                for s in bad['suites'] for a in s['assertions']), bad
        finally:
            path.write_bytes(original)
            entry['restoredByteIdentical'] = path.read_bytes() == original
            entry['restored'] = run(names)
            result['runs'].append(entry)
            save()
        assert green(entry['restored']), entry['restored']['output']
        print(name, ': green / named assertion red / restored green, bytes identical', flush=True)


if __name__ == '__main__':
    main()
