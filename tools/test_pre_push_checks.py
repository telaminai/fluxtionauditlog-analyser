"""Exercise the installed hook with real local Git pushes; no network, Java or Maven."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent


class PrePushTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='push test ')
        self.addCleanup(self.tmp.cleanup)
        base = Path(self.tmp.name)
        self.repo = base / 'working tree'
        self.repo.mkdir()
        self.remote = base / 'remote.git'
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.name', 'DEMO')
        self.git('config', 'user.email', 'demo@example.com')
        self.git('config', 'commit.gpgsign', 'false')
        self.git('config', 'core.hooksPath', '.githooks')
        subprocess.run(['git', 'init', '-q', '--bare', str(self.remote)], check=True)
        self.git('remote', 'add', 'origin', str(self.remote))
        for rel in ('.githooks/pre-push', 'tools/pre_push_checks.py'):
            destination = self.repo / rel
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / rel, destination)
        # These stand in for the check processes, not the hook/runner under test. A failed
        # process must stop the actual Git push before the bare remote receives a ref.
        self.preflight = self.repo / 'tools/verify_project_chart_review.py'
        self.preflight.write_text("""from pathlib import Path
import sys
with Path('checks.log').open('a') as log: log.write('preflight\\n')
if Path('broken-anchor').exists():
    print('mutation anchor: DEMO matched twice', file=sys.stderr)
    sys.exit(1)
Path(sys.argv[-1]).write_text('{}')
""")
        for name in ('test_project_chart_review.py', 'test_mutation_shards.py', 'test_mutation_iteration.py'):
            (self.repo / 'tools' / name).write_text("""from pathlib import Path
import sys
name = Path(__file__).name
with Path('checks.log').open('a') as log: log.write(name + '\\n')
sys.exit(1 if Path('fail-' + name).exists() else 0)
""")
        (self.repo / '.gitignore').write_text('checks.log\n')
        (self.repo / 'DEMO.txt').write_text('original\n')
        self.commit()

    def git(self, *args, check=True, cwd=None):
        return subprocess.run(['git', *args], cwd=cwd or self.repo, text=True,
                              capture_output=True, check=check)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'DEMO fixture')

    def pushed(self, *refs):
        return self.git('push', 'origin', *(refs or ('HEAD:refs/heads/demo',)), check=False)

    def checks(self):
        log = self.repo / 'checks.log'
        return log.read_text().splitlines() if log.exists() else []

    def assert_no_remote_refs(self):
        result = subprocess.run(['git', '--git-dir', str(self.remote), 'show-ref'],
                                capture_output=True, text=True)
        self.assertEqual('', result.stdout, 'a rejected push must not publish a ref')

    def test_installed_hook_checks_before_a_real_push_from_a_spaced_path(self):
        nested = self.repo / 'nested'
        nested.mkdir()
        result = self.git('push', 'origin', 'HEAD:refs/heads/demo', cwd=nested, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(['preflight', 'test_project_chart_review.py', 'test_mutation_shards.py',
                          'test_mutation_iteration.py'], self.checks(), 'every cheap check must run before push')
        published = subprocess.run(['git', '--git-dir', str(self.remote), 'rev-parse', 'refs/heads/demo'],
                                   capture_output=True, text=True, check=True).stdout
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout, published)

    def test_an_ambiguous_anchor_blocks_push_before_any_suite(self):
        (self.repo / 'broken-anchor').touch()
        self.commit()
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'registry failure must prevent publication')
        self.assertIn('mutation anchor: DEMO matched twice', result.stderr)
        self.assertEqual(['preflight'], self.checks())
        self.assert_no_remote_refs()

    def test_a_failed_suite_blocks_push_and_stops_later_checks(self):
        (self.repo / 'fail-test_project_chart_review.py').touch()
        self.commit()
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'a failed registry regression must prevent publication')
        self.assertEqual(['preflight', 'test_project_chart_review.py'], self.checks())
        self.assert_no_remote_refs()

    def test_dirty_tracked_bytes_cannot_certify_a_different_commit(self):
        (self.repo / 'DEMO.txt').write_text('uncommitted\n')
        result = self.pushed()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('differs from HEAD', result.stderr)
        self.assertEqual([], self.checks())
        self.assert_no_remote_refs()

    def test_index_and_worktree_changes_cannot_cancel_each_other(self):
        site = self.repo / 'DEMO.txt'
        original = site.read_bytes()
        site.write_text('staged change\n')
        self.git('add', 'DEMO.txt')
        site.write_bytes(original)
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'a dirty index must be checked separately from the worktree')
        self.assertIn('differs from HEAD', result.stderr)
        self.assertEqual([], self.checks())
        self.assert_no_remote_refs()

    def test_an_untracked_frame_test_cannot_make_a_broken_commit_pass(self):
        # The committed preflight requires a test the commit does not contain. A local-only
        # file satisfies the disk scan, so running the checks successfully is not enough.
        relative = 'src/test/java/com/acme/FooFrameTest.java'
        self.preflight.write_text(self.preflight.read_text().replace(
            "if Path('broken-anchor').exists():", f"if not Path('{relative}').is_file():"))
        self.commit()
        source = self.repo / relative
        source.parent.mkdir(parents=True)
        source.write_text('package com.acme; class FooFrameTest {}\n')
        self.assertNotEqual(0, self.git('cat-file', '-e', 'HEAD:' + relative, check=False).returncode)
        manual = subprocess.run([sys.executable, 'tools/pre_push_checks.py'], cwd=self.repo,
                                text=True, capture_output=True)
        self.assertEqual(0, manual.returncode, 'control: disk checks see the untracked test')
        (self.repo / 'checks.log').unlink()
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'an untracked frame test must not certify the published commit')
        self.assertIn('untracked files', result.stderr)
        self.assertEqual([], self.checks(), 'reject before scanning the misleading worktree')
        self.assert_no_remote_refs()

    def test_ignored_build_outputs_do_not_block_push(self):
        (self.repo / '.gitignore').write_text('checks.log\ntarget/\n')
        self.commit()
        output = self.repo / 'target/build.log'
        output.parent.mkdir()
        output.write_text('DEMO build output\n')
        result = self.pushed()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(4, len(self.checks()))

    def test_a_tracked_edit_during_checks_cannot_certify_the_commit(self):
        self.preflight.write_text(self.preflight.read_text()
                                 + "\nPath('DEMO.txt').write_text('fixed only in the worktree\\n')\n")
        self.commit()
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'a tracked edit during checks must prevent publication')
        self.assertIn('differs from HEAD', result.stderr)
        self.assertEqual('fixed only in the worktree\n', (self.repo / 'DEMO.txt').read_text())
        self.assertEqual(4, len(self.checks()), 'the change occurred inside the checks')
        self.assert_no_remote_refs()

    def test_a_commit_during_checks_does_not_change_which_candidate_was_requested(self):
        self.preflight.write_text(self.preflight.read_text() + """
