# Spring-side release 1.0.74 — execution record

**Published and deployed; G14 remains open after an incomplete retry.** The owner approved the release order and
explicitly authorised the configured compilation key for G14 only. No other release check
uses it. Accepted non-blocking follow-ups remain open; publication alone does not close G14.

## What is deployed

- Compiler, starter and BOM **1.0.74**, immutable tag `v1.0.74`, release commit `4a2f7de1`.
  Publication workflow **35928152967** passed. The owner reports cloud compiler 1.0.74 deployed;
  the G14 retry has generated source stamped with client and target-generator 1.0.74.
  That establishes the deployed generator version, not the unfinished scenario acceptance.
- Playground **5d6a38a**, following the reviewed fixes replayed onto main `784fd66`.
  Cloudflare production deployment and both static checks passed. The existing Mongoose
  1.0.29/plugins 1.0.44 pins and latest skill bytes were retained. Runtime remains 1.0.16.
- Browser artifact mirror **f178921**. Its SHA1 agrees with the public repository.
  Starter and curated-example BOM independently pin the now-published 1.0.74.
- Analyser remains the released **1.19.0** application. This work updates documentation and
  evidence, not its application code or release version.

New downloads include callback audit scaffolding, the hosted Spring authoring record/scripts,
version/mode refusals, and the reviewed reconciliation corrections. Business-state logging is
still authored; generated callback facts do not establish business correctness. Dependency
identity in receipts, runtime value escaping and the accepted review Lows are not closed.

## Checks actually run

