"""M72 L0 — measure the split: where an authoring run's tokens, tool bytes and cost go.

Reads one Claude Code stream-json transcript (the runner's `raw.jsonl`, see tools/g14_runner.py) and writes three
derived tables beside the run's score.json (docs/specs/spec-authoring-token-lowering.md, L0):

    buckets.json     per request: input, cache-read, cache-write and output tokens, the prompt size and the >100k
                     tier flag; per-run totals by tier; cost recomputed from the buckets beside the CLI's billed cost
    categories.json  tool-result bytes by kind (the classifier below), with lower/upper bounds for compound calls,
                     per-rule counts and an explicit `unclassified` bucket
    phases.json      each tool call and request assigned to design / run / diagnosis (the definition below)

Transcripts are never committed (they hold paths and model text), so these tables are the evidence; a re-split
from a transcript is a check of the tables. The tables hold counts, byte sizes, tool names, kind and rule names
only: no command text, no path, no model output.

    python3 tools/token_split.py split <raw.jsonl> --out <dir> [--run NAME]
    python3 tools/token_split.py summary <results-dir> [<results-dir> ...]     # markdown, from committed tables

Measurement notes (each is a fact about stream-json, found while building this, 2026-10-13):

* An assistant message is emitted once per content block, every copy carrying the same `message.id` and usage, so
  requests are de-duplicated by `message.id` — as score_ladder.py's `usage()` already does.
* The input buckets (`input_tokens`, `cache_read_input_tokens`, `cache_creation_input_tokens`) per request are exact.
  **The per-request `output_tokens` is not**: it is the value at the start of the stream (single digits), and only
  the run total in the `result` event is billed. Output is therefore ALLOCATED to requests in proportion to what
  each emitted — the `system/thinking_tokens` deltas that precede it plus text and tool-input characters / 4 — and
  scaled so the allocation sums to the billed total. `outputStreamed` keeps the raw value.
* `result.modelUsage` can exceed the sum of the visible requests: the CLI makes side calls (e.g. to summarise a
  WebFetch) that never appear as assistant messages. The difference is recorded per model as `sideCalls`, priced
  at the base tier with no cache, and kept out of the phases (it cannot be placed in time).
* The `>100k` tier flag is prompt = input + cache-read + cache-write > 100,000, the definition score_ladder.py uses
  for `usage.over100k`.
* A subagent (the `Agent` tool, alias `Task`, in Claude Code 2.1.282's binary) runs in its own context. stream-json
  marks each of its messages with the delegating call's id in `parent_tool_use_id` (null for the main context; the
  SDK message schema, read from the same binary, 2026-10-13). Each request is assigned to a CONTEXT by that field, a
  tool result is carried only by later requests of its own context, and a run that delegated gets a `contexts`
  table (buckets.json) and a `delegation` block (phases.json): M72 L4 (iii)'s acceptance reads them. A run with
  one context and no delegating call writes neither: re-split, the 28 L0 runs reproduce their committed tables
  exactly apart from phases.json's `definition` text, which now names the delegation rule. **Not yet seen in a
  real transcript**: whether subagent messages reach stream-json under `-p`, and whether the `result` event's
  `usage.output_tokens` includes their output. A run that delegated but shows no subagent request is flagged
  (`delegation.warnings`); its subagent cost then sits in `sideCalls`, outside the phases.

Pricing (PRICES below) was fitted to the CLI's own billed `total_cost_usd` and must be re-read from the provider's
pricing page before it is relied on (CLAUDE.md rule 6). Fit, 2026-10-13: Sonnet 5 reproduces every first-ladder
run's billed cost to the cent; Haiku 5.5's base tier reproduces exactly the one run with no request above 100k
(base-r3-h55-2); with the 5x tier and the output allocation above, every Haiku 5.5 run's recomputed cost is within
1.3% of the billed cost. Cache writes in these runs are 1-hour writes, priced at 2x input, not the 5-minute 1.25x.
"""
from pathlib import Path
import argparse
import json
import re
import statistics
import sys

SCHEMA = 'token-split/1'
TIER_THRESHOLD = 100_000

# USD per million tokens. `over100k` multiplies every bucket of a request whose prompt exceeds TIER_THRESHOLD.
PRICES = {
    'claude-haiku-5-5': {'input': 0.10, 'cacheRead': 0.01, 'cacheWrite5m': 0.125, 'cacheWrite1h': 0.20,
                         'output': 0.50, 'over100k': 5.0},
    'claude-sonnet-5': {'input': 2.00, 'cacheRead': 0.20, 'cacheWrite5m': 2.50, 'cacheWrite1h': 4.00,
                        'output': 10.00, 'over100k': 1.0},
}

# ---------------------------------------------------------------------------------------------------------------
# The classifier. A tool call's kind comes from its TOOL NAME, then from its SUBJECT: the command for Bash (heredoc
# bodies removed, so a script the model writes is not mistaken for what it reads), the file path for Read, the
# pattern and path for Grep/Glob, the URL for WebFetch. Every subject rule that matches is recorded; the call's
# bytes go to the highest-priority kind (KINDS order), and a call matching more than one kind is "multi-kind": its
# bytes count toward the UPPER bound of every kind it matched and the LOWER bound of none. So for any kind,
# lowerBound <= bytes <= upperBound, and the bound width is the doubt compound commands (`cat a && cat b`) leave.
# auditLog is first, so its `bytes` is already its upper bound minus nothing it could not claim.
# A call no rule matches is `unclassified` — counted, never silently folded into `other`.
# ---------------------------------------------------------------------------------------------------------------
KINDS = ('auditLog', 'generated', 'server', 'build', 'design', 'library', 'docs', 'source', 'analyser', 'other')

