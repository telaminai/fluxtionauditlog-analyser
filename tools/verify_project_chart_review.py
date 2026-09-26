#!/usr/bin/env python3
"""Display and mutation gates. Use an isolated worktree and JDK21 with a real display.
Preflight checks all anchors before any Maven run. One shared clean baseline establishes
all selected methods; each mutation must fail its named assertion and restore green.

Engines (--mode mutations): `--engine maven` runs a Maven lifecycle per run (the original);
`--engine fast` compiles once, then per control javac's only the mutated file and runs the named
test in a fresh JVM (tools/mutation_gate_fast.py). `--mode compare` runs both over every control
plus PLANTED survivors and requires identical verdicts. `--changed-since REF` runs only the
controls a branch diff can affect and prints every skip. `--mode selftest` checks the fast
engine's constant/signature fallback detection.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
from pathlib import Path
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mutation_gate_fast as fast  # noqa: E402  (the fast engine and branch-subset selection)

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
 ('dialog-unanswered-row',
  'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/DuplicateChartRepair.java',
  '            default -> null;',
  '            default -> new Choice(Action.DELETE, null);',
  'DuplicateChartRepairTest#anUnansweredRowProducesNoChoiceAtAll'),
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


# Resource-menu controls share the existing baseline / named-failure / byte-restore protocol.
CASES.extend([
    ('menu-layout', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     'JMenu projectMenu = new JMenu("Project");', 'JMenu projectMenu = new JMenu("File");',
     'MenuLayoutFrameTest#projectSourcesAndAuditHaveTheirOwnActions'),
    ('menu-source-page', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     'this::readerSummaries, page));', 'this::readerSummaries));',
     'MenuLayoutFrameTest#sourceShortcutsOpenTheNamedSettingsPage'),
    ('menu-close-log', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '            closeLog(); });', '            /* drop the human close action */ });',
     'MenuLayoutFrameTest#closeLogFromItsMenuPreservesTheProjectAndSavedChart'),
])


CASES.extend([
    ('menu-exit', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '        projectMenu.add(exit);', '', 'MenuLayoutFrameTest#projectSourcesAndAuditHaveTheirOwnActions'),
    ('menu-close-graph-item', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '        sources.add(closeGraphItem);', '', 'MenuLayoutFrameTest#projectSourcesAndAuditHaveTheirOwnActions'),
    ('menu-reset-item', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '        projectMenu.add(resetItem);', '', 'MenuLayoutFrameTest#projectSourcesAndAuditHaveTheirOwnActions'),
    ('menu-recent-log', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '        audit.add(recentMenu);', '', 'MenuLayoutFrameTest#projectSourcesAndAuditHaveTheirOwnActions'),
    ('menu-reset-human', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     'resetItem.addActionListener(e -> { sessionInteractive = true;', 'resetItem.addActionListener(e -> {',
     'MenuLayoutFrameTest#closeBothFromItsMenuKeepsProjectAndCharts'),
    ('menu-reset-topology', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     '            resetAll(); });', '            closeLog(); });',
     'MenuLayoutFrameTest#closeBothFromItsMenuKeepsProjectAndCharts'),
    ('menu-help-s3', 'src/main/resources/help/help.html',
     'Audit log → Open log from S3…', 'Audit log → Open from S3…',
     'MenuDocumentationTest#documentedPathsNameExistingItems'),
])


# Optional PR 15 follow-ups: only these four plus menu-help-s3 form the requested witness run.
CASES.extend([
    ('menu-doc-ai', 'docs/site/faq.md',
     'AI ▸ Fluxtion API key…', 'AI ▸ Nonexistent item…',
     'MenuDocumentationTest#documentedPathsNameExistingItems'),
    ('menu-java-path', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProjectModel.java',
     'Audit log ▸ Open log…', 'Audit log ▸ Open',
     'MenuDocumentationTest#documentedPathsNameExistingItems'),
    ('menu-close-log-human', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     'closeLogItem.addActionListener(e -> { sessionInteractive = true;', 'closeLogItem.addActionListener(e -> {',
     'MenuLayoutFrameTest#closeLogFromItsMenuPreservesTheProjectAndSavedChart'),
    ('menu-close-graph-human', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java',
     'closeGraphItem.addActionListener(e -> { sessionInteractive = true;', 'closeGraphItem.addActionListener(e -> {',
     'MenuLayoutFrameTest#closeGraphFromItsMenuKeepsLogProjectAndCharts'),
])

# Menu discoverability after the 1.20.0 reorganisation: a miss says where the item went; context lists the menus.
MENU_HINTS = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MenuHints.java'
MAIN_FRAME = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java'
SOURCE_PANEL = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SourcePanel.java'
SOURCE_SERVICE = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/source/SourceService.java'
TEMPLATE_ARCHIVE = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/template/TemplateArchive.java'
SESSION_RECOVERY = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/SessionRecovery.java'
RECOVERY_CONTROLLER = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SessionRecoveryController.java'
PROJECT_PROFILE = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/ProjectProfile.java'
TEMPLATE_ROOTS = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/TemplateRoots.java'
CASES += [
    ('menu-hint-renamed', MENU_HINTS, 'List.of("Reset", "Reset (close log + graph)")', 'List.of()',
     'MenuHintsTest#theRenamedResetPointsAtItsNewName_whateverSpellingWasUsed'),
    # PR #19 review R1: dropping every parenthetical (the first version) turns close-both into close-log
    ('menu-hint-keeps-qualifiers', MENU_HINTS, r'Pattern.compile("\\s*\\(\\w\\)$")', r'Pattern.compile("\\s*\\([^)]*\\)$")',
     'MenuHintsTest#anUnknownQualifierGetsNoIdentityClaim'),
    # PR #19 review O1: without the same-menu step, a spelling miss is sent to another menu's copy
    ('menu-hint-same-menu-first', MENU_HINTS, '            if (!menu.getKey().equalsIgnoreCase(asked)) continue;',
     '            if (true) continue;', 'MenuHintsTest#aSpellingMissInsideTheAskedMenuStaysInThatMenu'),
    ('menu-hint-wired', MAIN_FRAME, '+ menuItemTexts(m) + " (a submenu\'s items cannot be lit)" + whereIsNote(t)',
     '+ menuItemTexts(m) + " (a submenu\'s items cannot be lit)"',
     'NamedGraphAndMenuSpotlightFrameTest#aMenuMissSaysWhereTheItemIs_andContextListsTheMenus'),
    ('context-menus', MAIN_FRAME, '            out.put("menus", menuMap());\n', '',
     'NamedGraphAndMenuSpotlightFrameTest#aMenuMissSaysWhereTheItemIs_andContextListsTheMenus'),
    ('context-menu-changes', MAIN_FRAME, '            out.put("menuChanges", MenuHints.changes(menuMap()));', '',
     'NamedGraphAndMenuSpotlightFrameTest#aMenuMissSaysWhereTheItemIs_andContextListsTheMenus'),
    # edit-loop spec §C: Topology's embedded pane fills itself only on first use; a reopened log must recheck it
    ('source-embedded-refresh', MAIN_FRAME,
     '                    sourcePanel.showSelectedProcessor();\n                    topologyPanel.revalidateEmbeddedSource();',
     '                    sourcePanel.showSelectedProcessor();',
     'SourceFreshnessFrameTest#bothPanesShowTheFileAsItIsAfterTheLogIsReopenedAndOnTheNextNavigation'),
    # §C: node-id navigation after a rename must use the model from the same read, not a cached one
    ('source-service-model', SOURCE_PANEL,
     '        if (Objects.equals(fqn, service.selectedFqn())) service.acceptModel(lookup, fqn, read.model());', '',
     'SourcePanelFreshnessTest#afterAClassRenameNodeNavigationUsesTheProcessorAsItIsNow'),
    # §C: a read that blocks must not block the EDT
    ('source-reads-off-edt', SOURCE_PANEL,
     '        return READS.submit(task);',
     '        task.run(); return java.util.concurrent.CompletableFuture.completedFuture(null);',
     'SourcePanelFreshnessTest#aBlockedReadLeavesTheEdtResponsiveAndASupersededAnswerIsDropped'),
    # §C: an unchanged name is not an unchanged file
    ('source-same-name-reread', SOURCE_PANEL,
     '        load(pane, fqn, instead != null ? instead : () -> {',
     '        if (!newName && !pane.source.isEmpty()) return;\n        load(pane, fqn, instead != null ? instead : () -> {',
     'SourcePanelFreshnessTest#navigatingToTheSameClassAgainShowsItsFileAsItIsNow'),
    # §C: an answer past its deadline (or superseded) is never installed
    ('source-stale-ticket', SOURCE_PANEL,
     '            deadline.stop();\n            if (pane.readTicket != ticket) { decisions.accept("discarded " + fqn + ": superseded or expired"); return; }\n            pane.reading = false;\n            if (service == null',
     '            deadline.stop();\n            pane.reading = false;\n            if (service == null',
     'SourcePanelFreshnessTest#aReadPastItsDeadlineSaysSoAndItsLateAnswerIsIgnored'),
    # PR #30 review 1: two panels share the service; an older read landing last must not replace a newer model
    ('source-model-sequence', SOURCE_SERVICE, '        if (lookup.sequence() < installedSequence) return;\n', '',
     'SourcePanelFreshnessTest#anOlderReadFromTheOtherPaneCannotReplaceANewerModel'),
    # PR #30 review 2: the EDT never reads the processor to learn its model
    ('source-model-no-edt-read', SOURCE_SERVICE,
     '        if (javax.swing.SwingUtilities.isEventDispatchThread()) return Optional.empty();\n', '',
     'SourceServiceTest#onTheEdtAnUnreadModelIsNotReadAndSaysSo'),
    # PR #30 review 3: a Ctrl-click's existence check runs off the EDT
    ('source-type-click-off-edt', SOURCE_PANEL,
     '        pendingTypeCheck = offEdt(() -> check.apply(lookup, fqn), present -> {\n',
     '        Boolean onEdt = check.apply(lookup, fqn);\n        pendingTypeCheck = offEdt(() -> onEdt, present -> {\n',
     'SourcePanelFreshnessTest#aTypeClickChecksExistenceOffTheEdtThenOpensIt'),
    # PR #30 review 4: at the deadline the body stops saying it is reading
    ('source-timeout-body', SOURCE_PANEL,
     '            if (pane.source.isEmpty()) pane.renderPlain("Timed out after " + readDeadline.toMillis() + " ms reading "\n'
     '                    + fqn + "; nothing was read. Navigate to it again to retry.");\n', '',
     'SourcePanelFreshnessTest#aTimedOutReadOfANewNameSaysItTimedOutInTheBodyToo'),
    # PR #30 review 4: hung reads hold a bounded number of threads
    ('source-reads-bounded', SOURCE_PANEL, 'new java.util.concurrent.ThreadPoolExecutor(2, 2,',
     'new java.util.concurrent.ThreadPoolExecutor(64, 64,',
     'SourcePanelFreshnessTest#readsThatHangDoNotAccumulateThreadsWithoutBound'),
    # PR #30 review 5: a node request whose processor read gives up says why
    ('source-node-open-reason', SOURCE_PANEL,
     '                why -> nodePane.label.setText("could not open node \'" + instanceId + "\': " + why));',
     '                why -> { });',
     'SourcePanelFreshnessTest#aNodeRequestWhoseProcessorReadTimesOutSaysWhyItDidNotOpen'),
    # edit-loop spec §G, feedback 8: archive modes are ignored, so the fixed list is what makes generate.sh runnable
    ('installer-authoring-executables', TEMPLATE_ARCHIVE, '"setup.sh", "validate.sh", "generate.sh");',
     '"setup.sh", "validate.sh");', 'TemplateArchiveTest#springAuthoringScriptsAreInstalledRunnableWithoutChmod'),
    # PR #27 review nit 1: only ROOT entries on the list become executable; a basename-only match would
    # make a nested bundle/tools/generate.sh executable
    ('installer-nested-script-not-executable', TEMPLATE_ARCHIVE,
     "if (portable.indexOf('/') == portable.lastIndexOf('/') && POSIX_EXECUTABLES.contains(base)) {",
     'if (POSIX_EXECUTABLES.contains(base)) {',
     'TemplateArchiveTest#archiveExecutableClaimIsIgnoredOutsideTheFixedAllowlist'),
    # edit-loop spec §E: without the profile-identity comparison a project recreated at the same path is offered the old session
    ('recovery-profile-identity', SESSION_RECOVERY, '                && !e.profileIdentity().equals(candidate.profileIdentity())) {',
     '                && false) {', 'SessionRecoveryTest#aSessionCapturedByADifferentProfileAtThisPathIsWithheldNotOffered'),
    # PR #28 review: a missing identity is 'unknown', never 'different profile' and never a match
    ('recovery-identity-unknown', SESSION_RECOVERY,
     '                && (e.profileIdentity() == null || candidate.profileIdentity() == null)) {', '                && false) {',
     'SessionRecoveryTest#aSessionWithoutAProfileIdentityIsWithheldAsCapturedByAnUnknownProfile'),
    # PR #28 review: a withheld offer still discloses its input origin (§E)
    ('recovery-withheld-input-origin', SESSION_RECOVERY, '            out.put("inputs", inputOrigin(withheld));', '',
     'SessionRecoveryTest#aWithheldOfferStillDisclosesItsInputOrigin'),
    # PR #28 review: the capturing identity is taken when the capture is built, not when the queued save runs
    ('recovery-identity-at-capture', RECOVERY_CONTROLLER,
     '            String identity = c.profile() == null ? null : c.profileIdentity();',
     '            String identity = c.profile() == null ? null : telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.nonce(c.profile()).orElse(null);',
     'SessionRecoveryControllerTest#theCapturingIdentityIsTakenWhenTheCaptureIsBuiltNotWhenItIsSaved'),
    # PR #28 review: the identity is a creation nonce, kept by every save of the profile
    ('profile-nonce-kept-by-saves', PROJECT_PROFILE,
     '        String nonce = previous == null ? null : validNonce(previous.getProperty(NONCE_KEY));', '        String nonce = null;',
     'ProjectProfileTest#theCreationNonceIsMintedOnceKeptBySavesAndNeverShared'),
]

# M44.4 and M68 (feat/m44-single-state-session): the mutation witnesses behind that branch's evidence sets, registered
# so they protect the code from now on instead of recording one run. See tools/mutation_controls_session.py.
from mutation_controls_session import CONTROLS as SESSION_CONTROLS  # noqa: E402
assert not {c[0] for c in CASES} & {c[0] for c in SESSION_CONTROLS}, 'a session control reuses a control name'
CASES += SESSION_CONTROLS

# §H feedback 17: context {sections} is a filter over the full payload that keeps each selected verdict's qualification.
CONTEXT_SECTIONS = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/ContextSections.java'
CASES += [
    ('context-projection', CONTEXT_SECTIONS, '                if (selects(key)) {', '                if (!key.isEmpty()) {',
     'ContextSectionsTest#menuOnly_isTheMenusAndTheScope_andNothingElse'),
    ('context-qualification', CONTEXT_SECTIONS, '"producer", List.of("pairing", "topology", "view", "charts"),',
     '"producer", List.of("topology", "view", "charts"),',
     'ContextSectionsTest#aSelectedVerdictCarriesItsBasisAndEveryQualification'),
    # PR #29 review 1: a rolled set's member list travels with the view, whose selection has file-local offsets
    ('context-files-with-view', CONTEXT_SECTIONS, '            "files", List.of("view"));', '            "files", List.of());',
     'ContextSectionsTest#aRolledSetsFilesTravelWithTheView_andProducerFaultsWithTheTopology'),
    # PR #29 review 2: collapsed-framing producer faults qualify the topology cursor's record and row count
    ('context-producer-with-topology', CONTEXT_SECTIONS, '"producer", List.of("pairing", "topology", "view", "charts"),',
     '"producer", List.of("pairing", "view", "charts"),',
     'ContextSectionsTest#aRolledSetsFilesTravelWithTheView_andProducerFaultsWithTheTopology'),
    # PR #29 review 3: a projection without fluxtionKey reads no key file
    ('context-key-file-guard', MAIN_FRAME, '            if (need.test("fluxtionKey")) {', '            if (true) {',
     'ContextSectionsTest#aProjectionWithoutFluxtionKeyReadsNoKeyFile'),
    # edit-loop spec §I1: a template profile may only grant reads inside the project it installs
    ('template-root-install-check', TEMPLATE_ARCHIVE,
     '                    telamin.fluxtion.audit.analyser.analyser.config.TemplateRoots.requireContained(root, settings);\n', '',
     'TemplateRootContainmentTest#aRootThatLeavesTheProjectIsRefused'),
    # §I1: ../<archive-root>/… resolves inside staging and outside the installed project; only this rule refuses it
    ('template-root-leading-parent', TEMPLATE_ROOTS,
     '        if (normal.getName(0).toString().equals("..")) throw refuse(root, "leaves the project");\n', '',
     'TemplateRootContainmentTest#aRootThatReentersThroughTheArchiveRootsOwnNameIsRefused'),
    # §I1 / D3: src/.., . and ./ are the whole project, which containment alone would accept
    ('template-root-whole-project', TEMPLATE_ROOTS,
     '        if (normal.toString().isEmpty()) throw refuse(root, "is the project root itself, which would grant the whole project");\n', '',
     'TemplateRootContainmentTest#aRootThatIsTheWholeProjectIsRefused'),
    # PR #31 review 2: every settings file in the archive is a read grant, not only the root profile
    ('template-root-every-profile', TEMPLATE_ARCHIVE,
     'files.filter(p -> p.getFileName().toString().endsWith(".fluxtion-settings")',
     'files.filter(p -> p.equals(root.resolve(".analyser/project.fluxtion-settings"))',
     'TemplateRootContainmentTest#everyProjectProfileInTheArchiveIsChecked_namedAndNested'),
    # PR #31 review 3: Windows path syntax is refused on every OS
    ('template-root-backslash', TEMPLATE_ROOTS,
     '        if (root.indexOf(\'\\\\\') >= 0) throw refuse(root, "contains a backslash (Windows path syntax)");\n', '',
     'TemplateRootContainmentTest#windowsPathSyntaxIsRefusedOnEveryOs'),
    # PR #31 review 5: a failed log open returns to no log, and the note comes back
    ('no-log-note-after-failed-open', MAIN_FRAME,
     '        if (store == null && topologyPanel.hasGraph()) publishPairing();\n', '',
     'NoLogDesignJourneyFrameTest#designTopologyAndJavaOpenWithNoLogAndClaimNoComparison'),
    # §I1: the design-first tour's first step needs no log — requiring one must fail the no-log journey
    ('no-log-design-open', MAIN_FRAME, '            return openDesign(path, () -> true);\n',
     '            return store == null ? telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error("open a log first") : openDesign(path, () -> true);\n',
     'NoLogDesignJourneyFrameTest#designTopologyAndJavaOpenWithNoLogAndClaimNoComparison'),
    # §I1: with no log, the Topology tab must say the graph was not compared, not stay silent
    ('no-log-pairing-note', MAIN_FRAME,
     '            if (store == null) publishPairing();      // §I1: a graph opened with no log says it was not compared\n', '',
     'NoLogDesignJourneyFrameTest#designTopologyAndJavaOpenWithNoLogAndClaimNoComparison'),
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


# Controls that MUST survive: a mutation no assertion covers. `--mode compare` requires BOTH engines to report
# them as not caught; an engine that cannot report a survivor is not faster, it is broken. The second one adds a
# public constant, which changes the class's API and so exercises the fast engine's full-compile fallback and
# its byte-identical restore of target/classes.
PLANTED = [
    ('plant-survivor-comment',
     'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/DuplicateChartRepair.java',
     'public final class DuplicateChartRepair {',
     'public final class DuplicateChartRepair { // gate plant: changes nothing',
     'DuplicateChartRepairTest#anUnansweredRowProducesNoChoiceAtAll'),
    ('plant-survivor-api-change',
     'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/DuplicateChartRepair.java',
     'public final class DuplicateChartRepair {',
     'public final class DuplicateChartRepair { public static final int GATE_PLANT = 1;',
     'DuplicateChartRepairTest#anUnansweredRowProducesNoChoiceAtAll'),
]


def subset_selftest():
    """Branch-subset rules against the real CASES, on synthetic diffs."""
    cases = selected_cases(None)
    def pick(changed):
        chosen, skipped = fast.select_subset(cases, changed)
        assert len(chosen) + len(skipped) == len(cases), 'every control is either selected or reported skipped'
        return {c[0] for c, _ in chosen}
    graph_verb = 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ActionExecutor.java'
    by_site = {c[0] for c in cases if c[1] == graph_verb}
    helper = 'src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/ChartLifecycleReviewFrameTest.java'
    users = {c[0] for c in cases
             if re.search(r'\bChartLifecycleReviewFrameTest\b',
                          next(fast.TEST_SOURCES.rglob(c[4].split('#')[0] + '.java')).read_text())}
    checks = {
        'the harness itself selects the full set': pick(['tools/verify_project_chart_review.py']) == {c[0] for c in cases},
        'pom.xml selects the full set': pick(['pom.xml']) == {c[0] for c in cases},
        'CI selects the full set': pick(['.github/workflows/ci.yml']) == {c[0] for c in cases},
        'a site file selects its own controls, and not the whole set': (
            by_site <= pick([graph_verb]) and bool(by_site) and pick([graph_verb]) != {c[0] for c in cases}),
        'a production class a target test uses selects that test (GraphSpec)': {
            c[0] for c in cases if c[4].startswith('GraphProfileMetadataTest#')} <= pick(
            ['src/main/java/telamin/fluxtion/audit/analyser/analyser/config/GraphSpec.java']),
        'a resource that is not a site selects the full set': pick(
            ['src/main/resources/llm/system-prompt.md']) == {c[0] for c in cases},
        'a shared test helper selects every control whose test uses it': pick([helper]) == users and len(users) > 1,
        'an unrelated doc selects nothing': pick(['docs/index.md']) == set(),
    }
    return {k: {'ok': v} for k, v in checks.items()}


# PR #18 re-review R1: the fallback must make a changed compile-time fact reach its CONSUMER. Checking the
# detector alone passed while Maven's incremental compile left consumers stale, so these probes drive the whole
# path — FastEngine.control, the fallback, the test run and the restore — in a copy of the build.
PROBE_MAIN = {
    'gateprobe/Mark.java': 'package gateprobe; import java.lang.annotation.*;'
                           ' @Retention(RetentionPolicy.CLASS) public @interface Mark {}\n',
    'gateprobe/Consumer.java': 'package gateprobe; @Mark public class Consumer {}\n',
    'gateprobe/Constant.java': 'package gateprobe; public class Constant { public static final int K = 1; }\n',
    'gateprobe/Reader.java': 'package gateprobe; public class Reader { public static int k() { return Constant.K; } }\n',
}
PROBE_TEST = {
    'gateprobe/GateProbeTest.java':
        'package gateprobe; import org.junit.jupiter.api.*; import static org.junit.jupiter.api.Assertions.*;\n'
        'class GateProbeTest {\n'
        '  @Test void retention() { assertNull(Consumer.class.getAnnotation(Mark.class), "not visible at run time"); }\n'
        '  @Test void constant() { assertEquals(1, Reader.k(), "the constant Reader inlined"); }\n'
        '}\n',
}
PROBE_CASES = [
    ('probe-annotation-retention', 'src/main/java/gateprobe/Mark.java', 'RetentionPolicy.CLASS',
     'RetentionPolicy.RUNTIME', 'GateProbeTest#retention'),
    ('probe-inlined-constant', 'src/main/java/gateprobe/Constant.java', 'K = 1;', 'K = 2;', 'GateProbeTest#constant'),
]


def fallback_endtoend_selftest():
    """Both compile-time dependencies, end to end through the fast engine, in a throwaway copy of the build."""
    import os
    import shutil
    import tempfile
    repo = Path.cwd()
    tmp = Path(tempfile.mkdtemp(prefix='gate-probe-'))
    results = {}
    try:
        work = tmp / 'repo'
        work.mkdir()
        shutil.copy2('pom.xml', work / 'pom.xml')
        for folder in ('src', 'tools'):
            shutil.copytree(folder, work / folder)
        for rel, text in PROBE_MAIN.items():
            (work / 'src/main/java' / rel).parent.mkdir(parents=True, exist_ok=True)
            (work / 'src/main/java' / rel).write_text(text)
        for rel, text in PROBE_TEST.items():
            (work / 'src/test/java' / rel).parent.mkdir(parents=True, exist_ok=True)
            (work / 'src/test/java' / rel).write_text(text)
        os.chdir(work)
        engine = fast.FastEngine()
        engine.prepare()
        baseline, entries = run_gate(PROBE_CASES, engine, fail_fast=False)
        for e in entries:
            results['end to end: ' + e['name'] + ' is caught through the full-compile fallback'] = {
                'baselineGreen': e['baselineGreen'], 'verdict': e['verdict'],
                'fallback': e['fullCompileFallback'], 'classesRestored': e['classesRestoredByteIdentical'],
                'ok': (e['baselineGreen'] and e['verdict'] == 'caught' and e['fullCompileFallback']
                       and e['classesRestoredByteIdentical'] and e['restoredByteIdentical'])}
    finally:
        os.chdir(repo)
        shutil.rmtree(tmp, ignore_errors=True)
    return results


def maven_run_safe(names):
    """The Maven engine's run, but a build that produced no report (e.g. a compile failure) is a result."""
    try:
        return run(names)
    except AssertionError as missing:
        return {'command': ['mvn', 'test', '-Dtest=' + names], 'exit': 1, 'suites': [],
                'output': 'no Surefire report: ' + str(missing)}


