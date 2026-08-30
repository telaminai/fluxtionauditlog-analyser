#!/usr/bin/env python3
"""D-AX1c: every URL in the agreed reference set must still resolve.

This is the ONLY check that sees these URLs. Two recorded gate blind spots meet exactly here:
`mkdocs build --strict` cannot check external links, and SpecLinksResolveTest only walks docs/specs.
Without this, the cascade mechanism fails silently and every generated project keeps claiming to point
at guidance that no longer exists — which is worse than not pointing at all, because it still reads as
authoritative.

Deliberately NOT a unit test: a network fetch in `mvn test` makes the build flake on someone else's
outage. Run it before publishing a bundle, and in the preflight before an experiment round.

    tools/bench/reference-set-bench.py             # agreed entries (what bundles may ship)
    tools/bench/reference-set-bench.py --all       # also proposed and excluded, for review
"""

import argparse
import json
import pathlib
import sys
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
SET_FILE = REPO / "src" / "main" / "resources" / "reference-set.json"
TIMEOUT = 30
AGENT = "fluxtion-auditlog-analyser reference-set-bench"


def check(url):
    """@return (ok, detail). A HEAD is not enough — some hosts answer HEAD differently from GET."""
    request = urllib.request.Request(url, method="GET", headers={"User-Agent": AGENT})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            body = response.read(4096)
            if response.status != 200:
                return False, f"HTTP {response.status}"
            if not body.strip():
                return False, "200 but the body is empty — a page that resolves to nothing is still a dead pointer"
            return True, f"HTTP 200, {len(body)}+ bytes"
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code}"
    except (urllib.error.URLError, OSError) as e:
        return False, f"unreachable: {e}"


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--all", action="store_true",
                        help="check proposed and excluded entries too (they are not shippable either way)")
    args = parser.parse_args()

    data = json.loads(SET_FILE.read_text())
    resources = data["resources"]
    wanted = resources if args.all else [r for r in resources if r["status"] == "agreed"]

    if not wanted:
        print("no AGREED resources in the reference set.")
        print("The set is still awaiting sign-off (D-AX1c), so there is nothing a bundle may ship yet.")
        print("Re-run with --all to check the proposed entries.")
        return 0

    failures = []
    for resource in wanted:
        ok, detail = check(resource["url"])
        print(f"{'PASS' if ok else 'FAIL'}  {resource['id']:<16} {resource['url']}  {detail}")
        if not ok and resource["status"] != "excluded":
            failures.append(resource["id"])

    print()
    if failures:
        print(f"{len(failures)} of {len(wanted)} unreachable: {', '.join(failures)}")
        print("A generated project that references a dead URL reads as authoritative and teaches nothing.")
        return 1
    print(f"all {len(wanted)} resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
