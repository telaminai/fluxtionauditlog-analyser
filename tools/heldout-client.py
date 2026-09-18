#!/usr/bin/env python3
"""The held-out acceptance for M64 (spotlight): a CONTEXT-FREE client.

An LLM is handed exactly what an MCP client is handed — the bridge's server instructions (read from /manifest,
which serves them verbatim) and the tool list derived from /manifest — and nothing else. It is asked the "Ask it
to show you" sentences from docs/site/user-guide/assistant.md, each in a fresh conversation, and then the
guided-start tour with the skill text attached. The script records which tools it called and what was lit.

EVERY CONVERSATION GETS ITS OWN ANALYSER PROCESS under a fresh isolated user.home (re-review 2026-09-18 F4: the
app keeps charts and their notes across `open {close: "all"}` by design, so one process cannot be reset to a
baseline). The baseline is ASSERTED from `context` before the model is called: the demo log and graph open, no
chart but the default empty one, no filter, no selection, nothing lit.

LOCAL ONLY — NEVER CI. It spends the owner's LLM key (read from ~/.fluxtion/fluxtion.apiKeyFile, `llmApiKey`,
never printed). Needs a built jar (mvn -q package -DskipTests).

    python3 tools/heldout-client.py --check-fixture           # NO key, NO model: proves the isolation (exit 0/1)
    python3 tools/heldout-client.py [model] [1,4] [tour]      # default model claude-sonnet-5; optional subset

Result: heldout-results.md in the current directory — read it, do not commit it (it is generated text).
Record: docs/handoff/completed/heldout_m64_2026-09-17.md (the first run, its correction, and the re-runs).
"""
import glob, json, os, pathlib, re, shutil, subprocess, sys, tempfile, time, urllib.error, urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
LOG = REPO / "src/main/resources/demo/demo-quote-series.yaml"
GRAPHML = REPO / "src/test/resources/topology/demo-quote-processor.graphml"
CHECK_ONLY = "--check-fixture" in sys.argv
ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
MODEL = next((a for a in ARGS if not a[0].isdigit() and a != "tour"), "claude-sonnet-5")
ONLY = set(next((a for a in ARGS if a[0].isdigit()), "").split(",")) - {""} or None
if "CI" in os.environ or "GITHUB_ACTIONS" in os.environ:
    sys.exit("heldout-client.py is local only: it spends the owner's LLM key")


def jar():
    jars = [j for j in glob.glob(str(REPO / "target" / "fluxtion-auditlog-analyser-*.jar")) if "original" not in j]
    if not jars:
        sys.exit("no jar — run `mvn -q package -DskipTests` first")
    return jars[0]


# ---- one analyser process per conversation ------------------------------------------------------------------

URL = TOK = None
_proc = None
_home = None


def stop_analyser():
    global _proc, _home
    if _proc is not None:
        _proc.terminate()
        try:
            _proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            _proc.kill()
        _proc = None
    if _home is not None:
        shutil.rmtree(_home, ignore_errors=True)
        _home = None


