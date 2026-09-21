# Illustrated Spring getting-started guide — 2026-09-21

Owner request: getting-started documentation with screenshots and a clear guided path.

Added `docs/site/spring-authoring-getting-started.md` under Getting started, linked from the home
page, existing Spring capability page, analyser tour and projects guide. Guided means an optional
conversation following the downloaded runbooks. The analyser never builds or executes the app.
The walkthrough distinguishes the included analyser demo, keyless Audit analyser bundle, standalone
Spring and hosted Spring. It includes a copyable client prompt, prerequisites, source-root grants,
design inspection, real validation findings, conditional generation/run and explicit restore.

## Verified

- Downloaded the public `fluxtion-spring` template with neutral `com.acme.springdemo` names.
  Read its actual PROJECT, RUNBOOK, profile, source, scripts, XML and ownership record.
- The published starter jar validates the untouched XML. Replacing `nodeBeans`'s `orderGate`
  value with `missingNode` produces real errors. Restoring it validates successfully again.
  These are the screenshots' actual producer outputs, not fabricated diagnostic fixtures.
- Released analyser 1.16.0: open shipped profile without log, list actual runbooks, add narrow
  design/target roots, open XML and select riskEngine, intake invalid then corrected reports.
  Four native window captures under an isolated home; all visible strings inspected, neutral only.
  Side panels are collapsed to give the bean index/XML and reports readable space.
- Website screenshot reuses the owner's public graph-preview witness from the release. It is
  explicitly captioned as a separate two-node imported design, not the five-node downloaded project.
- Capture tool `tools/capture-spring-authoring.py` works on a disposable project copy and restores
  its XML even after failure. It requires a native window capture and refuses screen-region fallback.

## SG-1: setup failure found by writing the guide

The actual downloaded `./setup.sh` fails in `dependency:copy`: the starter's published POM requires
`com.telamin.fluxtion:master:pom:1.0.72`, unavailable on the public Maven route. Repeated with a fresh
isolated cache and forced updates; same failure. Direct HTTP lookup of that parent returns 404.

The release's GET-based BOM/executable parity check did not traverse the descriptor; the passing
public Audit analyser bundle preflight does not provision the local starter. Neither result
establishes this Spring setup path. This corrects the scope of the coordinated-workflow availability
claim, not the already witnessed analyser release or keyless bundle result.

The guide warns before acquisition, retains the real setup command/error, and offers a narrowly
scoped direct-public-executable XML validation route with its verified SHA-256. That route does not
resolve the project classpath and is not a setup/generation workaround. No application was generated
or run for these screenshots; no compilation key used. The normal workflow remains blocked at setup.

**Owners:** compiler release packaging/publication and playground provisioning. **Closure:** a public
Spring download must execute its shipped setup against an empty cache without private repos, locally
installed release coordinates or substituting a different distribution; then validate and exercise
generation. Add that cheap repeatable gate to publication CI before claiming closure. Do not mutate
the released jar in place just to repair documentation.

## Reproduce captures

Stage the public download and published jars at neutral paths, then run:

```sh
python3 tools/capture-spring-authoring.py \
  --project /private/tmp/spring-guide-capture/project/spring-first-project \
  --jar /private/tmp/analyser-published-1.16.0/fluxtion-auditlog-analyser-1.16.0.jar \
  --starter-jar /private/tmp/fluxtion-starter-core-1.0.72-all.jar
```

Requires Java 21, macOS screen recording permission and the public project/jars. The operator stages
the jar directly because SG-1 prevents the normal setup. The guide discloses the same limitation.
[Hashes and scope](evidence/spring-getting-started-2026-09-21/verification.json),
[real validator outputs](evidence/spring-getting-started-2026-09-21/validation-commands.json),
[fresh-cache setup error](evidence/spring-getting-started-2026-09-21/setup-empty-cache.log).

Not verified: a fresh guided client following this new prompt, full Spring generate/run through the
broken setup route, or responsive page layouts in a browser (none connected). No new model trial
was started. The screenshots and runbook checks do not close those limits.

## Gates

Full analyser suite: 1,718 tests, zero failures/errors, 40 display skips.
The first sandboxed run had 29 socket-permission errors; the permitted rerun passes.
Strict MkDocs build passes; all five images were visually inspected. The capture command passed
against the released app; there are no new application-source changes.
