#!/usr/bin/env python3
"""Build a native image until it LANDS in the fast mode, and keep the one that did — M50/W9.

Round 60 established that a GraalVM native image lands at either ~1.6 ns/event or ~5.5 — and that
**the PGO profile decides which.** Hold the profile fixed and three rebuilds reproduce the mode
(1.60/1.66/1.68 from one profile, 5.71/5.63/5.61 from another). The compiler is deterministic in the
decision that matters; what varies is the profile, because collecting one means running an
instrumented binary, and two collections of the same workload differ in over a thousand call-count
contexts. See docs/experience/runs/round-60/NOTES.md and https://github.com/oracle/graal/issues/14387.

So the route is: **collect profiles until one lands, then keep the PROFILE.** A landing profile is a
reproducible input you can commit and rebuild from; a landing binary is only one artifact. This
harness does both — it keeps the binary and it keeps the profile pair that produced it.

What it refuses to do, because round 59 and 60 each lost a day to one of these:

* **report a number without checking the arms agree.** Every arm must emit the same check string,
  or the attempt is discarded rather than timed — a fast arm computing something else is not fast.
* **believe an elimination.** A measurement at or below ``--floor`` is a deleted loop, not a result.
* **cap coverage silently.** Every attempt is printed, and exhausting the attempts is a non-zero
  exit with the best-so-far kept and named.
* **reuse a profile across a rebuild of the instrumented image.** Profiles belong to the instrumented
  image that produced them; carried across a rebuild of *that* image they measured 8.0 ns, worse than
  no profile at all. Reuse against the same instrumented image, or with ``--profile`` for a final
  build, is exactly what you want and is how a landing result is reproduced.

Usage::

    # search: collect profiles until one lands; keeps target/bench and target/bench.profiles/
    land-native.py --graal-home $GRAAL --cp "$CP" --main app.Bench --out target/bench \\
                   --arm generated --arm hand --target 2.0 --attempts 10

    # reproduce: rebuild from the profile that landed, no collection, no lottery
    land-native.py --graal-home $GRAAL --cp "$CP" --main app.Bench --out target/bench \\
                   --arm generated --arm hand --target 2.0 --attempts 1 \\
                   --profile target/bench.profiles/generated.iprof \\
                   --profile target/bench.profiles/hand.iprof

Exit 0 when an attempt lands, 1 when none does (the best is still kept).
"""
import argparse
import pathlib
import shutil
import subprocess
import sys

RESULT_PREFIX = "RESULT"


# ---- pure logic: everything below is unit-tested in test_land_native.py -------------------

def parse_result(text):
    """Pull ``RESULT <arm> <ns> <checks...>`` out of a program's stdout.

    Returns ``(arm, ns, checks)`` where checks is the remainder joined by a space — the string that
    must be identical across arms. Raises ValueError if there is no RESULT line, because a program
    that produced no result is a failure, never a zero.
    """
    for line in text.splitlines():
        if line.startswith(RESULT_PREFIX):
            parts = line.split()
            if len(parts) < 3:
                raise ValueError(f"malformed RESULT line: {line!r}")
            return parts[1], float(parts[2]), " ".join(parts[3:])
    raise ValueError("no RESULT line in output")


def checks_agree(results):
    """True when every arm produced the same check string. One arm is trivially in agreement."""
    checks = {c for (_ns, c) in results.values()}
    return len(checks) <= 1


def eliminated(ns, floor):
    """True when a measurement is too fast to be real — the loop was optimised away."""
    return ns <= floor


def landed(results, primary, target):
    """True when the primary arm reached the target. Other arms are gates, not goals."""
    return results[primary][0] <= target


def instrumented_attempts(attempts, every):
    """Which attempt numbers rebuild the instrumented image.

    Attempt 1 always does — there is nothing to collect from otherwise. ``every=0`` means never
    again, which is the cheap mode: one instrumented build, a fresh profile per attempt.
    """
    if attempts < 1:
        return []
    if every <= 0:
        return [1]
    return [n for n in range(1, attempts + 1) if (n - 1) % every == 0]


