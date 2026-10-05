#!/usr/bin/env python3
"""Read an exact GitHub run's job timings after completion; no runtime claims from scheduling estimates."""
import argparse
import datetime as dt
import json
import subprocess
from pathlib import Path

REPO = 'telaminai/fluxtionauditlog-analyser'


def seconds(start, end):
    return (dt.datetime.fromisoformat(end.replace('Z', '+00:00')) -
            dt.datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds()


def summarise(run, jobs):
    if run['status'] != 'completed' or any(j['status'] != 'completed' for j in jobs):
        raise ValueError('wait for the complete run; partial jobs are not timing evidence')
    rows = [{'name': j['name'], 'conclusion': j['conclusion'],
             'seconds': seconds(j['started_at'], j['completed_at']),
             'queueSeconds': seconds(j['created_at'], j['started_at']) if j.get('created_at') else None,
             'steps': [{'name': s['name'], 'conclusion': s['conclusion'],
                        'seconds': seconds(s['started_at'], s['completed_at'])}
                       for s in j.get('steps', []) if s.get('started_at') and s.get('completed_at')]}
            for j in jobs if j.get('started_at') and j.get('completed_at')]
    return {'run': run['html_url'], 'head': run['head_sha'], 'event': run['event'],
            'conclusion': run['conclusion'], 'elapsedSeconds': seconds(run['run_started_at'], run['updated_at']),
            'jobExecutionMinutes': sum(j['seconds'] for j in rows) / 60,
            'meaning': 'API wall-clock job durations, not billed minutes; steps include setup/upload when reported',
            'jobs': rows}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', required=True, type=int)
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    def api(path):
        return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/actions/runs/{args.run}' + path], text=True))
    run = api('')
    jobs = []
    page = 1
    while True:
        batch = api(f'/jobs?per_page=100&page={page}')['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    result = summarise(run, jobs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'jobs'}, indent=2))


if __name__ == '__main__':
    main()
