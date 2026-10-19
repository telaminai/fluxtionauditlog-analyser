#!/usr/bin/env python3
"""M74 P3 — the permission-prompt probes P3-0…P3-6 (spec-claude-code-provider.md §8.3), reproducible.

  python3 p3.py --list
  python3 p3.py --workdir DIR --jar JAR --only p3_lines_iso                       (isolated: no login, no spend)
  python3 p3.py --workdir DIR --jar JAR --i-accept-spend --only p3_0_real,...      (real login, on the person's plan)
  python3 p3.py --sanitise RAW.jsonl OUT.jsonl --workdir DIR [--cwd SUBDIR]

THE REAL BRIDGE. Unlike P0b (a Python stub), the hosted MCP server here is the analyser's own bridge from the built jar
(`java -jar JAR --mcp --endpoint-file F`), behind a tee that records every JSON-RPC line both ways, so `tools/call`'s
exact `arguments` and `_meta` are seen as the bridge received them. The bridge forwards to a fake analyser REST
endpoint in this harness (the endpoint file names it and this process's pid), which records each `/action` it was
sent and answers ok — the analyser's own admission and grants are not under test here, only the CLI and the bridge.

The P3 line is §4.3's: the default posture with `--permission-mode default --permission-prompts host
--permission-prompt-tool stdio` and NO `--disallowedTools`; the full posture with the chosen mode, the same two prompt
flags, `--disallowedTools mcp__fluxtion-analyser` (plus the destructive verbs under auto and plan). Never an MCP
permission tool (the model could call it, P0b-3). OD-74.18 decides the method:

* `*_iso` experiments run under an isolated CLAUDE_CONFIG_DIR: not logged in, so `initialize`, `mcp_status` and a
  turn's `system/init` cost nothing and see nothing of the person's.
* `*_real` experiments use the real login on the person's Claude plan, ALWAYS with `--strict-mcp-config` (the harness's
  addition on a full line; already on the default line), `--max-budget-usd` per process as a runaway guard and a ledger
  capped by --cap. Their raw transcripts are never committed; only sanitised fixtures and the report's observations are.

Never passes bypassPermissions, dontAsk, --dangerously-skip-permissions or an MCP permission-prompt tool.
"""
import argparse, glob, http.server, importlib.util, json, os, queue, shutil, subprocess, sys, threading, time, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("p0probe", os.path.join(HERE, "..", "m74-p0", "probe.py"))
P0 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(P0)

HOSTED = "fluxtion-analyser-hosted"
PRE_ALLOWED = ["read", "aggregate", "series", "context", "coverage", "source", "filter", "goto", "flag", "graph",
               "topology", "spotlight", "propose"]                       # McpTools.HOSTED_PRE_ALLOWED
DESTRUCTIVE = ["open", "source_root", "screenshot", "report", "walk", "import"]
NOTE = ("You are running inside the Fluxtion Audit Log Analyser, which started you. Call the analyser_context tool "
        "first: it says what is open.")
MODES = ["default", "acceptEdits", "auto", "plan"]
P3_PROMPTS = ["--permission-prompts", "host", "--permission-prompt-tool", "stdio"]

TEE = r'''#!/usr/bin/env python3
import json, subprocess, sys, threading, time
log = open(sys.argv[1], "a", buffering=1)
child = subprocess.Popen(sys.argv[2:], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
t0 = time.time()
def rec(d, l):
    log.write(json.dumps({"dir": d, "t": round(time.time() - t0, 2), "line": l.rstrip("\n")}) + "\n")
def up():
    for l in child.stdout:
        rec("S>C", l); sys.stdout.write(l); sys.stdout.flush()
threading.Thread(target=up, daemon=True).start()
for l in sys.stdin:
    rec("C>S", l); child.stdin.write(l); child.stdin.flush()
child.stdin.close(); child.wait()
'''


def tool(v):
    return f"mcp__{HOSTED}__analyser_{v}"


