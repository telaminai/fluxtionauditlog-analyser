"""Tests for the land-until-fast native build harness — M50/W9.

Each test pins a trap rounds 58–60 actually fell into. The harness exists so that the next person
does not fall into it again, so the tests assert that it refuses rather than reports.
"""
import importlib.util
import pathlib
import sys
import unittest

SCRIPT = pathlib.Path(__file__).with_name("land-native.py")
SPEC = importlib.util.spec_from_file_location("land_native", SCRIPT)
land = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = land
SPEC.loader.exec_module(land)


class ParseResult(unittest.TestCase):
    def test_reads_arm_ns_and_checks(self):
        arm, ns, checks = land.parse_result("noise\nRESULT generated 1.6048 buf=11551.2267 upd=5 brch=2\n")
        self.assertEqual(arm, "generated")
        self.assertAlmostEqual(ns, 1.6048)
        self.assertEqual(checks, "buf=11551.2267 upd=5 brch=2")

    def test_no_result_line_is_an_error_not_a_zero(self):
        """Round 58 twice recorded 0.0000 ns. A program that produced nothing has failed."""
        with self.assertRaises(ValueError):
            land.parse_result("Exception in thread \"main\" java.lang.NoClassDefFoundError\n")

    def test_truncated_result_line_is_an_error(self):
        with self.assertRaises(ValueError):
            land.parse_result("RESULT generated\n")


class ChecksAgree(unittest.TestCase):
    def test_arms_computing_the_same_thing_agree(self):
        self.assertTrue(land.checks_agree({"generated": (1.6, "buf=1 upd=2"), "hand": (1.5, "buf=1 upd=2")}))

    def test_arms_computing_different_things_do_not(self):
        """A fast arm that computes something else is not fast, it is wrong."""
        self.assertFalse(land.checks_agree({"generated": (1.6, "buf=1"), "hand": (0.9, "buf=2")}))

    def test_a_single_arm_is_trivially_in_agreement(self):
        self.assertTrue(land.checks_agree({"generated": (1.6, "buf=1")}))


class Eliminated(unittest.TestCase):
    def test_a_deleted_loop_is_not_a_result(self):
        self.assertTrue(land.eliminated(0.0, 0.05))
        self.assertTrue(land.eliminated(0.05, 0.05))

    def test_a_real_measurement_is_kept(self):
        self.assertFalse(land.eliminated(1.43, 0.05))


class Landed(unittest.TestCase):
    def test_primary_arm_decides(self):
        r = {"generated": (1.60, "x"), "hand": (5.50, "x")}
        self.assertTrue(land.landed(r, "generated", 2.0))

    def test_a_slow_primary_misses_however_fast_the_control_is(self):
        r = {"generated": (5.60, "x"), "hand": (1.56, "x")}
        self.assertFalse(land.landed(r, "generated", 2.0))

    def test_the_target_is_inclusive(self):
        self.assertTrue(land.landed({"generated": (2.0, "x")}, "generated", 2.0))


class InstrumentedAttempts(unittest.TestCase):
    def test_the_first_attempt_always_builds_one(self):
        self.assertEqual(land.instrumented_attempts(5, 0), [1])

    def test_every_n_rebuilds_on_the_expected_attempts(self):
        self.assertEqual(land.instrumented_attempts(7, 3), [1, 4, 7])

    def test_every_one_rebuilds_each_time(self):
        self.assertEqual(land.instrumented_attempts(3, 1), [1, 2, 3])

    def test_no_attempts_means_no_builds(self):
        self.assertEqual(land.instrumented_attempts(0, 3), [])


class BestAndSummary(unittest.TestCase):
    HISTORY = [
        {"attempt": 1, "results": {"generated": (5.70, "x")}, "binary": "a1"},
        {"attempt": 2, "discarded": "arms disagree on output — not a valid comparison"},
        {"attempt": 3, "results": {"generated": (5.51, "x")}, "binary": "a3"},
    ]

    def test_best_ignores_discarded_attempts(self):
        best = land.best_attempt(self.HISTORY, "generated")
        self.assertEqual(best["attempt"], 3)

    def test_best_is_none_when_nothing_was_measurable(self):
        self.assertIsNone(land.best_attempt([{"attempt": 1, "discarded": "build failed"}], "generated"))

    def test_every_attempt_appears_in_the_summary(self):
        """No silent caps: a run that hid its discarded attempts would read as full coverage."""
        text = land.summarise(self.HISTORY, "generated", 2.0)
        self.assertEqual(len(text.splitlines()), 3)
        self.assertIn("DISCARDED", text)
        self.assertIn("missed", text)

    def test_a_landing_attempt_is_marked_as_one(self):
        text = land.summarise([{"attempt": 1, "results": {"generated": (1.60, "x")}}], "generated", 2.0)
        self.assertIn("LANDED", text)


if __name__ == "__main__":
    unittest.main()
