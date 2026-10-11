# Assistant token cost — proposal, predictions and the instrument to test them

**Status:** PROPOSED 2026-10-11, **for independent review before any implementation**. No tracker item owns it yet.
**Prompted by:** Claude Haiku 5.5 (9 Oct 2026) and its published price steps — see §1.
**Scope:** the analyser's LLM assistant and the authoring loop it serves. **Not** the Fluxtion runtime, which
contains no model and whose cost this proposal cannot change.

Everything in §2 was checked in this tree at `4fd84c5`. Everything in §4 is a **prediction** with its arithmetic
and assumptions exposed, intended to be falsified. Nothing here has been implemented or measured end to end.

## 1 · The price shape that prompts this

Published figures, Claude Haiku 5.5 (anthropic.com/claude-haiku-5-5, read 2026-10-11), per 1M tokens:

| | prompt ≤ 100k | prompt > 100k |
|---|---|---|
| input | $0.10 | $0.50 |
| output | $0.50 | $2.50 |
| cache read | $0.01 | $0.05 |
| cache write | $0.125 | $0.625 |

Two properties matter more than the absolute numbers:

1. **A 5× step at 100k applied to the whole prompt, per request.** Once a session's context crosses 100k, every
   later turn pays 5× — including the part that would otherwise be a cache read. Context length is therefore a
   cost control with a cliff in it, not a smooth gradient.
2. **Cache reads are 10× cheaper than fresh input** ($0.01 vs $0.10). A stable prefix re-read twenty times costs
   a tenth of what it costs re-sent. This is only available to a caller that marks the prefix.

The vendor's own positioning — "high-volume, cost-sensitive… summaries, compactions, classification… subagent on
coding work" — matches some of our loops and not others. §3 says which.

## 2 · What the analyser does today (verified in this tree)

| Fact | Where |
|---|---|
| Default Anthropic model is `claude-sonnet-5`; OpenAI default `gpt-4o` | `llm/LlmClient.java:16-17` |
| **No prompt caching.** `cache_control` appears nowhere in `src/main/java`; the request body is `model`, `max_tokens`, `system`, `messages` | `llm/AnthropicClient.java:37-41`, repo grep: 0 hits |
| **No token accounting.** `input_tokens` / `output_tokens` / `usage` are never read from the response | repo grep: 0 hits |
| Output is capped at 2048 tokens per reply | `llm/AnthropicClient.java:39` |
| The static system prompt is 5,703 chars ≈ **1.4k tokens** (chars/4 estimate, not a tokeniser count) | measured against `PromptBuilder.systemPrompt()` |
| The full prompt adds a record context and an action manifest on top of that | `PromptBuilder.fullPrompt`, `recordContext`, `inProcessActionManifest`, `restActionManifest` |
| Verbs already return *computed answers* (`aggregate`, `coverage`, `series`, `context`) rather than raw log | `llm/*Service.java` |
| `read` returns N records around an anchor; the prompt is deliberately seeded with the log's path and per-record byte offsets so an agentic model can seek the whole file | `VerbSchemas` `read`, `recordContext(…, LogFileInfo, …)` |

Read together: the design already avoids the expensive failure mode (dumping a log into the context), but it
takes **none** of the two savings the price table offers, and it **cannot currently tell** what a session costs.

## 3 · Proposals

Ordered so that each is testable before the next is justified. P1 is a precondition for believing any of §4.

**P1 — Instrument before optimising.** Read `usage.input_tokens`, `usage.output_tokens`,
`usage.cache_read_input_tokens` and `usage.cache_creation_input_tokens` from each response; accumulate per
session; surface in `context` (the verb that already reports what is in force) and in the status bar. No pricing
table in the app — report tokens, not money, because prices change and a stale price in a UI is a lie.
*Reason:* every number in §4 is currently a model. Without this we cannot confirm or refute one of them.
*Cost:* small; one response field read and a counter.

**P2 — Mark the stable prefix for caching.** Add `cache_control: {type: "ephemeral"}` at the end of the system
prompt + action manifest (identical for every turn of a session) and, as a second breakpoint, at the end of the
record context. *Reason:* turns the 10× cache-read discount on for the part of the prompt that never changes.
*Risk:* a cache breakpoint placed before something that does change buys nothing; if the manifest is rebuilt per
turn with a varying token (the REST manifest embeds a URL and token) the prefix is not stable — check before
claiming the saving.

**P3 — A context budget with a visible edge at 100k.** Track the running prompt size; warn in the UI and in the
verb echo as it approaches 100k; refuse or summarise rather than silently crossing. Offer `context {compact:
true}` that replaces earlier turn results with their findings.
*Reason:* the cliff is 5× on the whole prompt. An investigation that drifts over it quintuples every remaining
turn, including cache reads. This is the single largest avoidable cost in the design.
*Note:* this is also a correctness win — a long context is where a model starts contradicting earlier findings.