class FakeAnalyser:
    """The REST endpoint the real bridge forwards to: records each /action (the hosted header's presence, the body)."""

    def __init__(self, W):
        self.calls, self.hosted = [], "hosted-" + uuid.uuid4().hex
        outer = self

        class H(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def do_POST(self):
                n = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(n).decode()
                outer.calls.append({"path": self.path, "hostedHeader": self.headers.get("X-Analyser-Hosted") == outer.hosted,
                                    "sharedToken": self.headers.get("X-Analyser-Token") is not None,
                                    "toolUseHeader": {k: v for k, v in self.headers.items() if "tool" in k.lower()},
                                    "body": json.loads(body) if body else None, "t": time.time()})
                try:
                    action = json.loads(body).get("action")
                except ValueError:
                    action = None
                out = json.dumps({"ok": True, "action": action, "result": {"stub": "fake analyser", "action": action}})
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(out.encode())

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"

    def endpoint_file(self, path, nonce):
        json.dump({"url": self.url, "nonce": nonce, "hosted": self.hosted, "pid": os.getpid(),
                   "startedAt": "2026-10-17T00:00:00Z"}, open(path, "w"))
        os.chmod(path, 0o600)

    def actions(self, since=0.0):
        return [{"action": (c["body"] or {}).get("action"), "params": (c["body"] or {}).get("params"),
                 "hostedHeader": c["hostedHeader"], "sharedToken": c["sharedToken"], "toolUseHeader": c["toolUseHeader"]}
                for c in self.calls if c["t"] >= since]


class Proc(P0.Proc):
    """P0's Proc with an absolute executable and a caller-built environment (as PB's)."""

    def __init__(self, ctx, name, exe, args, cwd, env):
        self.probe, self.name = ctx, name
        self.raw = open(os.path.join(ctx.W, "raw", name + ".jsonl"), "w")
        self.err = open(os.path.join(ctx.W, "raw", name + ".err"), "w")
        self.p = subprocess.Popen([exe] + args, cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=self.err, text=True, bufsize=1, start_new_session=True)
        self.q, self.lines, self.cost0, self.cost = queue.Queue(), [], None, 0.0
        self.t0 = time.time()
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for l in self.p.stdout:
            self.raw.write(l); self.raw.flush()
            try:
                d = json.loads(l)
            except ValueError:
                d = {"_nonjson": l.rstrip()}
            d["_t"] = round(time.time() - self.t0, 2)
            self.lines.append(d)
            if d.get("type") == "result":
                c = d.get("total_cost_usd") or 0.0
                self.probe.ledger.add(self.name, max(0.0, c - self.cost)); self.cost = c
            self.q.put(d)
        self.q.put(None)

    def answer(self, request_id, response):
        self.send({"type": "control_response", "response": {"subtype": "success", "request_id": request_id,
                                                              "response": response}})

    def kinds(self, after=0.0):
        """Every line's kind after time `after`, compressed: what the CLI said while we waited."""
        out = []
        for d in self.lines:
            if d.get("_t", 0) < after:
                continue
            k = d.get("type", "?") + ("/" + d["subtype"] if d.get("subtype") else "")
            if d.get("type") == "stream_event":
                k += "/" + d.get("event", {}).get("type", "?")
            if d.get("type") == "command_lifecycle":
                k += "/" + str(d.get("state"))
            if not out or out[-1][0] != k:
                out.append([k, 1, d.get("_t")])
            else:
                out[-1][1] += 1
        return out


class Ctx:
    def __init__(self, a):
        self.a = a
        self.W = os.path.realpath(a.workdir)
        for d in ["raw", "markers", "cfg", "proj", "outside", "full/.claude"]:
            os.makedirs(f"{self.W}/{d}", exist_ok=True)
        self.ledger = P0.Ledger(os.path.join(self.W, "ledger.json"), a.cap)
        self.results = {}
        self.claude = os.path.realpath(shutil.which("claude") or os.path.expanduser("~/.local/bin/claude"))
        self.java = shutil.which("java")
        self.jar = os.path.realpath(a.jar) if a.jar else None
        open(f"{self.W}/tee.py", "w").write(TEE)
        open(f"{self.W}/proj/inside.txt", "w").write("INSIDE-CANARY-17\n")
        open(f"{self.W}/proj/notes.md", "w").write("# notes\nline one\n")
        open(f"{self.W}/outside/harmless.txt", "w").write("OUTSIDE-CANARY-42\n")
        self.fake = FakeAnalyser(self.W)

    def record(self, key, **obs):
        self.results[key] = obs
        s = json.dumps({"experiment": key, **obs}, default=str).replace(self.W, "<W>")
        print(s.replace(os.path.expanduser("~"), "<HOME>")[:8000], flush=True)

    def bridge(self, name):
        """The real hosted bridge, from the jar, behind the tee; a fresh nonce and endpoint file per process."""
        nonce = uuid.uuid4().hex[:16]
        ep = f"{self.W}/endpoint-{name}.json"
        self.fake.endpoint_file(ep, nonce)
        cfg = {"mcpServers": {HOSTED: {"command": sys.executable,
                                       "args": [f"{self.W}/tee.py", f"{self.W}/raw/{name}.bridge.jsonl", self.java,
                                                "-jar", self.jar, "--mcp", "--endpoint-file", ep]}}}
        return json.dumps(cfg), nonce

    def default_args(self, name, sid=None):
        """§4.3's default posture, P3: no --disallowedTools (the default posture hides none), the P3 triple."""
        cfg, nonce = self.bridge(name)
        return ["-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                "--include-partial-messages", "--replay-user-messages", "--session-id", sid or str(uuid.uuid4()),
                "--model", self.a.model, "--tools", "Read,Grep,Glob", "--restricted", "--add-dir", f"{self.W}/proj",
                "--strict-mcp-config", "--mcp-config", cfg,
                "--allowedTools", ",".join(tool(v) for v in PRE_ALLOWED),
                "--permission-mode", "default"] + P3_PROMPTS + [
                "--max-budget-usd", str(self.a.budget), "--append-system-prompt", NOTE], nonce

    def full_args(self, name, mode, model=None):
        """§4.3's full posture, P3: nothing confines; the destructive verbs hidden only under auto and plan. The
        harness adds --strict-mcp-config (OD-74.18), never the analyser."""
        assert mode in MODES
        cfg, nonce = self.bridge(name)
        hidden = ["mcp__fluxtion-analyser"] + ([tool(v) for v in DESTRUCTIVE] if mode in ("auto", "plan") else [])
        return ["-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                "--include-partial-messages", "--replay-user-messages", "--session-id", str(uuid.uuid4()),
                "--model", model or self.a.model, "--mcp-config", cfg,
                "--allowedTools", ",".join(tool(v) for v in PRE_ALLOWED),
                "--disallowedTools", ",".join(hidden),
                "--permission-mode", mode] + P3_PROMPTS + [
                "--max-budget-usd", str(self.a.budget), "--append-system-prompt", NOTE], nonce

    def spawn(self, name, args, cwd, iso):
        env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}   # never nest inside a host session
        env.pop("ANTHROPIC_API_KEY", None)
        if iso:
            env["CLAUDE_CONFIG_DIR"] = f"{self.W}/cfg"
        else:
            if not self.a.i_accept_spend:
                raise SystemExit("refusing: a real-login run uses the person's plan; pass --i-accept-spend")
            if self.ledger.total() + self.a.budget > self.a.cap:
                raise SystemExit(f"STOP: ledger {self.ledger.total():.4f} + {self.a.budget} would exceed cap {self.a.cap}")
            args = args if "--strict-mcp-config" in args else args + ["--strict-mcp-config"]   # OD-74.18, every run
        joined = " ".join(args)
        assert "bypassPermissions" not in joined and "dangerously" not in joined and "dontAsk" not in joined
        assert "--permission-prompt-tool" in args and args[args.index("--permission-prompt-tool") + 1] == "stdio"
        return Proc(self, name, self.claude, args, cwd, env)


