#!/usr/bin/env python3
"""M74 P0 — protocol probe of `claude -p` stream-json, reproducible.

Spends real money on the logged-in Claude Code account of whoever runs it, so it refuses to run
without --i-accept-spend. Every process gets --max-budget-usd; a ledger sums the per-process cost
reported in each `result` (total_cost_usd is cumulative per SESSION, restored on --resume, so the
ledger records per-process deltas) and the script stops before --cap would be exceeded.

  python3 probe.py --list
  python3 probe.py --i-accept-spend [--model haiku] [--budget 0.25] [--cap 5] [--workdir DIR] [--only a,b]
  python3 probe.py --sanitise RAW.jsonl OUT.jsonl --workdir DIR [--cwd SUBDIR]

Isolation: everything runs in --workdir (a fresh temp dir by default). Experiments marked `iso` run
under an isolated CLAUDE_CONFIG_DIR (not logged in — they cost nothing and cannot see the person's
servers); every experiment that uses the real login passes --strict-mcp-config, so the person's own
MCP servers and claude.ai connectors never load. Raw transcripts in <workdir>/raw are SENSITIVE
(system/init lists the person's skills, plugins and paths; `initialize` returns the account email):
only sanitised copies may be committed (--sanitise). Never pass bypassPermissions.

P0b (the `b_*` experiments, 2026-10-16; P0b-report.md): the exact §12.1 default command against a stub
`fluxtion-analyser-hosted` server, the binding nonce in serverInfo.version, the P3 permission contract
(--permission-prompt-tool stdio vs an MCP prompt tool), slash commands, and initialize.account field names.
`b_slash_config_iso` runs `/config key=value` and MUST stay on the isolated config: against the real login it
would rewrite the person's own user settings.json. The sanitiser rejoins split streaming deltas (a path split
across chunks escapes a line-wise pass).
"""
import argparse, glob, json, os, queue, re, signal, subprocess, sys, tempfile, threading, time, uuid

STUB = r'''#!/usr/bin/env python3
import json, sys, os, signal, time
if os.environ.get("STUB_IGNORE_TERM"):
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
name = sys.argv[1]; log = sys.argv[2]
def note(s):
    with open(log, "a") as f: f.write(f"{name} {s}\n")
TOOLS = [{"name": "echo", "description": "Echo text.", "inputSchema": {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"]}},
         {"name": "sleep", "description": "Sleep N seconds.", "inputSchema": {"type": "object", "properties": {"seconds": {"type": "integer"}}, "required": ["seconds"]}},
         {"name": "add", "description": "Add two integers.", "inputSchema": {"type": "object", "properties": {"a": {"type": "integer"}, "b": {"type": "integer"}}, "required": ["a", "b"]}}]
note("start")
for line in sys.stdin:
    if not line.strip(): continue
    m = json.loads(line); mid = m.get("id"); meth = m.get("method")
    if meth == "initialize":
        res = {"protocolVersion": m["params"].get("protocolVersion", "2025-06-18"), "capabilities": {"tools": {}},
               "serverInfo": {"name": name, "version": "0.0.1", "boundEndpoint": "stub://" + name}}
    elif meth == "tools/list": res = {"tools": TOOLS}
    elif meth == "tools/call":
        p = m["params"]; a = p.get("arguments", {}); note("call " + p["name"] + " " + json.dumps(a))
        if p["name"] == "sleep": time.sleep(int(a.get("seconds", 1))); t = "slept"
        elif p["name"] == "echo": t = f"[{name}] {a.get('text')}"
        else: t = f"[{name}] {int(a.get('a', 0)) + int(a.get('b', 0))}"
        res = {"content": [{"type": "text", "text": t}]}
    elif mid is None: continue
    else:
        print(json.dumps({"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "no"}}), flush=True); continue
    print(json.dumps({"jsonrpc": "2.0", "id": mid, "result": res}), flush=True)
note("exit")
'''


class Ledger:
    def __init__(self, path, cap):
        self.path, self.cap = path, cap
        self.entries = json.load(open(path)) if os.path.exists(path) else []

    def total(self):
        return sum(e["cost"] for e in self.entries)

    def add(self, name, cost):
        self.entries.append({"name": name, "cost": cost})
        json.dump(self.entries, open(self.path, "w"), indent=1)


class Probe:
    def __init__(self, a):
        self.a = a
        self.W = os.path.realpath(a.workdir)
        self.ledger = Ledger(os.path.join(self.W, "ledger.json"), a.cap)
        self.results = {}

    # ---------- environment ----------
    def setup(self):
        W = self.W
        for d in ["raw", "markers", "trusted", "othercwd", "outside", "claudemd", "untrusted/.claude", "symdir",
                  "skilldir/.claude/skills/probe-skill", "cfg-isolated"]:
            os.makedirs(os.path.join(W, d), exist_ok=True)
        open(f"{W}/stub.py", "w").write(STUB)
        open(f"{W}/trusted/inside.txt", "w").write("INSIDE-CANARY-17\n")
        open(f"{W}/outside/harmless.txt", "w").write("OUTSIDE-CANARY-42\n")
        open(f"{W}/claudemd/CLAUDE.md", "w").write("# Probe\nIf anyone asks for the codeword, it is PINEAPPLE-7.\n")
        open(f"{W}/skilldir/.claude/skills/probe-skill/SKILL.md", "w").write(
            "---\nname: probe-skill\ndescription: harmless probe skill\n---\nWhen invoked, reply with exactly: SKILL-RAN-9\n")
        hook = lambda tag: {"hooks": [{"type": "command", "command": f"echo {tag} >> {W}/markers/hook.txt"}]}
        json.dump({"hooks": {"SessionStart": [hook("sessionstart")], "UserPromptSubmit": [hook("promptsubmit")]}},
                  open(f"{W}/untrusted/.claude/settings.json", "w"))
        json.dump({"mcpServers": {"projstub": self.server("projstub", "mcp.txt")}}, open(f"{W}/untrusted/.mcp.json", "w"))
        for n, t in [("link.txt", f"{W}/outside/harmless.txt"), ("outdir", f"{W}/outside")]:
            if not os.path.lexists(f"{W}/symdir/{n}"):
                os.symlink(t, f"{W}/symdir/{n}")

    def server(self, name, log, env=None):
        s = {"command": sys.executable, "args": [f"{self.W}/stub.py", name, f"{self.W}/markers/{log}"]}
        if env:
            s["env"] = env
        return s

    def mcp(self, *names, env=None):
        return json.dumps({"mcpServers": {n: self.server(n, "calls.txt", env) for n in names}})

    def markers(self):
        return {os.path.basename(f): open(f).read().split() for f in glob.glob(f"{self.W}/markers/*")}

    def clear_markers(self):
        for f in glob.glob(f"{self.W}/markers/*"):
            os.remove(f)

    # ---------- process ----------
    def spawn(self, name, args, cwd="trusted", iso=False, env=None, budget=None, base=True):
        budget = self.a.budget if budget is None else budget
        if not iso and self.ledger.total() + budget > self.a.cap:
            raise SystemExit(f"STOP: ledger {self.ledger.total():.4f} + {budget} would exceed cap {self.a.cap}")
        e = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}  # never nest inside a host session
        if iso:
            e["CLAUDE_CONFIG_DIR"] = f"{self.W}/cfg-isolated"
        e.update(env or {})
        full = (["-p", "--model", self.a.model, "--input-format", "stream-json", "--output-format", "stream-json",
                 "--verbose", "--max-budget-usd", str(budget)] if base else []) + args
        return Proc(self, name, full, os.path.join(self.W, cwd), e)

    def turns(self, name, args, prompts, timeout=120, **kw):
        p = self.spawn(name, args, **kw)
        res = []
        for t in prompts:
            p.send_user(t)
            r = p.result(timeout)
            res.append(r)
            if not isinstance(r, dict):
                break
        p.code = p.close()
        return p, res

    def record(self, key, **obs):
        self.results[key] = obs
        print(json.dumps({"experiment": key, **obs}, default=str)[:2000].replace(self.W, "<W>"), flush=True)


