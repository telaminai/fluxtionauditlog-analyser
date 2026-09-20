# Choice-neutral comment contract — 2026-09-20

Playground `47b9952` consumes the resource in the locally packaged starter artifact. Its exact coordinate,
resource path and artifact/resource digests are retained in artifact-manifest.json. Provenance is explicitly
local-development. No private repository is required to verify a future published artifact.

Verified locally:

- Java clean package: 84 builder + 47 starter tests, no failures or skips. The DATA→TRIGGER→DATA test
  preserves the generated neutral comment and developer method, compiles both states and checks repeated
  reconciliation is a no-op. Existing cross-language ownership/removal cases also pass.
- Jar check: 2,060 classes, Java 17 base floor, canonical resource bytes packaged unchanged, keyless validation.
- Browser/Java direct wording parity: real browser source and fresh Java-emitted source are compared with
  the packaged resource for event/trigger, reference, parent-update, lifecycle, export and sink constructs.
- Seen red: mutate only the browser renderer. Parity fails at browser reference while the same test suite's
  ownership-hash equivalence check passes. Source restored in finally, then parity passes. The initial
  runner also required Vitest to print the mutated value, which it elided; the corrected runner checks the
  actual failing assertion and was repeated. Logs have terminal escapes/trailing whitespace normalised.
- Playground full gate: 504/504 with the new local jar; production build passes. A final 23-test focused run
  also checks downloaded resource digests. Type check retains four existing SplitPane errors and six warnings.

Publication is not verified: the actual authoritative Repsy request returned HTTP 404 (retained log).
The playground smoke workflow requires public artifact verification and must stay red until that artifact
is published and the resource re-vendored from it. Snapshot/local artifact digests are informative only;
resource bytes are the content parity oracle. Released immutable coordinates additionally enforce the jar digest.

Reproduce from web/ with Java 21 and the local jar supplied explicitly:

```sh
node scripts/verify-comment-contract.mjs --local-artifact <local-starter-jar>
FLUXTION_STARTER_TEST_JAR=<local-starter-jar> pnpm vitest run src/lib/starter/comment-contract.test.ts
# Optional red witness; use an isolated implementation checkout, no concurrent edits/tests.
FLUXTION_STARTER_TEST_JAR=<local-starter-jar> python3 <this-packet>/mutate-browser.py
```

The mutation helper writes its log under /private/tmp and restores the exact original renderer bytes.
The resource/provenance shipped in Spring projects does not make the live prose reference version-safe;
full task-reference compatibility and selected diagnostic improvements remain open. No runtime default,
application behaviour, existing class ancestry or developer body was changed to add this guidance.