def best_attempt(history, primary):
    """The attempt with the lowest primary-arm figure. None when nothing was measurable."""
    scored = [h for h in history if h.get("results")]
    if not scored:
        return None
    return min(scored, key=lambda h: h["results"][primary][0])


def summarise(history, primary, target):
    """One line per attempt — no attempt is ever hidden, including the discarded ones."""
    lines = []
    for h in history:
        if not h.get("results"):
            lines.append(f"  attempt {h['attempt']}: DISCARDED — {h['discarded']}")
            continue
        arms = "  ".join(f"{a}={ns:.4f}" for a, (ns, _c) in sorted(h["results"].items()))
        verdict = "LANDED" if h["results"][primary][0] <= target else "missed"
        lines.append(f"  attempt {h['attempt']}: {arms}  [{verdict}]")
    return "\n".join(lines)


# ---- the build/measure loop --------------------------------------------------------------

def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def build_instrumented(ni, cp, main, out, extra):
    cmd = [ni, "-cp", cp, "--no-fallback", "--pgo-instrument", "-R:MaxHeapSize=2g",
           *extra, "-o", str(out), main]
    r = run(cmd)
    if r.returncode != 0:
        raise RuntimeError(f"instrumented build failed:\n{r.stdout[-2000:]}{r.stderr[-2000:]}")


def keep_profiles(profiles, destination):
    """Copy the profile pair that produced a result next to the binary it produced.

    The binary is one artifact; the profile is the reproducible input. Keeping only the binary is
    how a landing result becomes unrepeatable the moment the classes change.
    """
    destination.mkdir(parents=True, exist_ok=True)
    kept = []
    for src in profiles:
        dst = destination / pathlib.Path(src).name
        shutil.copy2(src, dst)
        kept.append(dst)
    return kept


def collect_profiles(instr, arms, work, warm, iters):
    profiles = []
    for arm in arms:
        p = work / f"{arm}.iprof"
        if p.exists():
            p.unlink()
        run([str(instr), f"-XX:ProfilesDumpFile={p}", f"-Darm={arm}",
             f"-Dwarm={warm}", f"-Diters={iters}"])
        if not p.exists():
            raise RuntimeError(f"instrumented run for arm {arm!r} produced no profile")
        profiles.append(str(p))
    return profiles


def build_final(ni, cp, main, out, profiles, extra):
    cmd = [ni, "-cp", cp, "--no-fallback", "--gc=epsilon", "-R:MaxHeapSize=256m",
           f"--pgo={','.join(profiles)}", *extra, "-o", str(out), main]
    r = run(cmd)
    if r.returncode != 0:
        raise RuntimeError(f"final build failed:\n{r.stdout[-2000:]}{r.stderr[-2000:]}")


