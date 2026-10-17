#!/usr/bin/env python3
"""M74 onboarding virgin-LLM runner — the OFFLINE half.

The protocol is docs/proposals/beta-testing/m74-onboarding/PROTOCOL.md and the scorecard SCORECARD.md beside it.
What is here runs today with no live `claude`, no key, no network and no unmerged M74 code:

    lint-pack     the strict prompt-pack grammar (a Python mirror of the analyser's PromptPack/ExampleSource)
    provenance    a Maven local repository's provenance verdict (the warm-cache rule)
    seed          copy a provenance-clean, frozen seed into a run's own repository, recording its tree digest
    seal          seal a cycle's predictions (one-shot), and verify-seal before a run
    init-run      lay out a run folder with every pin recorded, refusing an unsealed cycle or a used folder
    wrapper       write the `claude` wrapper that adds --strict-mcp-config (OD-74.18)
    score         compute the scorecard from a recorded run folder: the session record, the operator's log,
                  the observer's snapshots and the runner's meta — never from narrative

What is NOT here, because it needs the merged P2 (and for T4 the P3) analyser: `launch` — the in-JVM operator
harness that presses the analyser's own controls (PROTOCOL §6). It refuses with that reason rather than pretending.

The session record's facts are read by the names and node-log keys on the M74 P2 branch (SessionEvents and the
session nodes' auditLog keys). Where the scorecard needs a fact the code does not record, the field says
`unavailable` with the requirement id (SCORECARD.md § Requirements) instead of inventing a value.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import sys
import unicodedata
from urllib.parse import urlsplit

# ---------------------------------------------------------------------------------------------------------------------
# Tracks (PROTOCOL §5). `needs` is the phase the track cannot run without; `pass` names the scorecard checks a PASS needs.

TRACKS = {
    'T1': {'name': 'cold newcomer', 'needs': 'P2', 'posture': 'default', 'pass': ['oracle', 'noContradiction']},
    'T2': {'name': 'set up a project from code', 'needs': 'P2', 'posture': 'default',
           'pass': ['oracle', 'everyItemReviewed']},
    'T3': {'name': 'example entry', 'needs': 'P2', 'posture': 'default', 'pass': ['oracle', 'noContradiction']},
    'T4': {'name': 'authoring', 'needs': 'P3', 'posture': 'full', 'pass': ['oracle', 'noContradiction', 'keyScan']},
    'T5': {'name': 'external MCP diagnosis (regression)', 'needs': 'released', 'posture': 'external',
           'pass': ['oracle', 'noContradiction']},
}

# Person acts: the facts only the person's own controls post (PROTOCOL §6.1). The operator's log must account for every
# one of them, in order, kind by kind. P3's LocalAgentPermissionChosen is the spec's name (§8.3), not yet code.
PERSON_ACT_EVENTS = {
    'dropPack': 'StartPromptPackDropped',
    'returnToMain': 'ReturnToMainOptions',
    'startPage': 'StartPagePromptSent',
    'start': 'LocalAgentStartRequested',
    'folder': 'LocalAgentFolderAnswered',
    'send': 'LocalAgentSendRequested',
    'stop': 'LocalAgentStopRequested',
    'apply': 'ProposalApplyRequested',
    'dismiss': 'ProposalDismissed',
    'exampleDownload': 'ExampleDownloadRequested',
    'exampleConfirm': 'ExampleDownloadConfirmed',
    'switch': 'LocalAgentSwitchChosen',
    'permission': 'LocalAgentPermissionChosen',
}
EVENT_TO_ACT = {v: k for k, v in PERSON_ACT_EVENTS.items()}

# Node-log keys that carry a refusal, and how the reason is named (read from the P2 branch's session nodes).
REFUSAL_KEYS = {'localAgentRefused', 'localAgentSpawnFailed', 'exampleRefused'}

HOSTED_SERVER = 'fluxtion-analyser-hosted'

ALLOWED_REMOTES = {'central', 'repsy-fluxtion-public'}

# A fetch of this repository's protocol, evidence or oracle material by a web-capable subject is prompt leakage.
ORACLE_LEAK_PATTERN = re.compile(
    r'(github\.com/telaminai/fluxtionauditlog-analyser/(?:blob|tree|raw)/[^\s"\']*docs/(?:proposals|handoff)'
    r'|raw\.githubusercontent\.com/telaminai/fluxtionauditlog-analyser/[^\s"\']*docs/(?:proposals|handoff)'
    r'|m74-onboarding/(?:SCORECARD|PROTOCOL|virgin-pack)\.md)')


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    return sha256_bytes(Path(path).read_bytes())


def fail(message):
    """Refuse visibly with exit 2. Not `assert`: python3 -O strips assertions, and these are guards."""
    print(f'm74_onboarding_runner: {message}', file=sys.stderr)
    return 2


# ---------------------------------------------------------------------------------------------------------------------
# The prompt-pack grammar: a mirror of assistant/PromptPack and ExampleSource on the M74 P2 branch (spec §5.8,
# OD-74.28..30). The analyser's parser is the authority; this mirror exists so a pack is checked before a run without a
# built analyser, and PROTOCOL §4.3 records the one cross-check against the Java parser.

MAX_BYTES = 16 * 1024
MAX_TITLE, MAX_DESCRIPTION, MAX_LABEL, MAX_TEXT, MAX_ENTRIES = 60, 140, 60, 500, 9
ALLOWED_HOSTS = ('github.com', 'codeload.github.com', 'gitlab.com')
TELAMIN_ORG = 'telaminai'

_ENTRY = re.compile(r'^(\d{1,2})\. (.*)$')
_BOLD_LABEL = re.compile(r'^\*\*([^*]+)\*\* — (.*)$')
_HTML_TAG = re.compile(r'<\s*[/!?A-Za-z]')
_PLACEHOLDER = re.compile(r'\{[A-Za-z_][A-Za-z0-9_]*\}')
_LINK = re.compile(r'\]\(|https?://|\bwww\.|<https?:', re.IGNORECASE)
_EXAMPLE_LINE = re.compile(r'^\d{1,2}\. \*\*[^*]+\*\* — example:.*$')
_EXAMPLE = re.compile(r'^example: (?:<(\S+)>|(\S+))(?: sha256:(\S+))?$')
_SEG = r'[A-Za-z0-9._-]{1,100}'
_GITHUB = re.compile(rf'^/({_SEG})/({_SEG})/archive/(.+)\.zip$')
_CODELOAD = re.compile(rf'^/({_SEG})/({_SEG})/zip/(.+)$')
_GITLAB = re.compile(rf'^/({_SEG})/({_SEG})/-/archive/([^/]+)/([^/]+)\.zip$')
_HEX40 = re.compile(r'^[0-9a-fA-F]{40}$')
_HEX64 = re.compile(r'^[0-9a-fA-F]{64}$')
_URI_CHARS = re.compile(r"^[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]*$")


def _hidden(cp):
    if cp < 0x20 or cp == 0x7F or 0x80 <= cp <= 0x9F:
        return True
    if 0x202A <= cp <= 0x202E or 0x2066 <= cp <= 0x2069 or cp in (0x200E, 0x200F):
        return True
    # Java's FORMAT, LINE/PARAGRAPH_SEPARATOR, PRIVATE_USE, UNASSIGNED
    return unicodedata.category(chr(cp)) in ('Cf', 'Zl', 'Zp', 'Co', 'Cn')


def parse_example(raw, digest):
    """ExampleSource.parse: (source dict, None) or (None, refusal code)."""
    if digest is not None and not _HEX64.match(digest):
        return None, 'BAD_DIGEST'
    # java.net.URI.create refuses a character RFC 3986 does not allow (`<`, `>`, `{`, a space…) before any other check,
    # which ExampleSource reports as NOT_AN_ARCHIVE; urlsplit accepts them. Found by the cross-check against the Java
    # parser (PROTOCOL §4.3): `…/archive/<40-hex>.zip` was MOVING_REF here and NOT_AN_ARCHIVE there.
    if not _URI_CHARS.match((raw or '').strip()):
        return None, 'NOT_AN_ARCHIVE'
    try:
        u = urlsplit((raw or '').strip())
        port = u.port
    except ValueError:
        return None, 'NOT_AN_ARCHIVE'
    if (u.scheme or '').lower() != 'https':
        return None, 'NOT_HTTPS'
    host = (u.hostname or '').lower()
    if host not in ALLOWED_HOSTS:
        return None, 'HOST_NOT_ALLOWED'
    if u.username is not None or u.password is not None or port is not None:
        return None, 'USER_INFO_OR_PORT'
    if u.query or u.fragment or '?' in raw or '#' in raw:
        return None, 'QUERY_OR_FRAGMENT'
    path = u.path or ''
    if host == 'github.com' and _GITHUB.match(path):
        owner, repo, ref = _GITHUB.match(path).groups()
    elif host == 'codeload.github.com' and _CODELOAD.match(path):
        owner, repo, ref = _CODELOAD.match(path).groups()
    elif host == 'gitlab.com' and _GITLAB.match(path):
        owner, repo, ref, name = _GITLAB.match(path).groups()
        if name != f'{repo}-{ref}':
            return None, 'NOT_AN_ARCHIVE'
    else:
        return None, 'NOT_AN_ARCHIVE'
    if not _HEX40.match(ref):
        return None, 'MOVING_REF'
    third_party = host not in ('github.com', 'codeload.github.com') or owner.lower() != TELAMIN_ORG
    return {'url': raw.strip(), 'host': host, 'owner': owner, 'repo': repo, 'commit': ref.lower(),
            'sha256': digest.lower() if digest else None, 'thirdParty': third_party}, None


_EXAMPLE_CAUSE = {'NOT_HTTPS': 'EXAMPLE_NOT_HTTPS', 'HOST_NOT_ALLOWED': 'EXAMPLE_HOST',
                  'USER_INFO_OR_PORT': 'EXAMPLE_NOT_ARCHIVE', 'QUERY_OR_FRAGMENT': 'EXAMPLE_NOT_ARCHIVE',
                  'NOT_AN_ARCHIVE': 'EXAMPLE_NOT_ARCHIVE', 'MOVING_REF': 'EXAMPLE_MOVING_REF',
                  'BAD_DIGEST': 'EXAMPLE_DIGEST'}


def lint_pack(data):
    """A pack, or ONE refusal: {'pack': {...}} or {'refusal': {'line': n, 'cause': CODE}} — PromptPack.parse's shape."""
    def refuse(line, cause):
        return {'refusal': {'line': line, 'cause': cause}}

    if isinstance(data, str):
        data = data.encode('utf-8')
    if data is None or len(data) > MAX_BYTES:
        return refuse(0, 'TOO_LARGE')
    try:
        text = data.decode('utf-8', errors='strict')
    except UnicodeDecodeError:
        return refuse(0, 'NOT_UTF8')
    lines = text.replace('\r\n', '\n').split('\n')
    for i, l in enumerate(lines, 1):
        if any(c != '\t' and _hidden(ord(c)) for c in l):
            return refuse(i, 'HIDDEN_CHARACTER')
        if '![' in l:
            return refuse(i, 'IMAGE')
        if _EXAMPLE_LINE.match(l):
            continue
        if _HTML_TAG.search(l):
            return refuse(i, 'HTML')
        if _LINK.search(l):
            return refuse(i, 'LINK')
        if _PLACEHOLDER.search(l):
            return refuse(i, 'PLACEHOLDER')

    def skip_blank(k):
        while k < len(lines) and not lines[k].strip():
            k += 1
        return k

    i = skip_blank(0)
    if i >= len(lines) or not lines[i].startswith('# ') or not lines[i][2:].strip():
        return refuse(i + 1, 'NO_TITLE')
    title = lines[i][2:].strip()
    if len(title) > MAX_TITLE:
        return refuse(i + 1, 'TITLE_TOO_LONG')
    i = skip_blank(i + 1)
    description = None
    if i < len(lines) and not _ENTRY.match(lines[i]):
        description = lines[i].strip()
        if len(description) > MAX_DESCRIPTION:
            return refuse(i + 1, 'DESCRIPTION_TOO_LONG')
        i = skip_blank(i + 1)
    entries = []
    while i < len(lines):
        l = lines[i]
        if not l.strip():
            i += 1
            continue
        m = _ENTRY.match(l)
        if not m:
            return refuse(i + 1, 'NOT_AN_ENTRY' if not entries else 'EXTRA_CONTENT')
        if len(entries) == MAX_ENTRIES:
            return refuse(i + 1, 'TOO_MANY')
        if int(m.group(1)) != len(entries) + 1:
            return refuse(i + 1, 'OUT_OF_ORDER')
        b = _BOLD_LABEL.match(m.group(2))
        if not b:
            return refuse(i + 1, 'MISSING_LABEL')
        label, prompt = b.group(1).strip(), b.group(2).strip()
        if not label:
            return refuse(i + 1, 'MISSING_LABEL')
        if len(label) > MAX_LABEL:
            return refuse(i + 1, 'LABEL_TOO_LONG')
        if not prompt:
            return refuse(i + 1, 'EMPTY_TEXT')
        if len(prompt) > MAX_TEXT:
            return refuse(i + 1, 'TEXT_TOO_LONG')
        if prompt.startswith('example:'):
            x = _EXAMPLE.match(prompt)
            if not x:
                return refuse(i + 1, 'EXAMPLE_MALFORMED')
            source, why = parse_example(x.group(1) or x.group(2), x.group(3))
            if why:
                return refuse(i + 1, _EXAMPLE_CAUSE[why])
            entries.append({'label': label, 'example': source})
        else:
            entries.append({'label': label, 'text': prompt})
        i += 1
    if not entries:
        return refuse(len(lines), 'NO_ENTRIES')
    return {'pack': {'title': title, 'description': description, 'entries': entries}}


