#!/usr/bin/env python3
"""M74 P1b — the full-posture probes PB-1…PB-4 (spec-claude-code-provider.md §8.1), reproducible.

  python3 pb.py --list
  python3 pb.py --workdir DIR --only pb2_path_iso,pb4_loading_iso,pb1_modes_iso      (isolated: no login, no spend)
  python3 pb.py --workdir DIR --i-accept-spend --cap 1.0 --only pb3_auto_real,pb1_bash_real   (real login)
  python3 pb.py --sanitise RAW.jsonl OUT.jsonl --workdir DIR [--cwd SUBDIR]

The full-posture command line is §4.3's, built here from the same pieces as P0b's stub (`STUB_B` in
../m74-p0/probe.py stands for the hosted bridge). OD-74.18 decides the method:

* `*_iso` experiments run under an isolated CLAUDE_CONFIG_DIR **and** an isolated HOME, with planted user and project
  skills, commands, hooks and servers. Not logged in: `initialize`, `mcp_status`, `SessionStart` hooks and a turn's
  `system/init` all happen without a login, so they cost nothing and can see nothing of the person's.
* `*_real` experiments use the real login, the full line PLUS `--strict-mcp-config` (none of the person's servers or
  connectors load), `--max-budget-usd` on every process, a ledger capped by --cap. Raw transcripts of these list the
  person's skills, plugins and agents and are NEVER committed: only the report's observations are.

Never passes bypassPermissions or dontAsk to a full-posture line; never runs `/config`.
"""
import argparse, glob, importlib.util, json, os, queue, shutil, subprocess, sys, threading, time, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("p0probe", os.path.join(HERE, "..", "m74-p0", "probe.py"))
P0 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(P0)

HOSTED = "fluxtion-analyser-hosted"
GUI_PATH = "/usr/bin:/bin:/usr/sbin:/sbin"          # macOS's minimal GUI PATH [P0-38]
MODES = ["default", "acceptEdits", "auto", "plan"]
FULL_NOTE = ("You are running inside the Fluxtion Audit Log Analyser, which started you with the person's full Claude "
             "Code. Call the analyser_context tool first: it says what is open.")

NODE_MCP = r'''#!/usr/bin/env node
// a stdio MCP server in node: it can start only if `node` resolves on the child's PATH (#!/usr/bin/env node)
const fs = require("fs"); const log = process.argv[2]; const name = process.argv[3] || "pb2-node";
fs.appendFileSync(log, name + " start\n");
let buf = "";
process.stdin.on("data", d => { buf += d; let i; while ((i = buf.indexOf("\n")) >= 0) { const l = buf.slice(0, i); buf = buf.slice(i + 1);
  if (!l.trim()) continue; const m = JSON.parse(l); if (m.id === undefined) continue; let r;
  if (m.method === "initialize") r = {protocolVersion: m.params.protocolVersion || "2025-06-18", capabilities: {tools: {}}, serverInfo: {name, version: "0.0.1"}};
  else if (m.method === "tools/list") r = {tools: [{name: "ping", description: "Ping.", inputSchema: {type: "object", properties: {}}}]};
  else { process.stdout.write(JSON.stringify({jsonrpc: "2.0", id: m.id, error: {code: -32601, message: "no"}}) + "\n"); continue; }
  process.stdout.write(JSON.stringify({jsonrpc: "2.0", id: m.id, result: r}) + "\n"); } });
'''


class Ledger(P0.Ledger):
    pass


class Proc:
    """P0's Proc, with an absolute executable (a minimal PATH cannot find `claude`) and a caller-built environment."""

    def __init__(self, ctx, name, exe, args, cwd, env):
        self.ctx, self.name = ctx, name
        self.raw = open(os.path.join(ctx.W, "raw", name + ".jsonl"), "w")
        self.err = open(os.path.join(ctx.W, "raw", name + ".err"), "w")
        self.p = subprocess.Popen([exe] + args, cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=self.err, text=True, bufsize=1, start_new_session=True)
        self.q, self.lines, self.cost = queue.Queue(), [], 0.0
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
                self.ctx.ledger.add(self.name, max(0.0, c - self.cost)); self.cost = c
            self.q.put(d)
        self.q.put(None)

    send = P0.Proc.send
    send_user = P0.Proc.send_user
    control = P0.Proc.control
    wait = P0.Proc.wait
    result = P0.Proc.result
    close = P0.Proc.close

    def events(self, *types):
        return [d for d in self.lines if d.get("type") in types or d.get("subtype") in types]