def launch_analyser():
    """A brand-new process under a brand-new home: nothing survives from the previous conversation."""
    global URL, TOK, _proc, _home
    stop_analyser()
    _home = pathlib.Path(tempfile.mkdtemp(prefix="heldout-"))
    cfg = _home / ".fluxtion-analyser"
    cfg.mkdir(parents=True)
    exchange = _home / "exchange"
    exchange.mkdir()
    (cfg / "config").write_text(f"assistant.exports=true\nassistant.exportDir={exchange}\nassistant.rest=true\n")
    _proc = subprocess.Popen(["java", f"-Duser.home={_home}", "-jar", jar(), "--rest"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    endpoint = cfg / "rest-endpoint"
    for _ in range(300):
        if endpoint.exists():
            try:
                ep = json.loads(endpoint.read_text())
                URL, TOK = ep["url"], ep["token"]
                get("/manifest")
                return
            except Exception:  # noqa: BLE001 — not up yet
                pass
        time.sleep(0.1)
    sys.exit("the analyser did not publish a REST endpoint within 30 s")


def get(path):
    req = urllib.request.Request(URL + path, headers={"X-Analyser-Token": TOK})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def act(verb, params):
    """A refused verb answers HTTP 400 with the same JSON shape ({ok:false, error}); the client must SEE the refusal,
    not an HTTPError — that is the text a model learns the vocabulary from."""
    req = urllib.request.Request(URL + "/action", data=json.dumps({"action": verb, "params": params}).encode(),
                                 headers={"X-Analyser-Token": TOK, "Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        try:
            return json.loads(body)
        except ValueError:
            return {"ok": False, "error": f"HTTP {e.code}: {body[:200]}"}


def must(verb, params):
    r = act(verb, params)
    if not r.get("ok"):
        sys.exit(f"fixture setup failed: {verb} {params} -> {r.get('error')}")
    return r


def fresh_fixture():
    """A new process, the demo log + graph open, and the baseline ASSERTED before any model call."""
    launch_analyser()
    must("open", {"log": str(LOG), "graphml": str(GRAPHML)})
    ctx = None
    for _ in range(300):
        ctx = act("context", {})["context"]
        if (ctx.get("log") or {}).get("records"):
            break
        time.sleep(0.1)
    else:
        sys.exit("the demo log did not load")
    problems = []
    if ctx.get("spotlight"):
        problems.append(f"something is lit: {ctx['spotlight']}")
    if ctx.get("filter") not in (None, {}):
        problems.append(f"a filter is set: {ctx['filter']}")
    if ctx.get("selection"):
        problems.append(f"a record is selected: {ctx['selection']}")
    graphs = ctx.get("graphs") or []
    if [g for g in graphs if g != "Graph 1"]:
        problems.append(f"charts carried over: {graphs}")
    if not (ctx.get("graphPairing") or {}).get("graph"):
        problems.append("the demo graph is not open")
    if problems:
        sys.exit("the fixture is not at its baseline: " + "; ".join(problems))
    return ctx


def check_fixture():
    """No key, no model: dirty one process, then prove the next fixture is at the baseline."""
    fresh_fixture()
    must("graph", {"newTab": True, "name": "carry-over", "series": ["quotePublisher.spread"],
                   "notes": [{"recordIndex": 15, "text": "left by a previous conversation"}]})
    must("filter", {"text": "nothing-matches-check"})
    must("goto", {"recordIndex": 15})
    must("spotlight", {"target": "status", "caption": "left lit"})
    dirty = act("context", {})["context"]
    assert "carry-over" in (dirty.get("graphs") or []) and dirty.get("spotlight"), f"the dirtying did not take: {dirty}"
    fresh_fixture()                                             # exits non-zero if anything carried over
    r = act("spotlight", {"target": "graph:carry-over:note:1"})
    stop_analyser()
    if r.get("ok"):
        sys.exit("the previous conversation's chart note is still lightable — the fixture is not isolated")
    print("fixture isolation holds: a new process, the baseline asserted from context, and the previous "
          f"conversation's chart is gone ({r.get('error')})")


if CHECK_ONLY:
    check_fixture()
    sys.exit(0)

key = re.search(r"llmApiKey\s*=\s*(\S+)", open(pathlib.Path.home() / ".fluxtion" / "fluxtion.apiKeyFile").read()).group(1)

# ---- the client: instructions + tools from /manifest, nothing else --------------------------------------------

launch_analyser()
manifest = get("/manifest")
INSTRUCTIONS = manifest["instructions"]          # == McpBridge.INSTRUCTIONS, held by ActionServerTest
assert "up to " in INSTRUCTIONS and "REPLACES" in INSTRUCTIONS, "the manifest's instructions are not the full guidance"
NO_GUIDANCE = INSTRUCTIONS.split("POINT BEFORE")[0]
TOOLS = []
for verb, schema in manifest["schemas"].items():
    sch = dict(schema)
    desc = sch.pop("description", verb)
    sch.setdefault("type", "object")
    TOOLS.append({"name": "analyser_" + verb, "description": desc, "input_schema": sch})


def llm(system, messages):
    body = {"model": MODEL, "max_tokens": 4000, "system": system, "tools": TOOLS, "messages": messages}
    req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(body).encode(),
                                 headers={"x-api-key": key, "anthropic-version": "2023-06-01",
                                          "content-type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read().decode())


def lit():
    sp = act("context", {})["context"].get("spotlight") or {}
    return [f"{x.get('n')}:{x.get('target')}" for x in (sp.get("lit") or [])]


def run(user_text, system, extra_user=None, max_turns=24):
    messages = []
    if extra_user:
        messages += [{"role": "user", "content": extra_user}, {"role": "assistant", "content": "Understood."}]
    messages.append({"role": "user", "content": user_text})
    calls, final_text, capped = [], "", True
    for _ in range(max_turns):
        resp = llm(system, messages)
        content = resp.get("content", [])
        tool_uses = [b for b in content if b.get("type") == "tool_use"]
        final_text = " ".join(b.get("text", "") for b in content if b.get("type") == "text").strip() or final_text
        if not tool_uses:
            capped = False
            break
        messages.append({"role": "assistant", "content": content})
        results = []
        for tu in tool_uses:
            verb = tu["name"].replace("analyser_", "", 1)
            try:
                r = act(verb, tu.get("input") or {})
            except Exception as e:  # noqa: BLE001 — the transcript records it
                r = {"ok": False, "error": str(e)}
            calls.append((verb, json.dumps(tu.get("input") or {})[:140], r.get("ok"), (r.get("error") or "")[:100]))
            results.append({"type": "tool_result", "tool_use_id": tu["id"], "content": json.dumps(r)[:6000]})
        messages.append({"role": "user", "content": results})
        if resp.get("stop_reason") == "end_turn":
            capped = False
            break
    return calls, final_text, lit(), capped


def outcome(calls, lit_after, capped, sentence):
    """One word per conversation, so a table cannot overstate: POINTED / CLEARED / ASKED / TURN-CAPPED / NONE."""
    if capped:
        return "TURN-CAPPED"
    if sentence.startswith("Clear"):
        return "CLEARED" if not lit_after else "NOT CLEARED"
    if lit_after:
        return "POINTED"
    return "ASKED (no spotlight; answered in words or asked back)"


SENTENCES = [                      # the user guide's "Ask it to show you" rows, as published
    "Show me where live orders first went above 1 — point at it.",
    "Where do I start a project from a template?",
    "Which node never logged? Highlight it on the graph.",
    "Walk me through this cycle and highlight each step.",
    "Highlight everything involved in that breach.",
    "Point at the note on the chart you mean.",
    "Clear the highlights.",
]

out = [f"# Held-out run — model {MODEL}, {time.strftime('%Y-%m-%d %H:%M')}\n",
       "Client context: ONLY the MCP bridge's server instructions (from /manifest) + the tools from /manifest. "
       "Every conversation: a NEW analyser process under a fresh home, baseline asserted from context.\n"]
try:
    for variant, system in (("B — instructions incl. the spotlight guidance (what MCP clients get)", INSTRUCTIONS),
                            ("A — tool schemas only, NO guidance (harder than any real client)", NO_GUIDANCE)):
        out.append(f"\n## Variant {variant}\n")
        for i, s in enumerate(SENTENCES, 1):
            if ONLY and str(i) not in ONLY:
                continue
            fresh_fixture()
            if s.startswith("Point at the note"):   # "the note on the chart you mean" needs a chart with a note first
                must("graph", {"newTab": True, "name": "spread", "series": ["quotePublisher.spread"],
                               "notes": [{"recordIndex": 40, "text": "first widening"}]})
            if s.startswith("Clear the highlights"):   # something must be lit for a clear to mean anything
                must("spotlight", {"target": "status", "caption": "lit for the clear sentence"})
            calls, text, lit_after, capped = run(s, system)
            out.append(f"\n### {i}. \"{s}\"\n- outcome: **{outcome(calls, lit_after, capped, s)}**\n- tools: "
                       + " → ".join(f"{c[0]}{'' if c[2] else ' ✗'}" for c in calls)
                       + f"\n- lit afterwards: {lit_after}\n- said: {text[:400]}\n")
            for c in calls:
                if c[0] == "spotlight":
                    out.append(f"  - spotlight {c[1]} → ok={c[2]} {c[3]}\n")
    if not ONLY or "tour" in ARGS:
        skill = (REPO / "docs/skills/common/guided-start/SKILL.md").read_text()
        out.append("\n## Guided-start tour (variant B + the skill text as an attached document; fresh process)\n")
        fresh_fixture()
        calls, text, lit_after, capped = run("Give me the guided start.", INSTRUCTIONS,
                                             extra_user="Here is a skill to follow when I ask for the guided start:\n\n" + skill)
        out.append(f"- outcome: **{'TURN-CAPPED' if capped else 'COMPLETED'}** · spotlight calls: "
                   f"{sum(1 for c in calls if c[0] == 'spotlight' and c[2])} · lit at the end: {lit_after}\n- tools: "
                   + " → ".join(f"{c[0]}{'' if c[2] else ' ✗'}" for c in calls) + f"\n- said (first 600): {text[:600]}\n")
        for c in calls:
            if c[0] == "spotlight":
                out.append(f"  - spotlight {c[1]} → ok={c[2]} {c[3]}\n")
finally:
    stop_analyser()

pathlib.Path("heldout-results.md").write_text("".join(out))
print("".join(out))