class Proc:
    def __init__(self, probe, name, args, cwd, env):
        self.probe, self.name = probe, name
        self.raw = open(os.path.join(probe.W, "raw", name + ".jsonl"), "w")
        self.err = open(os.path.join(probe.W, "raw", name + ".err"), "w")
        self.p = subprocess.Popen(["claude"] + args, cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
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
            self.lines.append(d)
            if d.get("type") == "result":
                c = d.get("total_cost_usd") or 0.0
                # total_cost_usd is cumulative per session (restored on --resume); record the delta. The first
                # result of a resumed process includes earlier processes' cost, so this over-counts: safe side.
                self.probe.ledger.add(self.name, max(0.0, c - self.cost)); self.cost = c
            self.q.put(d)
        self.q.put(None)

    def send(self, obj):
        self.p.stdin.write((obj if isinstance(obj, str) else json.dumps(obj)) + "\n"); self.p.stdin.flush()

    def send_user(self, text, mid=None):
        mid = mid or str(uuid.uuid4())  # a reused uuid is silently dropped by the CLI (see dedupe experiment)
        self.send({"type": "user", "message": {"role": "user", "content": text}, "parent_tool_use_id": None, "uuid": mid})
        return mid

    def control(self, request, rid=None):
        rid = rid or "req-" + uuid.uuid4().hex[:8]
        self.send({"type": "control_request", "request_id": rid, "request": request})
        return self.wait(lambda d: d.get("type") == "control_response" and d["response"].get("request_id") == rid, 30)

    def wait(self, pred, timeout=90):
        end = time.time() + timeout
        while time.time() < end:
            try:
                d = self.q.get(timeout=max(0.1, end - time.time()))
            except queue.Empty:
                break
            if d is None:
                return None
            if pred(d):
                return d
        return "TIMEOUT"

    def result(self, timeout=120):
        return self.wait(lambda d: d.get("type") == "result", timeout)

    def close(self, timeout=60):
        try:
            self.p.stdin.close()
        except OSError:
            pass
        try:
            self.p.wait(timeout)
        except subprocess.TimeoutExpired:
            self.p.kill()
        return self.p.returncode

    def init(self):
        return next((d for d in self.lines if d.get("subtype") == "init"), {})


def brief(r):
    if not isinstance(r, dict):
        return r
    return {"subtype": r.get("subtype"), "is_error": r.get("is_error"), "cost": r.get("total_cost_usd"),
            "terminal_reason": r.get("terminal_reason"), "denials": [d["tool_name"] for d in r.get("permission_denials", [])],
            "result": (r.get("result") or "")[:120]}


def descendants(pid):
    rows = []
    for l in subprocess.run(["ps", "-A", "-o", "pid=,ppid="], capture_output=True, text=True).stdout.splitlines():
        a, b = l.split()[:2]; rows.append((int(a), int(b)))
    out, fr = [], [pid]
    while fr:
        p = fr.pop(); ch = [r[0] for r in rows if r[1] == p]; out += ch; fr += ch
    return out


def alive(pids):
    out = []
    for p in pids:
        try:
            os.kill(p, 0); out.append(p)
        except OSError:
            pass
    return out


# ---------------------------------------------------------------- experiments
NOTOOLS = ["--strict-mcp-config", "--tools", "", "--permission-mode", "dontAsk", "--permission-prompts", "none"]
DEFAULT = ["--strict-mcp-config", "--tools", "Read,Grep,Glob", "--permission-mode", "dontAsk", "--permission-prompts", "none"]
EXPERIMENTS = {}


def exp(name, iso=False):
    def deco(f):
        EXPERIMENTS[name] = (f, iso); return f
    return deco


@exp("auth_isolated", iso=True)
def _(P):
    p, res = P.turns("auth-isolated", NOTOOLS, ["Reply OK"], iso=True, timeout=60)
    P.record("auth_isolated", code=p.code, apiKeySource=p.init().get("apiKeySource"), result=brief(res[0]))


@exp("verbose_required", iso=True)
def _(P):
    p = P.spawn("noverbose", ["-p", "--input-format", "stream-json", "--output-format", "stream-json"], iso=True, base=False)
    code = p.close(); P.record("verbose_required", code=code, stderr=open(p.err.name).read()[:200])


@exp("multiturn")
def _(P):
    sid = str(uuid.uuid4())
    p = P.spawn("multiturn", NOTOOLS + ["--include-partial-messages", "--replay-user-messages", "--session-id", sid])
    out = []
    for t in ["Reply with exactly: one", "Reply with exactly: two"]:
        p.send_user(t); out.append(brief(p.result()))
    p.send_user("Count from 1 to 30, one number per line.")
    p.wait(lambda d: d.get("type") == "stream_event" and d["event"].get("type") == "content_block_delta", 60)
    p.send_user("Reply with exactly: four")  # arrives mid-turn: queued, runs after
    out += [brief(p.result()), brief(p.result())]
    t = time.time(); code = p.close()
    i = p.init()
    P.record("multiturn", results=out, eof_exit=code, eof_secs=round(time.time() - t, 1),
             init_keys=sorted(i), capabilities=i.get("capabilities"), permissionMode=i.get("permissionMode"),
             inits=sum(1 for d in p.lines if d.get("subtype") == "init"),
             kinds=sorted({f"{d.get('type')}/{d.get('subtype') or d.get('state') or (d.get('event') or {}).get('type')}" for d in p.lines}))


@exp("interrupt")
def _(P):
    p = P.spawn("interrupt", NOTOOLS + ["--replay-user-messages"])
    p.send_user("Write the numbers from 1 to 400 as English words, one per line.")
    p.wait(lambda d: d.get("type") == "stream_event", 60); time.sleep(1)
    q = p.send_user("Reply with exactly: queued")
    resp = p.control({"subtype": "interrupt"})
    r1, r2 = p.result(60), p.result(60)
    p2 = P.spawn("interrupt-cancel-queued", NOTOOLS)
    p2.send_user("Write the numbers from 1 to 400 as English words, one per line.")
    p2.wait(lambda d: d.get("type") == "stream_event" or d.get("type") == "assistant", 60); time.sleep(1)
    p2.send_user("Reply with exactly: queued")
    resp2 = p2.control({"subtype": "interrupt", "cancel_queued": True})
    r3 = p2.result(60); r4 = p2.wait(lambda d: d.get("type") == "result", 8)
    p.close(); p2.close()
    P.record("interrupt", control_response=resp, interrupted=brief(r1), queued_after=brief(r2),
             cancel_queued_response=resp2, interrupted2=brief(r3), further_result=brief(r4))


@exp("tools_allowlist")
def _(P):
    W = P.W
    p, res = P.turns("tools-allowlist", DEFAULT, [
        "Use the Read tool to read inside.txt in the current directory. Reply with its content only.",
        "Use the Glob tool to list *.txt files in the current directory. Reply with the names only.",
        "Use the Bash tool to run: echo hi. If you have no Bash tool, reply exactly NO-BASH.",
        f"Use the Read tool on {W}/outside/harmless.txt and reply with its content only, or DENIED if refused.",
        "Use the Write tool to create w.txt containing x. If you cannot, reply exactly NO-WRITE."])
    i = p.init()
    P.record("tools_allowlist", tools=i.get("tools"), agents=i.get("agents"), results=[brief(r) for r in res],
             wrote=os.path.exists(f"{W}/trusted/w.txt"))


@exp("restricted_reads")
def _(P):
    W = P.W; allow = json.dumps({"permissions": {"allow": ["Read(//**)"]}})
    qs = [f"Call the Read tool on {W}/outside/harmless.txt, then reply with the tool output or error text.",
          f"Call the Grep tool with pattern CANARY and path {W}/outside, then reply with the output or error text.",
          "Call the Read tool on link.txt in the current directory, then reply with the output or error text."]
    out = {}
    for name, extra in [("allowrule", []), ("allowrule_restricted", ["--restricted"])]:
        p, res = P.turns("read-" + name, DEFAULT + ["--settings", allow] + extra, qs, cwd="symdir")
        out[name] = [brief(r) for r in res]
    P.record("restricted_reads", **out)


@exp("untrusted_dir", iso=True)
def _(P):
    out = {}
    for name, extra in [("plain", []), ("strict", ["--strict-mcp-config"]), ("setting_sources_user", ["--setting-sources", "user"]),
                        ("safe_mode", ["--safe-mode"]), ("restricted", ["--restricted"]),
                        ("restricted_adddir", ["--restricted", "--add-dir", f"{P.W}/untrusted"])]:
        P.clear_markers()
        p = P.spawn("untrusted-" + name, ["--permission-mode", "dontAsk", "--permission-prompts", "none"] + extra,
                    cwd="untrusted", iso=True, env={"CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD": "1"})
        time.sleep(6); before = P.markers()
        p.send_user("hello"); p.result(60); p.close()
        out[name] = {"before_first_turn": before, "after": P.markers(), "mcp": p.init().get("mcp_servers"),
                     "hook_events": [d.get("hook_name") for d in p.lines if d.get("subtype") == "hook_started"]}
    P.record("untrusted_dir", **out)


@exp("claudemd")
def _(P):
    q = "What is the codeword? Reply with the codeword only, or NONE."
    out = {}
    for name, extra, env in [("plain", [], None), ("restricted", ["--restricted"], None),
                             ("setting_sources_user", ["--setting-sources", "user"], None), ("safe_mode", ["--safe-mode"], None),
                             ("restricted_adddir_env", ["--restricted", "--add-dir", f"{P.W}/claudemd"],
                              {"CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD": "1"})]:
        p, res = P.turns("claudemd-" + name, NOTOOLS + extra, [q], cwd="claudemd", env=env)
        out[name] = {"answer": brief(res[0])["result"], "skills": len(p.init().get("skills", []))}
    P.record("claudemd", **out)


@exp("mcp_permissions")
def _(P):
    cfg = P.mcp("fluxtion-analyser", "fluxtion-analyser-hosted")
    qs = ['Call mcp__fluxtion-analyser-hosted__echo with text "h1"; reply with its output or the exact error.',
          'Call mcp__fluxtion-analyser-hosted__add with a=2 b=3; reply with its output or the exact error.',
          'Call mcp__fluxtion-analyser__echo with text "u1"; reply with its output or the exact error.']
    out = {}
    for name, extra in [("explicit", ["--allowedTools", "mcp__fluxtion-analyser-hosted__echo"]),
                        ("wildcard", ["--allowedTools", "mcp__fluxtion-analyser__*"]),
                        ("server_allow_and_disallow", ["--allowedTools", "mcp__fluxtion-analyser-hosted",
                                                       "--disallowedTools", "mcp__fluxtion-analyser"])]:
        P.clear_markers()
        p, res = P.turns("mcp-" + name, NOTOOLS + ["--mcp-config", cfg] + extra, qs)
        out[name] = {"tools": p.init().get("tools"), "results": [brief(r) for r in res],
                     "calls": [l for l in P.markers().get("calls.txt", []) if l in ("echo", "add")]}
    P.record("mcp_permissions", **out)


@exp("collision", iso=True)
def _(P):
    env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}
    env["CLAUDE_CONFIG_DIR"] = f"{P.W}/cfg-isolated"
    s = P.server("user-registered", "collide.txt")
    subprocess.run(["claude", "mcp", "add", "--scope", "user", "fluxtion-analyser", "--", s["command"]] + s["args"],
                   env=env, cwd=f"{P.W}/trusted", capture_output=True)
    cfg = json.dumps({"mcpServers": {"fluxtion-analyser": P.server("flag-supplied", "collide.txt")}})
    out = {}
    for name, extra in [("nostrict", []), ("strict", ["--strict-mcp-config"])]:
        P.clear_markers()
        p, res = P.turns("collision-" + name, ["--mcp-config", cfg] + extra, ["hello"], iso=True, timeout=60)
        out[name] = {"mcp": p.init().get("mcp_servers"), "spawned": P.markers().get("collide.txt")}
    P.record("collision", **out)


