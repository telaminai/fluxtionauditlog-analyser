# Review — M75 document renderer spec, r1

- **Reviewed:** `docs/specs/spec-document-renderer.md` (DRAFT r1), commit `950dde0b` on branch `docs/m75-document-renderer`
- **Read against:** the code at that commit (`main` 1.35.1, `e09552dd`, plus the spec commit); SF-2 on
  `origin/fix/sf1-sf2-copy-prompt-html`; the philosophy packs on `docs/m74-virgin-onboarding-pack`; the Fluxtion
  framework reference `docs/claude.txt` (fetched 2026-10-17)
- **Date:** 2026-10-17
- **Reviewer:** independent Claude agent

## Verdict

The direction is sound and the E1–E14 table is accurate: every line reference and value I checked is right,
including E7. The spec is not ready to build from yet. Three of its core claims do not hold as written:

- The `documentView` node's contract does not fit how `walkPlayback`, `SessionDriver` and the walk performers
  actually work (R2, R4).
- Its staleness triggers miss most of what a report re-renders from (R3).
- Its multi-turn assembly gives up the single-moment consistency that report assembly depends on today (R5).

Two feasibility claims also understate the work: the E7 fix (R6) and vector capture (R8). The size estimate (R21)
is optimistic. Security is mostly well considered. It has one real contradiction (lesson authority, R9) and one
wrong statement (Export PDF and `ExportGuard`, R1).

**Checked and correct:**

- **E1–E6, E8–E14.** Palettes, `Face.width`, `escape`, `PAGE_W/H`, the 1200×800 and 1200×600 captures
  (≈166 dpi at the 520 pt content width) and the overlay values (`#1B1F24`/`#FFFFFF`, `#3D444D`/`#C2CAD3`, 22 px
  badge, 13 pt) all match the code.
- **The type scale and the ink colours in §6.**
- **E7.** `TopologyCanvas.java:699` and `ChartPanel.java:652` call `ThemeManager.isDark()`, and `UiTheme.surface()`
  returns `#1B1F24` under a dark theme.
- **No existing override.** Nothing in `paintOffscreen`, `toImage`, `renderForReport` or `ReportSeriesPicture`
  forces a light palette, and there is no force-light API anywhere in `src/main`. W-0 should fail on `main` today,
  as the spec predicts.
- **Rule 1 and links.** The rule-1 sweep is clean, and every relative `.md` link in the spec resolves on this branch.
- **The PR sizes cited in §14.** #57 has 9,966 additions and #63 has 6,844.

## Findings

### R1 — MAJOR — §11 "Writes (Export PDF…) ride `ExportGuard`, unchanged (D-I5)" is false

**Evidence.** The `llm/ExportGuard.java` class javadoc says: "Human-driven exports (menu choosers) are not routed
through here — a person picking a location in a dialog *is* the authorisation." `MainFrame.exportReportPdfWithChooser`
agrees: "the chooser IS the consent … a human-picked path never rides the exchange guard". Only verb-initiated writes
(`report {path}`) go through the guard.

**Fix.** State both paths:

- The window's *Export PDF…* is a chooser. The chooser is the consent, and there is no guard.
- An agent's `report {path}` is guarded.

Add a check that the window's export never calls `ExportGuard`, or say why it should. Otherwise a later "harmonise
on the guard" change would refuse a person's own save because the assistant opt-in is off.

### R2 — MAJOR — §10 Effects: "ApplyDocumentLinkEffect → the same view + spotlight path walks use (ApplyWalkViewEffect / LightWalkTargetsEffect's performer)" cannot be reused as stated

**Evidence.**

- `SessionEffects.ApplyWalkViewEffect(opId, ticket, generation, WalkSpec walk, int step, recordsTrusted)` is
  walk-shaped: it needs a `WalkSpec` and a step index.
- The performer `walkPresenter.light(...)` (MainFrame ≈ l.2805–2843) sets `walkOwnSpotlight = true`. It answers
  with `WalkTargetsLit(opId, ticket, …)`, which `WalkPlayback.onWalkTargetsLit` consumes after
  `stale(e.ticket(), …)`.
