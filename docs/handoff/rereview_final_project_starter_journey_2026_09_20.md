# Final re-review — J1–J3 corrections

Date: 2026-09-20 · Reviewer: the session that wrote the
[first review](review_project_starter_journey_2026_09_20.md), both earlier re-reviews, the
[consolidated review](review_all_specs_2026_09_20.md) and the
[H-series re-review](rereview_all_specs_2026_09_20.md) · Scope: the **current working tree**, tracked and
untracked.

Nothing was committed or pushed, no branch switched, no existing spec, review or response edited, and the
staged application untouched. No key, no publication, no implementation edit.

## Inspected heads

| Repository | Head | Tree |
|---|---|---|
| `fluxtionauditlog-analyser` (reviewed) | `703136e` | the two specs, `tracker.md` and the handoff documents are the subject; `tools/`, `CHANGELOG.md` and the Mermaid fix are unrelated and unjudged |
| `fluxtion-web` | `e8e70f0` | 2 unrelated (vitest cache, `.svelte-kit/`) |
| `fluxtion-playground-libs` | read only, working copy | the artifact mirror behind K1 |
| `mongoose/mongoose` | `17a03b4`, POM `1.0.30-SNAPSHOT` | clean |
| `fluxtion-compiler` main checkout | `aec606ec` | read only |

---

## Verdicts

| Subject | Verdict |
|---|---|
| `docs/specs/spec-template-from-analyser.md` | **READY FOR IMPLEMENTATION.** Unchanged by the J corrections — its 74 insertions are the earlier D-1/F1 revision, and a search for comment-contract, digest, snapshot and cache terms in its diff returns nothing. No F, G or H closure regressed. |
| `docs/specs/spec-project-starter-journey.md` | **READY FOR IMPLEMENTATION**, subject to K1 as a naming correction rather than a design question. J1–J3 are closed; K1 and K2 are one sentence each and neither reopens a settled decision. |
| Combined implementation handoff | **READY**, with the sequencing below. Slices 1, 3 and 4 can start today. Slice 2 is unblocked — J2's stamp landed. Slice 5 needs K1 and is additionally gated on the starter artifact's publication, which is a pre-existing release gate rather than anything this design introduced. |

All owner decisions remain intact: support defaults on including old links with explicit false staying off,
reopen and relaunch share one explicit restore offer, the full catalogue shows with recommendations rather
than a filter, guided means a runbook walkthrough, and the analyser renders evidence and stores pointers while
execution stays with external tools. No guide verb is approved.

---

## J dispositions

**J1 — public starter-artifact resource, released-version pin, digest verification, no closed-repository
access: CLOSED.**

`:161-172` specifies a versioned UTF-8 resource at `META-INF/fluxtion/starter/comment-contract.json` inside
the released `com.telamin.fluxtion:fluxtion-starter-core` jar, classifier `all`; Java emission reads the
resource, the playground vendors the copy extracted from the same published artifact and pins its released
version via `starterCore`, recording coordinate, artifact SHA-256 and resource SHA-256. It forbids pinning
public guidance to a closed-repository commit or requiring private credentials, and requires CI to compare
vendored bytes against the resource in the pinned public artifact, failing on a missing artifact, missing
resource or digest mismatch. Acceptance 11 `:408-412` carries it, explicitly "without compiler-repository
access", with missing resources and altered vendored bytes required to fail, followed by the existing
direct-text comparison and one-emitter mutation.

That answers the finding exactly. Three things I checked rather than accepted:

- **The coordinate is real and already load-bearing.** `starterCore` exists as the independent pin added for
  G9, and the generated `setup.sh` already resolves `com.telamin.fluxtion:fluxtion-starter-core:$STARTER_VERSION:jar:all`,
  so CI would verify the same coordinate a user's project downloads.
- **The distribution mechanism extends existing capability rather than inventing one.** The playground already
  fetches Maven-layout jars at build time — `web/scripts/fetch-libs.mjs` with eleven entries in
  `lib-manifest.json`. Extracting one resource from one more jar is the same shape of work.
- **The spec is honest about what does not exist.** `:170-171` states that packaging and publishing the
  resource remain implementation work and that "its presence in any existing release is not asserted", and
  `:169-170` allows a local artifact for development while refusing it as the publication gate. That is the
  distinction the brief asked for, stated by the author rather than left for a reviewer to find.

**J2 — snapshot attribution with acceptance on the resolved version: CLOSED.** `:118-121` now opens the
Mongoose recital with "Source observation, not a released-version guarantee", names
`com.telamin:mongoose:1.0.30-SNAPSHOT` at local source `17a03b4`, contrasts it with the inspected playground
pin of `1.0.29`, and states that the recital "is not the acceptance oracle". Acceptance 8 `:389-390` requires
expected defaults to be derived "from that pinned artifact, not the snapshot observations above". Both halves
of the finding are answered: the attribution and the oracle.

**J3 — consumed-cache semantics: CLOSED.** `:125-128` now states that dispatch sends the pending cached
prefix to subscribers present at that moment and advances the publisher-wide pointer, that a later subscriber
does not receive the consumed prefix, and that another dispatch with no new cached entries sends nothing.
This matches `EventToQueuePublisher.dispatchCachedEventLog` (`:170-183`), where the loop runs from
`cacheReadPointer` and the pointer is then set to `eventLog.size()`. The response's caveat that this "does
not claim that later newly cached entries can never be dispatched" is also correct against that code, and the
spec's wording ("with no new cached entries") preserves it. Acceptance 8 `:388-389` exercises a late
subscriber and a repeat dispatch against the resolved connector's contract.

