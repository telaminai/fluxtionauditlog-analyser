"""Cheap negative controls for the display/mutation gate, without launching Maven."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('chart_gate', Path(__file__).with_name('verify_project_chart_review.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
ROOT = Path(__file__).resolve().parent.parent


class GateTest(unittest.TestCase):
    def fixture(self, root):
        ci = root / '.github/workflows/ci.yml'
        ci.parent.mkdir(parents=True)
        ci.write_text((ROOT / '.github/workflows/ci.yml').read_text())
        tests = root / 'src/test/java'
        tests.mkdir(parents=True)
        for p in (ROOT / 'src/test/java').rglob('*FrameTest.java'):
            (tests / p.name).touch()
        for _, site, _, _, _ in gate.CASES:
            path = root / site
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((ROOT / site).read_bytes())
        return ci

    def test_current_lists_and_anchors(self):
        self.assertTrue(gate.display_classes(ROOT))
        self.assertEqual(len(gate.CASES), len(gate.selected_cases(None, ROOT)))

    def test_omission_from_both_ci_lists_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ci = self.fixture(root)
            text = ci.read_text().replace(',ProjectPanelChartLifecycleFrameTest', '').replace(' ProjectPanelChartLifecycleFrameTest', '')
            ci.write_text(text)
            with self.assertRaisesRegex(AssertionError, 'frame suites differ from source'):
                gate.display_classes(root)

    def test_absent_later_anchor_is_rejected_before_any_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            case = next(c for c in gate.CASES if c[0] == 'chart-adapter')
            path = root / case[1]
            path.write_text(path.read_text().replace(case[2], 'return false;'))
            with self.assertRaisesRegex(AssertionError, 'chart-adapter'):
                gate.selected_cases(None, root)

    def test_unknown_case_cannot_report_an_empty_success(self):
        with self.assertRaisesRegex(AssertionError, 'unknown mutation'):
            gate.selected_cases(['misspelt-control'], ROOT)

    def test_skips_empty_suites_and_errors_are_not_green(self):
        self.assertFalse(gate.green({'exit': 0, 'suites': []}))
        suite = {'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0}
        self.assertTrue(gate.green({'exit': 0, 'suites': [suite]}))
        for key, value in [('tests', 0), ('skipped', 1), ('errors', 1), ('failures', 1)]:
            self.assertFalse(gate.green({'exit': 0, 'suites': [{**suite, key: value}]}))


if __name__ == '__main__':
    unittest.main()