- Tickets are per-node counters (`WalkPlayback.ticket++`). A document ticket N and a walk ticket N would be
  indistinguishable to that check.
- `MainFrame.spotlightDismissed` posts `WalkEndRequested` for any spotlight change that is not the walk's own. The
  spec does not say whether a document link while a walk is showing ends the walk, is refused, or lights alongside.
  Reusing the performer under `walkOwnSpotlight = true` would silently pass the document's light off as the walk's.

**Fix.**

- Extract a shared lighting primitive from `WalkPresenter`: resolve, select, light and renumber, with no walk state.
- Give documents their own answer types (`DocumentLinkApplied(opId, ticket, lit, reason)`).
- Decide the walk interaction in a node. Recommendation: a document link while a walk shows ends the walk, through
  `walkPlayback`, with a reason.
- Add a node unit test: a document fact carrying a walk's ticket value changes nothing in `walkPlayback`.

### R3 — MAJOR — §10 "When it is stale" lists log generation, graph revision and definition change; a report re-renders from much more

**Evidence.**

- **Filter.** D-I3 says report evidence is live, and the tab re-renders on a filter change: MainFrame ≈ l.7092–7095,
  "the view banner, the filter offer and the table rows must move with the filter". The resolution's VIEW line, TABLE
  rows and chart pictures (`panel.scopeText()`) all depend on it.
- **Flags.** FINDING sections must be "BYTE-IDENTICAL to what flag wrote" (`ReportRenderer.section`, D-I1). A
  `flag` edit after the document is shown leaves an old finding on the page with no STALE mark.
- **Chart, marker and focus definitions.** CHART captures read the live `GraphPanel`. TOPOLOGY reads
  `namedFocuses`.
- **Producer findings land asynchronously.** `ScanLogEvidenceEffect`, then `ProducerFindingsObserved`. The tab
  refreshes when they move (MainFrame ≈ l.6410, "D-MA0c: the tab states THIS log's findings"). A document assembled
  just after a load can lack its LOG FINDINGS block and never learn otherwise.
- **The file-identity verdict.** `walkPlayback` re-resolves on a change of `openLog.identity()` and stops certifying
  record targets when `WalkIdentity.recordsTrusted` is false (`WalkPlayback.onLogChanged`, `recordsTrusted()`). The
  document binding has no identity.

OD-75.3 ("never re-render under the reader") is a deliberate departure from D-I3's liveness for the window. The spec
does not say so.

**Fix.**

- Make the binding (log generation, graph revision, `filterKey`, findings revision, identity verdict,
  producer-findings revision, chart/focus definition revisions) a declared field of `DocumentSource.binding()`.
  Every one of these is a staleness input, with its fact named.
- Record the departure from D-I3 as a decision.
- Add a ★ witness: flag-edit a record in an open report, and the window shows STALE with "the finding on record #N
  changed".

### R4 — MAJOR — §10 The node's facts and snapshot do not carry what its own decisions and its status line need

**Evidence.**

- **Link refusal facts.** The refusal "if the resolution carried a fingerprint mismatch" needs the mismatch as a
  fact. `DocumentAssembled(ticket, generation, digest, pageCount, links)` has no field for it, and none for the
  identity verdict.
- **Status line.** The status line is to show "about demo.yml, generation 3", "rendered from the snapshot, never
  composed in the window". But `DocumentViewState(key, kind, title, phase, page, pageCount, stale, reason)` has no
  binding: no log name and no generation. The window would have to fetch those from elsewhere and compose them. That
  is the hand-placed status-line assembly `OneDispatchModelTest` exists to stop ("the log's status line is composed"
  rule).
- **Effect answers.** `SessionDriver.Adapter.perform` must return a result synchronously: "never null … an
  unanswered effect is a hole in the record". `AssembleDocumentEffect` spans many EDT turns, so it needs an immediate
  acknowledgement, as `ScanLogEvidenceEffect` → `ScanScheduled` and `OpenLogEffect` → `Pending` have. The spec lists
  none, and none for Show/Scroll/Close either.

