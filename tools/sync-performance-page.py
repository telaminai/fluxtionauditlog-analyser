#!/usr/bin/env python3
"""Copy the performance page into the Fluxtion docs site, rewriting its repo-local links — M50/W9.

`docs/proposals/fluxtion-performance-configuration.md` is authored here, because the evidence behind
every figure lives here (`docs/experience/runs/round-*/NOTES.md`) and this is where the harnesses run.
It is *published* from the Fluxtion repo, as `docs/reference/performance-tuning.md`.

Two copies drift. This session hand-synced them three times in one day and that is exactly how a
figure ends up corrected in one place only — the failure mode the page itself is about. So the copy is
mechanical:

* everything from `## Read this first` down is replaced, leaving the destination's own front matter and
  title intact;
* `../experience/runs/<round>/NOTES.md` becomes an absolute link into this repo, because the runs are
  not published to the Fluxtion site;
* `tools/…` code spans become links into this repo, because the harnesses are not published either —
  an unlinked `tools/bench/land-native.py` on the Fluxtion site names a file the reader cannot find.

Usage::

    tools/sync-performance-page.py ../fluxtion/docs/reference/performance-tuning.md
    tools/sync-performance-page.py --check ../fluxtion/docs/reference/performance-tuning.md
"""
import argparse
import pathlib
import re
import sys

SOURCE = "docs/proposals/fluxtion-performance-configuration.md"
MARKER = "## Read this first"
BLOB = "https://github.com/telaminai/fluxtionauditlog-analyser/blob/main"


def rewrite(body, blob=BLOB):
    """Turn this repo's relative references into absolute ones. Pure — the tests drive this."""
    body = re.sub(r"\]\(\.\./experience/(runs/[^)]+)\)", rf"]({blob}/docs/experience/\1)", body)

    def link_tool(m):
        path = m.group(1)
        return f"[`{path}`]({blob}/{path})"

    # a bare code span naming a tool, not one already inside a link or a fenced command line
    body = re.sub(r"(?<!\[)`(tools/[A-Za-z0-9._/-]+)`(?!\])", link_tool, body)
    return body


def render(source_text, destination_text):
    """The destination's head, then the rewritten body. Raises if either side lacks the marker."""
    if MARKER not in source_text:
        raise ValueError(f"source has no {MARKER!r} marker")
    if MARKER not in destination_text:
        raise ValueError(f"destination has no {MARKER!r} marker — refusing to guess its head")
    head = destination_text[: destination_text.index(MARKER)]
    return head + rewrite(source_text[source_text.index(MARKER):])


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("destination", help="path of performance-tuning.md in the Fluxtion repo")
    ap.add_argument("--source", default=SOURCE)
    ap.add_argument("--check", action="store_true", help="exit 1 if the destination is out of date")
    a = ap.parse_args(argv)

    src = pathlib.Path(a.source)
    dst = pathlib.Path(a.destination)
    if not src.is_file():
        print(f"no source page at {src}", file=sys.stderr)
        return 2
    if not dst.is_file():
        print(f"no destination page at {dst}", file=sys.stderr)
        return 2

    wanted = render(src.read_text(), dst.read_text())
    if dst.read_text() == wanted:
        print(f"{dst} is up to date")
        return 0
    if a.check:
        print(f"{dst} is OUT OF DATE — run without --check to sync")
        return 1
    dst.write_text(wanted)
    print(f"synced {src} -> {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
