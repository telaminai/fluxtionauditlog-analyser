#!/usr/bin/env python3
"""A stand-in Mongoose admin surface — the loop's server half, reduced to the contract.

This is NOT a Mongoose server. It does exactly what the analyser's upstream asks ask of the real one
(docs/proposals/upstream-asks.md UP-MNG-01 and the export half of UP-MNG-02), and nothing more:

  1. publishes a registry file   <registry>/<name>           (mode 600, the UP-MNG-01 shape)
  2. serves                      GET /api/audit/files
                                 GET /api/audit/file/{id}/export?format=yaml
                                 GET /api/processors/{group}/{name}/graphml
     from the in-tree demo set (examples/fixture-generator output, rule-1 clean: com.acme.demo).

So it is the contract in executable form. The mongoose-side work is "make the real server pass what
this passes" — and loop-bench.py, pointed at a real ~/.mongoose/servers/, is that acceptance test.

ASSUMPTIONS the real server has not decided, flagged rather than baked in:
  · authMode: this stub publishes "NONE" and checks no header. The bench sends
    `Authorization: Bearer <token>` when a registry file says authMode "TOKEN" — that header shape is
    the bench's GUESS at svc-admin-web's session model, and the mongoose side may correct it.
  · the audit-files listing shape ({id, name, sink:{type, location}}) is UP-MNG-04's proposal.

Usage:
  tools/bench/mongoose-stub.py --registry /tmp/bench/servers --name demo-quote [--port 0]
Runs until killed; prints the registry file path on the first line so a caller can wait for it.
"""
import argparse
import http.server
import json
import os
import pathlib
import socket
import sys
import threading
import time
import urllib.parse

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
FIXTURES = REPO / "src/test/resources/topology"
HOME_DIR = REPO / "examples/fixture-generator"           # the "server home" — its source roots live here
GROUP, PROCESSOR = "main", "DemoQuoteProcessor"
AUDIT_FILES = {
    "audit-1": FIXTURES / "demo-quote-audit.yaml",         # the walkthrough: 10 records, 5/5 nodes
    "audit-traced": FIXTURES / "demo-quote-audit-traced.yaml",
    "audit-series": FIXTURES / "demo-quote-series.yaml",   # enough data to chart
}
GRAPHML = FIXTURES / "demo-quote-processor.graphml"


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):   # quiet — the bench prints what matters
        pass

    def _send(self, code, body, ctype="application/json"):
        data = body if isinstance(body, bytes) else body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        parts = [p for p in u.path.split("/") if p]
        q = urllib.parse.parse_qs(u.query)
        if parts == ["api", "server"]:
            return self._send(200, json.dumps({"pid": os.getpid(), "runtime": "stub", "startedAt": STARTED}))
        if parts == ["api", "audit", "files"]:
            files = [{"id": k, "name": v.name, "sink": {"type": "file", "location": str(v)}}
                     for k, v in AUDIT_FILES.items()]
            return self._send(200, json.dumps({"files": files}))
        if len(parts) == 5 and parts[:3] == ["api", "audit", "file"] and parts[4] == "export":
            f = AUDIT_FILES.get(parts[3])
            if f is None:
                return self._send(404, json.dumps({"error": "no such audit file: " + parts[3]}))
            if q.get("format", ["yaml"])[0] != "yaml":
                return self._send(400, json.dumps({"error": "only format=yaml is served here"}))
            return self._send(200, f.read_bytes(), "application/yaml")
        if len(parts) == 5 and parts[:2] == ["api", "processors"] and parts[4] == "graphml":
            if parts[2] == GROUP and parts[3] == PROCESSOR:
                return self._send(200, GRAPHML.read_bytes(), "application/xml")
            return self._send(404, json.dumps({"error": "no such processor"}))
        self._send(404, json.dumps({"error": "not found: " + u.path}))


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--registry", required=True, help="directory that plays ~/.mongoose/servers")
    ap.add_argument("--name", default="demo-quote")
    ap.add_argument("--port", type=int, default=0, help="0 = pick a free port")
    a = ap.parse_args()

    global STARTED
    STARTED = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    port = a.port or free_port()
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    registry = pathlib.Path(a.registry)
    registry.mkdir(parents=True, exist_ok=True)
    entry = registry / a.name
    record = {
        "name": a.name,
        "home": str(HOME_DIR),
        "url": f"http://127.0.0.1:{port}",
        "token": "stub-no-auth",
        "authMode": "NONE",
        "environment": "dev",
        "pid": os.getpid(),
        "startedAt": STARTED,
        "processors": [{"group": GROUP, "name": PROCESSOR,
                        "className": f"com.acme.demo.generated.{PROCESSOR}",
                        "graphml": f"/api/processors/{GROUP}/{PROCESSOR}/graphml"}],
    }
    # mode 600 by construction — the same posture as ~/.fluxtion-analyser/rest-endpoint (UP-MNG-01)
    fd = os.open(entry, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(record, f, indent=1)
    os.chmod(entry, 0o600)
    print(entry, flush=True)
    print(f"stub mongoose '{a.name}' on {record['url']} — serving the demo set; Ctrl-C to stop", flush=True)
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass
    finally:
        entry.unlink(missing_ok=True)      # a clean shutdown removes its file; a crash leaves a dead pid
        server.shutdown()


if __name__ == "__main__":
    main()