class Ctx:
    def __init__(self, a):
        self.a = a
        self.W = os.path.realpath(a.workdir)
        os.makedirs(f"{self.W}/raw", exist_ok=True)
        self.ledger = Ledger(os.path.join(self.W, "ledger.json"), a.cap)
        self.results = {}
        self.claude = os.path.realpath(shutil.which("claude") or os.path.expanduser("~/.local/bin/claude"))
        open(f"{self.W}/stub_b.py", "w").write(P0.STUB_B)

    def record(self, key, **obs):
        self.results[key] = obs
        s = json.dumps({"experiment": key, **obs}, default=str).replace(self.W, "<W>")
        print(s.replace(os.path.expanduser("~"), "<HOME>")[:6000], flush=True)

    def hosted(self, version, log="calls.txt"):
        return {"command": sys.executable, "args": [f"{self.W}/stub_b.py", HOSTED, f"{self.W}/markers/{log}", version]}

    def full_args(self, mode, strict, sid=None, budget=None, model=None):
        """§4.3's full line (everything after `claude`). `strict` is the HARNESS's addition (OD-74.18), never the
        analyser's."""
        assert mode in MODES, mode
        budget = self.a.budget if budget is None else budget
        cfg = json.dumps({"mcpServers": {HOSTED: self.hosted("1.35.0+bind." + uuid.uuid4().hex[:16])}})
        a = ["-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
             "--include-partial-messages", "--replay-user-messages",
             "--session-id", sid or str(uuid.uuid4()), "--model", model or self.a.model,
             "--mcp-config", cfg,
             "--allowedTools", f"mcp__{HOSTED}__context,mcp__{HOSTED}__echo",
             "--disallowedTools", f"mcp__fluxtion-analyser,mcp__{HOSTED}__report,mcp__{HOSTED}__approve",
             "--permission-mode", mode, "--permission-prompts", "none",
             "--max-budget-usd", str(budget), "--append-system-prompt", FULL_NOTE]
        return a + (["--strict-mcp-config"] if strict else [])

    def env(self, iso, path=None, home=None):
        e = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE")}   # never nest inside a host session
        e.pop("ANTHROPIC_API_KEY", None)
        if iso:
            e["CLAUDE_CONFIG_DIR"] = f"{self.W}/cfg"
        if home:
            e["HOME"] = home
        if path is not None:
            e["PATH"] = path
        return e

    def spawn(self, name, args, cwd, iso, path=None, home=None, budget=None):
        if not iso:
            b = budget if budget is not None else self.a.budget
            if not self.a.i_accept_spend:
                raise SystemExit("refusing: a real-login run spends money; pass --i-accept-spend")
            if self.ledger.total() + b > self.a.cap:
                raise SystemExit(f"STOP: ledger {self.ledger.total():.4f} + {b} would exceed cap {self.a.cap}")
            if "--strict-mcp-config" not in args:
                raise SystemExit("refusing: a real-login run must carry --strict-mcp-config (OD-74.18)")
        joined = " ".join(args)
        assert "bypassPermissions" not in joined and "dangerously" not in joined
        assert "dontAsk" not in joined or "--restricted" in args      # dontAsk only on the default posture's control
        return Proc(self, name, self.claude, args, cwd, self.env(iso, path, home))

    def markers(self):
        return {os.path.basename(f): open(f).read().splitlines() for f in glob.glob(f"{self.W}/markers/*")}


def init_req(p):
    r = p.control({"subtype": "initialize"})
    return r["response"]["response"] if isinstance(r, dict) else {"_error": r}


def poll_all(p, timeout=40):
    """mcp_status until no server is pending; returns {name: status} and the transitions."""
    end, seen = time.time() + timeout, []
    while time.time() < end:
        r = p.control({"subtype": "mcp_status"})
        if not isinstance(r, dict):
            return {}, seen + [("no response", r)]
        st = {s.get("name"): s.get("status") for s in r["response"]["response"].get("mcpServers", [])}
        if not seen or seen[-1][1] != st:
            seen.append((round(time.time() - p.t0, 2), st))
        if "pending" not in st.values():
            return st, seen
        time.sleep(0.25)
    return st, seen


def last_init(p):
    return next((d for d in reversed(p.lines) if d.get("subtype") == "init"), {})


EXPERIMENTS = {}


def exp(name):
    def deco(f):
        EXPERIMENTS[name] = f
        return f
    return deco


