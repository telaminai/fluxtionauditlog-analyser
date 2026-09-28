#!/usr/bin/env python3
"""DEMO-only review probes. No source edits, network, providers or regeneration.
Build the analyser first; pass --out with a NEW scratch directory. Results are observations,
not a gate that expects the defects to remain. A fixed build should change the documented results.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
REPO = next(p for p in HERE.parents if (p / 'pom.xml').is_file())
a = argparse.ArgumentParser(description=__doc__)
a.add_argument('--out', type=Path, required=True)
a.add_argument('--runtime', type=Path, default=Path.home() / '.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar')
a.add_argument('--jar', type=Path, default=REPO / 'target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar')
args = a.parse_args()
out = args.out.resolve()
if out.exists():
    raise SystemExit('--out must name a new directory; existing evidence is never deleted')
if not args.runtime.is_file() or not args.jar.is_file():
    raise SystemExit('Build the analyser and resolve its runtime dependency first, or pass --runtime/--jar')
out.mkdir(parents=True)
classes = out / 'classes'
classes.mkdir()
java_bin = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
java = str(java_bin / 'java') if java_bin else 'java'
javac = str(java_bin / 'javac') if java_bin else 'javac'
results = []


def clean(text):
    for value, replacement in [(str(out), '<scratch>'), (str(REPO), '<repo>'), (str(Path.home()), '<home>')]:
        text = text.replace(value, replacement)
    return text


def run(name, command):
    p = subprocess.run(command, cwd=REPO, text=True, capture_output=True, timeout=60)
    row = dict(name=name, exit=p.returncode, stdout=clean(p.stdout), stderr=clean(p.stderr))
    results.append(row)
    (out / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(row), flush=True)
    return p


cp = os.pathsep.join(map(str, [classes, args.runtime, args.jar]))
sources = [p for p in (REPO / 'examples/fixture-generator/src/main/java').rglob('*.java')
           if 'builder' not in p.parts and p.name != 'GenerateFixtures.java']
p = run('compile', [javac, '-proc:none', '-cp', cp, '-d', str(classes),
                   str(REPO / 'tools/replay/ReplayBundle.java'), *map(str, sources),
                   str(HERE / 'LiveCapture.java'), str(HERE / 'Probe.java')])
if p.returncode:
    raise SystemExit('probe compilation failed; inspect results.json')
res = Path('com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml')
shutil.copyfile(REPO / 'examples/fixture-generator/src/main/resources' / res, classes / res)
fixtures = REPO / 'src/test/resources/replay'
base = {'graph/DEMO.graphml': (fixtures / 'demo-quote-recorded-processor.graphml').read_bytes(),
        'log/DEMO.yaml': (fixtures / 'demo-quote-recorded-audit.yaml').read_bytes(),
        'replay/DEMO.yaml': (fixtures / 'demo-quote-recorded.replay.yaml').read_bytes()}


def bundle(name, contents, pretty=False):
    manifest = {'format': 2, 'replay': {'member': 'replay/DEMO.yaml', 'records': 7, 'serviceCalls': 0},
                'members': [{'path': k, 'sha256': hashlib.sha256(v).hexdigest(), 'bytes': len(v)}
                            for k, v in contents.items()]}
    path = out / (name + '.fexp')
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', json.dumps(manifest, indent=2) if pretty else json.dumps(manifest, separators=(',', ':')))
        for key, value in contents.items():
            z.writestr(key, value)
    return path


cli = [java, '-Duser.home=' + str(out / 'home'), '-jar', str(args.jar)]
runner = [java, '-Xmx64m', '-Duser.home=' + str(out / 'home'), '-cp', cp, 'ReplayBundle',
          '--processor', 'com.acme.demo.generated.DemoQuoteRecordedProcessor', '--cp', str(classes)]
normal = bundle('normal', base)
run('normal-runner', runner + ['--bundle', str(normal), '--out', str(out / 'normal.yaml')])
run('normal-compare', cli + ['--replay-compare', str(normal), str(out / 'normal.yaml')])
pretty = bundle('pretty', base, pretty=True)
run('pretty-verify', cli + ['--verify', str(pretty)])
run('pretty-runner', runner + ['--bundle', str(pretty), '--out', str(out / 'pretty.yaml')])

log = base['log/DEMO.yaml'].decode().replace('eventLogRecord: \n', 'eventLogRecord: \n            # DEMO indented YAML comment\n', 1)
log = log.replace('        - priceListener: { symbol: DEMO-A, mid: 100.19999999999999}',
                  '        - priceListener:\n            thread: DEMO-old', 1)
assert 'thread: DEMO-old' in log, 'fixture anchor moved; adapt this probe, do not call it passing'
b = bundle('nested-comment', dict(base, **{'log/DEMO.yaml': log.encode()}))
f = out / 'nested-comment.yaml'
f.write_text(log.replace('thread: DEMO-old', 'thread: DEMO-new', 1))
run('nested-comment-compare', cli + ['--replay-compare', str(b), str(f)])

payload = base['replay/DEMO.yaml'].replace(b'symbol: "DEMO-A"', b'symbol: "DEMO event: EventLogControlEvent"', 1)
assert payload != base['replay/DEMO.yaml'], 'payload anchor moved'
b = bundle('payload-control-name', dict(base, **{'replay/DEMO.yaml': payload}))
run('payload-runner', runner + ['--bundle', str(b), '--out', str(out / 'payload.yaml')])
f = out / 'payload-input.yaml'
f.write_bytes(payload)
run('payload-direct-capture', [java, '-cp', cp, 'LiveCapture', str(f), str(out / 'payload-direct.yaml')])

broken = base['replay/DEMO.yaml'].replace(b'\n---\n', b'\n', 1)
b = bundle('missing-separator', dict(base, **{'replay/DEMO.yaml': broken}))
run('missing-separator', runner + ['--bundle', str(b), '--out', str(out / 'missing-separator.yaml')])
f = out / 'changed-values.yaml'
f.write_bytes(base['replay/DEMO.yaml'].replace(b'bid: 100.1', b'bid: 999.9', 1))
run('duplicate-fields-and-pairing', [java, '-cp', cp, 'Probe', str(fixtures / 'demo-quote-recorded-audit.yaml'), str(f)])

# Each member is small, but their aggregate allocation exceeds the explicitly constrained child heap.
large = dict(base)
for i in range(12):
    large['replay/extra-' + str(i)] = b'x' * (6 * 1024 * 1024)
b = bundle('many-replays', large)
run('aggregate-memory', runner + ['--bundle', str(b), '--out', str(out / 'many.yaml')])
run('live-recorder', [java, '-cp', cp, 'LiveCapture', str(fixtures / 'demo-quote-recorded.replay.yaml'), str(out / 'live.yaml')])

# Mutation is a scratch-only COPY; neither repository source nor its compiled classes are altered.
mutant = out / 'identity-mutant'
mutant.mkdir()
source = (REPO / 'examples/fixture-generator/src/main/java/com/acme/demo/replay/ReplayCapture.java').read_bytes()
assert source.count(b'event != expected || target == null') == 1, 'mutation anchor moved'
(mutant / 'ReplayCapture.java').write_bytes(source.replace(b'event != expected || target == null', b'target == null'))
p = run('compile-identity-mutant', [javac, '-proc:none', '-cp', cp, '-d', str(mutant), str(mutant / 'ReplayCapture.java')])
if p.returncode == 0:
    run('live-recorder-identity-mutant', [java, '-cp', str(mutant) + os.pathsep + cp, 'LiveCapture',
        str(fixtures / 'demo-quote-recorded.replay.yaml'), str(out / 'mutant.yaml')])
print('Observations saved to results.json; compare with README, then turn the corrected expectations into regressions.')