**F, G and H closures are intact.** I re-read the corrected regions and none of the three edits touches the
choice-neutral comment rule, the fallback exception, the adoption threshold, the report/non-regression split,
the emitter-parity mutation, the panel/landing split, the override precedence or the duplicate-path
assertion. The template spec is untouched by this round.

---

## New findings

### K1 — Moderate. "The publicly resolved artifact" names no endpoint, and the two public sources that exist serve different purposes

`docs/specs/spec-project-starter-journey.md:167-169` requires CI to compare vendored bytes with "the resource
in the pinned public artifact"; acceptance 11 `:408-409` says "the publicly resolved `starterCore` artifact".
Neither names where CI resolves it from, and two public sources already exist with different contents and
different guarantees:

- `https://repo.repsy.io/mvn/fluxtion/fluxtion-public` — the Maven repository the generated project's
  `setup.sh` and POM actually resolve from (`fluxtion.ts:8`).
- `https://raw.githubusercontent.com/telaminai/fluxtion-playground-libs/main/libs` — the GitHub mirror the
  playground's own build-time fetcher uses (`lib-manifest.json`, `repoBase`). It is a curated copy: it
  currently holds builder and runtime artifacts only, and no starter entry.

Failure scenario: CI is implemented against the mirror because that is what `fetch-libs.mjs` already does, the
mirror copy lags a re-release, and the parity gate passes green while users downloading from repsy receive a
jar whose comment contract differs from the vendored bytes. The check would then be verifying a copy of a
copy, which is the drift the canonical-owner rule exists to prevent.

Required correction: name the authoritative endpoint, and make it the one `setup.sh` resolves from, so the
gate verifies the same bytes the user's project downloads. If the mirror is used for build convenience, say
that it must be validated against the Maven coordinate rather than treated as the source of truth.

### K2 — Minor. An artifact-level SHA-256 is the fragile half of the pin, and this repo already knows why

`:166` requires recording "artifact SHA-256 and resource SHA-256".

The resource digest is the right oracle and is stable. The artifact digest is only meaningful for an immutable
released coordinate. The playground's existing fetcher deliberately avoids hashing whole jars for this exact
reason — `fetch-libs.mjs:9-11` explains that the size field is a cache key because "local snapshots drift in
size due to Retrolambda repack", and the verification at `:43-45` is a size comparison, not a digest.

Failure scenario: a developer pins an artifact digest taken from a locally built or snapshot `-all` jar,
a rebuild of the same version produces different bytes, and the gate fails with nothing substantive wrong —
or the team relaxes the check and loses the resource digest with it.

Required correction: one clause — the artifact digest applies to immutable released coordinates only; the
resource digest is the parity oracle in all cases.

Neither K finding is a design question, and neither blocks a slice that was previously unblocked.

---

## Which slices can start, and what remains

**Now**

1. Playground — `analyserSupport`, default and query override, with captured legacy-link fixtures.
2. Playground — factor the emitters out of the bundle, one owner per path with the uniqueness assertion, and
   the capability-specific Mongoose runbooks. J2's stamp removed the last condition on this slice.
3. Playground — headless extension and the empty-directory recipe in `web/static/CLAUDE.md`.
4. Analyser — `context.savedGraphs`, project landing, project-scoped session state and the unified restore
   offer replacing the implicit opens in `Main.main` and `reopenLastGraphml`.

**After K1**

5. Compiler/starter and playground — comments and diagnostics. Note the sequencing this design inherits
   rather than creates: the comment-parity CI gate cannot be green until a starter artifact carrying the
   resource is published, and `starterCore` currently pins `1.0.72`, which is unpublished and is the same
   release gate the Spring authoring work already waits on. The spec's local-artifact allowance keeps
   development moving in the meantime.

**Then** the full-catalogue picker, the optional authoring section, and the witnessed acceptance runs.

**Prerequisites still to be designed, unchanged and correctly still open:** the additive metadata schema for
processor states; registry-backed recipe plugin coordinates and version floor; diagnostic messages selected
from reproduced failures; authoring-page layout; and the vendor template coordinate with M67 coordination and
the feedback-29 shadowing fix. None blocks slices 1–4.

---

## Verified versus read

**Executed**

- `mvn -q -o -Dtest=SpecLinksResolveTest,ProjectPanelIsRevealOnlyTest test` → **5 tests, 0 failures, 0 errors,
  0 skipped**, re-run with this document present.
- `git diff --check` → clean, exit 0.
- CLAUDE.md rule-1 sweep, both halves: tracked files print nothing beyond the two that state the rule; the
  same sweep over untracked files, including every spec and review document and this one, prints nothing.
- `git diff --stat` and a keyword search over the template spec's diff, confirming it carries no J-era edit.

**Read in source for this pass**

`fluxtion-web/web/scripts/fetch-libs.mjs:5-52`, `lib-manifest.json` (repoBase and eleven entries),
`web/src/lib/starter/fluxtion.ts:8` (the repsy base), `example-versions.json`;
`fluxtion-playground-libs/libs/com/telamin/fluxtion/` contents; mongoose
`EventToQueuePublisher.java:170-190`, `pom.xml:7`; and the corrected regions of the journey spec at
`:118-136`, `:156-179`, `:382-394` and `:400-414`, plus the response's J section and the tracker entry.

**Not verified**

- No UI, no template generation, no Mongoose server, no artifact fetch or publication, no fresh-client
  session. K1 and K2 rest on reading the two fetch paths and the existing verification code.
- Whether the shaded `-all` jar is byte-reproducible across builds. K2 is argued from this repo's own recorded
  experience with repacked artifacts, not from a reproducibility experiment I ran.
- The comment resource does not exist yet in any artifact; the spec says so, and I did not attempt to fetch
  one.

## Housekeeping

This file is the only addition. Both specs, the tracker, the response and every earlier review are exactly as
I found them, as is the unrelated working-tree work.
