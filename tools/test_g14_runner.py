"""Tests for the G14 harness.

The key-leak scan is the control that replaces the containment G14 gives up by granting the
compilation key, so it is tested against every leak shape the key file's own format allows and,
most importantly, for NOT emitting the secret it searches for. A scan that reported the key in its
own verdict would be the leak.

Nothing here executes `run_trial`'s launch path, reads the real key file, contacts the network or
starts a model session. Every test patches `KEY_FILE` and `TRIAL_TOKEN` onto temporary files.
"""
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('g14_runner', Path(__file__).parent / 'g14_runner.py')
g14 = importlib.util.module_from_spec(spec)
sys.modules['g14_runner'] = g14
spec.loader.exec_module(g14)

SECRET = 'sk-fluxtion-TESTONLY-4f8a2c1b9e7d'


class TempKeyTest(unittest.TestCase):
    """Base: a temporary key file patched in, so no test can reach the real one."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.archive = self.root / 'run'
        self.archive.mkdir()
        self.key = self.root / 'fluxtion.apiKeyFile'
        self.key.write_text(f'apiKey={SECRET}\nhost=example.invalid\n')
        self._real_key, self._real_token = g14.KEY_FILE, g14.TRIAL_TOKEN
        g14.KEY_FILE = self.key
        self.token = self.root / 'token'
        self.token.write_text('trial-token-TESTONLY\n')
        g14.TRIAL_TOKEN = self.token

    def tearDown(self):
        g14.KEY_FILE, g14.TRIAL_TOKEN = self._real_key, self._real_token
        self.tmp.cleanup()

    def write(self, name, text):
        (self.archive / name).write_text(text)


class KeyLeakScanTest(TempKeyTest):
    def test_clean_transcript_passes(self):
        self.write('raw.jsonl', '{"type":"text","text":"ran ./generate.sh, it succeeded"}\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertTrue(verdict['clean'])
        self.assertEqual(verdict['secretOccurrences'], 0)
        self.assertEqual(verdict['keyPathReads'], 0)

    def test_verbatim_secret_is_caught(self):
        self.write('raw.jsonl', f'{{"type":"text","text":"the key is {SECRET}"}}\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertFalse(verdict['clean'])
        self.assertEqual(verdict['secretOccurrences'], 1)

    def test_cat_of_the_key_path_is_caught_without_the_secret(self):
        """The observed leak shape: 2 of 6 Sonnet sessions ran `cat` on the key file."""
        self.write('raw.jsonl',
                   '{"type":"tool_use","input":{"command":"cat ~/.fluxtion/fluxtion.apiKeyFile"}}\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertFalse(verdict['clean'])
        self.assertEqual(verdict['secretOccurrences'], 0)
        self.assertEqual(verdict['keyPathReads'], 1)

    def test_scans_every_output_file(self):
        self.write('raw.jsonl', 'clean\n')
        self.write('events.jsonl', 'clean\n')
        self.write('stderr.log', f'{SECRET}\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertFalse(verdict['clean'])
        self.assertEqual(verdict['files']['stderr.log']['secretOccurrences'], 1)

    def test_verdict_never_contains_the_secret(self):
        """The scan must be safe to commit, print and attach to a report."""
        self.write('raw.jsonl', f'{SECRET} {SECRET}\n')
        verdict = g14.key_leak_scan(self.archive)
        serialised = json.dumps(verdict)
        self.assertNotIn(SECRET, serialised)
        self.assertNotIn('sk-fluxtion', serialised)
        self.assertEqual(verdict['secretOccurrences'], 2)

    def test_short_structural_lines_do_not_match_everything(self):
        self.key.write_text('apiKey=abc\nhost=x\n')
        self.write('raw.jsonl', 'the letters abc appear in ordinary prose\n')
        self.assertTrue(g14.key_leak_scan(self.archive)['clean'])

    def test_absent_key_file_is_vacuous_not_clean(self):
        """No key, no evidence. Reporting `clean` here would claim a check that never ran."""
        self.key.unlink()
        self.write('raw.jsonl', 'anything\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertIsNone(verdict['clean'])
        self.assertFalse(verdict['keyFilePresent'])


class KeyFormatTest(TempKeyTest):
    """Finding 7 — the parser must not report clean because the file is not `key=value`.

    The builder loads this file with java.util.Properties.load (established by disassembling
    FluxtionConfigManager, not by reading the key), so `=`, `:` and whitespace are all separators.
    """

    def assert_caught(self, key_text, leak_text):
        self.key.write_text(key_text)
        self.write('raw.jsonl', leak_text)
        verdict = g14.key_leak_scan(self.archive)
        self.assertFalse(verdict['clean'], f'leak missed for this key-file shape: {key_text!r}')
        self.assertGreaterEqual(verdict['secretOccurrences'], 1)
        self.assertNotIn(SECRET, json.dumps(verdict))

    def test_no_separator_at_all(self):
        """The review's case: a key file with no `=` must still catch a verbatim leak."""
        self.assert_caught(f'{SECRET}\n', f'leaked {SECRET} here\n')

    def test_colon_separator(self):
        self.assert_caught(f'apiKey:{SECRET}\n', f'leaked {SECRET} here\n')

    def test_whitespace_separator(self):
        self.assert_caught(f'apiKey {SECRET}\n', f'leaked {SECRET} here\n')

    def test_comment_lines_are_not_secrets(self):
        """A comment is not a secret; treating it as one would fail every run on its own header."""
        self.key.write_text(f'# fluxtion configuration header line\napiKey={SECRET}\n')
        self.write('raw.jsonl', 'the fluxtion configuration header line appears in a log\n')
        self.assertTrue(g14.key_leak_scan(self.archive)['clean'])


