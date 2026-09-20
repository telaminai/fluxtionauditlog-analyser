#!/usr/bin/env python3
"""Check exported evidence, not client testimony or hidden/local project state."""
from pathlib import Path
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    result = []
    for block in path.read_text().split('---'):
        if not re.search(r'^    event: PriceEvent$', block, re.M):
            continue
        match = re.search(r'eventToString: PriceEvent\{symbol=([^,]+), price=([^,]+), volume=([^}]+)\}', block)
        assert match, path
        row = (match[1], float(match[2]), int(match[3]))
        fields = re.search(r'- rootNode: .*\}, price: ([^,]+), volume: ([^}]+)\}', block)
        assert fields and (float(fields[1]), int(fields[2])) == row[1:], path
        result.append(row)
    return result


def main():
    seal = json.loads((HERE / 'protocol-seal.json').read_text())
    for name, sha in seal['files'].items():
        assert digest(HERE / name) == sha, f'protocol changed: {name}'
    manifest = json.loads((HERE / 'manifest.json').read_text())
    for item in manifest['files']:
        assert digest(HERE / item['file']) == item['publishedSha256'], item['file']
    expected = {
        'A': [('AAPL', 195.3, 1200), ('MSFT', 410.1, 800), ('GOOG', 155.75, 1500), ('AMZN', 178.22, 640), ('NVDA', 905.6, 2100)],
        'B': [('ACME', 10.25, 3), ('ACME', 10.25, 3), ('BETA', 20.5, 7)],
        'C': [('ZERO', 0., 0), ('NEG', -12.5, -4), ('POS', 12.5, 4)],
        'D': [('SMALL', .125, 1), ('LARGE', 1000000.25, 2000000000), ('TAIL', .5, 2)],
        'E': [('THIRD', 3., 30), ('FIRST', 1., 10), ('SECOND', 2., 20)],
        'F': [('ONLY', 7.75, 11)],
    }
    for trial in range(1, 4):
        for scenario, oracle in expected.items():
            assert rows(HERE / f'client-{trial}/audit-{scenario}.yaml') == oracle, (trial, scenario)
    generation = (HERE / 'generation/audit-preview-smoke.yaml').read_text()
    assert [int(x) for x in re.findall(r'totalVolume: (\d+)', generation)] == [1200, 2000, 3500, 4140, 6240]
    print('PASS: protocol seal, exported hashes, 54 client business rows and 5 regenerated totals')
    print('Not checked here: local original-file identities, credential route, elapsed-time causality, shutdown or manual source attribution.')


if __name__ == '__main__':
    main()
