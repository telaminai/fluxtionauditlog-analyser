"""Headless checks for tools/evidence-bundle-demo.py's own helpers.

The capture refusals this file used to test moved into the analyser with the capture skill (convergence,
2026-09-28): they are the evidenceCapture session node's, tested in EvidenceCaptureTest (the node, on the real
generated processor) and EvidenceCaptureFrameTest (the frame, through the real verb). What stays here is the driver's
EP-A7 bookkeeping: the machine-tier settings a recipient's session changed are LISTED, key by key.
"""
import importlib.util
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("evidence_bundle_demo", HERE / "evidence-bundle-demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


class ConfigDiffTest(unittest.TestCase):

    def test_lists_added_removed_and_changed_keys_only(self):
        self.assertEqual(["a", "c", "d"], demo.config_diff({"a": "1", "b": "2", "c": "3"}, {"a": "9", "b": "2", "d": "4"}))

    def test_props_reads_keys_and_ignores_comments(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "config"
            p.write_text("# a comment\nrecentFile.count=1\nrecentFile.0=/tmp/DEMO/x.yaml\n")
            self.assertEqual({"recentFile.count": "1", "recentFile.0": "/tmp/DEMO/x.yaml"}, demo.props(p))


if __name__ == "__main__":
    unittest.main()
