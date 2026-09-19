#!/usr/bin/env python3
"""Check captured observations against the pre-registered business oracle; no model/key needed."""
import csv
import hashlib
from pathlib import Path

root = Path(__file__).resolve().parent
expected = [(0, 0., False, 0), (1, 10., False, 1), (1, 10., False, 1),
            (1, 10., True, 1), (1, 10., True, 1), (0, 0., False, 1), (1, 40., False, 2)]
for name in ('c', 'd'):
    folder = root / f'run-{name}'
    with (folder / 'state.csv').open() as stream:
        rows = list(csv.DictReader(stream))
    actual = [(int(r['processedCount']), float(r['lastProcessedPrice']),
               r['paused'] == 'true', int(r['sinkTotal'])) for r in rows]
    assert actual == expected, (name, actual)
    with (folder / 'checked.csv').open() as stream:
        sinks = [(float(r['price']), int(r['count'])) for r in csv.DictReader(stream)]
    assert sinks == [(10., 1), (40., 1)], (name, sinks)
    assert (folder / 'audit.yaml').read_text().count('\n---\n') == 11
    print(f'Run {name}: all seven state checkpoints, two ordered sink outputs, 12 audit records')
for line in (root / 'SHA256SUMS').read_text().splitlines():
    expected_hash, name = line.split('  ', 1)
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected_hash, name
print('Captured artifact hashes match. This checks saved evidence; it does not rerun the application.')
