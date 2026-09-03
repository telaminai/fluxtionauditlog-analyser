#!/usr/bin/env python3
"""Smoke tests for bean-resolver.py and fluxtion-harness.py.

These tools carry a product argument -- "the assembly is mechanical" rests on the resolver
reproducing a measured optimum -- and until now they had **no tests at all**. Every case below either
pins a behaviour an independent review found broken, or pins the regression that matters most: the
committed round-48 output must stay byte-identical through any change.

    python3 tools/test_tools.py          # from the repo root

No dependencies, no test framework: the tools have none either, and a smoke suite that needs
installing does not get run.
"""
from __future__ import annotations
import importlib.util
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
FIGURES = ("mid,depth,vol,ewma,adjusted,spread,book,score,notional,exposure,var,streak,"
           "charge,buffer,fee,breachCount,alert,alertCount")
R48 = ROOT / "docs/experience/runs/round-48/manifests-v2"
R48_V1 = ROOT / "docs/experience/runs/round-48/manifests"
R55 = ROOT / "docs/experience/runs/round-55/rung1/manifests"
COMMITTED_XML = ROOT / "docs/experience/runs/round-57/resolver-output.xml"

_spec = importlib.util.spec_from_file_location("resolver", ROOT / "tools/bean-resolver.py")
R = importlib.util.module_from_spec(_spec)
sys.modules["resolver"] = R
_spec.loader.exec_module(R)

FAILURES: list[str] = []


def check(name: str, condition: bool, detail: str = "") -> None:
    print(f"  {'PASS' if condition else 'FAIL'}  {name}")
    if not condition:
        FAILURES.append(f"{name}{': ' + detail if detail else ''}")


