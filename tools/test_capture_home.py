#!/usr/bin/env python3
"""M77-4 (CLAUDE.md rule 1, T20): the docs harness refuses a user.home that is, or runs through, a symbolic link.

The analyser resolves user.home to its real path, so a capture run under a symlinked home showed the account's scratch
path — the account name in it — in dialogs (found by a walk, 2026-10-18), where the sweep cannot read it. These cases
pin require_real_home in tools/capture-docs.py, which every capture JVM's -Duser.home goes through (home_arg).
The Java capture programs' guard (tools/DocShot.java) is CaptureHomeGuardTest's.

    python3 tools/test_capture_home.py

Witness: make require_real_home return the path without comparing it with os.path.realpath — the link cases pass.
"""
import importlib.util
import os
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("capture_docs", HERE / "capture-docs.py")
cd = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cd)


class CaptureHomeTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = pathlib.Path(os.path.realpath(self.tmp.name))     # macOS: /var → /private/var

    def tearDown(self):
        self.tmp.cleanup()

    def test_a_real_home_passes(self):
        real = self.base / "real-home"
        real.mkdir()
        self.assertEqual(str(real), cd.require_real_home(real))
        self.assertEqual("-Duser.home=" + str(real), cd.home_arg(real))

    def test_a_symlinked_home_is_refused(self):
        real = self.base / "real-home"
        real.mkdir()
        link = self.base / "linked-home"
        link.symlink_to(real)
        with self.assertRaises(SystemExit) as e:
            cd.require_real_home(link)
        self.assertIn("symbolic link", str(e.exception))

    def test_a_home_through_a_linked_parent_is_refused(self):
        (self.base / "real-home").mkdir()
        parent = self.base / "linked-parent"
        parent.symlink_to(self.base)
        self.assertTrue((parent / "real-home").is_dir())
        with self.assertRaises(SystemExit):
            cd.home_arg(parent / "real-home")

    def test_the_harness_home_is_its_own_real_path(self):
        # the harness's own HOME must pass its own guard: on macOS /tmp is a link, so CAPTURE_ROOT starts from realpath
        self.assertEqual(os.path.realpath(str(cd.HOME)), str(cd.HOME))

    def test_a_home_that_names_the_account_is_refused(self):
        account = cd.getpass.getuser()
        if len(account) <= 2:
            self.skipTest("an account name too short to search for")
        named = self.base / account / "home"
        named.mkdir(parents=True)
        with self.assertRaises(SystemExit):
            cd.require_real_home(named)


if __name__ == "__main__":
    result = unittest.main(exit=False).result
    sys.exit(0 if result.wasSuccessful() else 1)
