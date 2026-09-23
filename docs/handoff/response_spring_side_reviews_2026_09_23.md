# Spring-side work block — response to both independent reviews

Work is isolated on `fix/spring-side-review-response`. Neither review file was edited.
Read reviewer G (`6d5c68c8`, compiler) and the [second review](https://github.com/telaminai/fluxtionauditlog-analyser/blob/review/spring-side-work-block/docs/handoff/review_spring_side_work_block_2026_09_23_claude.md).
Their differing verdicts were not resolved by choosing the favourable one.
Predictions were committed before trials: `b957efed` for G18/G17/G19/G20 and
`de17692c` for H1/M1–M6. No merge, rebase, force-push, binary publication, deployment,
owner key or new client trial.

## Reviewer G dispositions

| Item | Response / commit | Status |
|---|---|---|
| G18 access | D-X7's [source repository](https://github.com/telaminai/fluxtion-vendor-jars/tree/feat/spring-side-work-block) is pushed at `3a89391` (implementation `b05f6e2`). A separate empty clone runs the packet guard: nine placeholder Java sources; missing-spec and non-placeholder-package controls reject, restored packet passes. | Independently CLOSED by G at `e559c424`, including source/oracle review. Binary resolution/integration and D-X9 remain open. |
| G17 gate | Compiler `090c39b8`: whole-module runner plus source-driven test-class coverage guard. Removing the four suites omitted by the old filter fails the guard despite otherwise green reports. | CLOSED; final measured commands/results below. |
| G19 scope | Compiler `2806fb62`, analyser `e62d6497`: explicit scope sentence beside canonical archive checks; removing it fails the documentation check. | CLOSED as a documentation finding. G14 remains open. |
| G20 key | Compiler `5d1c8573`: `type:<FQN>` in record and report; old bare keys migrate, conflicting entries refuse. Regression fails on the old emission, then passes withdrawal/migration. | Independently CLOSED by G at `e559c424`. |

The source-only repository is now readable on another machine:

```sh
git clone --branch feat/spring-side-work-block https://github.com/telaminai/fluxtion-vendor-jars.git
cd fluxtion-vendor-jars
python3 tools/check-review-source.py --controls
```

Read SPEC.md before checks/check_risk.py and the two risk implementations. The prior
prediction commit fixes the one-day formula; the Decimal checker imports neither
implementation. This is a constructed supplier-convention mismatch, not a real
vendor incident. Source availability is not approval or a published Maven catalogue.

## Second-review dispositions

Compiler correction commit **6e69b231**, playground **9e2c112**:

- **H1:** both generators replace CR/LF in the generated filter field with spaces.
  Hostile-filter callbacks reproduced separators before the fix and pass afterwards.
  Runtime eventToString/eventFilter escaping remains separate upstream work.
- **M1:** inherited interface-default logger methods, directly or through a local
  superclass, refuse transactionally rather than being silently overridden.
- **M2:** static fields are excluded from implicit reference matching; the review
  case returns to refusal instead of adopting the static field.
- **M3:** conservative project-input scanning includes tests, additional roots, XML,
  Kotlin and resources. Its snapshot also catches references added after planning.
  The supported scope/excluded build, cache and VCS paths are explicit in the contract.
- **M4:** unexpected runtime inspection failures and missing-type LinkageErrors retain
  collected diagnostics and attach their causes. A real removed-class fixture is tested.
- **M5:** fresh standalone, hosted and bundle archive tests withdraw lifecycle nodes;
  all untouched stubs must be removed, never demoted. Reversing transform/hash order in
  either generator fails the named removal assertion.
- **M6:** compiled browser-node tests cover changing strings, hostile strings, default/
  absent/integer filters, every lifecycle phase, unregistered nodes and NONE-level logging.
  Guard-removal and constant-value mutations fail runtime assertions.

These required findings are implemented and executable; they await independent re-review,
not publication. Eight Java review-response controls, seven hosted controls and seven
browser callback controls have green baselines, named failures and restored green.
All thirteen earlier Java follow-up controls were repeated: twelve in one invocation,
then the final identity control separately after correcting an ambiguous anchor. The
first interrupted invocation remains a failure in the evidence, not a claimed pass.

Low findings retained as follow-ups: diagnostic attribution/deduplication, multi-phase
annotation precedence, browser header parsing, unusual classpath layouts, empty-directory
rollback, whitespace/lifecycle presentation and interpreted-version documentation.
The non-UTF-8 scan now uses conservative byte-preserving text; cross-category XML order
is no longer promised where the typed model does not retain it. Neither point implies
that the remaining Low findings are closed.

## Exact final gates

Compiler (Java 21):

```sh
export JAVA_HOME=/path/to/jdk-21
bash tools/spring-authoring/run-local-gates.sh
```

That runs `./mvnw -q -o -pl fluxtion-starter-core -am clean package`, the coverage guard
and jar verifier: **264 builder tests, zero failures/errors, one existing packaged-jar
skip; 71 starter tests, zero failures/errors/skips; 2,063 packaged classes, Java 17 floor,
keyless validation passes**. The earlier filtered command is superseded for this gate.

Playground, from `web/`, after the Java package finishes:

```sh
export JAVA_HOME=/path/to/jdk-21
export FLUXTION_STARTER_TEST_JAR=/path/to/starter-1.0.74-SNAPSHOT-all.jar
export FLUXTION_RUNTIME_TEST_JAR=/path/to/fluxtion-runtime-1.0.16.jar
pnpm test
pnpm build
pnpm check
```

**563 passed, zero skipped** (the original suite plus three withdrawal cases); build
passes. Type check still reports the same four existing errors and six warnings.
The reviewer correctly observed **559 passed, one skipped** when only the starter jar
was supplied: the missing runtime jar skips the compiled callback probe. Both jars
were supplied for this final run; no skip is hidden in the count.

**Archive scope:** the round trips and generated-project scripts use the branch tool,
while the emitted POM/record pin published 1.0.73, containing none of this work.
These are not evidence of what a public-download user receives. G14 still requires
the carrying release and deployment, then its own frozen public-download trial.

Final script verification and documentation gates are recorded in the completion
entry below. All raw failures/control summaries and exact commands are retained in
the compiler's `design/spring-authoring/response-2026-09-23-review-findings.md` and
its `evidence/spring-side-review-response-2026-09-23/` directory. No analyser code or
server was changed/run.

## Completion and review entry points

- Compiler: `fix/spring-side-review-response` at **a4bf2077**; implementation **6e69b231**, reviewed at **8db49c4c**.
- Playground: `fix/spring-side-review-response` at **9e2c112**.
- Catalogue: `feat/spring-side-work-block` at **3a89391**, source accessible from another machine.
- Analyser: `fix/spring-side-review-response`; this document and canonical status only.

Final download verifier, from the compiler root with five freshly emitted archives
(standalone bundle, standalone AOT, extended standalone, hosted bundle, hosted AOT):

```sh
JAVA_HOME=/path/to/jdk-21 python3 tools/spring-authoring/verify-download.py <standalone-bundle> <standalone-aot> <extended> <hosted-bundle> <hosted-aot>
```

Exit 0. Five setup/validate/generate paths and three standalone launchers pass.
Both regenerated hosts and the pristine keyless hosted bundle pass five-record export,
deliberately wrong expectation rejection and clean stop. All five receipts distinguish
a clean build from an injected real javac failure. The private evidence packet records
the exact input paths, raw exports and each command's output. These tests use uniquely
versioned local artifacts and cached dependencies with external JVM egress denied;
they do not close G14 or prove a public user's setup route.

`mkdocs build --strict` passed. Final analyser test and sweep results are recorded below.
The remaining dependencies are the other reviewer's independent re-review; upstream
runtime metadata escaping; the carrying release and matching browser deployment before
G14; catalogue binary publication/integration; D-X9's actual pre-existing component.
The accepted Low follow-ups remain as listed above. Nothing was merged or released.

Analyser documentation check: `mvn -q test` passed with 1,876 tests, zero failures/errors,
62 skips. The first sandboxed attempt had 29 loopback-binding errors; the unchanged
code passed when test-fixture socket binding was permitted. No analyser application
server was launched. `mkdocs build --strict`, `git diff --check`, and the exact rule-one
sweep over tracked and untracked files pass. Both marked tracker scope blocks also
pass the evidence-scope guard and reject its removed-scope control.

## Reviewer G re-review recorded

G's report at compiler commit `e559c424`,
`design/spring-authoring/review-2026-09-23-spring-side-response-G.md`, closes G17–G20.
The commit was found on the remote response branch, not the branch named in the chat.
It is preserved unchanged. Jar A's authentic convention error, the independent oracle
and placeholder packages were inspected; the oracle results were rerun by G. This
supersedes the earlier pending source-approval status without closing binary publication,
integration or D-X9. G reproduced the documented Java and playground counts and corrected
their prior interpretation of the callback skip: the runtime-jar variable was missing.

G21 is accepted as an open P4 test-gate follow-up, with owner and acceptance at the
existing Spring-side tracker entry. No production or test code changed in this intake.
H1/M1–M6 still need the other reviewer's verification. No release gate changed.

Status-only intake checks: `mvn -q -Dtest=SpecLinksResolveTest test` passes; `git diff --check`
and the exact rule-one tracked-file sweep are clean. No new untracked files were added.
