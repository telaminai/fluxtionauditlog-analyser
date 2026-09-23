# Review — Spring-side work block (reviewer: claude)

**Scope.** Branch `feat/spring-side-work-block`:
- compiler at `a962d01c` (source implementation `b940d985`, base `69d61d80`);
- playground at `21346be` (base `a4a56d1`);
- the local catalogue at `b05f6e2`;
- the analyser at `754aa025`.

Handoff: [report_spring_side_work_block_2026_09_23.md](report_spring_side_work_block_2026_09_23.md).

**Verdict: CHANGES REQUIRED before the carrying tool is published.** The work is careful, and
nearly every claim reproduces:
- the gates reproduce, at the exact counts;
- all 26 committed mutation controls fail their named tests with exact restoration;
- the jars rebuild byte for byte;
- 41 attempts to bypass F9/F11 were all refused.

But independent attacks found problems in the reconciler, the generated audit and G7:
- **one High:** event-supplied text injected into every generated handler's audit;
- **four correctness Mediums**, three of them contradicting the spec text they implement;
- **two test-guard Mediums**, where a claimed property has no test that would catch its regression.

None needs redesign. Because publication puts this in every download, fix the High and the four
correctness Mediums first.

Nothing was fixed, merged, published or deployed. No key was used and no client session was run. The
review ran in disposable detached worktrees, split three ways:
- the Java reconciler attack, with a 39-test harness;
- the playground;
- G7, the Java callback controls and the jars.

Every finding below marked RAN was reproduced. For the key reconciler findings I re-ran the harness
myself on a clean copy.

---

## High

### H1 · The generated callback audit writes event-supplied text unescaped (RAN)

Both emitters log the incoming event's filter:
- `Reconciler.java:628`;
- `callback-audit.ts:15`.

The call is `logger.info("filterString", auditEvent.filterString())`.

`EventLogger.info(String, String)` in `fluxtion-runtime-1.0.16` writes the value raw. A filter string
containing `\n---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 0\n---\n` produced a record
with **two bare `---` lines and a forged stream-end marker**
(`evidence/spring-side-review-2026-09-23/FilterStringInjectionProbe.java.txt`). The playground review
reproduced the same through a Signal name: bare `---` lines in `eventToString`, in `eventFilter`, and
in the new `nodeLogs: filterString` entry.

The analyser's round-4 review of the Mongoose audit spec (addendum 2, G1) showed what that does to a
file: 3 records read as 5, the forged marker is recognised, and a whole file reports
`missing_records`.

- **The root cause is the runtime.** Values have never been escaped, and `eventToString` already
  carried this.
- **What B4 adds is scale.** Before B4, only author-written audit calls exposed a value. B4 adds an
  unescaped channel to **every generated event handler, by default**, fed by event data. Filter strings
  are often external identifiers: symbols, topics, keys.

B4's choice to read the filter from the incoming event is right for truthfulness. It needs an escape.

**Required, either:**
- the runtime escapes values (the durable fix, tracked with G1 of the audit spec); or
- until then, both emitters neutralise line breaks in the logged filter,
  e.g. `filterString().replace('\n', ' ')`.

Also add a hostile-filter case to `CallbackAuditTest` and to `callback-audit.test.ts`.

## Medium: correctness

### M1 · An interface default `setLogger` is silently overridden (RAN; contradicts B4)

- **Scenario:** `interface Audited extends EventLogSource { default void setLogger(EventLogger l){…} }`
  and `class Parent implements Audited {}`.
- **What happens:** the plan has no conflict. It adds `implements EventLogSource`, a
  `__fluxtionAuditLog` field and a `public void setLogger(…)` override. Compiled and registered with a
  real `EventLogManager`, **the developer's default method never runs**.
- **Cause:**
  - `inheritedCandidate` (`Reconciler.java:589-592`) only checks interfaces for annotated members, and
    the generated setter has none;
  - `prepareAuditSupport` (`:658-664`) only notices `EventLogSource` when it is implemented directly.
  - The same gap applies through a local superclass that implements such an interface.
- **Why it matters:** B4 says "Never replace developer logger callbacks or fields silently".
  Test: `audit_interfaceDefaultSetLoggerIsNotOverridden`.

### M2 · An implicit reference adopts a `static` field (RAN; regression)

- **Where:** `existingReferenceName` (`Reconciler.java:1306-1311`) matches on type only.
- **Scenario:** Child has `static Parent DEFAULT; private final Object upstream;`, with a constructor
  taking `Parent` and no reference binding.
- **What happens:** the record adopts `"field:DEFAULT" … "static demo.node.Parent DEFAULT"` as the
  reference.
- **Before this change** the same case refused ("Cannot invent constructor/property wiring"). The change
  turned a safe refusal into a wrong, silent choice.
- **Required:** exclude static fields, and add the case to G12's tests.
  Test: `g12_staticFieldIsNotChosenAsReference`.

### M3 · A whole type is deleted while code outside one Java source root still uses it (RAN)

