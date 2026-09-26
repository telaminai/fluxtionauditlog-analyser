# Re-review: final C1 correction, descendant-only template roots

**Subject:** `87672a42` on `docs/spring-session-friction-2026-09-25`, reviewed as the delta `f880a3f0..87672a42`.

**Previous review:** `23f47a1c`, in `docs/handoff/review_spec_spring_authoring_edit_loop_c1_final_2026_09_26_claude.md`.

This is a narrow documentation re-review. C2, C3 and D1–D8 are not reopened.

## Verdict: changes required, one clause

The descendant-path rule closes the demonstrated escape, and every point asked about checks out.
One accepted input conflicts with approved D3: a root that normalises to the **project root
itself**. Refusing it is one clause.

**Slice 8's containment work can start now.** Add that clause before its acceptance fixtures are
frozen.

## Findings

**1. The rule closes the re-entry escape. RUN and READ.**
- Spec lines 556–563 refuse a root whose lexically normalised relative form begins with `..`,
  before it is resolved against staging.
- RUN, a JDK 21 scratch program using `Path.normalize()` (the loader's expression, READ at
  `config/SettingsShare.java:688-700`):
  - `../spring-demo/src/main/java` → refused;
  - `a/../../x` → `../x`, refused;
  - `src/main/../../../x` → `../x`, refused;
  - `..` → refused;
  - `src/../src/main/java`, `./src/main/fluxtion/designer` and `target/generated` → accepted as
    descendants.

**2. The survival claim holds. RUN and READ.**
- A normalised relative path with no leading `..` stays lexically under whatever directory it is
  resolved against, and the loader resolves lexically.
- RUN: a descendant root was contained both in staging and after a real `ATOMIC_MOVE` to a
  differently named destination.
- **Missing directories:** the nearest-ancestor rule is kept (lines 564–566).
- **Aliases:** both sides are canonicalised (lines 562–563).
- **Existing links:** none can come from extraction (READ `template/TemplateArchive.java:102-146`,
  `:181-182`).
- The later-filesystem-change caveat is intact (lines 569–571).

**3. The fixture and mutation are well aimed. READ.**
- The re-entry fixture uses a differently named destination and ordinary entry names (lines
  584–587). Its refusal comes only from the leading-parent guard, because canonical containment
  passes in staging.
- So removing just that guard fails the fixture, as line 587 requires.
- `safeTarget` checks only entry names (`TemplateArchive.java:149-160`), so it cannot mask the
  result.

**4. `workspaceRoot` is described accurately. READ.**
- It is read and validated (`SettingsShare.java:274-279`), but `resolveAgainstBase` never takes it
  (`:688-700`).
- The spec now says this (lines 552–554) and keeps rejection of any nonblank template anchor,
  including `.`, as a deliberate extra restriction, not the boundary.
- User-authored external roots stay supported (line 592).

**5. The optional points are adopted without contradiction. READ.**
- **Regular-file component:** the loader does not reject roots that point at files; they simply
  resolve to nothing. So refusing `pom.xml/sub` at install (lines 567–568) is a stricter installer
  rule, scoped to template profiles, and adds no loader behaviour.
- **`mavenRepo`:** exempting template `mavenRepo` locations (lines 573–576) matches the narrower
  existing access. The repository resolver searches only `*-sources.jar` archives. The text claims
  no source-root grant for them.

**6. Scope. RUN.**
- Only the spec and the companion response changed (`git diff --name-only`).
- The tracker diff is empty, and no decision row moved.
- The response labels its scratch program as path mechanics, not installer acceptance.

## Required correction

1. **Refuse a root that resolves to the project root itself.**
   - `src/..`, `.` and similar inputs normalise to the empty path. RUN: accepted by the
     leading-parent rule. They are contained, so every current check passes them.
   - But a template profile declaring the whole project as a source root is an implicit
     whole-project read grant, contradicting §I1 line 527 ("not an implicit grant to the entire
     project") and approved D3 (line 777, "No implicit whole-project grant").
   - **Fix:** in lines 556–563, also refuse a template root whose normalised form is empty (the
     project root), and add it to the negative fixtures (lines 580–584).

## Optional

- **Windows root components (READ, not run here):** where roots have a root component but are not
  absolute, such as a drive-relative `C:foo` or a rooted `\foo`, `Path.isAbsolute()` is false while
  `resolve` does not stay under the base. Stating "refuse any root with a root component" (that is,
  `getRoot() != null`) instead of only "absolute" (line 551) would cover them. This could not be run
  on this macOS host.

## Evidence and limits

- **READ:** the delta; `SettingsShare`, `TemplateArchive` at `87672a42`.
- **RUN:** two scratch JDK 21 programs (path normalisation, and the atomic move of a descendant
  root) in the job's temporary directory; `git diff --name-only`; `git diff --check` on this file;
  the rule-one sweep.
- **Not run:** `SpecLinksResolveTest`, because no link targets changed. No MkDocs build, Maven
  suite, installer run or product scenario.
- The author's recorded checks were not treated as mine.
