# Option C author checks — 2026-11-03

Branch `spec/analyser-context-canvas`; base proposal `f4e96ba2`, main `10841b2a`.
This is a documentation/specification change. No new canvas, installer flag, Java implementation or release.

- Original proposal review: `review_option_c_2026_11_03.md`; source checked against main and remote SHAs.
- Full JDK 21 `mvn -o -q test`: **4,475 total, 4,094 passed, 381 skipped, 0 failures, 0 errors** (627 XML reports).
  First sandbox run had 106 process-inspection errors (`sysctl failed`); unrestricted rerun exited 0.
  Skipped tests are not desktop acceptance, and this is not evidence that the unimplemented canvas works.
- `git diff --cached --check`: PASS. After staging new files, `SpecLinksResolveTest` and
  `TrailingWhitespaceTest`: **5 tests passed, 0 failures/errors/skips**.
- Public tracked-text sweep: original seven proposal commits each had zero matches outside the two required exclusions.
  Final staged tree sweep also returned zero matches outside those same two exclusions.
- Review findings R1–R8 have design dispositions, with implementation tests still required.
- Skill prototype separately lives in web branch `feat/analyser-setup-skill`; it uses existing 1.40.2 UI.
- Real JBang 0.123.0 install into a temporary `JBANG_DIR` succeeded after owner chose Trust once. It also added a
  temporary PATH/alias block to the real `.bashrc`: that exact suffix was removed, preserving prior content.
  This found that `JBANG_DIR` alone does not isolate shell setup. Future install trials must isolate Java user.home
  as well, or deliberately authorise/document profile edits. The skill now discloses JBang environment setup.
- Generated app launcher invokes `jbang` by name. Full spec now requires transitive launcher resolution from a GUI
  host; an absolute script path is not sufficient evidence. No MCP registrations or credentials were changed.
- No push to main, merge or production change is part of this work.

Independent review and recheck completed: `handoff_2026_11_03_option_c_report.txt`.
Four documentation/prototype findings addressed; future implementation and cold-host release gates remain open.

## External onboarding follow-up — 2026-11-03

Specification §2.1.1 now records the external skill review at web commit 823b50f: 6 PASS, 2 FAIL, 1 BLOCKED.
The native Claude context call used a per-server PATH, temporary registration and test-only external-access
flag; this does not pass the intended consent/reload journey. The verbatim report lives in web branch
feat/analyser-setup-skill at docs/handoff/handoff_2026_11_03_analyser_setup_report.txt.

C0 requires explicit launcher environment, advance trust/JAVA_HOME disclosure, supervised timeout recovery,
executable checksum validation and rejection of the --rest recovery shortcut. C1 must generate the corrected
environment itself. No Analyser runtime source was changed.

Executed targeted SpecLinksResolveTest and TrailingWhitespaceTest: 5 passed, no skips or failures.
Tracked-file public sweep passed excluding only the two rule-definition files. Full suite results above remain
prior evidence; the full runtime suite was not repeated for this specification-only correction.

Round 2 received during corrections: external report at web branch
docs/handoff/handoff_2026_11_03_analyser_setup_report_round2.txt records a real macOS/Claude Code terminal
UI-consent/registration/context pass on 4a4289c. The updated spec distinguishes this from round 1 and retains
untested GUI, clean-account, UI-cancel/conflict and targeted corrupted-cache recovery gates. Independent
review also required resolving the JBang executable separately from its app directory for package-manager
layouts; the corrected skill's isolated recipe suite now has 13 passing tests.