def init_req(p):
    r = p.control({"subtype": "initialize"})
    return r["response"]["response"] if isinstance(r, dict) else {"_error": r}


def poll_bound(p, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        r = p.control({"subtype": "mcp_status"})
        if not isinstance(r, dict):
            return {"error": str(r)}
        srv = next((s for s in r["response"]["response"].get("mcpServers", []) if s.get("name") == HOSTED), None)
        if srv and srv.get("status") != "pending":
            return {"status": srv.get("status"), "version": (srv.get("serverInfo") or {}).get("version"),
                    "at": round(time.time() - p.t0, 2)}
        time.sleep(0.25)
    return {"status": "TIMEOUT"}


def last_init(p):
    return next((d for d in reversed(p.lines) if d.get("subtype") == "init"), {})


def hosted_tools(init):
    return sorted(t for t in init.get("tools", []) if t.startswith(f"mcp__{HOSTED}__"))


def next_event(p, timeout=120):
    """The next can_use_tool request or the turn's result (or a cancel request), whichever comes first."""
    return p.wait(lambda d: d.get("type") in ("result", "control_request", "control_cancel_request"), timeout)


def bridge_calls(C, name):
    out = []
    path = f"{C.W}/raw/{name}.bridge.jsonl"
    if not os.path.exists(path):
        return out
    for l in open(path):
        r = json.loads(l)
        try:
            m = json.loads(r["line"])
        except ValueError:
            continue
        if r["dir"] == "C>S" and m.get("method") == "tools/call":
            out.append({"name": m["params"].get("name"), "arguments": m["params"].get("arguments"),
                        "_meta": m["params"].get("_meta"), "raw_params_keys": sorted(m["params"].keys())})
    return out


def summary(req):
    r = req.get("request", {})
    return {"request_id": "<id>", "subtype": r.get("subtype"), "tool_name": r.get("tool_name"),
            "mcp_server": r.get("mcp_server"), "display_name": r.get("display_name"), "input": r.get("input"),
            "tool_use_id": "present" if r.get("tool_use_id") else None, "keys": sorted(r.keys()),
            "suggestions": [s.get("type") for s in r.get("permission_suggestions") or []]}


def result_brief(d):
    if not isinstance(d, dict):
        return d
    return {"subtype": d.get("subtype"), "is_error": d.get("is_error"), "terminal_reason": d.get("terminal_reason"),
            "permission_denials": [x.get("tool_name") for x in d.get("permission_denials") or []],
            "result": (d.get("result") or "")[:300], "cost": d.get("total_cost_usd"), "_t": d.get("_t")}


EXPERIMENTS = {}


def exp(name):
    def deco(f):
        EXPERIMENTS[name] = f
        return f
    return deco


# ---------------------------------------------------------------- P3-0 / §4.4: the lines at no cost
@exp("p3_lines_iso")
def _(C):
    """The P3 lines on the real bridge, not logged in: initialize's mode, the binding, and a turn's init tools — the
    default posture and each full mode. Zero cost (OD-74.18)."""
    out = {}
    runs = [("default-posture", None)] + [(f"full-{m}", m) for m in MODES]
    for label, mode in runs:
        name = f"p3-lines-{label}-iso"
        if mode is None:
            args, nonce = C.default_args(name)
            cwd = f"{C.W}/proj"
        else:
            args, nonce = C.full_args(name, mode, model="sonnet" if mode == "auto" else None)
            cwd = f"{C.W}/full"
        p = C.spawn(name, args, cwd, iso=True)
        ini = init_req(p)
        b = poll_bound(p)
        p.send_user("hello")
        r = p.result(60)
        i = last_init(p)
        p.close(20)
        out[label] = {"initialize.current_permission_mode": ini.get("current_permission_mode"),
                      "bound": b.get("status"), "nonce_exact": (b.get("version") or "").endswith("+bind." + nonce),
                      "version": (b.get("version") or "").replace(nonce, "<nonce>"),
                      "init.permissionMode": i.get("permissionMode"), "hosted_tools": hosted_tools(i),
                      "builtin_tools": sorted(t for t in i.get("tools", []) if not t.startswith("mcp__")),
                      "mcp_servers": i.get("mcp_servers"), "result": result_brief(r)}
    C.record("p3_lines_iso", **out)


# ---------------------------------------------------------------- P3-0 and P3-3: the real bridge, allow and deny
@exp("p3_0_real")
def _(C):
    """P3-0: the whole default P3 line on the real bridge — report visible, a can_use_tool for it, deny reaches the model,
    allow runs it with updatedInput. P3-3: what the bridge's tools/call carries (_meta, the tool_use_id) and which input
    reaches the server when updatedInput differs from input (once, here only)."""
    name = "p3-0-default-real"
    args, nonce = C.default_args(name)
    p = C.spawn(name, args, f"{C.W}/proj", iso=False)
    out = {"initialize.current_permission_mode": init_req(p).get("current_permission_mode"), "binding": poll_bound(p)}
    turns = [("deny", {"title": "p3-zero-deny", "path": "p3-zero-deny.pdf"}),
             ("allow", {"title": "p3-zero-allow", "path": "p3-zero-allow.pdf"}),
             ("changed", {"title": "p3-zero-original", "path": "p3-zero-original.pdf"})]
    for kind, inp in turns:
        t0 = time.time()
        p.send_user("Call the tool mcp__fluxtion-analyser-hosted__analyser_report exactly once with exactly this input: "
                    + json.dumps(inp) + ". Then reply with its output or the exact error text, in one line.")
        reqs, r = [], None
        while True:
            d = next_event(p)
            if not isinstance(d, dict) or d.get("type") == "result":
                r = d
                break
            if d.get("type") != "control_request":
                reqs.append({"other": d.get("type")})
                continue
            reqs.append(summary(d))
            rq = d["request"]
            if kind == "deny":
                p.answer(d["request_id"], {"behavior": "deny", "message": "Denied by the person in the analyser"})
            elif kind == "allow":
                p.answer(d["request_id"], {"behavior": "allow", "updatedInput": rq.get("input")})
            else:
                changed = dict(rq.get("input") or {}, title="p3-zero-CHANGED-BY-HOST")
                p.answer(d["request_id"], {"behavior": "allow", "updatedInput": changed})
        out[kind] = {"requests": reqs, "result": result_brief(r), "server_actions": C.fake.actions(t0)}
        if not isinstance(r, dict):
            break
    i = next((d for d in p.lines if d.get("subtype") == "init"), {})
    out["init.permissionMode"] = i.get("permissionMode")
    out["init.tools"] = sorted(i.get("tools", []))
    out["bridge_tools_call"] = bridge_calls(C, name)
    # the tool_use_id of each request against the _meta the bridge saw, compared here, never recorded raw
    ids = [d["request"].get("tool_use_id") for d in p.lines if d.get("type") == "control_request"]
    metas = json.dumps([c["_meta"] for c in out["bridge_tools_call"]])
    out["tool_use_id_in_bridge_meta"] = [bool(x) and x in metas for x in ids]
    p.close(30)
    C.record("p3_0_real", **out)


# ---------------------------------------------------------------- P3-1: unanswered
@exp("p3_1_real")
def _(C):
    """P3-1: a can_use_tool left unanswered — does the turn block, does the CLI time out on its own (and how)?"""
    name = "p3-1-unanswered-real"
    args, _ = C.default_args(name)
    p = C.spawn(name, args, f"{C.W}/proj", iso=False)
    init_req(p); poll_bound(p)
    p.send_user('Call the tool mcp__fluxtion-analyser-hosted__analyser_report with input {"title":"p3-one","path":'
                '"p3-one.pdf"}. Then reply with its output or the exact error text.')
    d = p.wait(lambda d: d.get("type") in ("control_request", "result"), 120)
    out = {"first": summary(d) if isinstance(d, dict) and d.get("type") == "control_request" else result_brief(d)}
    asked_at = d.get("_t") if isinstance(d, dict) else None
    wait_s = C.a.unanswered
    after = p.wait(lambda x: x.get("type") in ("result", "control_cancel_request", "control_request"), wait_s)
    out["waited_s"] = wait_s
    out["during_wait"] = p.kinds(asked_at or 0)
    out["ended_on_its_own"] = result_brief(after) if isinstance(after, dict) else after
    out["server_actions"] = C.fake.actions()
    if isinstance(d, dict) and d.get("type") == "control_request" and not isinstance(after, dict):
        # still pending: a deny answer after the wait — is it still accepted?
        p.answer(d["request_id"], {"behavior": "deny", "message": "Not answered in the analyser within 120 s"})
        r = p.result(90)
        out["late_deny_then"] = result_brief(r)
    p.close(30)
    C.record("p3_1_real", **out)


# ---------------------------------------------------------------- P3-2: interrupt while pending
@exp("p3_2_real")
def _(C):
    """P3-2: an interrupt while a request is pending: a cancel request, or nothing; the turn's end; an answer after."""
    name = "p3-2-interrupt-real"
    args, _ = C.default_args(name)
    p = C.spawn(name, args, f"{C.W}/proj", iso=False)
    init_req(p); poll_bound(p)
    mid = p.send_user('Call the tool mcp__fluxtion-analyser-hosted__analyser_report with input {"title":"p3-two",'
                      '"path":"p3-two.pdf"}. Then reply with its output or the exact error text.')
    d = p.wait(lambda d: d.get("type") in ("control_request", "result"), 120)
    out = {"first": summary(d) if isinstance(d, dict) and d.get("type") == "control_request" else result_brief(d)}
    if isinstance(d, dict) and d.get("type") == "control_request":
        t = d["_t"]
        ir = p.control({"subtype": "interrupt"}, rid="interrupt-1")
        out["interrupt_response"] = {"subtype": ir["response"].get("subtype"),
                                     "response": ir["response"].get("response")} if isinstance(ir, dict) else ir
        r = p.result(30)
        out["after_interrupt"] = p.kinds(t)
        out["cancel_requests"] = [x for x in p.lines if x.get("type") == "control_cancel_request"]
        out["lifecycle"] = [x.get("state") for x in p.lines if x.get("type") == "command_lifecycle"
                            and x.get("command_uuid") == mid]
        out["result"] = result_brief(r)
        t2 = time.time()
        p.answer(d["request_id"], {"behavior": "allow", "updatedInput": d["request"].get("input")})
        time.sleep(4)
        out["late_allow_lines"] = p.kinds(round(t2 - p.t0, 2))
        out["late_allow_server_actions"] = C.fake.actions(t2)
        # the session still works afterwards
        p.send_user("Reply with the single word READY.")
        r2 = p.result(90)
        out["next_turn"] = result_brief(r2)
    out["server_actions"] = C.fake.actions()
    p.close(30)
    C.record("p3_2_real", **out)


# ---------------------------------------------------------------- P3-4: what asks in the default posture
@exp("p3_4_real")
def _(C):
    """P3-4: under --restricted and mode default, does anything but an MCP tool ever ask? Read/Grep/Glob in and out of
    the folder, and @path mentions in and out."""
    name = "p3-4-restricted-real"
    args, _ = C.default_args(name)
    p = C.spawn(name, args, f"{C.W}/proj", iso=False)
    init_req(p); poll_bound(p)
    W = C.W
    asks = [f"Call the Read tool on {W}/proj/inside.txt, then the Grep tool for CANARY in {W}/proj, then the Glob tool "
            f"for **/*.md in {W}/proj. Reply with what each returned, in one line each.",
            f"Call the Read tool on {W}/outside/harmless.txt, then the Grep tool for CANARY in {W}/outside, then the Glob "
            f"tool for * in {W}/outside. Reply with what each returned or its exact error, one line each.",
            f"What does @{W}/proj/notes.md say? And @{W}/outside/harmless.txt? One line each."]
    out = {"turns": []}
    for a in asks:
        p.send_user(a)
        reqs, r = [], None
        while True:
            d = next_event(p, 120)
            if not isinstance(d, dict) or d.get("type") == "result":
                r = d
                break
            if d.get("type") == "control_request":
                reqs.append(summary(d))
                p.answer(d["request_id"], {"behavior": "deny", "message": "Denied by the probe"})
        uses = [b.get("name") for x in p.lines if x.get("type") == "assistant"
                for b in x.get("message", {}).get("content", []) if b.get("type") == "tool_use"]
        out["turns"].append({"can_use_tool": reqs, "result": result_brief(r)})
    out["tool_uses"] = [b.get("name") for x in p.lines if x.get("type") == "assistant"
                        for b in x.get("message", {}).get("content", []) if b.get("type") == "tool_use"]
    out["denials"] = [{"tool": x.get("tool_name"), "reason": x.get("decision_reason_type")} for x in p.lines
                      if x.get("subtype") == "permission_denied"]
    p.close(30)
    C.record("p3_4_real", **out)


# ---------------------------------------------------------------- P3-5: the full posture
def plant_full(C, hook_verb=None, rule_verb=None):
    """The probe folder's own .claude/settings.json (the FOLDER's, never the person's): a PreToolUse hook that allows
    one hosted verb, and an allow rule for another."""
    s = {}
    if hook_verb:
        # the hook is a script file, so no shell quoting stands between it and its JSON (the first run's inline echo
        # carried an apostrophe and failed as a shell syntax error — recorded in the report)
        allow = json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow",
                                                   "permissionDecisionReason": "planted by the probe"}})
        script = f"{C.W}/full/allow-hook.sh"
        open(script, "w").write(f"#!/bin/sh\necho hook >> {C.W}/markers/pretooluse.txt\ncat <<'JSON'\n{allow}\nJSON\n")
        os.chmod(script, 0o755)
        s["hooks"] = {"PreToolUse": [{"matcher": tool(hook_verb), "hooks": [{"type": "command", "command": script}]}]}
    if rule_verb:
        s["permissions"] = {"allow": [tool(rule_verb), "Bash(echo P35RULE:*)"]}
    json.dump(s, open(f"{C.W}/full/.claude/settings.json", "w"))
    local = f"{C.W}/full/.claude/settings.local.json"
    if rule_verb:
        json.dump({"permissions": {"allow": [tool(rule_verb), "Bash(echo P35RULE:*)"]}}, open(local, "w"))
    elif os.path.exists(local):
        os.remove(local)