**P4 — Route the small, repetitive calls to a cheap fast model.** Candidates, in the vendor's own terms: verb
dispatch (natural language → one action), per-record summarisation, diagnostics triage (classify a producer
finding; apply a `suggestedFix` that is already written), caption generation for spotlights, report section
drafting. Keep the investigation partner — the turn that decides *what to look at next* and the one that writes
a conclusion — on the stronger model. Make it a setting with a per-call default, not a global swap.
*Reason:* these are classification and transformation, not reasoning; they are also the highest-frequency calls.
*Risk:* a weaker model that mis-dispatches a verb costs a round trip and user trust. P1's accounting plus an
A/B over the existing fixtures is what decides it, not this document.

**P5 — Keep answering from artefacts rather than from the model.** Continue the existing bias: a verb that can
compute an answer (coverage, aggregate, series, the M66 location table, a diagnostic's `suggestedFix`) should
compute it, so the model spends tokens *reading an answer* rather than *deriving one*. No work item; this is a
standing rule for new verbs, stated here because it is the reason the analyser's cost profile is good at all.

**P6 — Do not change the runtime or the hosted generator.** Named explicitly so the proposal is not read as
touching them: the generated processor contains no model, and `/generate-source` compiles rather than infers.
Their cost is CPU and memory and is unaffected by any model price.

## 4 · Predicted savings — the arithmetic, so it can be attacked

**Model session** (stated so a reviewer can disagree with the shape, not just the result): one investigation,
20 turns; stable prefix (system 1.4k + action manifest ≈ 2k) ≈ 3.4k tokens; record context ≈ 8k; each turn
appends ≈ 1.5k of results; output ≈ 600 tokens/turn. Total prompt at turn 20 ≈ 40k — **under the cliff**.

| Configuration | Input / cache cost | Output cost | Session total |
|---|---|---|---|
| **A. Today** (no caching, Haiku price applied for comparability) | 525k fresh input × $0.10/M = **$0.0525** | 12k × $0.50/M = $0.006 | **≈ $0.059** |
| **B. P2 caching** (whole prior context re-read as cache each turn; deltas written) | ≈ 525k cache read × $0.01/M = $0.0053, writes ≈ 40k × $0.125/M = $0.005 | $0.006 | **≈ $0.016** |
| **C. B, but the session drifts over 100k** (no P3) | same shape × 5 | × 5 | **≈ $0.08 and rising per turn** |

Predictions, each falsifiable by P1's accounting:

- **PR-1.** P2 reduces a 20-turn session's *input* cost by **3–5×** (table: 525k×$0.10 → ≈$0.010 combined
  read+write). Falsified if the stable prefix is not in fact stable (P2's risk) or if measured savings < 2×.
- **PR-2.** Crossing 100k multiplies the remaining session cost by **exactly 5×** per the published table.
  Falsified if the step applies to the delta rather than the whole prompt — worth confirming empirically, since
  the whole proposal's priority ordering rests on it.
- **PR-3.** Under P3, typical investigations stay under 100k. **Unknown today** — I have no distribution of real
  session sizes, which is precisely why P1 comes first. Falsified if instrumented sessions routinely exceed it,
  in which case P3 becomes compaction rather than a warning.
- **PR-4.** P4 moves a majority of *calls* but a minority of *tokens* to the cheap model. The saving is therefore
  smaller than the call-count suggests; I predict **<30%** of remaining spend. Falsified by measurement either way.

**The honest size of the prize.** For a single desktop user these are cents per session; the absolute saving does
not justify the work on its own. The reasons to do it are, in order: (1) **P1**, because an assistant whose cost
is unmeasurable cannot be tuned or priced; (2) **P3**, because the cliff is also where answer quality degrades;
(3) **P2**, which is cheap to implement and makes a hosted or multi-user deployment viable at a different scale;
(4) **P4**, last, and only with evidence. A reviewer who concludes "instrument, then stop" is giving a defensible
answer.

**Out of scope and unmeasured:** the development agent fleet (the largest actual spend, and not this repo's to
control); the authoring loop's model choice in the compiler-side runbook; any claim about competing vendors'
prices, which this document deliberately does not make.

## 5 · What a reviewer should attack

1. **§2's facts** — re-grep them. Are `cache_control` and `usage` genuinely absent, and is `claude-sonnet-5` the
   effective default in a shipped build (not overridden by config or the Assistant settings panel)?
2. **The stable-prefix claim (P2).** Is the system prompt + manifest actually byte-identical turn to turn? The
   REST manifest embeds a URL and token; the in-process one may vary. If it varies, P2's saving is overstated.
3. **PR-2.** Confirm from the vendor's documentation whether the >100k price applies to the entire prompt or to
   the excess. The proposal's priority order depends on it.
4. **The model session in §4.** Is 20 turns / 40k representative? Substitute your own numbers; the arithmetic is
   deliberately exposed so the conclusion can move with them.
5. **P4's routing list.** Which of those calls would a weaker model get wrong in a way the user would notice?
   Verb dispatch is the risky one: a mis-dispatched verb changes the view the human is looking at.
6. **Whether P1 alone is the right answer for now** — say so if it is.
7. **Anything here that reads as a claim but is actually an assumption.** Several are marked; find the unmarked ones.