@exp("control_requests")
def _(P):
    out = {}
    for name, iso in [("iso", True), ("real", False)]:
        p = P.spawn("control-" + name, NOTOOLS + ["--mcp-config", P.mcp("fluxtion-analyser-hosted")], iso=iso)
        r = p.control({"subtype": "initialize"})
        acct = r["response"]["response"].get("account", {}) if isinstance(r, dict) else {}
        s = p.control({"subtype": "mcp_status"})
        p.close()
        out[name] = {"initialize_keys": sorted(r["response"]["response"]) if isinstance(r, dict) else r,
                     "account_keys": sorted(acct), "mcp_status": [
                {k: m.get(k) for k in ("name", "status", "serverInfo")} for m in s["response"]["response"]["mcpServers"]]
            if isinstance(s, dict) else s}
    P.record("control_requests", **out)


@exp("resume")
def _(P):
    sid = str(uuid.uuid4())
    q = "What number did I ask you to remember, and what is the secret colour? Reply NUMBER COLOUR, or NONE NONE."
    P.turns("resume-first", NOTOOLS + ["--session-id", sid, "--append-system-prompt", "The secret colour is TEAL."],
            ["Remember the number 7341. Reply exactly OK."])
    out = {}
    for name, extra, cwd in [("same_cwd", [], "trusted"), ("other_cwd", [], "othercwd"),
                             ("changed_append", ["--append-system-prompt", "The secret colour is RED."], "trusted"),
                             ("changed_mcp", ["--mcp-config", P.mcp("other-stub")], "trusted")]:
        p, res = P.turns("resume-" + name, NOTOOLS + ["--resume", sid] + extra, [q], cwd=cwd)
        out[name] = {"answer": brief(res[0])["result"] if isinstance(res[0], dict) else res[0],
                     "mcp": p.init().get("mcp_servers")}
    p, res = P.turns("sessionid-reused", NOTOOLS + ["--session-id", sid], ["Reply OK"], timeout=40)
    out["session_id_reused"] = {"code": p.code, "stderr": open(p.err.name).read()[:120]}
    # a turn whose uuid already exists in the session is silently dropped
    p = P.spawn("dedupe", NOTOOLS + ["--resume", sid]); mid = str(uuid.uuid4())
    p.send_user("Reply exactly A", mid); a = p.result(60); p.send_user("Reply exactly B", mid)
    b = p.wait(lambda d: d.get("type") in ("result", "command_lifecycle") and d.get("command_uuid", mid) == mid
               and d.get("state") != "queued", 30)
    p.close()
    out["reused_message_uuid"] = {"first": brief(a), "second": b}
    files = glob.glob(os.path.expanduser(f"~/.claude/projects/*/{sid}.jsonl"))
    out["session_files"] = [f.split("/projects/")[1].split("-")[-1] + "/" + os.path.basename(f) for f in files]
    P.record("resume", **out)