# (tool-name prefix or exact name, kind, rule)
TOOL_RULES = (
    ('mcp__fluxtion-analyser__', 'analyser', 'analyserVerb'),   # the analyser's replies (prefix)
    ('WebFetch', 'docs', 'webFetch'),
    ('WebSearch', 'docs', 'webSearch'),
    ('Skill', 'docs', 'skillLoad'),
    ('ToolSearch', 'other', 'toolSchema'),                      # deferred tool schemas
    ('Edit', 'other', 'editAck'),                               # edit/write acknowledgements
    ('Write', 'other', 'editAck'),
    ('MultiEdit', 'other', 'editAck'),
    ('NotebookEdit', 'other', 'editAck'),
    ('TodoWrite', 'other', 'harness'),
    ('TaskStop', 'other', 'harness'),
    ('TaskOutput', 'other', 'harness'),
    ('TaskCreate', 'other', 'harness'),
    ('TaskUpdate', 'other', 'harness'),
    ('TaskList', 'other', 'harness'),
    ('TaskGet', 'other', 'harness'),
    ('Agent', 'other', 'delegate'),                             # a subagent: its reply is the result (L4 iii)
    ('Task', 'other', 'delegate'),                              # the same tool's older name
)
DELEGATE_TOOLS = ('Agent', 'Task')
SUBJECT_TOOLS = ('Bash', 'Read', 'Grep', 'Glob')

# a script EXECUTED, not named: `./x.sh`, `bash x.sh`, `sh x.sh` — and not the `.sh x.sh` of `cat a.sh x.sh`
_EXEC = r'(?:(?<![\w.])\./|(?<![\w./-])(?:bash|sh)\s+)'
SUBJECT_RULES = (
    # auditLog — the audit export or its text, read by a shell or Read
    ('auditLog', 'auditFile', r'audit[\w.-]*\.(?:ya?ml|log|jsonl?|txt|csv)\b'),
    ('auditLog', 'auditDir', r'(?:^|[\s/"\'=])(?:logs/audit|audit/)'),
    ('auditLog', 'auditKeys', r'\b(?:triggerFired|eventToString|nodeLogs|eventTime)\b'),
    ('auditLog', 'auditCompare', r'audit-compare'),
    # (plus `openedLog`: any file the same run opened with analyser `open {log}` — added per transcript)
    # generated — the generated processor and its GraphML
    ('generated', 'generatedDir', r'/generated/'),
    ('generated', 'graphml', r'\.graphml\b'),
    # server — running the processor or server, and its output
    ('server', 'runScript', _EXEC + r'(?:run-server|run-and-export|export-audit|run|stop-server|start-server)\.sh\b'),
    ('server', 'javaRun', r'(?<![\w./-])java\s+(?!-version\b)[-\w"$]'),   # not the `.java ` of a file name
    ('server', 'serverLog', r'\b(?:server|run)[\w-]*\.log\b'),
    ('server', 'serverProbe', r'\bcurl\b[^|;&]*localhost|\blsof\s+-i|\bpkill\b|\bkill\s+-?\w|\bjps\b'),
    ('server', 'mongooseDir', r'\.mongoose/'),
    # build — generation, compilation and the compiler's reports
    ('build', 'buildScript', _EXEC + r'(?:setup|validate|generate|check-fluxtion-key)\.sh\b'),
    ('build', 'maven', r'(?:^|[\s;&|(/])mvnw?\b'),
    ('build', 'javac', r'\bjavac\b'),
    ('build', 'compilerReport', r'fluxtion-(?:run|validation|diagnostics|reconciliation)[\w-]*\.json'),
    ('build', 'buildLog', r'\b(?:gen|generate|validate|setup|build|package|compile|fx-package|mvn)[\w-]*\.log\b'),
    # design — the Spring XML design and the authoring manifest
    ('design', 'springXml', r'application-context\.xml|/designer/'),
    ('design', 'authoringJson', r'fluxtion-authoring\.json'),
    # library — inspecting dependency jars, classes and the resolved classpath
    ('library', 'javap', r'\bjavap\b'),
    ('library', 'unzip', r'\bunzip\b|\bjar\s+t'),
    ('library', 'jarOrClass', r'\.(?:jar|class|pom)\b'),
    ('library', 'mavenRepo', r'/m2/|\.m2/'),
    ('library', 'classpath', r'\.fluxtion/classpath'),
    ('library', 'strings', r'\bstrings\b'),
    # docs — Markdown, skills, help text
    ('docs', 'markdown', r'\.md\b'),
    ('docs', 'skills', r'\.claude/skills'),
    ('docs', 'help', r'--help\b'),
    # source — hand-written project files: Java, POM, scripts, config and data
    ('source', 'javaSource', r'\.java\b'),
    ('source', 'pom', r'\bpom\.xml\b'),
    ('source', 'scriptSource', r'\.(?:sh|py)\b'),
    ('source', 'projectFile', r'(?:^|[\s/"\'])(?:config|data|src)/|\.(?:ya?ml|csv|properties|txt)\b'),
    ('source', 'analyserSettings', r'fluxtion-settings'),
    ('source', 'gitignore', r'\.gitignore\b'),
    # other — shell bookkeeping whose first word says it reads no file content
    ('other', 'shellMeta', r'^\s*(?:ls|find|pwd|which|echo|mkdir|cd|rm|cp|mv|wc|sleep|date|env|shasum|sha256sum|'
                           r'chmod|touch|tree|du|stat|file|true|test|jobs|wait|java\s+-version|'
                           r'python3?\s+-c\s+[\'"]import\s+\w+;\s*print\(\w+\.__version__)'),
)
_COMPILED = tuple((k, r, re.compile(p)) for k, r, p in SUBJECT_RULES)
_HEREDOC = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?[^\n]*\n.*?\n\s*\1\b", re.DOTALL)

