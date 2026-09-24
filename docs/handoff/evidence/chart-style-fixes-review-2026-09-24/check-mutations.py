#!/usr/bin/env python3
"""Run only in a disposable checkout of 90746e83, after mvn test/package and compiling the probes.
JAVA_HOME must select JDK 21. Pass the directory containing compiled StyleContractProbe.class.
Every mutation restores source bytes; no git reset and no changes to committed evidence.
"""
from pathlib import Path
import hashlib, json, os, subprocess, sys
import xml.etree.ElementTree as ET
root = Path.cwd()
evidence = Path(__file__).resolve().parent
out = evidence / 'mutation-results.json'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
cp = sys.argv[1] + ':target/classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar'
base = Path('src/main/java/telamin/fluxtion/audit/analyser/analyser')
records = []
def command(args):
    p = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    result = {'command': args, 'exit': p.returncode, 'output': p.stdout}
    if args[0] == 'mvn':
        result['suites'] = []
        for path in Path('target/surefire-reports').glob('TEST-*StyleDropdownRequestsASaveTest.xml'):
            t = ET.parse(path).getroot()
            result['suites'].append({**t.attrib, 'failedAssertions': [
                {'test': c.attrib['name'], 'message': f.attrib.get('message')}
                for c in t.findall('testcase') for f in c.findall('failure')]})
    return result
mvn = ['mvn', '-q', 'test', '-Dtest=StyleDropdownRequestsASaveTest']
probe = [java, '-Djava.awt.headless=true', '-cp', cp, 'StyleContractProbe', 'paths-only']
baseline = command(mvn)
assert baseline['exit'] == 0, baseline
cases = [
 ('dropdown-notification', base/'ui/GraphPanel.java',
  '            mutated();\n        });\n        zoomIn.addActionListener',
  '        });\n        zoomIn.addActionListener', True),
 ('import-callsite-old-constructor', base/'config/SettingsShare.java',
  'graphs.set(gi, spec.withExternal(fixed, fixedMarkers));',
  'graphs.set(gi, new GraphSpec(spec.name(), spec.series(), spec.exprs(), spec.from(), spec.to(), spec.note(), spec.explanation(), spec.notes(), spec.rightAxis(), spec.guides(), spec.bands(), fixed, fixedMarkers));', False)
]
for name, path, old, new, expect_red in cases:
    original = path.read_bytes()
    text = original.decode()
    assert text.count(old) == 1, name
    record = {'name': name, 'sourceSite': str(path), 'originalSha256': hashlib.sha256(original).hexdigest(), 'baselineGreen': baseline['exit'] == 0}
    try:
        path.write_text(text.replace(old,new))
        record['committedTest'] = command(mvn)
        assert (record['committedTest']['exit'] != 0) == expect_red, record
        if not expect_red:
            record['reviewProbe'] = command(probe)
            assert record['reviewProbe']['exit'] != 0 and 'PROFILE_METADATA' in record['reviewProbe']['output'], record
    finally:
        path.write_bytes(original)
        record['restoredSha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        record['byteIdentical'] = path.read_bytes() == original
        record['restoredTest'] = command(mvn)
        record['restoredProbe'] = command(probe)
        records.append(record)
        out.write_text(json.dumps({'baseline':baseline,'mutations':records},indent=2)+'\n')
    assert record['restoredTest']['exit'] == 0 and record['restoredProbe']['exit'] == 0, record
    print(name, 'committed test exit', record['committedTest']['exit'], 'restored green, bytes identical', flush=True)