# ---------------------------------------------------------------------------------------------------------------------
# The warm cache (g14/PROTOCOL.md ▸ Cache state; tracker G14-W). Provenance from `_remote.repositories`, the rule the
# 2026-10-12/13 ladders applied by hand (their harness/provenance.py), here as a tested function.

def tree_sha256(root):
    """sha256 over sorted '<relative path>\\0<sha256 of file>\\n' lines (files; symlinks as their link text)."""
    root = Path(root)
    h = hashlib.sha256()
    for p in sorted(root.rglob('*'), key=lambda q: str(q.relative_to(root))):
        rel = str(p.relative_to(root))
        if p.is_symlink():
            d = sha256_bytes(os.readlink(p).encode())
        elif p.is_file():
            d = sha256_file(p)
        else:
            continue
        h.update(f'{rel}\0{d}\n'.encode())
    return h.hexdigest()


def m2_provenance(m2, allowed=ALLOWED_REMOTES):
    """Every jar/pom must be listed in its directory's _remote.repositories under an allowed remote. Refused: a
    SNAPSHOT, a file with no recorded source, a locally installed artefact (empty remote), any other remote."""
    m2 = Path(m2)
    counts, problems = {}, []
    dirs = sorted({p.parent for p in m2.rglob('*') if p.is_file() and p.suffix in ('.jar', '.pom')})
    for d in dirs:
        prov = {}
        rr = d / '_remote.repositories'
        if rr.exists():
            for line in rr.read_text(errors='replace').splitlines():
                mm = re.match(r'^([^#>][^>]*)>(.*)=$', line.strip())
                if mm:
                    prov.setdefault(mm.group(1), set()).add(mm.group(2))
        for f in sorted(d.iterdir()):
            if f.suffix not in ('.jar', '.pom'):
                continue
            rel = str(f.relative_to(m2))
            if 'SNAPSHOT' in rel:
                problems.append(['snapshot', rel])
                continue
            r = prov.get(f.name)
            if not r:
                problems.append(['no-provenance', rel])
            elif '' in r:
                problems.append(['locally-installed', rel])
            elif not r <= set(allowed):
                problems.append(['other-remote:' + ','.join(sorted(r)), rel])
            else:
                key = sorted(r)[0]
                counts[key] = counts.get(key, 0) + 1
    return {'artefactFilesByRemote': counts, 'problemCount': len(problems), 'problems': problems[:20],
            'clean': not problems and bool(counts)}