# Phase markers (see PHASE_DEFINITION).
BUILD_RULES = {'maven', 'javac'}
BUILD_GENERATE = re.compile(_EXEC + r'generate\.sh\b')
RUN_RULES = {'runScript', 'javaRun'}
# A result that carries audit-record keys: a run, build or server output echoing the audit log it writes. Counted
# per call (`auditEcho`) so the audit log's share can be bounded from the committed tables, not only by kind.
AUDIT_ECHO = re.compile(r'\b(?:eventToString|nodeLogs|triggerFired)\b')
FAILED = re.compile(r'BUILD FAILURE|\[ERROR\]|\bexit[=: ]+[1-9]|\b(?:validate|generate|setup|package|build)'
                    r'(?:[ -]?exit)?[=: ]+[1-9]\b|COMPILATION ERROR')

PHASE_DEFINITION = (
    'Calls in transcript order. E (the export) = the last run call (rules runScript or javaRun) before the last '
    'analyser `open` that names a log; with no such open, the last run call. B (the build) = the last successful '
    'build call (generate.sh executed, mvn/mvnw, or javac; successful = not is_error and no failure marker in the '
    'result) before E, or the last one overall when there is no E. D = the first analyser call or subagent '
    'delegation (Agent/Task) after E (after B when there is no E). design = calls up to and including B (with no B: the calls before the first run call); '
    'diagnosis = D to the end; run = everything between. A request takes the phase of the first tool call it '
    'emits; a request that emits none takes the phase of the nearest earlier request that emitted one (design when '
    'there is none). CLI side calls '
    '(sideCalls) are not placed in a phase.')


def subject(name, inp):
    if name == 'Bash':
        return _HEREDOC.sub('<<heredoc', str(inp.get('command') or ''))
    if name == 'Read':
        return str(inp.get('file_path') or '')
    if name in ('Grep', 'Glob'):
        return ' '.join(str(inp.get(k) or '') for k in ('pattern', 'path', 'glob'))
    return ''


def opened_logs(calls):
    """Basenames of files this run opened as audit logs through the analyser: reading one is reading the log."""
    names = set()
    for c in calls:
        if c['name'].endswith('analyser_open'):
            for key in ('log', 'logs'):
                v = c['input'].get(key)
                for p in (v if isinstance(v, list) else [v]):
                    if isinstance(p, str) and p:
                        base = p.rstrip('/').rsplit('/', 1)[-1]
                        if len(base) > 4:
                            names.add(base)
    return names


def classify(name, inp, logs=()):
    """(kind, kinds, rule) for one tool call; kind is 'unclassified' when no rule matches."""
    for prefix, kind, rule in TOOL_RULES:
        if name == prefix or (prefix.endswith('__') and name.startswith(prefix)):
            return kind, [kind], rule
    if name.startswith('mcp__'):
        return 'other', ['other'], 'otherMcp'
    if name not in SUBJECT_TOOLS:
        return 'unclassified', [], None
    text = subject(name, inp)
    matches = []                                       # (kind, rule, token start, token end)
    for kind, rule, rx in [('auditLog', 'openedLog', re.compile(r'(?<![\w.-])' + re.escape(b) + r'(?![\w.-])'))
                           for b in sorted(logs)] + list(_COMPILED):
        for m in rx.finditer(text):
            matches.append((kind, rule) + _token_span(text, m.start(), m.end()))
    # One path or word is ONE thing: a match whose token overlaps a higher-priority kind's match is dropped, so
    # `logs/audit-x.yaml` is an audit log and not also a YAML project file, and `.../generated/X.java` is
    # generated code and not also Java source. Distinct tokens of different kinds make a multi-kind call.
    matches.sort(key=lambda m: (KINDS.index(m[0]), m[2]))
    kept = []
    for m in matches:
        if not any(KINDS.index(k[0]) < KINDS.index(m[0]) and k[2] < m[3] and m[2] < k[3] for k in kept):
            kept.append(m)
    if not kept and name in ('Grep', 'Glob'):
        return 'source', ['source'], 'projectSearch'   # a content or name search over the project tree
    if not kept:
        return 'unclassified', [], None
    kinds = sorted({k for k, *_ in kept}, key=KINDS.index)
    rule = next(r for k, r, *_ in kept if k == kinds[0])
    return kinds[0], kinds, rule


def _token_span(text, start, end):
    """Widen a match to the whitespace-delimited token(s) it touches."""
    while start > 0 and not text[start - 1].isspace():
        start -= 1
    while end < len(text) and not text[end].isspace():
        end += 1
    return start, end