# ---------------------------------------------------------------- PB-2: the child's PATH (isolated, free)
@exp("pb2_path_iso")
def _(C):
    """Does a planted SessionStart hook see OD-74.11's appended PATH, and does a planted stdio server launched as `npx …`
    (and one `#!/usr/bin/env node` script) start? Control: the bare minimal GUI PATH."""
    W = C.W
    home = f"{W}/pb2-home"
    for d in ["markers", "cfg", f"pb2-home/.local/bin", "pb2/proj/.claude", "pb2/pkg/bin"]:
        os.makedirs(f"{W}/{d}" if not d.startswith("pb2-home") else f"{W}/{d}", exist_ok=True)
    marker = f"{home}/.local/bin/pb2-marker"
    open(marker, "w").write("#!/bin/sh\necho PB2-MARKER-RAN\n"); os.chmod(marker, 0o755)
    nodemcp = f"{home}/.local/bin/pb2-node-mcp"
    open(nodemcp, "w").write(NODE_MCP); os.chmod(nodemcp, 0o755)
    json.dump({"name": "pb2-mcp-stub", "version": "1.0.0", "bin": {"pb2-mcp-stub": "bin/stub.js"}},
              open(f"{W}/pb2/pkg/package.json", "w"))
    open(f"{W}/pb2/pkg/bin/stub.js", "w").write(NODE_MCP); os.chmod(f"{W}/pb2/pkg/bin/stub.js", 0o755)
    hook = lambda tag: {"hooks": [{"type": "command", "command":
                                   f'printf "{tag} marker=%s npx=%s node=%s\\n" "$(command -v pb2-marker || echo MISSING)" '
                                   f'"$(command -v npx || echo MISSING)" "$(command -v node || echo MISSING)" '
                                   f'>> {W}/markers/pb2-hook.txt'}]}
    json.dump({"hooks": {"SessionStart": [hook("project")]}}, open(f"{W}/pb2/proj/.claude/settings.json", "w"))
    json.dump({"hooks": {"SessionStart": [hook("user")]}}, open(f"{W}/cfg/settings.json", "w"))
    srv_env = {"npm_config_update_notifier": "false", "npm_config_cache": f"{home}/.npm"}
    json.dump({"mcpServers": {
        "pb2-npx": {"command": "npx", "args": ["--offline", "--yes", f"{W}/pb2/pkg", f"{W}/markers/pb2-mcp.txt", "pb2-npx"],
                    "env": srv_env},
        "pb2-envnode": {"command": "pb2-node-mcp", "args": [f"{W}/markers/pb2-mcp.txt", "pb2-envnode"]}}},
        open(f"{W}/pb2/proj/.mcp.json", "w"))
    appended = [d for d in [f"{home}/.local/bin", "/opt/homebrew/bin", "/usr/local/bin",
                            (os.environ.get("NPM_CONFIG_PREFIX") or "") + "/bin" if os.environ.get("NPM_CONFIG_PREFIX") else None,
                            f"{home}/.npm-global/bin"]
                if d and os.path.isdir(d) and d not in GUI_PATH.split(":")]
    out = {"appended": appended}
    for label, path in [("gui_path_only", GUI_PATH), ("gui_path_plus_od_74_11", GUI_PATH + ":" + ":".join(appended))]:
        for f in glob.glob(f"{W}/markers/pb2-*"):
            os.remove(f)
        p = C.spawn(f"pb-path-{label}", C.full_args("default", strict=False), f"{W}/pb2/proj", iso=True, path=path,
                    home=home)
        ini = init_req(p)
        st, seen = poll_all(p)
        time.sleep(1.5)
        hooks = [{k: d.get(k) for k in ("subtype", "hook_name", "hook_event", "outcome", "exit_code") if k in d}
                 for d in p.events("hook_started", "hook_response")]
        p.close(timeout=20)
        out[label] = {"current_permission_mode": ini.get("current_permission_mode"), "servers": st, "transitions": seen,
                      "hook_events": hooks, "hook_lines": C.markers().get("pb2-hook.txt", []),
                      "mcp_started": C.markers().get("pb2-mcp.txt", [])}
    C.record("pb2_path_iso", **out)


