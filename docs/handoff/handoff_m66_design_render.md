# M66 design-render implementation handoff

Implementation snapshot: `b7c82f5` on `feat/m66-design-render` in `telaminai/fluxtionauditlog-analyser`.
The [spec](../specs/spec-design-render.md) defines the contract; the [tracker](../specs/tracker.md) owns status.
The [independent spec re-review](review_spec_design_render_232c846a.md) is preserved at `aa41d06`.
This handoff records pre-merge implementation evidence. Subsequent independent acceptance, upstream contract
intake and the owner-authorised merge are recorded in the tracker; the limits below still apply.

## Implementation

`DesignSession` is an authored node in the existing Fluxtion session graph. Read requests, completed reads,
parse failures, producer-result replacement/refusal and project clears are replayable facts. A generation
token rejects late results. Parsing, filesystem access and hashing run outside the Swing thread; the frame
applies facts and renders the node's state on the EDT. Follow has its own eligibility and works without a log.

`DesignFiles`, `DesignDocument`, `ProducerResult` and `DiagnosticLocation` hold the read boundary, inert XML
index, input comparisons and diagnostic-location policy. No Spring context or user-class loading occurs.
The Source tab adds Design, a bean/config index, file history and bean/node/record navigation. Producer
findings have a separate Reports category. File-menu actions and the `open.design`, `open.diagnostics` and
`source` API paths share the adapters. Design spotlights resolve the session file, qualify captions after
edits and go out on missing/ambiguous identities or a changed line revision.

The owner separately requested the dependency update: runtime **1.0.16**, regeneration builder **1.0.71**.
Rendering does not require a newer Fluxtion API. Committed processor/source-resource/GraphML outputs were
regenerated from the graph builder. Normal builds remain keyless. Validation used behaviour, state replay
and emitted audit records; there was no manual review of generated dispatch. No paid generation calls ran.

## Spec re-review intake

| Item | Resolution |
|---|---|
| G1 | The spec and tracker distinguish the author check from the independent review at `aa41d06`. |
| G2 | Sixteenth verb published through REST/MCP and the built-in assistant; inventory/parameter/vocabulary tests updated. Both user-guide inventories and the design spotlight vocabulary are documented. Existing M64 probe scope is explicit; the new M66 probe covers design families. No canonical teaching skill changes or playground re-vendor are included. |
| G3 | Configured roots grant Java/XML/producer-JSON reads. Project membership supplies a relative base only. Outside-root and symlink escapes are refused; profiles are not rewritten by file glances. |
| G4 | Upgrade recorded separately as owner-requested; runtime-format conformance and demo-log checks passed with the new dependency. |
| G5 | Current-main documentation through `940eeba` was integrated with a three-way content merge. The tracker keeps both its new tidy/M67 sections and M66. This preserves the published checkpoint/review history without a force push; no merge to main was performed. |
| G6 | Index/resolver/fixtures use `serviceRegistrations`; unknown handler-node findings also search `eventHandlers`. The full collision code name is corrected. |
| G7 | Receipt outputs precede inputs; `sha256:` prefixes are normalised and stage source-root overrides are honoured. Result input hashes can disprove a newer receipt's XML match. |

## Verification

All Maven commands used Java 21. Set `JAVA_HOME` to your Java 21 installation and put its `bin` first on PATH.

```sh
mvn -q -o test
mvn -q -o -DskipTests package
mkdocs build --strict
python3 tools/check-design-render.py
python3 tools/check-design-render.py --dark
```

- Full suite: **1,688 tests / 0 failures / 0 errors / 31 display skips**, summed from **216 Surefire XML**
  reports. This includes the 19 format-conformance and 26 binary-conformance cases, session replays, audit
  ordering, root/selector refusals, source rereads, read-only files, stale completions, wrapper versions,
  diagnostic locations and receipt input/output comparisons.
- Keyless packaging and strict docs build pass. Both generated source copies match and publication hygiene
  is checked by the suite and the staged-diff sweep.
- The committed packaged-app probe starts a real window and REST endpoint with an isolated home, synthetic
  XML/Java/JSON and the public demo log. It closes its process afterwards and prints a directory containing
  the transcript and screenshots; it never records the endpoint token in the transcript.
- App checks pass: A-session/B-glance pinning; no-log Follow; edited captions; removed, duplicate and changed-line
  anchors; malformed intermediate XML and malformed first-open navigation; all three result wrappers;
  root refusal; clear-on-refusal; project clear and an accepted outside-project log clear.
- The combined case passes on the app: unchanged XML, edited Java, an older demo log, failed build with
  `compilerRan=false` → XML input-current, Java mismatch, sidecar predates the attempt, loaded-run relationship
  unverified. Screenshots were inspected in light and dark themes. Inspection caught and corrected indented
  declaration button eligibility and over-stretched finding rows.

Counts can be re-derived without trusting Maven's console summary:

```sh
python3 - <<'PYCOUNT'
from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path('target/surefire-reports').glob('TEST-*.xml'))
counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
for path in reports:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, 0))
print(len(reports), counts)
PYCOUNT
```

## Boundaries and what was not checked

- Exact producer locations remain gated on the producer supplying accurate `sourceRef`. Fixtures exercise
  explicit locations and every fallback family; no newly released producer bundle or paid remote route was used.
- There is no log-to-build identity carrier in M66. Matching names always remain navigation, with relationship
  unverified. Java/record/receipt checks are timestamped at explicit diagnostic intake; reopen after a source
  edit or build. XML comparison follows the rendered revision.
- Reads accept UTF-8 files up to 2 MiB. Java hashing refuses more than 10,000 files. Record counts are bounded
  previews qualified by `recordsScanned`/`recordsExact`; Show records finds the first match in the full log.
- The probe drives the action API and inspects rendered screenshots. It does not automate every native chooser
  or context-menu click, nor benchmark multi-gigabyte logs. Filesystem adapters are tested with read-only files;
  a read-only mounted filesystem was not used.
- Cross-tab simultaneous spotlights, YAML design support, validation/generation/editing, M67 and canonical
  teaching-skill updates remain outside this implementation. Nothing was released or deployed.