# ---------------------------------------------------------------------------------------------------------------
# Transcript parsing
# ---------------------------------------------------------------------------------------------------------------
def parse(lines):
    requests, order, calls, by_id, result = {}, [], [], {}, None
    pending_thinking = 0
    for line in lines:
        try:
            d = json.loads(line)
        except (ValueError, TypeError):
            continue
        t = d.get('type')
        if t == 'system' and d.get('subtype') == 'thinking_tokens':
            pending_thinking += d.get('estimated_tokens_delta') or 0
        elif t == 'assistant':
            m = d.get('message') or {}
            mid = m.get('id') or f'anon-{len(order)}'
            if mid not in requests:
                requests[mid] = {'model': m.get('model'), 'usage': {}, 'emitted': 0.0, 'calls': [],
                                 'parent': d.get('parent_tool_use_id')}
                order.append(mid)
            r = requests[mid]
            r['usage'] = m.get('usage') or r['usage']
            r['emitted'] += pending_thinking
            pending_thinking = 0
            for c in m.get('content') or []:
                ct = c.get('type')
                if ct == 'text':
                    r['emitted'] += len(c.get('text') or '') / 4
                elif ct == 'tool_use':
                    r['emitted'] += len(json.dumps(c.get('input'))) / 4
                    call = {'n': len(calls), 'id': c.get('id'), 'name': c.get('name') or '',
                            'input': c.get('input') or {}, 'request': order.index(mid), 'bytes': 0,
                            'isError': None, 'text': ''}
                    calls.append(call)
                    by_id[call['id']] = call
                    r['calls'].append(call['n'])
        elif t == 'user':
            content = (d.get('message') or {}).get('content')
            if isinstance(content, list):
                for c in content:
                    if c.get('type') == 'tool_result':
                        body = c.get('content')
                        body = body if isinstance(body, str) else json.dumps(body)
                        call = by_id.get(c.get('tool_use_id'))
                        if call is None:   # a result with no visible call still counts toward toolResultBytes
                            call = {'n': len(calls), 'id': c.get('tool_use_id'), 'name': '?', 'input': {},
                                    'request': len(order) - 1, 'bytes': 0, 'isError': None, 'text': ''}
                            calls.append(call)
                        call['bytes'] += len(body)       # the measure score.json's toolResultBytes uses
                        call['isError'] = c.get('is_error')
                        call['text'] = body[-4000:]      # held in memory for the success check; never written
                        call['auditEcho'] = call.get('auditEcho', False) or bool(AUDIT_ECHO.search(body))
        elif t == 'result':
            result = d
    return [dict(requests[i], id=i) for i in order], calls, result


def _cache_split(u):
    cw = u.get('cache_creation_input_tokens') or 0
    cc = u.get('cache_creation') or {}
    one_h = cc.get('ephemeral_1h_input_tokens')
    five_m = cc.get('ephemeral_5m_input_tokens')
    if one_h is None and five_m is None:
        return cw, 0          # no split reported: treated as 1-hour writes, as every observed run used
    return one_h or 0, five_m or 0


def _cost(model, b, over):
    p = PRICES.get(model)
    if p is None:
        return None
    mult = p['over100k'] if over else 1.0
    return mult * (b['input'] * p['input'] + b['cacheRead'] * p['cacheRead'] + b['cacheWrite1h'] * p['cacheWrite1h']
                   + b['cacheWrite5m'] * p['cacheWrite5m'] + b['outputAllocated'] * p['output']) / 1e6


def _phases(calls):
    def ok(c):
        return c['isError'] is not True and not FAILED.search(c.get('text') or '')

    run_idx = [c['n'] for c in calls if RUN_RULES & c['rules']]
    opens = [c['n'] for c in calls if c['name'].endswith('analyser_open')
             and any(c['input'].get(k) for k in ('log', 'logs'))]
    if opens:
        before = [i for i in run_idx if i < opens[-1]]
        export = before[-1] if before else None
    else:
        export = run_idx[-1] if run_idx else None
    builds = [c['n'] for c in calls if (BUILD_RULES & c['rules'] or BUILD_GENERATE.search(subject(c['name'], c['input'])))
              and ok(c) and (export is None or c['n'] < export)]
    build = builds[-1] if builds else None
    after = export if export is not None else build
    diag = next((c['n'] for c in calls if (c['kind'] == 'analyser' or c['name'] in DELEGATE_TOOLS)
                 and (after is None or c['n'] > after)), None)
    if build is not None:
        design_end = build
    elif run_idx:
        design_end = run_idx[0] - 1
    else:
        design_end = (diag - 1) if diag is not None else len(calls) - 1
    phase = {}
    for c in calls:
        i = c['n']
        phase[i] = 'diagnosis' if diag is not None and i >= diag else 'design' if i <= design_end else 'run'
    return phase, {'exportCall': export, 'lastBuildCall': build, 'diagnosisStartCall': diag,
                   'designEndCall': design_end}


