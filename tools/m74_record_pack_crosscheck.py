#!/usr/bin/env python3
"""Re-record tools/fixtures/m74-onboarding/pack-crosscheck.json from the analyser's own Java parser.

    python3 tools/m74_record_pack_crosscheck.py <analyser checkout> [--label '<branch> at <commit>']

The checkout must have compiled classes (`mvn -o compile` there). Every case's `input` in the fixture is written to a
scratch directory as bytes, tools/m74_record_pack_crosscheck/RecordPackVerdicts.java parses each with
assistant/PromptPack.parse, and each case's `java` verdict is replaced by what the Java parser said. Inputs are never
changed here: a new case is added to the fixture with any `java` value and this is re-run. The Python mirror
(m74_onboarding_runner.lint_pack) is pinned to these verdicts by tools/test_m74_onboarding_runner.py.
"""
import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).parent
FIXTURE = HERE / 'fixtures/m74-onboarding/pack-crosscheck.json'
RECORDER = HERE / 'm74_record_pack_crosscheck/RecordPackVerdicts.java'


def visible_json(doc):
    """Readable JSON (an em dash stays an em dash), but a character the pack grammar calls hidden is written as its
    \\u escape, so the fixture itself carries no invisible character."""
    sys.path.insert(0, str(HERE))
    from m74_onboarding_runner import _hidden
    text = json.dumps(doc, indent=1, ensure_ascii=False) + '\n'
    return ''.join(c if c == '\n' or not _hidden(ord(c)) else json.dumps(c)[1:-1] for c in text)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('checkout')
    ap.add_argument('--label', default=None, help='what was built, for the fixture _about')
    ap.add_argument('--check', action='store_true', help='compare current Java verdicts without rewriting any fixture')
    ns = ap.parse_args(argv)
    classes = Path(ns.checkout) / 'target/classes'
    if not (classes / 'telamin/fluxtion/audit/analyser/analyser/assistant/PromptPack.class').exists():
        print(f'no compiled PromptPack under {classes}: run `mvn -o compile` in the checkout', file=sys.stderr)
        return 2
    doc = json.loads(FIXTURE.read_text())
    cases = doc['cases']
    with tempfile.TemporaryDirectory() as tmp:
        for name, c in cases.items():
            (Path(tmp) / f'{name}.bin').write_bytes(c['input'].encode('utf-8'))
        out = subprocess.run(['java', '-cp', str(classes), str(RECORDER), tmp], check=True, capture_output=True,
                             text=True).stdout
    got = dict(line.split('\t', 1) for line in out.splitlines() if line)
    missing, extra = sorted(set(cases) - set(got)), sorted(set(got) - set(cases))
    if missing or extra:
        print(f'the Java parser case set differs: missing={missing}, extra={extra}', file=sys.stderr)
        return 2
    if ns.check:
        changed = [name for name in cases if json.loads(got[name]) != cases[name]['java']]
        if changed:
            print('current Java verdicts differ for: ' + ', '.join(changed), file=sys.stderr)
            return 1
        print(f'checked {len(cases)} current Java verdicts; fixture unchanged')
        return 0
    for name in cases:
        cases[name]['java'] = json.loads(got[name])
    if ns.label:
        doc['_about'] = (f"Verdicts of the analyser's own assistant/PromptPack.parse, {ns.label}, recorded by "
                         "tools/m74_record_pack_crosscheck.py running the Java parser over each input. "
                         "tools/test_m74_onboarding_runner.py pins the Python mirror to them, so a divergence fails a "
                         "test; re-record when PromptPack changes.")
    doc['cases'] = dict(sorted(cases.items()))
    FIXTURE.write_text(visible_json(doc))
    print(f'recorded {len(cases)} verdicts into {FIXTURE}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
