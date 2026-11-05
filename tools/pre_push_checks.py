#!/usr/bin/env python3
"""Offline registry/frame preflight and its cheap regression suites; no Maven or display.

The installed Git hook checks only a clean HEAD, and refuses a push of other commits rather
than certifying an unrelated checkout. Run without --hook while editing to check the worktree.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SUITES = ('test_project_chart_review.py', 'test_mutation_shards.py', 'test_mutation_iteration.py')


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, text=True, capture_output=True, check=True).stdout.strip()


def check_push(updates):
    """Git's pre-push input: local ref/oid, remote ref/oid. Deletions introduce no candidate."""
    candidates = []
    for line in updates.splitlines():
        fields = line.split()
        if len(fields) != 4:
            raise ValueError('invalid pre-push ref input')
        local_ref, local_oid, _, _ = fields
        if local_oid.strip('0'):
            candidates.append((local_ref, local_oid))
    if not candidates:
        return False
    head = git('rev-parse', 'HEAD')
    for ref, oid in candidates:
        if git('rev-parse', '--verify', oid + '^{commit}') != head:
            raise ValueError(f'{ref} is not the checked-out HEAD; check out its commit and push separately')
    for args in (['diff', '--quiet', '--'], ['diff', '--cached', '--quiet', '--']):
        if subprocess.run(['git', *args], cwd=ROOT).returncode:
            raise ValueError('tracked worktree/index differs from HEAD; commit or preserve those edits before pushing')
    return True


def run_checks():
    with tempfile.TemporaryDirectory(prefix='analyser-push-preflight-') as tmp:
        commands = [
            [sys.executable, 'tools/verify_project_chart_review.py', '--mode', 'preflight',
             '--output', str(Path(tmp) / 'preflight.json')],
            *([sys.executable, 'tools/' + suite] for suite in SUITES),
        ]
        for command in commands:
            print('pre-push: ' + Path(command[1]).name, flush=True)
            result = subprocess.run(command, cwd=ROOT)
            if result.returncode:
                return result.returncode if result.returncode > 0 else 1
    print('pre-push: registry, frame lists and policy/evidence checks passed', flush=True)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hook', action='store_true', help='validate Git ref updates from stdin before checking HEAD')
    args = parser.parse_args()
    try:
        if args.hook and not check_push(sys.stdin.read()):
            return 0
        return run_checks()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print('pre-push: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