class ScanBeyondTranscriptTest(TempKeyTest):
    """Finding 6 — a project tree is what gets preserved and shared as evidence."""

    def test_secret_in_a_project_file_is_caught(self):
        base = self.root / 'base'
        (base / 'project' / 'target').mkdir(parents=True)
        (base / 'project' / 'target' / 'build.log').write_text(f'resolved with {SECRET}\n')
        self.write('raw.jsonl', 'clean transcript\n')
        verdict = g14.key_leak_scan(self.archive, base)
        self.assertFalse(verdict['clean'])
        self.assertEqual(verdict['secretOccurrences'], 1)
        self.assertIn('project/target/build.log', verdict['files'])

    def test_verdict_still_never_contains_the_secret(self):
        base = self.root / 'base'
        (base / 'tmp').mkdir(parents=True)
        (base / 'tmp' / 'receipt.json').write_text(json.dumps({'key': SECRET}))
        verdict = g14.key_leak_scan(self.archive, base)
        self.assertFalse(verdict['clean'])
        self.assertNotIn(SECRET, json.dumps(verdict))

    def test_binaries_are_skipped_not_decoded(self):
        base = self.root / 'base'
        (base / 'project').mkdir(parents=True)
        (base / 'project' / 'app.jar').write_bytes(b'PK\x03\x04\x00\x00binary' + SECRET.encode())
        verdict = g14.key_leak_scan(self.archive, base)
        self.assertGreaterEqual(verdict['filesSkipped'], 1)
        self.assertNotIn(SECRET, json.dumps(verdict))

    def test_oversize_files_are_skipped(self):
        base = self.root / 'base'
        (base / 'project').mkdir(parents=True)
        (base / 'project' / 'huge.log').write_text('x' * (g14.SCAN_MAX_BYTES + 1))
        verdict = g14.key_leak_scan(self.archive, base)
        self.assertGreaterEqual(verdict['filesSkipped'], 1)


class IsolationProfileTest(unittest.TestCase):
    """Findings 1 and 2 — the two grants the review found weaker than PROTOCOL claims."""

    def profile(self):
        return g14.isolation_profile('/private/tmp/fx-g14-test', '54321', '/opt/jdk21')

    def test_no_local_maven_repository_grant(self):
        """Finding 1: the owner's local repository must not be readable or writable.

        Java resolves user.home from the account, so a grant here would send every JVM the subject
        starts to the owner's real repository — contaminating the evidence with locally installed or
        branch-built artefacts, which the template's own rules reject.
        """
        self.assertNotIn('.m2', self.profile(),
                         'the profile grants access to a local Maven repository')

    def test_maven_is_pointed_at_the_per_run_repository(self):
        base = '/private/tmp/fx-g14-test'
        tmp = Path(base) / 'tmp'
        token = Path(tempfile.gettempdir()) / 'g14-test-token'
        token.write_text('t\n')
        real = g14.TRIAL_TOKEN
        g14.TRIAL_TOKEN = token
        try:
            env = g14.child_env(base, tmp, str(tmp / 'home'), '/opt/jdk21')
        finally:
            g14.TRIAL_TOKEN = real
            token.unlink(missing_ok=True)
        self.assertIn('-Dmaven.repo.local=', env['MAVEN_OPTS'])
        self.assertIn(f'{base}/tmp', env['MAVEN_OPTS'])
        self.assertIn('-s ', env['MAVEN_ARGS'])
        self.assertIn(f'{base}/tmp', env['MAVEN_ARGS'])

    def test_keychain_grant_is_narrowed_to_the_documented_minimum(self):
        """Finding 2: no directory-wide keychain grant.

        `claude --help` documents keychain reads as something only `--bare` skips, and that mode
        forbids the OAuth token this rig supplies, so the read is kept — narrowed to one file.
        """
        keychain_lines = [line for line in self.profile().splitlines() if 'Keychains' in line]
        self.assertTrue(keychain_lines, 'expected a keychain line to inspect')
        for line in keychain_lines:
            self.assertNotIn('subpath', line,
                             'the keychain grant must be a literal file, not a subpath')
            self.assertIn('login.keychain-db', line)

    def test_containment_claims_protocol_makes(self):
        text = self.profile()
        self.assertIn('(deny file-read*', text)
        self.assertIn('localhost:54321', text)
        self.assertIn('.fluxtion/', text)


