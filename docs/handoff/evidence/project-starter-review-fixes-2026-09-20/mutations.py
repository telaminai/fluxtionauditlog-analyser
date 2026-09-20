"""Run from analyser root, on a display, in a disposable checkout. Restores each file in finally."""
from pathlib import Path
import json, os, subprocess, tempfile
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
NODE = Path('src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/SessionRecovery.java')
FRAME = Path('src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java')
CASES = [
    ('JI-4', FRAME, '            supersedeRecoveryLog(opId);', '',
     'SessionRecoveryFrameTest#supersededRecoveryCompletionLeavesTheOfferDecidable'),
    ('JI-2', NODE, 'boolean applying = "restoring".equals(state) && e.operationId() != Long.MIN_VALUE;',
     'boolean applying = false;', 'SessionRecoveryTest#recheckWithdrawsTheWholeSetAndCannotResurrectAnOmittedMember'),
    ('JI-3', NODE, 'if (e.generation() != generation) {', 'if (false) {',
     'SessionRecoveryTest#staleAcceptanceAndDismissalCannotAnswerANewerOffer'),
]
results = []
for issue, path, old, new, test in CASES:
    original = path.read_bytes()
    try:
        assert old in original.decode(), issue
        path.write_text(original.decode().replace(old, new))
        report = ROOT / 'target/surefire-reports'
        for stale in report.glob('TEST-*SessionRecovery*.xml'): stale.unlink()
        run = subprocess.run(['mvn','-q','-o','-Dtest='+test,'-Djava.awt.headless=false',
                              '-DargLine=-Djava.awt.headless=false','test'], capture_output=True,text=True)
        (Path(tempfile.gettempdir()) / (issue+'-mutation.log')).write_text(run.stdout+run.stderr)
        failures=[]
        for xml in report.glob('TEST-*SessionRecovery*.xml'):
            for case in ET.parse(xml).getroot().iter('testcase'):
                failure = case.find('failure')
                if failure is not None:
                    assert case.attrib['name'].startswith(test.split('#')[1]), case.attrib
                    failures.append({'test':case.attrib['name'], 'type':failure.attrib['type']})
        assert run.returncode != 0 and failures, issue+' did not fail the intended assertion'
        results.append({'finding':issue,'mutation':old+' -> '+new,'failures':failures})
        print(issue+': intended regression seen red',flush=True)
    finally: path.write_bytes(original)
Path('docs/handoff/evidence/project-starter-review-fixes-2026-09-20/mutations.json').write_text(json.dumps(results,indent=2)+'\n')