def seed_m2(seed, dest):
    """Copy a frozen, provenance-clean seed into a run's EMPTY repository. Returns the record to keep with the run."""
    seed, dest = Path(seed), Path(dest)
    verdict = m2_provenance(seed)
    if not verdict['clean']:
        raise ValueError(f'seed {seed} is not provenance-clean: {verdict["problemCount"]} problem(s), '
                         f'first {verdict["problems"][:1]}')
    if dest.exists() and any(dest.iterdir()):
        raise ValueError(f'{dest} is not empty — a run repository is seeded once, never merged into')
    digest = tree_sha256(seed)
    shutil.copytree(seed, dest, dirs_exist_ok=True, symlinks=True)
    if tree_sha256(dest) != digest:
        raise ValueError('the copied repository does not match its seed')
    return {'warmCache': True, 'seed': seed.name, 'seedTreeSha256': digest,
            'artefactFilesByRemote': verdict['artefactFilesByRemote']}


# ---------------------------------------------------------------------------------------------------------------------
# Predictions before results (PROTOCOL §3). One-shot seal; a run refuses an unsealed or edited prediction.

def seal(cycle_dir):
    cycle_dir = Path(cycle_dir)
    pred = cycle_dir / 'predictions.md'
    out = cycle_dir / 'seal.json'
    if not pred.exists():
        raise ValueError(f'no {pred}')
    if out.exists():
        raise ValueError(f'{out} exists — a seal is written once; a changed prediction is a new cycle')
    pinned = {}
    for name in ('virgin-pack.md', 'oracle.json', 'operator.json'):
        f = cycle_dir / name
        if f.exists():
            pinned[name] = sha256_file(f)
    record = {'predictionsSha256': sha256_file(pred), 'pinned': pinned, 'sealedUtc': utc_now()}
    out.write_text(json.dumps(record, indent=1) + '\n')
    return record