def split(lines, run=None):
    """Return (buckets, categories, phases) for one transcript. Pure: no file is read or written."""
    requests, calls, result = parse(lines)
    logs = opened_logs(calls)
    for c in calls:
        c['kind'], c['kinds'], c['rule'] = classify(c['name'], c['input'], logs)
        c['rules'] = set()
        if c['name'] in SUBJECT_TOOLS:
            text = subject(c['name'], c['input'])
            c['rules'] = {r for _, r, rx in _COMPILED if rx.search(text)}
    phase_of_call, bounds = _phases(calls)

    # requests: exact input buckets, allocated output
    billed_out = (result or {}).get('usage', {}).get('output_tokens')
    streamed = [(r['usage'].get('output_tokens') or 0) for r in requests]
    emitted = sum(r['emitted'] for r in requests)
    total_out = billed_out if billed_out is not None else sum(streamed)
    rows, last_phase = [], 'design'
    for i, r in enumerate(requests):
        u = r['usage']
        one_h, five_m = _cache_split(u)
        prompt = (u.get('input_tokens') or 0) + (u.get('cache_read_input_tokens') or 0) + \
                 (u.get('cache_creation_input_tokens') or 0)
        share = (r['emitted'] / emitted) if emitted else (1 / len(requests))
        if r['calls']:
            last_phase = phase_of_call[r['calls'][0]]
        row = {'n': i, 'model': r['model'], 'input': u.get('input_tokens') or 0,
               'cacheRead': u.get('cache_read_input_tokens') or 0,
               'cacheWrite': u.get('cache_creation_input_tokens') or 0, 'cacheWrite1h': one_h,
               'cacheWrite5m': five_m, 'prompt': prompt, 'over100k': prompt > TIER_THRESHOLD,
               'outputStreamed': streamed[i], 'outputAllocated': round(total_out * share, 3), 'toolCalls': len(r['calls']),
               'phase': last_phase}
        c = _cost(r['model'], row, row['over100k'])
        row['costUsd'] = round(c, 8) if c is not None else None
        rows.append(row)

    # side calls: modelUsage beyond the visible requests
    side, model_usage = {}, (result or {}).get('modelUsage') or {}
    main_models = {r['model'] for r in rows}
    for m, mu in model_usage.items():
        vis = [r for r in rows if r['model'] == m]
        s = {'input': max(0, (mu.get('inputTokens') or 0) - sum(r['input'] for r in vis)),
             'cacheRead': max(0, (mu.get('cacheReadInputTokens') or 0) - sum(r['cacheRead'] for r in vis)),
             'cacheWrite': max(0, (mu.get('cacheCreationInputTokens') or 0) - sum(r['cacheWrite'] for r in vis)),
             'output': max(0, (mu.get('outputTokens') or 0) - (total_out if m in main_models else 0))}
        b = {'input': s['input'], 'cacheRead': s['cacheRead'], 'cacheWrite1h': s['cacheWrite'], 'cacheWrite5m': 0,
             'outputAllocated': s['output']}
        c = _cost(m, b, False)
        s['costUsd'] = round(c, 8) if c is not None else None
        if any(s[k] for k in ('input', 'cacheRead', 'cacheWrite', 'output')):
            side[m] = s

    def tier(sel):
        t = {'requests': len(sel)}
        for k in ('input', 'cacheRead', 'cacheWrite', 'outputAllocated'):
            t[k] = round(sum(r[k] for r in sel), 3)
        costs = [r['costUsd'] for r in sel]
        t['costUsd'] = round(sum(costs), 8) if None not in costs else None
        return t

    lo, hi = tier([r for r in rows if not r['over100k']]), tier([r for r in rows if r['over100k']])
    side_cost = sum(s['costUsd'] for s in side.values()) if all(s['costUsd'] is not None for s in side.values()) else None
    recomputed = (lo['costUsd'] + hi['costUsd'] + side_cost) if None not in (lo['costUsd'], hi['costUsd'], side_cost) else None
    billed = (result or {}).get('total_cost_usd')
    buckets = {
        'schema': SCHEMA, 'run': run, 'model': next(iter(main_models), None) if len(main_models) == 1 else sorted(main_models),
        'pricingUsdPerMTok': {m: PRICES[m] for m in sorted(main_models | set(side)) if m in PRICES},
        'tierThreshold': TIER_THRESHOLD,
        'outputAllocation': 'billed run output allocated in proportion to thinking-token deltas + (text and '
                            'tool-input chars)/4 per request; outputStreamed is the stream-start value',
        'requests': rows,
        'sideCalls': side,
        'totals': {
            'requests': len(rows), 'over100k': hi['requests'], 'maxPrompt': max((r['prompt'] for r in rows), default=0),
            'le100k': lo, 'gt100k': hi, 'sideCallsCostUsd': round(side_cost, 8) if side_cost is not None else None,
            'billedOutputTokens': billed_out, 'costUsd': billed,
            'costRecomputedUsd': round(recomputed, 8) if recomputed is not None else None,
            'costRecomputedRatio': round(recomputed / billed, 4) if recomputed is not None and billed else None,
            'gt100kCostShare': round(hi['costUsd'] / recomputed, 4) if recomputed and hi['costUsd'] is not None else None,
        },
    }

    # categories
    tot = sum(c['bytes'] for c in calls)
    # A tool result stays in the context: every later request re-reads it. `carried` is bytes x later requests;
    # `carriedTierWeighted` multiplies each later request by its tier price factor (5 for a Haiku 5.5 request over
    # 100k), so it ranks kinds by what they cost to keep, not only by what they cost to read once.
    # A result is carried only by later requests of ITS OWN context: a subagent does not re-read the main
    # context's results, nor the main context a subagent's (it gets the subagent's reply, the delegate result).
    factor = [(PRICES.get(r['model']) or {}).get('over100k', 1.0) if r['over100k'] else 1.0 for r in rows]
    ctx = [r['parent'] for r in requests]
    later_n = [sum(1 for j in range(i + 1, len(rows)) if ctx[j] == ctx[i]) for i in range(len(rows))]
    later_w = [sum(factor[j] for j in range(i + 1, len(rows)) if ctx[j] == ctx[i]) for i in range(len(rows))]
    blank = {'calls': 0, 'bytes': 0, 'lowerBound': 0, 'upperBound': 0, 'carried': 0, 'carriedTierWeighted': 0.0}
    kinds = {k: dict(blank) for k in KINDS}
    kinds['unclassified'] = dict(blank)
    by_rule = {}
    for c in calls:
        k = kinds[c['kind']]
        k['calls'] += 1
        k['bytes'] += c['bytes']
        q = c['request'] if 0 <= c['request'] < len(rows) else len(rows) - 1
        if rows:
            k['carried'] += c['bytes'] * later_n[q]
            k['carriedTierWeighted'] = round(k['carriedTierWeighted'] + c['bytes'] * later_w[q], 1)
        if len(c['kinds']) <= 1:
            k['lowerBound'] += c['bytes']
        for kk in (c['kinds'] or ['unclassified']):
            kinds[kk]['upperBound'] += c['bytes']
        key = c['rule'] or 'unclassified'
        br = by_rule.setdefault(key, {'kind': c['kind'], 'calls': 0, 'bytes': 0})
        br['calls'] += 1
        br['bytes'] += c['bytes']
    categories = {
        'schema': SCHEMA, 'run': run, 'toolResultBytes': tot, 'toolCalls': len(calls),
        'kindPriority': list(KINDS), 'kinds': kinds, 'byRule': dict(sorted(by_rule.items())),
        'multiKindCalls': sum(1 for c in calls if len(c['kinds']) > 1),
        'multiKindBytes': sum(c['bytes'] for c in calls if len(c['kinds']) > 1),
        # whole-call bytes of build/server results that carry audit-record keys: an upper bound on audit text the
        # auditLog kind does not count
        'auditEchoBytes': {k: sum(c['bytes'] for c in calls if c['kind'] == k and c.get('auditEcho'))
                           for k in ('server', 'build')},
        'calls': [{'n': c['n'], 'tool': c['name'], 'kind': c['kind'], 'kinds': c['kinds'], 'rule': c['rule'],
                   'bytes': c['bytes'], 'isError': c['isError'], 'request': c['request'],
                   'auditEcho': bool(c.get('auditEcho'))} for c in calls],
    }

    # phases
    ph = {}
    for p in ('design', 'run', 'diagnosis'):
        pc = [c for c in calls if phase_of_call[c['n']] == p]
        pr = [r for r in rows if r['phase'] == p]
        costs = [r['costUsd'] for r in pr]
        ph[p] = {'toolCalls': len(pc), 'analyserCalls': sum(1 for c in pc if c['kind'] == 'analyser'),
                 'toolResultBytes': sum(c['bytes'] for c in pc), 'requests': len(pr),
                 'over100k': sum(1 for r in pr if r['over100k']),
                 'input': sum(r['input'] for r in pr), 'cacheRead': sum(r['cacheRead'] for r in pr),
                 'cacheWrite': sum(r['cacheWrite'] for r in pr),
                 'outputAllocated': round(sum(r['outputAllocated'] for r in pr), 3),
                 'costUsd': round(sum(costs), 8) if None not in costs else None}
    phases = {'schema': SCHEMA, 'run': run, 'definition': PHASE_DEFINITION, 'boundaries': bounds, 'phases': ph,
              'sideCallsCostUsd': buckets['totals']['sideCallsCostUsd'],
              'calls': [{'n': c['n'], 'phase': phase_of_call[c['n']]} for c in calls]}
    contexts, delegation = _contexts(requests, rows, calls)
    if delegation is not None:      # only a run that delegated: a single-context run's tables stay L0's, byte for byte
        buckets['contexts'] = contexts
        phases['delegation'] = delegation
    return buckets, categories, phases


