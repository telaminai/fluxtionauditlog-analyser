#!/usr/bin/env python3
"""Display and mutation gates for the pinned Project/chart review. Run in a disposable worktree.
JAVA_HOME selects JDK21. Requires a real display; preserves source bytes even on failure.
Writes JSON evidence at the path supplied by --output. No git restore/reset is used.
"""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--mode',choices=['display','mutations'],required=True);p.add_argument('--case',action='append');args=p.parse_args()
output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
result={'mode':args.mode,'runs':[]}
def run(names):
    cmd=['mvn','-q','test','-Dtest='+names,'-Djava.awt.headless=false','-DargLine=-Djava.awt.headless=false']
    for name in names.split(','):
        for report in Path('target/surefire-reports').glob('TEST-*.'+name.split('#')[0]+'.xml'): report.unlink()
    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    suites=[]
    for name in names.split(','):
        cls=name.split('#')[0];paths=list(Path('target/surefire-reports').glob('TEST-*.'+cls+'.xml'))
        if len(paths)!=1:raise AssertionError('missing suite '+cls)
        root=ET.parse(paths[0]).getroot()
        suites.append({'name':cls,**{k:int(root.get(k,'0')) for k in ['tests','failures','errors','skipped']},
            'assertions':[{'test':t.get('name'),'kind':e.tag,'message':e.get('message')} for t in root.findall('testcase') for e in list(t) if e.tag in ['failure','error']]})
    return {'command':cmd,'exit':proc.returncode,'suites':suites,'output':proc.stdout}
def green(r):
    return r['exit']==0 and all(s['tests']>0 and s['skipped']==s['failures']==s['errors']==0 for s in r['suites'])
def save():output.write_text(json.dumps(result,indent=2)+'\n')
if args.mode=='display':
    ci=Path('.github/workflows/ci.yml').read_text();names=re.search(r"-Dtest='([^']+)'",ci).group(1)
    guard=re.search(r'for c in (.*?); do',ci).group(1).split()
    assert names.split(',')==guard,'CI execution and guard lists differ'
    r=run(names);result['runs'].append(r);save();assert green(r),r['output']
    print('display:',sum(s['tests'] for s in r['suites']),'tests, zero failures/errors/skips',flush=True)
else:
    cases=[
        ('explicit-name', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', '        if (name != null && !name.isBlank()\n                && (graphNamed(name.trim()) != null || (!restoring && hasDefinition(name)))) return null;', '', 'ChartLifecycleReviewFrameTest#closedNamesAreReservedForCreationAndRename'),
        ('closed-definition', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', 'if (java.util.Objects.equals(saved.name(), target) && openSaved(saved))', 'if (false && java.util.Objects.equals(saved.name(), target) && openSaved(saved))', 'ChartLifecycleReviewFrameTest#graphActionOnClosedChartPreservesItsDefinition'),
        ('import-before-view', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java', 'if (store != null) graphTabs.restore(List.copyOf(config.savedGraphs));', 'onConfigChanged();\n        if (store != null) graphTabs.restore(List.copyOf(config.savedGraphs));', 'ChartLifecycleReviewFrameTest#importingOverAnOpenChartKeepsIncomingDefinitionAndOpenState'),
        ('stale-confirmation', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', 'if (current >= 0 && name.equals(gp.graphName())) deleteConfirmed(current);', 'deleteConfirmed(i);', 'ChartLifecycleReviewFrameTest#confirmationCannotDeleteAReplacementTab'),
        ('external-import', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java', 'spec.withExternal(fixed, fixedMarkers)', 'new GraphSpec(spec.name(), spec.series(), spec.exprs(), spec.from(), spec.to(), spec.note(), spec.explanation(), spec.notes(), spec.rightAxis(), spec.guides(), spec.bands(), fixed, fixedMarkers)', 'GraphProfileMetadataTest#externalPathsKeepStyleAndClosedState'),
        ('duplicate-profile', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java', '            SavedGraphMerge.requireUniqueNames(graphs);', '', 'GraphProfileMetadataTest#duplicateNamesRefuseWithoutChangingProfileOrTarget'),
        ('duplicate-saved', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java', '        requireUniqueNames(saved);', '', 'SavedGraphMergeTest#aDuplicateNameInTheProfileIsRefusedInsteadOfDiscarded'),
        ('duplicate-tabs', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java', '        requireUniqueNames(openTabs);', '', 'GraphProfileMetadataTest#mergeRefusesAmbiguousOpenTabsWithoutChoosingAWinner'),
        ('merge-preservation', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SavedGraphMerge.java', 'merged.add(live != null ? live.withOpen(true) : existing.withOpen(false));', 'if (live != null) merged.add(live.withOpen(true));', 'SavedGraphMergeTest#aChartThatIsNoLongerATabIsKeptAndMarkedClosed'),
        ('reserved-names', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', 'if (known != null) taken.addAll(known);', '', 'ChartNamesCannotCollideTest#aGeneratedNameSkipsOneAClosedChartStillHolds'),
        ('restore-closed', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', '            if (!g.open()) continue;', '', 'ChartNamesCannotCollideTest#restoreReopensOpenChartsAndLeavesClosedOnesClosed'),
        ('reopen-notifies', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', '        if (opened) fireChanged();', '', 'ChartNamesCannotCollideTest#reopeningASavedChartAsksToBeSaved'),
        ('report-adapter', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java', 'if (name != null && reportsPanel != null) reportsPanel.select(name);', '', 'ChartLifecycleReviewFrameTest#alternatingReportRowsReachesTheRealReportsPanelByIdentity'),
        ('chart-adapter', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java', 'graphTabs.openSaved(g); return;', 'return;', 'ChartLifecycleReviewFrameTest#rowReopenPersistsOpenStateWithoutAnotherEdit'),
        ('rename-modal', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java', '        if (takenNames().contains(to)) return false;', '        if (takenNames().contains(to)) { JOptionPane.showMessageDialog(this, \"Name already used\"); return false; }', 'ChartLifecycleReviewFrameTest#closedNamesAreReservedForCreationAndRename'),
        ('style-dropdown', 'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphPanel.java', '            mutated();\n        });\n        zoomIn', '        });\n        zoomIn', 'StyleDropdownRequestsASaveTest#choosingAStyleFromTheDropdownAsksToBeSaved'),
    ]
    for name,site,old,new,names in cases:
        if args.case and name not in args.case: continue
        path=Path(site);test=names.split('#')[1];baseline=run(names);assert green(baseline),baseline['output']
        original=path.read_bytes();text=original.decode();assert text.count(old)==1,(name,text.count(old))
        entry={'name':name,'site':site,'sha256':hashlib.sha256(original).hexdigest(),'baseline':baseline}
        try:
            path.write_text(text.replace(old,new));bad=run(names);entry['mutated']=bad
            assert bad['exit']!=0 and any(a['test']==test and a['kind']=='failure' for s in bad['suites'] for a in s['assertions']),bad
        finally:
            path.write_bytes(original);entry['restoredByteIdentical']=path.read_bytes()==original
            entry['restored']=run(names);result['runs'].append(entry);save()
        assert green(entry['restored']),entry['restored']['output']
        print(name,': green / named assertion red / restored green, bytes identical',flush=True)