| Check | Result and scope |
|---|---|
| Compiler candidate CI | Exact candidate `47b69001` passed MavenCI 35925847472. |
| Extra fresh-clone full release-profile test run | 4,123 tests, **one failure**, zero errors, nine skips. The existing loopback HTTP test received 404 instead of 200. All other cases passed. |
| Targeted HTTP recheck | All seven cases passed unchanged. The intermittent 404's cause is not established; the initial failure remains a failure. |
| Documented release-profile packaging preflight | All eleven modules passed with tests skipped, as the release runbook specifies. It is not a second full test pass. |
| Public publication check | BOM, parent-free consumer POM and executable 1.0.74 verified by unauthenticated public resolution. |
| Published jar | 2,063 classes, Java 17 base floor; SHA256 `21e2d8008c9b2429de33b6bbe320963001b1bd0ec8a639f9f4fe4426e4596110`. |
| Comment contract | Public released artifact/resource digest and byte parity passed. |
| Playground `pnpm test` | Published starter jar and runtime 1.0.16 supplied in both documented environment variables: **563 passed, zero skipped**. |
| `pnpm build` / version sync | Passed; zero example-version drift. |
| `pnpm check` | **Not green:** the same four existing errors and six warnings. |
| Preflight script unit tests | Seven passed. |
| Preview download setup/validate | Both families passed using shipped scripts, separate empty Maven/wrapper caches and empty settings; all original files unchanged. No generation. |
| Public download CI | [35929392911](https://github.com/telaminai/fluxtion-web/actions/runs/35929392911): hosted Spring and keyless bundle passed initially. Standalone initially downloaded 1.0.73, refused correctly, then passed as 1.0.74 on the separately preserved retry. |
| Public keyless bundle | Build, run, five sample rows, audit export and clean stop; 43 originals unchanged; no client or compilation key. |
| Analyser documentation integration | Corrected full `mvn -q test`: 1,876 tests, zero failures/errors, 62 skips; strict MkDocs passes. See the evidence-test correction below. |

Exact Java preflight commands, with actual JDK paths substituted locally:

```sh
JAVA_HOME=/path/to/jdk21 ./mvnw -B -P release -Djdk8.home=/path/to/jdk8 clean verify
JAVA_HOME=/path/to/jdk21 ./mvnw -q -pl fluxtion-integration-tests -am -Dtest=Phase14HttpRemoteGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test
JAVA_HOME=/path/to/jdk21 ./mvnw -B -P release -DskipTests -Djdk8.home=/path/to/jdk8 clean verify
BOM_GATE_EXPECT_RELEASE=1 scripts/bom-gate.sh
```

Exact playground commands:

```sh
JAVA_HOME=/path/to/jdk21 FLUXTION_STARTER_TEST_JAR=/path/to/fluxtion-starter-core-1.0.74-all.jar FLUXTION_RUNTIME_TEST_JAR=/path/to/fluxtion-runtime-1.0.16.jar pnpm test
pnpm build
pnpm check
node scripts/verify-comment-contract.mjs --vendor
node scripts/sync-example-versions.mjs --check
# From repository root:
python3 -m unittest discover -s web/scripts -p 'test_*preflight.py'
```

Public CI executes `web/scripts/spring-preflight.py --url PUBLIC_TEMPLATE_URL --output NEW_DIRECTORY
--expect-version 1.0.74` for each Spring template and `web/scripts/bundle-preflight.py --output NEW_DIRECTORY`
for the bundle. The committed [result files](evidence/spring-release-1.0.74-2026-09-23/README.md)
keep the first failure, retry, preview and public conditions separate. GitHub retained two artifacts
with the same standalone name; the successful retry is artifact **10780084666**. Downloading by
name selected the earlier failure, so the retry was recovered by its exact artifact ID.

## G14 — bounded published-download acceptance

Predictions and participant prompt were committed before the trial; the USER TASK is byte-identical
to the September 19 task. The public extended-design ZIP SHA256 is
`1830c09ea274f335574f0137f6f951b8856671ba7cb7589880d14ed2781bb102`. The sole initial content
change is the same deliberate `child` → `childTypo` signal-binding defect. No local provider,
repository source or pre-provisioned project tool/classpath is available to the subject.

The first fresh client stopped on an **operator environment failure**: the sandbox excluded the
resolved credential symlink and the directory-mode operation performed by the published config
reader. Its two generation-script attempts did not reach remote generation. Its transcript and
source changes are preserved separately; they are not a product acceptance pass. The operator
stopped it after 361 seconds. It received no implementation coaching.

The retry uses a new client, empty analyser home and Maven/wrapper caches, the identical public ZIP
and initial project hashes, and the same frozen task. Only neutral environment access was corrected:
read the actual configured credential target, permit the existing directory's owner-only mode
operation, and keep bare `mktemp -d` inside the isolated temporary directory. Credential-content
writes stay forbidden. A published-tool preflight in a separate throwaway directory reported
`Generation route: rapidapi`; no generation request was made by that check. Its temporary tool
was removed before the subject started.

The retry finished after 868.82 seconds, exit 0, zero substantive interventions. It
reported three generation-script attempts (one rejected by reconciliation) and two
fresh-process scenario logs. Source carries client/target-generator 1.0.74. The subject
then failed to read the analyser endpoint file and produced no chart or evidence-linked
report. This observation does not establish that the analyser process was absent; the
observer launch log records successful startup. The transport failure needs diagnosis.

The subject also edited recorded annotation ownership to get past a reconciliation
conflict. That is recorded behaviour, not an endorsed recovery instruction. Its scenario
claims and that edit have not yet had the independent acceptance check. Exit 0 means the
client ended, not that G14 passed. **G14 remains open**; no additional client trial was started.

## Documentation integration and limits

Main's preserved producer `spike-output.txt` carried intentional trailing spaces without the
whitespace test's exception. The initial analyser gate ran 1,876 tests with one failure and 62 skips.
A narrow test-only exemption and existing byte-preservation assertion now cover that file;
its bytes remain identical to main. No production Java or historical evidence was edited.
The initial red gate and corrected green run are recorded separately. No additional UI gate
or mutation of that preserved fixture is claimed.

The guide now distinguishes starter 1.0.74 from older manual-audit workarounds, describes both
launchers, and scopes dependency-shell protection to local reconciliation with the resolved
classpath. The browser cannot inspect a user's local jars. Screenshots still illustrate design
and validation, not new runtime proof. The older vendor experiment and its limitations remain
historical evidence.

The original branch controls remain the independently reproduced **12 Java + 8 callback + 7 hosted**
witnesses. They were not rerun as part of this version-pin release. Public hosted setup/validate is
new evidence; the earlier hosted generation/run controls retain their branch-artifact scope.
Catalogue binary integration, D-X9 provenance, runtime escaping, P1/NF1/G21/G22/G23 and the previously
deferred Lows remain at their canonical entries. No broader evidence-integrity headline is claimed.
