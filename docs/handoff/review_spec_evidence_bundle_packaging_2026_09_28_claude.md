# Review — evidence bundle packaging, first delivery (DRAFT r1)

Reviewing `docs/specs/spec-evidence-bundle-packaging.md` at `0e268087`, against §3's placement question.
Examples use DEMO-safe naming; the experiments ran against a private project's log, described generically.

## Verdict

**C, plus two things the analyser must own: a `--verify` flag on the existing binary, and two fields in
`context`. No new UI, no new verb, no dialog.**

Not B-minimal. B-minimal's own justification is that a skill cannot make capture atomic and cannot be trusted
with hashing. The first is true and is fixed by two fields, not by a core. The second is true and is fixed by a
CLI flag on a binary that already exists. What is left of B-minimal after that is a menu item and a file
chooser — which is exactly the surface the owner is worried about, bought for nothing.

**The single strongest reason: the recipient half is already built and I ran it.** `open {project, log, graphml}`
restored three walks, seven reports, and played a four-step walk with **every target `CURRENT` and available**
after a cold reopen. There is no "open a bundle" feature to write, only an unzip in front of three verbs that
work today.

---

## Answers

### 1. Which shape, and why? — **RAN**

C, with the analyser providing verification and capture coherence and nothing else.

What the analyser must still provide, and why a skill cannot:

| need | why a skill cannot | smallest form |
|---|---|---|
| **verification** | it must be one implementation with pinned fixtures, or a recipient cannot rely on it. Skill prose re-implementing sha256 per reader is not a trust anchor | `analyser --verify <bundle>` on the existing jbang binary, beside `--mcp` and `--rest`. **Zero UI.** |
| **capture coherence** | `context` exposes neither the log generation nor whether a load is pending (see 2) | add `generation` and `loadPending` to `context.log` |

Everything else — assembling the folder, zipping it, writing the manifest, unzipping, calling three open verbs —
is filesystem work a skill already does well, and Friday's skills do it today.

### 2. Capture coherence (§4.1) — **RAN**

**A skill cannot currently get a coherent capture. Mine relied on nothing changing.**

`context {sections:["log"]}` returns exactly these keys: `following`, `freshness`, `from`, `to`, `openedBy`,
`openedFrom`, `path`, `records`, `sizeBytes`, `streamEnd`, `supportsFollow`, `tailNote`,
`trailingRecordsIncluded`, `trailingRecordsPending`.

Against §4.1's four preconditions:

| precondition | can a skill check it? |
|---|---|
| a log is open | ✅ `path` |
| **Follow is paused** | ✅ `following` |
| the read identity is not UNVERIFIED/REPLACEMENT | ⚠️ partly — `freshness` is metadata-based and says so itself: *"unchanged metadata does not prove identical bytes"*. `log.identity` is absent entirely when the reader has not assessed |
| **no load is pending** | ❌ not exposed |
| **the generation did not move during the capture** | ❌ not exposed |

The call sequence I used was `context` → copy files → `context` again, which detects nothing: with no generation
to compare, the second call cannot tell a moved log from a still one.

**The analyser already has this exact rule, internally, for walk capture.** `WalkAuthoring` refuses a save with
*"another log was opened while this walk was being saved — nothing was saved, because its steps were read against
the previous log"*, by comparing a captured generation against the frame's. §4.1 is that rule again. Do not build
a second one: **expose the generation** and let the skill do capture → re-read → refuse, and the property is the
same one M69 already ships.

### 3. Flags — **RAN + READ. This is the finding that changes the spec.**

**Flags do not persist anywhere. Not in the profile, not in the machine config, not on disk at all.**

- `MainFrame:131` — `private final java.util.Set<Integer> flaggedRows = new java.util.HashSet<>();`
- `MainFrame:4457` — `flaggedRows.clear();   // flags are per-file (model row indices)`, on every log open.
- I flagged a record through the verb with a project active, then grepped that project's profile: **zero** flag
  keys. After a reopen, `context.flags` is `[]`.

So §2's "where flags persist is not yet established" resolves to **nowhere**, and §4.2's
`profile/…  and flags if EB-0 shows where they live` cannot be satisfied. Capturing flags in v1 means **building
flag persistence first** — a real feature, with the model-row-index-per-file problem attached.