def _contexts(requests, rows, calls):
    """Per-context totals and the delegation block, or (None, None) for a run with one context and no delegation.

    A context is `main` or `subagent-<k>`, numbered by first appearance; the delegating call's id never leaves this
    function (it is an opaque transcript id). `firstPrompt` of a subagent is the base it re-pays (spec §5)."""
    delegates = [c for c in calls if c['name'] in DELEGATE_TOOLS]
    parents = []
    for r in requests:
        if r['parent'] is not None and r['parent'] not in parents:
            parents.append(r['parent'])
    if not delegates and not parents:
        return None, None
    by_id = {c['id']: c for c in calls}
    names = {None: 'main'}
    for k, p in enumerate(parents, 1):
        names[p] = f'subagent-{k}'
    table = []
    for p in [None] + parents:
        sel = [row for row, r in zip(rows, requests) if r['parent'] == p]
        costs = [q['costUsd'] for q in sel]
        parent_call = by_id.get(p)
        table.append({'context': names[p], 'delegatedBy': parent_call['n'] if parent_call else None,
                      'models': sorted({q['model'] for q in sel if q['model']}),
                      'requests': len(sel), 'over100k': sum(1 for q in sel if q['over100k']),
                      'firstPrompt': sel[0]['prompt'] if sel else 0, 'maxPrompt': max((q['prompt'] for q in sel), default=0),
                      'phases': sorted({q['phase'] for q in sel}),
                      'costUsd': round(sum(costs), 8) if None not in costs else None})
    main = table[0]
    subs = table[1:]
    warnings = []
    if delegates and not subs:
        warnings.append('delegated, but no subagent request is visible in this transcript: its cost is in '
                        'sideCalls, outside the phases, so the diagnosis phase is undercounted')
    for t in subs:
        if t['models'] != main['models']:
            warnings.append(f"{t['context']} ran on {', '.join(t['models'])}, not the main context's model: a model "
                            'change confounds the comparison')
    sub_costs = [t['costUsd'] for t in subs]
    main_diag = [row for row, r in zip(rows, requests) if r['parent'] is None and row['phase'] == 'diagnosis']
    delegation = {
        'delegations': len(delegates),
        'delegateResultBytes': sum(c['bytes'] for c in delegates),     # what the main context carries back
        'delegateInputChars': sum(len(json.dumps(c['input'])) for c in delegates),   # the briefs, as sent
        'subagentContexts': len(subs),
        'subagentRequests': sum(t['requests'] for t in subs),
        'subagentOver100k': sum(t['over100k'] for t in subs),
        'subagentMaxPrompt': max((t['maxPrompt'] for t in subs), default=0),
        'subagentCostUsd': round(sum(sub_costs), 8) if None not in sub_costs else None,
        'mainDiagnosisRequests': len(main_diag),
        'mainDiagnosisOver100k': sum(1 for q in main_diag if q['over100k']),
        'mainMaxPrompt': main['maxPrompt'],
        'warnings': warnings,
    }
    return table, delegation


