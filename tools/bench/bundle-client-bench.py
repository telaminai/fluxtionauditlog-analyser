#!/usr/bin/env python3
"""Drive a generated M19 bundle through a fresh analyser and its packaged MCP bridge.

This is the live consumer half of ``bundle-bench.py``. The producer must already have run the bundle
and exported its audit YAML. This script opens the bundle project first (a project switch is a session
boundary), then its YAML + declared GraphML, and proves the same state is visible through MCP.
"""
import argparse
import importlib.util
import json
import pathlib
import shutil
import signal
import subprocess
import sys
import tempfile
import time

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
HERE = pathlib.Path(__file__).resolve().parent
RESULTS = []


def load_loop_bench():
    spec = importlib.util.spec_from_file_location("loop_bench", HERE / "loop-bench.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def step(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  — {detail}" if detail else ""), flush=True)
    return ok


def resolve(project, supplied):
    path = pathlib.Path(supplied)
    return path if path.is_absolute() else project / path


def profile_provenance(profile):
    for raw in profile.read_text().splitlines():
        line = raw.strip()
        if line.startswith("skills.provenance="):
            return line.split("=", 1)[1]
    return None


def mcp_context(response):
    for item in response.get("result", {}).get("content", []):
        if item.get("type") == "text":
            try:
                return json.loads(item.get("text", "")).get("context", {})
            except (TypeError, json.JSONDecodeError):
                pass
    return {}