**Recommendation: drop flags from the first delivery, and say why in the spec.** A walk already does the job.
A flag is *"look at this record, here is my note"*; a walk step is *"look at this record, here is my caption"* —
and walks persist, travel in the profile, and re-resolve their targets with an identity verdict. Flags do none of
that. The bundle's purpose is served without them, and the demo is unaffected.

### 4. Load — **RAN**

A cold reopen of project + log + graphml, then playing the walk:

```
step 1 SHOWN [('records:row:0',   'CURRENT', True)]
step 2 SHOWN [('graph:<a chart>', 'CURRENT', True)]
step 3 SHOWN [('records:row:799', 'CURRENT', True)]
step 4 SHOWN [('topology:verdict','CURRENT', True)]
```

Three walks and seven reports restored from the profile. Flags: `[]`, as above. **The recipient experience already
works** — the missing pieces are packaging and verification, not opening.

Not tested: another machine and another home (EP-A6). I reopened on the same machine, so path anchoring across
homes is **ASSUMED**, not shown.

### 5. Verification — **READ**

In the analyser's binary, as a flag; not in the UI, and not in skill prose.

Friday's skills contain **no hashing of any kind** — I checked both skills and the experiments README for
`sha256`, `checksum`, `verify`: nothing. So verification is genuinely absent today and genuinely needs building.

My skills would **call** it, never compute it. `analyser --verify bundle.fexp` printing the identity and a
per-member verdict is one implementation, testable headless, usable by a human with no agent, and adds no surface
to the running app. This is the one place I would spend the delivery.

### 6. What would break a demo, in each shape — **RAN (I broke two of them today)**

**A and B — new UI states.** Today's 1.26.0 demo died on a stuck mouse-drag that left the records table scrolling
until restart, and every symptom downstream (a walk dismissing itself) followed from it. That came from an
existing surface interacting badly; a file chooser, a verification dialog and an "opened a copy" notice are three
more. This is the owner's concern and it is well founded.

**C — version skew between the agent's bridge and the app.** I hit this today, three times in one session: the
MCP bridge was an older build, so `walk` was not in my tool list at all, `series` silently ignored `graph` and
`style`, `screenshot` required a `path` the newer schema defaults, and `flag` wanted `recordIndexes` where I sent
`recordIndex`. **An agent-led demo fails when the bridge is older than the app, and it fails confusingly.** The
mitigation is that a bundle skill should call the CLI and the three stable open verbs, all of which are old and
unlikely to drift — not new verbs.

**C's second risk, from Friday: the profile re-anchors when moved.** The capture skill records it as a hard-won
lesson — *"Move it into the experiment folder and every one of them re-anchors there: source roots point at
`audit-experiments/<slug>/<repo>/…` and runbooks go orange with `exists:false`. A missing source root is not an
error, so navigation silently stops working. Found by loading a copied bundle from cold."* Friday's answer was to
leave the profile in `.analyser/` and ship a pointer — which cannot work for a bundle that must open on another
machine.

**The spec is already immune to this, and should say so.** §4.2's allow-list is GRAPHS, REPORTS and VIEW, which
excludes source roots, workspace anchors and runbook pointers — the very keys that re-anchor. That is a real
advantage of the spec over Friday's skills and it is currently unstated.

### 7. What is over-built, and what is missing — **READ**

**Cut:**
- **Flags from §4.2 and the bundle entirely** (see 3). They cannot be delivered without building persistence.
- **EP-A11** as an acceptance check. The rule-1 sweep is a release gate that already runs on everything; listing
  it as a bundle acceptance adds ceremony, not coverage. Keep the by-eye image check, which the sweep cannot do.
- **§4.1's "Follow is paused" as a refusal.** Pausing Follow is something capture can just *do*, then restore.
  Refusing makes the agent's flow two steps where it could be none. Keep the refusal for the identity verdict,
  where the analyser genuinely cannot proceed safely.

**Wrong, with evidence:**
- **§2, "confirm there are no absolute paths in a captured profile", is the wrong question.** Friday's failure was
  *relative* paths re-anchoring, which is silent, whereas an absolute path fails loudly. Restate it as: *confirm
  no allow-listed category contains a path of any kind.* Note that GRAPHS may carry **external series file
  paths** — that is the one I would check first, and I did not.