def write_tables(raw, out, run=None):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    tables = split(Path(raw).read_text(errors='replace').splitlines(), run=run)
    for name, data in zip(('buckets.json', 'categories.json', 'phases.json'), tables):
        (out / name).write_text(json.dumps(data, indent=1) + '\n')
    return tables


# ---------------------------------------------------------------------------------------------------------------
# Summary — computed from the committed tables (and score.json) only
# ---------------------------------------------------------------------------------------------------------------
def load_runs(results_dir):
    runs = []
    for d in sorted(Path(results_dir).iterdir()):
        if not d.is_dir() or 'VOID' in d.name or not (d / 'buckets.json').exists():
            continue
        t = {n: json.loads((d / f'{n}.json').read_text()) for n in ('buckets', 'categories', 'phases')}
        t['score'] = json.loads((d / 'score.json').read_text()) if (d / 'score.json').exists() else {}
        t['name'] = d.name
        runs.append(t)
    return runs


def _pct(a, b, places=1):
    return f'{a / b:.{places}%}' if b else 'n/a'


def _ratio(a, b):
    return f'{a / b:.4f}' if b else 'n/a'


def summary(dirs):
    out = []
    for results in dirs:
        runs = load_runs(results)
        models = sorted({r['buckets']['model'] for r in runs if isinstance(r['buckets']['model'], str)})
        out.append(f'### {Path(results).resolve().parent.name}\n')
        out.append('| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |')
        out.append('|---|---|---|---|---|')
        for r in runs:
            t = r['buckets']['totals']
            sc = (r['score'].get('reads') or {}).get('toolResultBytes')
            out.append(f"| {r['name']} | {r['categories']['toolResultBytes']} = {sc} "
                       f"{'yes' if sc == r['categories']['toolResultBytes'] else 'NO'} | {t['costRecomputedRatio']} | "
                       f"{t['over100k']}/{t['requests']} | {t['gt100kCostShare']} |")
        for model in models:
            rs = [r for r in runs if r['buckets']['model'] == model]
            tot_bytes = sum(r['categories']['toolResultBytes'] for r in rs)
            out.append(f'\n**{model}, {len(rs)} runs — tool-result bytes by kind** (share of {tot_bytes:,} bytes; '
                       'share = bytes assigned by kind priority; single-kind = calls that read only that kind, the '
                       'lower bound; upper = every call that names the kind)\n')
            out.append('| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | '
                       'tier-weighted carried share |')
            out.append('|---|---|---|---|---|---|---|---|')
            car = sum(r['categories']['kinds'][k]['carried'] for r in rs for k in r['categories']['kinds'])
            carw = sum(r['categories']['kinds'][k]['carriedTierWeighted'] for r in rs for k in r['categories']['kinds'])
            for k in list(KINDS) + ['unclassified']:
                b = sum(r['categories']['kinds'][k]['bytes'] for r in rs)
                lo = sum(r['categories']['kinds'][k]['lowerBound'] for r in rs)
                hi = sum(r['categories']['kinds'][k]['upperBound'] for r in rs)
                n = sum(r['categories']['kinds'][k]['calls'] for r in rs)
                ck = sum(r['categories']['kinds'][k]['carried'] for r in rs)
                cw = sum(r['categories']['kinds'][k]['carriedTierWeighted'] for r in rs)
                out.append(f'| {k} | {b:,} | {_pct(b, tot_bytes, 1)} | {_pct(lo, tot_bytes, 1)} | {_pct(hi, tot_bytes, 1)} | {n} | '
                           f'{_pct(ck, car, 1)} | {_pct(cw, carw, 1)} |')
            raw_audit = sum(v['bytes'] for r in rs for rule, v in r['categories']['byRule'].items()
                            if v['kind'] == 'auditLog' and rule != 'auditCompare')
            per_run = [sum(v['bytes'] for rule, v in r['categories']['byRule'].items()
                           if v['kind'] == 'auditLog' and rule != 'auditCompare') / r['categories']['toolResultBytes']
                       for r in rs]
            echo = sum(sum(r['categories'].get('auditEchoBytes', {}).values()) for r in rs)
            out.append(f'\nRaw audit-log text (auditLog without auditCompare): {raw_audit:,} bytes, '
                       f'{_pct(raw_audit, tot_bytes, 1)} of tool bytes; per run median {statistics.median(per_run):.1%}, '
                       f'max {max(per_run):.1%}. Build/server results that also carry audit-record keys: {echo:,} bytes, '
                       f'{_pct(echo, tot_bytes, 1)} (whole calls, an upper bound on echoed audit text).')
            billed = sum(r['buckets']['totals']['costUsd'] or 0 for r in rs)
            rec = sum(r['buckets']['totals']['costRecomputedUsd'] or 0 for r in rs)
            hi_cost = sum(r['buckets']['totals']['gt100k']['costUsd'] or 0 for r in rs)
            lo_cost = sum(r['buckets']['totals']['le100k']['costUsd'] or 0 for r in rs)
            side = sum(r['buckets']['totals']['sideCallsCostUsd'] or 0 for r in rs)
            hi_in = 0.0
            for r in rs:
                for q in r['buckets']['requests']:
                    p = PRICES.get(q['model'])
                    if q['over100k'] and p:
                        hi_in += p['over100k'] * (q['input'] * p['input'] + q['cacheRead'] * p['cacheRead'] +
                                                  q['cacheWrite1h'] * p['cacheWrite1h'] +
                                                  q['cacheWrite5m'] * p['cacheWrite5m']) / 1e6
            out.append(f'\nCost: billed ${billed:.4f}, recomputed ${rec:.4f} ({_ratio(rec, billed)}); requests over 100k '
                       f'${hi_cost:.4f} = **{_pct(hi_cost, rec, 1)}** of recomputed cost (input side alone '
                       f'${hi_in:.4f} = {_pct(hi_in, rec, 1)}); at or under 100k ${lo_cost:.4f} = {_pct(lo_cost, rec, 1)}; '
                       f'CLI side calls ${side:.4f} = {_pct(side, rec, 1)}.')
            out.append('\n| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | '
                       'tool bytes | bytes share |')
            out.append('|---|---|---|---|---|---|---|---|---|')
            for p in ('design', 'run', 'diagnosis'):
                c = sum(r['phases']['phases'][p]['costUsd'] or 0 for r in rs)
                q = sum(r['phases']['phases'][p]['requests'] for r in rs)
                o = sum(r['phases']['phases'][p]['over100k'] for r in rs)
                tc = sum(r['phases']['phases'][p]['toolCalls'] for r in rs)
                ac = sum(r['phases']['phases'][p]['analyserCalls'] for r in rs)
                tb = sum(r['phases']['phases'][p]['toolResultBytes'] for r in rs)
                out.append(f'| {p} | {c:.4f} | {_pct(c, rec, 1)} | {q} | {o} | {tc} | {ac} | {tb:,} | {_pct(tb, tot_bytes, 1)} |')
            out.append(f'| (CLI side calls) | {side:.4f} | {_pct(side, rec, 1)} | | | | | | |')
            no_diag = [r['name'] for r in rs if r['phases']['boundaries']['diagnosisStartCall'] is None]
            if no_diag:
                out.append(f'\nRuns with no diagnosis phase: {", ".join(no_diag)}.')
            out.extend(_delegation_summary(rs))
        out.append('')
    return '\n'.join(out)


