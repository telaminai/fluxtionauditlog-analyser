#!/usr/bin/env python3
"""Replay the reviewed scorer witnesses; no model, network, keys or private archive.

Usage: python3 tools/check_coldstart_corpus.py --scorer-revision 18b47a7
The public corpus is a source excerpt, not a baseline-adjusted acceptance run.
Passing these witnesses does not establish precision on every possible Java program.
"""
import argparse
import json
import subprocess
import types
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scorer-revision', default='18b47a7')
    parser.add_argument('--project', type=Path, help='Fingerprint a finished project without a journal')
    parser.add_argument('--baseline', type=Path, help='Pristine extracted download; exclude unchanged shipped files')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    source = subprocess.check_output(
        ['git', 'show', f'{args.scorer_revision}:docs/proposals/beta-testing/coldstart/score_journal.py'],
        cwd=repo, text=True)
    scorer = types.ModuleType('scorer_under_review')
    exec(compile(source, 'score_journal.py', 'exec'), scorer.__dict__)
    if args.project:
        if not args.project.is_dir() or (args.baseline and not args.baseline.is_dir()):
            parser.error('project and supplied baseline must be existing directories')
        findings = [{'trap': key, 'description': description, 'candidates': hits}
                    for key, description, hits in scorer.fingerprints(args.project, args.baseline)]
        needs_review = any(row['candidates'] for row in findings)
        print(json.dumps({'scorerRevision': args.scorer_revision, 'findings': findings,
                          'baselineApplied': bool(args.baseline), 'needsManualReview': needs_review,
                          'scope': 'static candidates, not behavioural proof or a routing score'}, indent=2))
        return needs_review
    if args.baseline:
        parser.error('--baseline requires --project')
    corpus = repo / 'docs/handoff/evidence/coldstart-v2-2026-09-20'
    failures = []

    def check(label, condition):
        print(f'{"PASS" if condition else "FAIL"}: {label}')
        if not condition:
            failures.append(label)

    for run, expected in enumerate([2, 0, 13, 21, 17, 16], 1):
        entries = scorer.parse(corpus / f'run{run}/JOURNAL.md')[0]
        check(f'run {run}: {expected} entries', len(entries) == expected)
        if run == 4:
            check('run 4: first source remains operator, not a later contract',
                  entries[0]['fromKind'] == 'operator')
    prints = {}
    for run in [3, 5, 6]:
        prints[run] = {key: hits for key, _, hits in
                       scorer.fingerprints(corpus / f'run{run}/project', None)}
    for run, name in [(3, 'OrderMonitorApp.java'), (6, 'RiskWatchMain.java')]:
        check(f'run {run}: manually confirmed literal harness detected',
              any(name in hit for hit in prints[run]['T-MAIN']))
    check('run 5: preceding-line ignore annotations excluded',
          not prints[5]['T-TRANSIENT'])
    check('run 6: seven unmarked collections retained',
          len(prints[6]['T-TRANSIENT']) == 7)
    print('MANUAL: run 5 FluxtionMain reads a CSV file. A T-MAIN candidate does not prove literal events.')
    print('LIMIT: timing, actual source use, target identity and task completion require transcript review.')
    return bool(failures)


if __name__ == '__main__':
    raise SystemExit(main())
