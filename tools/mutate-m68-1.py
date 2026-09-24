#!/usr/bin/env python3
"""M68.1 regression closure: each of the four corrections must be load-bearing ON ITS OWN.

Applies one mutation at a time to the source, runs the three M68.1 suites, records which tests go red, and
restores the file — always, including on failure. A mutation that leaves the suite green means the suite does not
guard that correction, which is the failure mode this repository keeps meeting: an instrument that passes while no
longer testing what it claims.

    python3 tools/mutate-m68-1.py

Result on 2026-09-24: all four RED — ignore declared authorship (4 tests), authored-only membership (3), membership
derived from the ratio (2), retention reaching the downstream claim (1).
"""
import sys, subprocess, shutil, re, glob, os
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B='src/main/java/telamin/fluxtion/audit/analyser/analyser/'
MUT = {
 'M1 ignore declared authorship': (B+'topology/Scaffolding.java',
   'if (nodeScoped(node) && vocabulary != null && vocabulary.trustedForNodeFacts()) {',
   'if (false && nodeScoped(node) && vocabulary != null && vocabulary.trustedForNodeFacts()) {'),
 'M2 restore authored-only membership': (B+'topology/CoverageService.java',
   'Set<String> outOfTopology = membership.unknownToTopology();',
   'Set<String> outOfTopology = new LinkedHashSet<>(logged); outOfTopology.removeAll(scope.loggable()); outOfTopology.removeAll(scope.excluded().keySet());'),
 'M3 derive membership from the ratio': (B+'topology/CoverageService.java',
   'member.put("established", !logged.isEmpty());',
   'member.put("established", ratioAvailable);'),
 'M4 retention reaches a downstream claim': (B+'session/CoveragePolicy.java',
   'if (pairing != null && !pairing.evidenced()) {',
   'if (false && pairing != null && !pairing.evidenced()) {'),
}
TESTS='EvidenceIntegrityCoverageTest,CoveragePolicyEvidenceTest,EntryPointAuthorshipTest'
env=dict(os.environ)   # uses your JAVA_HOME; Java 21 required
for name,(f,old,new) in MUT.items():
    path=os.path.join(ROOT,f); orig=open(path).read()
    assert orig.count(old)==1, (name, 'anchor not unique')
    open(path,'w').write(orig.replace(old,new,1))
    try:
        for r in glob.glob(ROOT+'/target/surefire-reports/*'): os.remove(r)
        subprocess.run(['mvn','-q','-o','-Dtest='+TESTS,'-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=ROOT,env=env,capture_output=True)
        failed=[]
        for rep in glob.glob(ROOT+'/target/surefire-reports/TEST-*.xml'):
            x=open(rep).read()
            for m in re.finditer(r'<testcase name="([^"]+)" classname="[^"]*\.([A-Za-z]+)"[^>]*?(/>|>(.*?)</testcase>)', x, re.S):
                body=m.group(4) or ''
                if '<failure' in body or '<error' in body: failed.append(m.group(2)+'.'+m.group(1))
        print(f"{name}: {'RED' if failed else 'STILL GREEN — the suite does not guard this'} ({len(failed)} failing)")
        for t in failed: print('    -', t)
    finally:
        open(path,'w').write(orig)
