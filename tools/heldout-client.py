#!/usr/bin/env python3
"""The held-out acceptance for M64 (spotlight): a CONTEXT-FREE client.

An LLM is handed exactly what an MCP client is handed — the bridge's server instructions and the tool list
derived from the analyser's /manifest — and nothing else. It is asked the six "Ask it to show you" sentences
from docs/site/user-guide/assistant.md, each in a fresh conversation, and then the guided-start tour with the
skill text attached. The script records which tools it called and what was lit afterwards, per sentence.

LOCAL ONLY — NEVER CI. It spends the owner's LLM key (read from ~/.fluxtion/fluxtion.apiKeyFile, `llmApiKey`,
never printed) and needs a running analyser with the REST transport on and a log + graph open:

    mvn -q package -DskipTests
    java -jar target/fluxtion-auditlog-analyser-*.jar --rest src/main/resources/demo/demo-quote-series.yaml
    # open the demo graph too (File ▸ Open GraphML, or the open verb), then:
    python3 tools/heldout-client.py [model] [1,4]        # default model claude-sonnet-5; optional sentence subset

Result: heldout-results.md in the current directory — read it, do not commit it (it is generated text).
First run 2026-09-17 (1.14.1, claude-sonnet-5): with the instructions, 5 of 6 sentences produced a spotlight on
the right thing (the sixth asked which note was meant — it had no antecedent); without the guidance paragraph
the same 5 of 6; the tour lit each beat before speaking. Record: docs/handoff/completed/heldout_m64_2026-09-17.md.
"""
import json, os, pathlib, re, sys, time, urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent
HOME = pathlib.Path(os.environ.get("ANALYSER_HOME", pathlib.Path.home()))
MODEL = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1][0].isdigit() else "claude-sonnet-5"
ONLY = set(a.split(",")) if (a := next((x for x in sys.argv[1:] if x[0].isdigit()), None)) else None
if "CI" in os.environ or "GITHUB_ACTIONS" in os.environ:
    sys.exit("heldout-client.py is local only: it spends the owner's LLM key")

ep = json.load(open(HOME / ".fluxtion-analyser" / "rest-endpoint"))
URL, TOK = ep["url"], ep["token"]
key = re.search(r"llmApiKey\s*=\s*(\S+)", open(pathlib.Path.home() / ".fluxtion" / "fluxtion.apiKeyFile").read()).group(1)


def get(path):
    req = urllib.request.Request(URL + path, headers={"X-Analyser-Token": TOK})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def guidance_text():
    """SpotlightVocabulary.GUIDANCE, as McpBridge.INSTRUCTIONS carries it (the constant read from source)."""
    src = (REPO / "src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/SpotlightVocabulary.java").read_text()
    i = src.index("GUIDANCE"); j = src.index(";", src.index("=", i))
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', src[src.index("=", i) + 1:j])
    return "".join(parts).encode().decode("unicode_escape")


manifest = get("/manifest")
INSTRUCTIONS = ("Drives a running Fluxtion Audit Log Analyser over its localhost action socket. "
                "Query verbs (analyser_aggregate, analyser_read) read the loaded audit log; the render verbs "
                "change what the desktop app shows (filter, graph, goto, flag) and are all reversible. "
                "The analyser must be running with the REST transport enabled (Settings > Assistant). "
                + guidance_text().replace("`spotlight`", "analyser_spotlight"))
NO_GUIDANCE = INSTRUCTIONS.split("POINT BEFORE")[0]


def tools():
    out = []
    for verb, schema in manifest["schemas"].items():
        sch = dict(schema)
        desc = sch.pop("description", verb)
        sch.setdefault("type", "object")
        out.append({"name": "analyser_" + verb, "description": desc, "input_schema": sch})
    return out


def act(verb, params):
    req = urllib.request.Request(URL + "/action", data=json.dumps({"action": verb, "params": params}).encode(),
                                 headers={"X-Analyser-Token": TOK, "Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def llm(system, messages):
    body = {"model": MODEL, "max_tokens": 4000, "system": system, "tools": tools(), "messages": messages}
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
    calls, final_text = [], ""
    for _ in range(max_turns):
        resp = llm(system, messages)
        content = resp.get("content", [])
        tool_uses = [b for b in content if b.get("type") == "tool_use"]
        final_text = " ".join(b.get("text", "") for b in content if b.get("type") == "text").strip() or final_text
        if not tool_uses:
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
            break
    else:
        final_text = (final_text + " [HARNESS: turn cap reached]").strip()
    return calls, final_text, lit()


SENTENCES = [
    "Show me where the spread first crossed 0.004 — point at it.",
    "Which node never logged? Highlight it on the graph.",
    "Walk me through this cycle and highlight each step.",
    "Highlight everything involved in that breach.",
    "Point at the note on the chart you mean.",
    "Clear the highlights.",
]

out = [f"# Held-out run — model {MODEL}, {time.strftime('%Y-%m-%d %H:%M')}\n",
       "Client context: ONLY the MCP bridge's server instructions + the tools from /manifest.\n"]
for variant, system in (("B — instructions incl. the spotlight guidance (what MCP clients get)", INSTRUCTIONS),
                        ("A — tool schemas only, NO guidance (harder than any real client)", NO_GUIDANCE)):
    out.append(f"\n## Variant {variant}\n")
    for i, s in enumerate(SENTENCES, 1):
        if ONLY and str(i) not in ONLY:
            continue
        if i == 5:   # "the note on the chart you mean" needs a chart with a note first
            act("graph", {"newTab": True, "name": "spread", "series": ["quotePublisher.spread"],
                          "notes": [{"recordIndex": 40, "text": "first widening"}]})
        if i != 6:
            act("spotlight", {"clear": True})
        calls, text, lit_after = run(s, system)
        pointed = any(c[0] == "spotlight" and c[2] for c in calls)
        out.append(f"\n### {i}. \"{s}\"\n- tools: " + " → ".join(f"{c[0]}{'' if c[2] else ' ✗'}" for c in calls)
                   + f"\n- called spotlight successfully: **{pointed}** · lit afterwards: {lit_after}\n- said: {text[:400]}\n")
        for c in calls:
            if c[0] == "spotlight":
                out.append(f"  - spotlight {c[1]} → ok={c[2]} {c[3]}\n")
        act("spotlight", {"clear": True})

if not ONLY or "tour" in ONLY:
    skill = (REPO / "docs/skills/common/guided-start/SKILL.md").read_text()
    out.append("\n## Guided-start tour (variant B + the skill text as an attached document)\n")
    calls, text, lit_after = run("Give me the guided start.", INSTRUCTIONS,
                                 extra_user="Here is a skill to follow when I ask for the guided start:\n\n" + skill)
    out.append("- tools: " + " → ".join(f"{c[0]}{'' if c[2] else ' ✗'}" for c in calls)
               + f"\n- spotlight calls: {sum(1 for c in calls if c[0] == 'spotlight')} · lit at the end: {lit_after}\n- said (first 600): {text[:600]}\n")
    for c in calls:
        if c[0] == "spotlight":
            out.append(f"  - spotlight {c[1]} → ok={c[2]} {c[3]}\n")

pathlib.Path("heldout-results.md").write_text("".join(out))
print("".join(out))