`WholeTypes.referenced()` (`WholeTypes.java:69`) scans only `*.java` under the effective source root.
Four pristine, withdrawn types were deleted while still referenced:

| Referenced from | Consequence |
|---|---|
| `src/test/java` | test compilation breaks |
| `META-INF/services` | `ServiceLoader` fails at runtime |
| an application-context XML | the Spring context fails |
| a `.kt` file inside `src/main/java` | compilation breaks |

Spec 3 says "no remaining project source mentions its simple name" and doesn't define project source.
**Required:** scan all source roots and resources (conservatively, as it already is for comments), or
state the one-root limit in Spec 3 and in the report output. Tests: `g5_referenceFrom*`.

### M4 · G7's complete collection is lost on the first uncoded exception (RAN; contradicts Spec 3 G7)

`attempt()` (`SpringDeclarationVerifier.java`) catches only `SpringDiagnosticException`. My probe
collected 4 failures, then hit a `NullPointerException` in a later check. `FluxtionDiagnostics.capture`
returned **zero diagnostics** and the NPE.

The trigger here was artificial (null `nodeBeans`). The realistic one is a `LinkageError` from
`getClass().getMethods()` on a node whose class references a missing type. That is an `Error`, which
`capture`'s `catch (Exception)` does not catch either.

Spec 3: "capture and its normal sidecar boundary retain the complete collection."
**Required:** record uncoded failures inside `attempt()` as a coded finding, or throw the collected set
with the uncoded failure attached. Probe: `evidence/…/ReviewG7ProbeTest.java.txt`.

## Medium: claimed properties with no test guarding them (RAN)

### M5 · Nothing detects hashing before the audit and comment transforms

The order is correct today (`mongoose.ts:1373`, the same at `fluxtion.ts:3383`), and every
`stubBodyHash` matches the final ZIP. With the order reversed, so the record is hashed before audit
lines are added:
- all starter and SG-2 route tests still pass (437);
- the consequence is real: after removing `lifecycleNodes` and regenerating with the branch jar, the four
  lifecycle stubs are **demoted as "Implemented body retained"** instead of removed.

None of the five hosted controls covers the ordering the response claims. Add a withdrawal-after-download
test.

### M6 · The browser runtime test doesn't enforce B4's filter rules

`callback-audit.test.ts:52-56` checks substrings of one record and nothing else:
- `filterString: pause` is checked on a Signal named `pause`;
- there is no assertion that an absent or default filter is omitted;
- `filterId` is never exercised;
- only the `start` phase is checked at runtime;
- "audit-disabled configuration" (a frozen B4 prediction) is not exercised: the probe forces a manager
  at INFO.

Two mutations survive with both tests green:
- removing the default and absent guards;
- logging a constant `"pause"` while leaving the asserted text as dead code.

The committed browser control only renames keys.

## Low

- **G7 attribution (RAN).**
  - A duplicate exporter, a configuration-wide collision, is reported as `SPRING_BEAN beanName=b`,
    where Spec 3 says it stays `SPRING_CONFIG`. By reading, the same applies to
    `SPRING_REFERENCE_DUPLICATE`/`MODE_CONFLICT`.
  - An unknown reference **target** is attributed to the owner bean, with the owner's class.
  - One unknown lifecycle bean produces four identical findings.
  - Findings arrive in category order (handlers, references, exports…), not the declaration order Spec 3
    states.
- **Emitter divergence (READ).** With several lifecycle annotations on one method, the browser logs the
  last phase (`callback-audit.ts:33-34`, no break) and Java the first (`Reconciler.java:630-631`). No
  current generator emits that.
- **`extends` read from comments (RAN).** `callback-audit.ts:40-43` runs `/\bextends\b/` over Javadoc,
  so a sink named `extends` suppresses `EventLogNode`. javac then fails with 5× `cannot find symbol
  auditLog`. `indexOf(' implements ')` is equally fragile. The input is contrived.
- **Non-UTF-8 source (RAN).** An unrelated ISO-8859-1 `.java` file makes withdrawal throw
  `MalformedInputException` rather than a diagnostic (`WholeTypes.java:72`). It fails in the safe
  direction but gives the user nothing to act on.
- **Classpath layouts (RAN).**
  - A class present only under `META-INF/versions/N/` of a multi-release jar is not detected, so a
    shadowing shell is planned.
  - The same happens for `BOOT-INF/classes/` in a fat jar.
  - Neither is a documented limit.
- **Rollback (RAN).** A failed record write restores everything except an empty `demo/fresh/`
  directory. By reading, `apply` (`Reconciler.java:1962`) rolls back on `IOException` only.
- **Trailing whitespace (RAN).** Blank lines between added members keep the indent (`"    \n"`), which
  `git diff --check` flags in the user's repository.
- **Lifecycle stub formatting (RAN).** The body ends ` /* TODO lifecycle */ }` misindented.
- **Unannounced change (RAN).** A legacy interpreted Spring spec with `springDeclarations: {}` now pins
  BOM 1.0.72, not 1.0.73. Harmless, but not recorded anywhere.