def verify_seal(cycle_dir):
    """The seal's digests still match. Returns the seal; raises naming the first file that changed."""
    cycle_dir = Path(cycle_dir)
    sf = cycle_dir / 'seal.json'
    if not sf.exists():
        raise ValueError('the cycle is not sealed: write predictions.md and run `seal` before any launch')
    s = json.loads(sf.read_text())
    if sha256_file(cycle_dir / 'predictions.md') != s['predictionsSha256']:
        raise ValueError('predictions.md changed after the seal')
    for name, digest in s.get('pinned', {}).items():
        if not (cycle_dir / name).exists() or sha256_file(cycle_dir / name) != digest:
            raise ValueError(f'{name} changed after the seal')
    return s


def init_run(cycle_dir, run_dir, track, pins):
    """Lay out a run folder and record its pins. Refuses an unsealed cycle, an unknown track and a used folder."""
    s = verify_seal(cycle_dir)
    if track not in TRACKS:
        raise ValueError(f'unknown track {track}')
    run_dir = Path(run_dir)
    if run_dir.exists() and any(run_dir.iterdir()):
        raise ValueError(f'{run_dir} is not empty — an existing or interrupted run is never overwritten')
    missing = [k for k in ('analyserJarSha256', 'claudeBinSha256', 'claudeVersion', 'model') if not pins.get(k)]
    if missing:
        raise ValueError(f'unpinned: {", ".join(missing)}')
    run_dir.mkdir(parents=True, exist_ok=True)
    config = {'run': run_dir.name, 'track': track, 'trackName': TRACKS[track]['name'],
              'needs': TRACKS[track]['needs'], 'posture': TRACKS[track]['posture'],
              'predictionsSha256': s['predictionsSha256'], 'sealedUtc': s['sealedUtc'], 'pinned': s.get('pinned', {}),
              **pins, 'createdUtc': utc_now()}
    (run_dir / 'config.json').write_text(json.dumps(config, indent=1) + '\n')
    return config


# ---------------------------------------------------------------------------------------------------------------------
# Isolation helpers (PROTOCOL §2). The analyser JVM is the subject's parent, so it is what gets the isolated homes.

SCRUB_PREFIXES = ('CODEX_', 'CLAUDE', 'ANTHROPIC', 'OPENAI', 'FLUXTION', 'RAPIDAPI')
SCRUB_EXACT = ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'CLASSPATH')


def analyser_env(base, inherited, oauth_token, wrapper_dir, java_home, maven=False):
    """The environment the analyser JVM (and so Claude Code) starts with: everything provider- or session-shaped
    scrubbed, then an isolated HOME and CLAUDE_CONFIG_DIR, the trial token, and the wrapper first on PATH. The analyser
    keeps CLAUDE_CONFIG_DIR and CLAUDE_CODE_OAUTH_TOKEN for its child (P2 ChildEnvironment), which is what makes a
    virgin config AND a login possible together."""
    base = Path(base)
    home = base / 'home'
    env = {k: v for k, v in inherited.items() if not k.startswith(SCRUB_PREFIXES) and k not in SCRUB_EXACT}
    env.update({'HOME': str(home), 'CLAUDE_CONFIG_DIR': str(home / '.claude-virgin'), 'TMPDIR': str(base / 'tmp'),
                'DISABLE_AUTOUPDATER': '1', 'JAVA_HOME': str(java_home),
                'PATH': f'{wrapper_dir}:{java_home}/bin:' + inherited.get('PATH', '/usr/bin:/bin')})
    if maven:
        env['MAVEN_OPTS'] = f'-Dmaven.repo.local={base}/tmp/m2'
        env['MAVEN_ARGS'] = f'-s {base}/tmp/settings-empty.xml'
    env['CLAUDE_CODE_OAUTH_TOKEN'] = oauth_token  # the trial token; never printed, never written to config.json
    return env


def strict_wrapper(real_cli):
    """A `claude` that adds --strict-mcp-config when the analyser's line lacks it (OD-74.18: the full posture's line
    never carries it; a capture must). It is put FIRST so no `--` or prompt position can swallow it."""
    q = str(real_cli).replace("'", "'\\''")
    return ("#!/bin/sh\n# m74 virgin-run wrapper (OD-74.18): adds --strict-mcp-config when absent\n"
            'for a in "$@"; do\n'
            f'  if [ "$a" = "--strict-mcp-config" ]; then exec \'{q}\' "$@"; fi\n'
            'done\n'
            f'exec \'{q}\' --strict-mcp-config "$@"\n')


def write_wrapper(wrapper_dir, real_cli):
    wrapper_dir = Path(wrapper_dir)
    wrapper_dir.mkdir(parents=True, exist_ok=True)
    w = wrapper_dir / 'claude'
    w.write_text(strict_wrapper(real_cli))
    w.chmod(0o555)
    return {'wrapper': str(w), 'wrapperSha256': sha256_file(w), 'realCli': str(real_cli)}


def assert_virgin_config(config_dir):
    """A virgin CLAUDE_CONFIG_DIR is absent or empty before Start. Returns its digest (of nothing) or raises."""
    p = Path(config_dir)
    if p.exists() and any(p.rglob('*')):
        raise ValueError(f'{p} is not empty — a virgin run starts with no Claude Code configuration, history or memory')
    return {'configDir': str(p), 'emptyAtStart': True}


# ---------------------------------------------------------------------------------------------------------------------
# The session record: the session processor's own audit log, exported by the harness (SessionAuditSink.export) in the
# Fluxtion record format the analyser reads. Parsed here into event, the event's fields and the node logs.