**Fix.**

- Add `fingerprintMismatch` (or a link-policy verdict) and the identity verdict to `DocumentAssembled`.
- Add `binding` (and a node-composed `statusLine`) to `DocumentViewState`.
- Name every effect's synchronous answer (`DocumentAssemblyScheduled`, `DocumentShown`, …).

### R5 — MAJOR — §10/§12 One section per `invokeLater` turn breaks the single-moment capture report assembly relies on, and the "no turn exceeds ~100 ms" budget is unsupported

**Evidence.**

- **Single moment.** `MainFrame.coverageForReport` exists so that "the store, the graph and the claim are read here
  together, on the EDT, so the verdict describes exactly what is scored" (independent review R2). Today the whole of
  `renderReportPdf` is one synchronous EDT call. Spread across turns, a Follow append, a filter change or a flag lands
  between sections. The ticket and generation check does not catch a Follow append, because the spec says it must
  not churn the document. The result is one PDF whose section 2 counts N records and section 5 counts N+k.
- **Live components are mutated during capture.**
  - `ChartPanel.toImage` resizes the live chart (`setSize(w,h)` … `setSize(was)`), and its paint overwrites
    `lastPaint`, which `drawnFact()` uses for walk chart targets.
  - `GraphPanel.chartImage` hides the live *Edit series* button.
  - `exportFinding` sets a record marker on the live `GraphPanel` and clears it in a `finally`.

  Each of these is safe only inside one turn.
- **Turn length.** A single SERIES capture runs `SeriesExtractor.extractExpr` over the whole store on the EDT
  (`ReportSeriesPicture.of`). TABLE assembly (`ReportVerb.assembleTable` → `ReadService` / aggregate) is also
  synchronous over the store. Neither is bounded by section count, so "~100 ms per turn" is not a property of the
  plan.

**Fix.**

- Take the evidence once, as `evidenceCapture` does. That node already pauses Follow for a capture
  (`SetFollowEffect`) and restores it.
- Or: resolve, read the claim and pin the record count at the start, and refuse a section whose inputs moved.
- Move store-scanning extraction off the EDT through `core.Background` against a pinned snapshot. Keep only painting
  on the EDT.
- Keep every live-component mutation inside one turn, and add a test that a walk's chart `drawnFact` is unchanged by
  a document capture.
- Replace the 100 ms claim with a measured budget.

### R6 — MAJOR — §6 / E7: the fix is larger than "`TopologyCanvas` and `ChartPanel` gain a `StyleTokens` input"

**Evidence.** E7 itself is confirmed, and there is no existing override. But the global theme reads go well beyond
the two `paintComponent` calls:

- **TopologyCanvas.** The static helpers `fade` and `fade2` read `UiTheme.surface()` directly (l.1066, l.1159),
  ignoring their `dark` argument. `paintLegend` calls `ThemeManager.isDark()` again (l.1198).
- **ChartPanel.** It reads `UIManager.getColor("Panel.background")` (l.654) for its margins.
- **Chart captures paint Swing children.** `GraphPanel` adds `legendScroll` to the chart (l.264). The legend
  background is taken from `UIManager` once, at construction (l.242). Those children paint through FlatLaf
  delegates, whatever palette the painter is given.
- **Fonts.** Text everywhere uses the LAF's component font (`getFont()`).
- **SERIES.** `ReportSeriesPicture` builds `new ChartPanel()`, which inherits all of the above.

**Fix.**

- Scope P0 as "every colour and font a document picture uses comes from tokens". That includes the static helpers,
  the legend (draw it in the painter for documents) and the component font.
- Make W-0 cover all four capture paths, not only `renderFocusForReport`: `renderCycleViews`,
  `GraphPanel.renderForReport`, `ReportSeriesPicture.of` and `renderFocusForReport`. Assert on the margin and legend
  pixels as well as the canvas.

### R7 — MAJOR — §7.1/§12: "2× today's pixel size (2400×1600)" changes the picture, and the memory and size budgets leave out the rasters that dominate them

**Evidence.**

