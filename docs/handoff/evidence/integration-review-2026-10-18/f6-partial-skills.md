# Integration F6 — a partial skills write is not zero writes

Owner: `feature/propose-starter` (#167); relevant to `fix/starter-followups`' digest record. Before-fix production: `5eb46b5d`.

Create a regular file at `.claude/skills/spring-authoring/reference` inside a temporary DEMO project. The real `StarterSkills.vendor` writes earlier skills, then fails creating that support directory. Its report includes written skills and a refusal; the session said “No skills were added”. The combined review also always said “its skills are written”, even with an empty written list.

Executed before fix: **2 / 2 / 0 / 0**:

- `StarterProjectTest#partialSkillWriteDoesNotClaimNothingWasAdded`: the real writer's earlier files and nonempty written list are asserted first; the generated processor then fails `a partial write must not be reported as zero writes`.
- `StarterProposalFrameTest#theDesignRowIsPreTickedAndTheOthersAreNot`: the actual review panel with no written skills fails `the review must not claim successful skills when none were written`.

Correction: the node records and presents an incomplete outcome with retained-file qualification. The dialog describes the list of successfully added skills, not unconditional completion. The existing continue-to-discovery policy is unchanged. Documentation now explicitly says writes are not transactional and the digest record is written at the end (an existing record is kept). This does not implement rollback, atomic vendoring or a repair mechanism. No topology change or regeneration was required. Final headless/display counts are in the review report.
