#!/usr/bin/env python3
"""Machine-local Spring rehearsal. Private artifacts and generated sources stay outside Git."""
import argparse
import errno
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[1]
NS = '{http://maven.apache.org/POM/4.0.0}'
ET.register_namespace('', NS[1:-1])


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value)


def save(path, value):
    temporary = path.with_suffix('.tmp')
    write(temporary, json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def run(command, cwd, env, log):
    print(f'  {log.stem} …', flush=True)
    with log.open('w') as output:
        result = subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                                stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}); read {log}')


def identity(pid):
    # A shebang launcher can change its command string during exec; birth time stays stable.
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'stat=', '-o', 'lstart='],
                            capture_output=True, text=True)
    fields = result.stdout.strip().split(maxsplit=1)
    return fields[1] if result.returncode == 0 and len(fields) == 2 and not fields[0].startswith('Z') else ''


def alive(process):
    return bool(process.get('identity')) and identity(process['pid']) == process['identity']


def stop(state, statefile):
    for name, process in reversed(list(state.get('processes', {}).items())):
        if not alive(process):
            continue
        print(f'Stopping {name} (PID {process["pid"]})', flush=True)
        # Every service was started as a new session leader. Never kill by port/name alone.
        try:
            if os.getpgid(process['pid']) != process['pid']:
                raise RuntimeError(f'Refusing unexpected process group for {name}')
            os.killpg(process['pid'], signal.SIGTERM)
        except ProcessLookupError:
            continue
        deadline = time.monotonic() + 10
        while alive(process) and time.monotonic() < deadline:
            time.sleep(.2)
        if alive(process):
            os.killpg(process['pid'], signal.SIGKILL)
    state['processes'] = {}
    save(statefile, state)


