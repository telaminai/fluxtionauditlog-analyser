"""Process ownership checks for the local demo launcher (no Maven, servers or GUI)."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('spring_demo', Path(__file__).with_name('spring-demo.py'))
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


class OwnershipTest(unittest.TestCase):
    def test_teardown_does_not_kill_a_reused_or_unrelated_pid(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'], start_new_session=True)
            try:
                state = {'processes': {'old-demo': {'pid': child.pid, 'identity': 'different birth time'}}}
                demo.stop(state, root / 'state.json')
                self.assertIsNone(child.poll())
                self.assertEqual({}, json.loads((root / 'state.json').read_text())['processes'])
            finally:
                child.terminate()
                child.wait(timeout=5)

    def test_owned_service_stops_and_project_is_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            note = root / 'project/user-work.txt'
            demo.write(note, 'keep this edit')
            state = {}
            child = demo.launch('owned', [sys.executable, '-c', 'import time; time.sleep(60)'],
                                root, os.environ, state, root / 'state.json', root)
            process = state['processes']['owned'].copy()
            try:
                self.assertTrue(demo.alive(process))
                demo.stop(state, root / 'state.json')
                self.assertFalse(demo.alive(process))
                self.assertEqual('keep this edit', note.read_text())
            finally:
                if demo.alive(process):
                    os.kill(process['pid'], 15)
                child.wait(timeout=5)

    def test_prepared_project_is_not_regenerated_on_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            demo.write(root / 'project/.demo-ready', 'local-version')
            demo.write(root / 'project/edited.java', 'user edit')
            # No build arguments are needed: restarting must not touch tools or source.
            self.assertEqual(root / 'project', demo.prepare_project(None, root, None, None, None))
            self.assertEqual('user edit', (root / 'project/edited.java').read_text())


if __name__ == '__main__':
    unittest.main()