def _split_top(s, sep=','):
    """Split on `sep` at bracket depth 0 ([], {}, ())."""
    out, depth, cur = [], 0, []
    for c in s:
        if c in '[{(':
            depth += 1
        elif c in ']})':
            depth -= 1
        if c == sep and depth == 0:
            out.append(''.join(cur))
            cur = []
        else:
            cur.append(c)
    if cur:
        out.append(''.join(cur))
    return out


def _value(v):
    v = v.strip()
    if v in ('true', 'false'):
        return v == 'true'
    if v == 'null':
        return None
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    if v.startswith('[') and v.endswith(']'):
        inner = v[1:-1].strip()
        return [_value(x) for x in _split_top(inner)] if inner else []
    m = re.fullmatch(r'([A-Za-z_$][\w$]*)\[(.*)\]', v, re.S)
    if m:
        return {'@type': m.group(1), **parse_fields(m.group(2))}
    return v


def parse_fields(s):
    out = {}
    for part in _split_top(s):
        if '=' in part:
            k, v = part.split('=', 1)
            out[k.strip()] = _value(v)
    return out


def parse_event_to_string(s):
    """`Name[a=1, b=[x, y], c=Inner[d=2]]` (a Java record's toString) -> (Name, fields)."""
    s = (s or '').strip()
    m = re.fullmatch(r'([A-Za-z_$][\w$]*)\[(.*)\]', s, re.S)
    if not m:
        return s, {}
    return m.group(1), parse_fields(m.group(2))


def _flow_map(s):
    s = s.strip()
    if s.startswith('{') and s.endswith('}'):
        s = s[1:-1]
    out = {}
    for part in _split_top(s):
        if ':' in part:
            k, v = part.split(':', 1)
            out[k.strip()] = _value(v)
    return out


def parse_session_record(text):
    """Records in arrival order: {'seq', 'event', 'fields', 'eventTime', 'logs': [(node, {key: value})]}."""
    records = []
    for chunk in re.split(r'(?m)^---\s*$', text):
        if 'eventLogRecord' not in chunk:
            continue
        rec = {'seq': len(records), 'event': None, 'fields': {}, 'eventTime': None, 'logs': []}
        in_logs = False
        for line in chunk.splitlines():
            st = line.strip()
            if st.startswith('nodeLogs:'):
                in_logs = True
                continue
            if in_logs and st.startswith('- '):
                node, _, body = st[2:].partition(':')
                rec['logs'].append((node.strip(), _flow_map(body)))
                continue
            in_logs = in_logs and (not st or st.startswith('- '))
            if st.startswith('event:'):
                rec['event'] = st.split(':', 1)[1].strip()
            elif st.startswith('eventToString:'):
                _, rec['fields'] = parse_event_to_string(st.split(':', 1)[1])
            elif st.startswith('eventTime:'):
                rec['eventTime'] = _value(st.split(':', 1)[1])
        records.append(rec)
    return records


def node_values(records, key):
    """Every (seq, value, the whole node-log map) for node-log key `key`, in order."""
    out = []
    for r in records:
        for _, m in r['logs']:
            if key in m:
                out.append((r['seq'], m[key], m))
    return out


def load_jsonl(path):
    p = Path(path)
    if not p.exists():
        return []
    return [json.loads(line) for line in p.read_text().splitlines() if line.strip()]


# ---------------------------------------------------------------------------------------------------------------------
# Scoring (SCORECARD.md). Every field names its source; a field whose fact the code does not record is `unavailable`.

def boundary_check(acts, records):
    """Every person-act fact is accounted for by an operator act of the same kind, in the same order, and every act
    that expected a fact produced one. A fact with no act means something other than the operator pressed."""
    facts = {}
    for r in records:
        kind = EVENT_TO_ACT.get(r['event'])
        if kind:
            facts.setdefault(kind, []).append(r['seq'])
    pressed = {}
    for a in acts:
        if a.get('kind') in PERSON_ACT_EVENTS and a.get('expectFact', True):
            pressed.setdefault(a['kind'], []).append(a)
    unaccounted = {k: len(v) - len(pressed.get(k, [])) for k, v in facts.items()
                   if len(v) > len(pressed.get(k, []))}
    missing = {k: len(v) - len(facts.get(k, [])) for k, v in pressed.items() if len(v) > len(facts.get(k, []))}
    return {'ok': not unaccounted and not missing, 'factsWithoutAnAct': unaccounted, 'actsWithoutAFact': missing,
            'personActs': {k: len(v) for k, v in facts.items()}}


def turns(records):
    """Turns by ticket: start seq, end seq, outcome, hosted calls admitted/refused between them (OD-74.7: one at a
    time, so a call between a turn's start and end belongs to that turn)."""
    out, open_t = [], None
    for r in records:
        for _, m in r['logs']:
            if 'localAgentTurn' in m and 'generation' in m:
                open_t = {'ticket': m['localAgentTurn'], 'start': r['seq'], 'end': None, 'outcome': None,
                          'admitted': 0, 'refused': 0}
                out.append(open_t)
            elif 'localAgentCall' in m and open_t is not None and open_t['end'] is None:
                open_t['admitted' if m['localAgentCall'] == 'ADMITTED' else 'refused'] += 1
            elif 'localAgentTurnEnded' in m:
                t = next((x for x in reversed(out) if x['ticket'] == m.get('ticket') and x['end'] is None), None)
                if t:
                    t['end'], t['outcome'] = r['seq'], m['localAgentTurnEnded']
    return out


def refusals(records):
    counts = {}

    def add(k):
        counts[k] = counts.get(k, 0) + 1

    seq = []
    for r in records:
        for node, m in r['logs']:
            for key in REFUSAL_KEYS & set(m):
                add(f'{key}:{m[key]}')
                seq.append(f'{key}:{m[key]}')
            if m.get('localAgentCall') == 'REFUSED':
                add('hostedCall:REFUSED')
                seq.append('hostedCall:REFUSED')
            if m.get('proposal') == 'REFUSED':
                add(f'proposal:{m.get("why")}')
                seq.append(f'proposal:{m.get("why")}')
            if m.get('startPromptPack') == 'REFUSED':
                add(f'pack:{m.get("cause")}')
            if 'localAgentPosture' in m and m['localAgentPosture'] != 'OK':
                add(f'posture:{m["localAgentPosture"]}')
            if m.get('localAgentMode') == 'MODE_MISMATCH':
                add('posture:MODE_MISMATCH')
        if r['event'] == 'LocalAgentNoted':
            note = r['fields'].get('note')
            add(f'note:{note}')
            if note in ('PERMISSION_DENIED', 'APPROVAL_UNAVAILABLE'):
                seq.append(f'note:{note}')
    return counts, seq


