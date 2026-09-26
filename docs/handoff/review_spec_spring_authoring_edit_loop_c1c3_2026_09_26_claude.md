# Re-review: Spring edit-loop spec corrections C1–C3

**Subject:** `39368980` on `docs/spring-session-friction-2026-09-25`, reviewed as the correction
diff `13c44071..39368980`.

**Previous review:** `1bae2480`, with addendum `2fccb5f0`, in
`docs/handoff/review_spec_spring_authoring_edit_loop_v4_2026_09_26_claude.md`.

D1–D8, as approved in `13c44071`, are treated as settled. This review covers only the corrections
and any contradictions they introduce. It is documentation-only: no edits to the subject documents,
no participant access, no client session, key, merge or publication.

## Verdict: changes required, one short C1 clarification

C2 and C3 are resolved. C1 now puts the check in the right place, but its fixtures and boundary
are not yet implementable through `TemplateArchive` as it actually installs. The four points below
fit in one paragraph.

Slices 1–7 may start now. Slice 8's containment work should wait for that paragraph.

## C1 — template-root containment: placement right, boundary underspecified

**Resolved:**
- **Placement:** the check is at install time, "where the profile is known to be template-supplied,
  not in generic profile loading" (spec lines 547–548).
- **No typed key:** there is no typed design-root key, so every template profile root is checked
  (lines 548–550).
- **Positive control:** a user-authored profile keeps an explicitly granted external monorepo root
  (lines 556–558). READ: `config/PathForm.java:13,34` supports that form, so existing behaviour is
  preserved.
- **Tracker:** the tracker line matches.
- **Separation:** archive-entry safety (`safeTarget`, entry limits) is a separate existing guard,
  and the spec does not conflate it with profile-root grants.

**Required clarification.** READ `template/TemplateArchive.java:64-100, 102-150, 181-182`.

1. **The symlink fixture cannot come from an archive.**
   - Extraction writes only directories and regular files, with `CREATE_NEW`.
   - A populated or symbolic-link destination is refused.
   - So no archive can plant a link inside the installed tree, and "a root resolving outside through
     a symlink" (lines 554–555) has no archive-level fixture.

   **Fix:** state that link-freedom comes from extraction, and assert it (no symbolic link exists
   under the installed root). Then either:
   - test symlink resolution through a validation seam that plants a link in the staged tree
     before the check; or
   - drop that fixture as unreachable.

   The `../` and absolute-path fixtures remain the real install tests.
2. **"Installed project boundary" does not exist when the check must run.**
   - The installer extracts to a sibling staging directory, then makes one atomic move (lines
     73–88).
   - Refusing after the move would need an uninstall, contradicting the no-partial-install
     behaviour that line 551 preserves.

   **Fix:** validate against the **staged project root**, canonicalised, before the move. Relative
   containment carries over unchanged. Canonicalise both sides: comparing a canonical root with a
   non-canonical boundary false-refuses under a symlinked parent such as `/tmp` → `/private/tmp`.
3. **Use the loader's own resolution forms.**
   - A stored root may be project-relative, workspace-relative (`..` under a declared
     `workspaceRoot`), `~/…`, absolute or plain relative (`PathForm.Form`).
   - `workspaceRoot` is a profile family (`KnownKeys`).

   **Fix:** resolve each template root exactly as profile loading will. Treat a **template-supplied
   `workspaceRoot`, `~/` or absolute root as escaping**; otherwise a template can widen its own
   boundary with a `..` anchor.

   "Exercise actual canonical resolution, not just the spelling" (line 556) points this way, but
   does not name these forms.
4. **Roots that do not exist yet.**
   - "Refuse … if containment cannot be established" (lines 550–551) would refuse a legitimate
     template root that does not exist at install time, such as a future `target/…` root.
     `toRealPath` fails on a missing path.
   - This is latent today, because the emitted roots exist.

   **Fix:** define the rule. For example, canonicalise the nearest existing ancestor, then append
   the lexically normalised remainder, which may contain no `..`.

**The mutation** (disable the install-time check; the `../` fixture must fail its containment
assertion) is implementable as written.

## C2 — slice 7 and D1: resolved

- §B (lines 144–147) says slice 7 removes fabrication together with D1's audited rejection path,
  including blank and short rows. It also says that no interim raw-input, null or exception
  substitute that leaves rows unaccounted is approved.
- Slice 7 (lines 762–763) says the same.
- The upstream asks say the same (upstream-asks.md, lines 1969–1970).
- RUN: a search of all four documents for "start independently" or "removing fabrication" finds no
  remaining independence claim.

**Nit (optional):** the companion review's feedback-6 routing (review_spring_authoring_feedback,
line 58) still reads "remove fabrication; full visible rejection requires …". That could be read as
two steps. "Together with" would match §B.

## C3 — conversion ownership: resolved

- §I3 (lines 639–642) places any application-specific conversion in the application or in a
  separate integration module. The reusable vendor jar emits its own or the shared contract, with no
  dependency on demo classes.
- It explicitly "does not reopen D7".
- A search finds no remaining option (b) wording in the governing text; the companion response
  (line 332) mentions it only as removed.
- The boundary is coherent with the D7 shared-interface choice.

## Also checked

- **O3, resolved:** lines 583–585 now state that `startedAt` is set at admin-service start,
  truncated to whole seconds and fixed across registry refreshes, with one-second precision. READ:
  mongoose-plugins v1.0.44 `WebAdminService.java:238-242` (assignment) and `:705-711` (refresh does
  not change it).
- **O1 and O2:** they remain optional and are labelled unresolved in the companion response. They
  are not raised here.
- **Status marks:** RUN, `git diff 13c44071..39368980 -- docs/specs/tracker.md`. No ☐/☑/◐ line was
  added, removed or changed, so no implementation status advanced.
- **Public data:** RUN, the rule-one terms, local paths and address scan of the added lines: 0 hits.

## Evidence and limits

- **READ:** the correction diff; the companion "V4 re-review response — C1–C3" section;
  `TemplateArchive`, `PathForm`, `KnownKeys` at `39368980`; the mongoose-plugins v1.0.44 registry
  start and refresh code.
- **RUN:** the searches and diffs named above.
- **Not run:** no Maven test, because no links changed in the governing text and the author reports
  the link and strict-docs checks. No MkDocs build, mutation, install or display check, and no
  product scenario.
- **Uncertain:** whether the implementer prefers a validation seam or dropping the symlink fixture
  (point 1). Either satisfies the boundary, once stated.
