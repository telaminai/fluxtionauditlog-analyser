# Re-review: Spring edit-loop spec, final C1 correction

**Subject:** `f880a3f0` on `docs/spring-session-friction-2026-09-25`, reviewed as the diff
`39368980..f880a3f0` (spec §I1 plus the companion's "C1 boundary clarification").

**Previous review:** `43a117ba`, in `docs/handoff/review_spec_spring_authoring_edit_loop_c1c3_2026_09_26_claude.md`.

This review is documentation-only. C2, C3 and D1–D8 are settled and not reopened.

## Verdict: changes required, one sentence

Five of the seven questions are answered well. The staged-validation point, the path forms, the
link-free guarantee and the mutation are all implementable.

One claim is false: "Relative containment then survives the move" (spec line 559). A root that
leaves the project with `..` and re-enters through the archive root's own folder name passes the
staged check, and escapes after installation (Q6).

The fix is one rule: **the lexically normalised relative root must not begin with `..`**. Every
template root must be a plain descendant path.

With that sentence added, slice 8 may start.

## Answers

**Q1 — Implementable validation point? Yes.**
- READ: `template/TemplateArchive.java:74-88`. After `extract` and `soleProjectRoot`, and before
  `Files.move(root, target, ATOMIC_MOVE)`, the staged root and the staged profile both exist, so the
  check has a natural home (spec lines 547–549, 557–558).
- Refusing there leaves nothing installed. The `finally` block deletes staging and restores an empty
  destination (`:89-97`), which preserves the no-partial-install contract.

**Q2 — Do the path rules match how profiles are read? Yes, with one note.**
- READ: `ProjectProfile.baseDirFor` (`config/ProjectProfile.java:130-139`) anchors a
  `.analyser/project.*` profile at the project directory, not at `.analyser`. The spec says the same
  (line 550).
- `SettingsShare` expands `~` and `~/` against the importer's home (`config/SettingsShare.java:674-679`).
  It keeps absolute paths verbatim, and resolves any other path against that base with **lexical**
  `normalize()` (`:688-700`), called at `:283`.
- So rejecting template home-relative and absolute roots (line 551) matches the loader.
- **Note:** `workspaceRoot` is read and validated (`:274-279`), but it plays no part in resolving
  roots at load. It only shapes how paths are written back (`:150-158`, `toPortable`). The widening a
  template can actually achieve is a `..` in the root itself (see Q6). Rejecting a template-supplied
  `workspaceRoot` is harmless belt-and-braces; it is not the boundary.
- User-authored profiles keep external roots, because the check lives only in the installer
  (lines 568–570).

**Q3 — Missing-directory rule? Sound for containment, with two edges.**
- Canonicalising the nearest existing ancestor and appending a normalised remainder with no `..`
  (lines 555–557) handles parent traversal and filesystem aliases, since both sides are
  canonicalised.
- An existing symlink cannot occur inside the tree (see Q4).
- **Edge 1:** the rule is stated only for *missing* directories. An *existing* root is checked
  canonically, and that is exactly the Q6 gap.
- **Edge 2 (optional):** a non-directory ancestor, such as `pom.xml/sub`, still passes containment
  as written. It cannot escape, but it can never become a directory. Refusing a regular-file
  component would make "invalid forms" (line 557) concrete.

**Q4 — Can extraction create archive-supplied symlinks? No. The assertion is an adequate
replacement.**
- READ: `extract` (`:102-146`) creates entries only with `Files.createDirectories` and
  `Files.newOutputStream(..., CREATE_NEW)`. `setFixedPermissions` only sets permission bits.
- A symbolic-link destination is refused (`:181-182`), and staging is a fresh temporary directory.
- Asserting "no symbolic links under the installed root, including when ZIP metadata requests one"
  (lines 562–565), with a crafted `S_IFLNK` entry, guards the guarantee directly.
- Dropping the unreachable fixture is the right call.

**Q5 — Does the mutation test the right guard? Yes.**
- `safeTarget` inspects only archive **entry names** for absolute paths and `..` components
  (`:149-160`).
- A `sourceRoot.0=../outside` line sits inside the profile's **content**, under a benign entry name,
  so the ZIP-entry guard never sees it.
- Removing the profile-root check therefore fails the `../` refusal assertion on its own (lines
  567–569), provided the fixture's entry names are ordinary. The spec says so.

**Q6 — Does containment survive the move? No. This is the required correction.**
- The staged project root takes the archive's folder name, but the installed root is the
  user-chosen destination.
- A root such as `../<archive-root-name>/src/main/java` resolves inside staging, and outside the
  project after the move: to a sibling folder with the archive's name.
- **RUN** (a JDK 21 single-file program using the loader's own `resolve(...).normalize()` and a real
  `ATOMIC_MOVE`, in a scratch directory):
  - staged check: `contained=true`;
  - after moving to `my-project`: resolves to `<parent>/spring-demo/src/main/java`, `contained=false`.
- **Fix:** refuse any template root whose lexically normalised relative form begins with `..`
  (before resolving it), in addition to the canonical check. A descendant-only relative path keeps
  its containment under any rename or move of the project root.
- Add the re-entry root as a negative install fixture.
- The limit sentence at lines 559–560, "does not promise safety against later filesystem changes",
  is correctly scoped and should stay.

**Q7 — Can slice 8 start? Yes, once the Q6 sentence is added.** Nothing else blocks it.

## Required correction

1. **§I1, around lines 555–560:** require every template root's lexically normalised relative form to
   be a descendant path, with no leading `..`, and add the re-entry fixture. Without this, the
   "survives the move" sentence is false.

## Optional

- **Q3 edge 2:** refuse a regular-file path component, making "invalid forms" concrete.
- **Maven repos:** say whether template-supplied `mavenRepo` locations are validated or explicitly
  exempt. They are also template-supplied read locations, though narrower (source jars only), and a
  default `~/.m2` repository is legitimate.
- **Anchor `.`:** rejecting *any* nonblank template `workspaceRoot` also rejects the harmless anchor
  `.`. That is fine as a strictness choice, but worth stating as deliberate.

## Evidence and limits

- **READ:** the correction diff; `TemplateArchive`, `ProjectProfile`, `SettingsShare`, `PathForm` at
  `f880a3f0`.
- **RUN:** the scratch Java program above (JDK 21, a temporary directory, deleted with the job);
  searches and diffs.
- **Not run:** no Maven test, MkDocs build, installer or product scenario. Link targets are
  unchanged, and the author reports the strict docs build and sweeps.
- **Private repositories:** none were needed for this review.
- **Uncertain:** whether a real template would ever ship such a root. The rule matters because the
  archive, and so the profile, is untrusted input to the installer.
