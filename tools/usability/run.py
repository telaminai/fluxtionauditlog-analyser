#!/usr/bin/env python3
"""Run the usability scorecard's task battery against the BUILT jar.

    python3 tools/usability/run.py                 # measure; results in target/usability/
    python3 tools/usability/run.py --record        # also write docs/usability/baseline-<sha>.{json,md}
    python3 tools/usability/run.py --build         # rebuild the jar first (mvn -o package -DskipTests)

What it does
------------
Eight scripted tasks and three key states (docs/usability/scorecard.md), each in a real analyser window at 1200x800,
driven by Swing events through the test harness (src/test/java/.../ui/UsabilityTasks.java). The JVM runs under an
ISOLATED user.home below a neutral path, so no real setting, project or path can reach a measure, and every task
makes its own home inside it. Claude Code is the test suite's FixtureAgent (recorded streams) and the `claude` the
analyser finds is a stub that only exits: no live prompt is ever sent, nothing is registered, no network is used.

The classpath is the shaded jar first, then target/test-classes (the harness and the fixture agent) and the test-scope
dependencies (JUnit, which the frame tests' helpers use). The product classes come from the jar, never target/classes.
"""
import argparse
import os
import pathlib
import shutil
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
ROOT = pathlib.Path("/tmp/analyser-usability")          # neutral: no account name can appear in a measure
HOME = ROOT / "home"
WORK = ROOT / "work"
MAIN = "telamin.fluxtion.audit.analyser.analyser.ui.UsabilityBattery"
HARNESS = REPO / "target/test-classes/telamin/fluxtion/audit/analyser/analyser/ui/UsabilityBattery.class"


def jar():
    hits = [p for p in sorted((REPO / "target").glob("fluxtion-auditlog-analyser-*.jar"))
            if not p.name.startswith("original-")]
    return hits[-1] if hits else None


def mvn(*args):
    subprocess.run(["mvn", "-o", "-q", *args], cwd=REPO, check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--build", action="store_true", help="rebuild the jar (and the test classes) first")
    ap.add_argument("--record", action="store_true", help="also write docs/usability/baseline-<label>.{json,md}")
    ap.add_argument("--label", help="the run's label (default: the short commit)")
    ap.add_argument("--out", default=str(REPO / "target/usability"), help="where the results go")
    a = ap.parse_args()

    if a.build or jar() is None or not HARNESS.exists():
        mvn("package", "-DskipTests")                  # the shaded jar AND the compiled test classes
    built = jar()
    if built is None:
        sys.exit("no jar after the build")
    label = a.label or subprocess.run(["git", "rev-parse", "--short=8", "HEAD"], cwd=REPO, capture_output=True,
                                      text=True, check=True).stdout.strip()

    shutil.rmtree(ROOT, ignore_errors=True)
    HOME.mkdir(parents=True)
    WORK.mkdir(parents=True)
    (ROOT / "tmp").mkdir()
    deps = ROOT / "test-deps.txt"
    mvn("dependency:build-classpath", "-Dmdep.includeScope=test", f"-Dmdep.outputFile={deps}")
    cp = os.pathsep.join([str(built), str(REPO / "target/test-classes"), deps.read_text().strip()])

    # the host's Claude Code session must not leak into the analyser's child processes
    env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    # USABILITY_SHOTS=<dir>: paint each screen to a PNG there, for reading a measure (never committed)
    shots = [f"-Dusability.shots={os.environ['USABILITY_SHOTS']}"] if os.environ.get("USABILITY_SHOTS") else []
    r = subprocess.run(["java", *shots, f"-Duser.home={HOME}", f"-Djava.io.tmpdir={ROOT / 'tmp'}", "-cp", cp, MAIN,
                        str(out), label, str(WORK)], cwd=REPO, env=env, timeout=1800)
    if a.record:
        dest = REPO / "docs" / "usability"
        dest.mkdir(parents=True, exist_ok=True)
        for ext in ("json", "md"):
            shutil.copyfile(out / f"usability-{label}.{ext}", dest / f"baseline-{label}.{ext}")
        print(f"recorded docs/usability/baseline-{label}.json and .md")
    shutil.rmtree(ROOT, ignore_errors=True)
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