def proposals(records, final_context, acts):
    offered = [v for _, v, m in node_values(records, 'proposal') if isinstance(v, str) and v.startswith('p-')]
    items = sum(m.get('items', 0) for _, v, m in node_values(records, 'proposal') if v in offered)
    applied = [m for _, v, m in node_values(records, 'proposalItem')]
    rows = (final_context or {}).get('proposals', [])
    states = {}
    for row in rows:
        if row.get('kind') == 'createProject':
            continue
        states[row.get('state')] = states.get(row.get('state'), 0) + 1
    shown = {row['id'] for row in rows if row.get('kind') != 'createProject' and row.get('state') not in
             ('refused', 'outside')}
    reviewed = {a['item'] for a in acts if a.get('kind') == 'review'}
    checked = node_values(records, 'proposalChecked')
    return {'offered': len(offered), 'items': items,
            'applied': sum(1 for m in applied if m.get('outcome') == 'APPLIED'
                           and not str(m.get('proposalItem')).endswith('.create')),
            'createApplied': any(str(m.get('proposalItem')).endswith('create') and m.get('outcome') == 'APPLIED'
                                 for m in applied),
            'finalStates': states, 'dismissed': len(node_values(records, 'proposalDismissed')),
            'itemsShown': len(shown), 'itemsReviewed': len(shown & reviewed),
            'everyItemReviewed': shown <= reviewed,
            'pairing': checked[-1][2].get('pairing') if checked else None,
            'fit': checked[-1][2].get('fit') if checked else None,
            'personAnswerPerItem': 'unavailable: R5 (ProposalApplyRequested carries only the Yes list; a No and an '
                                   'unread item look alike in the record) — reviewed is taken from the operator log'}


def approvals(records):
    asked = [r for r in records if r['event'] == 'LocalAgentPermissionRequested']
    answered = [r for r in records if r['event'] == 'LocalAgentPermissionAnswered']
    unavailable_notes = sum(1 for r in records if r['event'] == 'LocalAgentNoted'
                            and r['fields'].get('note') == 'APPROVAL_UNAVAILABLE')
    if not asked and not answered:
        return {'asked': 0, 'granted': 0, 'byClass': {}, 'deniedBy': {},
                'approvalUnavailable': unavailable_notes,
                'note': 'no P3 permission facts in this record (P3 names: spec §8.3, R8)'}
    by_class, denied_by = {}, {}
    for r in asked:
        c = r['fields'].get('toolClass')
        by_class[c] = by_class.get(c, 0) + 1
    granted = 0
    for r in answered:
        if r['fields'].get('decision') == 'ALLOW' and r['fields'].get('by') == 'PERSON':
            granted += 1
        else:
            b = r['fields'].get('by')
            denied_by[b] = denied_by.get(b, 0) + 1
    return {'asked': len(asked), 'granted': granted, 'byClass': by_class, 'deniedBy': denied_by,
            'approvalUnavailable': unavailable_notes}


def cost(records):
    obs = [r['fields'] for r in records if r['event'] == 'LocalAgentCostObserved']
    billing = [r['fields'].get('billing') for r in records if r['event'] == 'LocalAgentInitialized']
    b = billing[-1] if billing else 'UNKNOWN'
    total = max((o.get('totalCostMicros', 0) for o in obs), default=None)
    per_turn = {o['ticket']: o.get('totalCostMicros') for o in obs if o.get('ticket')}
    return {'totalUsd': None if total is None else round(total / 1e6, 6), 'billing': b,
            'basis': {'API_KEY': 'billed', 'SUBSCRIPTION': 'notional (plan; the CLI figure, not a charge)'}.get(
                b, 'unknown'), 'turnsWithCost': len(per_turn)}


def posture_integrity(records, posture):
    """A virgin or captured run must show only the hosted bridge in every turn's system/init (OD-74.18)."""
    inits = [r['fields'] for r in records if r['event'] == 'LocalAgentInitObserved']
    bad = []
    for f in inits:
        servers = f.get('servers') or []
        if posture in ('default', 'full') and servers != [f'{HOSTED_SERVER}:connected']:
            bad.append({'ticket': f.get('ticket'), 'servers': servers})
        tools = f.get('tools') or []
        foreign = [t for t in tools if isinstance(t, str) and t.startswith('mcp__')
                   and not t.startswith(f'mcp__{HOSTED_SERVER}__')]
        if foreign:
            bad.append({'ticket': f.get('ticket'), 'foreignTools': foreign})
    verdicts = [v for _, v, _ in node_values(records, 'localAgentPosture')]
    return {'ok': bool(inits) and not bad and all(v == 'OK' for v in verdicts), 'turnsChecked': len(inits),
            'problems': bad, 'skillsLoaded': 'unavailable: R7 (system/init skills are not in LocalAgentInitObserved)'}


def _pointer_matches(pointer, want):
    return all(pointer.get(k) == v for k, v in want.items())


