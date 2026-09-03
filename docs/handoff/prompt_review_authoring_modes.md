# Review brief — the authoring-modes programme and the runtime benchmark

_Paste the block below to the reviewing session. Everything after the rule is the prompt._

---

You are reviewing a large body of work committed to `main` on 2026-09-03 by a single session, with no
brief → report → review cycle. **Nothing in it has had an independent read.**

**Start here, in this order:**

1. `docs/handoff/REVIEWER-ORIENTATION.md` — §6 is re-dated to today and names what landed.
2. `docs/handoff/unreviewed-changes.md` — one open `☐` entry, `f645cce^..3c39c6c` (32 commits). It lists
   six things it says you must still check. Treat that list as the author's own guess, not a boundary.
3. `docs/ONBOARDING.md` if you intend to change code.

## Flag: the authoring work is entirely new to you

**The previous review cycles covered the analyser as a forensic tool.** This work is about something the
project has not previously specified: **how a Fluxtion application gets authored in the first place, and
what part of that a language model should do.** If you last reviewed M40/M42/M43, none of this existed.

It also **reframes work you have seen**. M46 (toolchain repair) and M47 (start from a template) both
assumed authoring is something a model does. The central claim here is that a large part of it is not.
If that claim is wrong, M48 collapses and M46/M47 stand unchanged; if it is right, they sit underneath it.

## Two independent bodies of work — review them separately

**A · Runtime benchmark (M49, `docs/experience/runs/round-54/`).** The first runtime measurement in the
project's history; every prior round measured tokens. **No analyser code changed.** Self-contained and
reproducible from that directory. If you only have time for one, this is not the one.

**B · Authoring modes (M48, four new specs + two Python tools + one `src/` change).** This is where the
argument is, and where the risk is.

## The claims, ranked by how much rests on them

Attack them in this order. Each names where the evidence lives.

1. **"The analyser is required for verification."** `spec-authoring-mode-selector.md` §toolbench argues
   this from capacity, not preference: at 460 B/event a 10,000-event run is 5.8× Haiku's context, so an
   LLM cannot read the evidence at any interesting scale. **This is the most consequential claim written
   here** and the whole toolbench section follows it. The arithmetic is checkable in a minute; the
   *inference* from it is what needs judgement.
2. **"The bean-file half of integration is a constraint solve, not a model task."**
   `docs/experience/runs/round-57/NOTES.md`. `tools/bean-resolver.py` reproduces the measured-optimal
   selection *and* wiring from jar manifests alone, builds green, and emits alerts byte-identical to
   `round-48/expected.alerts` — at zero token cost against 1.98M weighted / 51 turns. **If this holds,
   every "authoring cost" figure this project has published was measuring a model doing a resolver's
   job.** Re-run it; the commands are in the notes.
3. **"Selection is memoisable too."** Round 57 addendum: a `Fluxtion-Convention` manifest field plus a
   one-line site profile resolves a six-way type-identical ambiguity, and changing the profile word
   changes the selected component. The matching rule — *silence is not a match* — is asserted from how a
   model reasoned in round 55. Is that the right rule, or convenient?
4. **The mode taxonomy itself.** Four modes, derived rather than chosen. Is this a real decomposition or
   a post-hoc tidy of one fixture? It rests on a single component-assembly fixture with five jars.
5. **`analyser.score.ExpectationScorer`** — the only `src/` change. It exists to correct **five scoring
   defects in this project's history, three in one session, every one of which erred toward agreeing
   with its author.** Two things to check: whether the five guards are the right five, and whether its
   figure-extraction rule (two shapes, chosen by whether tag keys are present) is sound — **that rule is
   asserted, not derived from the published format spec.**

## What the author did NOT verify — confirm or contradict

- **Modes 2 and 3 are entirely unmeasured.** No authoring instruction has ever been ablated here; every
  ablation this project has run was over *assembly* guidance. The Haiku ceiling remains unfound.
- **`docs/proposals/assessment-playground-ai-prompts.md` is a reading, not a measurement**, and says so.
  Its sharpest finding — that the Spring contract's wiring rule is right for scaffolded nodes and
  harmful for bought-in components — is inferred from round 48, not tested in the playground's setting.
- **The plain-Java comparison arm is n=1** against Fluxtion's n=4, and round 53 showed that arm can pass.
  Any published ratio is blocked on this.
- **Every ratio published to date is in raw weighted units**, which per P3b understates the dollar
  difference ~5× and overstates like-for-like work ~1.3×.
- **No `docs/site/` change, so no MkDocs build was run.** No Swing change, so rule 4's build-and-run gate
  was not exercised.
- **The two Python tools have no tests.** They now carry a product-strategy argument.
- **`round-49/expected.txt` is not in the analyser's record format** (18 blocks, zero `---` separators) —
  recorded in `FORMAT-NOTE.md`, deliberately not repaired. Is that deferral right?

## A habit of this session to check specifically

**The author was wrong several times and corrected in flight.** Examples: the audit log was asserted to
allocate before being measured; an upstream ask was filed calling a deliberate default "the single
largest performance item" before asking what the default was for; a scorer reported FAIL twice on work
that passed 17/17. **Each correction was written into the docs rather than edited away — verify that.**
Where a commit message records a retraction, check the corresponding spec carries it too; a correction
that lives only in git history is not a correction.

Also: the author violated rule 4 once in this range — ran the suite, saw a failure, and committed anyway
(`a5298fc`, fixed in `4d6887a`). Worth a glance at whether anything else slipped through.

## Upstream

Four asks were filed from this work: **UP-FLX-45** (clock), **UP-FLX-46** (lodged as
[telaminai/fluxtion#31](https://github.com/telaminai/fluxtion/issues/31)), **UP-FLX-47**
(`springToFluxtion` exposes no audit configuration), **UP-FLX-48** (audit default allocates where
nothing is published). If you are also reviewing the compiler's current state, 46 has a twelve-line
reproduction and 47 blocks an integrator from producing a log at all.

## What a good review looks like here

Per `REVIEWER-ORIENTATION.md` §5: check claims against the source rather than the report; **say what you
did not check, because silence reads as verified**; and prefer a command that settles something to an
argument about it. If any document above reads as persuasion rather than evidence, say so — several were
written quickly and at length, and volume is not rigour.
