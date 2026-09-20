#!/usr/bin/env python3
"""Score a cold-start run: parse the journal, fingerprint the resulting project, emit a scoresheet.

usage: score_journal.py <JOURNAL.md> [project-dir] [--baseline <pristine-download-dir>] [--json]

--baseline is strongly recommended: it is the untouched extracted download. Files identical to it are
excluded from the fingerprints, so the score reflects what the SUBJECT did, not what the template ships.

Objective counts only. Anything this cannot establish is printed under MANUAL so it is scored by hand
rather than silently assumed. The transcript, not the journal, is the evidence of record.
"""
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

ENTRY = re.compile(r"^##\s*(E\d+)\s*·\s*(\S+)\s*·\s*(\w+)\s*$", re.M)
FIELD = re.compile(r"^(what|why|from):\s*(.*)$", re.M)
FROM_KINDS = ["runbook", "readme", "contract", "error", "stub", "example", "search", "prior", "operator"]
ROUTED = {"runbook", "readme", "contract", "error", "stub"}      # a shipped artefact did the work
UNROUTED = {"search", "prior", "operator"}                        # the subject did the work

# Static fingerprints over the finished project. Each is (id, description, predicate).
# These find traps that SURVIVED to the end. Traps hit and then fixed appear as ERROR entries instead.


def subject_files(root, baseline, pattern):
    """Files matching pattern that the subject created or modified. Identical-to-baseline files are the
    template's, not the subject's, and scoring them would credit the subject with the template's shape."""
    out = []
    for p in root.rglob(pattern):
        if {"target", "generated", ".git", ".fluxtion", "coldstart"} & set(p.parts):
            continue
        if baseline:
            b = baseline / p.relative_to(root)
            if b.exists() and b.read_bytes() == p.read_bytes():
                continue
        out.append(p)
    return out


NODE_ANNOTATION = re.compile(r"^\s*@(?:[\w.]*\.)?(OnEventHandler|OnTrigger)\b", re.M)


def fingerprints(root, baseline):
    if not root or not root.exists():
        return [("--", "project directory not supplied or missing; all fingerprints skipped", None)]
    srcs = subject_files(root, baseline, "*.java")
    text = {p: p.read_text(errors="replace") for p in srcs}
    rel = lambda p: str(p.relative_to(root))
    nodes = [p for p, t in text.items() if NODE_ANNOTATION.search(t)]
    out = []

    # T-MAIN: a hand-rolled harness - main() that builds the processor and feeds it literal events
    out.append(("T-MAIN", "hand-rolled harness feeding literal events instead of the shipped feed",
                [rel(p) for p, t in text.items()
                 if "static void main" in t and re.search(r"\.onEvent\(\s*new\s", t)]))

    # T-SLEEP: Thread.sleep pacing - the v1 tell for a hand-written scenario
    out.append(("T-SLEEP", "Thread.sleep pacing in a harness",
                [rel(p) for p, t in text.items() if "Thread.sleep" in t]))

    # T-EVENTLOG: a node with a handler that never writes to the audit log
    out.append(("T-EVENTLOG", "node with a handler that never writes to the audit log",
                [rel(p) for p in nodes if "auditLog" not in text[p]]))

    # T-RETURN-TRUE: a signal handler returning true - the v1 double-booking hazard
    sig = []
    for p in nodes:
        for m in re.finditer(r"filterString\s*=\s*\"(\w+)\"[\s\S]{0,400}?return\s+(true|false)\s*;", text[p]):
            if m.group(2) == "true":
                sig.append(f"{rel(p)}:{m.group(1)}")
    out.append(("T-RETURN-TRUE", "signal handler returns true (propagates to dependents)", sig))

    # T-TRANSIENT: a NODE's non-static final collection field with no transient / @FluxtionIgnore
    leak = []
    for p in nodes:
        for m in re.finditer(r"^[ \t]*(?!.*@FluxtionIgnore)(?:private|public|protected)?[ \t]*"
                             r"(?!.*\bstatic\b)(?=.*\bfinal\b)(?!.*\btransient\b)"
                             r"[\w<>, .\[\]]*\b(?:Map|List|Set|HashMap|ArrayList|TreeMap|Collection)\s*<[^;]*>\s+(\w+)\s*[=;]",
                             text[p], re.M):
            leak.append(f"{rel(p)}:{m.group(1)}")
    out.append(("T-TRANSIENT", "node field: final collection without transient/@FluxtionIgnore", leak))

    # T-TWOFEEDS: >1 feed in a single host config - ordering across feeds is not deterministic
    multi = []
    for y in subject_files(root, baseline, "*.yml") + subject_files(root, baseline, "*.yaml"):
        block = re.search(r"^eventFeeds:\s*$(.*?)(?=^\w|\Z)", y.read_text(errors="replace"), re.M | re.S)
        if block:
            names = re.findall(r"^\s+-\s*name:\s*(\S+)", block.group(1), re.M)
            if len(names) > 1:
                multi.append(f"{rel(y)}: {', '.join(names)}")
    out.append(("T-TWOFEEDS", "more than one event feed in one host config", multi))

    # T-NODEBEANS-JAR: a nodeBeans entry whose class has no source anywhere in the project
    xml = "\n".join(p.read_text(errors="replace") for p in root.rglob("*.xml")
                    if "target" not in p.parts)
    names = {p.stem for p in root.rglob("*.java")}
    listed = set(re.findall(r"<value>(\w+)</value>", xml))
    out.append(("T-NODEBEANS-JAR", "nodeBeans entry whose class has no source in the project",
                [f"{i}={c}" for i, c in re.findall(r"<bean\s+id=\"(\w+)\"\s+class=\"([\w.$]+)\"", xml)
                 if i in listed and c.split(".")[-1] not in names]))

    # Informational: does an independent verification artefact exist at all?
    out.append(("V-CHECK", "candidate independent-check artefacts (informational, noisy)",
                sorted({rel(p) for p in root.rglob("*")
                        if p.is_file() and p.suffix in {".py", ".sh", ".java"}
                        and "event" not in p.parts and "target" not in p.parts
                        and re.search(r"expect|assert|verif|oracle|reference|mutation", p.name, re.I)})))
    return out