def launch(name, command, cwd, env, state, statefile, logs):
    with (logs / f'{name}.log').open('a') as output:
        process = subprocess.Popen([str(x) for x in command], cwd=cwd, env=env,
                                   stdin=subprocess.DEVNULL, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    state.setdefault('processes', {})[name] = {'pid': process.pid, 'identity': identity(process.pid)}
    save(statefile, state)
    return process


def wait_for(check, label, state, timeout=60):
    deadline = time.monotonic() + timeout
    last = ''
    while time.monotonic() < deadline:
        for name, process in state['processes'].items():
            if not alive(process):
                raise RuntimeError(f'{name} stopped; inspect its service log')
        try:
            value = check()
            if value:
                return value
        except (OSError, ValueError, RuntimeError) as error:
            last = str(error)
        time.sleep(.3)
    raise RuntimeError(f'Timed out waiting for {label}: {last}')


def api(root, action, params):
    endpoint = json.loads((root / 'analyser-home/.fluxtion-analyser/rest-endpoint').read_text())
    request = urllib.request.Request(endpoint['url'] + '/action',
        data=json.dumps({'v': 1, 'action': action, 'params': params}).encode(),
        headers={'Content-Type': 'application/json', 'X-Analyser-Token': endpoint['token']})
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
    if result.get('error') or result.get('ok') is False:
        raise RuntimeError(str(result))
    return result


def http_ready(url):
    with urllib.request.urlopen(url, timeout=3) as response:
        return response.status == 200


def properties(path, values):
    def escape(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('=', '\\=').replace(':', '\\:')
    write(path, ''.join(f'{key}={escape(value)}\n' for key, value in values.items()))


def prepare_project(args, root, java_home, env, logs):
    project = root / 'project'
    if (project / '.demo-ready').exists():
        return project
    if project.exists():
        raise RuntimeError(f'Incomplete preparation at {project}; inspect it, then move it aside to retry')
    sample = args.sample.resolve()
    provider = sample / '.fluxtion/local-provider.jar'
    for required in (sample / 'RUNBOOK.md', sample / 'fluxtion-authoring.json', provider):
        if not required.is_file():
            raise RuntimeError(f'Missing local fixture prerequisite: {required}; use --sample')
    compiler = args.compiler.resolve()
    run([compiler / 'mvnw', '-q', '-o', '-pl', 'fluxtion-starter-core', '-am',
         '-DskipTests', 'package'], compiler, env, logs / 'compiler-build.log')
    version = ET.parse(compiler / 'pom.xml').getroot().findtext(NS + 'version')
    builder = compiler / f'fluxtion-builder/target/fluxtion-builder-{version}.jar'
    starter = compiler / f'fluxtion-starter-core/target/fluxtion-starter-core-{version}-all.jar'
    local_version = version.removesuffix('-SNAPSHOT') + '-demo-' + hashlib.sha256(
        builder.read_bytes() + starter.read_bytes()).hexdigest()[:12]
    pom = ET.parse(compiler / 'fluxtion-builder/.flattened-pom.xml')
    pom.getroot().find(NS + 'version').text = local_version
    pom.write(root / 'builder.pom', encoding='unicode')
    install = [compiler / 'mvnw', '-q', '-o', '-N', 'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file']
    run(install + [f'-Dfile={builder}', f'-DpomFile={root / "builder.pom"}'], compiler, env, logs / 'install-builder.log')
    run(install + [f'-Dfile={starter}', '-DgroupId=com.telamin.fluxtion', '-DartifactId=fluxtion-starter-core',
                  f'-Dversion={local_version}', '-Dclassifier=all', '-Dpackaging=jar', '-DgeneratePom=true'],
        compiler, env, logs / 'install-starter.log')
    shutil.copytree(sample, project, ignore=shutil.ignore_patterns(
        'target', '.git', '.idea', '.fluxtion', 'confirmation-run-*', '.mcp.json'))
    (project / '.fluxtion').mkdir()
    shutil.copy2(provider, project / '.fluxtion/local-provider.jar')
    record = json.loads((project / 'fluxtion-authoring.json').read_text())
    record['starterVersion'] = local_version
    save(project / 'fluxtion-authoring.json', record)
    pom = ET.parse(project / 'pom.xml')
    for dependency in pom.getroot().find(NS + 'dependencies'):
        if dependency.findtext(NS + 'artifactId') == 'fluxtion-builder':
            dependency.find(NS + 'version').text = local_version
    pom.write(project / 'pom.xml', encoding='unicode')
    # These are local demo wrappers around the downloaded scripts, not release-template changes.
    java_options = (f'-Dfluxtion.apiKeyFile="{project / "unused-key.properties"}" '
                    '-Dfluxtion.apiKey=MISSING_KEY -Dfluxtion.suppressApiKeyWarning=true')
    write(project / '.demo-env.sh', f'export JAVA_HOME={shlex.quote(str(java_home))}\n'
          'export PATH="$JAVA_HOME/bin:$PATH"\n'
          f'export JAVA_TOOL_OPTIONS={shlex.quote(java_options)}\n')
    for name in ('setup.sh', 'validate.sh', 'generate.sh', 'run.sh', 'check-fluxtion-key.sh'):
        path = project / name
        lines = path.read_text().splitlines(keepends=True)
        lines.insert(1, 'source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/.demo-env.sh"\n')
        path.write_text(''.join(lines))
    write(project / 'run-scenario.sh', '''#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
source .demo-env.sh
exec java -Djava.awt.headless=true -cp "target/classes:$(cat .fluxtion/classpath)" com.example.myapp.AcceptanceScenario "${1:-evidence/latest}"
''')
    (project / 'run-scenario.sh').chmod(0o755)
    for script in ('setup.sh', 'validate.sh', 'generate.sh', 'run-scenario.sh'):
        run(['bash', script], project, env, logs / f'project-{script}.log')
    receipt = json.loads((project / 'target/fluxtion-run.json').read_text())
    build = receipt['stages']['build']
    if not (build['route'] == 'local' and build['outcome'] == 'ok' and build['compilerRan']):
        raise RuntimeError(f'Unexpected sample build receipt: {build}')
    (project / '.demo-ready').write_text(local_version + '\n')
    return project


def describe(root, state):
    print(f'\nProject:    {root / "project"}\nRead first: {root / "project/LOCAL-DEMO.md"}'
          f'\nPlayground: {state["playground_url"]}\nDocs:       {state["docs_url"]}'
          f'\nLogs:       {root / "logs"}\nMCP:        {root / "project/.mcp.json"}'
          f'\nStop:       {REPO / "tools/stop-spring-demo.sh"} --root {shlex.quote(str(root))}', flush=True)


def start(args, root, state, statefile):
    if state.get('processes') and all(alive(p) for p in state['processes'].values()):
        api(root, 'context', {})
        print('Demo already running; project edits preserved.')
        describe(root, state)
        return
    stop(state, statefile)
    for port in (args.docs_port, args.web_port):
        with socket.socket() as sock:
            try:
                sock.bind(('127.0.0.1', port))
            except OSError as error:
                if error.errno == errno.EADDRINUSE:
                    raise RuntimeError(f'Port {port} is occupied; choose --docs-port / --web-port') from error
                raise
    java_home = args.java_home
    if java_home is None:
        java_home = Path(subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip())
    java_home = java_home.resolve()
    env = dict(os.environ, JAVA_HOME=str(java_home), PATH=str(java_home / 'bin') + ':' + os.environ['PATH'])
    env.pop('JAVA_TOOL_OPTIONS', None)
    logs = root / 'logs'
    logs.mkdir(exist_ok=True)
    project = prepare_project(args, root, java_home, env, logs)
    mkdocs = REPO / '.venv/bin/mkdocs'
    if not mkdocs.exists():
        run([sys.executable, '-m', 'venv', REPO / '.venv'], REPO, env, logs / 'docs-venv.log')
        run([REPO / '.venv/bin/pip', 'install', '-r', REPO / 'docs-requirements.txt'], REPO, env, logs / 'docs-install.log')
    run(['mvn', '-q', '-DskipTests', 'package'], REPO, env, logs / 'analyser-build.log')
    jar = root / 'analyser.jar'
    shutil.copy2(REPO / 'target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar', jar)
    profile = project / '.analyser/project.fluxtion-settings'
    if not profile.exists():
        properties(profile, {'share.version': 1, 'sourceRoot.count': 2,
            'sourceRoot.0': 'src/main/java', 'sourceRoot.1': '.',
            'eventProcessorFqn.count': 1, 'eventProcessorFqn.0': 'com.example.myapp.generated.MyProcessor',
            'selectedEventProcessor': 'com.example.myapp.generated.MyProcessor', 'runbook.count': 2,
            'runbook.0.name': 'local-demo', 'runbook.0.path': 'LOCAL-DEMO.md',
            'runbook.0.description': 'Local rehearsal, scenarios, evidence and MCP connection.',
            'runbook.1.name': 'spring-authoring', 'runbook.1.path': 'RUNBOOK.md',
            'runbook.1.description': 'Validate, reconcile and generate the Spring project.'})
    home = root / 'analyser-home'
    config = home / '.fluxtion-analyser/config'
    if not config.exists():
        properties(config, {'activeProjectPath': profile, 'recentProject.count': 1, 'recentProject.0': profile,
            'assistant.rest': 'true', 'assistant.exports': 'true', 'assistant.exportDir': project / 'evidence',
            'windowW': 1440, 'windowH': 950})
    bridge = [str(java_home / 'bin/java'), f'-Duser.home={home}', '-jar', str(jar), '--mcp']
    if not (project / '.mcp.json').exists():
        save(project / '.mcp.json', {'mcpServers': {'fluxtion-analyser': {
            'command': bridge[0], 'args': bridge[1:]}}})
    state['docs_url'] = f'http://127.0.0.1:{args.docs_port}/fluxtionauditlog-analyser/'
    state['playground_url'] = f'http://127.0.0.1:{args.web_port}/start'
    write(project / 'LOCAL-DEMO.md', f'''# Local Spring authoring rehearsal

This is a worked example, provisioned with branch artifacts and a local generation provider.
It is not the post-publication fresh-download acceptance run. The cached dependency BOM is
retained from the acceptance fixture; the builder and starter use a distinct local demo version.

Read RUNBOOK.md and authoring-docs/contract.md. Use JDK 21 (the scripts set it).
From this directory run `./validate.sh`, `./generate.sh`, then `./run-scenario.sh`.
After dependency changes run `./setup.sh` again. No paid generation key is used.
The scripts source .demo-env.sh, a local-only wrapper around the downloaded workflow.

The scenario checks seven states and exactly two outputs: Checked(10,1), Checked(40,1).
Filter 9 is ignored; pause stops processing; exported reset clears state and resumes it.
Results: evidence/latest/audit.yaml, state.csv, checked.csv. Repeat with
`./run-scenario.sh evidence/another-run` to retain a comparison run.

Open an MCP-capable client in this directory and enable the project .mcp.json entry.
The client must support that configuration format; it is not a global client registration.
Ask it to read this file, call analyser_context, predict a scenario and show evidence
in the analyser. Its project shell tools perform builds; the analyser renders results.
The demo analyser has its own home and recent-project list. Reports/screenshots may
be written under evidence/. Your regular analyser preferences are separate.
The profile authorises this sample's whole project directory for source, XML,
ownership-record and diagnostic reads so the analyser can check producer freshness.

Playground: {state['playground_url']}
Local contract: http://127.0.0.1:{args.web_port}/spring-authoring/contract.md
Docs: {state['docs_url']}spring-authoring/
For the browser preview import src/main/fluxtion/designer/application-context.xml.
A new browser download retains the unpublished release pin; it is not automatically
provisioned like this sample. That installation path remains a release gate.

Start again: {REPO / 'tools/start-spring-demo.sh'} --root {root}
Stop services: {REPO / 'tools/stop-spring-demo.sh'} --root {root}
Restart preserves project edits, the original evidence and analyser preferences.
Teardown stops owned services only; it retains files and local Maven test artifacts.
''')
    launch('docs', [mkdocs, 'serve', '-a', f'127.0.0.1:{args.docs_port}'], REPO, env, state, statefile, logs)
    launch('playground', ['pnpm', 'exec', 'vite', '--host', '127.0.0.1', '--port', str(args.web_port), '--strictPort'],
           args.web.resolve() / 'web', env, state, statefile, logs)
    launch('analyser', bridge[:-1] + ['--rest'], project, env, state, statefile, logs)
    wait_for(lambda: api(root, 'context', {}), 'analyser', state)
    # Explicit opens avoid discovery guesses and the known rolled-log/graph combined-open issue.
    api(root, 'open', {'project': str(profile)})
    api(root, 'open', {'log': str(project / 'evidence/latest/audit.yaml')})
    wait_for(lambda: api(root, 'context', {}).get('context', {}).get('log', {}).get('records', 0) > 0,
             'audit log load', state)
    api(root, 'open', {'graphml': str(project / 'src/main/resources/com/example/myapp/generated/MyProcessor.graphml'),
                       'processor': 'com.example.myapp.generated.MyProcessor'})
    api(root, 'open', {'design': str(project / 'src/main/fluxtion/designer/application-context.xml')})
    api(root, 'open', {'diagnostics': str(project / 'target/fluxtion-validation.json')})
    wait_for(lambda: http_ready(state['docs_url']), 'docs', state)
    wait_for(lambda: http_ready(state['playground_url']), 'playground', state)
    # Exercise the actual generated project MCP command, not just the REST transport.
    entry = json.loads((project / '.mcp.json').read_text())['mcpServers']['fluxtion-analyser']
    messages = [{'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
        'protocolVersion': '2025-11-25', 'capabilities': {}, 'clientInfo': {'name': 'local-demo-probe', 'version': '1'}}},
        {'jsonrpc': '2.0', 'method': 'notifications/initialized'},
        {'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': 'analyser_context', 'arguments': {}}}]
    probe = subprocess.run([entry['command']] + entry['args'], input=''.join(json.dumps(m) + '\n' for m in messages),
                           capture_output=True, text=True, timeout=30, env=env)
    write(logs / 'mcp-check.jsonl', probe.stdout)
    replies = [json.loads(line) for line in probe.stdout.splitlines()]
    result = next((r for r in replies if r.get('id') == 2), {})
    if probe.returncode or not result.get('result') or result.get('error') or result['result'].get('isError'):
        raise RuntimeError(f'MCP check failed; read {logs / "mcp-check.jsonl"}')
    context = api(root, 'context', {})
    canvas = context['context']
    if (canvas.get('project', {}).get('settings') != str(profile)
            or canvas.get('design', {}).get('file') != str(project / 'src/main/fluxtion/designer/application-context.xml')
            or not canvas.get('graphPairing', {}).get('graphPath')):
        raise RuntimeError('Canvas did not open the expected project, XML and graph')
    save(logs / 'context.json', context)
    print('Sample ready; docs, playground, analyser and project MCP probe passed.')
    describe(root, state)
    if not args.no_browser:
        subprocess.run(['open', state['playground_url'], state['docs_url'] + 'spring-authoring/'], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop'])
    parser.add_argument('--root', type=Path, default=Path('/tmp/fluxtion-spring-demo'))
    parser.add_argument('--compiler', type=Path, default=Path('/tmp/fluxtion-spring-authoring-compiler'))
    parser.add_argument('--web', type=Path, default=Path('/tmp/fluxtion-spring-authoring-web'))
    parser.add_argument('--sample', type=Path, default=Path('/tmp/spring-authoring-acceptance/run1/observer/confirmation-2'))
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--docs-port', type=int, default=8000)
    parser.add_argument('--web-port', type=int, default=5174)
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    root = args.root.resolve()
    if args.action == 'stop' and not root.exists():
        print('Demo is not running.')
        return
    marker = root / '.spring-demo'
    if root.exists() and not marker.exists():
        raise RuntimeError(f'Refusing unmanaged directory {root}; choose a new --root')
    root.mkdir(parents=True, exist_ok=True)
    root.chmod(0o700)
    marker.touch()
    with (root / 'control.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        statefile = root / 'services.json'
        state = json.loads(statefile.read_text()) if statefile.exists() else {}
        if args.action == 'stop':
            stop(state, statefile)
            print(f'Services stopped. Project and evidence retained at {root / "project"}')
        else:
            try:
                start(args, root, state, statefile)
            except BaseException:
                stop(state, statefile)
                raise


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        sys.exit(str(error))
