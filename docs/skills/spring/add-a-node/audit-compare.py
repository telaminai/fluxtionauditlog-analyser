#!/usr/bin/env python3
"""Compare two Fluxtion audit logs captured for the same input, before and after a change.

    python3 audit-compare.py BEFORE.yaml AFTER.yaml [--max-diffs N] [--keep-identity-hashes]

Both files are analyser-readable YAML audit exports (Format 1). Every entry a node writes is split into
BUSINESS data - the keys the node chose to log - and FRAMEWORK data - the keys invocation tracing adds.
Tracing writes them FIRST in an entry, in a fixed order, and the rule is positional, as it is in the
analyser: an optional `thread` (before or just after the method), then `method` (or `forkedExecution` then `asyncMethod` for a forked node),
then an optional `annotation` whose value is an annotation's simple name (`OnTrigger`). Only that leading
run is framework. Everything after it is the node's own, even when spelled like a trace key, and an entry
with no leading `method`/`asyncMethod` has no framework keys at all: a lone `thread` or `annotation` is
business. A record with at least one business entry is a business record, and the
business comparison is the sequence of business records: their event, event text and every node's
business entries, in dispatch order. Times and the record's thread are never compared.

The verdict comes from the business comparison alone. Framework differences - another dispatch path,
a trace-only record, a renamed callback - are reported separately and never decide it.

Masked before comparing (`--keep-identity-hashes` keeps them): `Object.toString` identity hashes, in the
`ClassName@1a2b3c4d` shape only, and lambda class addresses (`$$Lambda/0x...`); the count is printed.
Event text that runs onto further lines (an exported service's `@Override` + signature) is compared whole.

Exit status: 0 business behaviour did not change; 1 it changed; 2 nothing comparable (a file is
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
IDENTITY_HASH = re.compile(r"(?<![\w$.])((?:[a-z_$][\w$]*\.)*[A-Z][\w$]*)@[0-9a-f]{1,8}\b")
LAMBDA_ADDRESS = re.compile(r"\$\$Lambda/0x[0-9a-f]+(?:@[0-9a-f]{1,8}\b)?")  # the address, and its hash
ANNOTATION_NAME = re.compile(r"[A-Z][A-Za-z0-9_]*")


class Unreadable(Exception):
    """A log in a shape this script does not read: comparing it would prove nothing."""


def trace_run(entries):
    """The number of leading entries invocation tracing wrote: thread?, method | forkedExecution asyncMethod,
    annotation?. Zero when the entry does not start with that run - then every key is the node's own."""
    keys = [k for k, _ in entries]
    thread = keys[:1] == ["thread"]
    i = 1 if thread else 0
    if keys[i:i + 1] == ["method"]:
        i += 1
    elif keys[i:i + 2] == ["forkedExecution", "asyncMethod"]:
        i += 2
    else:
        return 0
    if not thread and keys[i:i + 1] == ["thread"]:   # older logs: method, then thread (as the analyser reads it)
        i += 1
    if keys[i:i + 1] == ["annotation"] and ANNOTATION_NAME.fullmatch(entries[i][1] or ""):
        i += 1
    return i
ITEM = re.compile(r"^\s*-\s+(\S+?):\s*\{(.*)\}\s*$", re.S)


def split_top_level(body):
    """Split `k: v, k: v` on commas outside (), [], {} and double quotes (Format 1 section 3)."""
    parts, depth, quoted, start = [], 0, False, 0
    for i, ch in enumerate(body):
        if ch == '"':
            quoted = not quoted
        elif not quoted and ch in "([{":
            depth += 1
        elif not quoted and ch in ")]}":
            depth = max(0, depth - 1)
        elif not quoted and depth == 0 and ch == ",":
            parts.append(body[start:i])
            start = i + 1
    parts.append(body[start:])
    entries = []
    for part in (p.strip() for p in parts):
        if part:
            key, sep, value = part.partition(":")
            entries.append((key.strip(), value.strip()) if sep else (None, part))
    return entries


