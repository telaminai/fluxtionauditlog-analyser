#!/usr/bin/env python3
"""Supervise an isolated analyser and a dependent acceptance command; never auto-restart."""
import argparse
import datetime
import json
from pathlib import Path
import subprocess
import time
import urllib.request


def health(process, endpoint):
    """A file alone is not a live server. Return only non-secret health information."""
    code = process.poll()
    if code is not None:
        raise RuntimeError(f'analyser exited: {code}')
    try:
        value = json.loads(endpoint.read_text())
    except (OSError, ValueError) as error:
        raise RuntimeError('analyser endpoint missing or unreadable') from error
    if value.get('pid') != process.pid:
        raise RuntimeError('analyser endpoint belongs to another process')
    request = urllib.request.Request(value['url'] + '/manifest',
                                     headers={'X-Analyser-Token': value['token']})
    try:
        with urllib.request.urlopen(request, timeout=3) as reply:
            manifest = json.load(reply)
    except Exception as error:
        raise RuntimeError('analyser manifest request failed') from error
    if not isinstance(manifest, dict) or not manifest:
        raise RuntimeError('analyser manifest was empty or invalid')
    return {'pid': process.pid, 'manifest': 'ok'}


def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def configure_exchange(home, exchange):
    """Explicit operator opt-in in the isolated profile, before launching the app."""
    exchange.mkdir(parents=True, exist_ok=True)
    config = home / '.fluxtion-analyser/config'
    config.parent.mkdir(parents=True, exist_ok=True)
    lines = config.read_text().splitlines() if config.exists() else []
    lines = [line for line in lines if not line.startswith(('assistant.exports=', 'assistant.exportDir='))]
    value = str(exchange.resolve()).replace('\\', '\\\\').replace(':', '\\:')
    if '\n' in value or '\r' in value:
        raise ValueError('exchange path contains a newline')
    config.write_text('\n'.join(lines + ['assistant.exports=true', 'assistant.exportDir=' + value]) + '\n')


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    if getattr(args, "exchange", None) is not None:
        configure_exchange(args.home, args.exchange)
    start = time.monotonic()
    client = None
    def record(kind, **data):
        with (args.output / 'health.jsonl').open('a') as stream:
            stream.write(json.dumps({'kind': kind, 'elapsed': time.monotonic()-start,
                'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), **data})+'\n')
    with (args.output / 'analyser.log').open('w') as log:
        server = subprocess.Popen([args.java, '-Duser.home='+str(args.home), '-jar',
            str(args.jar), '--rest'], stdin=subprocess.DEVNULL, stdout=log,
            stderr=subprocess.STDOUT, start_new_session=True)
    record('started', pid=server.pid)
    endpoint = args.home / '.fluxtion-analyser/rest-endpoint'
    try:
        while True:
            try:
                record('ready', **health(server, endpoint))
                break
            except RuntimeError:
                if server.poll() is not None or time.monotonic()-start > 60:
                    raise
                time.sleep(.2)
        with (args.output / 'client.log').open('w') as log:
            client = subprocess.Popen(args.command, stdin=subprocess.DEVNULL,
                stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        record('client-started', pid=client.pid)
        while True:
            record('healthy', **health(server, endpoint))
            code = client.poll()
            if code is not None:
                record('client-ended', exit=code)
                return code
            if time.monotonic()-start > args.seconds:
                raise RuntimeError('supervised run exceeded its deadline')
            time.sleep(2)
    except Exception as error:
        record('failure', error=str(error))
        return 1
    finally:
        stop(client)
        record('operator-stop', pid=server.pid)
        stop(server)
        record('stopped', exit=server.returncode)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java', required=True)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--home', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--seconds', type=int, default=900)
    p.add_argument('--exchange', type=Path, help='enable exports only in this explicitly selected isolated home')
    p.add_argument('command', nargs=argparse.REMAINDER)
    a = p.parse_args()
    if a.command[:1] == ['--']:
        a.command = a.command[1:]
    if not a.command:
        p.error('a dependent command is required')
    raise SystemExit(run(a))
