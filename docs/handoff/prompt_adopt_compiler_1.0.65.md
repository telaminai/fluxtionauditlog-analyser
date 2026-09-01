# Prompt — adopt the released compiler backend (fluxtion-builder 1.0.65)

Paste the block below into the analyser session. It is written to be pasted verbatim.

---

`fluxtion-builder 1.0.65` is **released to Repsy** and the cloud compiler backend running it is
**deployed**. This unblocks work you deliberately parked. Read
`docs/handoff/handoff_compiler_1.0.65_released.txt` first — it is the upstream handoff and it states
what was measured versus what was inferred.

Work in this order. Each item says what "done" means, because several of these were parked precisely
to avoid acting on an assumption.

**1. M45.6 — take the real dependency.** The reason to hold was that committing a SNAPSHOT would make
every build depend on an unpublished artefact. `1.0.65` is published (`fluxtion-builder`,
`fluxtion-builder-all-java8`, `fluxtion-bom`, with the BOM pairing `base=1.0.14 / compiler=1.0.65`).
Verify it resolves before you rely on it — upstream verified by unpacking the jar, not by reading a
workflow's exit code. Keep the committed-output fixtures as the regression pin; they now cost nothing
and they are what caught the byte instability at 1.0.64.

**2. M45.6a — regenerate and let the canary fire.** Bump `-Pregen` from builder 1.0.64 to 1.0.65,
regenerate, re-pin `SessionGraphShapeTest`. **You wrote that this is expected to fail first, by
design** — it is the downstream canary you offered upstream. If it does NOT fail, that is the
interesting result and worth a paragraph, because it would mean the regenerated shape is identical
where you predicted a difference.

**3. M45.4 — unpark it, or say why it stays parked.** You parked this waiting on a named AUTHORITY for
`fluxtion.framework`, not on emission. The authority now exists: framework auditors are excluded from
`authoredNodes` at REGISTRATION, so the two sets partition by construction rather than by precedence,
and `NodeClassificationInvariantTest` asserts that across five graph shapes and pins `serviceRegistry`
by name. Your own F-A finding is what produced this.

The check that is yours, and that upstream cannot do for you: **confirm a false `framework=true` can
no longer flatter the denominator, on the real session graph rather than on upstream's fixtures.**
Your earlier review already measured 33 vertices correct and `authoredNodeCount` exactly 12 — re-run
that against the released build and say whether it holds.

**4. THE DEADLOCK — this is the one that needs an explicit answer.** The `PARALLEL` default is still
`OFF` and **both repos are waiting on each other for a condition that is already met**:

- upstream flips when model authority is closed **and** one consumer *understands* `PARALLEL`;
- model authority is **closed** upstream; **M45.5 shipped** on 2026-08-31 with five guards
  mutation-checked, and you wrote that M45.5 **is** the gate.

Neither tracker is wrong; the pair is stale, which is the failure mode a two-repo gate invites.
**Upstream will not flip on inference and has said so.** So the deliverable here is a sentence:
either "we accept the vocabulary, flip the default", or the specific thing still missing. If you
think the gate is NOT met, say what would meet it — that is more useful than the flip.

**5. Re-run the backwards-compatibility check you flagged.** Your note says the before/after parser
comparison was run at `dd36bc5` and **needs re-running at `7a273a8`**, where the exporter was
rewritten as a model projection — a far larger change than the one it covered. That is still open and
still yours. At `OFF` the only unconditional change is `edgedefault="directed"`, which your parser
does not read, but that is the claim to re-verify rather than restate.

**6. Remote builds now return structured diagnostics.** The wire envelope was inert in production
until today — the deployed compiler predated it and answered 415, so every remote build fell back to
a plain call. It is now live end to end, verified by an artefact rather than a log line. If you have
code that parses compiler failures out of exception message text, it can stop: a coded rejection
arrives as a wire-contract document with `code`, `severity`, `rule`, `why`, `suggestedFix` and
`element`.

Read the report by **severity, never by position** — a failing build's report contains the warnings it
found as well as the error that stopped it, and `diagnostics[0]` is frequently a warning. Upstream
lists that as the most common consumer mistake.

**7. Error page links.** The pages are live at
`https://telaminai.github.io/fluxtion/troubleshooting/errors/<CODE>` and the emitted
`documentationUrl` resolves. **Do not link to `fluxtion.com/errors/…`** — that domain is not owned by
the project; it is parked with a broker, which is where the original 404 came from.

## What upstream wants attacked

1. **The gateway conclusion.** It rests on one artefact — the descriptor fingerprint — plus two
   corroborations. If that fingerprint could reach the generated descriptor by any route other than
   the envelope, the conclusion collapses. Upstream's claimed route is: `transient` field, set
   server-side only from envelope metadata.

2. **The M45.4 fix.** The invariant is asserted, but the two ordering windows your F-A finding named
   (`rootNodes` and `publicNodes` adding to `authoredNodes` after the guard loop reads it) still
   exist. They are unreachable now that framework nodes never enter the set — which is a different
   guarantee from being removed.

3. **Anything in the handoff you think is wrong.** The previous round corrected two upstream premises
   and both corrections were right, so this is a genuine request rather than a courtesy.