- **Composition.** `paintOffscreen` lays out at the given `width × height` and then `fitToView(REPORT_MAX_ZOOM)`,
  with zoom capped at 2.2 and a fixed `labelSize`. `ChartPanel.toImage(w,h)` lays out ticks and wrapping for the
  given size. A 2400×1600 canvas is therefore a different composition (relatively smaller labels, more ticks), not
  today's picture at 2×.
- **Memory.** At 4 bytes a pixel, the cap of "64 Mpx of raster in total" is about 256 MB of `INT_RGB` heap. Then
  `PdfDoc.addImage` copies `w*h*3` samples before deflating. Yet the budget is "`LaidOut` < 5 MB excluding rasters".
- **PDF size.** If the PDF embeds the 2× raster, "size within 1.5× of today's" is not plausible with four times the
  pixels. If it embeds a 1× raster while the window shows the 2× one, G2 ("identical") no longer holds for figure
  content.

**Fix.**

- Paint at today's logical size under `g.scale(2,2)` (a device scale), so the composition is unchanged.
- Decide which raster the PDF embeds, and state it.
- Budget heap including rasters: lower the raster cap, or keep rasters compressed and decode per visible page.

### R8 — MAJOR — §7.2 / §16: `RecordingGraphics2D` "without being rewritten" does not survive how the painters lay out text

**Evidence.**

- **Text is measured with the LAF font before it is drawn.** `TopologyCanvas.fitNodeWidthToLabels` measures with
  `getFontMetrics(base.deriveFont(labelPoints))` on the component (l.261–264), not through the `Graphics`.
  `ChartPanel` positions axis labels, note pins and wrapped explanations from `g.getFontMetrics()` (l.667–670,
  l.713, l.741–748).
- **Remapping text breaks it.** "Text the painters draw is mapped to the bundled faces via the tokens" happens after
  measurement, so labels overflow or collide.
- **Platform fonts cannot be embedded.** The platform UI font cannot be embedded either, because its bytes are not
  available from `java.awt.Font`.
- **The real API surface is wider than the two painters.** Chart captures paint a `JScrollPane` of FlatLaf-delegated
  children (R6). The surface is therefore "whatever FlatLaf's delegates call", not "what the two painters use".

**Fix.**

- Before vector capture, document captures set the bundled face as the painter's font. The tokens carry fonts, not
  only colours. The recorder answers `getFontMetrics` from the same bundled font, so measurement and drawing agree.
- Draw the legend inside the painter for documents.
- Keep the strict recorder with a raster fallback per figure, as proposed.
- Move font-in-tokens to P0, since R6 needs it anyway.

### R9 — MAJOR — §7.3/§8.2/§11: lesson authority is contradictory, and a dropped lesson can borrow the bundled lessons' standing

**Evidence.**

- **Contradiction.** §11: "A lesson cannot produce an APP or EVIDENCE block." But §7.3 ("draws the *loaded*
  processor's topology … (EVIDENCE, not TESTIMONY)") and §8.2 ("a `from loaded-graph` figure is EVIDENCE") let a
  lesson produce EVIDENCE.
- **Lesson words on evidence.** `mark n node "label"` puts lesson words on that evidence figure.
- **Same label for foreign and reviewed lessons.** A dropped `.lesson.md`, foreign text, gets the same `LESSON`
  label and styling as a bundled lesson "reviewed against `claude.txt`". A stranger's file reads as the analyser's
  reviewed teaching.
- **Menu targets.** Lesson links use the spotlight vocabulary, which includes `menu:<Menu>:<item>` and
  `toolbar:<…>` (`SpotlightVocabulary`). A press on a lit menu item is passed to the frame, which "may choose a lit
  menu item under the press" (`SpotlightOverlay` mouse handler, M64.11 `onPressed`). A foreign lesson can therefore
  point at a menu action with an instruction caption.

**Fix.**

- Reword §11: a lesson may *request* an EVIDENCE figure, which the analyser draws and labels, but never author one.
- Draw marks on a live figure under the LESSON label.
- Label a lesson's provenance on every page: "bundled, reviewed" against "from <file name>, not reviewed".
- Refuse `menu:` and `toolbar:` targets from non-bundled lessons, or tag them distinctly.
- Add a ★ witness for each.

### R10 — MAJOR — §3.2 "Nothing in today's reports needs a block the model lacks"; §13.1 content parity would freeze defects the table misses

**Evidence.** Each item below is a gap between what a report carries today and what the spec's table maps.

- **(a) The CHART markers cap never reaches the page.** `renderReportPdf` builds the CHART `SectionContent` with
  the 4-argument constructor (no notes) and sends `mk.notes()` — including "showing the first 200 of N marker(s)"
  (`ReportVerb.markersTable`, `MARKER_TABLE_CAP`) — only to `warnings`. The CHART case in `ReportRenderer.section`
  never calls `tableNotes`. That is a silent cap, against D-M3.
- **(b) The RECORD `file` is dropped from the PDF.** A RECORD section's `file` (the rolled-set member,
  `ReportSpec.SectionSpec.file`) shows in the tab (`ReportsPanel` l.446–447). The PDF heading is
  `"Record #" + recordIndex`, and `recordLines(s.recordIndex())` ignores the file.
