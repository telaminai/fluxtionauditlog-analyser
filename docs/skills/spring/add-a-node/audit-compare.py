#!/usr/bin/env python3
"""Compare two Fluxtion audit logs captured for the same input, before and after a change.

    python3 audit-compare.py BEFORE.yaml AFTER.yaml [--max-diffs N] [--keep-identity-hashes] [--strict-trace]

Both files are analyser-readable YAML audit exports (Format 1). Every entry a node writes is split into
BUSINESS data - the keys the node chose to log - and FRAMEWORK data - the keys invocation tracing adds.
Tracing writes them FIRST in an entry, in a fixed order, and the rule is positional, as it is in the
analyser: an optional `thread`, then `method` (or `forkedExecution` then `asyncMethod` for a forked node),
then an optional `annotation` whose value is an annotation's simple name (`OnTrigger`). Only that leading
run is framework. Everything after it is the node's own, even when spelled like a trace key, and an entry
with no leading `method`/`asyncMethod` has no framework keys at all: a lone `thread` or `annotation` is
business. The starter's GENERATED callback audit is trace too: a fresh Spring node's handler opens its body with
`handledEvent` (then `filterId`, then `filterString`, when the event carries them), a trigger with `triggerFired:
true`, a lifecycle method with `lifecycle: <phase>`. Those exact runs, straight after the tracing run, are reported
with the framework data, so a generated node and a hand-written one that log the same business values match;
--strict-trace compares them as business again. With tracing off the callback run is simply the start of the entry.
Which nodes ran stays business: a node that showed it ran ONLY through its callback audit (a trigger that logs just
`triggerFired`) is compared as having run, so one that stops running is a difference. A difference that disappears
when a node shown by tracing alone counts the same is INCONCLUSIVE (exit 3), and so is a match where a node that ran
in both captures was reached by another event type or filter (its `handledEvent`/`filterString` changed). A record with at least one business entry is a business record, and the
business comparison is the sequence of business records: their event, event type, event text, any field the
reader does not know, and every node's business entries, in dispatch order. The times, the record's thread and
the grouping id are never compared: they vary between runs of the same input.

The verdict is about the LOGGED business entries of these two captures, not about behaviour in general: an output
or side effect that no node logs is not covered, and logging a new value shows as a difference. It comes from
the business comparison alone. Framework differences - another dispatch path,
a trace-only record, a renamed callback - are reported separately and never decide it.

Masked before comparing (`--keep-identity-hashes` keeps them): `Object.toString` identity hashes, in the
`ClassName@1a2b3c4d` shape only, and lambda class addresses (`$$Lambda/0x...`); the count is printed.
Each record is read by the analyser's own structural rule (RecordBreak, fixture C32): a quoted value that spans
lines is one value, the generator's exported-service signature belongs to its `@Override`, and a record whose
structure breaks - a value written unquoted with a line break in it, where the analyser can tell - is refused, as
the analyser reads it without its node logs. Where it cannot tell (a continuation that looks like a field), the
"field" is compared like every field but the times, the thread and the grouping id, and a `#` line or a nesting
under an unknown key - text the analyser skips - is compared with the value it sits in (unless that value is one
of the fields never compared).

Quoted scalars (format-spec section 3a, Format 1 revision 1.2): a record whose first line after `eventLogRecord:` is
`textEncoding: QUOTED_SCALARS` declares that a key, value, instance id or string header field written entirely as
one "..." scalar is text, with escapes decoded (\\\\ \\" \\n \\r \\t \\0 \\xXX \\uXXXX). The declaration is recognised
by position only and is never compared; the same spelling anywhere else is an ordinary field. An unmarked record is
read exactly as before: quotes kept, nothing decoded. A runtime that quotes writes a record bare unless a value needs
quoting, so one capture may mix marked and unmarked records. A marked record and an unmarked one whose entries
differ only as the old grammar would read the same values written bare (`reason: "a, b"` against `reason: a, b`)
match, but only when the unmarked record itself shows it was written before the runtime quoted - it holds an entry a
quoting runtime would have quoted (a keyless fragment, or a value with a separator, bracket, quote, leading
indicator or unbalanced parenthesis; a number, boolean, null or empty value proves nothing, since such text is written
bare by both runtimes) - or when the legacy reading changes nothing but surrounding whitespace in a header field. That is a
capture from before a runtime upgrade against one from after; the count of such records is printed, and the choice is
made record by record. Otherwise records are compared exactly: `a: "x, b: y"` and `a: x, b: y` from a runtime that
quotes are different.

An `annotation` straight after the method is ambiguous (tracing's from runtime 1.1.1, or an older node's own key):
when the match depends on it the verdict is INCONCLUSIVE (exit 3) unless --annotations-are-trace says it is
tracing's.

Exit status: 0 the logged business entries match; 1 they differ; 3 inconclusive (see above); 2 nothing comparable (a file is
missing, holds no business record, or is in a shape this script does not read - a list-shaped
`eventLogRecords:` export, or records run together without `---`), because an empty or misread
comparison proves nothing.
Python 3 standard library only.
"""
import argparse
import difflib
import re
import sys

