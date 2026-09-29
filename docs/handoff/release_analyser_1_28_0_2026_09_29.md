# Release 1.28.0 — evidence replay and Swing workspace (2026-09-29)

PR #71 is **merged**, not closed without a merge: merge commit `bce667f5c85caed27bd3bc75fb268fb77d812eb6`, authored with the personal address. Its tree is byte-identical to the final reviewed branch head `3cc8db1b402809627a9e5920c55879e2feb3ebe7`. The release workflow stamped `CHANGELOG.md` and tagged `edc275e6a1183f18464c09feee319f686f3a889a` as `v1.28.0`.

## Gates and publication

- [Final PR-head CI](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36584296461): success; build, UI frame, loop bench, mutation self-test, all four mutation shards and collector passed.
- [Merged-main CI](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36585070827): success at `bce667f5`; the same jobs passed on the integration commit. [Main static checks](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36585070547) passed. The native screenshot refresh and affected guides passed strict MkDocs and the rule-1 sweep before merge.
- The release smoke scripts passed on the final branch: `verify-m46-agent-api.py`, `verify-m48-handoff.py`, `verify-m64-spotlight.py`, `verify-session-restart.py`, and all seven `capture-conversations.py --require-images` scenarios. The five native conversation images and 28 native documentation images were inspected using DEMO data and an isolated home.
- [Release workflow](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36586608547): success, including Maven verify, changelog stamp, atomic tag/main push, both jars, checksums, GitHub release and explicit Pages dispatch.
- [Published v1.28.0](https://github.com/telaminai/fluxtionauditlog-analyser/releases/tag/v1.28.0): neither draft nor prerelease. Downloaded the versioned jar, stable-name jar and `SHA256SUMS.sha256`; both SHA-256 digests match. The versioned jar's manifest says `Implementation-Version: 1.28.0` and its bundled `release-notes/CHANGELOG.md` has the stamped 1.28.0 section. The `releases/latest` API resolves to v1.28.0, and the public latest-download jar hashes identically to the stable-name release asset.
- [Release-notes Pages deployment](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36586822751): success at the stamped release commit. The [published release-notes page](https://telaminai.github.io/fluxtionauditlog-analyser/release-notes/) returned HTTP 200 and contains the dated 1.28.0 section.
- The tracked-file public-data sweep printed nothing. The merge author and all new authored commits use the personal address; the historical employer-domain count was unchanged.

## Still open

M71.F1 remains optional: improve the tour's teaching sequence, narrow report/list reading width and the template dialog's choice hierarchy. Other tracker follow-ups retain their existing status. This receipt does not claim acceptance beyond the gates above.