# ---------------------------------------------------------------- PB-4: the person's skills, commands, CLAUDE.md (iso)
@exp("pb4_loading_iso")
def _(C):
    """Under the full line: initialize.commands lists planted builtin:false user and project commands; system/init.skills
    lists planted user and project skills; memory_paths lists the folder's CLAUDE.md with no env flag. Control: the
    same folder with the default posture's confining flags."""
    W = C.W
    for d in ["markers", "cfg/skills/pb4-user-skill", "cfg/commands", "cfg/agents", "pb4/proj/.claude/skills/pb4-proj-skill",
              "pb4/proj/.claude/commands"]:
        os.makedirs(f"{W}/{d}", exist_ok=True)
    sk = lambda n: f"---\nname: {n}\ndescription: a planted probe skill\n---\nReply with exactly: {n.upper()}-RAN\n"
    open(f"{W}/cfg/skills/pb4-user-skill/SKILL.md", "w").write(sk("pb4-user-skill"))
    open(f"{W}/pb4/proj/.claude/skills/pb4-proj-skill/SKILL.md", "w").write(sk("pb4-proj-skill"))
    open(f"{W}/cfg/commands/pb4-user-cmd.md", "w").write("---\ndescription: planted user command\n---\nReply PB4-USER-CMD\n")
    open(f"{W}/pb4/proj/.claude/commands/pb4-proj-cmd.md", "w").write("---\ndescription: planted project command\n---\nReply PB4-PROJ-CMD\n")
    open(f"{W}/cfg/agents/pb4-agent.md", "w").write("---\nname: pb4-agent\ndescription: planted agent\n---\nYou are a probe.\n")
    open(f"{W}/pb4/proj/CLAUDE.md", "w").write("# Probe\nIf anyone asks for the codeword, it is KIWI-PB4.\n")
    open(f"{W}/cfg/CLAUDE.md", "w").write("# User probe memory\n")
    if os.path.exists(f"{W}/cfg/settings.json"):
        os.remove(f"{W}/cfg/settings.json")          # PB-2's hooks are not part of this experiment
    out = {}
    default_line = ["-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
                    "--model", C.a.model, "--tools", "Read,Grep,Glob", "--restricted", "--add-dir", f"{W}/pb4/proj",
                    "--strict-mcp-config", "--permission-mode", "dontAsk", "--permission-prompts", "none",
                    "--max-budget-usd", "0.01"]
    for label, args in [("full", C.full_args("default", strict=False)), ("default_posture_control", default_line)]:
        p = C.spawn(f"pb-loading-{label}", args, f"{W}/pb4/proj", iso=True)
        ini = init_req(p)
        cmds = ini.get("commands", [])
        p.send_user("hello")                       # not logged in: system/init is still emitted, then an auth error
        p.result(60)
        i = last_init(p)
        p.close(timeout=20)
        out[label] = {"own_commands": sorted(c["name"] for c in cmds if not c.get("builtin")),
                      "builtin_count": sum(1 for c in cmds if c.get("builtin")),
                      "pb4_skills": sorted(s for s in i.get("skills", []) if "pb4" in s),
                      "pb4_slash": sorted(s for s in i.get("slash_commands", []) if "pb4" in s),
                      "pb4_agents": sorted(a for a in i.get("agents", []) if "pb4" in str(a)),
                      "memory_paths": i.get("memory_paths"), "permissionMode": i.get("permissionMode")}
    C.record("pb4_loading_iso", **out)


# ---------------------------------------------------------------- PB-1 (isolated half): modes, the hidden registration
@exp("pb1_modes_iso")
def _(C):
    """Each of the four modes under the full line: initialize.current_permission_mode and system/init.permissionMode; a
    planted user-scope `fluxtion-analyser` server's tools hidden by --disallowedTools mcp__fluxtion-analyser (witness:
    the same line without that entry)."""
    W = C.W
    os.makedirs(f"{W}/markers", exist_ok=True); os.makedirs(f"{W}/pb1/proj", exist_ok=True)
    if os.path.exists(f"{W}/cfg/settings.json"):
        os.remove(f"{W}/cfg/settings.json")
    env = C.env(True)
    reg = subprocess.run([C.claude, "mcp", "add", "--scope", "user", "fluxtion-analyser", "--", sys.executable,
                          f"{W}/stub_b.py", "fluxtion-analyser", f"{W}/markers/user-reg.txt", "1.35.0"],
                         env=env, cwd=f"{W}/pb1/proj", capture_output=True, text=True)
    out = {"registered": reg.returncode == 0}
    for mode in MODES:
        p = C.spawn(f"pb-mode-{mode}-iso", C.full_args(mode, strict=False), f"{W}/pb1/proj", iso=True)
        ini = init_req(p)
        st, _ = poll_all(p)
        p.send_user("hello"); p.result(60)
        i = last_init(p)
        p.close(timeout=20)
        tools = i.get("tools", [])
        out[mode] = {"initialize_mode": ini.get("current_permission_mode"), "init_mode": i.get("permissionMode"),
                     "servers": st,
                     "hosted_tools": sorted(t for t in tools if t.startswith(f"mcp__{HOSTED}__")),
                     "registration_tools": sorted(t for t in tools if t.startswith("mcp__fluxtion-analyser__")),
                     "builtin_tool_count": sum(1 for t in tools if not t.startswith("mcp__"))}
    # witness: without mcp__fluxtion-analyser in the disallow list the person's registration is visible
    args = C.full_args("default", strict=False)
    k = args.index("--disallowedTools") + 1
    args[k] = ",".join(x for x in args[k].split(",") if x != "mcp__fluxtion-analyser")
    p = C.spawn("pb-mode-witness-iso", args, f"{W}/pb1/proj", iso=True)
    p.send_user("hello"); p.result(60)
    i = last_init(p); p.close(timeout=20)
    out["witness_without_disallow"] = sorted(t for t in i.get("tools", []) if t.startswith("mcp__fluxtion-analyser__"))
    C.record("pb1_modes_iso", **out)


