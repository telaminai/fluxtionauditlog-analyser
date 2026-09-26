"""G14 — the acceptance run from a real download.

An extension of the virgin-LLM release runner (see
docs/proposals/beta-testing/coldstart/METHOD.md), not a second apparatus. The cold-start runner hands
the subject a project the operator already acquired, set up and generated with a key; G14 is precisely
those amputated steps, so this runner removes the fixture and grants the four capabilities the journey
needs: built-in tools, outbound network, the compilation key, and a longer cap.

Design notes live in docs/proposals/beta-testing/g14/PROTOCOL.md. The one that matters when reading the
code: granting the key re-opens a failure already observed on the released bundle (2 of 6 Sonnet
sessions ran `cat` on the key file), so every run ends with a key-leak scan whose verdict is a hard
gate. The scan never prints, logs or stores the secret.

    python3 tools/g14_runner.py --preflight --base /private/tmp/fx-g14-s
    python3 tools/g14_runner.py <run-name> <model> <base> <input-file>
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / 'tools'))
from coldstart_clock import ObserverClock  # noqa: E402  (path set above)

# The owner's compilation key. G14 is the only trial permitted to use it (docs/specs/tracker.md).
KEY_FILE = Path.home() / '.fluxtion' / 'fluxtion.apiKeyFile'
TRIAL_TOKEN = Path.home() / '.claude-trial-token'
CLAUDE_BIN = str(Path.home() / '.local/bin/claude')
SCAFFOLD_URL = 'https://fluxtion-playground.dev/start/scaffold?template=analyser-bundle'
DEFAULT_CAP_SECONDS = 1800  # the interrupted 1.0.74 attempt ran 869s and never reached a chart
REAP_GRACE_SECONDS = 10
# Below this length a candidate would match ordinary prose and fail every run on its own header.
MIN_SECRET_LENGTH = 12
# Bounds for the filesystem half of the key scan. A build tree holds jars and class files; reading
# them whole would make the scan slower than the trial without finding text a key could hide in.
SCAN_MAX_BYTES = 4 * 1024 * 1024
SCAN_SKIP_DIRS = {'.git', 'node_modules'}


def sha256_file(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def resolve_java_home(explicit=None):
    """Resolve a JDK 21 rather than hard-coding one.

    A hard-coded path ties the harness to one machine and fails somewhere else — which is how the
    reviewer found that the previously configured JDK was not installed on their machine.
    """
    if explicit:
        return str(explicit)
    env = os.environ.get('JAVA_HOME')
    if env and (Path(env) / 'bin/java').exists():
        return env
    try:
        out = subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True,
                             timeout=30)
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    found = shutil.which('java')
    return str(Path(found).resolve().parent.parent) if found else ''


def isolation_profile(base, port, java_home, home=None, key_file=None):
    """Per-run sandbox profile.

    Deliberately weaker than the cold-start profile in three places, each forced by the journey:
    built-in tools need a working filesystem, acquisition needs outbound network, and ./generate.sh
    needs the key. What is NOT relaxed: the source tree stays unreadable, every other analyser
    instance stays unreachable, and ~/.fluxtion stays invisible apart from the key file itself.

    Two grants the cold-start profile carries are deliberately absent or narrowed here, because that
    profile's subject had no tools and this one has Bash and open network:

    * **No local Maven repository grant.** Java reads `user.home` from the account, not the
      environment, so every JVM the subject starts would otherwise resolve Maven's local repository to
      the owner's real one. That is both a containment hole (writes persisting into the owner's later
      builds, and any credentials in a user settings file readable) and a contamination of the
      evidence: a warm repository can satisfy resolution from locally installed or branch-built
      artefacts, and the template's own "what would make this run not count" rejects branch-built
      artefacts anywhere in the chain. Each run gets an empty repository under `<base>/tmp/m2`
      instead; see `child_env`.
    * **The keychain is narrowed to one file, not removed.** `claude --help` documents keychain reads
      as something only `--bare` skips, and in that mode "OAuth and keychain are never read" with auth
      strictly ANTHROPIC_API_KEY or apiKeyHelper — incompatible with the CLAUDE_CODE_OAUTH_TOKEN this
      rig uses. So the read is kept, narrowed from the whole directory to the login keychain database.
    """
    # home/key_file are parameters so the enforcement tests can build a policy over disposable
    # fixtures and run sandbox-exec against it without going near the real home or the real key.
    home = str(home) if home else str(Path.home())
    key_file = str(key_file) if key_file else str(KEY_FILE)
    return f'''(version 1)
(allow default)
(deny file-read*
 (require-all (subpath "{home}")
  (require-not (subpath "{home}/.local/bin"))
  (require-not (subpath "{home}/.local/share/claude"))
  (require-not (subpath "{java_home}"))
  (require-not (literal "{home}/Library/Keychains/login.keychain-db"))
  (require-not (literal "{key_file}"))
  (require-not (literal "{home}/Library/Preferences/com.apple.security.plist")))
 (require-all (subpath "/private/tmp")
  (require-not (subpath "{base}/project"))
  (require-not (subpath "{base}/tmp"))))
(allow file-read-metadata)
(deny file-write*
 (require-all
  (require-not (subpath "{base}/project"))
  (require-not (subpath "{base}/tmp"))
  (require-not (subpath "/private/var/folders"))
  (require-not (subpath "/dev"))))
(deny network-outbound
 (require-all (remote ip "localhost:*")
  (require-not (remote ip "localhost:{port}"))))
(allow network-outbound (remote tcp "localhost:{port}"))
(deny file-read* (require-all (subpath "{home}/.fluxtion/") (require-not (literal "{key_file}"))))
'''


def maven_paths(base):
    """The per-run Maven repository and the empty user settings that keep the build off the owner's.

    The settings file is empty rather than absent because Maven falls back to the user-level settings
    under `user.home`, which the sandbox no longer lets it read. The shipped templates declare
    `central` and the public Repsy repository in their own POM and resolve anonymously, so an empty
    settings file costs the build nothing.
    """
    tmp = Path(base) / 'tmp'
    return tmp / 'm2', tmp / 'settings-empty.xml'


def child_env(base, tmp, home, java_home):
    """Scrubbed environment, then the trial token and the per-run Maven location.

    Provider and Fluxtion variables are dropped so nothing leaks in by inheritance; the key reaches the
    build through the key FILE, never through the environment. FLUXTION_API_KEY is not read by the
    build in any case.
    """
    repo_local, settings = maven_paths(base)
    env = {
        k: v for k, v in os.environ.items()
        if not k.startswith(('CODEX_', 'CLAUDE', 'ANTHROPIC', 'OPENAI', 'FLUXTION', 'RAPIDAPI'))
        and k not in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'CLASSPATH')
    }
    env.update({
        'TMPDIR': str(tmp), 'JAVA_HOME': java_home, 'HOME': home,
        'USER': os.environ.get('USER', ''), 'LOGNAME': os.environ.get('LOGNAME', ''),
        'DISABLE_AUTOUPDATER': '1', 'CLAUDE_CODE_TMPDIR': str(tmp), 'CLAUDE_TMPDIR': str(tmp),
        # Scrubbed above, then set deliberately: Java resolves user.home from the account, so without
        # these every JVM would use the owner's real local repository, which the profile no longer
        # grants and which would contaminate the evidence with locally installed artefacts.
        'MAVEN_OPTS': f'-Dmaven.repo.local={repo_local}',
        'MAVEN_ARGS': f'-s {settings}',
    })
    env['PATH'] = java_home + '/bin:' + env['PATH']
    env['CLAUDE_CODE_OAUTH_TOKEN'] = TRIAL_TOKEN.read_text().strip()  # trial-only; never printed
    return env


def _pid_alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def _group_members(pgid):
    """Live, non-zombie pids in a process group, or None when enumeration FAILED.

    `killpg(pgid, 0)` is not usable as the liveness probe: once signalled, the group's leader becomes
    a ZOMBIE until this process waits on it, and the probe keeps reporting the group alive — so the
    reaper could never observe success. Enumerating and discarding zombies is what makes "the group is
    gone" observable before the caller waits.

    None is not an empty list, and the difference is the whole point: returning [] when `ps` fails or
    times out made "I could not look" indistinguishable from "nothing is there", and the reaper
    reported success over a process that was still running. A non-zero exit status counts as failure
    too — it was previously unchecked.
    """
    try:
        out = subprocess.run(['ps', '-eo', 'pid=,pgid=,stat='], capture_output=True, text=True,
                             timeout=20)
    except (OSError, subprocess.SubprocessError):
        return None
    if out.returncode != 0:
        return None
    members = []
    for line in out.stdout.splitlines():
        parts = line.split(None, 2)
        if len(parts) < 3:
            continue
        try:
            pid, gid = int(parts[0]), int(parts[1])
        except ValueError:
            continue
        if gid == pgid and not parts[2].startswith('Z'):
            members.append(pid)
    return members


def _group_alive(pgid):
    """True, False, or None when it could not be established."""
    members = _group_members(pgid)
    return None if members is None else bool(members)


def reap_process_group(pgid, grace=REAP_GRACE_SECONDS, signal_group=True, leader_pid=None):
    """Terminate everything the subject started, not just the process it started first.

    Takes the group id CAPTURED AT LAUNCH, never one looked up afterwards. Looking it up from the
    leader was the second defect: after a normal exit `getpgid(leader)` raises, which was read as
    "the group is gone" and reported success while children were still running — and the production
    poll loop reaches exactly that state every time the subject exits on its own.

    Returns True only when the group was OBSERVED empty. False means still alive, or that enumeration
    could not establish it; an unverified cleanup must never read as a successful one.

    `signal_group=False` reproduces the leader-only behaviour this function replaces, for the control
    in the tests; it needs `leader_pid`.
    """
    if not signal_group and leader_pid is None:
        raise ValueError('leader-only signalling needs leader_pid')

    def send(sig):
        if signal_group:
            os.killpg(pgid, sig)
        else:
            os.kill(leader_pid, sig)

    for sig, wait in ((signal.SIGTERM, grace), (signal.SIGKILL, 2.0)):
        try:
            send(sig)
        except ProcessLookupError:
            pass  # the target is gone; the GROUP may not be, so keep checking below
        except PermissionError:
            pass
        deadline = time.time() + wait
        while time.time() < deadline:
            if _group_alive(pgid) is False:
                return True
            time.sleep(0.2)
    return _group_alive(pgid) is False


def _looks_binary(sample):
    return b'\x00' in sample


def _decode_properties(raw):
    """Decode bytes the way `java.util.Properties.load(InputStream)` does.

    Implemented rather than approximated, because the approximation reported CLEAN on two leaked keys
    the reviewer synthesised: a `\\uXXXX` escape and a backslash-newline continuation both decode to
    a value that a separator-splitting parser never sees.

    Three details that are easy to get wrong and each produce a false CLEAN:
    * the stream is decoded as **ISO-8859-1**, not UTF-8, before escapes are processed;
    * a line continues only when it ends in an ODD number of backslashes;
    * leading whitespace on a continuation line is stripped, and escaped separators (`\\=`, `\\:`)
      do not terminate the key.

    Returns (values, unsupported) — `unsupported` collects raw lines this parser could not decode, so
    the caller can fail closed instead of silently scanning for fewer things.
    """
    text = raw.decode('iso-8859-1')
    values, unsupported = [], []

    logical, pending = [], ''
    for natural in text.split('\n'):
        natural = natural.rstrip('\r')
        if pending:
            natural = pending + natural.lstrip(' \t\f')
            pending = ''
        stripped = natural.lstrip(' \t\f')
        if not pending and (not stripped or stripped[0] in '#!'):
            continue
        trailing = len(natural) - len(natural.rstrip('\\'))
        if trailing % 2 == 1:                      # odd count continues; even is an escaped backslash
            pending = natural[:-1]
            continue
        logical.append(natural)
    if pending:
        logical.append(pending)

    for line in logical:
        line = line.lstrip(' \t\f')
        if not line or line[0] in '#!':
            continue
        # Find the separator: first unescaped =, :, or run of whitespace.
        i, escaped, sep = 0, False, None
        while i < len(line):
            c = line[i]
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c in '=:':
                sep = i
                break
            elif c in ' \t\f':
                sep = i
                break
            i += 1
        if sep is None:
            value_part = line                      # a whole line with no separator IS a key with an
        else:                                      # empty value; the line itself is still a candidate
            rest = line[sep:]
            rest = rest.lstrip(' \t\f')
            if rest[:1] in ('=', ':'):
                rest = rest[1:].lstrip(' \t\f')
            value_part = rest
        try:
            values.append(_unescape_properties(value_part))
        except ValueError:
            unsupported.append(line)
        if sep is not None:
            try:
                values.append(_unescape_properties(line))
            except ValueError:
                unsupported.append(line)
    return values, unsupported


def _unescape_properties(s):
    """Java's `loadConvert`: \\uXXXX, \\t \\n \\r \\f, and \\<anything> -> <anything>."""
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c != '\\':
            out.append(c)
            i += 1
            continue
        i += 1
        if i >= len(s):
            break                                   # a trailing lone backslash is dropped, as Java does
        c = s[i]
        i += 1
        if c == 'u':
            if i + 4 > len(s):
                raise ValueError('truncated unicode escape')
            try:
                out.append(chr(int(s[i:i + 4], 16)))
            except ValueError:
                raise ValueError('malformed unicode escape')
            i += 4
        elif c == 't':
            out.append('\t')
        elif c == 'n':
            out.append('\n')
        elif c == 'r':
            out.append('\r')
        elif c == 'f':
            out.append('\f')
        else:
            out.append(c)
    return ''.join(out)


