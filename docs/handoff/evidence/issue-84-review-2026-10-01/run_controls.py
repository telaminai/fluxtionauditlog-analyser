#!/usr/bin/env python3
"""Small, serial reviewer controls; every mutation restores source/classes from byte copies.
Run in your own v1.30.0/v1.30.1 worktree, JDK21, under the shared display lock.
No production fixes or regenerated code. Do not run concurrently with other gates in this worktree.
"""
import json, sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/"tools"))
from mutation_gate_fast import FastEngine
from verify_project_chart_review import CASES, green, caught
TARGET="ProjectPanelIsRevealOnlyTest#thePanelNeverNamesMainFrame_itsOnlyExitIsTheTwoMethodNavigator"
SITE="src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProjectPanel.java"
ANCHOR="public final class ProjectPanel extends JPanel {"
custom=[
 ("review-panel-direct-frame",SITE,ANCHOR,ANCHOR+"\n    private MainFrame forbiddenFrame;",TARGET),
 ("review-navigator-list-closed",SITE,"    public interface Navigator {","    public interface Navigator {\n        default void discardEverything() { }",TARGET),
 ("review-panel-inner-frame",SITE,ANCHOR,ANCHOR+"\n    private Object forbiddenBridge() { return FrameBridge.make(); }\n    private static class FrameBridge { static Object make() { return new MainFrame(); } }",TARGET),
 ("review-navigator-inherited-action",SITE,"    public interface Navigator {","    private interface HiddenActions { default void runAnything(Runnable effect) { effect.run(); } }\n    public interface Navigator extends HiddenActions {",TARGET),
]
selected=[c for c in CASES if c[0] in ["walk-can-point-at-java","bundle-anchor-forgotten-when-deleted","bundle-anchor-restored","bundle-provenance-matches-the-applied-profile"]]
if "--bundle-only" in sys.argv:selected=[c for c in selected if c[0]!="walk-can-point-at-java"]
cases=custom+selected
engine=FastEngine();engine.prepare()
baseline=engine.run(list(dict.fromkeys(c[4] for c in cases)))
assert green(baseline),baseline
report={"mode":"RAN","requested":[c[0] for c in cases],"baseline":baseline,"controls":[]}
for case in cases:
 assert Path(case[1]).read_text().count(case[2])==1,case[0]
 entry={"name":case[0],"test":case[4],"site":case[1],"old":case[2],"new":case[3]}
 mutated,restored=engine.control(case,entry)
 entry.update(mutated=mutated,restored=restored,caught=caught(mutated,case[4].split("#",1)[1]))
 report["controls"].append(entry)
 Path("target/issue84-controls.json").write_text(json.dumps(report,indent=2))
 assert green(restored),entry
 assert entry["restoredByteIdentical"] and entry["classesRestoredByteIdentical"],entry
 print(case[0]+": "+("CAUGHT" if entry["caught"] else "SURVIVED")+"; restored GREEN",flush=True)
 # A deliberately planted guard bypass must not prevent later requested controls from running.
report["caught"]=[c["name"] for c in report["controls"] if c["caught"]]
report["survived"]=[c["name"] for c in report["controls"] if not c["caught"]]
Path("target/issue84-controls.json").write_text(json.dumps(report,indent=2))
print(json.dumps({"requested":report["requested"],"caught":report["caught"],"survived":report["survived"]},indent=2))