@exp("concurrent")
def _(P):
    sid = str(uuid.uuid4())
    P.turns("concurrent-0", NOTOOLS + ["--session-id", sid], ["Remember the number 7341. Reply OK."])
    a = P.spawn("concurrent-a", NOTOOLS + ["--resume", sid]); b = P.spawn("concurrent-b", NOTOOLS + ["--resume", sid])
    a.send_user("Remember the word ALPHA. Reply exactly OK-A."); b.send_user("Remember the word BRAVO. Reply exactly OK-B.")
    ra, rb = brief(a.result()), brief(b.result()); a.close(); b.close()
    p, res = P.turns("concurrent-after", NOTOOLS + ["--resume", sid], ["Which of ALPHA/BRAVO were you asked to remember? Reply the words only."])
    P.record("concurrent", a=ra, b=rb, after=brief(res[0]))


@exp("permission_mode")
def _(P):
    st = json.dumps({"permissions": {"defaultMode": "acceptEdits"}})
    out = {}
    for name, extra in [("settings_only", ["--settings", st]), ("explicit_over_settings", ["--settings", st, "--permission-mode", "dontAsk"])]:
        p, res = P.turns("mode-" + name, ["--strict-mcp-config", "--tools", ""] + extra, ["Reply OK"])
        out[name] = p.init().get("permissionMode")
    P.record("permission_mode", **out)


@exp("budget")
def _(P):
    sid = str(uuid.uuid4())
    P.turns("budget-warm", NOTOOLS + ["--session-id", sid], ["Reply exactly: warm"])
    p = P.spawn("budget-exceed", NOTOOLS + ["--resume", sid], budget=0.003)
    out = []
    for t in "abcde":
        p.send_user(f"Reply exactly: {t}"); out.append(brief(p.result(60)))
    code = p.close()
    P.record("budget", per_turn=out, exit=code)


@exp("compaction")
def _(P):
    p, res = P.turns("compact-manual-skill", NOTOOLS, ["Remember the number 9182. Reply OK.", "/probe-skill", "/compact",
                                                        "What number did I ask you to remember? Reply with the number only."],
                     cwd="skilldir")
    manual = [d.get("compact_metadata", {}).get("trigger") for d in p.lines if d.get("subtype") == "compact_boundary"]
    big = "The marker word is QUOKKA-55. Filler follows; reply exactly OK.\n" + "".join(
        f"item {i:05d}: alpha bravo charlie delta\n" for i in range(9000))
    p2, res2 = P.turns("autocompact", NOTOOLS + ["--autocompact", "100k"],
                       [big, "Reply exactly: second", "What is the marker word? Reply with it only."], budget=max(P.a.budget, 0.8), timeout=300)
    auto = [d.get("compact_metadata", {}).get("trigger") for d in p2.lines if d.get("subtype") == "compact_boundary"]
    P.record("compaction", skill=brief(res[1]), compact=brief(res[2]), recall=brief(res[3]), manual_boundaries=manual,
             auto_boundaries=auto, auto_recall=brief(res2[-1]))


@exp("process_tree")
def _(P):
    cfg = json.dumps({"mcpServers": {"hosted-stub": P.server("hosted-stub", "tree.txt", {"STUB_IGNORE_TERM": "1"})}})
    args = NOTOOLS + ["--mcp-config", cfg, "--allowedTools", "mcp__hosted-stub__sleep"]
    out = {}
    for mode in ["sigterm", "sigkill", "eof"]:
        p = P.spawn("tree-" + mode, args)
        p.send_user("Call mcp__hosted-stub__sleep with seconds=25, then reply exactly DONE.")
        p.wait(lambda d: d.get("type") == "assistant" and any(c.get("type") == "tool_use" for c in d["message"]["content"]), 60)
        time.sleep(1.5); kids = descendants(p.p.pid); t = time.time()
        if mode == "sigterm": os.kill(p.p.pid, signal.SIGTERM)
        elif mode == "sigkill": os.kill(p.p.pid, signal.SIGKILL)
        else: p.p.stdin.close()
        p.p.wait(60); time.sleep(1)
        surv = alive(kids)
        out[mode] = {"exit": p.p.returncode, "secs": round(time.time() - t, 1), "children": len(kids), "surviving": len(surv),
                     "results": [brief(d) for d in p.lines if d.get("type") == "result"]}
        for k in surv:
            os.kill(k, signal.SIGKILL)
    P.record("process_tree", **out)


@exp("api_key_env", iso=True)
def _(P):
    p, res = P.turns("apikey-env", ["--strict-mcp-config", "--tools", ""], ["Reply OK"], iso=True,
                     env={"ANTHROPIC_API_KEY": "probe-invalid-key"}, timeout=90)
    P.record("api_key_env", apiKeySource=p.init().get("apiKeySource"), result=brief(res[0]) if res else None)


@exp("path", iso=True)
def _(P):
    gui = subprocess.run(["/bin/sh", "-c", "command -v claude || echo NOT-FOUND"], env={"PATH": "/usr/bin:/bin:/usr/sbin:/sbin"},
                         capture_output=True, text=True).stdout.strip()
    login = subprocess.run(["/bin/sh", "-c", "command -v claude"], capture_output=True, text=True).stdout.strip()
    ver = subprocess.run(["claude", "--version"], capture_output=True, text=True).stdout.strip()
    home = os.path.expanduser("~")
    P.record("path", login_path=login.replace(home, "<HOME>"), real=os.path.realpath(login).replace(home, "<HOME>"),
             minimal_gui_path=gui, version=ver)


# ---------------------------------------------------------------- P0b (2026-10-16): the §12.1 default command, the
# binding nonce, the P3 permission contract, slash commands under the default posture, account field names.
HOSTED = "fluxtion-analyser-hosted"
STUB_B = r'''#!/usr/bin/env python3
import json, sys, os, time
name, log, version = sys.argv[1], sys.argv[2], sys.argv[3]
def note(s):
    with open(log, "a") as f: f.write(f"{name} {s}\n")
obj = lambda props, req: {"type": "object", "properties": props, "required": req}
TOOLS = [{"name": "context", "description": "Analyser context (stub).", "inputSchema": obj({}, [])},
         {"name": "echo", "description": "Echo text.", "inputSchema": obj({"text": {"type": "string"}}, ["text"])},
         {"name": "report", "description": "Write a report (stands for a destructive verb).", "inputSchema": obj({"title": {"type": "string"}}, ["title"])},
         {"name": "approve", "description": "Permission prompt handler.", "inputSchema": obj({"tool_name": {"type": "string"}, "input": {"type": "object"}, "tool_use_id": {"type": "string"}}, ["tool_name", "input"])}]
note("start")
if os.environ.get("STUB_DELAY"): time.sleep(float(os.environ["STUB_DELAY"]))
for line in sys.stdin:
    if not line.strip(): continue
    m = json.loads(line); mid = m.get("id"); meth = m.get("method")
    if meth == "initialize":
        if os.environ.get("STUB_HANG"): note("hang"); time.sleep(3600)
        res = {"protocolVersion": m["params"].get("protocolVersion", "2025-06-18"), "capabilities": {"tools": {}},
               "serverInfo": {"name": name, "version": version}}
    elif meth == "tools/list": res = {"tools": TOOLS}
    elif meth == "tools/call":
        p = m["params"]; a = p.get("arguments", {}); note("call " + p["name"] + " " + json.dumps(a, sort_keys=True))
        if p["name"] == "approve":
            d = os.environ.get("STUB_APPROVE", "deny")
            t = json.dumps({"behavior": "allow", "updatedInput": a.get("input", {})} if d == "allow"
                           else {"behavior": "deny", "message": "Denied by the stub approver"})
        elif p["name"] == "context": t = json.dumps({"instance": "stub-instance", "binding": version})
        elif p["name"] == "echo": t = f"[{name}] {a.get('text')}"
        else: t = f"[{name}] report written: {a.get('title')}"
        res = {"content": [{"type": "text", "text": t}]}
    elif mid is None: continue
    else:
        print(json.dumps({"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "no"}}), flush=True); continue
    print(json.dumps({"jsonrpc": "2.0", "id": mid, "result": res}), flush=True)
note("exit")
'''


