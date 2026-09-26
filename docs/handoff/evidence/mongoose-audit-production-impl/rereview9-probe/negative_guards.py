"""Ninth-review probes: three negative guards, one premise mutation, and one bound-rule challenge.
Only the named tests run. Source is always restored from its original byte copy, including on failure.
Run from repository root, with JDK 21 on PATH. This is a review harness, not production test infrastructure.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

SOURCE = Path('src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java')
REPORTS = Path('target/surefire-reports')
A = 'CoveragePerNodeLevelTest#aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim'
B = 'ControlAddressAndScopeTest#notEstablishedAndSpanningABoundaryConditionsBothHalves'
C = 'ControlAddressAndScopeTest#notEstablishedAndWhollyAfterABoundaryConcludesOnBothPremises'
M = 'ControlAddressAndScopeTest#noBranchOfTheSentencePresumesAProcessor'
END = 'return s.append(". It is still counted as uncovered, because a level change is not proof the node ran")'
CASES = [
 ('guard-definite-in-later-or-unknown', END,
  'return s.append(", so after record 1, this is a planted definite conclusion").append(". It is still counted as uncovered, because a level change is not proof the node ran")',
  [A, B], ['RR-4: no definite suppression claim', 'S2: nothing is definite']),
 ('guard-survival-alone', END,
  'return s.append(". If it survived the marker, then this is a planted incomplete condition").append(". It is still counted as uncovered, because a level change is not proof the node ran")',
  [C], ['S2: survival alone']),
 ('matrix-holds-drops-condition', 'return condition == null ? "It holds" : "If " + condition + ", it holds";',
  'return "It holds";', [M], ['R-C/R5/R6/R7:']),
 ('matrix-wrong-second-marker-number', '"the stream-end marker preceding record " + (m2 + 1) : null;',
  '"the stream-end marker preceding record " + (m2 + 2) : null;', [M], []),
]

def run(tests):
    for f in REPORTS.glob('TEST-*.xml'): f.unlink()
    result = subprocess.run(['mvn','-q','-o','-Dtest='+','.join(tests),'test'],capture_output=True,text=True,timeout=180)
    cases = {}
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for f in REPORTS.glob('TEST-*.xml'):
        suite = ET.parse(f).getroot()
        for k in counts: counts[k] += int(suite.get(k,0))
        for tc in suite.iter('testcase'):
            key = tc.get('classname').split('.')[-1]+'#'+tc.get('name').split('(')[0]
            failure, error, skip = tc.find('failure'), tc.find('error'), tc.find('skipped')
            kind = 'failure' if failure is not None else 'error' if error is not None else 'skip' if skip is not None else 'pass'
            message = (failure if failure is not None else error)
            cases[key] = (kind, '' if message is None else message.get('message',''))
    return result.returncode, counts, cases

def green(result, tests):
    rc, counts, cases = result
    return rc == 0 and counts['tests'] == len(tests) and all(cases.get(t,('missing',))[0]=='pass' for t in tests)

original = SOURCE.read_bytes()
original_hash = hashlib.sha256(original).hexdigest()
for name, old, new, tests, prefixes in CASES:
    baseline = run(tests)
    assert green(baseline,tests), (name, 'baseline', baseline)
    text = original.decode()
    assert text.count(old)==1, (name,'anchor',text.count(old))
    try:
        SOURCE.write_text(text.replace(old,new))
        mutated = run(tests)
    finally:
        SOURCE.write_bytes(original)
    restored = run(tests)
    identical = hashlib.sha256(SOURCE.read_bytes()).hexdigest()==original_hash
    expected = all(mutated[2].get(t,('missing',''))[0]=='failure' and mutated[2][t][1].startswith(prefix)
                   for t,prefix in zip(tests,prefixes)) if prefixes else green(mutated,tests)
    assert identical and green(restored,tests), (name, 'restore', restored)
    print(json.dumps(dict(case=name, baseline=baseline[1], mutated=mutated[1],
        named=[dict(test=t,kind=mutated[2].get(t,('missing',''))[0],message=mutated[2].get(t,('',''))[1][:220]) for t in tests],
        expectedObservation=expected, restored=restored[1], sourceByteIdentical=identical)),flush=True)
    assert expected,(name,'unexpected observation')
assert subprocess.check_output(['git','diff','--','src'])==b''