import subprocess
Path('DEMO.txt').write_text('committed during checks\\n')
subprocess.run(['git', 'add', 'DEMO.txt'], check=True)
subprocess.run(['git', 'commit', '-qm', 'DEMO concurrent edit'], check=True)
""")
        self.commit()
        requested = self.git('rev-parse', 'HEAD').stdout
        result = self.pushed()
        self.assertNotEqual(0, result.returncode, 'a changed HEAD must not certify the old requested candidate')
        self.assertIn('not the checked-out HEAD', result.stderr)
        self.assertNotEqual(requested, self.git('rev-parse', 'HEAD').stdout)
        self.assertEqual(4, len(self.checks()))
        self.assert_no_remote_refs()

    def test_any_unchecked_ref_rejects_the_whole_push(self):
        (self.repo / 'DEMO.txt').write_text('second commit\n')
        self.commit()
        result = self.pushed('HEAD:refs/heads/demo', 'HEAD~1:refs/heads/other')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('not the checked-out HEAD', result.stderr)
        self.assertEqual([], self.checks())
        self.assert_no_remote_refs()

    def test_an_annotated_tag_of_head_uses_the_same_checks(self):
        self.git('tag', '-a', '-m', 'DEMO', 'demo-tag')
        result = self.pushed('refs/tags/demo-tag')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(4, len(self.checks()))

    def test_deleting_a_ref_introduces_no_candidate(self):
        head = self.git('rev-parse', 'HEAD').stdout.strip()
        subprocess.run(['git', '--git-dir', str(self.remote), 'fetch', '-q', str(self.repo),
                        head + ':refs/heads/demo'], check=True)
        result = self.pushed(':refs/heads/demo')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([], self.checks())
        self.assert_no_remote_refs()

    def test_manual_checks_also_work_before_committing_edits(self):
        (self.repo / 'DEMO.txt').write_text('uncommitted\n')
        result = subprocess.run([sys.executable, 'tools/pre_push_checks.py'], cwd=self.repo,
                                text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(4, len(self.checks()))

    def test_ci_runs_the_same_checks_and_hook_regressions(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('python3 tools/pre_push_checks.py', ci)
        self.assertIn('python3 tools/test_pre_push_checks.py', ci)


if __name__ == '__main__':
    unittest.main()
