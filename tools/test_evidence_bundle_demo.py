"""Headless checks for tools/evidence-bundle-demo.py's capture refusals (spec r3 §4.1, EP-A1).

capture_refusal() is the capture skill's step 2 written as code. Each §4.1 condition must refuse BY NAME, and the one
thing that is not a refusal, an absent log.identity (no check has run yet), must not refuse. The contexts are shaped
like real ones: taken from a live analyser's context reply on the DEMO log (2026-09-28).
"""
import copy
import importlib.util
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("evidence_bundle_demo", HERE / "evidence-bundle-demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def a_capturable_context(log_path):
    member = {"path": str(log_path), "sizeBytes": 4053, "directory": False}
    return {"inFlight": None,
            "project": {"active": True, "settings": "/tmp/p/.analyser/project.fluxtion-settings", "unsavedEdits": False},
            "log": {"path": str(log_path), "records": 10, "generation": 1, "following": False,
                    "freshness": {"state": "unchanged-metadata", "members": [{"loaded": member, "state": "unchanged-metadata",
                                                                              "onDisk": member}]}}}


class CaptureRefusalTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.log = pathlib.Path(self.dir.name) / "demo-quote-audit.yaml"
        self.log.write_text("eventLogRecord: DEMO\n")
        self.ok = a_capturable_context(self.log)

    def tearDown(self):
        self.dir.cleanup()

    def refused(self, ctx, naming):
        why = demo.capture_refusal(ctx)
        self.assertIsNotNone(why, "expected a refusal naming " + naming)
        self.assertIn(naming, why)

    def test_a_settled_single_file_log_with_no_identity_check_yet_is_capturable(self):
        self.assertIsNone(demo.capture_refusal(self.ok), "an ABSENT identity means not yet checked, not a refusal")

    def test_no_log(self):
        self.refused({"log": {}, "inFlight": None}, "no log is open")

    def test_a_pending_load(self):
        ctx = copy.deepcopy(self.ok)
        ctx["inFlight"] = "open"
        self.refused(ctx, "a load is pending")

    def test_an_identity_that_is_not_established(self):
        for state in ("replacement", "unverified"):
            ctx = copy.deepcopy(self.ok)
            ctx["log"]["identity"] = {"state": state, "reason": "DEMO"}
            self.refused(ctx, "identity: " + state)
        ctx = copy.deepcopy(self.ok)
        ctx["log"]["identity"] = {"state": "unchanged", "reason": "DEMO"}
        self.assertIsNone(demo.capture_refusal(ctx), "an unchanged identity is capturable")

    def test_a_file_changed_on_disk(self):
        ctx = copy.deepcopy(self.ok)
        ctx["log"]["freshness"]["state"] = "changed-on-disk"
        self.refused(ctx, "changed on disk")

    def test_not_one_plain_file(self):
        ctx = copy.deepcopy(self.ok)
        ctx["log"]["freshness"]["members"].append(ctx["log"]["freshness"]["members"][0])
        self.refused(ctx, "not one plain file")
        ctx = copy.deepcopy(self.ok)
        ctx["log"]["freshness"]["members"][0]["loaded"]["directory"] = True
        self.refused(ctx, "not one plain file")
        ctx = copy.deepcopy(self.ok)
        ctx["log"]["path"] = str(self.log) + ".gone"
        self.refused(ctx, "not a plain file")


class ConfigDiffTest(unittest.TestCase):

    def test_lists_added_removed_and_changed_keys_only(self):
        self.assertEqual(["a", "c", "d"], demo.config_diff({"a": "1", "b": "2", "c": "3"}, {"a": "9", "b": "2", "d": "4"}))


if __name__ == "__main__":
    unittest.main()