class ReapProcessGroupTest(unittest.TestCase):
    """Finding 3 — the cap must end the subject's work, not just its first process."""

    def start_family(self, leader_exits=False):
        """A leader that spawns a sleeping grandchild and prints its pid.

        With leader_exits the leader returns immediately, leaving an orphaned child in the group —
        the normal-exit shape the runner's poll loop reaches every time the subject finishes.
        """
        tail = 'exit 0' if leader_exits else 'sleep 120'
        p = subprocess.Popen(['/bin/sh', '-c', f'sleep 120 & echo $!; {tail}'],
                             stdout=subprocess.PIPE, text=True, start_new_session=True)
        pgid = os.getpgid(p.pid)
        grandchild = int(p.stdout.readline().strip())
        for _ in range(50):
            if g14._pid_alive(grandchild):
                break
            time.sleep(0.05)
        return p, pgid, grandchild

    def test_reaping_the_group_kills_the_grandchild(self):
        p, pgid, grandchild = self.start_family()
        self.assertTrue(g14._pid_alive(grandchild), 'grandchild did not start')
        reaped = g14.reap_process_group(pgid, grace=3)
        p.wait(timeout=10)
        self.assertTrue(reaped, 'reap_process_group reported failure')
        self.assertFalse(g14._pid_alive(grandchild),
                         'the grandchild outlived the trial — backgrounded work keeps the key '
                         'readable and the network open after the cap')

    def test_control_signalling_only_the_leader_leaves_the_grandchild(self):
        """The control for finding 3: the behaviour this function replaces must be observable.

        If leader-only signalling ever stops leaving the grandchild alive, the group test above
        proves nothing.
        """
        p, pgid, grandchild = self.start_family()
        g14.reap_process_group(pgid, grace=1, signal_group=False, leader_pid=p.pid)
        p.wait(timeout=10)
        still_running = g14._pid_alive(grandchild)
        if still_running:
            os.kill(grandchild, 9)
        self.assertTrue(still_running,
                        'leader-only signalling killed the grandchild; the group fix is untestable')


class RefusalTest(TempKeyTest):
    """Findings 5 and 8 — refusals that survive `python3 -O` and never truncate evidence."""

    def base_with_endpoint(self):
        base = self.root / 'base'
        home = base / 'tmp' / 'home' / '.fluxtion-analyser'
        home.mkdir(parents=True)
        (home / 'rest-endpoint').write_text(json.dumps({'url': 'http://127.0.0.1:54321'}))
        return base

    def test_non_empty_archive_is_refused_and_bytes_unchanged(self):
        """A crashed attempt leaves raw.jsonl and no meta.json. It must not be reopened with 'w'."""
        base = self.base_with_endpoint()
        archive = base / 'crashed'
        archive.mkdir(parents=True)
        crashed = archive / 'raw.jsonl'
        crashed.write_text('{"partial":"evidence from an interrupted run"}\n')
        before = crashed.read_bytes()
        code = g14.run_trial('crashed', 'model', base, __file__, 60, '/opt/jdk21')
        self.assertEqual(code, 2)
        self.assertEqual(crashed.read_bytes(), before, 'the interrupted run was truncated')

    def test_non_empty_project_is_refused(self):
        base = self.base_with_endpoint()
        (base / 'project').mkdir(parents=True)
        (base / 'project' / 'prestaged.txt').write_text('the operator staged this')
        code = g14.run_trial('fresh', 'model', base, __file__, 60, '/opt/jdk21')
        self.assertEqual(code, 2)

    def test_refusals_hold_under_python_dash_O(self):
        """`python3 -O` strips `assert`. These guards must not be assertions."""
        base = self.base_with_endpoint()
        archive = base / 'crashed2'
        archive.mkdir(parents=True)
        (archive / 'raw.jsonl').write_text('partial\n')
        script = textwrap.dedent(f'''
            import importlib.util, sys
            spec = importlib.util.spec_from_file_location('g', {str(Path(g14.__file__))!r})
            m = importlib.util.module_from_spec(spec); sys.modules['g'] = m
            spec.loader.exec_module(m)
            sys.exit(m.run_trial('crashed2', 'model', {str(base)!r},
                                 {str(Path(__file__))!r}, 60, '/opt/jdk21'))
        ''')
        out = subprocess.run([sys.executable, '-O', '-c', script], capture_output=True, text=True,
                             timeout=120)
        self.assertEqual(out.returncode, 2, f'refusal did not hold under -O: {out.stderr}')
        self.assertIn('not empty', out.stderr)


