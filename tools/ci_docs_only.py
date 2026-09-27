#!/usr/bin/env python3
"""Decide whether a CI run is a docs-only change, so the expensive jobs can be skipped.

Documentation is an INPUT to the test suite here — spec links, CLAUDE.md, the site pages, the glossary and the
runbooks are all read by headless tests — so a docs-only change still runs `build`. What it skips is the work a
Markdown or image change cannot affect: the mutation shards and their self-test, the frame suites and the loop bench.

A path counts as documentation only when it is:
- a Markdown file outside `src/` (bundled `.md` resources such as the help page and the system prompt are product)
  and outside `docs/skills/` (skills are published product content, pinned by CanonicalSkillsTest); or
- an image under `docs/site/assets/`.

Anything else — including the `.graphml`, `.xml` and `.java` fixtures under `docs/handoff/evidence/` that frame
tests read — makes the change NOT docs-only. So does any doubt: no base revision (a new branch), a diff that fails,
or an empty diff. The decision fails closed: when unsure, run everything.

Usage: ci_docs_only.py <base> <head> [--three-dot]   → prints `docs_only=true|false` for $GITHUB_OUTPUT.
"""
import re
import subprocess
import sys

_IMAGE = re.compile(r'\.(png|jpe?g|gif|svg|webp)$', re.IGNORECASE)


def is_documentation(path: str) -> bool:
    if path.startswith('src/') or path.startswith('docs/skills/'):
        return False
    if path.lower().endswith('.md'):
        return True
    return path.startswith('docs/site/assets/') and bool(_IMAGE.search(path))


def docs_only(paths) -> bool:
    paths = [p for p in paths if p]
    return bool(paths) and all(is_documentation(p) for p in paths)


def changed_paths(base: str, head: str, three_dot: bool):
    if not base or set(base) == {'0'}:
        return None                                   # a new branch has no base: run everything
    spec = f'{base}...{head}' if three_dot else f'{base}..{head}'
    result = subprocess.run(['git', 'diff', '--name-only', spec], capture_output=True, text=True)
    if result.returncode != 0:
        return None
    return result.stdout.splitlines()


def main(argv):
    if len(argv) < 3:
        print('usage: ci_docs_only.py <base> <head> [--three-dot]', file=sys.stderr)
        print('docs_only=false')
        return 0
    paths = changed_paths(argv[1], argv[2], '--three-dot' in argv[3:])
    decision = paths is not None and docs_only(paths)
    for p in (paths or []):
        print(f"{'doc ' if is_documentation(p) else 'CODE'} {p}", file=sys.stderr)
    print(f'docs_only={"true" if decision else "false"}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
