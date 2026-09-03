#!/usr/bin/env python3
"""The Fluxtion authoring harness — it DERIVES the mode rather than asking for one.

The four modes are not four separate workflows. They are a fallback ladder, and which rung you are
on is decided by what the catalogue can answer, not by a flag:

    resolver RESOLVED       -> mode 0   nobody writes anything
    resolver RESOLVED (+profile) -> mode 0+  a one-line site profile decided it
    resolver AMBIGUOUS      -> mode 1   a human or model picks; everything else is mechanical
    resolver UNSATISFIABLE  -> mode 2/3 the catalogue cannot meet the requirement; author nodes

So the harness always runs the catalogue first. That is the point: you never author what you could
have resolved, and you learn which figures actually need authoring before writing a line.
"""
import argparse, importlib.util, json, pathlib, sys

_spec = importlib.util.spec_from_file_location(
    "resolver", pathlib.Path(__file__).parent / "bean-resolver.py")
R = importlib.util.module_from_spec(_spec)
sys.modules["resolver"] = R          # @dataclass resolves via sys.modules; register before exec
_spec.loader.exec_module(R)

BAR = "─" * 78

# Which skill each mode needs. Mode 0/0+ needs NONE - nobody authors anything, so the cheapest
# session loads nothing at all. The greenfield skills are the playground's existing assets.
SKILL = {
    "0":   None,
    "0+":  None,
    "1":   "fluxtion-selection",          # read a description; absence of a promise rules out
    "2":   "fluxtion-spring-scaffold",    # playground: spring-authoring/skill.md
    "3":   "fluxtion-node-authoring",     # playground: CLAUDE.md + golden path
    "2/3": "fluxtion-node-authoring",     # greenfield gap; 2 vs 3 is a user preference
    "unsatisfiable": None,                # nothing to load; the requirement needs revisiting
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jars"); ap.add_argument("--manifests")
    ap.add_argument("--figures", required=True)
    ap.add_argument("--conventions", default="")
    ap.add_argument("--xml")
    ap.add_argument("--json", action="store_true",
                    help="emit the machine-readable handoff: branch, modes, skills to load, work list")
    a = ap.parse_args()

    eps = R.load(a.manifests, a.jars)
    wanted = {f.strip() for f in a.figures.split(",") if f.strip()}
    profile = dict(c.split("=", 1) for c in a.conventions.split(",") if "=" in c)

    def handoff(branch, modes, resolved, gap, candidates=None):
        """The contract a skill loader consumes. Modes are per-figure, so `modes` is a list."""
        print(json.dumps({
            "branch": branch,                       # "catalogue" | "greenfield"
            "modes": modes,                         # e.g. ["0+"] or ["0+", "2/3"]
            "skills": [SKILL[m] for m in modes],
            "resolved_figures": sorted(resolved),
            "authoring_required": sorted(gap),
            "selection_candidates": candidates or {},
        }, indent=2))

    if a.json:
        pass
    else:
        print(BAR)
        print(f"  catalogue : {len(eps)} entry points across {len({e.jar for e in eps})} jars")
        print(f"  required  : {len(wanted)} figures")
        print(f"  profile   : {', '.join(f'{k}={v}' for k, v in profile.items()) or '(none)'}")
        print(BAR)

    # An EMPTY catalogue is greenfield, not an unsatisfiable catalogue. Reporting
    # catalogue/unsatisfiable told an author with no jars to revise their requirement, when the
    # right answer is that there is nothing to resolve against. (Review.)
    if not eps:
        if a.json:
            return handoff("greenfield", ["2/3"], [], sorted(wanted)) or 2
        print("\n  MODE 2 / 3 — GREENFIELD. No component catalogue was found on the path,")
        print("  so there is nothing to resolve against and every figure must be authored.\n")
        for m in sorted(wanted):
            print(f"      {m}")
        return 2

    # solve() returns only CONSTRUCTIBLE resolutions -- constructibility is decided there, not here.
    sols, err = R.solve(eps, wanted, profile)

    if err and err.startswith("UNPROVIDED:"):
        missing = sorted(err.split(":", 1)[1].split(","))
        covered = wanted - set(missing)
        # A session is normally MIXED: most figures resolve, a few need authoring. Resolve the
        # covered subset so the author sees a real bean file and a scoped work list, not a refusal.
        sub, suberr = R.solve(eps, covered, profile)
        if a.json:
            if sub and len(sub) == 1:
                m = ["0+" if profile else "0"]
            elif sub:
                m = ["1"]
            else:
                m = ["unsatisfiable"]      # the covered subset has no consistent selection
            return handoff("catalogue", m + ["2/3"], covered if sub else [], missing) or 2
        print(f"\n  MIXED SESSION — {len(covered)} of {len(wanted)} figures resolve mechanically,")
        print(f"  {len(missing)} require authoring.\n")
        if sub and len(sub) == 1:
            print(f"  MODE {'0+' if profile else '0'} for {len(covered)} figures — resolved, nobody authors:")
            for e in sub[0].ordered:
                print(f"      {e.jar:12} {e.simple}")
            if a.xml:
                pathlib.Path(a.xml).write_text(R.emit_xml(sub[0]) + "\n")
                print(f"      -> {a.xml} (the resolved half, ready to build)")
        elif sub:
            print(f"  MODE 1 for {len(covered)} figures — {len(sub)} candidate selections; needs a profile.")
        print(f"\n  MODE 2 / 3 for {len(missing)} figure(s) no component provides:")
        for m in missing:
            print(f"      {m}")
        print(f"\n  Author {len(missing)} node(s). The harness loop from here:")
        print("      1. create a node shell for each figure above (if missing)")
        print("      2. build the orchestration graph")
        print("      3. CHECK THE GRAPH  — assert dispatch shape before any test runs")
        print("      4. put the graph under test — check the application")
        print("      5. wrong figure  -> fix the node")
        print("         wrong ordering-> fix the orchestration")
        print("      6. re-test")
        print("\n  Authoring is scoped to the gap. The resolved half above is already a")
        print("  buildable bean file; the new nodes join it rather than replacing it.")
        return 2

    if err:
        if a.json:
            return handoff("catalogue", ["unsatisfiable"], [], sorted(wanted)) or 2
        print(f"\n  UNSATISFIABLE — {err}")
        print("  Every required figure exists in the catalogue, but no consistent selection")
        print("  satisfies them together — most often a site profile no variant declares.")
        return 2

    if len(sols) > 1:
        if a.json:
            cands = {}
            for jar in sorted({e.jar for x in sols for e in x.selection}):
                picks = sorted({e.simple for x in sols for e in x.selection if e.jar == jar})
                if len(picks) > 1:
                    cands[jar] = [{"class": p,
                                   "description": next(x for x in eps if x.simple == p).description,
                                   "conventions": next(x for x in eps if x.simple == p).conventions}
                                  for p in picks]
            return handoff("catalogue", ["1"], wanted, [], cands) or 3
        print(f"\n  MODE 1 — {len(sols)} equally minimal selections. Assembly is mechanical;")
        print("  selection is not. One question needs judgement:\n")
        for jar in sorted({e.jar for r in sols for e in r.selection}):
            picks = {e.simple for r in sols for e in r.selection if e.jar == jar}
            if len(picks) > 1:
                print(f"      jar '{jar}' — {len(picks)} candidates:")
                for p in sorted(picks):
                    e = next(x for x in eps if x.simple == p)
                    conv = f"  [{','.join(f'{k}={v}' for k, v in e.conventions.items())}]" if e.conventions else ""
                    print(f"        {p:18} {e.description}{conv}")
        print("\n  Answer it once with --conventions and this becomes mode 0+.")
        return 3

    sel = sols[0]
    mode = "0+" if profile else "0"
    if a.json:
        if a.xml:
            pathlib.Path(a.xml).write_text(R.emit_xml(sols[0]) + "\n")
        return handoff("catalogue", [mode], wanted, []) or 0
    print(f"\n  MODE {mode} — RESOLVED. Nobody authors anything.\n")
    for e in sols[0].ordered:
        print(f"      {e.jar:12} {e.simple}")
    xml = R.emit_xml(sel)
    if a.xml:
        pathlib.Path(a.xml).write_text(xml + "\n")
        print(f"\n  wrote {a.xml}")
    print(f"\n  Next: build. Generation binds at generate-sources in this mode,")
    print("  so a plain `mvn compile` is enough — no split-compile workaround.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