class JavaHomeTest(unittest.TestCase):
    """Finding 9 — no hard-coded JDK path."""

    def test_explicit_argument_wins(self):
        self.assertEqual(g14.resolve_java_home('/opt/my-jdk'), '/opt/my-jdk')

    def test_module_holds_no_hard_coded_home_directory(self):
        """Every personal path must be derived at runtime, not spelled in the source.

        Checks this machine's actual home as well as the two platform prefixes, so the guard keeps
        working on a Linux runner where a hard-coded path would not start with the macOS prefix.
        """
        source = Path(g14.__file__).read_text()
        for needle in (str(Path.home()), '/Users/' + os.environ.get('USER', 'nobody'),
                       '/home/' + os.environ.get('USER', 'nobody')):
            self.assertNotIn(needle, source, f'a personal filesystem path is hard-coded: {needle}')



class SandboxEnforcementTest(unittest.TestCase):
    """Second review, finding 1 — the kernel's reading of the policy, not the policy's text.

    The text-only assertions passed against a policy that denied EVERY read, because SBPL combines
    multiple filters to `deny` as alternatives: "under ~/.fluxtion/ OR not the key file" matches all
    files. `/bin/cat` aborted on an allowed project file. A negative-only test would still have
    passed, which is how it shipped — so the positive direction is the one that matters here.

    Disposable fixtures throughout: a fake home, a fake key, a fake keychain file. Nothing real is
    read, no model session is started, and the probe is `/bin/cat`.
    """

    @classmethod
    def setUpClass(cls):
        if sys.platform != 'darwin' or not Path('/usr/bin/sandbox-exec').exists():
            raise unittest.SkipTest('sandbox-exec is macOS-only')

    def setUp(self):
        # The base goes under /private/tmp so the profile's own /private/tmp rules apply as they will
        # in a run; the fake home goes OUTSIDE it, because in production home is /Users/<user> and the
        # policy's two deny alternatives do not overlap. A fake home nested inside the base made the
        # "/private/tmp except project and tmp" alternative swallow the key file — a fixture artifact
        # that looks exactly like a policy bug, so the layout has to mirror production.
        # .resolve() matters: the temp root lives under /var/folders, and /var is a symlink to
        # /private/var. SBPL matches the RESOLVED path, so an unresolved subpath in the policy simply
        # never matches and every negative assertion passes for the wrong reason. Production is
        # unaffected — Path.home() is already canonical — but a fixture must be resolved to test it.
        self.base = Path(tempfile.mkdtemp(dir='/private/tmp', prefix='g14-sbpl-')).resolve()
        self._home_dir = tempfile.TemporaryDirectory()
        self.home = (Path(self._home_dir.name).resolve()) / 'fakehome'
        (self.home / '.fluxtion').mkdir(parents=True)
        (self.home / 'Library' / 'Keychains').mkdir(parents=True)
        self.key = self.home / '.fluxtion' / 'fluxtion.apiKeyFile'
        self.key.write_text('apiKey=DUMMY-NOT-A-REAL-KEY-000000\n')
        self.other_secret = self.home / '.fluxtion' / 'other-secret.txt'
        self.other_secret.write_text('a second file in the key directory\n')
        self.elsewhere = self.home / 'source-tree.txt'
        self.elsewhere.write_text('pretend source tree\n')
        (self.base / 'project').mkdir()
        self.allowed = self.base / 'project' / 'input.txt'
        self.allowed.write_text('an allowed project file\n')
        (self.base / 'tmp').mkdir()
        self.policy = self.base / 'isolation.sb'
        self.policy.write_text(g14.isolation_profile(self.base, '54321', '/opt/jdk21',
                                                     home=self.home, key_file=self.key))

    def tearDown(self):
        subprocess.run(['rm', '-rf', str(self.base)], check=False)
        self._home_dir.cleanup()

    def cat(self, path):
        return subprocess.run(['/usr/bin/sandbox-exec', '-f', str(self.policy), '/bin/cat', str(path)],
                              capture_output=True, text=True, timeout=60)

    def test_allowed_project_file_is_readable(self):
        """The positive direction. This is the assertion the shipped policy failed."""
        out = self.cat(self.allowed)
        self.assertEqual(out.returncode, 0,
                         f'the policy aborted an ALLOWED read (rc={out.returncode}): {out.stderr}')
        self.assertIn('an allowed project file', out.stdout)

    def test_the_key_file_itself_is_readable(self):
        """./generate.sh needs it; that is the whole reason the grant exists."""
        out = self.cat(self.key)
        self.assertEqual(out.returncode, 0, f'the key grant does not work: {out.stderr}')

    def test_a_sibling_of_the_key_is_denied(self):
        """`~/.fluxtion` invisible apart from the key file itself — PROTOCOL's third claim."""
        self.assertNotEqual(self.cat(self.other_secret).returncode, 0,
                            'a second file in the key directory was readable')

    def test_the_rest_of_home_is_denied(self):
        """The source tree stays unreadable — PROTOCOL's first claim."""
        self.assertNotEqual(self.cat(self.elsewhere).returncode, 0,
                            'a file elsewhere in home was readable')


