#!/usr/bin/env bash
# Seed a bundle copy with the current doc set — and REWRITE the profile's runbook pointers from it.
# Round 03 found that copying a skills directory after a rename leaves .analyser/project.fluxtion-settings
# naming a file that no longer exists. Seeding by hand cannot be trusted to keep them in step.
set -euo pipefail
PROJECT="${1:?usage: seed-project.sh <project-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"

cp "$HERE/current/CLAUDE.md" "$PROJECT/CLAUDE.md"
cp "$HERE/current/AGENTS.md" "$PROJECT/AGENTS.md"
rm -rf "$PROJECT/.claude/skills"
mkdir -p "$PROJECT/.claude/skills"
cp -r "$HERE/current/skills/"* "$PROJECT/.claude/skills/"

PROFILE="$PROJECT/.analyser/project.fluxtion-settings"
python3 - "$PROFILE" "$PROJECT" <<'PY'
import pathlib, re, sys
profile, project = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
lines = [l for l in profile.read_text().splitlines() if not l.startswith("runbook.")]
skills = sorted((project / ".claude/skills").iterdir())
out, n = [], 0
for d in skills:
    f = d / "SKILL.md"
    if not f.is_file():
        continue
    head = f.read_text().split("---")[1] if f.read_text().startswith("---") else ""
    desc = next((l.split(":", 1)[1].strip() for l in head.splitlines()
                 if l.startswith("description:")), "")
    out += [f"runbook.{n}.name={d.name}",
            f"runbook.{n}.path=.claude/skills/{d.name}/SKILL.md",
            f"runbook.{n}.description={desc}"]
    n += 1
profile.write_text("\n".join(lines + [f"runbook.count={n}"] + out) + "\n")
print(f"  profile rewritten: {n} runbook pointer(s)")
PY

# fail closed: every pointer must resolve
python3 - "$PROFILE" "$PROJECT" <<'PY'
import pathlib, sys
profile, project = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
bad = [l.split("=",1)[1] for l in profile.read_text().splitlines()
       if l.startswith("runbook.") and ".path=" in l and not (project / l.split("=",1)[1]).is_file()]
if bad:
    raise SystemExit("  DEAD runbook pointer(s): " + ", ".join(bad))
print("  all runbook pointers resolve")
PY
