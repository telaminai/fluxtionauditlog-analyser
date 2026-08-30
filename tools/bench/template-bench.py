#!/usr/bin/env python3
"""M19.5 conformance: hardened local attacks, plus the optional deployed-playground leg."""

import argparse
import os
import pathlib
import subprocess
import sys


REPO = pathlib.Path(__file__).resolve().parent.parent.parent
FOCUSED = "TemplateCatalogueTest,TemplateArchiveTest,TemplateUiContractTest"
LIVE_MAIN = "telamin.fluxtion.audit.analyser.analyser.template.TemplateLiveBench"


def run(command):
    print("+", " ".join(command), flush=True)
    return subprocess.run(command, cwd=REPO).returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true",
                        help="also fetch the deployed catalogue and ?template= scaffold endpoint")
    args = parser.parse_args()

    code = run(["mvn", "-q", f"-Dtest={FOCUSED}", "test"])
    if code or not args.live:
        return code

    code = run(["mvn", "-q", "-DskipTests", "test-compile"])
    if code:
        return code
    classpath = os.pathsep.join([str(REPO / "target" / "test-classes"),
                                str(REPO / "target" / "classes")])
    return run(["java", "-cp", classpath, LIVE_MAIN])


if __name__ == "__main__":
    sys.exit(main())
