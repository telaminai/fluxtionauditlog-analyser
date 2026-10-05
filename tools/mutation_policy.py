"""Conservative draft selection and the single CI worker-count setting (MG-1).

Historical registry parsing is deliberately non-executable. An unfamiliar registry expression,
missing diff base, unknown file or shared infrastructure change selects the complete registry.
"""
import argparse
import ast
import json
import os
import subprocess
from pathlib import Path

import mutation_gate_fast as fast

WORKERS = 8
REGISTRY_FILES = ('tools/verify_project_chart_review.py', 'tools/mutation_controls_session.py')


def literal(node, values):
    if isinstance(node, ast.Constant) and isinstance(node.value, (str, int)):
        return node.value
    if isinstance(node, (ast.List, ast.Tuple)):
        result = [literal(n, values) for n in node.elts]
        return tuple(result) if isinstance(node, ast.Tuple) else result
    if isinstance(node, ast.Name):
        return values[node.id]
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        return literal(node.left, values) + literal(node.right, values)
    if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
            and node.func.attr == 'replace' and len(node.args) == 2 and not node.keywords):
        owner = literal(node.func.value, values)
        args = [literal(n, values) for n in node.args]
        if isinstance(owner, str) and all(isinstance(n, str) for n in args):
            return owner.replace(*args)
    raise ValueError('unsupported registry expression')


def assignments(source, wanted, initial=None):
    values = dict(initial or {})
    for node in ast.parse(source).body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            name = node.targets[0].id
            # Constants used by the registry are string expressions. Unrelated module state is ignored.
            try:
                values[name] = literal(node.value, values)
            except (ValueError, KeyError, TypeError):
                if name == wanted:
                    raise ValueError('cannot parse registry')
        elif isinstance(node, ast.AugAssign) and isinstance(node.target, ast.Name) and node.target.id == wanted:
            if not isinstance(node.op, ast.Add):
                raise ValueError('unsupported registry update')
            values[wanted] += literal(node.value, values)
        elif isinstance(node, ast.Expr) and isinstance(node.value, ast.Call):
            call = node.value
            if isinstance(call.func, ast.Attribute) and isinstance(call.func.value, ast.Name) and call.func.value.id == wanted:
                if call.func.attr not in ('append', 'extend') or len(call.args) != 1 or call.keywords:
                    raise ValueError('unsupported registry update')
                item = literal(call.args[0], values)
                values[wanted] += [item] if call.func.attr == 'append' else item
    cases = values[wanted]
    if not cases or any(not isinstance(c, tuple) or len(c) != 5 or
                        not all(isinstance(v, str) for v in c) for c in cases):
        raise ValueError('invalid registry tuples')
    if len({c[0] for c in cases}) != len(cases):
        raise ValueError('duplicate registry names')
    return cases


def registry_at(ref):
    def read(path):
        return subprocess.check_output(['git', 'show', f'{ref}:{path}'], text=True, stderr=subprocess.PIPE)
    session = assignments(read(REGISTRY_FILES[1]), 'CONTROLS')
    return assignments(read(REGISTRY_FILES[0]), 'CASES', {'SESSION_CONTROLS': session})


def selection(cases, base):
    result = {'since': base, 'selected': [], 'notRun': [], 'removed': [], 'changedMappings': []}
    try:
        # Validate a commit first. No unknown ref may turn into an empty-success subset.
        merge_base = subprocess.check_output(['git', 'merge-base', base, 'HEAD'], text=True,
                                            stderr=subprocess.PIPE).strip()
        old = registry_at(merge_base)
        changed = fast.changed_files(base)
        chosen, skipped = fast.select_subset(cases, changed)
        old_by_name = {c[0]: c for c in old}
        current_names = {c[0] for c in cases}
        result['removed'] = [c[0] for c in old if c[0] not in current_names]
        changed_cases = {c[0] for c in cases if old_by_name.get(c[0]) != c}
        result['changedMappings'] = sorted(changed_cases)
        reasons = {c[0]: why for c, why in chosen}
        reasons.update({n: 'new or changed registry tuple' for n in changed_cases})
        chosen = [(c, reasons[c[0]]) for c in cases if c[0] in reasons]
        skipped = [(c, why) for c, why in skipped if c[0] not in reasons]
        result['mergeBase'] = merge_base
    except (subprocess.SubprocessError, OSError, ValueError, SyntaxError, KeyError, TypeError, AssertionError):
        chosen, skipped = [(c, 'full set: base, diff or registry could not be established') for c in cases], []
        result['fallback'] = True
    result['selected'] = [{'name': c[0], 'why': why} for c, why in chosen]
    result['notRun'] = [{'name': c[0], 'why': why} for c, why in skipped]
    result['full'] = len(chosen) == len(cases)
    return result


def ci_plan(cases, event, draft, base):
    selected = selection(cases, base) if event == 'pull_request' and draft else None
    mode = 'feedback' if selected is not None and not selected['full'] else 'full'
    count = min(WORKERS, len(cases))
    return {'mode': mode, 'count': count, 'matrix': list(range(count)), 'selection': selected,
            'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
            'head': os.environ.get('PR_HEAD', ''), 'base': base}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    import verify_project_chart_review as gate
    data = ci_plan(gate.selected_cases(None), os.environ.get('EVENT', ''),
                   os.environ.get('PR_DRAFT') == 'true', os.environ.get('PR_BASE', ''))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, indent=2) + '\n')
    print('mutation_mode=' + data['mode'])
    print('shard_count=' + str(data['count']))
    print('shard_matrix=' + json.dumps(data['matrix']))


if __name__ == '__main__':
    main()