# ---------------------------------------------------------------- the hook-event fixture (isolated, free)
@exp("pbx_hooks_fixture_iso")
def _(C):
    """A fixture for the hook status lines: a user SessionStart hook that succeeds and prints a marker, a project
    SessionStart hook that fails (exit 1) printing markers to stdout and stderr, and a UserPromptSubmit hook; one turn
    (not logged in). The markers must never reach the analyser's store or snapshot."""
    W = C.W
    for d in ["markers", "cfg", "pbx/proj/.claude"]:
        os.makedirs(f"{W}/{d}", exist_ok=True)
    cmd = lambda c: {"hooks": [{"type": "command", "command": c}]}
    json.dump({"hooks": {"SessionStart": [cmd("echo HOOK-STDOUT-MARKER-USER")]}}, open(f"{W}/cfg/settings.json", "w"))
    json.dump({"hooks": {"SessionStart": [cmd("echo HOOK-STDOUT-MARKER-FAIL; echo HOOK-STDERR-MARKER-FAIL >&2; exit 1")],
                         "UserPromptSubmit": [cmd("echo HOOK-STDOUT-MARKER-PROMPT")]}},
              open(f"{W}/pbx/proj/.claude/settings.json", "w"))
    p = C.spawn("pb-hooks-iso", C.full_args("default", strict=False), f"{W}/pbx/proj", iso=True)
    init_req(p)
    poll_all(p)
    time.sleep(1.5)
    p.send_user("hello"); p.result(60)
    p.close(timeout=20)
    os.remove(f"{W}/cfg/settings.json")
    C.record("pbx_hooks_fixture_iso", hooks=[{k: d.get(k) for k in ("subtype", "hook_event", "outcome", "exit_code")}
                                             for d in p.events("hook_started", "hook_response")])


# ---------------------------------------------------------------- PB-3: is `auto` available (real login, no turn)
@exp("pb3_auto_real")
def _(C):
    W = C.W
    os.makedirs(f"{W}/markers", exist_ok=True); os.makedirs(f"{W}/real/proj", exist_ok=True)
    out = {}
    for mode in ["auto", "default"]:
        p = C.spawn(f"pb-auto-{mode}-real", C.full_args(mode, strict=True, budget=0.05), f"{W}/real/proj", iso=False,
                    budget=0.05)
        ini = init_req(p)
        st, _ = poll_all(p)
        time.sleep(0.5)
        acct = ini.get("account", {})
        out[mode] = {"initialize_mode": ini.get("current_permission_mode"),
                     "hosted_status": st.get(HOSTED), "other_servers": len([s for s in st if s != HOSTED]),
                     "logged_in": bool(acct.get("subscriptionType") or acct.get("apiKeySource")),
                     "hook_events": len(p.events("hook_started")),
                     "stderr_auto_lines": None, "exit_before_close": p.p.poll()}
        p.close(timeout=20)
        err = open(f"{W}/raw/pb-auto-{mode}-real.err").read()
        out[mode]["stderr_auto_lines"] = [l[:200] for l in err.splitlines() if "auto" in l.lower()][:5]
    C.record("pb3_auto_real", **out)