class NormalExitCleanupTest(unittest.TestCase):
    """Second review, finding 2 — the leader exits, its children do not.

    The runner's poll loop reaches this state on every normal exit, so it is the common path rather
    than an edge case. Resolving the group from a dead leader made `getpgid` raise, which was read as
    proof the group was empty.
    """

    def test_children_are_reaped_after_the_leader_exits_normally(self):
        p = subprocess.Popen(['/bin/sh', '-c', 'sleep 120 & echo $!; exit 0'],
                             stdout=subprocess.PIPE, text=True, start_new_session=True)
        pgid = os.getpgid(p.pid)           # captured at launch, as the runner now does
        child = int(p.stdout.readline().strip())
        p.wait(timeout=10)                 # the leader is gone and already waited
        self.assertTrue(g14._pid_alive(child), 'child did not start')
        reaped = g14.reap_process_group(pgid, grace=3)
        still = g14._pid_alive(child)
        if still:
            os.kill(child, 9)
        self.assertFalse(still, 'a child outlived the leader and the cleanup reported success')
        self.assertTrue(reaped)


class EnumerationFailureTest(unittest.TestCase):
    """Second review, finding 4 — "I could not look" must not read as "nothing is there"."""

    def setUp(self):
        self.p = subprocess.Popen(['/bin/sh', '-c', 'trap "" TERM; sleep 120'],
                                  start_new_session=True)
        self.pgid = os.getpgid(self.p.pid)
        self._real_run = subprocess.run

    def tearDown(self):
        subprocess.run = self._real_run
        try:
            os.killpg(self.pgid, 9)
        except (ProcessLookupError, PermissionError):
            pass   # already reaped; macOS reports EPERM for an empty group as readily as ESRCH
        self.p.wait(timeout=10)

    def patch_ps(self, behaviour):
        def fake(cmd, *a, **k):
            if isinstance(cmd, list) and cmd and cmd[0] == 'ps':
                return behaviour()
            return self._real_run(cmd, *a, **k)
        subprocess.run = fake

    def test_ps_failure_is_not_a_reaped_group(self):
        self.patch_ps(lambda: subprocess.CompletedProcess(['ps'], 1, '', 'ps: boom'))
        self.assertFalse(g14.reap_process_group(self.pgid, grace=1),
                         'a failed enumeration was reported as a successful cleanup')

    def test_ps_timeout_is_not_a_reaped_group(self):
        def boom():
            raise subprocess.TimeoutExpired(['ps'], 20)
        self.patch_ps(boom)
        self.assertFalse(g14.reap_process_group(self.pgid, grace=1),
                         'an enumeration timeout was reported as a successful cleanup')

    def test_unknown_is_distinct_from_empty(self):
        self.patch_ps(lambda: subprocess.CompletedProcess(['ps'], 1, '', ''))
        self.assertIsNone(g14._group_members(self.pgid))
        self.assertIsNone(g14._group_alive(self.pgid))

