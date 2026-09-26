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
JAVA_HOME = '/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home'
CLAUDE_BIN = str(Path.home() / '.local/bin/claude')
SCAFFOLD_URL = 'https://fluxtion-playground.dev/start/scaffold?template=analyser-bundle'
DEFAULT_CAP_SECONDS = 1800  # the interrupted 1.0.74 attempt ran 869s and never reached a chart


def sha256_file(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def isolation_profile(base, port):
    """Per-run sandbox profile.

    Deliberately weaker than the cold-start profile in three places, each forced by the journey:
    built-in tools need a working filesystem, acquisition needs outbound network, and ./generate.sh
    needs the key. What is NOT relaxed: the source tree stays unreadable, every other analyser
    instance stays unreachable, and ~/.fluxtion stays invisible apart from the key file itself.
    """
    home = str(Path.home())
    return f'''(version 1)
(allow default)
(deny file-read*
 (require-all (subpath "{home}")
  (require-not (subpath "{home}/.local/bin"))
  (require-not (subpath "{home}/.local/share/claude"))
  (require-not (subpath "{home}/Library/Java"))
  (require-not (subpath "{home}/Library/Keychains"))
  (require-not (subpath "{home}/.m2"))
  (require-not (literal "{KEY_FILE}"))
  (require-not (literal "{home}/Library/Preferences/com.apple.security.plist")))
 (require-all (subpath "/private/tmp")
  (require-not (subpath "{base}/project"))
  (require-not (subpath "{base}/tmp"))))
(allow file-read-metadata)
(deny file-write*
 (require-all
  (require-not (subpath "{base}/project"))
  (require-not (subpath "{base}/tmp"))
  (require-not (subpath "{home}/.m2"))
  (require-not (subpath "/private/var/folders"))
  (require-not (subpath "/dev"))))
(deny network-outbound
 (require-all (remote ip "localhost:*")
  (require-not (remote ip "localhost:{port}"))))
(allow network-outbound (remote tcp "localhost:{port}"))
(deny file-read* (subpath "{home}/.fluxtion/") (require-not (literal "{KEY_FILE}")))
'''


def child_env(base, tmp, home):
    """Scrubbed environment, then the trial token.

    Provider and Fluxtion variables are dropped so nothing leaks in by inheritance; the key reaches the
    build through the key FILE, never through the environment. FLUXTION_API_KEY is not read by the
    build in any case.
    """
    env = {
        k: v for k, v in os.environ.items()
        if not k.startswith(('CODEX_', 'CLAUDE', 'ANTHROPIC', 'OPENAI', 'FLUXTION', 'RAPIDAPI'))
        and k not in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'CLASSPATH')
    }
    env.update({
        'TMPDIR': str(tmp), 'JAVA_HOME': JAVA_HOME, 'HOME': home,
        'USER': os.environ.get('USER', 'greg'), 'LOGNAME': os.environ.get('LOGNAME', 'greg'),
        'DISABLE_AUTOUPDATER': '1', 'CLAUDE_CODE_TMPDIR': str(tmp), 'CLAUDE_TMPDIR': str(tmp),
    })
    env['PATH'] = JAVA_HOME + '/bin:' + env['PATH']
    env['CLAUDE_CODE_OAUTH_TOKEN'] = TRIAL_TOKEN.read_text().strip()  # trial-only; never printed
    return env


def key_leak_scan(archive):
    """Search the run's own output for the key. Returns a verdict; never returns the secret.

    Two shapes are counted: the key material appearing verbatim, and a command reading the key path
    (how the observed leak happened — `cat ~/.fluxtion/fluxtion.apiKeyFile`). The second can be true
    with the first false when output was redirected, and it is still a hygiene failure.
    """
    verdict = {'scannedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'keyFilePresent': KEY_FILE.exists(), 'secretOccurrences': 0, 'keyPathReads': 0,
               'files': {}}
    if not KEY_FILE.exists():
        verdict['note'] = 'key file absent; scan is vacuous'
        verdict['clean'] = None
        return verdict

    secrets = set()
    for line in KEY_FILE.read_text(errors='replace').splitlines():
        line = line.strip()
        if '=' in line:
            line = line.split('=', 1)[1].strip()
        if len(line) >= 12:  # ignore short/structural lines that would match everything
            secrets.add(line)
    path_pattern = re.compile(r'(cat|less|more|head|tail|print|echo|open)\b[^\n]{0,80}fluxtion\.apiKeyFile')

    for name in ('raw.jsonl', 'events.jsonl', 'stderr.log'):
        f = archive / name
        if not f.exists():
            continue
        text = f.read_text(errors='replace')
        hits = sum(text.count(s) for s in secrets)
        reads = len(path_pattern.findall(text))
        verdict['files'][name] = {'secretOccurrences': hits, 'keyPathReads': reads}
        verdict['secretOccurrences'] += hits
        verdict['keyPathReads'] += reads

    verdict['clean'] = verdict['secretOccurrences'] == 0 and verdict['keyPathReads'] == 0
    return verdict


