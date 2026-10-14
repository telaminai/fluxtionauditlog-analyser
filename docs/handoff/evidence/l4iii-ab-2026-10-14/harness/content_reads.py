"""Generated-code CONTENT reads in one transcript (post-hoc reporting measure, added 2026-10-13 after the release
ladder was sealed; it does not affect PASS or the sealed scoring). Prints JSON.

Usage: content_reads.py <raw.jsonl> <run base>

measure_reads.py counts a call when its command or path NAMES generated code (`/generated/`, the processor or GraphML
file, the builder, or a script source read with cat/sed/head/less), so a directory listing of generated/ or a Write of
the author's own file whose path matches counts. This counts a call only when its TOOL RESULT carries generated
content, judged by markers the generator writes and an author never does:

  processorHeader   the generated processor's stamp (`target generator version`, `analyser version          :`)
  dispatchCode      generated dispatch identifiers: `isDirty_…`, `guardCheck_…` or `initialiseAuditor(`
  graphml           GraphML content: `<graphml` or `<node id=` with `<edge`
  builderSource     the builder's source: `class SpringAotBuilder` / `MarketProcessorSupplier` bodies
  pathNamedRead     (added 2026-10-14, review of PR #140) a read verb (cat, grep, sed, head, tail, awk, nl, less, more)
                    whose argument is a file under `generated/` ending .java or .graphml (a glob counts), with a
                    non-empty, non-error result. This catches a grep or sed slice, or a `head -c`, of the processor
                    that carries no stamp, dispatch identifier or GraphML tag. A listing (`ls`) does not count, and
                    neither does a grep that returns only counts or names (`-c`, `-l`, `-L`, `-q`).

A call can match several markers. `partial` is true when the only marker is pathNamedRead (a slice of the file, not
its stamp, dispatch code, GraphML or builder body).

A call whose result is the tool acknowledging a Write/Edit is never counted. Each counted call is listed by its
command SHAPE: the tool, the verb and the target with the run base replaced by `./`, other temporary paths by
`<tmp>/` and the home directory by `~`, cut to 140 characters. No result content is reported.
"""
import json, re, sys
from pathlib import Path

MARKERS = {
    'processorHeader': re.compile(r'target generator version\s*:|analyser version\s{3,}:'),
    'dispatchCode': re.compile(r'\bisDirty_\w+|\bguardCheck_\w+|initialiseAuditor\('),
    'graphml': re.compile(r'<graphml\b|<node id="[^"]+"[\s\S]{0,400}<edge\b'),
    'builderSource': re.compile(r'class\s+(SpringAotBuilder|\w+ProcessorSupplier)\b[\s\S]{0,200}\{'),
}
GENREAD = re.compile(r'\b(cat|grep|sed|head|tail|awk|nl|less|more)\b[^|;&\n]*generated/[^\s|;&]*\.(java|graphml)\b')
EMPTY = re.compile(r'^\s*$|No such file|cannot open|No matches found', re.I)


def shape(name, inp, base):
    s = inp.get('command') or inp.get('file_path') or inp.get('path') or inp.get('pattern') or ''
    s = str(s).replace(str(base) + '/project/', './').replace(str(base) + '/project', '.').replace(str(base), '<base>')
    s = re.sub(r'/private/tmp/[^\s"\']*', '<tmp>/…', s)
    s = s.replace(str(Path.home()), '~')
    s = re.sub(r'\s+', ' ', s)
    return f'{name}: {s[:140]}'


def main(raw, base):
    calls, out = {}, {'contentReads': 0, 'contentBytes': 0, 'byMarker': {}, 'calls': []}
    for line in open(raw, errors='replace'):
        try:
            d = json.loads(line)
        except ValueError:
            continue
        if d.get('type') == 'assistant':
            for c in d['message'].get('content', []):
                if c.get('type') == 'tool_use':
                    calls[c['id']] = (c.get('name'), c.get('input') or {})
        elif d.get('type') == 'user' and isinstance(d['message'].get('content'), list):
            for c in d['message']['content']:
                if not (isinstance(c, dict) and c.get('type') == 'tool_result'):
                    continue
                name, inp = calls.get(c['tool_use_id'], ('?', {}))
                if name in ('Write', 'Edit', 'TodoWrite'):
                    continue
                t = c.get('content'); t = t if isinstance(t, str) else json.dumps(t)
                hit = [k for k, rx in MARKERS.items() if rx.search(t)]
                cmd = str(inp.get('command') or '')
                reading = [x for x in re.split(r';|&&|\n', cmd) if GENREAD.search(x) and not re.search(r'\bgrep\b(\s+-[A-Za-z]+)*\s+-[A-Za-z]*[clLq][A-Za-z]*\b', x)]
                if name == 'Bash' and reading and not EMPTY.search(t) and not c.get('is_error'):
                    hit.append('pathNamedRead')
                if hit:
                    out['contentReads'] += 1; out['contentBytes'] += len(t)
                    for k in hit:
                        out['byMarker'][k] = out['byMarker'].get(k, 0) + 1
                    out['calls'].append({'shape': shape(name, inp, base), 'markers': hit, 'resultBytes': len(t),
                                         'partial': hit == ['pathNamedRead']})
    print(json.dumps(out))


if __name__ == '__main__':
    main(sys.argv[1], Path(sys.argv[2]).resolve())
