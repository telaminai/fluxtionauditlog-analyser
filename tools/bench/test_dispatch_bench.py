"""Tests for the dispatch conformance bench — M50/W10.

Each test corresponds to a trap round 58 actually fell into. The bench exists to fail on these,
so the tests assert that it does. A fake "benchable" program stands in for a JVM or native image:
it honours the same contract (-Darm=, -Dwarm=, -Diters=, one RESULT line) so no toolchain is
needed to test the harness itself.
"""
import importlib.util
import pathlib
import stat
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("dispatch-bench.py")
SPEC = importlib.util.spec_from_file_location("dispatch_bench", SCRIPT)
dispatch_bench = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = dispatch_bench
SPEC.loader.exec_module(dispatch_bench)


FAKE = r"""#!/usr/bin/env python3
import sys
arm = next(a.split("=", 1)[1] for a in sys.argv if a.startswith("-Darm="))
TABLE = __TABLE__
ns, checks = TABLE[arm]
print(f"RESULT {arm} {ns} {checks}")
"""


def fake_program(table: dict, tmp: str) -> str:
    """Write a program whose arms report fixed (ns, checks). table: arm -> (ns, "chk chk")."""
    p = pathlib.Path(tmp) / "fake_bench.py"
    p.write_text(FAKE.replace("__TABLE__", repr(table)))
    p.chmod(p.stat().st_mode | stat.S_IEXEC)
    return str(p)


def run(table, arms, tmp, rounds=4):
    """Run the bench against a fake program in --native mode (a bare executable)."""
    prog = fake_program(table, tmp)
    return dispatch_bench.main([
        "--native", prog, "--arms", ",".join(arms),
        "--rounds", str(rounds), "--warm", "1", "--iters", "1", "--label", "unit-test",
    ])


class DispatchBenchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="m46-dispatch-bench-")
        self.tmp = self.temp.name
        dispatch_bench._failures.clear()

    def tearDown(self):
        self.temp.cleanup()

    # ---- the happy path ---------------------------------------------------------------
    def test_matching_output_and_plausible_timings_passes(self):
        table = {"fx": ("1.5960", "1050000 2100000 11551.2267"),
                 "hand": ("1.4802", "1050000 2100000 11551.2267")}
        self.assertEqual(0, run(table, ["fx", "hand"], self.tmp))

    # ---- T3: output equivalence is gated BEFORE timing is believed --------------------
    def test_arms_computing_different_things_fail(self):
        table = {"fx": ("1.59", "1050000 2100000 11551.2267"),
                 "hand": ("1.48", "1050000 2100000 9999.0000")}   # different buffer
        self.assertEqual(1, run(table, ["fx", "hand"], self.tmp))
        self.assertIn("all arms produce IDENTICAL output (T3)", dispatch_bench._failures)

    # ---- T4: a suspiciously clean zero is a broken probe, not a result ----------------
    def test_eliminated_loop_fails(self):
        table = {"fx": ("1.59", "1050000 2100000 11551.2267"),
                 "cpp_struct": ("0.0000", "1050000 2100000 11551.2267")}
        self.assertEqual(1, run(table, ["fx", "cpp_struct"], self.tmp))
        self.assertTrue(any("elimination floor" in f for f in dispatch_bench._failures))

    def test_floor_is_below_the_fastest_credible_measurement(self):
        # round 58's fastest real figure was 1.41 ns; the floor must not reject it
        self.assertLess(dispatch_bench.ELIMINATION_FLOOR_NS, 1.41)

    # ---- a single arm is not a comparison ---------------------------------------------
    def test_one_arm_is_refused(self):
        self.assertEqual(1, run({"fx": ("1.59", "1 1 1.0")}, ["fx"], self.tmp))

    # ---- T2: runtime kind must be stated, exactly one mode ----------------------------
    def test_both_modes_is_refused(self):
        rc = dispatch_bench.main(["--native", "/bin/true", "--main", "X",
                                  "--classpath", "cp", "--arms", "a,b"])
        self.assertEqual(2, rc)

    def test_neither_mode_is_refused(self):
        self.assertEqual(2, dispatch_bench.main(["--arms", "a,b"]))

    def test_jvm_mode_requires_a_classpath(self):
        self.assertEqual(2, dispatch_bench.main(["--main", "X", "--arms", "a,b"]))

    # ---- a missing RESULT line is an error, never a silent skip -----------------------
    def test_absent_arm_fails_loudly(self):
        table = {"fx": ("1.59", "1 1 1.0")}          # "hand" will produce no RESULT
        prog = fake_program(table, self.tmp)
        with self.assertRaises(Exception):
            dispatch_bench.run_arm([prog], "hand", 1, 1)

    # ---- the parser contract ----------------------------------------------------------
    def test_result_line_parsing(self):
        m = dispatch_bench.RESULT_RE.match("RESULT fluxtionStreamClock 8.5800 1050000 2100000 11551.2267")
        self.assertIsNotNone(m)
        self.assertEqual("fluxtionStreamClock", m.group(1))
        self.assertEqual("8.5800", m.group(2))
        self.assertEqual(("1050000", "2100000", "11551.2267"), tuple(m.group(3).split()))

    def test_result_line_with_no_checks_parses(self):
        m = dispatch_bench.RESULT_RE.match("RESULT bare 1.4141")
        self.assertIsNotNone(m)
        self.assertEqual((), tuple(m.group(3).split()))


if __name__ == "__main__":
    unittest.main()