def caught(result, test):
    return result['exit'] != 0 and any(fast.same_test(a['test'], test) and a['kind'] == 'failure'
                                       for s in result['suites'] for a in s['assertions'])


def run_gate(cases, engine, fail_fast, on_entry=lambda entry: None, on_baseline=lambda baseline: None):
    """Baseline, then every control. Returns (baseline, entries); each entry carries a verdict.

    `on_entry` is called after every control, so the evidence is on disk before the next one starts: a gate
    that dies half way (PR #18 review, finding 3) still leaves what it established.
    """
    runner = engine.run if engine else (lambda names: maven_run_safe(','.join(names)))
    classes = list(dict.fromkeys(c[4].split('#')[0] for c in cases))
    baseline = runner(classes)
    on_baseline(baseline)
    if fail_fast:
        assert green(baseline), baseline['output']
    entries = []
    for case in cases:
        name, site, old, new, target = case
        cls, test = target.split('#')
        assert any(s['name'] == cls and any(fast.same_test(n, test) for n in s['testNames'])
                   for s in baseline['suites']), ('missing baseline test', target)
        path = Path(site)
        original = path.read_bytes()
        assert original.decode().count(old) == 1, ('anchor changed since preflight', name)
        entry = {'name': name, 'site': site, 'sha256': hashlib.sha256(original).hexdigest(),
                 'baselineGreen': green(baseline), 'engine': 'fast' if engine else 'maven'}
        started = time.monotonic()
        if engine:
            mutated, restored = engine.control(case, entry)
        else:
            try:
                path.write_text(original.decode().replace(old, new))
                mutated = maven_run_safe(target)
            finally:
                path.write_bytes(original)
                entry['restoredByteIdentical'] = path.read_bytes() == original
            restored = maven_run_safe(target)
        entry.update({'mutated': mutated, 'restored': restored, 'seconds': round(time.monotonic() - started, 1)})
        restored_ok = (green(restored) and entry['restoredByteIdentical']
                       and entry.get('classesRestoredByteIdentical', True))
        if not restored_ok:
            entry['verdict'] = 'not-restored'
        elif mutated['output'].startswith('mutated source does not compile') or (
                not engine and not mutated['suites']):
            entry['verdict'] = 'compile-error'
        else:
            entry['verdict'] = 'caught' if caught(mutated, test) else 'survived'
        entries.append(entry)
        on_entry(entry)
        if fail_fast:
            assert entry['verdict'] == 'caught', (name, entry['verdict'], mutated['output'][-2000:])
            print(name, ': green / named assertion red / restored green, bytes identical'
                  + (' [full-compile fallback]' if entry.get('fullCompileFallback') else ''), flush=True)
    return baseline, entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--mode', choices=['preflight', 'display', 'mutations', 'compare', 'selftest'], required=True)
    parser.add_argument('--case', action='append')
    parser.add_argument('--engine', choices=['maven', 'fast'], default='maven',
                        help='mutations mode: maven = a Maven lifecycle per run (the original engine); '
                             'fast = one test-compile, single-file javac, one fresh JVM per run')
    parser.add_argument('--changed-since', metavar='REF',
                        help='mutations mode on a BRANCH: run only the controls the diff against REF can affect, '
                             'and print every control skipped. Never a substitute for the full set.')
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'mode': args.mode, 'runs': []}
    def save():
        output.write_text(json.dumps(result, indent=2) + '\n')
    if args.mode == 'selftest':
        checks = fast.selftest()
        checks.update(subset_selftest())
        engine = fast.FastEngine()
        engine.prepare()
        checks.update(fast.launcher_selftest(engine.cp, engine.launcher_dir))
        checks.update(fallback_endtoend_selftest())
        result['selftest'] = checks
        save()
        for label, r in checks.items():
            print(('ok   ' if r['ok'] else 'FAIL ') + label, r)
        assert all(r['ok'] for r in checks.values()), 'selftest failed'
        return
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
    if args.mode == 'compare':
        planted = [c for c in PLANTED if Path(c[1]).read_text().count(c[2]) == 1]
        assert len(planted) == len(PLANTED), 'a planted control lost its anchor'
        verdicts = {}
        for label, engine in (('maven', None), ('fast', fast.FastEngine())):
            started = time.monotonic()
            if engine:
                engine.prepare()
            partial = result.setdefault(label, {'entries': []})
            def keep(entry, partial=partial):
                partial['entries'].append(entry)
                save()
            baseline, entries = run_gate(cases + planted, engine, fail_fast=False, on_entry=keep)
            seconds = round(time.monotonic() - started, 1)
            result[label] = {'seconds': seconds, 'baselineGreen': green(baseline), 'entries': entries,
                             'fullCompileFallbacks': engine.fallbacks if engine else None}
            verdicts[label] = {e['name']: e['verdict'] for e in entries}
            save()
            print(label, 'engine:', seconds, 's', flush=True)
        rows = []
        for name in verdicts['maven']:
            expected = 'survived' if name.startswith('plant-') else 'caught'
            m, f = verdicts['maven'][name], verdicts['fast'][name]
            rows.append({'name': name, 'maven': m, 'fast': f, 'expected': expected, 'ok': m == f == expected})
            print(('ok   ' if rows[-1]['ok'] else 'DIFF ') + name, 'maven=' + m, 'fast=' + f, 'expected=' + expected)
        result['comparison'] = rows
        save()
        # PR #18 review, finding 4: identical verdicts over RED baselines prove nothing about either engine
        red = [label for label in ('maven', 'fast') if not result[label]['baselineGreen']]
        assert not red, 'baseline not green for: ' + ', '.join(red)
        assert all(r['ok'] for r in rows), 'the engines disagree, or a control or plant has the wrong verdict'
        print('compare: identical verdicts on', len(rows), 'controls; maven', result['maven']['seconds'],
              's, fast', result['fast']['seconds'], 's')
        return
    # mutations
    if args.changed_since:
        chosen, skipped = fast.select_subset(cases, fast.changed_files(args.changed_since))
        result['subset'] = {'since': args.changed_since,
                            'selected': [{'name': c[0], 'why': why} for c, why in chosen],
                            'skipped': [{'name': c[0], 'why': why} for c, why in skipped]}
        save()
        for c, why in chosen:
            print('selected', c[0], '-', why)
        for c, why in skipped:
            print('SKIPPED ', c[0], '-', why)
        print('SUBSET: %d of %d controls will run; %d skipped. A subset is a branch signal, not the gate.'
              % (len(chosen), len(cases), len(skipped)), flush=True)
        cases = [c for c, _ in chosen]
        if not cases:
            return
    engine = fast.FastEngine() if args.engine == 'fast' else None
    started = time.monotonic()
    if engine:
        engine.prepare()
    result.update({'engine': args.engine, 'runs': []})
    def keep(entry):
        result['runs'].append(entry)
        save()
    def keep_baseline(baseline):
        result['baseline'] = baseline
        save()
    try:
        run_gate(cases, engine, fail_fast=True, on_entry=keep, on_baseline=keep_baseline)
    finally:
        result['seconds'] = round(time.monotonic() - started, 1)
        save()
    print('mutations:', len(result['runs']), 'controls caught with the', args.engine, 'engine in', result['seconds'], 's')


if __name__ == '__main__':
    main()
