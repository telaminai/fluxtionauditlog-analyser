#!/usr/bin/env python3
"""The dispatch conformance bench — M50/W10, spec-generated-dispatch-performance §12.

Round 58 measured the same source at 1.42, 2.57, 4.82 and 6.34 ns depending on compilation
shape, and four successive drafts of the public write-up carried wrong headline figures because
a shape was quoted without being named. This harness exists so that cannot happen again.

It encodes the four traps that round actually fell into:

  T1  CROSS-BINARY COMPARISON.   Arms measured in different binaries are not comparable. A
                                 multi-arm binary and a single-purpose binary gave 4.86 vs 1.58 ns
                                 for identical source. Arms must live in ONE binary and be
                                 interleaved, and the paired difference is the statistic.
  T2  CROSS-IMAGE-KIND.          An executable's PGO profile applied to a shared library made it
                                 4x SLOWER. Runtime kind is recorded and comparison across kinds
                                 is refused.
  T3  UNVERIFIED OUTPUT.         Two arms that compute different things are not a comparison.
                                 Every arm must emit identical check values BEFORE any timing is
                                 reported.
  T4  DEAD-CODE ELIMINATION.     Twice a probe measured 0.0000 ns because the compiler deleted
                                 the loop whose result nothing read. A suspiciously small figure
                                 is a broken probe, not a result, and fails here.

Contract for a benchable main class: it accepts -Darm=<name>, -Dwarm=<n>, -Diters=<n> and prints
one line to stdout:

    RESULT <arm> <ns_per_event> <check> [<check> ...]

Every check field is compared verbatim across arms. Timing is the second field.

USAGE

  tools/bench/dispatch-bench.py --main V5_twoArms --arms fx,hand \\
      --classpath "$CP" --rounds 15

  tools/bench/dispatch-bench.py --main FloorBench --arms generatedMin,handInline \\
      --native ./floor-native --rounds 20

Exit code is non-zero if any gate fails. That is the point: a break fails HERE.
"""
from __future__ import annotations

import argparse
import os
import re
import statistics as st
import subprocess
import sys

RESULT_RE = re.compile(r"^RESULT\s+(\S+)\s+([\d.]+)\s*(.*)$")

# T4 — a per-event figure at or below this is treated as elimination, not speed.
# The fastest credible per-event cost in round 58 was 1.41 ns on a native image with a
# non-escaping processor; 0.05 ns is two orders below that and cannot be real work.
ELIMINATION_FLOOR_NS = 0.05

PASS, FAIL = "PASS", "FAIL"
_failures: list[str] = []


def step(name: str, ok: bool, detail: str = "") -> bool:
    """Print one named gate. `detail` explains a FAILURE and is shown only when one occurs."""
    global _failures
    print(f"  [{PASS if ok else FAIL}] {name}{('  — ' + detail) if (detail and not ok) else ''}")
    if not ok:
        _failures.append(name)
    return ok