- **§4.2's whole-log rule is right but expensive, and the spec does not say so.** The reasoning (keeping
  `recordIndex` and record digests valid) is sound. But logs in real use here run to 64MB and 142MB, and Friday's
  skill excerpts by time window with a tool that records the cut precisely because of that. A `.fexp` of a real
  incident may be a 140MB file to hand over. Say it, and say excerpts are the second delivery's.

**Missing:**
- **An acceptance that the bundle opens usefully with none of the sender's source roots.** The recipient has no
  copy of the sender's repositories. Walk targets will be `CURRENT` — I showed that — but source navigation will
  be dead. The spec should state what a recipient can and cannot do, or EP-A6 will pass while the demo
  disappoints.
- **M69.F3 is listed as a prerequisite and is right to be.** I confirmed the caveat fires per step: a bundle
  opens with Follow off, so on my four-step walk it appeared on every record and chart step and correctly not on
  the `topology:verdict` step. Once per walk is the right target.

---

## The smallest first delivery I would build

| piece | what it is | needs from the analyser |
|---|---|---|
| **1. `analyser --verify <bundle>`** | prints the manifest identity and a per-member verdict; exits non-zero on any mismatch; says in words that it does not authenticate the sender | the whole piece — this is the only new analyser code |
| **2. `generation` and `loadPending` in `context.log`** | two fields | two fields |
| **3. `capture-evidence-bundle` skill** | Friday's capture skill, minus the profile pointer, plus: allow-listed profile export via the existing Settings export, a `manifest.json`, a zip, and a `context` re-read that refuses if the generation moved | nothing beyond 2 |
| **4. `open-evidence-bundle` skill** | unzip to a working copy, call `--verify`, then `open {project}`, `open {log}`, `open {graphml}`, then `walk {play}` | nothing — all shipped |
| **5. M69.F3** | the caveat once per walk | its own regression |

**No new verb. No menu item. No dialog.** If the demo later shows a person without an agent needs it, one menu
item calling the same CLI path is a small, separate decision — made with evidence instead of in advance.

## The line, stated as a rule

The owner's question was broader than §3: where does built-in functionality end and a runbook skill begin? The
rule this review would apply, and which produced the verdict above:

> **Put it in the analyser if a skill would have to guess, or would have to re-implement something a recipient
> must trust. Otherwise put it in a skill.**

Applied here:

| | owns | because |
|---|---|---|
| **analyser** | verification; the log generation and pending-load facts | a second hashing implementation is not a trust anchor; a skill cannot see the generation, so it cannot tell a moved log from a still one |
| **skills** | assembly, excerpting, the narrative, the rerun recipe, what goes in the write-up | all judgement, all varying per investigation — and Friday's skills already do it |
| **UI** | nothing new | the recipient path is three shipped verbs |

### Friday's skills, measured against the bundle

They produced a **working, loadable bundle with no analyser change at all**: log, topology, profile, report PDF,
screenshots and a rerun recipe, in a folder that loads. The skill layer is demonstrably capable of the thing the
spec proposes to build. What it provably cannot do is the short list above, plus two more:

- **travel.** Friday's design leaves the profile in `.analyser/` and ships a *pointer* — right for a colleague on
  the same checkout, useless for a stranger. A bundle must carry the profile, which is what the spec's
  allow-list makes safe.
- **flags.** Nothing to capture (see 3).

They also hold knowledge no UI would have found — the capture skill's §2a records the silent re-anchoring failure
and says *"Found by loading a copied bundle from cold."* That is judgement in prose, and it belongs in prose.

## The honest weakness in this verdict

**C assumes the recipient will run a command.** `analyser --verify bundle.fexp`, then unzip, then open. For a
technical recipient or one with an agent, that is nothing. **For a person you hand a laptop to at a demo, it is a
terminal, and that may be unacceptable.**

So the choice is a question about the audience, not about architecture:

| the recipient is… | shape |
|---|---|
| technical, or has an agent | **C.** No UI, smallest delivery, no new states |
| a person handed a laptop | **C plus exactly one menu item** — *File ▸ Open evidence bundle…*, reusing the existing file chooser, verdict on the status line, **no new dialog** |

Build C first and add the menu item when a demo shows it is needed. A UI surface added speculatively is a state
machine nobody has exercised, which is how this morning's demo broke.

## What I did not do

- No product code changed; this is a review.
- EP-A6 (another machine, another home) is **ASSUMED**, not run.
- The GRAPHS-category external-series path question is **named, not checked**.
- I did not measure the demo's 3 minutes.