- **(c) A callout taller than a page runs off it.** `ReportRenderer.callout` and `FindingReport.calloutBlock` call
  `ensure(boxH)` and then draw the whole box. A long NARRATIVE runs off the page bottom. The new "64 KiB per text
  block" cap makes this the normal case. D-I2 "on every page" needs a split callout that repeats its label on
  continuation.
- **(d) `PdfDoc.clip` truncates silently.** Titles, meta values (log names, fingerprints) and table cells are cut to
  "…" with no notice.
- **(e) The tab and the PDF already disagree.** `"UNRESOLVED"` (tab) against `"UNRESOLVED REFERENCES"` (PDF), and
  `"Suggested fix"` against `Finding.fixLabel()` "Likely cause / suggested fix".
- **(f) The finding export loses its warnings.** "No declared node" (`MismatchWording.findingHasNoDeclaredNode`)
  goes only into the reply echo, not onto the page. `FindingReport` carries neither LOG FINDINGS nor a fingerprint.
- **(g) The window has no home for two report actions.** The D-I3a *Apply the authored view* offer (an action, not
  a spotlight target) and the TABLE CSV export (`report {name, section, csv}`, MainFrame ≈ l.1835–1862) have no
  place in the window. §9.4 keeps the offer in the tab but not in the window, which shows the same VIEW callout.

**Fix.**

- List (a)–(f) as P1 corrections, each with a regression check.
- Run content parity on whitespace-normalised text, with an explicit allow-list of the corrections.
- Give the document model an `Action` run for the filter offer, routed as an intent.
- Either offer CSV from a table's context menu or say it stays in the verb.

### R11 — MINOR — §9.2/§11: new PDF metadata is an injection surface, and `PdfInertTest` as described is a byte grep

**Evidence.** Today's `PdfDoc.toBytes` writes no `/Info` and no `/Lang`. M75 adds `/Title` (the report title,
foreign text), `/Lang`, link annotations and outline titles. If those strings are written as literals, a title
containing `)` followed by `/OpenAction …` breaks out of the string. A byte search for `/URI` both misses an
escaped form and flags an innocent hex-free title.

**Fix.**

- Write every metadata, outline and annotation string as hex UTF-16BE with a BOM.
- Make `PdfInertTest` parse the object graph and check dictionary keys, with a witness: a report titled
  `) /OpenAction << /S /JavaScript >> (` yields no `/OpenAction` key.

### R12 — MINOR — §11: an exported document leaves the machine, and the spec is silent on machine paths