MARKER_KEYS = {"streamEnd", "streamEndRecords", "logTime"}
# Object.toString's identity hash: a class name (simple name capitalised, optionally package-qualified or
# nested) directly before the @. A bare `batch@20261013` or `ops@cafe01` is a value, not a hash.
# Five to eight hex digits (an identity hash is a 31-bit value; `Release@2026` is a version), and nothing that
# continues the token after it (`Bob@deadbeef.com` is an address).
_HASH = r"@[0-9a-f]{5,8}(?![\w.@])"
IDENTITY_HASH = re.compile(r"(?<![\w$.])((?:[A-Za-z_$][\w$]*\.)*[A-Z][\w$]*|\[+L[\w.$]+;|\[+[BCDFIJSZ])" + _HASH)
# a lambda class address, JDK 9-20 (`$$Lambda$14/0x...`) and 21+ (`$$Lambda/0x...`), and the hash after it
LAMBDA_ADDRESS = re.compile(r"\$\$Lambda(?:\$\d+)?/(?:0x)?[0-9a-f]+(?:@[0-9a-f]{1,8}(?![\w.@]))?")
ANNOTATION_NAME = re.compile(r"[A-Z][A-Za-z0-9_]*")


class Unreadable(Exception):
    """A log in a shape this script does not read: comparing it would prove nothing."""


LIFECYCLE_PHASES = {"initialise", "start", "stop", "tearDown"}


def callback_run(entries, start):
    """The number of entries from `start` the starter's generated callback audit wrote (callback-audit.ts,
    Reconciler.callbackAudit): handledEvent, filterId?, filterString? | triggerFired: true | lifecycle: <phase>.
    Zero for anything else - a node's own key is never read as one by its name alone."""
    keys = [k for k, _ in entries[start:]]
    if keys[:1] == ["handledEvent"]:
        n = 1
        if keys[n:n + 1] == ["filterId"]:
            n += 1
        if keys[n:n + 1] == ["filterString"]:
            n += 1
        return n
    if keys[:1] == ["triggerFired"] and entries[start][1] == "true":
        return 1
    if keys[:1] == ["lifecycle"] and entries[start][1] in LIFECYCLE_PHASES:
        return 1
    return 0


def trace_run(entries):
    """The number of leading entries invocation tracing wrote: thread?, method | forkedExecution asyncMethod,
    annotation?. Zero when the entry does not start with that run - then every key is the node's own."""
    keys = [k for k, _ in entries]
    i = 1 if keys[:1] == ["thread"] else 0
    if keys[i:i + 1] == ["method"]:
        i += 1
    elif keys[i:i + 2] == ["forkedExecution", "asyncMethod"]:
        i += 2
    else:
        return 0
    if keys[i:i + 1] == ["annotation"] and ANNOTATION_NAME.fullmatch(entries[i][1] or ""):
        i += 1
    return i
ITEM = re.compile(r'^\s*-\s+("(?:[^"\\]|\\.)*"|\S+?):\s*\{(.*)\}\s*$', re.S)


def split_top_level(body):
    """Split `k: v, k: v` exactly as the analyser's NodeLogTokenizer.splitTopLevel does for a text export (the legacy
    grammar): on commas outside (), [], {} and outside single or double quotes, with no escapes (a backslash is
    text there), keeping every part - an empty one, a trailing one - as an entry."""
    parts, depth, start, in_s, in_d = [], 0, 0, False, False
    for i, ch in enumerate(body):
        if in_s:
            in_s = ch != "'"
            continue
        if in_d:
            in_d = ch != '"'
            continue
        if ch == "'":
            in_s = True
        elif ch == '"':
            in_d = True
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth = max(0, depth - 1)
        elif ch == "," and depth == 0:
            parts.append(body[start:i])
            start = i + 1
    parts.append(body[start:])
    entries = []
    for part in (astrip(p) for p in parts):
        key, sep, value = part.partition(":")
        entries.append((astrip(key), astrip(value)) if sep else (None, part))
    return entries


