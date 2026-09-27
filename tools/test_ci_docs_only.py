"""Regression checks for ci_docs_only.py — which changes may skip the expensive CI jobs."""
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import ci_docs_only as c  # noqa: E402


class DocsOnlyTest(unittest.TestCase):

    def test_markdown_and_site_images_are_documentation(self):
        for p in ['README.md', 'CHANGELOG.md', 'CLAUDE.md', 'docs/specs/tracker.md', 'docs/site/faq.md',
                  'docs/handoff/evidence/x/RESULTS.md', 'docs/site/assets/start-page.png']:
            self.assertTrue(c.is_documentation(p), p)

    def test_product_and_fixture_files_are_not(self):
        for p in ['src/main/resources/help/help.md', 'src/main/resources/llm/system-prompt.md',
                  'docs/skills/add-a-node/SKILL.md', 'pom.xml', '.github/workflows/ci.yml',
                  'tools/ci_docs_only.py', 'mkdocs.yml',
                  'docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml',
                  'docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round2/pom.xml',
                  'docs/images/diagram.png']:
            self.assertFalse(c.is_documentation(p), p)

    def test_one_code_file_makes_the_whole_change_code(self):
        self.assertTrue(c.docs_only(['docs/specs/tracker.md', 'CLAUDE.md']))
        self.assertFalse(c.docs_only(['docs/specs/tracker.md',
                                      'src/test/java/telamin/fluxtion/audit/analyser/analyser/parse/X.java']))

    def test_doubt_runs_everything(self):
        self.assertFalse(c.docs_only([]), 'an empty diff is not a docs-only change')
        self.assertIsNone(c.changed_paths('0000000000000000000000000000000000000000', 'HEAD', False),
                          'a new branch has no base revision')
        self.assertIsNone(c.changed_paths('not-a-revision', 'HEAD', False), 'a failing diff is not docs-only')
        out = subprocess.run([sys.executable, str(Path(c.__file__))], capture_output=True, text=True).stdout
        self.assertIn('docs_only=false', out, 'missing arguments fail closed')

    def test_real_history(self):
        # 24cf5e74 changed only the two trackers; b95ed357 is PR #43's merge, which changed code
        def decide(rev):
            out = subprocess.run([sys.executable, str(Path(c.__file__)), f'{rev}^', rev],
                                 capture_output=True, text=True).stdout
            return out.strip().splitlines()[-1]
        if subprocess.run(['git', 'cat-file', '-e', '24cf5e74^'], capture_output=True).returncode != 0:
            self.skipTest('shallow checkout: the pinned commits are not present')
        self.assertEqual('docs_only=true', decide('24cf5e74'))
        self.assertEqual('docs_only=false', decide('b95ed357'))


if __name__ == '__main__':
    unittest.main()
