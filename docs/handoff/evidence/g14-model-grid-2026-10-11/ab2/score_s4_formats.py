"""S4' (disclosed after sealing, applied to every run alike): S4 over both audit-log shapes.

The sealed S4 split records on '---' documents. One run exported a single document holding an
`eventLogRecords:` list, so S4 saw one record and matched one symbol although the values were right. S4'
rewrites list items into '---' records and then runs the sealed S4 code unchanged.

Usage: score_s4_formats.py <base> <node>   prints JSON."""
import importlib.util, json, re, shutil, sys, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location('s', '/private/tmp/fx-g14-grid/score_g14.py')
s = importlib.util.module_from_spec(spec); spec.loader.exec_module(s)
base, node = Path(sys.argv[1]), sys.argv[2]
root = s.project_root(base / 'project')
with tempfile.TemporaryDirectory() as d:
    copy = Path(d) / 'root'
    (copy / 'logs').mkdir(parents=True)
    for log in root.rglob('logs/*.yaml'):
        t = log.read_text(errors='replace')
        if t.lstrip().startswith('eventLogRecords:'):
            t = re.sub(r'\n  - eventTime:', '\n---\n  eventTime:', t)
        (copy / 'logs' / log.name).write_text(t)
    print(json.dumps(s.s4_behaviour(copy, node)))
