#!/usr/bin/env python3
"""Drive the real Swing start workspace and file-drop regression cases.

Requires a display. Each Java case uses a temporary home and DEMO-only inputs;
the drop cases invoke the frame's TransferHandler with a real file list.
"""

from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
CASES = {
    "StartWorkspaceFrameTest": {
        "startPageReplacesTheWholeInvestigationArea",
        "recentProjectChoiceOpensItsWorkspaceWithoutOpeningALog",
        "sampleChoiceOpensARealDemoProject",
        "droppingSpringDesignThenGraphAndLogOpensTheirRealViews",
        "droppingVerifiedExperimentOpensItsOwnProjectGraphAndLog",
    },
    "ProjectLandingTest": {
        "fullStartPageRoutesDistinctChoicesAndRecentProjectToItsWorkspace",
    },
    "FileDropRoutingTest": {
        "graphmlByExtensionCaseInsensitive",
        "bundleAndSpringDesignDoNotFallThroughToTheLogReader",
    },
}


def main() -> int:
    classes = ",".join(CASES)
    command = ["mvn", "-o", "-q", "test", f"-Dtest={classes}",
               "-Djava.awt.headless=false", "-DargLine=-Djava.awt.headless=false",
               "-Dsurefire.failIfNoSpecifiedTests=false"]
    completed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if completed.returncode:
        print(completed.stdout[-6000:], file=sys.stderr)
        print(completed.stderr[-6000:], file=sys.stderr)
        return completed.returncode

    failed = False
    for cls, expected in CASES.items():
        report = ROOT / "target" / "surefire-reports" / f"TEST-telamin.fluxtion.audit.analyser.analyser.ui.{cls}.xml"
        if not report.is_file():
            print(f"FAIL {cls}: Surefire report missing")
            failed = True
            continue
        suite = ET.parse(report).getroot()
        actual = {case.attrib["name"] for case in suite.findall("testcase")}
        missing = expected - actual
        bad = [case.attrib["name"] for case in suite.findall("testcase")
               if any(case.find(tag) is not None for tag in ("failure", "error", "skipped"))]
        if missing or bad:
            print(f"FAIL {cls}: missing={sorted(missing)}, failed-or-skipped={bad}")
            failed = True
        else:
            print(f"PASS {cls}: {len(actual)} cases, including {len(expected)} required witnesses")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
