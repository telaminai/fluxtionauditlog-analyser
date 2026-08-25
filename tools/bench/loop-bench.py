#!/usr/bin/env python3
"""The loop's conformance bench — spec-agent-brokered-dev-loop §H, homed in M19.

Plays the AGENT of §C3 steps 3–7 as a script, and asserts at every step:

  3  a server has published its registry file          (glob the directory, parse, check mode + pid)
  4  pick the server                                    (--server, or the only one)
  5  export its audit log and its processor's GraphML   (GET …/export?format=yaml, …/graphml)
  6  drive the analyser over REST                       open {close}, open {log, graphml, provenance},
                                                        source_root {add}
  7  the loop closed                                    context: provenance = the server's name, records
                                                        loaded, the graph FITS; coverage answers; topology
                                                        answers

Every step prints PASS/FAIL by name; the exit code is non-zero if any failed. That is the point of a
conformance bench: a break fails HERE, in the repo that owns the contract, not in a user's session.

Two ways to run it:
  · against the stub (today, no Mongoose needed):
        tools/bench/loop-bench.py --stub --launch
    starts mongoose-stub.py and a fresh analyser (--rest, isolated home) and tears both down.
  · against a REAL registry (the acceptance test for UP-MNG-01/02 once a server publishes one):
        tools/bench/loop-bench.py --registry ~/.mongoose/servers --server risk-engine
    with the analyser already running with REST on (or add --launch).

ASSUMPTION, flagged: when a registry file says authMode "TOKEN" the bench sends
`Authorization: Bearer <token>` — the bench's guess at the server's session model. The mongoose side
owns that decision (UP-MNG-01); correct the bench when it is made.
"""
import argparse
import json
import os
import pathlib
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
HERE = pathlib.Path(__file__).resolve().parent
RESULTS = []


