"""Run from an isolated playground web/ checkout with FLUXTION_STARTER_TEST_JAR set."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

PARITY_TEST = 'compares actual browser and Java emission against the packaged resource'


def verify_witness(report, expected='browser handler'):
    assert report['numFailedTests'] == 1 and report['numPassedTests'] == 2, 'unexpected test counts'
    assert report.get('numPendingTests', 0) == 0, 'parity must run, not skip'
    failures = [a for suite in report['testResults'] for a in suite['assertionResults'] if a['status'] == 'failed']
    assert len(failures) == 1 and failures[0]['title'] == PARITY_TEST, 'wrong failing test'
    messages = failures[0]['failureMessages']
    assert len(messages) == 1, 'expected one failing assertion'
    message = re.sub(r'\x1b\[[0-9;]*m', '', messages[0])
    # Only the actual first assertion line counts. Stack/code frames may quote ANY other label.
    first_line = message.splitlines()[0]
    assert re.match(r'^AssertionError: ' + re.escape(expected) + r': expected ', first_line), first_line
    return first_line


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check-report', type=Path)
    parser.add_argument('--expected-label', default='browser handler')
    parser.add_argument('--output-dir', type=Path, default=Path(tempfile.gettempdir()) / 'journey-comments-mutation')
    args = parser.parse_args()
    if args.check_report:
        print(verify_witness(json.loads(args.check_report.read_text()), args.expected_label))
        return
    assert os.environ.get('FLUXTION_STARTER_TEST_JAR'), 'set the freshly built starter jar explicitly'
    source = Path('src/lib/starter/comment-contract.ts')
    original = source.read_bytes()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report_path = args.output_dir.resolve() / 'vitest.json'
    report_path.unlink(missing_ok=True)
    try:
        old = 'return `${contract.comments[key]}'
        assert original.decode().count(old) == 1
        source.write_text(original.decode().replace(old, 'return `MUTATED ${contract.comments[key]}', 1))
        result = subprocess.run(['pnpm', 'vitest', 'run', 'src/lib/starter/comment-contract.test.ts',
                                 '--reporter=default', '--reporter=json', '--outputFile.json=' + str(report_path)],
                                capture_output=True, text=True)
        (args.output_dir / 'seen-red.log').write_text(result.stdout + result.stderr)
        assert result.returncode != 0, 'mutation did not fail'
        witness = verify_witness(json.loads(report_path.read_text()), args.expected_label)
        print('Seen red: ' + witness)
        print('The other two tests passed, including ownership hash equivalence.')
    finally:
        source.write_bytes(original)


if __name__ == '__main__':
    main()
