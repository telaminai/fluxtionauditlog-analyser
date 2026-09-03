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
import argparse, importlib.util, pathlib, sys

_spec = importlib.util.spec_from_file_location(
    "resolver", pathlib.Path(__file__).parent / "bean-resolver.py")
R = importlib.util.module_from_spec(_spec)
sys.modules["resolver"] = R          # @dataclass resolves via sys.modules; register before exec
_spec.loader.exec_module(R)

BAR = "─" * 78


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jars"); ap.add_argument("--manifests")
    ap.add_argument("--figures", required=True)
    ap.add_argument("--conventions", default="")
    ap.add_argument("--xml")
    a = ap.parse_args()

    eps = R.load(a.manifests, a.jars)
    wanted = {f.strip() for f in a.figures.split(",") if f.strip()}
    profile = dict(c.split("=", 1) for c in a.conventions.split(",") if "=" in c)

    print(BAR)
    print(f"  catalogue : {len(eps)} entry points across {len({e.jar for e in eps})} jars")
    print(f"  required  : {len(wanted)} figures")
    print(f"  profile   : {', '.join(f'{k}={v}' for k, v in profile.items()) or '(none)'}")
    print(BAR)

    sols, err = R.solve(eps, wanted, profile)

    if err and err.startswith("UNPROVIDED:"):
        missing = err.split(":", 1)[1].split(",")
        print(f"\n  MODE 2 / 3 — the catalogue cannot meet this requirement.\n")
        print(f"  {len(missing)} figure(s) no component provides:")
        for m in missing:
            print(f"      {m}")
        print(f"\n  You must author {len(missing)} node(s). The harness loop from here:")
        print("      1. create a node shell for each figure above (if missing)")
        print("      2. build the orchestration graph")
        print("      3. CHECK THE GRAPH  — assert dispatch shape before any test runs")
        print("      4. put the graph under test — check the application")
        print("      5. wrong figure  -> fix the node")
        print("         wrong ordering-> fix the orchestration")
        print("      6. re-test")
        print("\n  Everything the catalogue CAN supply is still resolved mechanically;")
        print("  authoring is scoped to the gap, not to the whole graph.")
        return 2

    if err:
        print(f"\n  UNSATISFIABLE — {err}")
        return 2

    if len(sols) > 1:
        print(f"\n  MODE 1 — {len(sols)} equally minimal selections. Assembly is mechanical;")
        print("  selection is not. One question needs judgement:\n")
        for jar in sorted({e.jar for s in sols for e in s}):
            picks = {e.simple for s in sols for e in s if e.jar == jar}
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
    print(f"\n  MODE {mode} — RESOLVED. Nobody authors anything.\n")
    for e in R.order(sel):
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