def full_turns(C, name, mode, asks, answer):
    args, _ = C.full_args(name, mode)
    p = C.spawn(name, args, f"{C.W}/full", iso=False)
    ini = init_req(p); b = poll_bound(p)
    res = {"initialize.current_permission_mode": ini.get("current_permission_mode"), "bound": b.get("status"), "turns": []}
    for a in asks:
        t0 = time.time()
        p.send_user(a)
        reqs, r = [], None
        while True:
            d = next_event(p, 120)
            if not isinstance(d, dict) or d.get("type") == "result":
                r = d
                break
            if d.get("type") == "control_request":
                reqs.append(summary(d))
                p.answer(d["request_id"], answer(d["request"]))
        res["turns"].append({"can_use_tool": reqs, "result": result_brief(r), "server_actions": C.fake.actions(t0)})
    i = last_init(p)
    res["init.permissionMode"] = i.get("permissionMode")
    res["hosted_tools"] = hosted_tools(i)
    res["registration_tools"] = [t for t in i.get("tools", []) if t.startswith("mcp__fluxtion-analyser__")]
    res["tool_uses"] = [b.get("name") for x in p.lines if x.get("type") == "assistant"
                        for b in x.get("message", {}).get("content", []) if b.get("type") == "tool_use"]
    res["denials"] = [{"tool": x.get("tool_name"), "reason": x.get("decision_reason_type")} for x in p.lines
                      if x.get("subtype") == "permission_denied"]
    p.close(30)
    return res