def key_secrets():
    """Candidate secrets from the key file. Returns (secrets, unsupported); never returns the file.

    The builder reads this file with `java.util.Properties.load` — verified by disassembling
    `FluxtionConfigManager` in fluxtion-builder, not by reading the key file — so the file is DECODED
    the way Java decodes it rather than split on a separator. Raw lines are kept as candidates too:
    a key that leaks exactly as it appears on disk is caught even if the decoder is ever wrong.
    """
    if not KEY_FILE.exists():
        return set(), []
    raw = KEY_FILE.read_bytes()
    decoded, unsupported = _decode_properties(raw)
    secrets = {c for c in decoded if len(c) >= MIN_SECRET_LENGTH}
    for line in raw.decode('iso-8859-1').splitlines():   # belt and braces: the on-disk form
        line = line.strip()
        if line and line[0] not in '#!' and len(line) >= MIN_SECRET_LENGTH:
            secrets.add(line)
    return secrets, unsupported


KEY_PATH_PATTERN = re.compile(
    r'(cat|less|more|head|tail|print|echo|open|strings|xxd|od)\b[^\n]{0,80}fluxtion\.apiKeyFile')


def key_leak_scan(archive, base=None):
    """Search the run's own output AND what the subject wrote for the key. Never returns the secret.

    Two shapes are counted: the key material appearing verbatim, and a command reading the key path
    (how the observed leak happened). The second can be true with the first false when output was
    redirected, and it is still a hygiene failure.

    The project tree is scanned as well as the transcript because a project tree is exactly what gets
    preserved, attached or shared as evidence after a run — a key copied into a build log or a
    receipt would otherwise leave with it.
    """
    verdict = {'scannedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'keyFilePresent': KEY_FILE.exists(), 'secretOccurrences': 0, 'keyPathReads': 0,
               'files': {}, 'filesScanned': 0, 'filesSkipped': 0, 'unsupportedKeyLines': 0}
    secrets, unsupported = key_secrets()
    verdict['unsupportedKeyLines'] = len(unsupported)
    if not verdict['keyFilePresent']:
        verdict['note'] = 'key file absent; scan is vacuous'
        verdict['clean'] = None
        return verdict
    if unsupported:
        # Fail closed. A line this parser could not decode may hold a secret it is therefore not
        # searching for, so the scan cannot claim clean — it reports that it could not establish it.
        verdict['note'] = (f'{len(unsupported)} key-file line(s) use a form this parser does not '
                           'decode; the scan cannot establish clean')

    def scan(path, label):
        try:
            if path.stat().st_size > SCAN_MAX_BYTES:
                verdict['filesSkipped'] += 1
                return
            data = path.read_bytes()
        except OSError:
            verdict['filesSkipped'] += 1
            return
        if _looks_binary(data[:8192]):
            verdict['filesSkipped'] += 1
            return
        text = data.decode('utf-8', errors='replace')
        hits = sum(text.count(s) for s in secrets)
        reads = len(KEY_PATH_PATTERN.findall(text))
        verdict['filesScanned'] += 1
        if hits or reads:
            verdict['files'][label] = {'secretOccurrences': hits, 'keyPathReads': reads}
        verdict['secretOccurrences'] += hits
        verdict['keyPathReads'] += reads

    archive = Path(archive)
    for name in ('raw.jsonl', 'events.jsonl', 'stderr.log'):
        f = archive / name
        if f.exists():
            scan(f, name)

    if base:
        base = Path(base)
        for root in (base / 'project', base / 'tmp'):
            if not root.exists():
                continue
            for path in root.rglob('*'):
                if any(part in SCAN_SKIP_DIRS for part in path.parts):
                    continue
                if path.is_file() and not path.is_symlink():
                    scan(path, str(path.relative_to(base)))

    if unsupported:
        verdict['clean'] = None
    else:
        verdict['clean'] = verdict['secretOccurrences'] == 0 and verdict['keyPathReads'] == 0
    return verdict