TEXT_ENCODING_KEY = "textEncoding"
QUOTED_SCALARS = "QUOTED_SCALARS"
# string header fields a record's own declaration decodes (format-spec 3a); other fields stay raw
DECODED_HEADERS = {"event", "eventType", "eventToString", "thread", "groupingId"}
_SIMPLE_ESCAPES = {"\\": "\\", '"': '"', "n": "\n", "r": "\r", "t": "\t", "0": "\0"}


def _hex(s, start, digits, end):
    """The value of `digits` ASCII hex digits at `start`, all before `end`; -1 when they are not there."""
    if start + digits > end:
        return -1
    h = s[start:start + digits]
    return int(h, 16) if all(c in "0123456789abcdefABCDEF" for c in h) else -1


def unquote(s):
    """(text, True) when `s` is entirely one double-quoted scalar, decoded as the analyser's NodeLogTokenizer.unquote
    decodes it; (s, False) otherwise. A malformed escape is kept verbatim; a dangling backslash or an unescaped
    quote inside means it is not one quoted scalar."""
    if len(s) < 2 or s[0] != '"' or s[-1] != '"':
        return s, False
    end, out, i = len(s) - 1, [], 1
    while i < end:
        c = s[i]
        if c == '"':
            return s, False
        if c != "\\":
            out.append(c)
            i += 1
            continue
        if i + 1 >= end:
            return s, False                              # dangling escape: not ours
        e = s[i + 1]
        i += 2
        if e in _SIMPLE_ESCAPES:
            out.append(_SIMPLE_ESCAPES[e])
        elif e == "x" and _hex(s, i, 2, end) >= 0:
            out.append(chr(_hex(s, i, 2, end)))
            i += 2
        elif e == "u" and _hex(s, i, 4, end) >= 0:
            v = _hex(s, i, 4, end)
            if 0xD800 <= v <= 0xDBFF:
                low = _hex(s, i + 6, 4, end) if s[i + 4:i + 6] == "\\u" and i + 5 < end else -1
                if 0xDC00 <= low <= 0xDFFF:
                    out.append(chr(0x10000 + ((v - 0xD800) << 10) + (low - 0xDC00)))
                    i += 10
                else:
                    out.append(c + e)                    # half a surrogate pair: kept verbatim
            elif 0xDC00 <= v <= 0xDFFF:
                out.append(c + e)
            else:
                out.append(chr(v))
                i += 4
        else:
            out.append(c + e)                            # unknown or short escape: kept verbatim
    return "".join(out), True


def index_of_sep(s):
    """The first top-level `: ` under the quoted grammar (NodeLogTokenizer.indexOfSep with escapes), or -1."""
    depth, in_s, in_d, i = 0, False, False, 0
    while i < len(s) - 1:
        c = s[i]
        if in_s:
            in_s = c != "'"
        elif in_d:
            if c == "\\":
                i += 1
            elif c == '"':
                in_d = False
        elif c == "'":
            in_s = True
        elif c == '"':
            in_d = True
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth = max(0, depth - 1)
        elif depth == 0 and c == ":" and s[i + 1] == " ":
            return i
        i += 1
    return -1


def split_quoted(body):
    """Split and decode `k: v, k: v` under the quoted grammar: as split_top_level, but a backslash inside double
    quotes escapes the next character, the key ends at the first top-level `: `, and a key or value that is entirely
    one quoted scalar decodes."""
    parts, depth, start, in_s, in_d, i = [], 0, 0, False, False, 0
    while i < len(body):
        ch = body[i]
        if in_s:
            in_s = ch != "'"
        elif in_d:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_d = False
        elif ch == "'":
            in_s = True
        elif ch == '"':
            in_d = True
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth = max(0, depth - 1)
        elif ch == "," and depth == 0:
            parts.append(body[start:i])
            start = i + 1
        i += 1
    parts.append(body[start:])
    entries = []
    for part in (astrip(p) for p in parts):
        sep = index_of_sep(part)
        if sep < 0 and part.endswith(":") and index_of_sep(part + " ") == len(part) - 1:
            sep = len(part) - 1                          # `key:` with nothing after it
        if sep < 0:
            entries.append((None, unquote(part)[0]))
        else:
            entries.append((unquote(astrip(part[:sep]))[0], unquote(astrip(part[sep + 1:]))[0]))
    return entries


def as_legacy(entries):
    """The entries as the legacy grammar reads the same values written bare - what a runtime that did not quote
    wrote. Used only to match a marked record against an unmarked one."""
    body = ", ".join(v if k is None else f"{k}: {v}" for k, v in entries)
    return split_top_level(body) if astrip(body) else []