**Evidence.** `BundleProfile.redactProse` is "the ONE copy of the rule … anything else that sends the session's
words off this machine (a walk reel, #113)". `WalkReel` sends every word through it and states that its frames are
not redacted (`FRAMES_NOT_REDACTED`). Report and finding PDFs do not redact today. The meta strip uses the file's
name only, but narrative, notes, flag text, record lines and SERIES captions can all carry paths.

**Fix.** Make it an owner decision whether *Export PDF…* applies `redactProse`. Either way, state on the page what
was and was not redacted, as the reel does, rasters included.

### R13 — MINOR — §1/§3.3: the walk handout ignores the existing `WalkReel`

**Evidence.** `walk/WalkReel.java` already renders a walk as a self-contained, script-free HTML page (issue #82). Its
honesty rules are load-bearing: `NO_BUNDLE`, `BUNDLE_IS_NOT_THESE_RECORDS`, `SENDER_WORDS`, and atomic writes so a
truncated reel never exists under a finished name. It is also one more renderer of walk content, which matters to
E2's "must not become a fourth".

**Fix.** Add it to the E-table. Have §3.3 either reuse the reel's finish-page rules in the handout, or say why a
handout differs.

### R14 — MINOR — §10 / OD-75.6: `report {name, show: true}` rides the build/replace path

**Evidence.** Any `report` call with a `name` goes through `ReportVerb.parse` → `parsed.onto(existing)` →
`putReport(spec)` and reveals the tab (MainFrame ≈ l.1866–1879). `onto` returns the new spec as-is when
`existing == null`. A mistyped name in a "show" call therefore creates an empty report and saves it.

**Fix.** `show` should be a read-only form, checked before parse. It refuses an unknown name and writes nothing. Add
a `VerbSchemasTest` case and a witness that a `show` of a missing name leaves the report store unchanged.

### R15 — MINOR — §5: the font plan has gaps (partly judgement)

**Evidence and gaps.**

- **Subsetting.** The plan omits composite-glyph closure, which accented Latin needs. It does not say whether glyph
  ids are kept (`CIDToGIDMap /Identity`, the robust choice) or renumbered. It does not retain `fpgm`/`prep`/`cvt `
  and `maxp`, nor update `head.checkSumAdjustment`.
- **Tabular figures (not verified).** "Real tabular figures" is unverified for IBM Plex Sans's default digits. If
  they are proportional, `tnum` needs GSUB, which the planned reader does not parse.
- **Right-to-left text (judgement).** The base Plex Sans has no Arabic or Hebrew (those are separate Plex
  families). Right-to-left text would render as missing-glyph boxes, not "in logical order" as §5.4 says.
- **The fallback box.** It uses `⟨ ⟩` (U+27E8/9), which the mono face may not cover.
- **Zero-width characters.** SF-2's `UiText` inserts U+200C as an invisible break (`UiText.BREAK`). If an
  SF-2-neutralised string ever reaches layout, §5.4 will draw it as a visible escape.

**Fix.**

- Specify subsetting that keeps glyph ids, with composite closure and the hinting tables.
- Verify figure style and script coverage at P0 from the font tables.
- Draw the missing-glyph box as a vector frame with ASCII hex inside.
- State that layout receives raw text, never `UiText` output.

### R16 — MINOR — §13: some static checks cannot be expressed as written, and some witnesses cannot fail

**Evidence.**

- **`OneDispatchModelTest`.** The `OnlyIn` rule homes are `MainFrame` methods only: `allowed` is `""` for every
  other file. The test scans only the `ui` package. The proposed rules ("assembles only through
  `DocumentAssembler`", "opened … only in `render…Document`" when the window is its own class) need the framework
  extended, or they flag the intended home.
- **Network-free test against OD-75.7.** `DocumentNetworkFreeTest` forbids `Desktop.browse` while OD-75.7 leaves
  the browser hand-off open.
- **★2.** It states no mutation. With no Swing HTML door and no image loader in the design, nothing can fetch, so
  the listener cannot record a hit.
- **Accessibility.** `AccessibleRole.PAGE_TAB` is a tabbed-pane role. Under Xvfb a test can check the accessible
  tree, not that a screen reader "reads it in reading order".
- **Shortcuts.** Use the toolkit's menu-shortcut mask, since ⌘ is Ctrl on CI.

**Fix.**

- Extend `OnlyIn` with homes in other classes, and widen its scope to the new packages.
- Resolve OD-75.7 before writing the test.
- Give ★2 a concrete mutation, such as `ImageIO.read(URL)` for a lesson line or `Desktop.browse` on click.
- Claim only the accessible-tree assertions.

### R17 — MINOR — §8.2/§9.3: a dropped lesson would open as an audit log today

**Evidence.** `MainFrame.dropKind` maps `.fexp`, `.graphml` and `.xml`. Everything else is `DropKind.LOG`
("preserve the file chooser's support for audit logs with unusual names"). A `.lesson.md` would be loaded as a log.
`openDroppedFiles` also acts directly rather than posting an intent.

**Fix.** Add `DropKind.LESSON` for `.lesson.md` and define mixed drops (a lesson with a log). Route the open as a
`DocumentOpenRequested` intent, not a direct call.

### R18 — MINOR — §6: "the dark set equal to today's `ReportsPanel.Theme`, so nothing moves on day one" cannot be a constant set

**Evidence.** `ReportsPanel.Theme.current()` derives `panelBg` and `fg` from `UIManager` at render time. Its
backgrounds are `mix(accent, panelBg, …)`. There are four themes, two of them dark with different panel colours. A
static `DARK` token set reproduces only one of them. The overlay's card fill also carries alpha (244/248), which
§6's "callout card" tokens omit.

**Fix.** Make role backgrounds a function of tokens and the surface (keep the `mix` rule), not literals. Include
alpha in the card tokens.

### R19 — MINOR — §7.3: diagram semantics against `claude.txt`, and focus links

**Evidence.**

- **`@ServiceRegistered`.** `claude.txt` ("Consuming an external service") describes it as a method invoked when the
  host calls `registerService`. The service comes "from outside the graph". An `inject` edge "from a `service`"
  node, drawn as a peer inside `group "generated processor"`, suggests otherwise.
- **Shape collision.** "The same shape as the topology canvas's node classes" would give `service` the
  `EXPORT_SERVICE` hexagon (`ProcessorTopology.Kind`). That is an exported entry point, the opposite direction.
- **The `dirty = false` branch.** `claude.txt` teaches it, and there is no edge kind for it.
- **Second layout.** `layout sequence` is a second layout, not `LayeredLayout`, so E12's "rather than growing a
  second graph layout" is only partly true.
- **Focus links.** The spotlight vocabulary has no focus target (`SpotlightVocabulary` target list). G5's "focus
  reference … lights it" therefore means applying the focus (a view change), as the tab's *apply focus* button does.

**Fix.**

- Draw `inject` from an `external` node outside the processor group.
- Give `service` its own shape.
- Add a `branch` (dirty = false) edge kind or say it is out of scope.
- Say plainly that the sequence form is a new layout.
- Define focus links as a view change followed by a light.

### R20 — NOTE — §10: `context.document` placement

**Evidence.** `MainFrame.context()` returns early when `filter == null` (≈ l.9941). Lessons open with no log, so a
document open on a fresh start would be invisible to agents if `document` sits below that return.

**Fix.** Put it above the return, behind `need.test("document")`. Name the ONBOARDING rule in the spec.

### R21 — NOTE — §14: the estimate is optimistic (judgement)

M69 delivered one node of this shape, plus a presenter and UI, in 9,966 additions (verified). M75's P2 alone is
roughly that: a node, effects, an assembler, and a window with find, selection, copy, keyboard and accessibility.
P1 and P3 then add a pagination engine, a TrueType reader and subsetter, a Type0 PDF writer with a test PDF reader,
a diagram language with two layouts, and a `Graphics2D` recorder.

**Fix.** Re-estimate per phase. On M69's evidence, 15–20k lines is more likely than 10k. Treat P0's scope as growing
per R6 and R8.

## Rule-9 judgement on what stays in the widget

Zoom, scroll offset, find query and selection staying in the window is acceptable. Nothing decides on them, and the
spec gives a rule for moving one into the node when something does. The status line (R4), the link policy inputs
(R4) and the staleness inputs (R3) are not presentation, and they must be in the node.