def parse(path):
    text = Path(path).read_text(errors="replace")
    body = text.split("# Entries", 1)[-1]
    entries, spans = [], [(m.start(), m.group(1), m.group(2), m.group(3)) for m in ENTRY.finditer(body)]
    for i, (start, eid, ts, kind) in enumerate(spans):
        end = spans[i + 1][0] if i + 1 < len(spans) else len(body)
        fields = dict(FIELD.findall(body[start:end]))
        src = fields.get("from", "").strip()
        entries.append({"id": eid, "ts": ts, "kind": kind.upper(),
                        "what": fields.get("what", "").strip(),
                        "why": fields.get("why", "").strip(),
                        "from": src, "fromKind": src.split(":")[0].strip().lower() or "MISSING"})
    preds = re.findall(r"^\s*(P\d)\s+(.+)$", text, re.M)
    closes = re.findall(r"^\s*(T\d+ outcome|first checked result at|which existing file[^:]*|"
                        r"what I would have wanted[^:]*|what I read that[^:]*|predictions that[^:]*):\s*(.*)$",
                        text, re.M)
    return entries, preds, closes


def elapsed(entries):
    stamps = []
    for e in entries:
        try:
            stamps.append(datetime.fromisoformat(e["ts"].replace("Z", "+00:00")))
        except ValueError:
            pass
    return (stamps[-1] - stamps[0]) if len(stamps) > 1 else None