def declares_quoted_scalars(lines, roles):
    """True when the record's first non-empty line after `eventLogRecord:` is the field `textEncoding: QUOTED_SCALARS`
    (format-spec 3a: by position, never by mention; a comment line there disqualifies it)."""
    for i, raw in enumerate(lines):
        if astrip(raw) == "eventLogRecord:":
            for j in range(i + 1, len(lines)):
                t = astrip(lines[j])
                if not t:
                    continue
                if roles[j] != "FIELD":
                    return False
                key, _, value = t.partition(":")
                return key == TEXT_ENCODING_KEY and astrip(value) == QUOTED_SCALARS
            return False
    return False


def astrip(s):
    """The analyser's AuditText.strip: ASCII whitespace (space, tab, CR, LF) and every leading byte-order mark, and
    nothing else - str.strip() also removes Unicode spaces, which the analyser treats as text."""
    s = s.strip(" \t\r\n")
    while s.startswith("\ufeff"):
        s = s[1:].strip(" \t\r\n")
    return s


# ---- record structure: a port of the analyser's RecordBreak.analyse and FramingScan.quoteStateAfter (UPS-1, C32) ----
# One rule says what each line of a record is. A record whose structure breaks - a value written unquoted with a
# line break in it - is one the analyser reads only up to the break, without its node logs, and this script refuses
# it: comparing what is left could report a match the text does not support.

# record fields never compared: times, the thread and the grouping id vary between runs; nodeLogs is the entries.
# Every other field - event, eventType, eventToString and any field the reader does not know - is compared, so a
# continuation line that merely LOOKS like a field (`detail: price=5`) is still compared.
NOT_COMPARED = {"eventTime", "logTime", "endTime", "thread", "groupingId", "nodeLogs"}
FIELD_KEYS = {"eventTime", "logTime", "endTime", "groupingId", "event", "eventType", "eventToString", "thread",
              "nodeLogs"}
EXPORT_SIGNATURE = re.compile(r"public [^:#'\"{}()]+\([^:#'\"{}()]*\)( throws [\w$., ]+)?")
BLOCK_SCALAR = re.compile(r"[|>][+-]?[1-9]?[+-]?")


def quote_state_after(s, open_q):
    """The quote still open at the end of one line (FramingScan.quoteStateAfter): double quotes honour backslash
    escapes, single quotes close on a lone ' ('' escapes it), and outside a quote one opens only at a value position."""
    k, n = 0, len(s)
    value_position = open_q == ""
    while k < n:
        c = s[k]
        if open_q == '"':
            if c == "\\":
                k += 2
                continue
            if c == '"':
                open_q = ""
        elif open_q == "'":
            if c == "'":
                if k + 1 < n and s[k + 1] == "'":
                    k += 2
                    continue
                open_q = ""
        else:
            if c in " \t":
                k += 1
                continue
            if value_position and c in "\"'":
                open_q, value_position = c, False
                k += 1
                continue
            if c == "#" and value_position:
                return ""
            if c == ":" and k + 1 < n and s[k + 1] == " ":
                value_position = True
                k += 2
                continue
            if c == "-" and value_position and k + 1 < n and s[k + 1] == " ":
                k += 2
                continue
            value_position = False
        k += 1
    return open_q


def key_of(t):
    if t == "eventLogRecord:":
        return None
    idx = t.find(":")
    if idx <= 0:
        return None
    key = t[:idx]
    if not key[0].isalpha() or not all(ch.isalnum() or ch == "_" for ch in key[1:]):
        return None
    return key


