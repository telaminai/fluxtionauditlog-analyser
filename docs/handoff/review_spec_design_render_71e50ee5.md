# Independent review: M49 design render

Reviewed `71e50ee5247ac165a23d5201b2e2e196af9a3346` on `spec/design-render`, including the M49 tracker row, on 2026-09-19.

**Verdict: NOT READY for implementation.** The read-only design view and one navigation verb fit the existing canvas. The diagnostic navigation and relationship to older evidence need the decisions below before implementation. These are specification findings; M49 has no implementation to exercise yet.

## R1 — P1 / CONFIRMED: the diagnostic-to-design rule cannot resolve the promised cases

**Location:** [spec lines 91–99](../specs/completed/spec-design-render.md#d-4--evidence--design), acceptance 5, and the tracker claim “any `SPRING_*` diagnostic”.

The companion diagnostic contract is a tagged union, not a guarantee of `element.beanName`:

| Case | Actual contract | Consequence for D-4 |
|---|---|---|
| `SPRING_UNKNOWN_BINDING_NODE` | `SPRING_SERVICE_BINDING`; `beanName` is optional and can name the missing or unselected target. The documented example has an `xpathHint` and no `sourceRef`. | `source {bean}` can search for a declaration that does not exist, or select the target instead of the offending binding. Acceptance 5 promises the binding location. |
| Incomplete service binding | The binding element has no required bean name. | There need not be a bean anchor at all. |
| `SPRING_RECONCILE_CONFLICT` | `SOURCE_MEMBER` requires `className`, `member`, `xmlDeclaration`, and `classDeclaration`, not `beanName`. Its source location can concern Java. | `{file, line}` need not open DESIGN, and a bean fallback cannot be assumed. |
| Malformed XML / configuration / event / service findings | The registry also uses `SPRING_DOCUMENT`, `SPRING_CONFIG`, `SPRING_TYPE`, `EVENT`, and `SERVICE`. | The three-kind dispatch in D-4 excludes these, despite the tracker’s “any” claim. |
| The spec’s `FLX-1009` example | Its diagnostic factory emits `element.kind = NODE`. | It does not enter D-4’s three-kind dispatch, and requiring locations on `SPRING_*` does not change it. |

The upstream ask also conflates two separate fields: `xpathHint` is an element property, not the optional spelling of `sourceRef`. A source reference is relative to the report’s `sourceRoot`; it is not directly interchangeable with the new verb’s project-relative filename. The existing contract omits an unavailable source reference rather than inventing a path or column.

**Required revision:** specify a location-resolution table, including which location is XML versus Java, how the report root maps to an authorised local root, and what the UI says when the location is unavailable. Require accurate locations where the producer has an originating document, rather than universal file/line/column values. Name the producing stages covered by that change, including reconciliation. Either provide a binding-location fallback or explicitly gate acceptance 5 on the upstream location contract; Q2 cannot leave that acceptance case unresolved. Correct the `FLX-1009` example or add its intended mapping.

**Acceptance additions:** missing target in an anonymous binding; incomplete binding with no node; malformed XML; a Java `SOURCE_MEMBER` location; `FLX-1009`; and a report whose `sourceRoot` differs from the project root. Unresolvable cases must retain the diagnostic and explain why navigation is unavailable.

## R2 — P1 / CONFIRMED: live XML is joined to older evidence without a relationship state

**Location:** spec lines 37, 61–64, 76–77 and 120–125.

An author can load a log and graph, then change the XML’s edges while retaining a bean id. M49 deliberately rereads the new XML, refreshes its spotlight, and offers the old log’s record count and topology node under that id. Name equality supports navigation, but does not establish that the visible declaration produced those records. A diagnostic file can likewise describe an earlier validation or build attempt; a surviving file is not proof that the latest build reached its producer.

This is a required distinction in the existing [shared-evidence specification](../specs/spec-shared-evidence-canvas.md): cross-artifact identity needs explicit linkage, and stale or unresolved anchors must be named. The companion authoring contract already distinguishes stage results from their freshness receipts. M49 currently specifies neither use of that information nor an explicit unverified state.

**Required revision:** expose the design’s relationship to the loaded evidence in both the UI and verb echo. It is sufficient for this rendering milestone to say “working copy; matched by name; relationship to this run unverified”. Do not infer a verified relationship from a bean id or reuse a stale diagnostic as current. If freshness is claimed, define which producer receipt/input identity establishes it. Define what happens to an existing caption when its bean’s content changes or disappears, and invalidate or mark line anchors whose document revision changed.

**Acceptance additions:** edit an edge while retaining its bean ids with an old log open; retain an old sidecar after a failed build that never ran the compiler; change or delete a spotlighted bean; insert lines above a line spotlight. The canvas must preserve the distinction between current text, historical evidence and LLM testimony.

## R3 — P2 / CONFIRMED: acceptance 5 has no specified diagnostic intake path

**Location:** spec lines 91–95 and 122–123.

The proposed inputs are `open.design` and `source` selectors for XML/Java. Neither specifies loading a diagnostic result. At this revision, production Java contains no references to the diagnostic contract, `SPRING_*`, `sourceRef`, or either authoring result filename. Existing Reports are investigation reports: their `FINDING` sections refer to a log `recordIndex`, not a compiler diagnostic (`ReportSpec` and `ReportVerb`). The existing `open` dispatcher also has no diagnostic-result case.

The three files share diagnostic vocabulary, but do not have the same outer shape: validation and reconciliation contain `diagnosticReport.diagnostics[]`; the compiler sidecar contains `diagnostics[]`. Saying a finding is “loaded” leaves the entry point and adapter unspecified.

**Required revision:** name the user/agent action that supplies a result file, its scope and read boundary, the supported wrappers, and its replacement/clear behaviour. Define how Reports or Assistant presents an authoritative producer diagnostic independently of a log-record finding. This can extend an existing verb; it does not require another verb or any analyser-side validation. If ingestion is a prerequisite milestone, link it and gate acceptance 5 accordingly.

**Acceptance additions:** open a design and a failed validation result with no log; load each wrapper; replace a result with a corrected one; close or switch project; handle an unsupported schema or unreadable file without retaining an apparently current old result.

## R4 — P2 / CONFIRMED: the new public spec cites a private repository and path

**Location:** spec lines 6–7.

The newly added “Builds on” reference names an unpublished implementation repository and its internal document path. This conflicts with the supplied cross-repository public-diff hygiene rule and gives public readers an inaccessible dependency.

**Required revision:** replace that reference with the public authoring runbook and a published/versioned diagnostic contract. Keep implementation ownership in the private coordination tracker. The mechanical analyser data-name sweep does not detect this category of disclosure.

## Other decisions and useful acceptance coverage

- Q1 may remain optional: sequential navigation is sufficient for this milestone. Preserve the current refusal of one spotlight request whose targets cannot be visible together; do not silently turn it into a partly successful request.
- Pin `open.design = A; source {file: B}; spotlight {target: "source:design:bean:x"}`. The session/view distinction suggests A; state that explicitly, echo the resolved file, and test with the same bean id in both files. Switching project must clear the session design.
- Test Follow with a design and **no log**, and with a log that cannot be followed. Existing `MainFrame.setFollowing` requires a followable log store; the design refresh needs its own eligibility. Also test malformed intermediate XML and a removed/duplicate bean id without rebinding a spotlight to a nearby declaration.
- Specify precedence or rejection for mixed selectors (`file` plus `fqn`, `bean` plus `line`, `method` without `fqn`) and apply the same root policy to every navigation route and reread.

## Follow-up: guidance on the author's proposed response

The author accepted all four findings and proposed an anchor table, receipt/hash-based freshness, `open.diagnostics`, and replacement of the private reference. That direction addresses the review subject to these corrections; the verdict above still applies to the reviewed revision, and the revised specification needs re-review.

1. **Resolve the offending location before the referenced bean.** For `SPRING_SERVICE_BINDING`, prefer the binding declaration even when its target exists. Label a config-block fallback approximate; multiple matching bindings must remain ambiguous. For `SOURCE_MEMBER`, prefer an authorised file/line location, then FQN navigation. Its `member` is not necessarily a method. Include document/config/type/event/service findings in the table.
2. **Separate input freshness from relationship to the loaded run.** An XML hash match establishes only XML input identity. The authoring receipt distinguishes XML, Java-source and authoring-record inputs; unchanged XML after a Java edit cannot establish freshness. A local build receipt also does not identify the processor that produced an arbitrary loaded log. M48.12 in the [tracker](../specs/tracker.md) still records the missing generated-model-to-audit-log identity carrier. Without an explicit run/model link, the relationship to the loaded log stays unknown even when the XML matches a build receipt. M49 can ship with that honest limitation. Adding a design hash to `DescriptorSupport.Meta` may help later, but does not itself close the linkage. `Meta` is public runtime API, so this is a coordinated contract change, not solely a compiler edit; keep it separate from M49's acceptance requirements.
3. **Use explicit diagnostic intake.** `open.diagnostics` is a suitable entry point. Discovery may offer candidates under an authorised project, but should not silently load one. Specify wrapper detection, replacement/clearing, project scope and freshness. Use a producer-finding category, since some findings concern Java or the whole document, and support it without an open log.
4. **Narrow the upstream location requirement.** Require accurate `sourceRef` where the producer knows the originating file and position. Preserve an explicit unavailable case rather than inventing files or columns. Keep `xpathHint` separate, resolve report-relative paths through authorised local roots, and include reconciliation producers in the ask.
5. **Use public documentation for the dependency reference.** This resolves R4 once the private reference is replaced.

The key combined acceptance case is **unchanged XML, edited Java, an old log, and a failed latest build**. The revised specification must state which relationships remain current, stale or unknown.

## Evidence and limits

Inspected the complete two-file commit diff, M49’s dependencies, the current source navigation, action dispatch, Reports model, Follow guards and spotlight resolution. Compared the companion authoring specification’s validation example and three-result contract with its diagnostic wire contract/registry and the `FLX-1009` factory. The companion authoring work remains a specification dependency; this review does not claim those producers are already shipped.

The [sibling probe record](review_spec_design_render_71e50ee5-probe.txt) records the small executable extraction and source scan. It confirms the documented missing-source-reference example, the `NODE` factory mapping, and zero production references to the diagnostic intake contract. Absence of those references supports the targeted code inspection; it is not a proof about every possible generic JSON reader.

Commands included:

```sh
git diff 71e50ee5^ 71e50ee5 -- docs/specs/spec-design-render.md docs/specs/tracker.md
rg -n 'SPRING_|DiagnosticReport|sourceRef|fluxtion-validation|fluxtion-reconciliation' src/main/java
rg -n 'recordIndex|enum Kind' src/main/java/telamin/fluxtion/audit/analyser/analyser/report/{ReportSpec,ReportVerb}.java
rg -n 'doOpen|setFollowing|pollFollow' src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/{ActionExecutor,MainFrame}.java
git diff --check
```

The initial specification review ran no Maven suite, live Swing session, screenshots, remote validation, or new M49 runtime behaviour: this is a two-document specification change. Publication checks are recorded below. No implementation, tests, existing tracker or primary checkout files were changed. Review artifacts were written in a detached worktree.

## Publication checks

Before committing these review artifacts, ran `mvn -q -o test` with `JAVA_HOME` and `PATH` explicitly selecting Java 21. Exit status **0**; totals independently summed from **212 Surefire XML reports: 1,656 tests, 0 failures, 0 errors, 31 skipped**. The first sandboxed attempt had 29 local-socket permission errors; rerunning with local socket access produced the passing result above. This verifies the repository's pre-commit gate, not M49 behaviour.

The whole tracked-tree data-name sweep passed with only the two documented rule-file exemptions. Inspected the complete staged diff, checked the new review files for private repository names and machine paths, and ran `git diff --cached --check` successfully. The publication adds only this review and its sibling probe.
