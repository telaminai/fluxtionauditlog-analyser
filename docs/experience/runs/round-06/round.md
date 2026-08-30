# Round 06 — the shipped assets, and the first round that could reach the audit half

**Exploration round, n=2.** Predictions registered at `c55d8b5`, rig amendment at `6a66cf4`, both before either agent
reported. *(Corrected by review F5: the first write named `4e3e5bc`/`94f51bf`, which a rebase left
unreachable — they exist in one working copy and no branch contains them, so a fresh clone could not
check the preregistration claim. The evidence for a timing claim has to survive a clone.)*

## The rig caveat did NOT fire

The amendment set a rule in advance: discount any finding that turns on my bundle predating the
numeric-`price` fix. **Nothing hinged on it.** Both agents logged their *own* `price` value from the event
inside their new node, so `RootNode`'s log shape never blocked them. The round stands as run.

## Results against the predictions

| | prediction | outcome |
|---|---|---|
| **P1** | the MCP/REST gap bites | **CONFIRMED 2/2, worse than predicted** |
| **P2** | the reference block gets fetched | **CONFIRMED 2/2** |
| **P3** | findings come from the audit half | **CONFIRMED** |
| **P4** | ≤1 build failure per run | **CONFIRMED — zero, both** |
| **P5** | someone overclaims from the log | **FALSIFIED — neither did** |

## P1 · The analyser was unreachable, and it is our defect

Both agents held a valid token from `~/.fluxtion-analyser/rest-endpoint`. Both tried roughly **twenty**
path/method/auth combinations — `/`, `/mcp`, `/sse`, `/api/context`, JSON-RPC `initialize`, Bearer auth,
SSE accept headers. **Every attempt returned the JDK's bare `404 No context found for request`**, including
with no token and with a deliberately wrong one, so the reply was not even auth-differentiated. Neither
reached the analyser; both fell back to reading the exported YAML directly.

Arm A's judgement is the one to keep: *"I judged that guessing further at its request format would cross
into 'invented'"* — the agent behaved correctly and our surface punished it.

**The diagnosis is exact and it is ours.** We publish a URL and a token and no way to learn the protocol.
`/manifest` answers everything — every verb, every schema — and **is itself undiscoverable**, because
nothing points at it and a wrong path says nothing.

**FIXED** (`ActionServer.handleUnknownPath`): an unknown path still returns 404, because the path really is
wrong, but the body now names both routes, the `{"action":…,"params":{…}}` envelope and the auth header.
Served without a token — it discloses the *shape* of the API, never data, and a caller who cannot
authenticate still needs to learn they are at the wrong door. Verified live against `/api/context`, one of
the exact paths both agents tried: one wrong guess is now enough. Three tests, including one that the real
routes still win over the catch-all.

## P2 · The pointing strategy WORKS — first evidence, after arguing it for two days

Arm B fetched three of the four canonical links, arm A two, and **both used what they found**: the
`auditLog.info(k,v).info(k,v)` chaining form from the golden path, and the *"referenced children are still
discovered"* `nodeBeans` rule from the Spring contract. Neither restated rule was in the bundle; both were
reached by following a link.

D-AX1b was an argument until this round. It is now an observation.

**One limitation worth recording, from arm A:** it noted `WebFetch` runs a page through a summarising
model, so it treated fetched content as *"a lead, not ground truth"* and cross-checked the load-bearing
claims against the runtime sources jar. Our links are read through a lossy channel — which is an argument
for the upstream text being precise, not for shipping copies.

## P4 · Zero build failures, both runs

Round 05 was 3/3 failures without the resources and 1/3 with. Round 06 is **0/2** — one build each, first
attempt. Arm B fetched the source-gen triage table and did not need it.

## P5 · FALSIFIED — and the answers are better than the question

Neither overclaimed. Both produced genuinely bounded answers to *"what can the log not settle"*: that the
threshold's correctness is not shown, that nothing proves anything **acted** on the flag, that the log
speaks only to the five rows in this run, that single-threaded ordering says nothing about concurrency.

Arm A went furthest, and it is the sharpest thing in the round:

> *"this particular log can't demonstrate the 'absence is ambiguous under no tracing' case the skill warns
> about; it only shows the traced case"*

— having found `eventLogger.trace = true` **hardcoded** in the generated processor. It named the boundary
of its own evidence, which is exactly the F1 distinction applied from the inside.

## The belief question — the result that matters

Round 05: **four of six** agents came away holding the same false rule about what puts a node in a record.

Round 06: **two of two** hold the correct three-condition model — in the graph, trigger fired, tracing on;
and separately `EventLogSource`/`EventLogNode` for values — and **both CHECKED rather than inferred**, both
using the same evidence unprompted: `riskCheck` appearing as a bare `{thread, method}` line beside their
own node's four key-values, *in the same record of the same run*.

Arm B was explicit about the half it had **not** verified: *"I did not test the tracing-off case myself…
that half is read from the skill, not independently reproduced by me here."*

**What changed between the rounds:** the `add-a-node` skill now states the three conditions — the F1
correction an independent reviewer forced on 2026-08-30 — and the bundle now produces a log to check it
against. **Not attributable**: different task, different bundle, analyser present, n=2. But the belief
difference is stark, the mechanism is plausible, and the metric that saw it exists only because round 05
made me add it.

## What this round changes

1. **The REST protocol is discoverable** — fixed, tested, verified live.
2. **D-AX1b has evidence.** Links get fetched and used.
3. **A limitation of the channel**: our links are read through a summariser, so upstream precision matters
   more than length.
4. **The audit regime is fixed at generation time** — filed as
   [fluxtion#25](https://github.com/telaminai/fluxtion/issues/25). Arm A reported `eventLogger.trace = true`
   as "hardcoded"; checking that myself sharpened it. It is **not** a constant — the bundle's processor has
   `trace = true` and the analyser's demo has `trace = false`, so it faithfully reflects each build. The
   defect is that generation is the *only* place the decision exists: `trace` is assigned solely by the
   builder-time `tracingOn`/`tracingOff`, and no runtime control event reaches them. So turning tracing on
   needs a regeneration, which needs a key — and the regime decides whether an absent node is *proof* or
   merely *silence*.

## Honest limits

n=2, one task, one model family, one bundle revision. The belief result is the strongest signal the loop
has produced and it is **not** an attribution: too much changed at once. What would settle it is the same
task against a bundle carrying the *pre-F1* skill text — which no longer exists anywhere, so it cannot be
run. That is the cost of fixing the thing you were measuring.