- **Tracker (READ).** The BETA-B2 intake entry still quotes that stage's gate (84 + 51, 551 tests) a few
  lines above the final checkpoint's 86 + 61 and 560. Label it as the stage gate.

## Verified, and it held

**Gates (RAN):**
- the named gate is **86 builder + 61 starter**, zero failures, errors and skips, exactly as claimed;
- the full modules give builder 263 (1 skip) and starter 62, BUILD SUCCESS;
- playground **560/560**; the production build passes; the type check has 4 errors and 6 warnings,
  all in files this branch doesn't touch.

**Controls (RAN), each with a green baseline, a failure in its named test, and exact restoration:**
- `verify-followups.py`: 13 of 13. The failure messages were checked individually, and each hits the
  intended assertion;
- `verify-callback-audit.py` (Java): 5 of 5;
- the browser `verify-callback-audit.py`: 3 of 3;
- `verify-hosted-authoring.py`: 5 of 5.

The runners check the failing test's name but not its assertion text. That is worth adding.

**G5 safe directions (RAN):**
- a substring mention and a reflection string both retain the type;
- edited-then-reverted bytes are removed, as specified;
- a CRLF-converted pristine file is retained and demoted, and repeat runs are a no-op;
- re-declaring a demoted type doesn't make it disposable.

**G12 (RAN):** an interface-typed field, an inherited field and a nested-class field all refuse or are
ignored correctly, and `Parent a, b;` refuses as ambiguous. The one exception is static fields (M2).

**Classpath (RAN):**
- directory entries are detected;
- a missing entry refuses with "rerun setup";
- a missing `.fluxtion/classpath` is the documented limit;
- dependency event types get no shell;
- no static initialiser runs.

**B4 retained bodies (RAN):**
- a local superclass that implements `EventLogSource` with its own setter refuses;
- a vendor superclass refuses;
- a `setLogger(String)` overload refuses;
- a local `EventLogNode` superclass is reused, byte-identical, and compiles.

**Formatting (RAN):** LF, CRLF, tab and mixed-EOL sources through three repeat runs and three
TRIGGER/DATA round trips show zero byte drift and compile. Developer comments survive exactly once.

**Transactionality (RAN):** a record-write failure restores the deleted type and removes the new file
(apart from the directory noted above).

**F9/F11 (RAN, 41 cases), all refused:**
- modes in several cases and paddings, with each extended declaration kind;
- BOM overrides `-SNAPSHOT`, `v`-prefixed, ranges, `${…}`, `1.0.073`, a zero-width prefix, `0.0.1`.

Legacy interpreted projects, non-Spring overrides and matching or padded overrides stay valid.

**SG-2 (RAN):**
- record hashes match the final ZIP content;
- the launcher is used, or declined honestly when absent;
- the bundle's first run is keyless;
- the route tests also pass against the **published** starter 1.0.73, which is what `setup.sh` fetches
  today.

**Emitter parity (READ):** keys, level, guard, filter conditions and callback priority match between the
browser and Java emitters, apart from the Low above.

**Shared jars (RAN):**
- `build.py` + `verify.py` rebuild with `catalogue.json` digests unchanged (only a log timestamp moved);
- A: 5 of 6 mismatches; B: 0 of 6; the zero-multiplier mutant: 5 of 6;
- the limit, notifier and feed controls fail as named; changed-artifact and unknown-catalogue controls
  refuse;
- the oracle is independent: Decimal, from SPEC.md, importing neither jar;
- A and B differ by exactly `sqrt(252)`.

**Analyser branch (RAN):** a public-repository sweep of the **added** lines is clean. The changes are
documentation and evidence only.

## Recorded, not verified: dependencies the handoff states honestly

- the carrying tool/BOM is unpublished, and the browser pin is unchanged;
- the playground is not deployed;
- G14 has not been run;
- the catalogue's public resolution and the integrated tour are open;
- D-X9's owner-built provenance is open;
- the local catalogue remote was unavailable.

The handoff states each of these accurately, and none is claimed closed.

## What was run and what was read

**Run:**
- all four gates, and all four mutation runners;
- the 39-test attack harness (`evidence/spring-side-review-2026-09-23/ReviewAttackTest.java.txt`;
  copy it into the starter-core test directory to run it; 9 of 39 fail, and each failure is a finding);
- the G7 probe and the filter-injection probe;
- 41 F9/F11 scratch cases, and the SG-2 hash re-derivation;
- the hash-order and filter-rule mutations;
- the lifecycle-withdrawal consequence, run with the branch jar;
- the catalogue rebuild and verify.

**Read:**
- the `apply` exception scope;
- the indent regex;
- emitter parity beyond what was run;
- the CI step (it downloads the pinned runtime and runs the callback test; it does not run the full
  suite, and was never claimed to).

**Not done:** any publication-dependent acceptance (G14), by design.