def oracle_status(oracle, snapshot, records):
    """Each primary predicate against one snapshot (and the record as it stood at that snapshot)."""
    upto = records[:snapshot.get('recordSeq', len(records))]
    results = {}
    for i, p in enumerate(oracle.get('primary', [])):
        kind = p['kind']
        if kind == 'pointer':
            ok = any(_pointer_matches(ptr, p['match']) for ptr in snapshot.get('pointers', []))
        elif kind == 'proposal':
            pr = proposals(upto, snapshot.get('context'), [])
            applied_kinds = {row.get('kind') for row in (snapshot.get('context') or {}).get('proposals', [])
                             if row.get('state') == 'applied'}
            ok = set(p.get('appliedKinds', [])) <= applied_kinds and (
                p.get('pairing') is None or pr['pairing'] == p['pairing'])
        elif kind == 'example':
            done = any(v == 'DONE' for _, v, _ in node_values(upto, 'example'))
            commits = [r['fields'].get('source', {}).get('commit') for r in upto
                       if r['event'] == 'ExampleDownloadRequested' and isinstance(r['fields'].get('source'), dict)]
            ok = done and p.get('commit') in commits and (
                p.get('projectRootSuffix') is None
                or str((snapshot.get('context') or {}).get('project', {}).get('root', '')).endswith(
                    p['projectRootSuffix']))
        elif kind == 'node-logged-once':
            # '@new' is the node the subject added, as the observer resolved it from the GraphML difference between the
            # template and the final project (PROTOCOL §5.4); the pointer must be at the one record where it logged.
            candidates = snapshot.get('newNodes', []) if p['node'] == '@new' else [p['node']]
            counts = snapshot.get('nodeLogCounts', {})
            ok = any(counts.get(n) == 1 and any(n in ptr.get('nodes', []) for ptr in snapshot.get('pointers', []))
                     for n in candidates)
        else:
            raise ValueError(f'unknown oracle predicate {kind}')
        results[f'{i}:{kind}'] = ok
    return results


def claim_checks(oracle, answers):
    req = [re.compile(x, re.I) for x in oracle.get('claims', {}).get('require', [])]
    forbid = [re.compile(x, re.I) for x in oracle.get('claims', {}).get('forbid', [])]
    texts = [a.get('text', '') for a in answers]
    return {'requiredFound': [bool(any(r.search(t) for t in texts)) for r in req],
            'contradictions': sum(1 for f in forbid for t in texts if f.search(t)),
            'answersRead': len(texts)}


def oracle_leaks(run_dir):
    hits = 0
    for name in ('cli-session.jsonl', 'answers.jsonl'):
        p = Path(run_dir) / name
        if p.exists():
            hits += len(ORACLE_LEAK_PATTERN.findall(p.read_text(errors='replace')))
    return hits


def dead_ends(records, turn_list, snapshots, oracle, first_correct_seq, refusal_seq, final_proposals):
    out = []
    for t in turn_list:
        if t['outcome'] and t['outcome'] != 'COMPLETED':
            out.append({'type': 'TURN_NOT_COMPLETED', 'ticket': t['ticket'], 'outcome': t['outcome']})
        if t['outcome'] == 'COMPLETED' and t['admitted'] == 0 and (first_correct_seq is None
                                                                  or t['end'] < first_correct_seq):
            out.append({'type': 'ANSWERED_WITHOUT_LOOKING', 'ticket': t['ticket']})
    for a, b in zip(refusal_seq, refusal_seq[1:]):
        if a == b:
            out.append({'type': 'REPEATED_REFUSAL', 'reason': a})
    for _, v, m in node_values(records, 'startPageFlow'):
        if m.get('outcome') in ('CANCELLED', 'REFUSED', 'FAILED'):
            out.append({'type': 'FLOW_ENDED', 'entry': v, 'outcome': m.get('outcome')})
    if final_proposals['offered'] and final_proposals['itemsShown'] == 0:
        out.append({'type': 'NOTHING_APPLICABLE_PROPOSED'})
    if any(r['event'] == 'LocalAgentNoted' and r['fields'].get('note') == 'APPROVAL_UNAVAILABLE' for r in records):
        out.append({'type': 'APPROVAL_UNAVAILABLE'})
    if first_correct_seq is None:
        out.append({'type': 'NO_CORRECT_ANSWER'})
    return out


