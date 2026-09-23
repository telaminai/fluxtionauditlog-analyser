# Independent review: Java source spotlight

Review `feat/java-source-spotlight` against main `809303f7`. Work in your own worktree; do not alter the
primary checkout or the author's implementation worktree. Start with `docs/ONBOARDING.md` and `CLAUDE.md`,
then read the whole [proposal](../proposals/source-spotlight.md) and
[implementation report](report_java_source_spotlight_2026_09_23.md). The proposal's accepted review,
S-1/S-2 and N-1 dispositions are part of the contract. Inspect the actual diff; do not take green on trust.

Attack these boundaries:

1. A mixed record-row/Java request must prepare before any filter or selection change. Source I/O and
   model parsing must not run on EDT. A missing second document leaves the view and bindings untouched.
2. Deadline, clear, interruption, configuration/selection change, log/project transition, newer requests
   and user view changes must prevent late publication. In particular, an expired or cancelled caller
   must not light later or clear another request's newer target.
3. The selected processor model and displayed text must come from the same snapshot. Origin travels with
   cached text; duplicate-root and sources-jar behavior intentionally differ from `source {fqn}`.
4. Registry bindings must measure their original revision/pane through scrolling, resize, mode changes,
   refresh, clear, replacement and dismissal. `add` must account for departures and one-document limits.
5. Java logical lines include all wrapped visual rows and clip with `partial`; design keeps the released
   containment refusal and has no `partial` field. Both viewers must remeasure on viewport change.
6. Check the visible label, rendered screenshot and MCP echo/context together. Displaying source never
   claims that those bytes built the loaded run or that a highlighted statement executed.

Run:

```sh
mvn clean test
mvn package
python3 tools/test_tools.py
python3 tools/verify-m64-spotlight.py
mkdocs build --strict
```

Run the exact real-display class list in `.github/workflows/ci.yml` with `-Djava.awt.headless=false` in
Maven and `-DargLine=-Djava.awt.headless=false` for the test JVM. Both `JavaSourceSpotlightFrameTest` and
`DesignSpotlightFrameTest` must appear in **both** CI lists. Require nonzero tests, no skips, no failures
or errors. Linux needs `xvfb-run -a`; a headless skip is not evidence of closure.

Repeat mutation witnesses in a disposable worktree, with a real display:

```sh
python3 tools/verify-java-source-spotlight.py
```

The helper restores exact source bytes after each mutation and checks JUnit's named assertion failure;
it refuses to count compilation errors or skips. It must run alone: it temporarily edits source files.
Use `--only <name>` for one witness. Read `docs/handoff/evidence/java-source-spotlight-2026-09-23/mutations.json`;
attack the evidence checker if a false-positive path is apparent. Rebuild after mutations before other gates.
Run `git diff --check` and CLAUDE rule 1's exact tracked and untracked sweeps.

Write `docs/handoff/review_java_source_spotlight_2026_09_23_<reviewer>.md` on your own review branch,
commit and push it. Give a verdict, numbered findings with file:line and reproduction, and distinguish
run/observed from source-read or unverified claims. Do not fix findings, edit the author's report, merge,
release, amend or force-push. Report the review branch, commit and report path to the author/owner.