def harness(*args: str) -> tuple[int, str]:
    r = subprocess.run([sys.executable, str(ROOT / "tools/fluxtion-harness.py"), *args],
                       capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def resolver(*args: str) -> tuple[int, str]:
    r = subprocess.run([sys.executable, str(ROOT / "tools/bean-resolver.py"), *args],
                       capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def manifest(tmp: pathlib.Path, name: str, body: str) -> pathlib.Path:
    d = tmp / name
    d.mkdir(parents=True, exist_ok=True)
    (d / f"{name}.mf").write_text(body)
    return d


# ---------------------------------------------------------------- the regression that matters

def test_round48_reproduces_byte_identically(tmp: pathlib.Path) -> None:
    out = tmp / "out.xml"
    code, _ = resolver("--manifests", str(R48), "--figures", FIGURES, "--xml", str(out))
    check("round-48 resolves", code == 0)
    check("round-48 XML is BYTE-IDENTICAL to the committed output",
          out.read_bytes() == COMMITTED_XML.read_bytes(),
          "the load-bearing regression -- any change that breaks this invalidates the published claim")


def test_audit_flag_does_not_perturb_the_baseline(tmp: pathlib.Path) -> None:
    plain, audited = tmp / "p.xml", tmp / "a.xml"
    resolver("--manifests", str(R48), "--figures", FIGURES, "--xml", str(plain))
    resolver("--manifests", str(R48), "--figures", FIGURES, "--audit", "INFO", "--xml", str(audited))
    check("--audit adds the config bean", "FluxtionSpringConfig" in audited.read_text())
    check("--audit off leaves the baseline untouched",
          plain.read_bytes() == COMMITTED_XML.read_bytes())


# ---------------------------------------------------------------- guards review found missing

def test_ambiguity_is_reported_not_guessed() -> None:
    code, out = resolver("--manifests", str(R55), "--figures", FIGURES)
    check("six type-identical candidates -> AMBIGUOUS", code == 3 and "AMBIGUOUS" in out)
    check("the undecided jar is named", "pricing" in out)


def test_a_profile_resolves_the_ambiguity() -> None:
    code, out = resolver("--manifests", str(R55), "--figures", FIGURES,
                         "--conventions", "spread=hedged")
    check("a site profile decides it", code == 0 and "PricingHedged" in out)
    code, out = resolver("--manifests", str(R55), "--figures", FIGURES,
                         "--conventions", "spread=netted")
    check("changing the profile changes the component", code == 0 and "PricingNetted" in out)


def test_unknown_convention_fails_closed() -> None:
    code, out = resolver("--manifests", str(R55), "--figures", FIGURES,
                         "--conventions", "spread=exotic")
    check("a convention nothing declares is UNSATISFIABLE, not a guess",
          code == 2 and "UNSATISFIABLE" in out)


def test_v1_manifests_name_the_real_cause() -> None:
    code, out = resolver("--manifests", str(R48_V1), "--figures", FIGURES)
    check("v1 manifests fail", code == 2)
    check("...and the message names the missing figure=Interface mapping",
          "figure=Interface" in out or "name=Api" in out, out.strip()[-120:])


def test_constructor_cycle_is_refused(tmp: pathlib.Path) -> None:
    d = manifest(tmp, "cyc", """Implementation-Title: cyc
Fluxtion-Component: true

Name: com/v/C1.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: c1=C1Api
Fluxtion-Requires: C2Api
Fluxtion-Constructor: (C2Api)

Name: com/v/C2.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: c2=C2Api
Fluxtion-Requires: C1Api
Fluxtion-Constructor: (C1Api)
""")
    code, out = resolver("--manifests", str(d), "--figures", "c1,c2")
    check("a constructor cycle is refused by the CLI", code == 2 and "cycle" in out.lower())
    code, out = harness("--manifests", str(d), "--figures", "c1,c2", "--json")
    check("...and by the --json path too (it once said RESOLVED)",
          '"unsatisfiable"' in out, out.strip()[:120])


def test_duplicate_providers_name_the_clash(tmp: pathlib.Path) -> None:
    d = manifest(tmp, "dup", """Implementation-Title: dup
Fluxtion-Component: true

Name: com/v/P1.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: x=XApi
Fluxtion-Constructor: ()

Name: com/v/P2.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: y=XApi
Fluxtion-Constructor: ()
""")
    code, out = resolver("--manifests", str(d), "--figures", "x,y")
    check("duplicate providers are refused", code == 2)
    check("...and the clashing interface is named", "XApi" in out and "P1" in out and "P2" in out,
          out.strip()[-140:])


def test_two_entry_points_from_one_jar(tmp: pathlib.Path) -> None:
    d = manifest(tmp, "multi", """Implementation-Title: multi
Fluxtion-Component: true

Name: com/v/Alpha.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: a=AApi
Fluxtion-Constructor: ()

Name: com/v/Beta.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: b=BApi
Fluxtion-Constructor: ()
""")
    code, out = resolver("--manifests", str(d), "--figures", "a,b")
    check("a jar may contribute more than one entry point", code == 0 and "Alpha" in out and "Beta" in out)
    ids = [l for l in out.splitlines() if "<bean id=" in l]
    check("...with unique bean ids", len(ids) == len({l.split('"')[1] for l in ids}), str(ids))


def test_cyclic_candidate_does_not_discard_a_valid_one(tmp: pathlib.Path) -> None:
    """A cyclic 2-component candidate once beat a valid 3-component one on minimality, and the
    valid answer was then thrown away downstream. Constructibility is now decided inside solve()."""
    d = manifest(tmp, "cyc2", """Implementation-Title: cyc2
Fluxtion-Component: true

Name: com/v/A.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: a=AApi
Fluxtion-Requires: BApi
Fluxtion-Constructor: (BApi)

Name: com/v/B.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: b=BApi
Fluxtion-Requires: AApi
Fluxtion-Constructor: (AApi)

Name: com/v/A2.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: a=AApi
Fluxtion-Requires: SeedApi
Fluxtion-Constructor: (SeedApi)

Name: com/v/Seed.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: seed=SeedApi
Fluxtion-Constructor: ()
""")
    code, out = resolver("--manifests", str(d), "--figures", "a,b")
    check("a cyclic candidate does not discard the valid acyclic answer",
          code == 0 and "A2" in out and "Seed" in out and "UNSATISFIABLE" not in out,
          out.strip()[-160:])


def test_same_simple_name_in_one_jar_gets_distinct_ids(tmp: pathlib.Path) -> None:
    """com.alpha.Node and com.beta.Node in ONE jar both emitted `bundleNode`. The earlier test used
    two DIFFERENTLY named classes and missed it -- the same defect class as fluxtion#31 and G9."""
    d = manifest(tmp, "bundle", """Implementation-Title: bundle
Fluxtion-Component: true

Name: com/alpha/Node.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: x=XApi
Fluxtion-Constructor: ()

Name: com/beta/Node.class
Fluxtion-Entry-Point: true
Fluxtion-Provides: y=YApi
Fluxtion-Constructor: ()
""")
    code, out = resolver("--manifests", str(d), "--figures", "x,y")
    ids = [l.split('"')[1] for l in out.splitlines() if "<bean id=" in l]
    check("same simple name, different packages, one jar -> distinct bean ids",
          len(ids) == 2 and len(set(ids)) == 2, str(ids))


def test_bean_ids_survive_jar_name_collision() -> None:
    e1 = R.EntryPoint("a-b", "com.A", {}, [], [], [])
    e2 = R.EntryPoint("ab", "com.B", {}, [], [], [])
    check("distinct jars normalising alike get distinct ids",
          R.bean_id(e1, [e1, e2]) != R.bean_id(e2, [e1, e2]))


# ---------------------------------------------------------------- the harness's mode derivation

def test_harness_derives_the_modes(tmp: pathlib.Path) -> None:
    import json
    code, out = harness("--manifests", str(R48), "--figures", FIGURES, "--json")
    d = json.loads(out)
    check("a fully-resolved catalogue derives mode 0, loading NO skill",
          d["modes"] == ["0"] and d["skills"] == [None], str(d.get("modes")))

    code, out = harness("--manifests", str(R55), "--figures", FIGURES, "--json")
    check("an ambiguous catalogue derives mode 1", json.loads(out)["modes"] == ["1"])

    code, out = harness("--manifests", str(R55), "--figures", FIGURES + ",netPosition",
                        "--conventions", "spread=hedged", "--json")
    d = json.loads(out)
    check("a MIXED session derives a mode per figure",
          d["modes"] == ["0+", "2/3"] and d["authoring_required"] == ["netPosition"], str(d))

    empty = tmp / "empty"
    empty.mkdir(exist_ok=True)
    code, out = harness("--manifests", str(empty), "--figures", "mid", "--json")
    check("an EMPTY catalogue is greenfield, not unsatisfiable",
          json.loads(out)["branch"] == "greenfield", out.strip()[:120])


def main() -> int:
    print("smoke tests — bean-resolver.py + fluxtion-harness.py\n")
    with tempfile.TemporaryDirectory() as td:
        tmp = pathlib.Path(td)
        test_round48_reproduces_byte_identically(tmp)
        test_audit_flag_does_not_perturb_the_baseline(tmp)
        test_ambiguity_is_reported_not_guessed()
        test_a_profile_resolves_the_ambiguity()
        test_unknown_convention_fails_closed()
        test_v1_manifests_name_the_real_cause()
        test_constructor_cycle_is_refused(tmp)
        test_duplicate_providers_name_the_clash(tmp)
        test_two_entry_points_from_one_jar(tmp)
        test_cyclic_candidate_does_not_discard_a_valid_one(tmp)
        test_same_simple_name_in_one_jar_gets_distinct_ids(tmp)
        test_bean_ids_survive_jar_name_collision()
        test_harness_derives_the_modes(tmp)
    print()
    if FAILURES:
        print(f"{len(FAILURES)} FAILED:")
        for f in FAILURES:
            print("  - " + f)
        return 1
    print("all passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