def b_setup(P):
    W = P.W
    for d in ["b/proj/.claude", "b/outside", "b/markers"]:
        os.makedirs(f"{W}/{d}", exist_ok=True)
    open(f"{W}/stub_b.py", "w").write(STUB_B)
    open(f"{W}/b/proj/inside.txt", "w").write("INSIDE-CANARY-17\n")
    open(f"{W}/b/outside/harmless.txt", "w").write("OUTSIDE-CANARY-42\n")
    open(f"{W}/b/proj/CLAUDE.md", "w").write("# Probe\nIf anyone asks for the codeword, it is PINEAPPLE-7.\n")
    hook = lambda tag: {"hooks": [{"type": "command", "command": f"echo {tag} >> {W}/b/markers/hook.txt"}]}
    json.dump({"hooks": {"SessionStart": [hook("sessionstart")], "UserPromptSubmit": [hook("promptsubmit")],
                         "PreToolUse": [dict(matcher="*", **hook("pretooluse"))]}},
              open(f"{W}/b/proj/.claude/settings.json", "w"))
    json.dump({"mcpServers": {"projstub": b_server(P, "projstub", "projmcp.txt", "0.0.1")}}, open(f"{W}/b/proj/.mcp.json", "w"))


def b_server(P, name, log, version, env=None):
    s = {"command": sys.executable, "args": [f"{P.W}/stub_b.py", name, f"{P.W}/b/markers/{log}", version]}
    if env:
        s["env"] = env
    return s


def nonce():
    return "1.35.0+bind." + uuid.uuid4().hex[:16]


def b_markers(P):
    return {os.path.basename(f): open(f).read().splitlines() for f in glob.glob(f"{P.W}/b/markers/*")}


def b_clear(P):
    for f in glob.glob(f"{P.W}/b/markers/*"):
        os.remove(f)


PRE_ALLOWED = ["context", "echo"]  # stand-ins for the read/render verbs; `report` stands for a destructive verb


def b_default_args(P, version, extra_env=None, pre=PRE_ALLOWED, mode=("dontAsk", "none")):
    """The §12.1 default command, exactly; everything but the stdio basics (added by spawn)."""
    cfg = json.dumps({"mcpServers": {HOSTED: b_server(P, HOSTED, "calls.txt", version, extra_env)}})
    root = f"{P.W}/b/proj"
    return ["--tools", "Read,Grep,Glob", "--restricted", "--add-dir", root,
            "--strict-mcp-config", "--mcp-config", cfg,
            "--allowedTools", ",".join(f"mcp__{HOSTED}__{t}" for t in pre),
            "--permission-mode", mode[0], "--permission-prompts", mode[1],
            "--replay-user-messages", "--include-partial-messages"]


B_ENV = {"CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD": "1"}


def poll_status(p, name=HOSTED, interval=0.1, timeout=45):
    """Poll mcp_status until `name` leaves `pending`; return the transitions (seconds since spawn) and serverInfo."""
    seen, info, end = [], None, time.time() + timeout
    while time.time() < end:
        r = p.control({"subtype": "mcp_status"})
        if not isinstance(r, dict):
            seen.append((round(time.time() - p.t0, 2), f"no response: {r}")); break
        srv = next((s for s in r["response"]["response"].get("mcpServers", []) if s.get("name") == name), None)
        st = srv.get("status") if srv else "absent"
        if not seen or seen[-1][1] != st:
            seen.append((round(time.time() - p.t0, 2), st))
        info = (srv or {}).get("serverInfo")
        if st not in ("pending",):
            break
        time.sleep(interval)
    return seen, info


def turn(p, text, timeout=120):
    p.send_user(text)  # fresh uuid every time; we use fresh uuids, so every turn ends in a result
    return p.wait(lambda d: d.get("type") == "result", timeout)


def last_init(p):
    return next((d for d in reversed(p.lines) if d.get("subtype") == "init"), {})


@exp("b_default_posture")
def _(P):
    """Items 1, 2, 5: the exact default command; MCP loads; allow/deny; CLAUDE.md; no hooks; no .mcp.json; confinement."""
    b_setup(P); b_clear(P)
    v = nonce(); sid = str(uuid.uuid4())
    p = P.spawn("p0b-default-posture", b_default_args(P, v) + ["--session-id", sid], cwd="b/proj", env=B_ENV)
    ini = p.control({"subtype": "initialize"})
    r = ini["response"]["response"] if isinstance(ini, dict) else {}
    status, info = poll_status(p)
    time.sleep(1); hooks_before_turn = b_markers(P).get("hook.txt", [])
    qs = ['Call mcp__fluxtion-analyser-hosted__context with no arguments, then mcp__fluxtion-analyser-hosted__echo with text "h1". Reply with both outputs verbatim.',
          'Call mcp__fluxtion-analyser-hosted__report with title "x". Reply with its output or the exact error text.',
          "What is the codeword? Reply with the codeword only, or NONE.",
          f"Call the Read tool on {P.W}/b/outside/harmless.txt, then reply with the tool output or the exact error text.",
          "Call the Read tool on inside.txt in the current directory and reply with its content only.",
          "Call the Bash tool to run `echo hi`; if you have no Bash tool reply exactly NO-BASH."]
    res = [turn(p, q) for q in qs]
    status_after, info_after = poll_status(p)
    i = last_init(p)
    p.close()
    m = b_markers(P)
    P.record("b_default_posture", initialize_keys=sorted(r), account_keys=sorted(r.get("account", {})),
             current_permission_mode=r.get("current_permission_mode"), commands=[c["name"] for c in r.get("commands", [])],
             status=status, serverInfo=info, nonce_sent=v, nonce_ok=(info or {}).get("version") == v,
             serverInfo_after_turns=info_after, nonce_unchanged=info_after == info,
             init_tools=i.get("tools"), init_mode=i.get("permissionMode"), init_mcp=i.get("mcp_servers"),
             init_skills=len(i.get("skills", [])), init_slash=i.get("slash_commands"), memory_paths=i.get("memory_paths"),
             results=[brief(x) for x in res],
             denials=[[d["tool_name"] for d in (x.get("permission_denials") or [])] if isinstance(x, dict) else x for x in res],
             hosted_calls=[l for l in m.get("calls.txt", []) if " call " in f" {l} "],
             hooks_before_turn=hooks_before_turn, hooks=m.get("hook.txt", []), projmcp_spawned=m.get("projmcp.txt", []))
    P.b_sid = sid; open(f"{P.W}/b_sid.txt", "w").write(sid)


