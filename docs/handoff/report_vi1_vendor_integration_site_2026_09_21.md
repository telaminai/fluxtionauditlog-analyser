# VI-1 — vendor integration site change, 2026-09-21

Status: implemented on `docs/vendor-integration-vi1`, awaiting owner review. No publication authorized
by this report; only a branch push and PR. Main and other sessions' IDE edits remain untouched.

## Delivered

- `composing-a-system.md`: both naming patterns, the dependency-shadowing warning immediately below
  the XML, interface-handler excerpt, qualified build/trace claims and supplier obligations.
- `spring-authoring-getting-started.md`: the same dependency warning and the manual `EventLogNode`
  workaround for newly generated nodes. SG-2 remains open. Removed the stale statement that SG-1
  setup was still broken, linking the existing standalone release check instead.
- `integrating-a-vendor-component.md`: fictional-vendor label, wiring, original screenshot, audit
  order, configuration/control, independent calculation, upgrade procedure, tampered result and
  integrator/supplier checklists. Navigation follows the composition page under The audit log.
- Tracker VI-1 records owner review pending. Feedback 35's page/checklist is delivered in this branch;
  publication/bootstrap discovery and the distinct 31/32 work remain open. Feedback 6/29 and TA-9
  are not closed by documentation. Changelog updated.

## Claim-audit disposition

| Original claim | Resolution |
|---|---|
| Bean ids always name supplier nodes | Corrected: direct selection retains integrator names; discovery through a root uses supplier `NamedNode` names or generated names. Names are global; cross-supplier collision was not tested. |
| Wiring mistakes become build failures | Qualified: typed checks catch several mistakes, but dependency shadowing can silently remove the supplier while the build succeeds. Warning included. |
| Supplier needs no knowledge of the graph | Kept the separation, added public nodes/wiring constructors, stable names, getters/setters and interface-typed handlers. |
| Topology is an account of everything that ran | Qualified: audit names nodes that logged; topology can omit the supertype route. TA-9 linked on both pages. |
| Java/C++ equivalence and deployment claim | **Owner decision resolved, 2026-09-21.** Label C++ as preview; retain the historical comparison scoped to the tested graph and event stream. Remove the broad equivalence-proof and supplier-deployment claims. State that the vendor experiment did not test C++. |

No claim-audit row remains awaiting a wording decision. The C++ comparison's existing number has not been
independently re-run or supplied with invented vendor evidence. All newly stated numerical computation results link to
the preserved vendor evidence. No new assurance mechanism is proposed.

## Verification and limits

- `mkdocs build --strict` (repository `.venv/bin/mkdocs`): PASS.
- `mvn -q test`: 1,718 tests, zero failures/errors, 40 display skips. Initial sandbox run had 29
  socket-bind errors; rerun with local socket access passed. No code changed.
- `check_var.py` from the committed evidence directory: genuine exits 0, tampered exits 1 as expected;
  complete output below. This rechecks recorded logs, not a new application generation/run.
- Screenshot copied unchanged; SHA-256 `7ce4b441cec7566cda37cc8491c04d487c0c92ba7796470edcf840903e1fe0f7`.
  Read the image for public-data safety; labels show the fictional component and sample application.
- The initial change preserved the C++ section byte-for-byte pending the owner's decision; the later
  approved qualification scopes the claim as recorded above. Inspected generated HTML warning boxes.
- Full host validation and the exported-service harness were not re-run: those harnesses are not in
  this evidence packet. The page omits the historical host numeric totals. Setter/dispatch excerpts
  are explicitly attributed to the original record; the dispatch excerpt is not presented as compilable
  Java. Original before/after receipts are also absent; their unchanged hashes remain attributed
  historical observations, not independently rechecked receipt evidence.
- `SpecLinksResolveTest`, strict docs build, rule-1 sweep and staged diff whitespace check: PASS.
- No key use, fresh client session, production-source change, merge or push to main.

After the owner-approved C++ qualification, the full test gate found the preserved reviewer source's
trailing-space line. Added those exact source paths to `TrailingWhitespaceTest`'s existing evidence
exemptions and pinned the hand-finished snapshot in its byte-sensitive fixture check. The evidence
bytes remain unchanged; there is no production-source change. The initial failure was a fixture
registration omission, not a C++ or runtime failure.

## Independent calculation output

Trailing padding spaces removed from the printed table; values and messages unchanged.