@exp("p3_5_real")
def _(C):
    """P3-5: in the full posture, Bash and Edit asks reach stdio under default and acceptEdits; a planted PreToolUse hook
    returning allow, and a planted allow rule, for hosted destructive verbs each BYPASS can_use_tool."""
    W = C.W
    open(f"{W}/full/edit-me.txt", "w").write("before\n")
    allow = lambda rq: {"behavior": "allow", "updatedInput": rq.get("input")}
    deny = lambda rq: {"behavior": "deny", "message": "Denied by the probe"}
    plant_full(C)
    out = {}
    out["default"] = full_turns(C, "p3-5-full-default-real", "default", [
        f"Run this exact Bash command once: touch {W}/markers/p3-5-bash-default && echo P35-BASH-DEFAULT . Reply with its output.",
        f"Use the Edit tool to change the word before to after in {W}/full/edit-me.txt. Reply DONE or the exact error."],
        allow)
    out["edit_file_after_default"] = open(f"{W}/full/edit-me.txt").read().strip()
    open(f"{W}/full/edit-me.txt", "w").write("before\n")
    out["acceptEdits"] = full_turns(C, "p3-5-full-acceptedits-real", "acceptEdits", [
        f"Run this exact Bash command once: touch {W}/markers/p3-5-bash-accept && echo P35-BASH-ACCEPT . Reply with its output.",
        f"Use the Edit tool to change the word before to after in {W}/full/edit-me.txt. Reply DONE or the exact error."],
        deny)
    out["edit_file_after_acceptEdits"] = open(f"{W}/full/edit-me.txt").read().strip()
    plant_full(C, hook_verb="report", rule_verb="screenshot")
    out["bypass"] = full_turns(C, "p3-5-full-bypass-real", "default", [
        'Call the tool mcp__fluxtion-analyser-hosted__analyser_report once with input {"title":"p3-five-hook","path":'
        '"p3-five-hook.pdf"}. Reply with its output or the exact error.',
        'Call the tool mcp__fluxtion-analyser-hosted__analyser_screenshot once with input {"path":"p3-five-rule.png"}. '
        'Reply with its output or the exact error.'], deny)
    out["markers"] = sorted(os.path.basename(f) for f in glob.glob(f"{W}/markers/*"))
    out["hook_ran"] = os.path.exists(f"{W}/markers/pretooluse.txt")
    plant_full(C)
    C.record("p3_5_real", **out)