def measure(binary, arms, warm, iters):
    results = {}
    for arm in arms:
        r = run([str(binary), f"-Darm={arm}", f"-Dwarm={warm}", f"-Diters={iters}"])
        arm_out, ns, checks = parse_result(r.stdout)
        results[arm_out] = (ns, checks)
    return results


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--graal-home", required=True)
    ap.add_argument("--cp", required=True, help="classpath for the image")
    ap.add_argument("--main", required=True, help="main class")
    ap.add_argument("--out", required=True, help="path of the binary to keep")
    ap.add_argument("--arm", action="append", default=[], help="repeatable; first is the primary")
    ap.add_argument("--target", type=float, required=True, help="ns/event the primary must reach")
    ap.add_argument("--floor", type=float, default=0.05, help="below this is a deleted loop")
    ap.add_argument("--attempts", type=int, default=10)
    ap.add_argument("--reinstrument-every", type=int, default=0,
                    help="rebuild the instrumented image every N attempts; 0 = build it once")
    ap.add_argument("--warm", type=int, default=5_000_000)
    ap.add_argument("--iters", type=int, default=100_000_000)
    ap.add_argument("--profile-warm", type=int, default=1_000_000)
    ap.add_argument("--profile-iters", type=int, default=20_000_000)
    ap.add_argument("--flag", action="append", default=[], help="extra native-image flag, repeatable")
    ap.add_argument("--profile", action="append", default=[],
                    help="use this profile instead of collecting one; repeatable, one per arm. "
                         "This is how a landing result is reproduced.")
    a = ap.parse_args(argv)

    arms = a.arm or ["generated"]
    primary = arms[0]
    ni = str(pathlib.Path(a.graal_home) / "bin" / "native-image")
    out = pathlib.Path(a.out)
    work = out.parent / f"{out.name}.land"
    work.mkdir(parents=True, exist_ok=True)
    instr = work / "instrumented"
    kept_profiles = pathlib.Path(f"{a.out}.profiles")
    fixed = [str(pathlib.Path(p).resolve()) for p in a.profile]
    for f in fixed:
        if not pathlib.Path(f).is_file():
            print(f"no profile at {f}", file=sys.stderr)
            return 2
    rebuilds = [] if fixed else instrumented_attempts(a.attempts, a.reinstrument_every)
    if fixed:
        print(f"using {len(fixed)} supplied profile(s); collecting none", flush=True)

    history = []
    for attempt in range(1, a.attempts + 1):
        note = {"attempt": attempt}
        candidate = work / f"attempt-{attempt}"
        try:
            if attempt in rebuilds:
                print(f"attempt {attempt}: building the instrumented image", flush=True)
                build_instrumented(ni, a.cp, a.main, instr, a.flag)
            profiles = fixed or collect_profiles(instr, arms, work, a.profile_warm, a.profile_iters)
            build_final(ni, a.cp, a.main, candidate, profiles, a.flag)
            results = measure(candidate, arms, a.warm, a.iters)
            if not checks_agree(results):
                note["discarded"] = "arms disagree on output — not a valid comparison"
            elif any(eliminated(ns, a.floor) for ns, _ in results.values()):
                note["discarded"] = f"a measurement at or below the {a.floor} ns floor is a deleted loop"
            else:
                note["results"] = results
                note["binary"] = candidate
        except (RuntimeError, ValueError) as exc:
            note["discarded"] = str(exc).splitlines()[0]
        history.append(note)

        if note.get("results"):
            arms_str = "  ".join(f"{k}={v[0]:.4f}" for k, v in sorted(results.items()))
            print(f"attempt {attempt}: {arms_str}", flush=True)
            if landed(results, primary, a.target):
                shutil.copy2(candidate, out)
                saved = keep_profiles(profiles, kept_profiles)
                print(f"\nLANDED on attempt {attempt} of {a.attempts} "
                      f"({primary}={results[primary][0]:.4f} ns <= {a.target})")
                print(summarise(history, primary, a.target))
                print(f"kept: {out}")
                print(f"kept the profile that produced it — rebuild from it with "
                      f"{' '.join('--profile ' + str(p) for p in saved)}")
                return 0
        else:
            print(f"attempt {attempt}: DISCARDED — {note['discarded']}", flush=True)

    best = best_attempt(history, primary)
    tries = "attempt" if a.attempts == 1 else "attempts"
    print(f"\nNO ATTEMPT LANDED in {a.attempts} {tries} (target {a.target} ns for {primary})")
    print(summarise(history, primary, a.target))
    if best:
        shutil.copy2(best["binary"], out)
        print(f"kept the best of them: attempt {best['attempt']}, "
              f"{primary}={best['results'][primary][0]:.4f} ns → {out}")
    else:
        print("nothing measurable was produced; no binary kept")
    return 1


if __name__ == "__main__":
    sys.exit(main())
