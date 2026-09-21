#!/usr/bin/env python3
"""Capture real Spring design/validation views from a copied public starter.

Requires macOS screen recording permission, Java 21, a released analyser jar and
the publicly downloaded starter jar. No generation or application execution.
Only the disposable copy is edited; no user profile is read.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

REPO = Path(__file__).resolve().parent.parent


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, REPO / 'tools' / filename)
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--starter-jar', required=True, type=Path)
    args = parser.parse_args()
    capture = module('spring_doc_capture', 'capture-docs.py')
    probe = module('spring_doc_probe', 'verify-m46-agent-api.py')
    work = Path(tempfile.mkdtemp(prefix='spring-authoring-docs-', dir='/private/tmp'))
    project = work / 'spring-first-project'
    shutil.copytree(args.project, project, ignore=shutil.ignore_patterns('target', '.git', '.fluxtion'))
    home, exports = work / 'home', work / 'captures'
    (home / '.fluxtion-analyser').mkdir(parents=True)
    exports.mkdir()
    (project / 'target').mkdir()
    (home / '.fluxtion-analyser/config').write_text(
        'assistant.rest=true\nassistant.exports=true\n'
        f'assistant.exportDir={exports}\ntheme=Light\n'
        'windowW=1680\nwindowH=940\nwindowX=20\nwindowY=20\n'
        'eventFilterCollapsed=true\nprojectPanelCollapsed=true\n')
    xml = project / 'src/main/fluxtion/designer/application-context.xml'
    original = xml.read_text()
    transcript = []

    def validate(expected):
        result = subprocess.run(['java', '-jar', str(args.starter_jar.resolve()), 'validate',
                                 '--summary-detail'], cwd=project, capture_output=True, text=True)
        transcript.append({'command': 'published starter validate --summary-detail',
                           'exit': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
        assert (result.returncode == 0) == expected, result.stdout + result.stderr
        return json.loads((project / 'target/fluxtion-validation.json').read_text())

    try:
        validate(True)
        with probe.Analyser(str(args.jar.resolve()), str(home), 'spring-guide') as app:
            def act(action, **params):
                result = app.act(action, **params)
                transcript.append({'action': action, 'params': params, 'result': result})
                assert result.get('ok'), result
                return result

            def shot(name):
                act('screenshot', path=name)
                ep = json.loads((home / '.fluxtion-analyser/rest-endpoint').read_text())
                capture.raise_window(ep['pid'])
                time.sleep(0.5)
                wid = capture.window_id(ep['pid'])
                assert wid is not None, 'No isolated window capture available; refusing region capture'
                target = exports / ('native-' + name)
                subprocess.run(['screencapture', '-x', '-o', '-l', str(wid), str(target)], check=True)
                assert target.stat().st_size > 0
                shutil.copy(target, REPO / 'docs/site/assets' / name)

            act('open', project=str(project / '.analyser/project.fluxtion-settings'))
            shot('spring-project-landing.png')
            act('source_root', add=[str(xml.parent), str(project / 'target')])
            act('open', design=str(xml))
            act('source', bean='riskEngine')
            shot('spring-design.png')
            # A real keyless validator refusal, not a hand-written diagnostic fixture.
            xml.write_text(original.replace('<value>orderGate</value>', '<value>missingNode</value>', 1))
            invalid = validate(False)
            assert invalid.get('diagnosticReport', {}).get('diagnostics'), invalid
            act('open', design=str(xml))
            act('open', diagnostics=str(project / 'target/fluxtion-validation.json'))
            shot('spring-validation-finding.png')
            xml.write_text(original)
            validate(True)
            act('open', design=str(xml))
            act('open', diagnostics=str(project / 'target/fluxtion-validation.json'))
            shot('spring-validation-clean.png')
        print('PASS: real project landing, design, validator refusal and corrected validation')
    finally:
        xml.write_text(original)
        (work / 'transcript.json').write_text(json.dumps(transcript, indent=2) + '\n')
        print('Evidence:', work)


if __name__ == '__main__':
    main()
