# Round 06 — the SHIPPED assets, and the audit half nobody has tested

**Predictions registered before either agent starts.** Exploration round (D-AX7): findings allowed, no
attribution claim.

## What changed, and why this round is different from 01–05

**The subject is now the assets as they SHIP**, not the `current/` set I maintained here. Owner's call.
`docs/experience/current/` becomes the thing I compare against rather than the thing under test.

More importantly: **every previous round ran against a bundle that could not produce an audit log.** The
processor was never shipped, so the server started and handled nothing — discovered against production on
2026-08-30 and fixed by the playground the same day. So rounds 01–05 tested Fluxtion authoring and the
bundle's scripts, and **never once tested the audit half**, which is this product's entire subject. That
is what this round aims at.

## Rig manifest

| | |
|---|---|
| Bundle | fetched live from `/start/scaffold?template=analyser-bundle`, contract `m19-bundle/4` |
| Assets under test | the bundle's own `CLAUDE.md` (60 lines, reference-block + project specifics) and its **four** shipped skills: `guided-start`, `load-audit-log`, `add-a-node`, `run-mongoose-server` |
| Skills provenance | `canonical@48b0e0a`, sha256-verified by the vendor |
| Analyser | **reachable** — `--rest` socket, which no previous round had |
| Transport | REST, not MCP. The agent has `curl`; the skills are written in MCP tool form |
| n | 2 · **Exploration**, not attribution |

## The task

Authoring **and** evidence, because the audit half only shows up when the log is the answer:

> Add a node that flags when a price exceeds a threshold. Then **prove from the audit log — not from your
> code — that it fired for exactly the events you expect**, and tell me what the log cannot settle.

## Predictions

- **P1 · The MCP/REST gap bites.** `load-audit-log` is written in `analyser_open {…}` tool form (6
  occurrences) and gives no HTTP fallback. An agent holding only `curl` must translate `analyser_*` into
  `POST /action {"action":…,"params":{…}}`. I expect at least one run to stall, guess the envelope wrong,
  or abandon the analyser and read the YAML directly.
- **P2 · The reference block earns itself or does not.** With four links at the top and the rules
  deliberately NOT restated, I expect at least one run to fetch at least one of them. If neither does, the
  pointing strategy is untested in practice however sound it is in principle — and that is worth knowing.
- **P3 · The audit half produces the round's real findings.** Rounds 01–05 could not reach it. I expect
  the new findings to be about instrumenting a node, reading the record, and the difference between "did
  not log" and "did not run" — not about `transient` or `nodeBeans`, which are now published upstream.
- **P4 · Build failures ≤ 1 per run.** The published triage table plus a working bundle should make the
  constructor-match error avoidable. Round 05 was 3/3 without the resources, 1/3 with.
- **P5 · At least one run overclaims from the log.** The task explicitly asks what the log *cannot*
  settle. I expect at least one to assert "fired for exactly these events" without checking whether the
  log's audit level makes absence meaningful — the F1 distinction, from the other side.

## What I am recording that no previous round did

**What the agent came away BELIEVING**, not only what it did. Round 05's most valuable result — four of
six holding the same false rule about dispatch order — came from a question I nearly did not include, and
no metric here was designed to capture it. Each run is asked what it now believes about how a node gets
into the record, and whether it checked.

## What would falsify the write-up

- **Both runs sail through the audit half** → the assets are better than I think and the "never tested"
  worry was overblown.
- **Neither run touches the analyser** → the socket is not the missing piece I claimed it was in round 05.
- **The findings are still about authoring** → the audit half is not reachable by this task either, and
  the task shape is wrong rather than the assets.