def analyse(lines):
    """Each line's role and the first break (None when whole), by RecordBreak.analyse's rule."""
    roles = ["OUTSIDE"] * len(lines)
    broken = None
    open_q = ""
    for line in lines:
        open_q = quote_state_after(line, open_q)
    quotes_count = open_q == ""          # quotes are trusted only when every one opened in the record closes
    field_indent, open_q, in_node, nested_ignored, block_scalar = -1, "", False, False, False
    first_event, previous_key, previous_value, seen = None, None, None, set()

    def brk(i, why):
        nonlocal broken
        if broken is None:
            broken = (i + 1, why)

    for i, raw in enumerate(lines):
        raw = raw.rstrip("\r")
        inside_quote = quotes_count and open_q != ""
        open_q = quote_state_after(raw, open_q)
        if inside_quote:
            roles[i] = "NODE_LINE" if in_node else "QUOTED"
            continue
        t = astrip(raw)
        if not t:
            continue
        if t.startswith("#"):
            if field_indent >= 0:
                # after the first field a `#` line is a value's text (no producer writes a comment there): the
                # analyser skips it, and so would a comparison that ignored it - so it is compared with its value
                roles[i] = "NODE_LINE" if in_node else "TEXT"
            continue
        indent = len(raw) - len(raw.lstrip(" \t"))
        if field_indent < 0:
            if t == "eventLogRecord:":
                continue
            if key_of(t) is None:
                continue
            field_indent = indent
        else:
            if t == "---":
                continue
            if t == "eventLogRecord:":
                brk(i, "a second 'eventLogRecord:' line (records run together without '---')")
                continue
            if indent < field_indent:
                if (indent == 0 and first_event == "ExportFunctionAuditEvent" and previous_key == "eventToString"
                        and previous_value == "@Override" and key_of(t) is None and EXPORT_SIGNATURE.fullmatch(t)):
                    roles[i] = "SIGNATURE"
                    previous_key = None
                    continue
                brk(i, "a line less indented than the record's fields")
                continue
            if in_node:
                item = t.startswith("- ") or t == "-"
                if indent > field_indent or item:
                    roles[i] = "NODE_ITEM" if item else "NODE_LINE"
                    continue
                in_node = False
            if indent > field_indent:
                if block_scalar:
                    roles[i] = "BLOCK"           # a block scalar's text: part of its field's value
                    continue
                if nested_ignored:
                    roles[i] = "TEXT"            # nesting under an unknown key: the analyser ignores it; compared here
                    continue
                brk(i, "a line more indented than the record's fields, outside its node logs")
                continue
        nested_ignored = block_scalar = False
        key = key_of(t)
        if key is None:
            brk(i, "a line at the fields' indentation that is not a field")
            continue
        roles[i] = "FIELD"
        value = astrip(t[len(key) + 1:])
        previous_key, previous_value = key, value
        if key in FIELD_KEYS:
            if key in seen:
                brk(i, f"the field '{key}' a second time")
            seen.add(key)
            if key == "nodeLogs":
                in_node = True
            elif BLOCK_SCALAR.fullmatch(value):
                block_scalar = True
            if key == "event":
                first_event = value
        elif not value:
            nested_ignored = True
    return roles, broken


def parse(path):
    """Return the log's records, each {event, text, items: [(instanceId, [(key, value)])]} or {raw}."""
    with open(path, encoding="utf-8-sig", newline="") as handle:   # split on \n only, as the analyser does
        content = handle.read()
    if re.search(r"(?m)^\s*eventLogRecords:\s*$|^\s*-\s+eventLogRecord:", content):
        raise Unreadable(f"{path}: a list-shaped (eventLogRecords:) log; export it as a document stream")
    documents = re.split(r"(?m)^---[ \t\r]*$", content)
    records = []
    for number, doc in enumerate(documents):
        all_lines = doc.split("\n")
        lines = [l for l in all_lines if astrip(l) and not astrip(l).startswith("#")]
        if not lines:
            continue
        if astrip(lines[0]) != "eventLogRecord:":
            records.append({"raw": "\n".join(astrip(l) for l in lines)})
            continue
        roles, broken = analyse(all_lines)
        if broken is not None:
            raise Unreadable(f"{path}: record structure breaks at line {broken[0]} of document {number}: {broken[1]}. "
                             "A value was written with a line break in it; the analyser reads such a record without "
                             "its node logs, so no comparison of it can be trusted")
        marked = declares_quoted_scalars(all_lines, roles)
        fields, items, last_field = {}, [], None
        for raw, role in zip(all_lines, roles):
            raw = raw.rstrip("\r")
            if role == "FIELD":
                key, _, value = astrip(raw).partition(":")
                fields[astrip(key)] = astrip(value)
                last_field = astrip(key)
            elif role in ("QUOTED", "SIGNATURE", "BLOCK", "TEXT") and last_field is not None:
                fields[last_field] += "\n" + astrip(raw)        # a value continued: compared whole
            elif role == "NODE_ITEM":
                items.append(raw)
            elif role == "NODE_LINE":
                if not items:
                    raise Unreadable(f"{path}: a node-log line before any entry in document {number}")
                items[-1] += "\n" + raw
        if marked:
            fields.pop(TEXT_ENCODING_KEY, None)          # the declaration is metadata, never a field
            for key in DECODED_HEADERS & set(fields):
                fields[key] = unquote(fields[key])[0]
        parsed = []
        for item in items:
            match = ITEM.match(item)
            if not match:
                raise Unreadable(f"{path}: a node entry that is not `- name: {{...}}` in document {number}: "
                                 f"{item.strip()[:60]}")
            body = match.group(2)
            # as the analyser's parseItem: an empty or blank body is no entry at all
            node = unquote(match.group(1))[0] if marked else match.group(1)
            parsed.append((node, (split_quoted(body) if marked else split_top_level(body)) if astrip(body) else []))
        if not parsed and fields and set(fields) <= MARKER_KEYS and "streamEnd" in fields:
            continue                                     # a stream-end marker is a container fact
        other = tuple(sorted((k, v) for k, v in fields.items() if k not in NOT_COMPARED | {"event", "eventToString"}))
        records.append({"event": fields.get("event"), "text": fields.get("eventToString"), "other": other,
                        "items": parsed, "marked": marked})
    return records