def _delegation_summary(rs):
    """M72 L4 (iii): per run, where the diagnosis ran. Empty when no run in the set delegated."""
    if not any('delegation' in r['phases'] for r in rs):
        return []
    out = ['\n**Diagnosis contexts** (L4 iii; a run with no `delegation` block did not delegate)\n',
           '| run | delegations | subagent requests | subagent >100k | subagent first / max prompt | '
           'main diagnosis requests | main diagnosis >100k | main max prompt | subagent cost $ | diagnosis cost $ | '
           'brief chars | reply bytes | warnings |',
           '|---|---|---|---|---|---|---|---|---|---|---|---|---|']
    for r in rs:
        d = r['phases'].get('delegation')
        diag_cost = r['phases']['phases']['diagnosis']['costUsd']
        if d is None:
            diag = r['phases']['phases']['diagnosis']
            out.append(f"| {r['name']} | 0 | | | | {diag['requests']} | {diag['over100k']} | "
                       f"{r['buckets']['totals']['maxPrompt']} | | {diag_cost} | | | |")
            continue
        subs = [c for c in r['buckets']['contexts'] if c['context'] != 'main']
        first = max((c['firstPrompt'] for c in subs), default=0)
        out.append(f"| {r['name']} | {d['delegations']} | {d['subagentRequests']} | {d['subagentOver100k']} | "
                   f"{first} / {d['subagentMaxPrompt']} | {d['mainDiagnosisRequests']} | {d['mainDiagnosisOver100k']} | "
                   f"{d['mainMaxPrompt']} | {d['subagentCostUsd']} | {diag_cost} | {d['delegateInputChars']} | "
                   f"{d['delegateResultBytes']} | {'; '.join(d['warnings']) or '—'} |")
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    sub = ap.add_subparsers(dest='cmd', required=True)
    s = sub.add_parser('split', help='write buckets/categories/phases.json for one transcript')
    s.add_argument('raw')
    s.add_argument('--out', required=True)
    s.add_argument('--run')
    m = sub.add_parser('summary', help='markdown summary from committed tables')
    m.add_argument('dirs', nargs='+')
    ns = ap.parse_args(argv)
    if ns.cmd == 'split':
        b, c, p = write_tables(ns.raw, ns.out, ns.run)
        t = b['totals']
        print(json.dumps({'run': ns.run, 'requests': t['requests'], 'over100k': t['over100k'],
                          'toolResultBytes': c['toolResultBytes'], 'unclassifiedBytes': c['kinds']['unclassified']['bytes'],
                          'costRecomputedRatio': t['costRecomputedRatio']}))
        return 0
    print(summary(ns.dirs))
    return 0


if __name__ == '__main__':
    sys.exit(main())