def parse(path):
    """Return the log's records, each {event, text, items: [(instanceId, [(key, value)])]} or {raw}."""
    with open(path, encoding="utf-8-sig") as handle:
        content = handle.read()
    if re.search(r"(?m)^\s*eventLogRecords:\s*$|^\s*-\s+eventLogRecord:", content):
        raise Unreadable(f"{path}: a list-shaped (eventLogRecords:) log; export it as a document stream")
    documents = re.split(r"(?m)^---[ \t\r]*$", content)
    records = []
    for doc in documents:
        lines = [l for l in doc.splitlines() if l.strip() and not l.lstrip().startswith("#")]
        if not lines:
            continue
        if sum(1 for l in lines if l.strip() == "eventLogRecord:") > 1:
            raise Unreadable(f"{path}: records run together without a '---' separator: "
                             + lines[0].strip()[:40] + " ...")
        if lines[0].strip() != "eventLogRecord:":
            records.append({"raw": "\n".join(l.strip() for l in lines)})
            continue
        fields, items, pending, last_field = {}, [], None, None
        for line in lines[1:]:
            if pending is not None:                      # an item whose value ran onto the next line
                pending += "\n" + line
                line, pending = pending, None
            stripped = line.strip()
            if not line[:1].isspace() and last_field is not None and not items:
                fields[last_field] += "\n" + stripped   # a field value continued at column 0 (event text)
                continue
            if stripped.startswith("- "):
                match = ITEM.match(line)
                if match:
                    items.append((match.group(1), split_top_level(match.group(2))))
                else:
                    pending = line
            elif ":" in stripped and pending is None:
                key, _, value = stripped.partition(":")
                if key.strip() not in fields:
                    fields[key.strip()] = value.strip()
                    last_field = key.strip()
                else:
                    last_field = None
        if pending is not None:
            items.append(("?", [(None, pending.strip())]))
        if not items and fields and set(fields) <= MARKER_KEYS and "streamEnd" in fields:
            continue                                     # a stream-end marker is a container fact
        records.append({"event": fields.get("event"), "text": fields.get("eventToString"), "items": items})
    return records


def split_record(record, normalise):
    """Return (business view or None, framework view) of one record."""
    if "raw" in record:
        return ("UNPARSED", normalise(record["raw"])), ()
    business, framework = [], []
    for node, entries in record["items"]:
        run = trace_run(entries)
        own = tuple((k, normalise(v)) for k, v in entries[run:])
        trace = tuple(entries[:run])
        if own:
            business.append((node, own))
        if trace:
            framework.append((node, trace))
    head = (record["event"], normalise(record["text"] or ""))
    return ((head, tuple(business)) if business else None), (head, tuple(framework))


def describe(view):
    if view[0] == "UNPARSED":
        return "unparsed document: " + view[1][:120]
    (event, text), nodes = view
    shown = "; ".join(f"{n} {{{', '.join(f'{k}: {v}' for k, v in e)}}}" for n, e in nodes)
    return f"{event} [{text[:60]}] {shown}"


def compare(before, after, max_diffs=10, keep_identity_hashes=False, out=sys.stdout):
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
    print("verdict: business behaviour " + ("CHANGED" if changed else "did not change"), file=out)
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
    parser.add_argument("--keep-identity-hashes", action="store_true",
                        help="compare Object.toString identity hashes (@1a2b3c4d) instead of masking them")
    args = parser.parse_args(argv)
    try:
        before, after = parse(args.before), parse(args.after)
    except (OSError, Unreadable) as error:
        print(f"audit-compare: {error}", file=sys.stderr)
        print("verdict: NOTHING TO COMPARE - a log could not be read, so no conclusion follows")
        return 2
    return compare(before, after, args.max_diffs, args.keep_identity_hashes)


if __name__ == "__main__":
    sys.exit(main())