RAN = ("(ran)", "")    # business marker: a node that showed it ran only through its generated callback audit


def callback_entries(entries):
    """The generated callback audit of one node entry: the callback run after the tracing run."""
    run = trace_run(entries)
    return tuple(entries[run:run + callback_run(entries, run)])


def split_record(record, normalise, annotations_are_trace=True, legacy=False, callbacks_are_trace=True,
                 presence="callback"):
    """Return (business view or None, framework view) of one record.

    An `annotation` at the end of the trace run is AMBIGUOUS: Fluxtion 1.1.1 tracing writes the dispatching
    annotation there, and a node of an older runtime that logged its own `annotation: Approved` first reads the
    same. With annotations_are_trace=False it is kept in the business view, so the caller can tell whether the
    verdict depends on it. With legacy=True a marked record's business entries are read as the legacy grammar reads
    the same values written bare (as_legacy)."""
    if "raw" in record:
        return ("UNPARSED", normalise(record["raw"])), ()
    business, framework = [], []
    for node, entries in record["items"]:
        run = trace_run(entries)
        end = run + (callback_run(entries, run) if callbacks_are_trace else 0)
        rest, trace = entries[end:], entries[:end]
        if not annotations_are_trace and run and entries[run - 1][0] == "annotation":
            # The ambiguous annotation stays business; the generated callback run after it is still trace.
            rest, trace = [entries[run - 1]] + rest, entries[:run - 1] + entries[run:end]
        own_entries = as_legacy(rest) if legacy and record.get("marked") else rest
        own = tuple((k, normalise(v)) for k, v in own_entries)
        trace = tuple(trace)
        if own:
            business.append((node, own))
        elif callbacks_are_trace and (end > run or presence == "any" and trace):
            # Which nodes ran stays business: a trigger that logs only `triggerFired` and stops running is a change.
            # presence="any" also counts a node shown by tracing alone, to tell that apart from a real difference.
            business.append((node, (RAN,)))
        if trace:
            framework.append((node, trace))
    strip = astrip if legacy and record.get("marked") else (lambda v: v)   # a bare header lost its edge whitespace
    head = (strip(record["event"]) if record["event"] is not None else None, normalise(strip(record["text"] or "")),
            tuple((k, normalise(strip(v) if k in DECODED_HEADERS else v)) for k, v in record.get("other", ())))
    return ((head, tuple(business)) if business else None), (head, tuple(framework))


def describe(view):
    if view[0] == "UNPARSED":
        return "unparsed document: " + view[1][:120]
    (event, text, other), nodes = view
    shown = "; ".join(f"{n} {{{', '.join(f'{k}: {v}' for k, v in e)}}}" for n, e in nodes)
    extra = "".join(f" {k}={v[:30]}" for k, v in other)
    return f"{event}{extra} [{text[:60]}] {shown}"


