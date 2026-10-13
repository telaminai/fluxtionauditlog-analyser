#!/usr/bin/env python3
"""Compare two Fluxtion audit logs captured for the same input, before and after a change.

    python3 audit-compare.py BEFORE.yaml AFTER.yaml [--max-diffs N] [--keep-identity-hashes]

Both files are analyser-readable YAML audit exports (Format 1). Every entry a node writes is split into
BUSINESS data - the keys the node chose to log - and FRAMEWORK data - the keys invocation tracing adds.
Tracing writes them FIRST in an entry, in a fixed order, and the rule is positional, as it is in the
analyser: an optional `thread`, then `method` (or `forkedExecution` then `asyncMethod` for a forked node),
then an optional `annotation` whose value is an annotation's simple name (`OnTrigger`). Only that leading
run is framework. Everything after it is the node's own, even when spelled like a trace key, and an entry
with no leading `method`/`asyncMethod` has no framework keys at all: a lone `thread` or `annotation` is
business. A record with at least one business entry is a business record, and the
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
        parsed = []
        for item in items:
            match = ITEM.match(item)
            if not match:
                raise Unreadable(f"{path}: a node entry that is not `- name: {{...}}` in document {number}: "
                                 f"{item.strip()[:60]}")
            body = match.group(2)
            # as the analyser's parseItem: an empty or blank body is no entry at all
            parsed.append((match.group(1), split_top_level(body) if astrip(body) else []))
        if not parsed and fields and set(fields) <= MARKER_KEYS and "streamEnd" in fields:
            continue                                     # a stream-end marker is a container fact
        other = tuple(sorted((k, v) for k, v in fields.items() if k not in NOT_COMPARED | {"event", "eventToString"}))
        records.append({"event": fields.get("event"), "text": fields.get("eventToString"), "other": other,
                        "items": parsed})
    return records


def split_record(record, normalise, annotations_are_trace=True):
    """Return (business view or None, framework view) of one record.

    An `annotation` at the end of the trace run is AMBIGUOUS: Fluxtion 1.1.1 tracing writes the dispatching
    annotation there, and a node of an older runtime that logged its own `annotation: Approved` first reads the
    same. With annotations_are_trace=False it is kept in the business view, so the caller can tell whether the
    verdict depends on it."""
    if "raw" in record:
        return ("UNPARSED", normalise(record["raw"])), ()
    business, framework = [], []
    for node, entries in record["items"]:
        run = trace_run(entries)
        if not annotations_are_trace and run and entries[run - 1][0] == "annotation":
            run -= 1
        own = tuple((k, normalise(v)) for k, v in entries[run:])
        trace = tuple(entries[:run])
        if own:
            business.append((node, own))
        if trace:
            framework.append((node, trace))
    head = (record["event"], normalise(record["text"] or ""), tuple((k, normalise(v)) for k, v in record.get("other", ())))
    return ((head, tuple(business)) if business else None), (head, tuple(framework))


def describe(view):
    if view[0] == "UNPARSED":
        return "unparsed document: " + view[1][:120]
    (event, text, other), nodes = view
    shown = "; ".join(f"{n} {{{', '.join(f'{k}: {v}' for k, v in e)}}}" for n, e in nodes)
    extra = "".join(f" {k}={v[:30]}" for k, v in other)
    return f"{event}{extra} [{text[:60]}] {shown}"


def compare(before, after, max_diffs=10, keep_identity_hashes=False, out=sys.stdout, annotations_are_trace=False):
    masked = [0]

    def mask(s):
        s, lambdas = LAMBDA_ADDRESS.subn("$$Lambda/<address>", s)
        s, hashes = IDENTITY_HASH.subn(r"\1@<hash>", s)
        masked[0] += hashes + lambdas
        return s
    normalise = (lambda s: s) if keep_identity_hashes else mask
    sides = []
    for name, records in (("before", before), ("after", after)):
        split = [split_record(r, normalise) for r in records]
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
    changed = report("business", sides[0][0], sides[1][0], max_diffs, out)
    report("framework (reported, does not decide the verdict)", sides[0][1], sides[1][1], max_diffs, out)
    if sides[0][2] != sides[1][2]:
        print(f"  framework-only records: {sides[0][2]} before, {sides[1][2]} after", file=out)
    print("note: this compares what nodes LOGGED for these captures; an output or side effect no node logged is not "
          "covered, and a change to what is logged shows as a difference", file=out)
    if not changed and not annotations_are_trace:
        strict = [[b for b in (split_record(r, normalise, False)[0] for r in records) if b is not None]
                  for records in (before, after)]
        if strict[0] != strict[1]:
            print("verdict: INCONCLUSIVE - the entries match only if every `annotation` written straight after the "
                  "method is tracing's (Fluxtion 1.1.1+); a node of an older runtime logging its own `annotation` "
                  "reads the same. Pass --annotations-are-trace when both captures come from runtime 1.1.1 or later "
                  "and no node logs a key called annotation first.", file=out)
            return 3
    print("verdict: logged business entries " + ("DIFFER" if changed else "MATCH") + " for these captures", file=out)
    return 1 if changed else 0


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
    args = parser.parse_args(argv)
    try:
        before, after = parse(args.before), parse(args.after)
    except (OSError, Unreadable) as error:
        print(f"audit-compare: {error}", file=sys.stderr)
        print("verdict: NOTHING TO COMPARE - a log could not be read, so no conclusion follows")
        return 2
    return compare(before, after, args.max_diffs, args.keep_identity_hashes,
                   annotations_are_trace=args.annotations_are_trace)


if __name__ == "__main__":
    sys.exit(main())
