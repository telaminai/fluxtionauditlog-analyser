#!/usr/bin/env python3
"""Compare two Fluxtion audit logs captured for the same input, before and after a change.

    python3 audit-compare.py BEFORE.yaml AFTER.yaml [--max-diffs N] [--keep-identity-hashes]

Both files are analyser-readable YAML audit exports (Format 1). Every entry a node writes is split into
BUSINESS data - the keys the node chose to log - and FRAMEWORK data - the keys invocation tracing adds:
`thread`, `method`, `annotation`, `forkedExecution` and `asyncMethod`. Those count as framework only in an
entry that also carries `method` or `asyncMethod`; a lone `thread` or `annotation` is the node's own key,
as it is in the analyser. A record with at least one business entry is a business record, and the
business comparison is the sequence of business records: their event, event text and every node's
business entries, in dispatch order. Times and the record's thread are never compared.

The verdict comes from the business comparison alone. Framework differences - another dispatch path,
a trace-only record, a renamed callback - are reported separately and never decide it.

Exit status: 0 business behaviour did not change; 1 it changed; 2 nothing comparable (a file is
missing or holds no business record), because an empty comparison proves nothing.
Python 3 standard library only.
"""
import argparse
import difflib
import re
import sys

TRACE_KEYS = {"thread", "method", "annotation", "forkedExecution", "asyncMethod"}
TRACE_MARKERS = {"method", "asyncMethod"}
MARKER_KEYS = {"streamEnd", "streamEndRecords", "logTime"}
IDENTITY_HASH = re.compile(r"@[0-9a-f]{6,8}\b")
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
        documents = re.split(r"(?m)^---[ \t\r]*$", handle.read())
    records = []
    for doc in documents:
        lines = [l for l in doc.splitlines() if l.strip() and not l.lstrip().startswith("#")]
        if not lines:
            continue
        if lines[0].strip() != "eventLogRecord:":
            records.append({"raw": "\n".join(l.strip() for l in lines)})
            continue
        fields, items, pending = {}, [], None
        for line in lines[1:]:
            if pending is not None:                      # an item whose value ran onto the next line
                pending += "\n" + line
                line, pending = pending, None
            stripped = line.strip()
            if stripped.startswith("- "):
                match = ITEM.match(line)
                if match:
                    items.append((match.group(1), split_top_level(match.group(2))))
                else:
                    pending = line
            elif ":" in stripped and pending is None:
                key, _, value = stripped.partition(":")
                fields.setdefault(key.strip(), value.strip())
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
        traced = any(key in TRACE_MARKERS for key, _ in entries)
        is_trace = lambda key: traced and key in TRACE_KEYS
        own = tuple((k, normalise(v)) for k, v in entries if not is_trace(k))
        trace = tuple((k, v) for k, v in entries if is_trace(k))
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
    normalise = (lambda s: s) if keep_identity_hashes else (lambda s: IDENTITY_HASH.sub("@<hash>", s))
    sides = []
    for name, records in (("before", before), ("after", after)):
        split = [split_record(r, normalise) for r in records]
        business = [b for b, _ in split if b is not None]
        sides.append((business, [f for _, f in split], sum(1 for b, _ in split if b is None)))
        print(f"{name}: {len(records)} records, {len(business)} business, "
              f"{sides[-1][2]} framework-only", file=out)
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
    except OSError as error:
        print(f"audit-compare: {error}", file=sys.stderr)
        return 2
    return compare(before, after, args.max_diffs, args.keep_identity_hashes)


if __name__ == "__main__":
    sys.exit(main())