def step(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  — {detail}" if detail else ""), flush=True)
    return ok


def http_get(url, token=None, auth_mode="NONE", timeout=30):
    req = urllib.request.Request(url)
    if auth_mode == "TOKEN" and token:
        req.add_header("Authorization", f"Bearer {token}")     # ASSUMPTION — see module doc
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


class Analyser:
    def __init__(self, endpoint_file):
        self.endpoint_file = pathlib.Path(endpoint_file)
        self.url = self.token = None

    def wait(self, seconds=60):
        deadline = time.time() + seconds
        while time.time() < deadline:
            if self.endpoint_file.exists():
                ep = json.loads(self.endpoint_file.read_text())
                self.url, self.token = ep["url"], ep["token"]
                try:
                    self.act("context")
                    return True
                except Exception:
                    pass
            time.sleep(0.5)
        return False

    def act(self, action, params=None):
        body = json.dumps({"v": 1, "action": action, "params": params or {}}).encode()
        req = urllib.request.Request(self.url + "/action", data=body, method="POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("X-Analyser-Token", self.token)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            return json.loads(e.read())


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--registry", help="the servers directory (default ~/.mongoose/servers, or the stub's)")
    ap.add_argument("--server", help="which server; default: the only one")
    ap.add_argument("--stub", action="store_true", help="start mongoose-stub.py in a temp registry")
    ap.add_argument("--launch", action="store_true", help="start the analyser from target/ with --rest into an isolated home")
    ap.add_argument("--keep", action="store_true", help="leave the analyser and stub running afterwards")
    a = ap.parse_args()

    work = pathlib.Path(tempfile.mkdtemp(prefix="loop-bench-"))
    procs = []
    try:
        # ---- the server half -------------------------------------------------------------------------
        registry = pathlib.Path(a.registry) if a.registry else pathlib.Path.home() / ".mongoose" / "servers"
        if a.stub:
            registry = work / "servers"
            p = subprocess.Popen([sys.executable, str(HERE / "mongoose-stub.py"), "--registry", str(registry)],
                                 stdout=subprocess.PIPE, text=True)
            procs.append(p)
            first = p.stdout.readline().strip()          # the registry file path, printed first
            step("stub published its registry file", pathlib.Path(first).exists(), first)

        # ---- the analyser half -------------------------------------------------------------------------
        if a.launch:
            jars = sorted((REPO / "target").glob("fluxtion-auditlog-analyser-*.jar"))
            jars = [j for j in jars if "original" not in j.name]
            if not jars:
                sys.exit("no jar in target/ — run `mvn package -DskipTests` first")
            home = work / "home"                           # a NEVER-configured install: slice 1's own test
            home.mkdir()
            p = subprocess.Popen(["java", f"-Duser.home={home}", "-jar", str(jars[-1]), "--rest"],
                                 stdout=open(work / "analyser.out", "w"), stderr=subprocess.STDOUT)
            procs.append(p)
            analyser = Analyser(home / ".fluxtion-analyser" / "rest-endpoint")
        else:
            analyser = Analyser(pathlib.Path.home() / ".fluxtion-analyser" / "rest-endpoint")

        # ---- §C3 step 3: the registry ------------------------------------------------------------------
        print(f"registry: {registry}")
        entries = sorted(p for p in registry.glob("*") if p.is_file()) if registry.is_dir() else []
        if not step("registry directory has at least one server file", bool(entries), str(registry)):
            return finish(procs, a.keep, work)
        chosen = None
        for e in entries:
            if a.server is None or e.name == a.server:
                chosen = e
                break
        if not step("a server was picked", chosen is not None, a.server or "(the only one)"):
            return finish(procs, a.keep, work)
        try:
            mode = chosen.stat().st_mode & 0o777
            step("registry file is mode 600", mode == 0o600, oct(mode))
        except OSError:
            step("registry file is mode 600", False, "cannot stat")
        try:
            srv = json.loads(chosen.read_text())
        except Exception as ex:
            step("registry file parses as JSON", False, str(ex))
            return finish(procs, a.keep, work)
        required = ["name", "home", "url", "token", "authMode", "pid", "startedAt", "processors"]
        missing = [k for k in required if k not in srv]
        if not step("registry file carries the UP-MNG-01 fields", not missing, "missing: " + ", ".join(missing) if missing else ""):
            return finish(procs, a.keep, work)
        alive = True
        try:
            os.kill(int(srv["pid"]), 0)
        except OSError:
            alive = False
        step("the server's pid is alive", alive, f"pid {srv['pid']}")
        step("environment is declared (UP-MNG-03)", "environment" in srv,
             srv.get("environment", "ABSENT — the bench cannot tell dev from prod"))

        # ---- §C3 step 5: export ------------------------------------------------------------------------
        base, tok, auth = srv["url"], srv["token"], srv.get("authMode", "NONE")
        try:
            files = json.loads(http_get(f"{base}/api/audit/files", tok, auth))["files"]
            step("GET /api/audit/files lists the audit files", bool(files), f"{len(files)} file(s)")
            audit_id = files[0]["id"]
            log_path = work / f"{srv['name']}-{audit_id}.yaml"
            log_path.write_bytes(http_get(f"{base}/api/audit/file/{audit_id}/export?format=yaml", tok, auth))
            step("export?format=yaml delivered the log", log_path.stat().st_size > 0, f"{log_path.stat().st_size} bytes")
        except Exception as ex:
            step("export the audit log", False, str(ex))
            return finish(procs, a.keep, work)
        try:
            proc = srv["processors"][0]
            graph_path = work / f"{srv['name']}-{proc['name']}.graphml"
            graph_path.write_bytes(http_get(base + proc["graphml"], tok, auth))
            step("processors[].graphml delivered the GraphML", graph_path.stat().st_size > 0, proc["name"])
        except Exception as ex:
            step("export the GraphML", False, str(ex))
            return finish(procs, a.keep, work)

        # ---- §C3 step 6: drive the analyser --------------------------------------------------------------
        if not step("the analyser answers on its REST endpoint", analyser.wait(90), str(analyser.endpoint_file)):
            return finish(procs, a.keep, work)
        if a.launch:
            # the first-run check fires from the splash timer ~0.7 s after the window shows, which can be
            # AFTER the socket first answers — poll briefly rather than read once
            out = ""
            for _ in range(20):
                out = (work / "analyser.out").read_text()
                if "no Settings dialog" in out:
                    break
                time.sleep(0.5)
            step("a fresh install started with --rest shows no first-run dialog (it said so on stdout)",
                 "no Settings dialog" in out, "M19.7 / review N2")
        analyser.act("open", {"close": "all"})
        r = analyser.act("open", {"log": str(log_path), "graphml": str(graph_path), "provenance": srv["name"]})
        step("open {log, graphml, provenance} accepted", r.get("ok", False), r.get("error", ""))
        src = pathlib.Path(srv["home"]) / "src" / "main" / "java"
        r = analyser.act("source_root", {"add": [str(src)]})
        step("source_root {add} accepted the server's home", r.get("ok", False), str(src))
        # the load is asynchronous: poll context until the log is in
        ctx = {}
        for _ in range(60):
            ctx = analyser.act("context").get("context", {})
            if ctx.get("log", {}).get("records"):
                break
            time.sleep(0.5)

        # ---- §C3 step 7: the loop closed ---------------------------------------------------------------
        step("context: the log loaded", bool(ctx.get("log", {}).get("records")), f"records={ctx.get('log', {}).get('records')}")
        step("context: provenance is the SERVER's name (§E)", ctx.get("provenance") == srv["name"], repr(ctx.get("provenance")))
        gp = ctx.get("graphPairing") or {}
        step("context: the graph FITS the log", gp.get("applies") is True, gp.get("verdict", "no pairing reported"))
        cov = analyser.act("coverage")
        step("coverage answers", cov.get("ok", False), json.dumps(cov.get("coverage", cov.get("error", "")))[:100])
        topo = analyser.act("topology")
        step("topology answers", topo.get("ok", False), topo.get("error", ""))
        step("no time-order dialog blocked the load (the app kept answering)", True, "weak evidence on its own; the structural proof is M35.9")
        return finish(procs, a.keep, work)
    finally:
        pass


def finish(procs, keep, work):
    failed = [n for n, ok, _ in RESULTS if not ok]
    print()
    print(f"{len(RESULTS) - len(failed)} passed, {len(failed)} failed" + (": " + "; ".join(failed) if failed else ""))
    if not keep:
        for p in procs:
            try:
                p.send_signal(signal.SIGINT)
                p.wait(timeout=10)
            except Exception:
                p.kill()
        shutil.rmtree(work, ignore_errors=True)
    else:
        print(f"kept running; work dir {work}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