def dir_size(path):
    total = 0
    path = Path(path)
    if not path.exists():
        return 0
    for f in path.rglob('*'):
        if f.is_file() and not f.is_symlink():
            try:
                total += f.stat().st_size
            except OSError:
                pass
    return total


def preflight(base, java_home):
    """Check the environment can support a run. Spends no key, launches no subject."""
    base = Path(base)
    tmp = base / 'tmp'
    jar = tmp / 'jar/analyser.jar'
    endpoint = tmp / 'home/.fluxtion-analyser/rest-endpoint'
    report = {'checkedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'base': str(base), 'javaHome': java_home, 'checks': {}}

    def record(name, ok, detail):
        report['checks'][name] = {'ok': bool(ok), 'detail': detail}

    record('analyserJar', jar.exists(),
           {'path': str(jar), 'sha256': sha256_file(jar) if jar.exists() else None})
    record('restEndpoint', endpoint.exists(),
           {'path': str(endpoint),
            'url': json.loads(endpoint.read_text()).get('url') if endpoint.exists() else None})
    # Presence only. The content is never read here, and never reaches this report.
    record('compilationKey', KEY_FILE.exists(), {'path': str(KEY_FILE), 'contentRead': False})
    record('trialToken', TRIAL_TOKEN.exists(), {'path': str(TRIAL_TOKEN), 'contentRead': False})
    record('javaHome', bool(java_home) and (Path(java_home) / 'bin/java').exists(),
           {'resolved': java_home, 'source': '/usr/libexec/java_home -v 21 unless overridden'})
    for binary, probe in (('sandbox-exec', '/usr/bin/sandbox-exec'), ('claude', CLAUDE_BIN),
                          ('mvn', shutil.which('mvn') or '')):
        record(binary, bool(probe) and Path(probe).exists(), {'path': probe})

    try:  # the published artefact the trial will acquire; digest recorded as the pristine copy
        # An explicit User-Agent is required, not cosmetic: the default Python-urllib agent is refused
        # with 403 by the CDN in front of the scaffold route, which would report the published
        # download as unavailable when a customer can fetch it perfectly well.
        request = urllib.request.Request(SCAFFOLD_URL, headers={'User-Agent': 'g14-preflight/1.0'})
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read()
        record('publicDownload', response.status == 200,
               {'url': SCAFFOLD_URL, 'status': response.status, 'bytes': len(body),
                'sha256': hashlib.sha256(body).hexdigest()})
    except Exception as exc:  # noqa: BLE001 — any failure here is a preflight failure
        record('publicDownload', False, {'url': SCAFFOLD_URL, 'error': str(exc)})

    report['ready'] = all(c['ok'] for c in report['checks'].values())
    base.mkdir(parents=True, exist_ok=True)
    out = base / 'preflight.json'
    out.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    print(f'\npreflight {"READY" if report["ready"] else "NOT READY"} — written to {out}')
    return 0 if report['ready'] else 1


def fail(message):
    """Refuse, visibly and with a non-zero exit.

    Not `assert`: `python3 -O` strips assertions, and these guards are the difference between
    preserving an interrupted run and silently truncating it.
    """
    print(f'g14_runner: {message}', file=sys.stderr)
    return 2


def run_trial(run, model, base, input_file, cap, java_home):
    base = Path(base)
    archive = base / run

    # Refuse to touch an archive that already holds anything. The old guard was meta.json, which is
    # written LAST, so a crash or Ctrl-C mid-run left no seal and a rerun reopened raw.jsonl with 'w'
    # and truncated the evidence PROTOCOL says is preserved.
    if archive.exists() and any(archive.iterdir()):
        return fail(f'{archive} is not empty — an existing or interrupted run is never overwritten')
    archive.mkdir(parents=True, exist_ok=True)

    tmp = base / 'tmp'
    home = str(tmp / 'home')
    jar = str(tmp / 'jar/analyser.jar')
    endpoint = tmp / 'home/.fluxtion-analyser/rest-endpoint'
    if not endpoint.exists():
        return fail(f'no analyser endpoint at {endpoint} — start the isolated instance first')
    port = json.loads(endpoint.read_text())['url'].rsplit(':', 1)[1]

    # G14 acquires its own project. The directory is created EMPTY on purpose: pre-staging anything
    # here would re-amputate the half of the journey this gate exists to measure.
    project = base / 'project'
    project.mkdir(parents=True, exist_ok=True)
    if any(project.iterdir()):
        return fail(f'{project} must be empty — the subject performs acquisition')

    repo_local, settings = maven_paths(base)
    repo_local.mkdir(parents=True, exist_ok=True)
    if not settings.exists():
        settings.write_text('<settings/>\n')

    (archive / 'isolation.sb').write_text(isolation_profile(base, port, java_home))
    mcp = {'mcpServers': {'fluxtion-analyser': {'type': 'stdio', 'command': java_home + '/bin/java',
                                                'args': ['-Duser.home=' + home, '-jar', jar, '--mcp']}}}
    env = child_env(base, tmp, home, java_home)
    command = ['/usr/bin/sandbox-exec', '-f', str(archive / 'isolation.sb'), CLAUDE_BIN,
               '-p', '--model', model, '--effort', 'medium', '--no-session-persistence',
               '--setting-sources', 'project,local', '--strict-mcp-config',
               '--mcp-config', json.dumps(mcp),
               # Built-in tools stay ON: the subject must download, unpack and build.
               '--permission-mode', 'dontAsk', '--no-chrome',
               '--output-format', 'stream-json', '--verbose',
               Path(input_file).read_text()]
    (archive / 'config.json').write_text(json.dumps({
        'run': run, 'model': model, 'base': str(base), 'port': port, 'capSeconds': cap,
        'jarSha256': sha256_file(jar), 'mcp': mcp, 'builtInTools': True,
        'keyGranted': True, 'gate': 'G14', 'javaHome': java_home,
        'mavenRepoLocal': str(repo_local), 'mavenSettings': str(settings)}, indent=1) + '\n')

    # Written BEFORE launch, so an interrupted run is identifiable as one rather than as a fresh start.
    (archive / 'started.json').write_text(json.dumps({
        'run': run, 'gate': 'G14',
        'startedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}, indent=2) + '\n')

    clock = ObserverClock()
    meta = {'run': run, 'requestedModel': model, 'gate': 'G14',
            'startedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    # 'x' not 'w': never reopen an existing transcript, even if the refusal above is ever loosened.
    with (archive / 'stderr.log').open('x') as err, (archive / 'raw.jsonl').open('x') as raw, \
            (archive / 'events.jsonl').open('x') as events:
        p = subprocess.Popen(command, cwd=project, env=env, stdout=subprocess.PIPE, stderr=err,
                             text=True, start_new_session=True)
        # Captured NOW, while the leader is certainly alive. Looking it up after the subject exits
        # raises, which used to be read as "the group is gone" while its children kept running.
        pgid = os.getpgid(p.pid)
        meta['processGroupId'] = pgid
        awake = subprocess.Popen(['/usr/bin/caffeinate', '-i', '-s', '-w', str(p.pid)],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print('Started', run, model, p.pid, pgid, flush=True)

        def capture():
            for line_no, line in enumerate(p.stdout, 1):
                raw.write(line)
                raw.flush()
                stamp = clock.sample()
                try:
                    event = json.loads(line)
                except json.JSONDecodeError:
                    continue
                events.write(json.dumps({'rawLine': line_no, **stamp, 'event': event}) + '\n')
                events.flush()

        thread = threading.Thread(target=capture)
        thread.start()
        while p.poll() is None:
            if clock.sample()['elapsedSeconds'] >= cap:
                meta['capped'] = True
                break
            time.sleep(.25)
        # Reap the GROUP, on the cap and after a normal exit alike: anything the subject backgrounded
        # outlives its leader otherwise, and the scan below would run while it is still writing.
        meta['processGroupReaped'] = reap_process_group(pgid)
        try:
            meta['exit'] = p.wait(timeout=20)
        except subprocess.TimeoutExpired:
            p.kill()
            meta['exit'] = p.wait()
        thread.join(timeout=10)
        if awake.poll() is None:
            awake.terminate()
            awake.wait()

    meta.update({'endedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                 'clock': clock.sample()})

    # What the subject actually acquired, for comparison with the pristine copy the operator preserved.
    zips = sorted(project.rglob('*.zip'))
    meta['acquired'] = [{'path': str(z.relative_to(project)), 'sha256': sha256_file(z)} for z in zips]
    meta['mavenRepoLocal'] = {'path': str(repo_local), 'bytes': dir_size(repo_local)}

    scan = key_leak_scan(archive, base)
    (archive / 'keyscan.json').write_text(json.dumps(scan, indent=2) + '\n')
    meta['keyScanClean'] = scan['clean']
    (archive / 'meta.json').write_text(json.dumps(meta, indent=2) + '\n')
    print(json.dumps(meta), flush=True)

    if scan['clean'] is False:
        print('\nKEY LEAK DETECTED — run preserved as evidence, NOT an acceptance. Rotate the key.',
              file=sys.stderr)
        return 3
    if scan['clean'] is None:
        print('\nKEY SCAN COULD NOT ESTABLISH CLEAN — not an acceptance.', file=sys.stderr)
        return 4
    # Cleanup that was not observed to succeed cannot be read as a pass: descendants may still hold
    # the key and the network, and the scan above ran while they could still be writing.
    if meta.get('processGroupReaped') is not True:
        print('\nPROCESS GROUP NOT CONFIRMED REAPED — not an acceptance; check for survivors in '
              f'group {meta.get("processGroupId")}.', file=sys.stderr)
        return 5
    return 0


def main():
    parser = argparse.ArgumentParser(description='G14 acceptance run from a real download')
    parser.add_argument('--preflight', action='store_true', help='check the environment; spend nothing')
    parser.add_argument('--base', help='per-run base directory under /private/tmp')
    parser.add_argument('--cap', type=int, default=DEFAULT_CAP_SECONDS, help='seconds (default 1800)')
    parser.add_argument('--java-home', help='JDK 21 home; default /usr/libexec/java_home -v 21')
    parser.add_argument('args', nargs='*', help='<run-name> <model> <base> <input-file>')
    ns = parser.parse_args()
    java_home = resolve_java_home(ns.java_home)

    if ns.preflight:
        if not ns.base:
            parser.error('--preflight needs --base')
        return preflight(ns.base, java_home)
    if len(ns.args) != 4:
        parser.error('need <run-name> <model> <base> <input-file>, or --preflight --base <dir>')
    if not java_home:
        return fail('no JDK 21 found — pass --java-home')
    return run_trial(ns.args[0], ns.args[1], ns.args[2], ns.args[3], ns.cap, java_home)


if __name__ == '__main__':
    sys.exit(main())