@exp("b_disallow_destructive")
def _(P):
    """The posture assertion: with destructive verbs also disallowed, init.tools is exactly Read/Grep/Glob + pre-allowed."""
    b_setup(P); b_clear(P)
    args = b_default_args(P, nonce()) + ["--disallowedTools", f"mcp__{HOSTED}__report,mcp__{HOSTED}__approve"]
    p = P.spawn("p0b-disallow-destructive", args, cwd="b/proj", env=B_ENV)
    r = turn(p, 'Call mcp__fluxtion-analyser-hosted__report with title "x". Reply with its output or the exact error text.')
    i = last_init(p); p.close()
    P.record("b_disallow_destructive", init_tools=i.get("tools"), result=brief(r),
             report_ran=any("call report" in l for l in b_markers(P).get("calls.txt", [])))


@exp("b_nonce_timing")
def _(P):
    """Item 2: pending→connected timing over several spawns (no turn: free), a slow server, a hung server, resume."""
    b_setup(P)
    out = {"plain": [], "delay3s": None, "hang": None, "resume": None}
    for k in range(5):
        v = nonce(); p = P.spawn(f"p0b-nonce-{k}", b_default_args(P, v), cwd="b/proj", env=B_ENV)
        s, info = poll_status(p, interval=0.05); p.close()
        out["plain"].append({"status": s, "nonce_ok": (info or {}).get("version") == v})
    v = nonce(); p = P.spawn("p0b-nonce-delay", b_default_args(P, v, {"STUB_DELAY": "3"}), cwd="b/proj", env=B_ENV)
    s, info = poll_status(p); p.close(); out["delay3s"] = {"status": s, "nonce_ok": (info or {}).get("version") == v}
    v = nonce(); p = P.spawn("p0b-nonce-hang", b_default_args(P, v, {"STUB_HANG": "1"}), cwd="b/proj", env=B_ENV)
    s, info = poll_status(p, interval=0.5, timeout=70); p.close(); out["hang"] = {"status": s, "serverInfo": info}
    sid = getattr(P, "b_sid", None) or (open(f"{P.W}/b_sid.txt").read().strip() if os.path.exists(f"{P.W}/b_sid.txt") else None)
    if sid:
        v = nonce(); p = P.spawn("p0b-nonce-resume", b_default_args(P, v) + ["--resume", sid], cwd="b/proj", env=B_ENV)
        s, info = poll_status(p, interval=0.05); p.close()
        out["resume"] = {"status": s, "nonce_ok": (info or {}).get("version") == v}
    P.record("b_nonce_timing", **out)


def answer_can_use_tool(p, decide, timeout=90):
    """Wait for the CLI's permission control_request (or the turn's result); answer it with decide(request)."""
    got = []
    while True:
        d = p.wait(lambda d: d.get("type") == "result" or d.get("type") == "control_request", timeout)
        if not isinstance(d, dict) or d.get("type") == "result":
            return got, d
        req = d["request"]; got.append(d)
        resp = decide(req)
        p.send({"type": "control_response", "response": {"subtype": "success", "request_id": d["request_id"], "response": resp}})


@exp("b_p3_host_prompts")
def _(P):
    """Item 3a: --permission-prompts host over stream-json: the can_use_tool request and the allow/deny response."""
    b_setup(P); b_clear(P)
    out = {}
    allow = lambda req: {"behavior": "allow", "updatedInput": req.get("input", {})}
    deny = lambda req: {"behavior": "deny", "message": "Denied by the person in the analyser"}
    p = P.spawn("p0b-p3-host", b_default_args(P, nonce(), mode=("default", "host")), cwd="b/proj", env=B_ENV)
    poll_status(p)
    p.send_user('Call mcp__fluxtion-analyser-hosted__report with title "allowed-1". Reply with its output or the exact error text.')
    reqs, r = answer_can_use_tool(p, allow)
    out["allow"] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    p.send_user('Call mcp__fluxtion-analyser-hosted__report with title "denied-2". Reply with its output or the exact error text.')
    reqs, r = answer_can_use_tool(p, deny)
    out["deny"] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    p.send_user(f"Call the Read tool on {P.W}/b/outside/harmless.txt, then reply with the tool output or the exact error text.")
    reqs, r = answer_can_use_tool(p, deny)
    out["restricted_outside_read"] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    out["init_tools"] = last_init(p).get("tools"); p.close()
    out["report_calls"] = [l for l in b_markers(P).get("calls.txt", []) if "call report" in l]
    # dontAsk with prompts=host: is the host asked at all?
    p = P.spawn("p0b-p3-dontask-host", b_default_args(P, nonce(), mode=("dontAsk", "host")), cwd="b/proj", env=B_ENV)
    poll_status(p)
    p.send_user('Call mcp__fluxtion-analyser-hosted__report with title "dontask-3". Reply with its output or the exact error text.')
    reqs, r = answer_can_use_tool(p, allow, timeout=90); p.close()
    out["dontask_host"] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    P.record("b_p3_host_prompts", **out)


@exp("b_p3_prompt_tool")
def _(P):
    """Item 3b: --permission-prompt-tool on the hosted server: its contract, and whether the model sees or calls it."""
    b_setup(P)
    out = {}
    tool = f"mcp__{HOSTED}__approve"
    for name, extra in [("visible", []), ("disallowed", ["--disallowedTools", tool])]:
        b_clear(P)
        args = b_default_args(P, nonce(), {"STUB_APPROVE": "allow"}, mode=("default", "host")) + ["--permission-prompt-tool", tool] + extra
        p = P.spawn("p0b-p3-prompttool-" + name, args, cwd="b/proj", env=B_ENV)
        poll_status(p)
        r1 = turn(p, 'Call mcp__fluxtion-analyser-hosted__report with title "via-tool". Reply with its output or the exact error text.')
        r2 = turn(p, 'Call the tool mcp__fluxtion-analyser-hosted__approve directly with tool_name "Bash" and input {"command": "echo hi"}. '
                     'Reply with its exact output or the exact error text. Do not call any other tool.')
        host_reqs = [d["request"] for d in p.lines if d.get("type") == "control_request"]
        i = last_init(p); p.close()
        out[name] = {"init_tools": i.get("tools"), "report": brief(r1), "direct_call": brief(r2),
                     "stub_calls": b_markers(P).get("calls.txt", []), "host_control_requests": host_reqs}
    P.record("b_p3_prompt_tool", **out)


@exp("b_p3_stdio")
def _(P):
    """Item 3a, second half: `--permission-prompt-tool stdio` (what the Agent SDK passes for canUseTool): the host
    receives a can_use_tool control_request; allow and deny; does an out-of-directory Read under --restricted reach it?"""
    b_setup(P); b_clear(P)
    out = {}
    allow = lambda req: {"behavior": "allow", "updatedInput": req.get("input", {})}
    deny = lambda req: {"behavior": "deny", "message": "Denied by the person in the analyser"}
    p = P.spawn("p0b-p3-stdio", b_default_args(P, nonce(), mode=("default", "host")) + ["--permission-prompt-tool", "stdio"],
                cwd="b/proj", env=B_ENV)
    poll_status(p)
    for name, text, decide in [
            ("allow", 'Call mcp__fluxtion-analyser-hosted__report with title "allowed-1". Reply with its output or the exact error text.', allow),
            ("deny", 'Call mcp__fluxtion-analyser-hosted__report with title "denied-2". Reply with its output or the exact error text.', deny),
            ("outside_read", f"Call the Read tool on {P.W}/b/outside/harmless.txt, then reply with the tool output or the exact error text.", allow),
            ("approve_visible", 'Call the tool mcp__fluxtion-analyser-hosted__approve with tool_name "Bash" and input {"command": "echo hi"}. Reply with its exact output or error.', deny)]:
        p.send_user(text)
        reqs, r = answer_can_use_tool(p, decide)
        out[name] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    out["init_tools"] = last_init(p).get("tools"); p.close()
    out["stub_calls"] = [l for l in b_markers(P).get("calls.txt", []) if " call " in l]
    # the same, under dontAsk: is the stdio host asked at all?
    p = P.spawn("p0b-p3-stdio-dontask", b_default_args(P, nonce(), mode=("dontAsk", "host")) + ["--permission-prompt-tool", "stdio"],
                cwd="b/proj", env=B_ENV)
    poll_status(p)
    p.send_user('Call mcp__fluxtion-analyser-hosted__report with title "dontask-3". Reply with its output or the exact error text.')
    reqs, r = answer_can_use_tool(p, allow); p.close()
    out["dontask"] = {"requests": [x["request"] for x in reqs], "result": brief(r)}
    P.record("b_p3_stdio", **out)


