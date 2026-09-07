"""Tests for the performance-page sync — M50/W9.

The page is authored in this repo and published from Fluxtion's. Hand-syncing it three times in one
day is how a corrected figure ends up corrected in one copy only, which is the failure the page is
about. These tests pin what the mechanical copy must do to the links on the way across.
"""
import importlib.util
import pathlib
import sys
import unittest

SCRIPT = pathlib.Path(__file__).with_name("sync-performance-page.py")
SPEC = importlib.util.spec_from_file_location("sync_performance_page", SCRIPT)
sync = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = sync
SPEC.loader.exec_module(sync)

BLOB = sync.BLOB


class Rewrite(unittest.TestCase):
    def test_run_notes_become_absolute(self):
        out = sync.rewrite("evidence [`round-60`](../experience/runs/round-60/NOTES.md) says so")
        self.assertIn(f"({BLOB}/docs/experience/runs/round-60/NOTES.md)", out)

    def test_tool_paths_become_links(self):
        """An unlinked tools/ path on the Fluxtion site names a file the reader cannot find."""
        out = sync.rewrite("run `tools/bench/land-native.py` to land a build")
        self.assertEqual(out, f"run [`tools/bench/land-native.py`]({BLOB}/tools/bench/land-native.py) to land a build")

    def test_an_already_linked_tool_is_left_alone(self):
        text = f"see [`tools/bench/dispatch-bench.py`]({BLOB}/tools/bench/dispatch-bench.py)"
        self.assertEqual(sync.rewrite(text), text)

    def test_other_code_spans_are_untouched(self):
        text = "set `-H:PriorityForceInline=com.your.pkg.MyProcessor.*` on the command line"
        self.assertEqual(sync.rewrite(text), text)

    def test_external_links_are_untouched(self):
        text = "root cause is [oracle/graal#14387](https://github.com/oracle/graal/issues/14387)"
        self.assertEqual(sync.rewrite(text), text)


class Render(unittest.TestCase):
    SRC = "# Authored title\n\nsome preamble\n\n## Read this first\n\nbody with `tools/bench/land-native.py`\n"
    DST = "---\ntitle: Performance tuning\n---\n\n# Published title\n\n## Read this first\n\nstale body\n"

    def test_the_destination_keeps_its_own_head(self):
        out = sync.render(self.SRC, self.DST)
        self.assertTrue(out.startswith("---\ntitle: Performance tuning\n---\n\n# Published title\n\n"))
        self.assertNotIn("Authored title", out)
        self.assertNotIn("stale body", out)

    def test_the_body_arrives_rewritten(self):
        self.assertIn(f"]({BLOB}/tools/bench/land-native.py)", sync.render(self.SRC, self.DST))

    def test_a_source_without_the_marker_is_refused(self):
        with self.assertRaises(ValueError):
            sync.render("# no marker here\n", self.DST)

    def test_a_destination_without_the_marker_is_refused(self):
        """Guessing where the published head ends would silently eat its front matter."""
        with self.assertRaises(ValueError):
            sync.render(self.SRC, "---\ntitle: x\n---\n\n# Published title\n")

    def test_rendering_is_idempotent(self):
        once = sync.render(self.SRC, self.DST)
        self.assertEqual(sync.render(self.SRC, once), once)


if __name__ == "__main__":
    unittest.main()