class PropertiesDecodingTest(TempKeyTest):
    """Second review, finding 3 — the loader's real format, not an approximation of it.

    Two shapes the separator-splitting parser never saw decoded to the secret and produced CLEAN: a
    `\\uXXXX` escape and a backslash-newline continuation. Both are ordinary Properties, both were
    confirmed against JDK 21 by the reviewer, and either would have let a leaked key pass.
    """

    def assert_decoded_leak_caught(self, key_bytes, description):
        self.key.write_bytes(key_bytes)
        self.write('raw.jsonl', f'the model printed {SECRET} into its transcript\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertIs(verdict['clean'], False, f'{description}: decoded secret produced CLEAN')
        self.assertGreaterEqual(verdict['secretOccurrences'], 1)
        self.assertNotIn(SECRET, json.dumps(verdict), 'the verdict leaked the secret')

    def test_unicode_escape_in_the_value(self):
        escaped = SECRET.replace('-', r'\u002d')
        self.assert_decoded_leak_caught(f'apiKey={escaped}\n'.encode(), 'unicode escape')

    def test_backslash_newline_continuation(self):
        head, tail = SECRET[:21], SECRET[21:]
        self.assert_decoded_leak_caught(f'apiKey={head}\\\n    {tail}\n'.encode(), 'continuation')

    def test_escaped_separator_still_caught(self):
        """The reviewer's third probe, which already passed. Kept so it cannot regress."""
        self.assert_decoded_leak_caught(f'api\\:Key={SECRET}\n'.encode(), 'escaped separator')

    def test_iso_8859_1_is_the_input_encoding(self):
        """Properties.load(InputStream) decodes ISO-8859-1; reading UTF-8 is a third wrong answer."""
        value = SECRET + '\xe9'
        self.key.write_bytes(('apiKey=' + value + '\n').encode('iso-8859-1'))
        self.write('raw.jsonl', f'leaked {value}\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertIs(verdict['clean'], False)

    def test_an_undecodable_line_fails_closed(self):
        """A line this parser cannot decode may hold a secret it is not searching for."""
        self.key.write_bytes(b'apiKey=\\uZZZZ-not-a-valid-escape-sequence\n')
        self.write('raw.jsonl', 'nothing interesting here\n')
        verdict = g14.key_leak_scan(self.archive)
        self.assertIsNone(verdict['clean'], 'an undecodable key line still reported a verdict')
        self.assertGreaterEqual(verdict['unsupportedKeyLines'], 1)

    def test_agrees_with_the_jdk(self):
        """Cross-check the decoder against java.util.Properties itself, not against my reading of it."""
        java = shutil.which('java')
        if not java:
            raise unittest.SkipTest('no JDK on PATH')
        cases = {
            'escape': f'apiKey={SECRET.replace("-", r"\u002d")}\n',
            'continuation': f'apiKey={SECRET[:21]}\\\n    {SECRET[21:]}\n',
            'colon': f'apiKey:{SECRET}\n',
            'whitespace': f'apiKey {SECRET}\n',
        }
        probe = self.root / 'Probe.java'
        probe.write_text(
            'import java.io.*;import java.util.*;\n'
            'public class Probe{public static void main(String[] a)throws Exception{\n'
            ' Properties p=new Properties();try(InputStream in=new FileInputStream(a[0])){p.load(in);}\n'
            ' System.out.print(p.getProperty("apiKey",""));}}\n')
        for name, text in cases.items():
            f = self.root / f'{name}.properties'
            f.write_bytes(text.encode('iso-8859-1'))
            out = subprocess.run([java, str(probe), str(f)], capture_output=True, text=True,
                                 timeout=180)
            self.assertEqual(out.returncode, 0, f'{name}: probe failed: {out.stderr}')
            jdk_value = out.stdout
            decoded, unsupported = g14._decode_properties(f.read_bytes())
            self.assertEqual(unsupported, [], f'{name}: parser reported the line unsupported')
            self.assertIn(jdk_value, decoded,
                          f'{name}: the JDK decoded a value this parser never produced')


if __name__ == '__main__':
    unittest.main()