def finish(processes, work, keep_work):
    for process in processes:
        if process.poll() is None:
            process.send_signal(signal.SIGINT)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
    failed = [name for name, ok, _ in RESULTS if not ok]
    print()
    print(f"{len(RESULTS) - len(failed)} passed, {len(failed)} failed"
          + (": " + "; ".join(failed) if failed else ""))
    if keep_work:
        print(f"work directory retained: {work}")
    else:
        shutil.rmtree(work, ignore_errors=True)
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", help="unzipped generated bundle root")
    parser.add_argument("--log", required=True, help="exported YAML, absolute or relative to the bundle")
    parser.add_argument("--graphml", required=True, help="declared GraphML, absolute or relative to the bundle")
    parser.add_argument("--expected-records", required=True, type=int, help="record count produced by the run")
    parser.add_argument("--jar", help="packaged analyser jar; default: newest non-original jar under target/")
    parser.add_argument("--keep-work", action="store_true", help="retain the isolated analyser home and stdout")
    args = parser.parse_args()

    loop = load_loop_bench()
    project = pathlib.Path(args.project).resolve()
    profile = project / ".analyser" / "project.fluxtion-settings"
    log = resolve(project, args.log).resolve()
    graphml = resolve(project, args.graphml).resolve()
    provenance = profile_provenance(profile) if profile.is_file() else None
    if args.jar:
        jars = [pathlib.Path(args.jar).resolve()]
    else:
        jars = [p for p in sorted((REPO / "target").glob("fluxtion-auditlog-analyser-*.jar"))
                if "original" not in p.name]

    work = pathlib.Path(tempfile.mkdtemp(prefix="bundle-client-bench-"))
    home = work / "home"
    home.mkdir()
    processes = []
    output = (work / "analyser.out").open("w")
    try:
        if not step("packaged analyser jar exists", bool(jars) and jars[-1].is_file(), str(jars[-1]) if jars else "missing"):
            return finish(processes, work, args.keep_work)
        required = [("bundle profile exists", profile), ("exported YAML exists", log),
                    ("declared GraphML exists", graphml)]
        for name, path in required:
            if not step(name, path.is_file(), str(path)):
                return finish(processes, work, args.keep_work)
        if not step("profile declares non-none skill provenance", bool(provenance) and provenance != "none",
                    repr(provenance)):
            return finish(processes, work, args.keep_work)

        app = subprocess.Popen(["java", f"-Duser.home={home}", "-jar", str(jars[-1]), "--rest"],
                               stdout=output, stderr=subprocess.STDOUT)
        processes.append(app)
        analyser = loop.Analyser(home / ".fluxtion-analyser" / "rest-endpoint")
        if not step("fresh analyser answers over REST", analyser.wait(90), str(analyser.endpoint_file)):
            return finish(processes, work, args.keep_work)

        analyser.act("open", {"close": "all"})
        opened = analyser.act("open", {"project": str(project)})
        step("bundle project opens in its own call", opened.get("ok", False), opened.get("error", ""))
        context = analyser.act("context").get("context", {})
        project_context = context.get("project", {})
        step("bundle project is active", project_context.get("active") is True,
             project_context.get("name", "not active"))
        runbooks = context.get("runbooks", [])
        step("both bundle runbooks are declared and resolve",
             len(runbooks) == 2 and all(r.get("description") and r.get("exists") is True for r in runbooks),
             f"{len(runbooks)} runbook(s)")
        step("context reports the profile's skill provenance",
             context.get("skills", {}).get("provenance") == provenance,
             repr(context.get("skills", {}).get("provenance")))

        opened = analyser.act("open", {"log": str(log), "graphml": str(graphml),
                                        "provenance": project.name})
        step("YAML and GraphML open after the project", opened.get("ok", False), opened.get("error", ""))
        for _ in range(120):
            context = analyser.act("context").get("context", {})
            if (context.get("log", {}).get("records") == args.expected_records
                    and context.get("graphPairing", {}).get("applies") is True):
                break
            time.sleep(0.5)
        step("the measured audit records loaded",
             context.get("log", {}).get("records") == args.expected_records,
             f"records={context.get('log', {}).get('records')}")
        pairing = context.get("graphPairing", {})
        step("the declared GraphML fits the log", pairing.get("applies") is True,
             pairing.get("verdict", "no pairing verdict"))
        coverage = analyser.act("coverage")
        cov = coverage.get("coverage", {})
        step("coverage is complete", coverage.get("ok") is True and cov.get("ratio") == 1.0
             and cov.get("uncovered") == 0 and cov.get("neverLogged") == [],
             f"ratio={cov.get('ratio')}, uncovered={cov.get('uncovered')}")

        bridge = loop.McpBridge(["java", f"-Duser.home={home}", "-jar", str(jars[-1]), "--mcp"])
        processes.append(bridge.process)
        discovered = bridge.request("server/discover", "bundle-discover", bridge.modern_params())
        step("packaged MCP bridge answers modern discovery", "result" in discovered, "server/discover")
        listed = bridge.request("tools/list", "bundle-tools", bridge.modern_params())
        advertised = listed.get("result", {}).get("tools", [])
        step("packaged MCP bridge advertises analyser_context",
             any(t.get("name") == "analyser_context" for t in advertised if isinstance(t, dict)),
             f"{len(advertised)} tool(s)")
        called = bridge.request("tools/call", "bundle-context", bridge.modern_params({
            "name": "analyser_context", "arguments": {}
        }))
        mcp = mcp_context(called)
        step("MCP analyser_context reaches this analyser",
             "result" in called and not called["result"].get("isError", False) and bool(mcp),
             "tools/call")
        step("MCP sees the same bundle project, records and provenance",
             mcp.get("project", {}).get("name") == project.name
             and mcp.get("log", {}).get("records") == args.expected_records
             and mcp.get("skills", {}).get("provenance") == provenance,
             f"project={mcp.get('project', {}).get('name')}, records={mcp.get('log', {}).get('records')}")
        step("MCP sees the same graph pairing",
             mcp.get("graphPairing", {}).get("applies") is True,
             mcp.get("graphPairing", {}).get("verdict", "no pairing verdict"))
        return finish(processes, work, args.keep_work)
    except KeyboardInterrupt:
        step("bundle-client bench ran to completion", False, "interrupted")
        return finish(processes, work, args.keep_work)
    except Exception as exc:
        step("bundle-client bench ran to completion", False, f"{type(exc).__name__}: {exc}")
        return finish(processes, work, args.keep_work)
    finally:
        output.close()


if __name__ == "__main__":
    sys.exit(main())