# ---------------------------------------------------------------- PB-1 (real half) + PB-4's CLAUDE.md turn
@exp("pb1_bash_real")
def _(C):
    """One Bash-asking turn per mode under the full line + the harness's --strict-mcp-config: is the request denied
    WITHOUT BLOCKING (a result arrives) with a permission_denied event; does the folder's CLAUDE.md load (codeword)."""
    W = C.W
    os.makedirs(f"{W}/markers", exist_ok=True); os.makedirs(f"{W}/real/proj", exist_ok=True)
    open(f"{W}/real/proj/CLAUDE.md", "w").write("# Probe\nIf anyone asks for the codeword, it is MANGO-PB1.\n")
    out = {}
    for mode in MODES:
        mk = f"{W}/markers/pb1-{mode}.txt"
        if os.path.exists(mk):
            os.remove(mk)
        # PB-3: with haiku `auto` falls back to `default`, so the auto turn runs on sonnet to be auto at all
        model = "sonnet" if mode == "auto" else None
        p = C.spawn(f"pb-bash-{mode}-real", C.full_args(mode, strict=True, model=model), f"{W}/real/proj", iso=False)
        ini = init_req(p)
        t0 = time.time()
        p.send_user("First line of your reply: the codeword from CLAUDE.md, or NONE. Then use the Bash tool to run exactly "
                    f"`touch {mk} && echo PB1-BASH-{mode.upper()}` and report its output or the exact error text. "
                    "Do not use any other tool and do not retry.")
        r = p.result(150)
        dt = round(time.time() - t0, 1)
        i = last_init(p)
        denied = [d for d in p.lines if d.get("subtype") == "permission_denied" or d.get("type") == "permission_denied"]
        ctl = [d for d in p.lines if d.get("type") == "control_request"]
        texts = []
        for d in p.lines:
            if d.get("type") == "assistant":
                for b in d.get("message", {}).get("content", []):
                    if b.get("type") == "text":
                        texts.append(b["text"][:300])
                    elif b.get("type") == "tool_use":
                        texts.append("TOOL_USE " + b.get("name", ""))
        p.close(timeout=30)
        out[mode] = {"initialize_mode": ini.get("current_permission_mode"), "init_mode": i.get("permissionMode"),
                     "seconds_to_result": dt, "result": P0.brief(r) if isinstance(r, dict) else r,
                     "permission_denied_events": [{k: d.get(k) for k in ("tool_name", "decision_reason_type",
                                                                         "decision_reason") if k in d} for d in denied],
                     "control_requests": len(ctl), "marker_created": os.path.exists(mk),
                     "codeword_seen": any("MANGO-PB1" in t for t in texts), "assistant": texts[:6],
                     "hosted_tools": sorted(t for t in i.get("tools", []) if t.startswith(f"mcp__{HOSTED}__")),
                     "registration_tools": sorted(t for t in i.get("tools", []) if t.startswith("mcp__fluxtion-analyser__")),
                     "servers": [s.get("name") for s in i.get("mcp_servers", [])]}
        print(f"# ledger {C.ledger.total():.4f}", flush=True)
    C.record("pb1_bash_real", **out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--i-accept-spend", action="store_true")
    ap.add_argument("--model", default="haiku")
    ap.add_argument("--budget", type=float, default=0.25)
    ap.add_argument("--cap", type=float, default=1.0)
    ap.add_argument("--workdir")
    ap.add_argument("--only")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--sanitise", nargs=2, metavar=("RAW", "OUT"))
    ap.add_argument("--cwd")
    a = ap.parse_args()
    if a.list:
        for k in EXPERIMENTS:
            print(k, "(real login: spends)" if k.endswith("_real") else "(isolated: no login, no spend)")
        return
    if a.sanitise:
        P0.sanitise_file(a.sanitise[0], a.sanitise[1], a.workdir, a.cwd)
        return
    if "bypass" in " ".join(sys.argv).lower():
        ap.error("bypassPermissions is never used by this probe")
    if not a.workdir:
        ap.error("--workdir is required")
    C = Ctx(a)
    for n in (a.only.split(",") if a.only else list(EXPERIMENTS)):
        try:
            EXPERIMENTS[n](C)
        except SystemExit as e:
            print(e); break
        print(f"# ledger {C.ledger.total():.4f} / cap {a.cap}", flush=True)
    prev = {}
    rp = os.path.join(C.W, "results.json")
    if os.path.exists(rp):
        prev = json.load(open(rp))
    prev.update(C.results)
    json.dump(prev, open(rp, "w"), indent=1, default=str)
    print(f"# done; total {C.ledger.total():.4f} USD")


if __name__ == "__main__":
    main()
