#!/usr/bin/env python3
"""Built-jar recovery witness across independent JVMs, no model/client or real profile.

The tiny launch shim calls the shipped Main.main and translates stdin EOF into the
normal window-closing event. It does not read or modify the app's internal state.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('api_probe', ROOT / 'tools/verify-m46-agent-api.py')
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
SHIM = '''import javax.swing.SwingUtilities;
import java.awt.Window;
import java.awt.event.WindowEvent;
public class RestartProbe {
 public static void main(String[] args) throws Exception {
  telamin.fluxtion.audit.analyser.Main.main(args);
  while (System.in.read() != -1) { }
  SwingUtilities.invokeAndWait(() -> {
   for (Window w : Window.getWindows())
    if (w instanceof telamin.fluxtion.audit.analyser.analyser.ui.MainFrame)
     w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING));
  });
 }
}
'''

class Process(api.Analyser):
    def __init__(self, jar, home, output, shim, args=()):
        super().__init__(str(jar), str(home), output.name)
        self.out, self.shim, self.args = str(output), shim, args

    def __enter__(self):
        with open(self.out, 'w') as output:
            self.proc = subprocess.Popen(['java', '-Duser.home=' + self.home, '-cp',
                self.jar + ':' + str(self.shim), 'RestartProbe', '--rest', *self.args],
                stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            for line in Path(self.out).read_text().splitlines():
                if 'X-Analyser-Token:' in line:
                    self.url = 'http://' + line.split('http://')[1].split()[0]
                    self.token = line.split('X-Analyser-Token:')[1].strip()
                    return self
            if self.proc.poll() is not None:
                raise AssertionError('JVM exited during startup; inspect local stdout')
            time.sleep(.2)
        raise AssertionError('JVM startup timed out')

    def __exit__(self, *exc):
        if self.proc and self.proc.poll() is None:
            self.proc.stdin.close()
            try:
                self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait()
                raise AssertionError('Normal window-close failed to terminate JVM')
            assert self.proc.returncode == 0, self.proc.returncode

    def wait(self, predicate):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            c = self.context()
            if predicate(c):
                return c
        raise AssertionError(json.dumps(c, indent=2))

    def ok(self, verb, **params):
        reply = self.act(verb, **params)
        assert reply.get('ok'), reply
        return reply


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT / 'target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    work = args.output or Path(tempfile.mkdtemp(prefix='analyser-process-restart-'))
    work.mkdir(parents=True, exist_ok=True)
    assert not (work / 'home').exists(), 'Use a fresh output directory'
    home = work / 'home'
    home.mkdir()
    project = work / 'project'
    (project / '.analyser').mkdir(parents=True)
    profile = project / '.analyser/project.fluxtion-settings'
    profile.write_text('share.version=1\nsourceRoot.count=1\nsourceRoot.0=.\n')
    log = Path(shutil.copy(ROOT / 'src/test/resources/sample.yml', project / 'run.yml'))
    graph = project / 'processor.graphml'
    graph.write_text("<graphml xmlns='http://graphml.graphdrawing.org/xmlns' xmlns:jGraph='http://www.jgraph.com/'>"
        "<key id='vertex_label' for='node' attr.name='nodeData' attr.type='string'/>"
        "<graph edgedefault='undirected'><node id='child'><data key='vertex_label'>"
        "<jGraph:ShapeNode><jGraph:Geometry height='70' width='160' x='20' y='20'/>"
        "<jGraph:label text='id:child&#10;class:com.acme.Child'/><jGraph:Style properties='NODE'/>"
        "</jGraph:ShapeNode></data></node></graph></graphml>")
    design = project / 'design.xml'
    design.write_text("<beans><bean id='child' class='com.acme.Child'/></beans>")
    diagnostics = project / 'fluxtion-validation.json'
    diagnostics.write_text(json.dumps({'contractVersion': '1.0', 'valid': True,
        'diagnosticReport': {'diagnosticsVersion': '1.0', 'diagnostics': []}}))
    shim = work / 'shim'
    shim.mkdir()
    (shim / 'RestartProbe.java').write_text(SHIM)
    subprocess.run(['javac', '-cp', str(args.jar), str(shim / 'RestartProbe.java')], check=True)
    pids = []
    def record(name, context):
        (work / (name + '.json')).write_text(json.dumps(context, indent=2) + '\n')
    with Process(args.jar, home, work / 'first.log', shim) as a:
        pids.append(a.proc.pid)
        a.ok('open', project=str(profile))
        a.ok('open', log=str(log), format='yaml')
        a.wait(lambda c: 'log' in c and not c.get('graphPairing', {}).get('loading'))
        a.ok('open', graphml=str(graph))
        a.ok('open', design=str(design))
        a.ok('open', diagnostics=str(diagnostics))
        a.ok('topology', select='child', scope='node', focus=True)
        a.ok('goto', recordIndex=5)
        before = a.context()
        assert before['topology']['recordIndex'] == 5, before
        assert len(before['selection']) == 1, before
        record('before-quit', before)
    with Process(args.jar, home, work / 'second.log', shim) as a:
        pids.append(a.proc.pid)
        offered = a.wait(lambda c: c.get('restoration', {}).get('state') == 'offered')
        assert 'log' not in offered, offered
        assert 'file' not in offered.get('design', {}), offered
        record('after-relaunch-offer', offered)
        a.ok('open', restore='last')
        restored = a.wait(lambda c: c.get('restoration', {}).get('state') == 'finished')
        assert restored['log']['path'] == str(log.resolve()), restored
        assert restored['log']['openedBy'] == 'explicit session restore', restored
        assert restored['design']['file'] == str(design.resolve()), restored
        assert restored['design']['diagnosticsFile'] == str(diagnostics.resolve()), restored
        assert restored['topology']['contextDepth'] == 1, restored
        assert restored['selection'] == before['selection'], restored
        assert restored['topology']['recordIndex'] == 5, restored
        record('after-explicit-restore', restored)
    alternate = Path(shutil.copy(log, project / 'explicit.yml'))
    with Process(args.jar, home, work / 'third.log', shim, [str(alternate)]) as a:
        pids.append(a.proc.pid)
        offered = a.wait(lambda c: c.get('log', {}).get('path') == str(alternate.resolve())
                         and c.get('restoration', {}).get('state') == 'offered')
        assert 'file' not in offered.get('design', {}), offered
        a.ok('open', restore='dismiss')
        dismissed = a.context()
        assert dismissed['restoration']['state'] == 'dismissed', dismissed
        assert dismissed['log']['path'] == str(alternate.resolve()), dismissed
        assert not a.act('open', restore='last').get('ok'), 'Dismissed offer was accepted'
        record('explicit-cli-and-dismiss', dismissed)
    assert len(set(pids)) == 3, pids
    result = {'status': 'PASS', 'independentJvms': len(pids), 'checks': [
        'normal quit persists session', 'relaunch offers without opening inputs',
        'explicit restore reloads log/topology/design/diagnostics/selection/focus',
        'CLI input does not grant restore permission', 'dismiss keeps CLI input and refuses restore'],
        'scope': 'Shipped Main startup and window-close path; isolated home; no model client'}
    record('result', result)
    print(json.dumps(result, indent=2))
    print('Local evidence:', work)

if __name__ == '__main__':
    main()