def preflight(base):
    """Check the environment can support a run. Spends no key, launches no subject."""
    base = Path(base)
    tmp = base / 'tmp'
    jar = tmp / 'jar/analyser.jar'
    endpoint = tmp / 'home/.fluxtion-analyser/rest-endpoint'
    report = {'checkedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'base': str(base), 'checks': {}}

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
    for binary, probe in (('sandbox-exec', '/usr/bin/sandbox-exec'), ('claude', CLAUDE_BIN),
                          ('java', JAVA_HOME + '/bin/java'), ('mvn', shutil.which('mvn') or '')):
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
    out = base / 'preflight.json'
    base.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    print(f'\npreflight {"READY" if report["ready"] else "NOT READY"} — written to {out}')
    return 0 if report['ready'] else 1


def run_trial(run, model, base, input_file, cap):
    base = Path(base)
    archive = base / run
    archive.mkdir(parents=True, exist_ok=True)
    assert not (archive / 'meta.json').exists(), 'sealed trial cannot be rerun'

    tmp = base / 'tmp'
    home = str(tmp / 'home')
    jar = str(tmp / 'jar/analyser.jar')
    port = json.loads((tmp / 'home/.fluxtion-analyser/rest-endpoint').read_text())['url'].rsplit(':', 1)[1]

    # G14 acquires its own project. The directory is created EMPTY on purpose: pre-staging anything
    # here would re-amputate the half of the journey this gate exists to measure.
    project = base / 'project'
    project.mkdir(parents=True, exist_ok=True)
    assert not any(project.iterdir()), f'{project} must be empty — the subject performs acquisition'

    (archive / 'isolation.sb').write_text(isolation_profile(base, port))
    mcp = {'mcpServers': {'fluxtion-analyser': {'type': 'stdio', 'command': JAVA_HOME + '/bin/java',
                                                'args': ['-Duser.home=' + home, '-jar', jar, '--mcp']}}}
    env = child_env(base, tmp, home)
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
        'keyGranted': True, 'gate': 'G14'}, indent=1) + '\n')

    clock = ObserverClock()
    meta = {'run': run, 'requestedModel': model, 'gate': 'G14',
            'startedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    with (archive / 'stderr.log').open('w') as err, (archive / 'raw.jsonl').open('w') as raw, \
            (archive / 'events.jsonl').open('w') as events:
        p = subprocess.Popen(command, cwd=project, env=env, stdout=subprocess.PIPE, stderr=err,
                             text=True, start_new_session=True)
        awake = subprocess.Popen(['/usr/bin/caffeinate', '-i', '-s', '-w', str(p.pid)],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print('Started', run, model, p.pid, flush=True)

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
                p.terminate()
                break
            time.sleep(.25)
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

    scan = key_leak_scan(archive)
    (archive / 'keyscan.json').write_text(json.dumps(scan, indent=2) + '\n')
    meta['keyScanClean'] = scan['clean']
    (archive / 'meta.json').write_text(json.dumps(meta, indent=2) + '\n')
    print(json.dumps(meta), flush=True)

    if scan['clean'] is False:
        print('\nKEY LEAK DETECTED — run preserved as evidence, NOT an acceptance. Rotate the key.',
              file=sys.stderr)
        return 2
    return 0


def main():
    parser = argparse.ArgumentParser(description='G14 acceptance run from a real download')
    parser.add_argument('--preflight', action='store_true', help='check the environment; spend nothing')
    parser.add_argument('--base', help='per-run base directory under /private/tmp')
    parser.add_argument('--cap', type=int, default=DEFAULT_CAP_SECONDS, help='seconds (default 1800)')
    parser.add_argument('args', nargs='*', help='<run-name> <model> <base> <input-file>')
    ns = parser.parse_args()

    if ns.preflight:
        if not ns.base:
            parser.error('--preflight needs --base')
        return preflight(ns.base)
    if len(ns.args) != 4:
        parser.error('need <run-name> <model> <base> <input-file>, or --preflight --base <dir>')
    return run_trial(ns.args[0], ns.args[1], ns.args[2], ns.args[3], ns.cap)


if __name__ == '__main__':
    sys.exit(main())