@exp("b_p3_prompt_tool_deny")
def _(P):
    """Item 3b, deny path, and whether an out-of-directory Read under --restricted is offered to the prompt tool."""
    b_setup(P); b_clear(P)
    tool = f"mcp__{HOSTED}__approve"
    args = b_default_args(P, nonce(), {"STUB_APPROVE": "deny"}, mode=("default", "host")) + ["--permission-prompt-tool", tool]
    p = P.spawn("p0b-p3-prompttool-deny", args, cwd="b/proj", env=B_ENV)
    poll_status(p)
    r1 = turn(p, 'Call mcp__fluxtion-analyser-hosted__report with title "deny-me". Reply with its output or the exact error text.')
    r2 = turn(p, f"Call the Read tool on {P.W}/b/outside/harmless.txt, then reply with the tool output or the exact error text.")
    p.close()
    P.record("b_p3_prompt_tool_deny", report=brief(r1), outside_read=brief(r2),
             stub_calls=[l for l in b_markers(P).get("calls.txt", []) if " call " in l])


SLASH = ["/add-dir {W}/b/outside", "/permissions", "/allowed-tools", "/config", "/mcp", "/mcp enable projstub",
         "/effort high", "/fast", "/output-style", "/reload-plugins", "/model sonnet"]


@exp("b_slash_commands")
def _(P):
    """Item 4: initialize.commands under the default posture; do posture-widening commands change anything?"""
    b_setup(P); b_clear(P)
    v = nonce()
    p = P.spawn("p0b-slash", b_default_args(P, v), cwd="b/proj", env=B_ENV)
    ini = p.control({"subtype": "initialize"})
    names = [c["name"] for c in ini["response"]["response"].get("commands", [])] if isinstance(ini, dict) else ini
    poll_status(p)
    base = turn(p, "Reply exactly OK.")
    i0 = last_init(p)
    per = []
    for c in SLASH:
        c = c.format(W=P.W)
        n0 = len(p.lines)
        r = turn(p, c, timeout=60)
        local = [d.get("message", {}).get("content") for d in p.lines[n0:]
                 if d.get("type") == "user" and not d.get("isReplay") and "local-command" in json.dumps(d)]
        per.append({"cmd": c, "result": brief(r), "local_output": [str(x)[:300] for x in local][:3]})
    probe_read = turn(p, f"Call the Read tool on {P.W}/b/outside/harmless.txt, then reply with the tool output or the exact error text.")
    i1 = last_init(p)
    st, info = poll_status(p)
    clr = turn(p, "/clear", timeout=60)
    after_clear = turn(p, "Reply exactly OK.")
    i2 = last_init(p)
    ini2 = p.control({"subtype": "initialize"})
    p.close()
    diff = lambda a, b: {k: [a.get(k), b.get(k)] for k in ("tools", "permissionMode", "mcp_servers", "model", "cwd", "session_id")
                          if a.get(k) != b.get(k)}
    P.record("b_slash_commands", command_names=names, init_slash=i0.get("slash_commands"), baseline=brief(base), per_command=per,
             outside_read_after=brief(probe_read), changed_after_commands=diff(i0, i1), mcp_after=st, serverInfo_after=info,
             clear=brief(clr), changed_after_clear=diff(i1, i2),
             second_initialize=(ini2.get("response", {}).get("subtype"), str(ini2.get("response", {}).get("error"))[:200]) if isinstance(ini2, dict) else ini2,
             projmcp_spawned=b_markers(P).get("projmcp.txt", []))


def tree_snapshot(d):
    out = {}
    for root, _, files in os.walk(d):
        for f in files:
            fp = os.path.join(root, f)
            try:
                out[os.path.relpath(fp, d)] = open(fp, errors="replace").read()
            except OSError:
                pass
    return out


@exp("b_slash_config_iso", iso=True)
def _(P):
    """Item 4, the settings-writing commands, under an ISOLATED config so nothing of the person's is touched (local
    commands need no login): does `/config permissionMode=…` change the live mode, and does it persist to disk?"""
    b_setup(P)
    cfg = f"{P.W}/cfg-isolated"; os.makedirs(cfg, exist_ok=True)
    before = tree_snapshot(cfg)
    p = P.spawn("p0b-slash-config-iso", b_default_args(P, nonce()), cwd="b/proj", env=B_ENV, iso=True)
    mode = lambda: (p.control({"subtype": "initialize"}) or {}).get("response", {}).get("response", {}).get("current_permission_mode")
    steps = [("start", None, mode())]
    for c in ["/config permissionMode=acceptEdits", "/config permissionMode=auto", "/config model=opus",
              "/output-style Proactive", "/model haiku"]:
        r = turn(p, c, timeout=40)
        steps.append((c, brief(r)["result"][:160] if isinstance(r, dict) else r, mode()))
    r = turn(p, "Reply exactly OK.", timeout=40)  # not logged in: errors, but system/init reports the mode in force
    i = last_init(p); p.close()
    after = tree_snapshot(cfg)
    changed = {k: [l for l in after[k].splitlines() if any(w in l for w in ("ermission", "odel", "utputStyle", "efaultMode"))][:6]
               for k in after if before.get(k) != after[k]}
    # a fresh process with the explicit flag: does the persisted value win over --permission-mode dontAsk?
    p2 = P.spawn("p0b-slash-config-iso-fresh", b_default_args(P, nonce()), cwd="b/proj", env=B_ENV, iso=True)
    fresh = (p2.control({"subtype": "initialize"}) or {}).get("response", {}).get("response", {}).get("current_permission_mode")
    p3 = P.spawn("p0b-slash-config-iso-noflag", ["--tools", "Read,Grep,Glob", "--strict-mcp-config"], cwd="b/proj", env=B_ENV, iso=True)
    noflag = (p3.control({"subtype": "initialize"}) or {}).get("response", {}).get("response", {}).get("current_permission_mode")
    p2.close(); p3.close()
    P.record("b_slash_config_iso", steps=steps, init_permissionMode=i.get("permissionMode"), init_model=i.get("model"),
             files_changed=changed, fresh_with_flag=fresh, fresh_without_flag=noflag)