def compare(before, after, max_diffs=10, keep_identity_hashes=False, out=sys.stdout, annotations_are_trace=False,
            callbacks_are_trace=True):
    masked = [0]

    def mask(s):
        s, lambdas = LAMBDA_ADDRESS.subn("$$Lambda/<address>", s)
        s, hashes = IDENTITY_HASH.subn(r"\1@<hash>", s)
        masked[0] += hashes + lambdas
        return s
    normalise = (lambda s: s) if keep_identity_hashes else mask
    sides = []
    for name, records in (("before", before), ("after", after)):
        split = [split_record(r, normalise, callbacks_are_trace=callbacks_are_trace) for r in records]
        business = [b for b, _ in split if b is not None]
        sides.append((business, [f for _, f in split], sum(1 for b, _ in split if b is None)))
        print(f"{name}: {len(records)} records, {len(business)} business, "
              f"{sides[-1][2]} framework-only", file=out)
    if masked[0]:
        print(f"masked: {masked[0]} identity hash(es) or lambda address(es) (--keep-identity-hashes to compare them)",
              file=out)
    if not sides[0][0] or not sides[1][0]:
        print("verdict: NOTHING TO COMPARE - a log holds no business record, so no conclusion follows", file=out)
        return 2
    business_before, business_after, upgraded = business_views(before, after, normalise,
                                                                callbacks_are_trace=callbacks_are_trace)
    if upgraded:
        print(f"quoted scalars: {upgraded} business record(s) match only as the legacy grammar reads them - one capture "
              "declares quoted scalars there and the other does not (a runtime upgrade between the captures)", file=out)
    changed = report("business", business_before, business_after, max_diffs, out)
    report("framework (reported, does not decide the verdict)", sides[0][1], sides[1][1], max_diffs, out)
    if sides[0][2] != sides[1][2]:
        print(f"  framework-only records: {sides[0][2]} before, {sides[1][2]} after", file=out)
    print("note: this compares what nodes LOGGED for these captures; an output or side effect no node logged is not "
          "covered, and a change to what is logged shows as a difference", file=out)
    if not changed and not annotations_are_trace:
        strict = business_views(before, after, normalise, annotations_are_trace=False,
                                callbacks_are_trace=callbacks_are_trace)
        if strict[0] != strict[1]:
            print("verdict: INCONCLUSIVE - the entries match only if every `annotation` written straight after the "
                  "method is tracing's (Fluxtion 1.1.1+); a node of an older runtime logging its own `annotation` "
                  "reads the same. Pass --annotations-are-trace when both captures come from runtime 1.1.1 or later "
                  "and no node logs a key called annotation first.", file=out)
            return 3
    if changed and callbacks_are_trace:
        relaxed = business_views(before, after, normalise, callbacks_are_trace=True, presence="any")
        if relaxed[0] == relaxed[1]:
            print("verdict: INCONCLUSIVE - the entries match only if a node shown by tracing alone counts the same as one "
                  "shown by its generated callback audit (handledEvent, triggerFired, lifecycle). Read the business "
                  "differences above; --strict-trace compares the callback audit as business.", file=out)
            return 3
    if not changed and callbacks_are_trace:
        routed = routing_changes(before, after, normalise)
        if routed:
            print("verdict: INCONCLUSIVE - the business entries match, but the generated callback audit of a node that "
                  "ran in both captures changed (another event type or filter reached it):", file=out)
            for line in routed[:max_diffs]:
                print("  " + line, file=out)
            print("  rerun with --strict-trace to compare the callback audit as business", file=out)
            return 3
    print("verdict: logged business entries " + ("DIFFER" if changed else "MATCH") + " for these captures", file=out)
    return 1 if changed else 0


def routing_changes(before, after, normalise):
    """Callback-audit VALUES that differ for a node that ran in both captures, record by record. Called only when the
    business views match, so the business records pair one to one; a node with callback audit on one side only (a
    generated node against a hand-written one) is not a routing change."""
    sides = []
    for records in (before, after):
        rows = []
        for r in records:
            if "raw" in r or split_record(r, normalise)[0] is None:
                continue
            rows.append({node: tuple((k, normalise(v)) for k, v in callback_entries(entries))
                         for node, entries in r["items"] if callback_entries(entries)})
        sides.append(rows)
    changes = []
    for number, (b, a) in enumerate(zip(*sides), 1):
        for node in b.keys() & a.keys():
            if b[node] != a[node]:
                changes.append(f"business record {number}, {node}: {dict(b[node])} -> {dict(a[node])}")
    return changes


def business_views(before, after, normalise, annotations_are_trace=True, callbacks_are_trace=True, presence="callback"):
    """(before views, after views, upgraded): the business views the verdict compares, chosen record by record.
    A record is read exactly unless it pairs - in the alignment of both captures under the legacy grammar - with a
    record whose declaration differs, the two read alike under the legacy grammar, and the legacy reading is safe:
    the marked record's entries read the same in both grammars (only header whitespace differs), or the unmarked
    record proves it was written before the runtime quoted (proves_pre_quoting). Then both are read under the legacy
    grammar; `upgraded` counts those pairs. Everything else is exact, so a real change is reported once and an
    upgrade-only record beside it is not."""
    sides = []
    for records in (before, after):
        rows = []
        for r in records:
            exact = split_record(r, normalise, annotations_are_trace, callbacks_are_trace=callbacks_are_trace,
                                 presence=presence)[0]
            if exact is None:
                continue
            legacy = split_record(r, normalise, annotations_are_trace, legacy=True, callbacks_are_trace=callbacks_are_trace,
                                  presence=presence)[0]
            marked = r.get("marked", False)
            rows.append((exact, legacy, marked, marked and exact[1] != legacy[1], not marked and proves_pre_quoting(r)))
        sides.append(rows)
    views = [[row[0] for row in rows] for rows in sides]
    upgraded = 0
    matcher = difflib.SequenceMatcher(None, [row[1] for row in sides[0]], [row[1] for row in sides[1]], autojunk=False)
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag != "equal":
            continue
        for i, j in zip(range(i1, i2), range(j1, j2)):
            b, a = sides[0][i], sides[1][j]
            if b[0] == a[0] or b[2] == a[2]:
                continue                                 # already equal, or both in one grammar: exact
            marked_row, bare_row = (b, a) if b[2] else (a, b)
            if marked_row[3] and not bare_row[4]:
                continue                                 # the legacy reading would merge entries a quoting runtime split
            views[0][i], views[1][j] = b[1], a[1]
            upgraded += 1
    return views[0], views[1], upgraded