def main():
    argv = sys.argv[1:]
    baseline = None
    if "--baseline" in argv:
        i = argv.index("--baseline")
        baseline = Path(argv[i + 1]).resolve()
        del argv[i:i + 2]
    args = [a for a in argv if not a.startswith("--")]
    journal = args[0]
    root = Path(args[1]).resolve() if len(args) > 1 else None
    entries, preds, closes = parse(journal)
    kinds = Counter(e["kind"] for e in entries)
    froms = Counter(e["fromKind"] for e in entries)
    routed = sum(v for k, v in froms.items() if k in ROUTED)
    unrouted = sum(v for k, v in froms.items() if k in UNROUTED)
    examples = Counter(e["from"].split(":", 1)[1].strip() for e in entries
                       if e["fromKind"] == "example" and ":" in e["from"])
    first_check = next((e["id"] for e in entries if e["kind"] == "CHECK"), None)
    span = elapsed(entries)
    prints = fingerprints(root, baseline) if root else []

    if "--json" in sys.argv:
        print(json.dumps({"entries": len(entries), "kinds": kinds, "from": froms,
                          "routed": routed, "unrouted": unrouted, "examples": examples,
                          "firstCheck": first_check, "elapsed": str(span),
                          "fingerprints": {i: v for i, _, v in prints if v}}, indent=1, default=str))
        return

    print(f"COLD-START SCORESHEET · {journal}")
    print(f"project: {root or '(not supplied)'}")
    print(f"baseline: {baseline or 'NONE - template files are scored as the subject\'s; results inflated'}\n")
    print(f"entries {len(entries)} · elapsed {span or 'n/a'} · first CHECK at {first_check or 'NEVER'}")
    print("kinds   " + "  ".join(f"{k}={v}" for k, v in sorted(kinds.items())))

    print("\nROUTING — the headline number")
    total = max(1, routed + unrouted)
    for k in FROM_KINDS:
        if froms.get(k):
            tag = "routed" if k in ROUTED else "unrouted" if k in UNROUTED else ""
            print(f"  {k:<10} {froms[k]:>3}   {tag}")
    if froms.get("MISSING"):
        print(f"  {'MISSING':<10} {froms['MISSING']:>3}   entries with no from: field")
    print(f"  --> routed {routed}/{total} = {100 * routed // total}%   "
          f"operator interventions: {froms.get('operator', 0)}")

    print("\nIMITATION — which existing files were copied (the v1 failure channel)")
    print("  none recorded" if not examples else
          "\n".join(f"  {n:>3}x  {f}" for f, n in examples.most_common()))

    if prints:
        print("\nTRAP FINGERPRINTS over the finished project")
        for tid, desc, hits in prints:
            if hits is None:
                print(f"  [skip] {desc}")
            elif hits:
                print(f"  [HIT ] {tid:<16} {desc}")
                for h in hits[:6]:
                    print(f"           {h}")
            else:
                print(f"  [ ok ] {tid:<16} {desc}")

    print("\nPREDICTIONS (frozen before T1)")
    print("\n".join(f"  {p} {t}" for p, t in preds) or "  none recorded — acceptance 12 requires these")
    if closes:
        print("\nCLOSE-OUT")
        print("\n".join(f"  {k}: {v}" for k, v in closes))

    print("""
MANUAL — this script cannot determine these; score them from the transcript
  [ ] T1 reached a measured first result unassisted
  [ ] T3 used the correct per-parent mechanism (and via which from:)
  [ ] T4(a) expectations derived independently of the application's own output
  [ ] T4(a) named assertions exist for arithmetic / grouping / count / threshold below-equal-above
  [ ] T4(a) both trigger paths and the empty state are covered
  [ ] T4(b) non-regression checked separately
  [ ] injected errors 1-5: which failed a named assertion, which survived
  [ ] for each survivor: scenario gap (state never created) or check gap (state created, not asserted)
  [ ] T5 established WHICH build of the dependency was used
  [ ] T6 identified the node from the log before editing code
  [ ] journal honesty: 10% of from: fields spot-checked against the transcript
  [ ] journal abandoned or degraded at any point (instrument too heavy)

COMPARE WITH v1 CONTROL (2026-09-19/20 session)
  T-MAIN         HIT   hand-rolled harness written twice
  T-EVENTLOG     HIT   twice, second time on a node whose purpose was audit output
  T-RETURN-TRUE  near  caught by reading the stub, not by the docs
  T-TWOFEEDS     ok    avoided only by reading Mongoose source
  T-NODEBEANS-JAR HIT  empty shell written over a certified class
  operator             2 decisive interventions
  injected errors      2 of 6 survived, both scenario gaps
v2 is better only if the HIT list shortens AND unrouted/operator fall. Faster with the same
routing failures is not an improvement.""")


if __name__ == "__main__":
    main()