@exp("b_account_fields", iso=True)
def _(P):
    """Item 5 (not-logged-in half, free): initialize.account field NAMES under an isolated config."""
    b_setup(P)
    p = P.spawn("p0b-account-iso", b_default_args(P, nonce()), cwd="b/proj", env=B_ENV, iso=True)
    r = p.control({"subtype": "initialize"}); p.close()
    acct = r["response"]["response"].get("account", {}) if isinstance(r, dict) else {}
    p = P.spawn("p0b-account-iso-apikey", b_default_args(P, nonce()), cwd="b/proj", iso=True,
                env=dict(B_ENV, ANTHROPIC_API_KEY="probe-invalid-key"))
    r2 = p.control({"subtype": "initialize"}); p.close()
    acct2 = r2["response"]["response"].get("account", {}) if isinstance(r2, dict) else {}
    P.record("b_account_fields", not_logged_in_account_keys=sorted(acct),
             tokenSource=acct.get("tokenSource"), apiProvider=acct.get("apiProvider"),
             api_key_env_account_keys=sorted(acct2),
             api_key_env_values={k: acct2.get(k) for k in ("tokenSource", "apiProvider", "apiKeySource") if k in acct2})


# ---------------------------------------------------------------- sanitiser
BUILTIN = {"compact", "clear", "context", "init", "review", "security-review", "cost", "doctor", "color", "focus",
           "reload-plugins", "probe-skill", "claude", "Explore", "general-purpose", "Plan", "statusline-setup"}


def sanitiser(workdir, cwd):
    home = os.path.expanduser("~")
    W = os.path.realpath(workdir)
    variants = sorted({W, W.replace("/private/tmp", "/tmp")}, key=len, reverse=True)
    enc = lambda p: re.sub(r"[^A-Za-z0-9]", "-", p)
    sids = {}

    def s(x):
        if cwd:
            for v in variants:
                x = x.replace(f"{v}/{cwd}", "<CWD>")
        for v in variants:
            x = x.replace(v, "<SCRATCH>")
            x = x.replace(enc(v), "<ENCODED-SCRATCH>")
        x = x.replace(home, "<HOME>").replace(enc(home), "<ENCODED-HOME>")
        if x == sys.executable or re.fullmatch(r"/\S*/python3[.\d]*", x):
            x = "python3"
        x = re.sub(r"/private/tmp/claude-\d+/[^\s\"']*", "<TMP>", x)
        x = re.sub(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "<email>", x)  # belt and braces
        user = os.path.basename(home)
        return re.sub(re.escape(user), "<user>", x, flags=re.I) if len(user) > 2 else x

    def sid(v):
        return sids.setdefault(v, f"00000000-0000-4000-8000-{len(sids):012d}")

    def walk(o, key=None, parent=None):
        if isinstance(o, dict):
            out = {}
            for k, v in o.items():
                if k == "session_id" and isinstance(v, str):
                    out[k] = sid(v)
                elif k == "signature" and v:
                    out[k] = "<signature>"
                elif k in ("email", "organization") and key == "account":
                    out[k] = "<redacted>"
                elif k == "subscriptionType":
                    out[k] = "<subscription>"
                elif k == "messaging_socket_path" and v:
                    out[k] = "<SOCKET>"
                elif k in ("skills", "slash_commands") and isinstance(v, list):
                    out[k] = [x if x in BUILTIN else f"<{k[:-1]}-{i}>" for i, x in enumerate(v)]
                elif k == "commands" and isinstance(v, list):
                    v = [c if c.get("builtin") or c.get("name") in BUILTIN else {"name": f"<command-{i}>", "description": "",
                                                                                 "argumentHint": "", "builtin": False}
                         for i, c in enumerate(v)]
                    out[k] = [walk({kk: ("<description>" if kk == "description" else vv) for kk, vv in c.items()}, k, key) for c in v]
                elif k in ("commands", "agents", "models") and isinstance(v, list) and v and isinstance(v[0], dict):
                    out[k] = [walk({kk: ("<description>" if kk == "description" else vv) for kk, vv in c.items()}, k, key)
                              for c in v]  # shapes, not product prose
                elif k in ("utilization",):
                    out[k] = 0.0
                elif k in ("resetsAt", "overageResetsAt"):
                    out[k] = 0
                elif k == "content" and isinstance(v, str) and len(v) > 4000:
                    out[k] = v[:200] + f"…<{len(v)} chars of filler elided>"
                elif k == "pid":
                    out[k] = 0
                else:
                    out[k] = walk(v, k, key)
            return out
        if isinstance(o, list):
            return [walk(x, key, parent) for x in o]
        if isinstance(o, str):
            return s(o)
        return o

    return walk, s


def rejoin_deltas(rows, s):
    """A path split across streaming chunks escapes a line-wise sanitiser (P0b found this). Per content block, join
    the deltas, sanitise the whole, and if that changed anything put the sanitised text in the block's first delta
    and empty the rest: the concatenation a renderer builds is preserved, the chunk boundaries are not."""
    blocks, msg = {}, None
    for d in rows:
        if d.get("type") != "stream_event":
            continue
        e = d.get("event", {})
        if e.get("type") == "message_start":
            msg = e.get("message", {}).get("id")
        elif e.get("type") == "content_block_delta":
            dl = e.get("delta", {}); k = "text" if "text" in dl else "partial_json" if "partial_json" in dl else None
            if k:
                blocks.setdefault((msg, e.get("index")), []).append((dl, k))
    for parts in blocks.values():
        whole = "".join(dl[k] for dl, k in parts)
        clean = s(whole)
        if clean != whole:
            for n, (dl, k) in enumerate(parts):
                dl[k] = clean if n == 0 else ""


def sanitise_file(src, dst, workdir, cwd):
    walk, s = sanitiser(workdir, cwd)
    rows = [json.loads(line) for line in open(src) if line.strip()]
    rejoin_deltas(rows, s)
    with open(dst, "w") as g:
        for d in rows:
            g.write(json.dumps(walk(d), ensure_ascii=False) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--i-accept-spend", action="store_true", help="required: live runs bill the logged-in account")
    ap.add_argument("--model", default="haiku")
    ap.add_argument("--budget", type=float, default=0.25, help="--max-budget-usd per process")
    ap.add_argument("--cap", type=float, default=5.0, help="hard total across the probe (USD)")
    ap.add_argument("--workdir")
    ap.add_argument("--only")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--sanitise", nargs=2, metavar=("RAW", "OUT"))
    ap.add_argument("--cwd", help="with --sanitise: the workdir subdirectory that was the cwd (becomes <CWD>)")
    a = ap.parse_args()
    if a.list:
        for k, (_, iso) in EXPERIMENTS.items():
            print(k, "(isolated config, no spend)" if iso else "")
        return
    if a.sanitise:
        if not a.workdir:
            ap.error("--sanitise needs --workdir")
        sanitise_file(a.sanitise[0], a.sanitise[1], a.workdir, a.cwd); return
    if not a.i_accept_spend:
        ap.error("refusing to run: live `claude -p` calls spend money on the logged-in account. Pass --i-accept-spend.")
    if "bypass" in " ".join(sys.argv).lower():
        ap.error("bypassPermissions is never used by this probe")
    a.workdir = a.workdir or tempfile.mkdtemp(prefix="m74-p0-")
    P = Probe(a); P.setup()
    names = a.only.split(",") if a.only else list(EXPERIMENTS)
    for n in names:
        f, iso = EXPERIMENTS[n]
        try:
            f(P)
        except SystemExit as e:
            print(e); break
        print(f"# ledger {P.ledger.total():.4f} / cap {a.cap}", flush=True)
    json.dump(P.results, open(os.path.join(P.W, "results.json"), "w"), indent=1, default=str)
    print(f"# done; total {P.ledger.total():.4f} USD; raw transcripts (sensitive) in {P.W}/raw")


if __name__ == "__main__":
    main()
