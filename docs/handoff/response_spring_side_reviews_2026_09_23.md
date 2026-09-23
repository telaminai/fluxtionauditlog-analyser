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
  Runtime eventToString/eventFilter and arbitrary node-field escaping remain upstream in
  [mongoose-plugins#39](https://github.com/telaminai/mongoose-plugins/issues/39); N1 below
  strengthens the generated-field guard.
- **M1:** inherited interface-default logger methods, directly or through a local
  superclass, refuse transactionally rather than being silently overridden.
- **M2:** static fields are excluded from implicit reference matching; the review
  case returns to refusal instead of adopting the static field.
- **M3:** test/resource/XML/Kotlin protection was independently confirmed, but the
  whole-project scan introduced R1–R3. The allow-list correction below supersedes that
  implementation and its former excluded-path wording; its snapshot still catches
  references added after planning.
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

## Java re-review: R1–R3 allow-list correction

The owner supplied Java re-review confirms H1/M1/M2/M4, the original M3 cases and
all eight controls on the prior head. Those are reviewer results, not newly run
attacks in this response. The later browser addendum independently confirms H1/M5/M6 and its 563-test/7+7-control
results on the prior head. It adds N1/N2; it is not confirmation of the new correction.

Prediction committed before trials: `224a1374`. Three constructed tests reproduced
R1–R3 on the prior implementation with three named assertion failures, no errors
or skips. The replacement inventory is explicitly allow-listed:

- effective source root and conventional `src/` tree, including tests, Kotlin,
  resources, service descriptors and design files;
- root `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`,
  `settings.gradle.kts`, `gradle.properties`;
- source/test/resource and build-helper roots declared in the local POM;
- effective authoring XML and emitted `config/server-config.yml`.

The POM is parsed inertly, resolving local properties and basedir; unresolved or
escaping roots refuse. External-parent input roots, arbitrary plugins and Gradle
source-set evaluation are not discovered: custom inputs must be declared through
those supported roots before using automatic whole-type deletion. Runtime `audit/`,
`logs/`, `data/` are not traversed unless explicitly declared as inputs. Snapshot,
reference search and symlink refusal all use the same allow-list. The effective
XML path now travels in the plan alongside the effective source root.

`ReconciliationInputsTest` adds the three requested regressions and four boundary
checks. Existing M3 Java tests remain, with the extra-root fixture now declaring its
root in the POM. Specification and public contract are updated together. The docs
sweep found the old wording in active Spec 3, the public contract and response;
these were replaced. The historical frozen prediction is retained with an explicit
superseding note, and reviewer reports/evidence are untouched.

Final correction commands and outcomes are recorded below; historical results above
remain attributed to their own heads.

## Browser addendum — N1 and N2

The owner supplied addendum independently confirms browser H1/M5/M6 on the reviewed
head. New N1 was reproduced in both emitters: the actual analyser tokenizer parsed
`triggerFired` as an extra field even though the callback did not log it. Prediction
was frozen in `7113dae6`. The test-only oracle consists of the four exact tokenizer/model
sources at analyser `e71abf4d`, with original licence and a SHA-256 manifest; tests verify
the manifest, compile the source, and exercise both legacy and quoted-scalar modes.
No analyser implementation changed.

The interim filter representation retains CR/LF-to-space and replaces every character
outside ASCII letters/digits, underscore, dot, space, slash, `@`, `+`, `-` with `_`.
This is explicitly **lossy**, including punctuation and non-ASCII text. Quoted-scalar
mode is chosen by the reader, not the callback: the guard must also work with legacy
text and cannot opt it into a different grammar. The tests require exactly
handledEvent/filterString/lifecycle, their values and no trace metadata for malicious
filters and a malicious declared signal name. Unmatched quotes/brackets, braces,
backslashes and line breaks are covered. Both fresh and existing Java classes execute.
Runtime escaping remains open beside eventToString under the existing
[upstream issue](https://github.com/telaminai/mongoose-plugins/issues/39), recorded in
[upstream asks](../proposals/upstream-asks.md#ta-u--tool-agreement-upstream-intake--2026-09-21).

N2 takes the narrower accepted option: the initial probe now says **callbacks on an
unregistered node do not throw**; the vacuous empty-record assertion is removed. No
audit-off processor run is claimed. The separate runtime NONE-injection check remains.

Two post-fix Java fixture attempts were refused because the hand-authored existing
class lacked the fixture's declared export methods, then lacked its DATA annotation.
Those logs are preserved as fixture mistakes; supplying those declarations corrected
the fixture. No production refusal was loosened to make this test pass.

## Final R1–R3 / N1–N2 verification

Compiler implementation/spec/test commit: `a156010b`. Predictions frozen at `224a1374`
and `7113dae6` held: all three inventory regressions and both parsed-field injections
were observed before correction. These corrections have committed tests and controls;
they await independent re-review, not publication. N2 narrows the claimed check rather
than claiming an audit-off processor was tested.

Exact commands (replace local paths on another machine):

```sh
export JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home
# Compiler root: unfiltered full gate, not the targeted mutation selector
bash tools/spring-authoring/run-local-gates.sh
python3 tools/spring-authoring/verify-review-corrections.py /private/tmp/spring-r123-n1-controls
# Playground root: both inputs are required for full callback evidence
export FLUXTION_STARTER_TEST_JAR=/private/tmp/spring-side-compiler-2026-09-23/fluxtion-starter-core/target/fluxtion-starter-core-1.0.74-SNAPSHOT-all.jar
export FLUXTION_RUNTIME_TEST_JAR=/Users/greg/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar
(cd web && pnpm test)
(cd web && pnpm build)
(cd web && pnpm check)
python3 web/scripts/verify-callback-audit.py /private/tmp/spring-n1-browser-controls
```

Results actually run: **264 builder tests (one existing skip), 80 starter tests (zero
skips), no failures/errors; 2,063 packaged classes**. Java 17 floor and keyless check
pass; coverage guard rejects the old filtered command. **563 playground tests, zero
skips**; build passes. Typecheck is unchanged at four errors/six warnings (exit 1).
Twelve Java correction controls and eight browser callback controls start green,
fail named assertions and restore green. N1 fails on parsed entries in both emitters.
R1/R2/R3 fail on their own assertions; M3-inputs/M3-snapshot still fail as named.
Two older controls affected by the plan signature (source-root-override and
symlink-snapshot) were rerun green/red/restored; the other old followup controls were
not repeated. The preserved private compiler packet is
`design/spring-authoring/evidence/spring-side-review-response-2026-09-23/r123-n1/`.

The final allow-list wording was swept in compiler Spec 3/tracker/response, this
response/tracker and the playground contract. The active deny-list policy is gone;
old predictions and immutable reviewer evidence remain labelled historical.
No new analyser code or server, no new LLM client session, no key, merge, rebase,
artifact publication or deployment. The unresolved dependencies remain runtime
escaping (#39), G21 and the accepted Low follow-ups, a carrying tool/browser release
before G14, catalogue binary publication/integration, and D-X9 provenance.

Final heads on `fix/spring-side-review-response`: **compiler `af7352bf`** (implementation
`a156010b`, predictions `224a1374` / `7113dae6`) and **playground `454a313`**. This analyser
commit updates only this response, the canonical tracker checkpoint and the upstream ask.
Catalogue source stays at `3a89391`; its independent approval remains recorded.

| New finding | Implemented acceptance / witness | Disposition |
|---|---|---|
| R1 | `unrelatedDataSymlinkDoesNotAffectReconciliation`; re-adding the whole project fails “data symlink must not affect plan or apply”. | Implemented; author verified, awaiting re-review. |
| R2 | `runtimeOutputsDoNotInvalidatePlan`; audit, logs and sink writes between plan/apply succeed; broad-inventory mutation fails “runtime output must not invalidate apply”. | Implemented; author verified, awaiting re-review. |
| R3 | `auditExportDoesNotRetainWithdrawnType`; broad-inventory mutation fails “audit export must not retain withdrawn type”. | Implemented; author verified, awaiting re-review. |
| N1 | Java `filterValuesAreSingleEntriesInRealTokenizer` and browser `compiles browser nodes and observes their actual runtime audit records`; each sanitizer-removal mutation fails “parsed callback entries”. | Interim generated-field correction implemented and witnessed; durable runtime escaping remains open. |
| N2 | Probe now asserts only that unregistered callbacks complete without throwing. The vacuous empty-record check is removed. | Misleading claim corrected; audit-off generated processor deliberately not claimed. |

The seven hosted controls were rerun too, including both M5 transform/hash-order
controls: green baseline, named red failures and restored green. All five fresh
standalone/extended/hosted download paths pass setup, validate and generate. Three
standalone launchers pass; both regenerated hosted paths and the untouched keyless
hosted bundle pass startup, five-record export, wrong-expectation rejection and clean
stop. All five builds then reject a real javac error with the old sidecar retained.
The exact five input paths and command are in the private packet's `downloads/README.md`.
These checks use branch artifacts with external JVM egress denied while the archive
pins 1.0.73; G14 is still unverified and depends on a carrying public tool/browser release.

Documentation checks actually run this pass: `mvn -q -Dtest=SpecLinksResolveTest test`
and `mkdocs build --strict` pass. The first MkDocs command failed because the worktree
has no `.venv`; rerunning the same docs with the installed primary-checkout executable
passed. Both outputs are retained privately. No full analyser suite was rerun for
these three documentation files. The exact rule-one sweep over tracked and untracked
files and `git diff --check` are clean. No reviewer file changed.
