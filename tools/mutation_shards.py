"""Deterministic allocation and fail-closed collection for isolated mutation CI jobs.

Timings affect scheduling only. The live CASES registry remains the complete required set.
Each worker uses its own checkout/class trees/display and the unchanged sequential gate.
"""
import argparse
import hashlib
import json
import math
import subprocess
from pathlib import Path

TIMINGS = Path(__file__).with_name('mutation_timings.json')
PRIORITY = 'design-status-capped'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def durations(path=TIMINGS):
    data = json.loads(Path(path).read_text())
    weights = data['seconds']
    default = data['defaultSeconds']
    for name, value in [('defaultSeconds', default), *weights.items()]:
        require(type(value) in (int, float) and math.isfinite(value) and value > 0,
                f'invalid duration for {name}')
    return weights, default


def allocate(cases, count, weights, default):
    """Longest-first greedy scheduling; ties are stable, new controls receive the default."""
    names = [c[0] for c in cases]
    require(len(names) == len(set(names)), 'duplicate names in control registry')
    require(1 <= count <= len(cases), 'shard count must be between 1 and the number of controls')
    shards, loads = [[] for _ in range(count)], [0.0] * count
    for case in sorted(cases, key=lambda c: (-weights.get(c[0], default), c[0])):
        index = min(range(count), key=lambda i: (loads[i], i))
        shards[index].append(case)
        loads[index] += weights.get(case[0], default)
    # Retain the Linux regression's early failure without compiling/running it twice.
    for shard in shards:
        shard.sort(key=lambda c: c[0] != PRIORITY)
    return shards, loads


def plan(cases, count):
    weights, default = durations()
    return allocate(cases, count, weights, default)


def revision():
    return subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()


def collect(cases, documents, count, expected_revision, gate):
    """Recompute the plan and verify the evidence, not just each worker's verdict string."""
    shards, _ = plan(cases, count)
    require(len(documents) == count, f'expected {count} shard results, got {len(documents)}')
    seen, entries = set(), []
    for doc in documents:
        meta = doc.get('shard', {})
        index = meta.get('index')
        require(type(index) is int and 0 <= index < count, f'invalid shard index: {index}')
        require(index not in seen, f'duplicate shard {index}')
        seen.add(index)
        require(meta.get('count') == count, f'shard {index}: wrong shard count')
        require(meta.get('revision') == expected_revision, f'shard {index}: wrong revision')
        require(doc.get('mode') == 'mutations' and doc.get('engine') == 'fast',
                f'shard {index}: not a fast mutation run')
        require('subset' not in doc, f'shard {index}: branch subset is not the gate')
        expected = shards[index]
        names = [c[0] for c in expected]
        require(meta.get('cases') == names, f'shard {index}: allocation differs from the full registry')
        runs = doc.get('runs', [])
        require([e.get('name') for e in runs] == names,
                f'shard {index}: missing, duplicate, extra or out-of-order controls')
        baseline = doc.get('baseline', {})
        require(gate.green(baseline), f'shard {index}: baseline not green')
        for case, entry in zip(expected, runs):
            name, site, _, _, target = case
            cls, method = target.split('#')
            require(any(s['name'] == cls and any(gate.fast.same_test(n, method) for n in s['testNames'])
                        for s in baseline['suites']), f'{name}: baseline missing named test')
            require(entry.get('site') == site and entry.get('sha256') ==
                    hashlib.sha256(Path(site).read_bytes()).hexdigest(), f'{name}: wrong source snapshot')
            require(entry.get('baselineGreen') is True and entry.get('engine') == 'fast',
                    f'{name}: wrong baseline or engine')
            require(entry.get('verdict') == 'caught', f'{name}: control not caught')
            require(entry.get('restoredByteIdentical') is True and
                    entry.get('classesRestoredByteIdentical') is True, f'{name}: bytes not restored')
            mutated = entry.get('mutated', {})
            require(gate.caught(mutated, method) and any(
                s['name'] == cls and any(a['kind'] == 'failure' and gate.fast.same_test(a['test'], method)
                                       for a in s['assertions']) for s in mutated['suites']),
                f'{name}: no named assertion failure')
            restored = entry.get('restored', {})
            require(gate.green(restored) and any(
                s['name'] == cls and any(gate.fast.same_test(n, method) for n in s['testNames'])
                for s in restored['suites']), f'{name}: named test not restored green')
            entries.append(entry)
    require(seen == set(range(count)), 'missing shard')
    return {'mode': 'mutations', 'engine': 'fast', 'revision': expected_revision,
            'shards': count, 'controls': len(entries), 'complete': True,
            'runs': sorted(entries, key=lambda e: e['name'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', required=True, type=Path)
    parser.add_argument('--count', required=True, type=int)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    import verify_project_chart_review as gate
    # Validate all current anchors independently of the workers' evidence.
    cases = gate.selected_cases(None)
    try:
        documents = [json.loads(p.read_text()) for p in sorted(args.artifacts.glob('mutation-shard-*.json'))]
        result = collect(cases, documents, args.count, args.revision, gate)
    except (ValueError, KeyError, TypeError, OSError) as exc:
        parser.exit(1, f'mutation evidence rejected: {exc}\n')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(f"Complete: {result['controls']} controls caught exactly once across {args.count} shards")


if __name__ == '__main__':
    main()