def run_arm(cmd: list[str], arm: str, warm: int, iters: int) -> tuple[float, tuple[str, ...]]:
    """Run one arm once. Returns (ns_per_event, check_fields)."""
    full = cmd + [f"-Darm={arm}", f"-Dwarm={warm}", f"-Diters={iters}"]
    proc = subprocess.run(full, capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        m = RESULT_RE.match(line.strip())
        if m and m.group(1) == arm:
            return float(m.group(2)), tuple(m.group(3).split())
    raise RuntimeError(
        f"arm {arm!r} produced no RESULT line\n"
        f"  cmd:    {' '.join(full)}\n"
        f"  stdout: {proc.stdout[-400:]}\n"
        f"  stderr: {proc.stderr[-400:]}"
    )


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--arms", required=True, help="comma-separated arm names, all in ONE binary")
    ap.add_argument("--main", help="main class (JVM mode)")
    ap.add_argument("--classpath", help="classpath (JVM mode)")
    ap.add_argument("--java", default="java", help="java executable (JVM mode)")
    ap.add_argument("--jvm-args", default="-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m")
    ap.add_argument("--native", help="path to a native image (native mode); mutually exclusive with --main")
    ap.add_argument("--rounds", type=int, default=15)
    ap.add_argument("--warm", type=int, default=5_000_000)
    ap.add_argument("--iters", type=int, default=200_000_000)
    ap.add_argument("--label", default="", help="free text recorded in the report, e.g. the shape")
    a = ap.parse_args(argv)

    arms = [s.strip() for s in a.arms.split(",") if s.strip()]

    print("dispatch conformance bench — M50/W10")
    print(f"  arms    : {', '.join(arms)}")
    print(f"  rounds  : {a.rounds} interleaved   warm={a.warm:,}  iters={a.iters:,}")

    # ---- T2: one runtime kind per invocation, recorded, never compared across ------------
    if bool(a.native) == bool(a.main):
        print("\n  [FAIL] exactly one of --native or --main is required")
        return 2
    if a.native:
        kind, cmd = "native-image", [os.path.abspath(a.native)]
    else:
        if not a.classpath:
            print("\n  [FAIL] --classpath is required with --main")
            return 2
        kind = "jvm"
        cmd = [a.java] + a.jvm_args.split() + ["-cp", a.classpath, a.main]
    print(f"  runtime : {kind}   {a.label}")
    print(f"  command : {' '.join(cmd)}\n")
    print("GATES")

    if len(arms) < 2:
        step("at least two arms to compare", False, "one arm is a measurement, not a comparison")
        return 1
    step("at least two arms to compare", True)
    step("single binary for all arms (T1): arms selected by -Darm= within one command", True)
    step(f"single runtime kind: {kind} (T2)", True)

    # ---- collect, INTERLEAVED (T1) --------------------------------------------------------
    samples: dict[str, list[float]] = {arm: [] for arm in arms}
    checks: dict[str, set[tuple[str, ...]]] = {arm: set() for arm in arms}
    try:
        for r in range(a.rounds):
            order = arms if r % 2 == 0 else list(reversed(arms))   # alternate to cancel drift
            for arm in order:
                ns, chk = run_arm(cmd, arm, a.warm, a.iters)
                samples[arm].append(ns)
                checks[arm].add(chk)
    except RuntimeError as e:
        step("every arm produced a RESULT line", False, str(e).splitlines()[0])
        print(f"\n{e}")
        return 1
    step("every arm produced a RESULT line", True)

    # ---- T3: output equivalence BEFORE any timing is reported -----------------------------
    unstable = [arm for arm, cs in checks.items() if len(cs) > 1]
    if not step("each arm is self-consistent across rounds (T3)", not unstable,
                f"varying output: {', '.join(unstable)}" if unstable else ""):
        for arm in unstable:
            print(f"      {arm}: {sorted(checks[arm])}")
        return 1

    distinct = {next(iter(cs)) for cs in checks.values()}
    if not step("all arms produce IDENTICAL output (T3)", len(distinct) == 1,
                "arms computing different things are not a comparison"):
        for arm in arms:
            print(f"      {arm}: {next(iter(checks[arm]))}")
        return 1

    # ---- T4: elimination detection ---------------------------------------------------------
    eliminated = [arm for arm in arms if st.median(samples[arm]) < ELIMINATION_FLOOR_NS]
    if not step(f"no arm below the elimination floor of {ELIMINATION_FLOOR_NS} ns (T4)", not eliminated,
                f"{', '.join(eliminated)} — the compiler deleted the loop; make the result observable"):
        return 1

    # ---- report ---------------------------------------------------------------------------
    print("\nRESULTS   (median of paired, interleaved rounds)")
    print(f"  {'arm':<24}{'median':>10}{'min':>10}{'max':>10}{'sd':>9}{'events/sec':>14}")
    for arm in arms:
        v = samples[arm]
        print(f"  {arm:<24}{st.median(v):>10.4f}{min(v):>10.4f}{max(v):>10.4f}"
              f"{st.pstdev(v):>9.4f}{1e9 / st.median(v) / 1e6:>12.0f}M")

    base, *rest = arms
    print(f"\nPAIRED DIFFERENCES vs {base}   (same round, same conditions — the valid statistic)")
    for arm in rest:
        pairs = [x - y for x, y in zip(samples[arm], samples[base])]
        slower = sum(1 for d in pairs if d > 0)
        overlap = min(max(samples[arm]), max(samples[base])) > max(min(samples[arm]), min(samples[base]))
        print(f"  {arm:<24} median {st.median(pairs):+8.4f} ns  "
              f"({st.median(pairs) / st.median(samples[base]) * 100:+6.1f}%)  "
              f"slower in {slower}/{len(pairs)}  ranges {'OVERLAP' if overlap else 'disjoint'}")
        if overlap:
            print("      ranges overlap — report as indistinguishable, not as a win")

    print(f"\n  shape recorded: runtime={kind}  {a.label or '(no --label given; name the shape)'}")
    print(f"\n{'ALL GATES PASSED' if not _failures else 'FAILED: ' + ', '.join(_failures)}")
    return 0 if not _failures else 1


if __name__ == "__main__":
    sys.exit(main())