@exp("p3_5b_real")
def _(C):
    """P3-5, the bypass half again (the first run's hook failed on shell quoting): a PreToolUse hook script answering
    allow for analyser_report; allow rules for analyser_screenshot and `Bash(echo P35RULE:*)` in the folder's settings
    and local settings. Which of them reach can_use_tool?"""
    deny = lambda rq: {"behavior": "deny", "message": "Denied by the probe"}
    plant_full(C, hook_verb="report", rule_verb="screenshot")
    out = full_turns(C, "p3-5b-full-bypass-real", "default", [
        'Call the tool mcp__fluxtion-analyser-hosted__analyser_report once with input {"title":"p3-five-hook","path":'
        '"p3-five-hook.pdf"}. Reply with its output or the exact error.',
        'Call the tool mcp__fluxtion-analyser-hosted__analyser_screenshot once with input {"path":"p3-five-rule.png"}. '
        'Reply with its output or the exact error.',
        "Run this exact Bash command once: echo P35RULE:ok . Reply with its output."], deny)
    out["hook_ran"] = os.path.exists(f"{C.W}/markers/pretooluse.txt")
    plant_full(C)
    C.record("p3_5b_real", **out)


# ---------------------------------------------------------------- P3-6: two in one message
@exp("p3_6_real")
def _(C):
    """P3-6: two can_use_tool requests from one assistant message — do both arrive before either is answered; denying the
    second while the first is open leaves the first answerable."""
    name = "p3-6-parallel-real"
    args, _ = C.default_args(name)
    p = C.spawn(name, args, f"{C.W}/proj", iso=False)
    init_req(p); poll_bound(p)
    t0 = time.time()
    p.send_user("In ONE single assistant message, emit TWO tool_use blocks in parallel (do not wait between them): "
                'mcp__fluxtion-analyser-hosted__analyser_report with input {"title":"p3-six-a","path":"p3-six-a.pdf"} and '
                'mcp__fluxtion-analyser-hosted__analyser_screenshot with input {"path":"p3-six-b.png"}. Then reply with '
                "each output or exact error, one line each.")
    first = p.wait(lambda d: d.get("type") in ("control_request", "result"), 120)
    out = {"first": summary(first) if isinstance(first, dict) and first.get("type") == "control_request" else result_brief(first)}
    second = p.wait(lambda d: d.get("type") in ("control_request", "result"), 8) if isinstance(first, dict) \
        and first.get("type") == "control_request" else None
    out["second_before_first_answered"] = summary(second) if isinstance(second, dict) and second.get("type") == "control_request" \
        else (result_brief(second) if isinstance(second, dict) else second)
    msgs = [[b.get("name") for b in x.get("message", {}).get("content", []) if b.get("type") == "tool_use"]
            for x in p.lines if x.get("type") == "assistant"]
    out["tool_use_blocks_per_assistant_event"] = [m for m in msgs if m]
    r = None
    if isinstance(second, dict) and second.get("type") == "control_request":
        p.answer(second["request_id"], {"behavior": "deny", "message": "Denied: another approval is waiting for the person"})
        time.sleep(2)
        p.answer(first["request_id"], {"behavior": "allow", "updatedInput": first["request"].get("input")})
        while True:
            d = next_event(p, 120)
            if not isinstance(d, dict) or d.get("type") == "result":
                r = d
                break
            if d.get("type") == "control_request":
                out.setdefault("later", []).append(summary(d))
                p.answer(d["request_id"], {"behavior": "deny", "message": "Denied by the probe"})
    elif isinstance(first, dict) and first.get("type") == "control_request":
        out["note"] = "the second request did not arrive while the first was open"
        p.answer(first["request_id"], {"behavior": "allow", "updatedInput": first["request"].get("input")})
        while True:
            d = next_event(p, 120)
            if not isinstance(d, dict) or d.get("type") == "result":
                r = d
                break
            if d.get("type") == "control_request":
                out.setdefault("later", []).append(summary(d))
                p.answer(d["request_id"], {"behavior": "deny", "message": "Denied by the probe"})
    out["result"] = result_brief(r)
    out["server_actions"] = C.fake.actions(t0)
    out["denials"] = [x.get("tool_name") for x in p.lines if x.get("subtype") == "permission_denied"]
    p.close(30)
    C.record("p3_6_real", **out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--i-accept-spend", action="store_true", help="required for *_real: model turns on the person's plan")
    ap.add_argument("--model", default="haiku")
    ap.add_argument("--budget", type=float, default=0.30, help="--max-budget-usd per process (a runaway guard)")
    ap.add_argument("--cap", type=float, default=2.0, help="hard total across the probe (USD-equivalent)")
    ap.add_argument("--unanswered", type=int, default=200, help="P3-1: seconds to leave a request unanswered")
    ap.add_argument("--workdir")
    ap.add_argument("--jar")
    ap.add_argument("--only")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--sanitise", nargs=2, metavar=("RAW", "OUT"))
    ap.add_argument("--cwd")
    a = ap.parse_args()
    if a.list:
        for k in EXPERIMENTS:
            print(k, "(isolated config, no spend)" if k.endswith("_iso") else "(real login)")
        return
    if a.sanitise:
        P0.sanitise_file(a.sanitise[0], a.sanitise[1], a.workdir, a.cwd)
        return
    if "bypass" in " ".join(sys.argv).lower() and "--only" not in sys.argv:
        ap.error("bypassPermissions is never used by this probe")
    if not a.jar:
        ap.error("--jar: the built analyser jar (its --mcp bridge is the hosted server under test)")
    watchdog = threading.Timer(1800, lambda: os._exit(3))   # the runaway guard: 30 minutes for the whole run
    watchdog.daemon = True
    watchdog.start()
    C = Ctx(a)
    for n in (a.only.split(",") if a.only else list(EXPERIMENTS)):
        try:
            EXPERIMENTS[n](C)
        except SystemExit as e:
            print(e)
            break
        print(f"# ledger {C.ledger.total():.4f} / cap {a.cap}", flush=True)
    json.dump(C.results, open(os.path.join(C.W, "results.json"), "w"), indent=1, default=str)
    print(f"# done; total {C.ledger.total():.4f}; raw transcripts (sensitive) in {C.W}/raw")


if __name__ == "__main__":
    main()