# The quoting runtime's flow rule (fluxtion-runtime YamlScalar.needsQuoting): an indicator at the start (`-` only
# when whitespace follows it), a comma, bracket, `#`, quote, `?`, control or non-printable character anywhere, `: `
# or a trailing `:`, edge whitespace, or an unbalanced parenthesis. A `:` followed by anything else (12:30:00, a URL)
# counts only as runtimes that leave it bare read it, so it never proves age. Typed-looking text - a number, boolean or null spelling, an empty
# value - is written bare, as the runtime before it wrote it, so it proves nothing.
_INDICATOR_START = set("-?:,[]{}#&*!|>'\"%@`=<")
_FLOW_SPECIAL = set(",{}[]#\"'?")


def _special(c):
    o = ord(c)
    return (c in _FLOW_SPECIAL or o < 32 or 0x7F <= o <= 0x9F or o in (0x2028, 0x2029, 0xFEFF, 0xFFFE, 0xFFFF)
            or 0xD800 <= o <= 0xDFFF)


def would_quote(value):
    """True when a runtime that quotes would have written this bare value quoted (YamlScalar.needsQuoting). Edge
    whitespace the legacy reading stripped (ASCII) cannot be seen; Unicode edge spaces it keeps can."""
    if not value:
        return False
    first = value[0]
    if len(value) == 1:
        return first in _INDICATOR_START or _special(first) or first.isspace() or first in "()"
    if (value[1].isspace() if first == "-" else first in _INDICATOR_START) or first.isspace() or value[-1].isspace():
        return True
    depth = 0
    for i, c in enumerate(value):
        if _special(c) or (c == ":" and (i + 1 == len(value) or value[i + 1] == " ")):
            return True
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth < 0:
                return True
    return depth != 0


def proves_pre_quoting(record):
    """True when an unmarked record could not have been written by a runtime that quotes: one of its entries is a
    keyless fragment (a bare comma split a value) or a value such a runtime would have quoted, and so marked."""
    return any(k is None or would_quote(v) for _, entries in record.get("items", ()) for k, v in entries)


def report(title, before, after, max_diffs, out):
    opcodes = [op for op in difflib.SequenceMatcher(None, before, after, autojunk=False).get_opcodes()
               if op[0] != "equal"]
    print(f"{title}: " + (f"DIFFERENT ({len(opcodes)} change(s))" if opcodes else "same"), file=out)
    if title == "business":
        for tag, i1, i2, j1, j2 in opcodes[:max_diffs]:
            print(f"  {tag} business record(s) before[{i1}:{i2}] after[{j1}:{j2}]", file=out)
            for view in before[i1:i2]:
                print("    - " + describe(view), file=out)
            for view in after[j1:j2]:
                print("    + " + describe(view), file=out)
        if len(opcodes) > max_diffs:
            print(f"  ... {len(opcodes) - max_diffs} more", file=out)
    return bool(opcodes)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--max-diffs", type=int, default=10)
    parser.add_argument("--annotations-are-trace", action="store_true",
                        help="treat an `annotation` straight after the method as tracing's (both captures from "
                             "Fluxtion runtime 1.1.1+, and no node logs a key called annotation first)")
    parser.add_argument("--keep-identity-hashes", action="store_true",
                        help="compare Object.toString identity hashes (@1a2b3c4d) instead of masking them")
    parser.add_argument("--strict-trace", action="store_true",
                        help="compare the starter's generated callback audit (handledEvent, filterId, filterString, "
                             "triggerFired, lifecycle) as business data instead of reporting it with the framework")
    args = parser.parse_args(argv)
    try:
        before, after = parse(args.before), parse(args.after)
    except (OSError, Unreadable) as error:
        print(f"audit-compare: {error}", file=sys.stderr)
        print("verdict: NOTHING TO COMPARE - a log could not be read, so no conclusion follows")
        return 2
    return compare(before, after, args.max_diffs, args.keep_identity_hashes,
                   annotations_are_trace=args.annotations_are_trace, callbacks_are_trace=not args.strict_trace)


if __name__ == "__main__":
    sys.exit(main())