Commands, from `docs/handoff/evidence/vendor-integration-2026-09-19/`:

```bash
python3 check_var.py runs/risk-run3/audit.yaml
python3 check_var.py runs/risk-tampered/audit.yaml
```

### risk-run3 — exit 0

```text
row                        model VaR  vendor VaR  breached  alert
RPOS,AAPL,1000,1                0.00        0.00  false     false
RQUOTE,AAPL,190.00              0.00        0.00  false     false
RQUOTE,AAPL,191.90           1093.35     1093.35  false     false
RPOS,ESZ6,20,50              1093.35     1093.35  false     false
RQUOTE,ESZ6,5000.00          1093.35     1093.35  false     false
RQUOTE,ESZ6,4950.00         29296.04    29296.04  true      true
RQUOTE,AAPL,188.00          30614.59    30614.59  true      false
RQUOTE,ESZ6,5040.00         61580.99    61580.99  true      false
RPOS,ESZ6,2,50               8328.81     8328.81  false     true
RQUOTE,AAPL,188.50           8278.88     8278.88  false     false
RPOS,ESZ6,0,50               2361.98     2361.98  false     false
rows 11 records 11 mismatches 0
node order in a quote record: ['acmeQuoteFeed', 'acmeVarCalculator', 'acmeRiskEngine', 'riskLimitGuard']
```

## Owner-supplied review reply — 2026-09-21

The review accepts the evidence treatment and asks for public warning destinations and a clearer
statement of the integration's value. Implemented in the same PR:

- Opened [issue 2](https://github.com/telaminai/fluxtionauditlog-analyser/issues/2) for dependency
  shadowing and [issue 3](https://github.com/telaminai/fluxtionauditlog-analyser/issues/3) for new-node
  audit scaffolding. Public pages now use descriptive issue links rather than internal feedback numbers.
- Pinned the TA-9 spec references to `332701dada41c52bd62a3abbf27d4f5e12492799`.
- Added “What you get” near the top: recorded discovery of the supplier internals/event types/service,
  re-verified audit order across the boundary, and recorded shared-interface dispatch without adapters.
  Recorded claims link to the original predictions; they are not relabelled as newly run experiments.
- Added the reviewer-reported 1.0.73 workaround result: a manually added `EventLogNode` superclass
  and audit-writing body survived regeneration and a subsequent handler declaration. The reviewer
  compiled the result but did not run key-gated generation or witness a runtime audit value. The guide
  keeps that distinction. The [scratch evidence is preserved](evidence/stub-reconcile-1.0.73-2026-09-21/README.md); no independent rerun is claimed.
- Historical host totals remain omitted. The owner subsequently chose the preview qualification
  recorded in the claim table. Merge remains pending.

The reviewer also reported repeated/interleaved comment-contract text in generated stubs. That is
recorded as upstream-owned tool-agreement D21 with a regression acceptance requirement. The
preserved snapshots directly show the formatting defect; neither this report nor the page calls the
generator independently re-run. Original source whitespace is retained (one trailing-space line in
the evidence snapshot); edited prose passes the whitespace check.

### risk-tampered — exit 1

```text
row                        model VaR  vendor VaR  breached  alert
RPOS,AAPL,1000,1                0.00        0.00  false     false
RQUOTE,AAPL,190.00              0.00        0.00  false     false
RQUOTE,AAPL,191.90           1093.35        0.00  false     false   <-- MISMATCH
RPOS,ESZ6,20,50              1093.35        0.00  false     false   <-- MISMATCH
RQUOTE,ESZ6,5000.00          1093.35        0.00  false     false   <-- MISMATCH
RQUOTE,ESZ6,4950.00         29296.04        0.00  false     false   <-- MISMATCH
RQUOTE,AAPL,188.00          30614.59        0.00  false     false   <-- MISMATCH
RQUOTE,ESZ6,5040.00         61580.99        0.00  false     false   <-- MISMATCH
RPOS,ESZ6,2,50               8328.81        0.00  false     false   <-- MISMATCH
RQUOTE,AAPL,188.50           8278.88        0.00  false     false   <-- MISMATCH
RPOS,ESZ6,0,50               2361.98        0.00  false     false   <-- MISMATCH
rows 11 records 11 mismatches 9
node order in a quote record: ['acmeQuoteFeed', 'acmeVarCalculator', 'acmeRiskEngine', 'riskLimitGuard']
```