def score_run(run_dir, oracle, oracle_sha256=None):
    run_dir = Path(run_dir)
    config = json.loads((run_dir / 'config.json').read_text())
    meta = json.loads((run_dir / 'meta.json').read_text()) if (run_dir / 'meta.json').exists() else {}
    track = config['track']
    rec_path = run_dir / 'session-record.yaml'
    records = parse_session_record(rec_path.read_text()) if rec_path.exists() else []
    rec_meta = json.loads((run_dir / 'session-record.meta.json').read_text()) \
        if (run_dir / 'session-record.meta.json').exists() else {}
    acts = load_jsonl(run_dir / 'operator.jsonl')
    snaps = load_jsonl(run_dir / 'snapshots.jsonl')
    answers = load_jsonl(run_dir / 'answers.jsonl')
    t0 = acts[0]['t'] if acts else None

    complete = bool(records) and rec_meta.get('dropped') == 0 and rec_meta.get('sinkFailures', 0) == 0
    tl = turns(records)
    ref_counts, ref_seq = refusals(records)
    final_ctx = snaps[-1].get('context') if snaps else None
    props = proposals(records, final_ctx, acts)

    first = None
    for s in snaps:
        status = oracle_status(oracle, s, records)
        if status and all(status.values()):
            first = s
            break
    sends_before = None
    if first is not None:
        sends_before = sum(1 for r in records[:first.get('recordSeq', len(records))]
                           if r['event'] == 'LocalAgentSendRequested')
    claims = claim_checks(oracle, answers)
    boundary = boundary_check(acts, records)
    integrity = posture_integrity(records, config.get('posture')) if config.get('posture') != 'external' \
        else {'ok': True, 'note': 'external client: checked by T5 contract checks'}

    card = {
        'run': config['run'], 'track': track, 'complete': complete,
        'sessionRecord': {'records': len(records), 'total': rec_meta.get('total'), 'dropped': rec_meta.get('dropped')},
        'timeToFirstCorrectSeconds': None if first is None or t0 is None else round(first['t'] - t0, 3),
        'firstCorrectAfterTicket': None if first is None else first.get('afterTicket'),
        'sendsToFirstCorrect': sends_before,
        'sendCount': sum(1 for r in records if r['event'] == 'LocalAgentSendRequested'),
        'startPageSends': sum(1 for r in records if r['event'] == 'StartPagePromptSent'),
        'starts': sum(1 for r in records if r['event'] == 'LocalAgentStartRequested'),
        'turns': {'count': len(tl), 'outcomes': {o: sum(1 for t in tl if t['outcome'] == o)
                                                 for o in sorted({t['outcome'] for t in tl if t['outcome']})}},
        'hostedCalls': {'admitted': sum(t['admitted'] for t in tl), 'refused': sum(t['refused'] for t in tl),
                        'byVerb': 'unavailable: R1 (localAgentCall carries no verb)'},
        'refusalsByReason': ref_counts,
        'proposals': props,
        'approvals': approvals(records),
        'operator': {'acts': len(acts),
                     'guidedActs': sum(1 for a in acts if a.get('guided')),
                     'cannedReplies': sum(1 for a in acts if a.get('reply')),
                     'nudges': sum(1 for a in acts if a.get('nudge')),
                     'scriptedOnly': all(a.get('source') in ('script', 'policy', 'guided', 'reply', 'nudge')
                                         for a in acts)},
        'boundary': boundary,
        'postureIntegrity': integrity,
        'correctness': {'oracleSha256': oracle_sha256 or sha256_bytes(json.dumps(oracle, sort_keys=True).encode()),
                        'primaryHeld': first is not None, 'claims': claims},
        'cost': cost(records),
        'wallSeconds': meta.get('wallSeconds'), 'capped': meta.get('capped', False),
        'reaped': meta.get('reaped'), 'keyScanClean': meta.get('keyScanClean'),
        'oracleLeakHits': oracle_leaks(run_dir),
    }
    first_seq = None if first is None else first.get('recordSeq', len(records))
    card['deadEnds'] = dead_ends(records, tl, snaps, oracle, first_seq, ref_seq, props)

    checks = {'oracle': first is not None, 'noContradiction': claims['contradictions'] == 0,
              'everyItemReviewed': props['everyItemReviewed'],
              'keyScan': None if TRACKS[track]['posture'] != 'full' else meta.get('keyScanClean') is True}
    void = []
    if not complete:
        void.append('INCOMPLETE_RECORD')
    if not boundary['ok']:
        void.append('BOUNDARY')
    if not integrity['ok']:
        void.append('POSTURE_OR_CONTAMINATION')
    if card['oracleLeakHits']:
        void.append('ORACLE_LEAK')
    if meta.get('reaped') is False:
        void.append('NOT_REAPED')
    if meta.get('infraVoid'):
        void.append('INFRA:' + str(meta['infraVoid']))
    card['checks'] = checks
    card['verdict'] = ('VOID' if void else
                       'PASS' if all(checks[c] for c in TRACKS[track]['pass']) else 'FAIL')
    card['void'] = void
    return card


# ---------------------------------------------------------------------------------------------------------------------

def main(argv=None):
    ap = argparse.ArgumentParser(description='M74 onboarding virgin-LLM runner (offline half)')
    sub = ap.add_subparsers(dest='cmd', required=True)
    sub.add_parser('lint-pack').add_argument('pack')
    sub.add_parser('provenance').add_argument('m2')
    sp = sub.add_parser('seed')
    sp.add_argument('seed')
    sp.add_argument('dest')
    sub.add_parser('seal').add_argument('cycle')
    sub.add_parser('verify-seal').add_argument('cycle')
    ip = sub.add_parser('init-run')
    ip.add_argument('cycle')
    ip.add_argument('run_dir')
    ip.add_argument('track', choices=sorted(TRACKS))
    ip.add_argument('--pins', required=True, help='a JSON file of pins (jar, CLI, model, …)')
    wp = sub.add_parser('wrapper')
    wp.add_argument('wrapper_dir')
    wp.add_argument('real_cli')
    sc = sub.add_parser('score')
    sc.add_argument('run_dir')
    sc.add_argument('oracle')
    sub.add_parser('launch').add_argument('rest', nargs='*')
    ns = ap.parse_args(argv)
    try:
        if ns.cmd == 'lint-pack':
            r = lint_pack(Path(ns.pack).read_bytes())
            print(json.dumps(r, indent=1, ensure_ascii=False))
            return 0 if 'pack' in r else 1
        if ns.cmd == 'provenance':
            r = m2_provenance(ns.m2)
            print(json.dumps(r, indent=1))
            return 0 if r['clean'] else 1
        if ns.cmd == 'seed':
            print(json.dumps(seed_m2(ns.seed, ns.dest), indent=1))
            return 0
        if ns.cmd == 'seal':
            print(json.dumps(seal(ns.cycle), indent=1))
            return 0
        if ns.cmd == 'verify-seal':
            print(json.dumps(verify_seal(ns.cycle), indent=1))
            return 0
        if ns.cmd == 'init-run':
            print(json.dumps(init_run(ns.cycle, ns.run_dir, ns.track, json.loads(Path(ns.pins).read_text())),
                             indent=1))
            return 0
        if ns.cmd == 'wrapper':
            print(json.dumps(write_wrapper(ns.wrapper_dir, ns.real_cli), indent=1))
            return 0
        if ns.cmd == 'score':
            card = score_run(ns.run_dir, json.loads(Path(ns.oracle).read_text()), sha256_file(ns.oracle))
            (Path(ns.run_dir) / 'score.json').write_text(json.dumps(card, indent=1) + '\n')
            print(json.dumps(card, indent=1))
            return 0
        if ns.cmd == 'launch':
            return fail('launch needs the in-JVM operator harness, which presses the analyser\'s own controls and is '
                        'built only on the merged M74 P2 (T1–T3) and P3 (T4) code — PROTOCOL.md §6 and §9. '
                        'T5 runs today through tools/g14_runner.py / the METHOD.md rig.')
    except ValueError as exc:
        return fail(str(exc))
    return 0


if __name__ == '__main__':
    sys.exit(main())
