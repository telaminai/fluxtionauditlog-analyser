#!/usr/bin/env python3
"""Refresh scheduling weights from >=3 complete collector artifacts, with a held-out run.

Download each mutation-gate artifact with gh run download RUN --name mutation-gate --dir DIR.
Pass --sample RUN=DIR/mutation-gate.json repeatedly and --holdout RUN=FILE separately.
These artifacts are scheduling observations, never reusable test or merge evidence.
"""
import argparse
import json
import math
import statistics
from pathlib import Path

import mutation_shards as shards


def sample(argument):
    run, path = argument.split('=', 1)
    if not run.isdigit():
        raise ValueError('sample requires a numeric GitHub run ID')
    data = json.loads(Path(path).read_text())
    rows = data.get('runs', [])
    names = [r.get('name') for r in rows]
    if (data.get('complete') is not True or data.get('scope', 'full') != 'full'
            or not data.get('revision') or not rows or len(names) != len(set(names))
            or len(rows) != data.get('controls')):
        raise ValueError('timings require a complete, unique full collector artifact')
    for r in rows:
        seconds = r.get('seconds')
        if (r.get('verdict') != 'caught' or r.get('baselineGreen') is not True
                or r.get('restoredByteIdentical') is not True or r.get('classesRestoredByteIdentical') is not True
                or type(seconds) not in (int, float) or not math.isfinite(seconds) or seconds <= 0):
            raise ValueError('timings require caught/restored positive observations')
    return {'run': 'https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/' + run,
            'revision': data['revision']}, {r['name']: r['seconds'] for r in rows}


def refresh(samples, holdout, cases):
    if len(samples) < 3 or len({p['run'] for p, _ in samples}) != len(samples):
        raise ValueError('at least three distinct complete runs required')
    if holdout[0]['run'] in {p['run'] for p, _ in samples}:
        raise ValueError('held-out run must not train the weights')
    weights = {}
    for case in cases:
        values = [times[case[0]] for _, times in samples if case[0] in times]
        # A lone observation is not a stable median; unknown cases keep a conservative fallback.
        if len(values) >= 3:
            weights[case[0]] = round(statistics.median(values), 3)
    if not weights:
        raise ValueError('no control has three observations')
    ordered = sorted(weights.values())
    fallback = max(4.2, ordered[min(len(ordered) - 1, math.ceil(.9 * len(ordered)) - 1)])
    plan, _ = shards.allocate(cases, 8, weights, fallback)
    actual = holdout[1]
    missing = [c[0] for c in cases if c[0] not in actual]
    loads = [sum(actual.get(c[0], fallback) for c in part) for part in plan]
    return {'sourceRuns': [p for p, _ in samples], 'method': 'median of at least three observations per control',
            'defaultSeconds': fallback, 'seconds': weights,
            'heldOut': {**holdout[0], 'workers': 8, 'controlSeconds': loads,
                        'missingObservations': missing,
                        'meaning': 'allocation model only; missing observations use fallback; excludes setup and queueing'}}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sample', action='append', required=True)
    p.add_argument('--holdout', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    import verify_project_chart_review as gate
    result = refresh([sample(s) for s in args.sample], sample(args.holdout), gate.CASES)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print('Recorded timings:', len(result['seconds']), 'fallback:', result['defaultSeconds'],
          'held-out missing:', len(result['heldOut']['missingObservations']))


if __name__ == '__main__':
    main()
