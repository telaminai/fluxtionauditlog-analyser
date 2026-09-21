# Response: revised source spotlight proposal — 2026-09-21

**Historical response:** the design-geometry correction and baseline below are superseded by the
[round-three response](response_source_spotlight_r3_2026_09_21.md), checked at released main `401da35b`.
Design keeps its released containment refusal; Java alone clips and labels partial lines.

Response to [SR-1–SR-10](review_source_spotlight_revised_2026_09_21_claude.md).
The [proposal](../proposals/source-spotlight.md) is revised in place. Review text is unchanged.
These are corrections to an implementation contract, not evidence that the feature is built.

| Finding | Response in proposal |
|---|---|
| SR-1: no preparation stage | Frame-owned prepareJavaSpotlights between pure precheck and resolveAll; a per-call Surface adapter consumes the immutable plan. Existing shared resolver/interface remain unchanged. Failed preparation causes zero reveals, with an ordering mutation required. |
| SR-2: Lit retains only a string | Lifetime javaSpotlightBindings registry keyed by exact Lit name, citing designSpotlightRevisions. Staged bindings are published only after batch success, consulted on remeasurement/echo, and removed on every departure including overlay dismissal. |
| SR-3: no viewport hook | JViewport ChangeListeners plus layout/document/mode notifications, coalesced on the EDT and deferred during batch application. Both Java and design line/bean targets gain clipping and scroll remeasurement; subscription/disposal and a disabled-listener mutation are required. |
| SR-4: type inference guesses/collapses conflicts | Node convenience target deferred, explicitly absent from this slice. Explicit FQN/line targets preserve the core example. A future node proposal must preserve conflict evidence before map construction and distinguish guesses from declared resolutions. This is scope reduction, not a claim to fix EventProcessorModel. |
| SR-5: pane opener also navigates | New ensureSourcePaneVisible entry point opens the bound viewer without navigation or processor seeding. Existing openSourcePane remains a getter; chooseSourceTarget keeps its record-sync semantics. |
| SR-6: headless frame tests skip | New frame tests must join both the existing CI ui-frame Xvfb class list and its nonzero/no-skip gate. Developer capture is additional. Headless geometric/identity tests and real-display assertions are distinguished. |
| SR-7: whole-pane differences | Deliberate difference: Java means text viewport, existing design means whole design panel including status. Design whole-panel behavior remains; design line geometry gains clipping/wrap correction explicitly. |
| SR-8: caret mutation insufficient | Separate mutations required for raw caret bounds, removed vertical clipping, and losing additional wrapped visual rows, plus real scrolled-pane assertions. |
| SR-9: revision policies differ | Explicit rationale: code captions can describe a changed expression, so Java extinguishes on revision change rather than adopting either design re-anchoring or its stale-caption annotation. |
| SR-10: archive acceptance conditional | Confirmed existing MavenSourceResolver archive fallback; acceptance now unconditional when enabled, including two entries, root precedence, disabled setting and refreshed cache. Require text and origin to travel together in the resolver cache and rendered snapshot. |

## Source checks and one correction to the review

Read the cited pipeline, overlay, source model, source resolvers and topology opener on local main
`9c10de82`. The stated integration gaps are present. In particular, SourceService/MavenSourceResolver
return/cache text without the archive identity, so origin-preserving lookup is now named work, not
assumed to exist.

SR-6's headless result correctly establishes that headless Maven does not exercise frame geometry.
It does not establish that display tests are developer-only: `.github/workflows/ci.yml` already has
`ui-frame`, Xvfb and a per-suite zero-skip/nonzero-test check. The revision requires extending that
real gate rather than substituting a manual run. No CI run for the proposed feature is claimed.

The earlier author run used SourceSyncTargetTest as its fourth suite (115 tests, no skips); the
reviewer used SpotlightFrameTest instead (115 tests, four skips). These are different selections,
not contradictory reports. Neither run verifies this unimplemented extension.

No implementation, UI drive or new client session was performed for this response. Checked local
Markdown file targets, whitespace and the rule-1 sweep on the revised documents. No release or
branch operation. Independent re-review decides whether these contract corrections are sufficient.
