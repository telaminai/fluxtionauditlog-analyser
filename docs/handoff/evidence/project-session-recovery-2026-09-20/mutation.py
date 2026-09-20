from pathlib import Path
import os, subprocess, sys
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[4]
p=root/'src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java'
original=p.read_text()
needle='                    && capturedLogs.equals(plan.snapshot().view().get("loadedLogHashes"))\n'
assert original.count(needle)==1
try:
    p.write_text(original.replace(needle,''))
    env=os.environ.copy() # Run with Java 21 (JAVA_HOME).
    with Path('/private/tmp/journey-restore-mutation.log').open('w') as out:
        result=subprocess.run(['mvn','-q','-o','-Dtest=SessionRecoveryFrameTest#fileEditedBeforeCloseCannotAcquireTheOldViewsIdentity','-Djava.awt.headless=false','-DargLine=-Djava.awt.headless=false','test'],cwd=root,env=env,stdout=out,stderr=subprocess.STDOUT)
    log=Path('/private/tmp/journey-restore-mutation.log').read_text()
    test = root/'src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/SessionRecoveryFrameTest.java'
    line = next(i for i,t in enumerate(test.read_text().splitlines(),1) if 'Saved view withheld' in t)
    assert result.returncode != 0 and 'Failures: 1' in log and f'SessionRecoveryFrameTest.java:{line}' in log, 'mutation did not fail the intended assertion'
    print('SEEN RED: removing the loaded-view identity guard fails the before-close mutation case')
finally:
    p.write_text(original)
    print('Original source restored')
