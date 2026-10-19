# M75 — The document renderer: one layout, a PDF and a window

**Status:** DRAFT r2, 2026-10-17. r2 folds in the independent review of r1
([k7q3](../handoff/review_m75_r1_k7q3.md), 21 findings, R1–R21). Every finding is fixed in place, marked *r2 (Rn)*;
none was declined. Written against `main` at `e09552dd` (1.35.1). Owner decisions OD-75.1–.17 are open (§17).

**Owner requirements (2026-10-17).**

- A pop-up window that is a true renderer of professional quality, so diagrams and text match the spotlights.
- Used for the reports the analyser generates: a rendered report can be seen without downloading and opening the PDF.
- Used for teaching content: the Fluxtion philosophy prompt packs need diagrams of events, triggers, dispatch, the
  diamond and DI wiring.
- The twin of a future *intent ledger*. This spec leaves a seam for it and nothing more.

**Builds on:**

- [investigation reports](completed/spec-investigation-reports.md) (D-I1…D-I8)
- [spotlight](completed/spec-spotlight.md) (D-SP1…D-SP4)
- [spotlight walks](completed/spec-spotlight-walks.md)
- [the session processor](spec-session-processor.md) (CLAUDE.md rule 9)
- [the trust structure](spec-trust-structure.md) (D-T4)
- [evidence bundle packaging](completed/spec-evidence-bundle-packaging.md) (reports travel to other people)

Two inputs were read but are not linked, because they are not on `main`:

- SF-2 (`UiText`, `ForeignTextIsPlainTest`), on branch `fix/sf1-sf2-copy-prompt-html`.
- The philosophy packs, under `docs/proposals/packs/` on branch `docs/m74-virgin-onboarding-pack`.

---

## 1. What exists today, read from the code

The owner's design direction was "one layout, two backends". It is right, but the code is further from it than the
phrase suggests. Every row below was checked by reading the named file; the review re-checked every row and found
them accurate.

| # | Fact | Where | Consequence for M75 |
|---|---|---|---|
| E1 | There are **two** PDF layouts, not one: `FindingReport` (the single-record `report {path, recordIndex}` sugar) and `ReportRenderer` (an investigation report). They share a palette by copy, not by reference. | `report/FindingReport.java`, `report/ReportRenderer.java` | Both port onto the one layout. Otherwise "on screen equals PDF" holds for one document type only. |
| E2 | There is a **third** rendering of a report: the Reports tab draws it again from Swing components (`JTextArea`, `JTable`), with its own `Theme` record and colours. M68.1 already found the two disagreeing ("one report, two verdicts"), and they still word things differently (E15). | `ui/ReportsPanel.java` | The window must not become a fourth rendering. See §9.4 and OD-75.5. |
| E3 | Layout is **immediate-mode and interleaved with PDF emission**. `ReportRenderer` measures, paginates and calls `PdfDoc.text`/`fillRect` in one pass through a `Cursor`. There is no intermediate model to hand a second backend. | `ReportRenderer.section/callout/table/mono` | The layout pass must be lifted out into a pure function that produces a positioned display list (§4). |
| E4 | Text widths are **guesses**: `Face.width = length × widthFactor × size`, a mean glyph width for Helvetica. It is exact only for Courier. | `PdfDoc.Face` | Java2D would measure differently, so lines would break in different places on screen. Equality needs one metrics source, read from the font file itself (§5.2). |
| E5 | Only the standard-14 fonts are used: nothing is embedded, and the encoding is WinAnsi only. Em dashes, curly quotes and arrows are **transliterated**; anything else above U+00FF prints `?`. A finding holding such a character is therefore *not* byte-identical on the page (D-I1). | `PdfDoc.escape` | Embedded Unicode fonts fix this. The fallback for a glyph the font lacks must be visible, never `?` (§5.4). |
| E6 | Pictures are **rasters**. The topology and chart painters are Swing components painted into a `BufferedImage` (1200×800 or 1200×600) and embedded under Flate. At 520 pt wide that is about 166 dpi. | `TopologyPanel.paintOffscreen`, `ChartPanel.toImage`, `GraphPanel.renderForReport`, `ReportSeriesPicture` | Rasters stay through P2, at a device scale (§7.1). Vector capture is P3 (§7.2). |
| E7 | The painters read the theme **globally**, and not only in `paintComponent` *(r2 (R6))*. `TopologyCanvas` checks `isDark()` at l.699 and again in `paintLegend` (l.1198); its static `fade`/`fade2` read `UiTheme.surface()` directly (l.1066, l.1159). `ChartPanel` checks `isDark()` (l.652) and reads `UIManager` `Panel.background` (l.654). `GraphPanel`'s chart picture includes a FlatLaf-painted legend whose background is fixed at construction (l.242, l.264). All text uses the LAF component font. Nothing forces a light palette anywhere, so a report picture taken under a dark theme is painted dark inside a document that is "deliberately always light". The reviewer confirmed this by reading; witness W-0 must show it. | `ui/TopologyCanvas.java`, `ui/ChartPanel.java`, `ui/GraphPanel.java` | A pre-existing defect. P0 fixes it: every colour **and font** a document picture uses comes from tokens (§6). |
| E8 | Content is **assembled in `MainFrame`**: `renderReportPdf` and the single-record branch read records, paint pictures, assemble tables and call the renderer inline, in **one synchronous EDT call**. That is what lets `coverageForReport` read "the store, the graph and the claim … together" *(r2 (R5))*. | `ui/MainFrame.java` ≈ l.1950–2200 | Under rule 9 this becomes an effect performer whose result is a fact. The single-moment property must survive (§10.4). |
| E9 | The spotlight's colours and font are **hard-coded in the overlay**: the card is `#1B1F24`/`#FFFFFF` at alpha 244/248, the border `#3D444D`/`#C2CAD3`, the accent `UiTheme.accent()`, the badge 22 px, and the text the component's own UI font at 13 pt. | `ui/SpotlightOverlay.java` | "Text matches the spotlights" needs the tokens extracted, and *both* surfaces using the bundled face (OD-75.9). |
| E10 | The spotlight's caption is **testimony**, tagged `assistant`, and transient (D-SP2, D-SP4). A press on a lit menu item may run it (M64.11). | `SpotlightOverlay.TAG`, `SpotlightOverlay` mouse handler | A document's numbered markers share the *look*, never the *tag* (§8.2). Lessons are restricted in which targets they may light (§8.3). |
| E11 | **A walk is not a report section.** `ReportSpec.Kind` is `FINDING, RECORD, CHART, TOPOLOGY, SERIES, TABLE, NARRATIVE`. Walks are `walk/WalkSpec`, played by the `walkPlayback` node. | `report/ReportSpec.java`, `walk/WalkSpec.java` | Walks fit the model only as a separate handout document, and only partly (§3.3). |
| E12 | `topology/LayeredLayout` is pure and deterministic (declaration order, never a hash). | `topology/LayeredLayout.java` | Graph-form teaching diagrams reuse it. The sequence form is a new, small layout (§7.3). |
| E13 | SF-2's `UiText` is **not on `main`** yet. `HelpPanel` renders the analyser's own bundled HTML through `JEditorPane`. | `ui/HelpPanel.java` | M75 depends on SF-2 merging. The window has no `JEditorPane` (§11). |
| E14 | The page is **US Letter, fixed** (`PdfDoc.PAGE_W/H`). | `PdfDoc` | Page size becomes a layout input (OD-75.10). |
| E15 | *r2 (R10).* The current PDFs carry six defects a straight port would freeze. (a) The CHART markers cap note ("showing the first 200 of N") goes only to the reply's warnings, never onto the page, which is a silent cap against D-M3. (b) A RECORD section's rolled-set `file` is shown in the tab but dropped from the PDF. (c) A callout taller than a page runs off it. (d) `PdfDoc.clip` truncates titles, meta values and cells silently. (e) The tab and the PDF word things differently ("UNRESOLVED" against "UNRESOLVED REFERENCES"; "Suggested fix" against `Finding.fixLabel()`). (f) The finding export's "no declared node" warning and the log's findings reach only the reply, not its page. | `MainFrame.renderReportPdf`, `ReportVerb.markersTable`, `ReportRenderer.callout`, `FindingReport`, `ReportsPanel` | P1 corrects each one, with a regression check (§13.1). |
| E16 | *r2 (R13).* A walk already has a renderer: `walk/WalkReel` writes a self-contained, script-free HTML reel (issue #82). Its honesty rules are load-bearing: `NO_BUNDLE`, `BUNDLE_IS_NOT_THESE_RECORDS`, `SENDER_WORDS`, atomic writes, and words sent through `BundleProfile.redactProse` with `FRAMES_NOT_REDACTED` stated. | `walk/WalkReel.java` | A walk handout reuses those rules (§3.3). Machine-path redaction applies to exported documents too (§11). |
| E17 | *r2 (R1).* A person's chooser export is **not** guarded. `ExportGuard`'s javadoc says "a person picking a location in a dialog *is* the authorisation", and `exportReportPdfWithChooser` agrees. Only verb-initiated writes (`report {path}`) ride the guard. | `llm/ExportGuard.java`, `MainFrame.exportReportPdfWithChooser` | The window's *Export PDF…* is a chooser and stays unguarded (§11). |
| E18 | *r2 (R17).* `MainFrame.dropKind` maps `.fexp`, `.graphml` and `.xml`; **every other extension is opened as an audit log**. `openDroppedFiles` acts directly rather than posting an intent. | `ui/MainFrame.java` l.2424–2430 | A dropped lesson needs its own drop kind and an intent (§8.2). |

## 2. Goals and non-goals

**Goals.**

- **G1 — See a report without the PDF.** Any saved report, and the single-record finding export, opens in a document
  window from the Reports tab, from the Project panel's report row, and after `report {path}` writes a PDF.
- **G2 — On screen equals PDF, by construction.** One layout pass produces one positioned display list, and the PDF
  writer and the window are two consumers of it. Line breaks, page breaks, positions, colours and figure content are
  identical. Only rasterisation differs, and that is the viewer's business.
- **G3 — Professional typography.** One bundled proportional family and one bundled monospace family are measured
  from the font files themselves and embedded in every PDF. Unicode punctuation prints as written.
- **G4 — Teaching documents with diagrams.** A small, plain-text document format with a box-and-arrow diagram block,
  rendered by the same layout and the same backends. It covers the philosophy packs' concepts: event → handler →
  trigger, dispatch order, the diamond, constructor wiring and `@ServiceRegistered` injection.
- **G5 — Live links.** Clicking a record, node or chart reference in a document lights it on the main canvas with a
  numbered spotlight. A focus link applies the focus and then lights it *(r2 (R19))*. All of this goes through the
  session processor.
- **G6 — One visual language.** Numbered markers, callout cards, colours, captions and the face come from one set of
  style tokens shared with the spotlight overlay, in light and dark.
- **G7 — A seam for the intent ledger.** A new document *source* plugs in without touching the layout, the backends
  or the window (§3.4).

**Non-goals.**

- No HTML, CSS, SVG, JavaScript or embedded browser (JavaFX `WebView`, JCEF or similar). See §11 and §15.
- No editing in the window. Reports are authored as today, through verbs and menus; the window is a view.
- No network access of any kind, from the renderer, from the window, or from the PDF it writes.
- No complex-script shaping, bidirectional reordering, hyphenation or kerning (§5.4 says what that costs).
- No tagged PDF or PDF/A in M75 (OD-75.13).
- Not a Markdown engine for arbitrary files, not a replacement for the docs site, and not the Help page. Help may
  migrate later (§14 P4).
- Not the intent ledger.

## 3. The document model

### 3.1 Blocks

A `Document` is pure data, in package `doc`. It uses no Swing and no AWT beyond `java.awt.Color` and
`BufferedImage`.

```
Document(kind, identity, provenance, title, furniture, blocks)
  kind        REPORT | FINDING | TEACHING | WALK_HANDOUT   (closed enum; a ledger adds a value by spec revision)
  identity    source key + revision + content digest       (what the session node names, §10)
  provenance  BUNDLED_REVIEWED | PROJECT | FOREIGN(file name) | SESSION   (r2 (R9): printed on every page, §8.3)
  furniture   header band (eyebrow, title), meta rows, footer label — repeated page furniture
  blocks      an ordered list of:
    Heading(level 1..3, text)
    Paragraph(runs)                       runs: Text(style ∈ body|strong|emph|code) · Link(text, target)
                                                · Symbol(id) · Action(text, intent)            (r2 (R10g))
    Callout(role, label, runs)            role ∈ NARRATIVE | WARN | PROBLEM | FIX | NOTE | LOG_FINDINGS
                                                 | NOT_RENDERED | DID_NOT_RESOLVE | LESSON
                                          a callout splits across pages and repeats its label (r2 (R10c))
    Meta(rows: label, value)
    Mono(heading, lines, lineLinks?)      evidence as log lines; a line may carry a link (a node line → node:<id>)
    Table(columns, rows, highlighted[], rowWhen, rowWhenLabel, scalarLine, emptyReason, notes, rowLinks?)
    Figure(heading, caption, picture, markers)
         picture ∈ Raster(image, pxPerPt) | Vector(displayList) | Diagram(diagramSpec) | LiveGraph(focus?)
         markers: numbered anchors over the picture, each (n, x%, y%, label) — the spotlight's numbered look
    ListBlock(ordered, items of runs)
    Code(lines)                            monospace, never interpreted
    Rule / PageBreak / KeepWithNext        layout hints, never content
```

`Action` *(r2 (R10g))* is a run that posts a named intent rather than following a link. It exists for the D-I3a
*Apply the authored view* offer, which the window must offer wherever it shows the VIEW callout, as the tab does.
The set of actions is closed, analyser-authored and never available to a lesson: `applyAuthoredView` and
`exportTableCsv(section)`, the latter calling the same code path as `report {name, section, csv}`.

**Text is never cut silently** *(r2 (R10d))*. Where a value must be shortened to fit (a title, a meta value, a
cell), layout elides it and records a diagnostic. The full value stays in the copy/find text, the tooltip, and the
document's last-page notes, which list "N values shortened to fit".

Every block carries an **authority**, the trust label that decides its styling and its standing label:

| Authority | Means | Examples |
|---|---|---|
| `EVIDENCE` | Derived from the log, the graph or a stored call; re-rendered live (D-I3). Drawn only by the analyser. | record lines, tables, chart and topology pictures, a lesson's `LiveGraph` figure |
| `TESTIMONY` | Someone's words, attributed | report notes and narrative (D-I2), flag notes as written (D-I1), a lesson's prose and its marks, a walk caption |
| `APP` | The analyser's own statement about the evidence | fingerprint/view/unresolved announce lines, NOT RENDERED, LOG FINDINGS, WARNING, shortened-value notes |

The authority is not decoration. `TESTIMONY` always renders with a standing label: `NARRATIVE — the author's
account, not log evidence` for reports (unchanged), and `LESSON — explanation, not this run's evidence` for
teaching. D-I2 therefore holds on every page of every document type, and the ledger seam (§3.4) is already there.

### 3.2 Do today's reports fit? Section by section

| Today (`ReportRenderer`/`FindingReport`/`ReportsPanel`) | M75 blocks | Fits? |
|---|---|---|
| header band "INVESTIGATION" + title | furniture | yes; a title too long is elided with a note (E15d) |
| meta strip (WRITTEN AGAINST, AUTHORED VIEW, LOG, CREATED, RENDERED) | `Meta` | yes |
| announce lines: fingerprint, VIEW, UNRESOLVED REFERENCES, LOG FINDINGS, notes | `Callout` (APP, or TESTIMONY for notes), in today's order, before any section; VIEW carries the `applyAuthoredView` `Action` | yes. Order is a test (D-I3a acceptance 9). |
| FINDING: note + fix + record lines | `Callout PROBLEM` + `Callout FIX` + `Mono` | yes. The strings pass through untouched (D-I1) and are drawn as written, not transliterated (E5). |
| RECORD, including a rolled-set member `file` | optional `Figure` + `Mono`, headed with the member file | yes, with E15b corrected |
| CHART + markers table (M32.7) + markers cap note | `Figure(Raster)` + `Table` with notes | yes, with E15a corrected: the cap is printed |
| TOPOLOGY focus, or "not defined" | `Figure(Raster)` or `Callout NOT_RENDERED` | yes |
| SERIES | `Figure(Raster)` or `Callout NOT_RENDERED` | yes |
| TABLE from aggregate / series / coverage (M33.7), with notes (M68.1), the highlight rule printed (D-I8), the header repeated per page, the empty reason, the scalar line, caps named (D-M3), the coverage refusal | `Table` (+ notes, + `rowLinks` from the record column, + `exportTableCsv` `Action`) | yes. Every field of `ReportRenderer.TableData` has a home. |
| NARRATIVE | `Callout NARRATIVE` (TESTIMONY), split across pages with its label repeated | yes, with E15c corrected |
| DID NOT RESOLVE (D-I3), WARNING per section | `Callout` (APP) | yes |
| footer label + "n / m" | furniture, laid out after pagination | yes. The layout knows the page count before any backend runs, so the two-pass `selectPage` trick goes away. |
| `FindingReport`: event summary strip, raw event lines, node log, "The cycle", "Where it sits", trend with dashed record marker | `Meta` + `Mono` ×2 + `Figure` ×≤3, **plus** the fingerprint, LOG FINDINGS and "no declared node" callouts | yes, with E15f corrected |
| tab-vs-PDF wording (E15e) | one wording, from the layout | yes, corrected: the tab adopts the layout's labels in P1 |
| **Walks** | not a report section (E11) | see §3.3 |

With the E15 corrections, nothing in today's reports needs a block the model lacks. Two things change on purpose:
transliteration (E5) goes, and the Letter page size becomes a parameter.

### 3.3 Walks: a handout, partly

A walk is a sequence of steps. Each step restores a view and lights up to six targets with captions. A handout
(`kind = WALK_HANDOUT`) maps onto that naturally. Each step gets a `Heading`, the step sentence as TESTIMONY, and a
`Figure` with numbered markers at the targets.

But a step's view can be rendered off-screen only for topology and chart targets:

- `records:row:N`, `detail:node:X`, `source:…`, `menu:…` and `tab:…` targets have no off-screen painter.
- Restoring the live view to screenshot it would move the person's canvas, the trap `TopologyPanel.paintOffscreen`'s
  comment already names.

So a handout states, per step, which targets it could draw, and lists the rest as text.

*r2 (R13).* `WalkReel` (E16) is the walk's existing outward renderer. A handout adopts its finish-page rules as they
stand: the sender's words are labelled as the sender's, there is no claim that the bundle holds these records, and
words pass through the redaction rule (§11). The handout is a print form of the reel, not a competing account. It is
P4 and optional (OD-75.14).

### 3.4 Sources, and the ledger seam

```
interface DocumentSource {
    DocumentKey key();        // report:<name>, finding:<recordIndex>, teaching:<id>, walk:<name>
    long revision();          // bumps when the definition changes
    Binding binding();        // what the document is ABOUT (§10.2) — declared inputs, each a staleness fact
    AssemblyPlan plan();      // which captures the adapter must make (pictures, tables), declared
}
```

The session node (§10) knows only `DocumentKey`, the revision and the binding. An intent ledger would be one more
source: stated intent is TESTIMONY, the evidence check is EVIDENCE, and the verdict is APP. If it needs a verdict
block, that is one more block type, added by a revision of this spec. Nothing else changes. That is the whole seam.

## 4. One layout, two backends

### 4.1 The pass

```
Document ──layout(document, PageGeometry, FontSet, StyleTokens)──▶ LaidOut
LaidOut = pages[ Page(size, ops[]) ], links[], actions[], textSpans[], outline[], diagnostics[]
```

`layout` is a pure function: no Swing, no I/O and no clock (the RENDERED time is a field of the document). It owns
every decision `ReportRenderer` makes today: wrapping, keep-together, table header repetition, callout splitting,
picture fitting and the `ensure` thresholds. Nothing else makes those decisions. The output is deterministic, which
is what makes the golden tests in §13 possible.

**Layout receives raw text, never `UiText` output** *(r2 (R15))*. SF-2's neutralising inserts an invisible U+200C,
and §5.4 would draw that as a visible escape.

### 4.2 The page canvas

The display list is a small, closed set of ops. The "page-canvas interface" is the visitor over it:

```
interface PageCanvas {
    void beginPage(PageSize size);
    void fill(Shape2 shape, Rgba color);                         // rect, round rect, polygon, path (lines + cubics)
    void stroke(Shape2 shape, Rgba color, float width, Dash dash);
    void glyphs(FaceId face, float size, Rgba color, float x, float baseline, int[] glyphIds, float[] advances,
                String text);                                    // positions decided by layout; text kept for copy/find
    void image(ImageRef ref, float x, float y, float w, float h);
    void pushClip(Rect r); void popClip();
    void endPage();
}
```

- **`PdfCanvas`** is `PdfDoc` grown. It adds Type0/CIDFontType2 embedded fonts with `/W` widths taken from the same
  font tables layout used; a `ToUnicode` CMap, so copy and search work in any PDF reader; paths and dashes; and
  `ExtGState` for alpha. The coordinate flip stays inside it, as today.
- **`Java2dCanvas`** draws each op onto a `Graphics2D` under the window's zoom transform, with
  `FRACTIONALMETRICS_ON` and hinting off. Glyphs are placed by `GlyphVector.setGlyphPosition` from the layout's own
  advances, never by `drawString`, which would re-measure and re-break.

Links, actions, text spans (for find, selection and copy) and the outline are *not* drawing ops. They are side tables
of the `LaidOut`, in page coordinates. The window hit-tests them, and the PDF writer uses them for in-document
destinations.

**Why equality holds.** Both backends receive identical glyph ids at identical positions with identical advances,
from identical font bytes, and neither measures text. A PDF viewer advances by `/W`, which is the font's own `hmtx`,
and the window places each glyph explicitly. Glyph ids are kept as they are in the font (`CIDToGIDMap /Identity`,
§5.3), so the ids layout chose are the ids the PDF draws. Rasters are the same `ImageRef` in both backends (§7.1).
What can still differ is pixels (hinting, anti-aliasing, a viewer's own smoothing). That is why the equivalence
tests compare positions, not images (§13.2).

## 5. Fonts

### 5.1 Choice and licence

Recommended: **IBM Plex Sans** (Regular, Italic, SemiBold) and **IBM Plex Mono** (Regular, SemiBold), all under the
**SIL Open Font License 1.1**. It is one designer family, so sans and mono share proportions, and it covers Latin,
Latin Extended, Greek and Cyrillic.

*r2 (R15).* Two properties are **not yet verified** and are P0 checks, read from the font tables before the choice
is final:

- **Default figures.** If Plex Sans's default digits are proportional, tabular figures would need GSUB `tnum`, which
  the planned reader does not parse. The fallback is to set numeric table cells in Plex Mono, as `ReportRenderer` does
  today with Courier. That is acceptable, and it is the current behaviour.
- **Script coverage.** Coverage is to be read from `cmap`, not taken from marketing pages.

Alternatives under the same licence: Inter with JetBrains Mono, and Source Sans 3 with Source Code Pro (OD-75.1).

What the OFL requires, and how M75 meets it:

- **Bundling with software** is permitted, but the licence text must travel with the fonts. `OFL.txt` sits beside the
  TTFs under `src/main/resources/fonts/` and is listed in the jar's third-party notices.
- **Embedding in documents** is permitted. In the OFL FAQ's reading, subsetting inside a PDF is a document-embedding
  act, not distribution of a modified font. **Confirm this against the FAQ text at implementation** and record it in
  the PR, in the spirit of rule 6.
- **No Reserved Font Name question** arises for the TTFs in the jar: they ship byte-for-byte unmodified, pinned by
  SHA-256 in a test.
- **Selling the fonts alone** is forbidden; we do not do it.

Cost: about 1.0–1.3 MB on a ~5.0 MB jar. Measure it at P0 and record it (OD-75.1).

### 5.2 Metrics

A small TrueType reader (`doc.font.TrueTypeFont`) gives advances in font units. It reads `head`, `hhea`, `hmtx`,
`cmap` (formats 4 and 12), `OS/2`, `post` and `maxp`, plus `glyf`/`loca` for subsetting. Layout uses only these
values.

The window builds its `java.awt.Font` from the same bytes (`Font.createFont(TRUETYPE_FONT, …)`), so glyph ids agree.
No AWT font metrics are consulted anywhere in layout, and a source-text test enforces that (§13.3).

### 5.3 Embedding and subsetting

*r2 (R15).* Fonts are subset per PDF, keeping a report small:

- **Glyph ids are preserved.** Unused glyphs become empty outlines in place, and `CIDToGIDMap` is `/Identity`, so
  there is no renumbering.
- **Composite glyphs are closed over.** An accented letter keeps its base glyph and its mark.
- **`cvt `, `fpgm`, `prep`, `maxp`, `head`, `hhea` and `hmtx` are retained.** `head.checkSumAdjustment` is
  recomputed.
- **A six-letter subset tag** prefixes the font name.

Every produced test PDF is validated by a structural parser (§13.2). A `tools/` script also runs `qpdf --check` and a
rasteriser where they are available; it is optional, not a gate. If subsetting proves fragile, the fallback is full
embedding, at about +600 KB per PDF: correctness first, size second.

### 5.4 Characters the fonts cannot draw, and characters that lie

- **A character with no glyph** renders as a **vector frame with its code point in ASCII hex inside** (`U+4E2D`)
  *(r2 (R15): not `⟨ ⟩`, which the face may lack)*. It is never `?`, and never a silent system-font fallback, which
  would break G2. Copy and search return the original character. The document's last page counts them ("12
  characters this font cannot draw").
- **Right-to-left scripts** *(r2 (R15))* are not covered by the base Plex faces, so Arabic or Hebrew text renders as
  code-point frames. If OD-75.1 chooses a family with that coverage, it renders in logical order, unshaped. The docs
  state whichever is true.
- **Format and control characters are made visible, not obeyed.** Bidi controls (U+202A–U+202E, U+2066–U+2069),
  zero-width characters, and C0/C1 controls render as their escapes. Text is never reordered, so a log line cannot use
  an override to make `profit` read as `tiforp`, or hide part of itself.
- **Symbols** the codebase uses (flag, check, arrows, the numbered badge) are drawn as **vector shapes** by layout
  (`Symbol` runs), not taken from a font, so they do not depend on coverage.

## 6. Style tokens shared with the spotlights

`StyleTokens` is immutable and pure, in package `doc.style`. It is consumed by the layout, `SpotlightOverlay`,
`ReportsPanel` (until P4 replaces it), the topology and chart painters, and the diagram painter. It holds:

- **Ink and surfaces**: ink, muted, rule, panel and paper. For light these are today's `#1F2A44 / #57606A / #D8DEE4 /
  #F6F8FA`.
- **Roles**: warn, problem, fix, narrative, lesson and highlight, each an **accent plus a rule for its background**.
  *r2 (R18).* A background is `mix(accent, surface, k)`, the rule `ReportsPanel.Theme` uses today, not a literal. On
  screen it follows whichever of the four themes is in force; on paper the surface is the light paper. The light
  accents equal today's PDF values. The dark accents equal `ReportsPanel.Theme`'s dark accents, which are constants
  there too.
- **Accent**: the two values of `UiTheme.accent()` (`#1F6FEB` / `#6CB6FF`).
- **Callout card**: fill **with its alpha** (244, and 248 for the strip), border, text, corner radius and padding, at
  today's overlay values (E9) *(r2 (R18))*.
- **Numbered marker**: 22 px in diameter (16 pt on the page), accent fill, white SemiBold numeral. The numbering rule
  is the spotlight's high-water rule (M64.6): a number is kept for the life of its document.
- **Type**: faces *(r2 (R8))*, plus a scale of title 16, heading 11.5, body 10.5, caption 8.5, table 8.2 and mono 8.2
  (today's sizes), in the bundled faces.

**Painters take tokens, fonts included, as a parameter** *(r2 (R6, R8))*. This fixes E7. It is wider than r1 said:

- `TopologyCanvas` and `ChartPanel` take a `StyleTokens` input. On screen it defaults to the theme's set; for every
  document picture it is `LIGHT` with the bundled faces.
- The static helpers `fade`/`fade2`, and `paintLegend`, use the tokens they are given.
- `ChartPanel` stops reading `UIManager` for its margins.
- Text is measured and drawn with the token face.
- For documents, the chart legend is drawn **by the chart painter** rather than by the FlatLaf `legendScroll`
  children, so no LAF delegate paints into a document.

A document is light on paper. The window offers dark paper as a palette swap of the same `LaidOut` (OD-75.4).
Palette is not layout, so G2 is unaffected.

"Text matches the spotlights" needs one more step: the overlay draws its captions in the bundled sans (OD-75.9).
Without it, the colours and shapes match and the face does not.

## 7. Diagrams

### 7.1 Evidence pictures (P1–P2): rasters, at a device scale

*r2 (R7).* Topology and chart pictures stay rasters from today's off-screen painters, with `LIGHT` tokens. They are
laid out at **today's logical size** (1200×800, 1200×600) and painted under `g.scale(s, s)`, so the composition is
the one the canvas would draw and only the pixel density changes. Painting at 2400×1600 instead would re-run
`fitToView` (zoom capped at 2.2) and the tick layout, and produce a different picture.

**One raster, both backends.** Each figure has a single `ImageRef` at `s = 2` (≈333 dpi on the page). The PDF embeds
that raster and the window scales it, so figure content is identical (G2).

**The cost, stated.** A 2× raster has four times the pixels. The PDF-size budget in §12 is restated for it, and
OD-75.15 offers `s = 1.5` if PDFs prove too large.

**Rasters dominate memory, and are budgeted** (§12). The adapter holds each raster Flate-compressed and decodes it
only for the visible pages ±1. `PdfDoc.addImage` streams rows into the deflater instead of copying `w×h×3` first.

They are evidence, and they are drawn by exactly the code that draws the canvas — which is the point.

### 7.2 Vector capture (P3)

A `RecordingGraphics2D` is a `Graphics2D` that records shapes, strokes, fills, alpha, transforms, clips and text into
display-list ops. It lets the existing painters draw vector figures into a document.

*r2 (R8).* "Without being rewritten" was wrong. The painters measure text through component `FontMetrics` before they
draw it (`TopologyCanvas.fitNodeWidthToLabels` l.261–264; `ChartPanel` axis labels, pins and wrapping), so
remapping text afterwards would misplace it. Vector capture therefore rests on the P0 change that puts the face in the
tokens:

- A document capture sets the bundled face as the painter's font.
- The recorder answers `getFontMetrics` from that same bundled font, so measurement and drawing agree.
- The legend is the painter's own drawing (§6), so no FlatLaf delegate is recorded.

The recorder is **strict**: an operation it does not support (gradients, arbitrary `Composite`s, a volatile image)
makes it fall back to the §7.1 raster for that figure. The fallback is recorded in the diagnostics; the figure is
never drawn wrong. A test runs both painters against the strict recorder over the demo set and the fixture graphs,
and pins the set of operations they use.

### 7.3 Teaching diagrams: the description format

A teaching diagram is a fenced block in a teaching document (§8.1):

````
```diagram
title   The diamond
layout  down                         # down | right — LayeredLayout's two orientations · sequence (below)
source  claude.txt — "The mental model in 60 seconds"
event   E  "PriceEvent"
handler A  "PriceHandler"            # @OnEventHandler
trigger B  "BidTracker"              # @OnTrigger
trigger C  "AskTracker"
trigger D  "Spread"
group   G  "generated processor" A B C D
E -> A  dispatch
A -> B  ctor                          # constructor reference: B depends on A
A -> C  ctor
B -> D  ctor
C -> D  ctor
mark 1 A "receives the event"
mark 2 D "after both parents — check the records for how often"
```
````

**Node kinds** (closed): `event`, `handler`, `trigger`, `service`, `external`, `sink` and `note`. Each kind has one
token-defined shape and colour. *r2 (R19).*

- `service` has **its own shape**. It does not borrow the topology canvas's `EXPORT_SERVICE` hexagon, because an
  exported service is an entry point into the processor, the opposite direction.
- The other kinds reuse the canvas's node classes where the meaning is the same.

**Edge kinds** (closed):

| Kind | Draws | Notes |
|---|---|---|
| `dispatch` | solid, with an arrow | |
| `ctor` | dashed | the constructor-reference wiring the framework topo-sorts |
| `inject` | dotted | *r2 (R19)*: only **from an `external` node outside the processor group** to the node whose `@ServiceRegistered` method receives it. `claude.txt` describes it as invoked when the host calls `registerService`, so the service comes from outside the graph. The parser refuses an `inject` whose source is inside a `group`. |
| `branch` | a labelled pair from one parent, `true` and `false` | *r2 (R19)*: the `@OnTrigger(dirty = false)` route `claude.txt` teaches. The true path runs the default children and the false path runs the `dirty = false` child. |
| `returns` | a labelled back-arrow | for a handler's boolean |
| `flow` | plain | |

**Sequence form.** `layout sequence` draws lifelines for the named nodes, with numbered calls in the order written,
for "who calls my methods?" and dispatch order. *r2 (R19).* This is a **second, small layout** (columns and rows,
about 150 lines), not `LayeredLayout`; E12 holds for the graph form only.

**No coordinates.** Positions are always computed. A diagram therefore cannot be laid out to suggest something its
edges do not say.

**Markers.** `mark n node "label"` uses the spotlight's numbered look, up to six markers, as on the canvas.

**Limits.** At most 24 nodes and 48 edges; labels up to 60 characters; ids `[A-Za-z][A-Za-z0-9_]{0,31}`. A breach is
a refusal that names the line, as the pack linter does.

**`source` is required.** A teaching diagram asserts framework semantics, and rule 6 says those are read, not
inferred. The parser refuses a diagram with no `source`, and the caption prints it.

- The reference read for this spec (`claude.txt`, 2026-10-17) states two things: topological ordering from
  constructor references, and the boolean as the dirty/propagation signal.
- It **does not state how many times the diamond's join node runs per event.** So no diagram may assert a count. The
  packs already phrase that as "check it against the records", and a diagram does the same.

**Live form.** `from loaded-graph [focus <name>]`, in place of nodes and edges, **requests** a `LiveGraph` figure.
The analyser draws it through the topology painter from the loaded processor, as EVIDENCE. If no graph is loaded, it
says so. *r2 (R9).* The lesson does not author that evidence:

- It cannot pass nodes or edges into a `LiveGraph`.
- A `mark` on a `LiveGraph` figure is drawn in the LESSON style, under the LESSON label, beside the evidence and not
  in its colours.

**Rendering.** Graph-form nodes and edges become a `ProcessorTopology`-shaped input to `LayeredLayout`. The diagram
painter draws the result onto the `PageCanvas` directly, so it is vector from day one in both backends.

## 8. Teaching documents

### 8.1 Format

A teaching document is UTF-8 plain text in a **closed Markdown subset**, parsed by our own parser. It supports:

- `#`–`###` headings, paragraphs, and `-` and `1.` lists
- `**strong**`, `*emph*` and `` `code` ``
- fenced code and fenced `diagram` blocks
- `> LABEL: text` callouts (role `LESSON` or `NOTE`)

Anything else — raw HTML, images, tables, footnotes, reference links — renders **as the literal characters written**.

Links are written `[text](target)`. A target is either an analyser target in the spotlight vocabulary, as restricted
by §8.3, or an `https:` URL. URLs are shown and copyable, and are never fetched (§11). OD-75.7 decides whether a
click may hand one to the system browser.

Limits: 256 KiB per file, 400 blocks and 24 diagrams.

### 8.2 Where they come from

- **Bundled lessons** live in the jar under `/teaching/`, one per philosophy concept the packs teach: events, handlers
  and triggers; dispatch order; the diamond; constructor wiring and injection; reading a record. Each is reviewed
  against `claude.txt` and the golden path before it ships, with the source line on every diagram.
- **Dropped files.** A `.lesson.md` can be dropped on the start page or opened from the menu, and is treated as
  foreign text, rendered plain. *r2 (R17).* `dropKind` gains `DropKind.LESSON` for `.lesson.md`, checked before the
  LOG default (E18). A drop holding a lesson and a log opens both: the log as today, and the lesson as a document. The
  lesson open is posted as `DocumentOpenRequested`, never a direct call.
- **Pack entries.** How a pack entry points at a lesson is a pack-format change (an entry kind `read`). That belongs
  to the packs' owner and is OD-75.7, not decided here.

Every lesson block is TESTIMONY and carries the `LESSON` label, except a `LiveGraph` figure, which is EVIDENCE the
analyser drew (§7.3). Spotlights lit from a lesson link are tagged `lesson`, not `assistant` (E10). The overlay gains
a tag parameter and nothing else.

### 8.3 Provenance and what a lesson may point at

*r2 (R9).*

- **Provenance on every page.** A bundled lesson's footer reads "bundled lesson, reviewed against the Fluxtion
  reference". A dropped one reads "lesson from <file name> — not reviewed by the analyser", and its LESSON label adds
  "(foreign)". A stranger's file can therefore never borrow the bundled lessons' standing.
- **Targets a lesson may light.** Lessons may light `records`, `records:row:N`, `detail`, `detail:node:X`,
  `topology`, `topology:node:X`, `graph[:name]…`, `source:…`, `project…` and `tab:…`.
  - **`menu:` and `toolbar:` targets are refused for a non-bundled lesson.** A lit menu item can be run by a press
    (M64.11), so a foreign file could otherwise point at an action with an instruction caption.
  - Bundled lessons may use them, because they are reviewed.
  - The refusal is the node's decision (§10.3), and it is stated in the window.

## 9. The window

### 9.1 Shape

The window is a non-modal, resizable `JFrame` titled `Document — <title>`, with the title set through `UiText`. There
is one window per open document (one at a time in P2; OD-75.11), and it remembers its size and position. It never
blocks the main frame (M35.9: no modal in the load path).

- **Toolbar**: page `n / m` field, previous and next, zoom (fit width, fit page, 50–400 %), find, *Export PDF…*,
  Refresh (enabled when stale), and the dark-paper toggle (if OD-75.4 says yes).
- **Body**: a continuous vertical scroll of pages on a neutral backdrop, with optional page thumbnails at the left.
- **Status line**: the node's own `statusLine` from the snapshot (§10.2), for example "about demo.yml · generation 3
  · view: all records". When the document is stale, the status line also says why. *r2 (R4).* The window **renders
  it and never composes it**.
- **Find** (the toolkit's menu-shortcut mask + F): searches the `LaidOut`'s text spans, highlights every hit, and
  steps with Enter and Shift-Enter.
- **Select and copy**: dragging selects text in reading order across blocks, and the shortcut copies the logical
  text (the original characters, §5.4). The context menu has "Copy page text". It also offers "Export table as CSV…"
  on a table, through the `exportTableCsv` action *(r2 (R10g))*.
- **Links and actions**: a hover cursor and an underline. A tooltip, set through `UiText.tooltip`, shows the target.
  A link the node would refuse is drawn struck through, with the reason in the tooltip.

### 9.2 Accessibility

*r2 (R16): only what can be asserted is claimed.*

- **Keys.** Every action has one: PageUp/PageDown, Home/End, zoom in/out/reset, Tab and Shift-Tab through links and
  actions with a visible focus ring drawn from the tokens, Enter or Space to follow, and Esc to close find. All use
  `Toolkit.getMenuShortcutKeyMaskEx()`, so the same test runs on macOS and on CI.
- **Accessible tree.** Each page is a child with role `AccessibleRole.PANEL`, an accessible name ("Page 3 of 12"), and
  an `AccessibleText` over its spans in reading order. Links and actions are `AccessibleHyperlink`s.
  - The frame tests assert this tree.
  - That a particular screen reader reads it well is a by-eye check on macOS VoiceOver at P2 acceptance, not a test
    claim.
- **Contrast.** Both token sets meet WCAG 2.1 AA for body text, and 3:1 for markers and rules. A test computes it
  (§13.3).
- **Zoom** reaches 400 % with no horizontal clipping of the page chrome. There is no animation.
- **PDFs** carry `/Title`, `/Lang` and extractable text. Tagged PDF is OD-75.13.

### 9.3 Opening

A document can be opened from:

- the Reports tab, through a *View* button beside *Export PDF*;
- the Project panel's report row (reveal-only, D-L3: it navigates to an existing report);
- the finding export;
- the status line after `report {path}`, as an offer, not an automatic pop-up;
- Help ▸ Learn, for lessons;
- a dropped lesson file.

Every route is one intent to the session node (§10).

### 9.4 The Reports tab

The tab keeps its list, its navigation buttons and the D-I3a *apply the authored view* offer. The window offers the
same thing through the `applyAuthoredView` action, so neither surface lacks it.

The tab's section preview is the third rendering (E2). P1 makes its wording the layout's (E15e). P4 replaces the
preview with an embedded `Java2dCanvas` over the same `LaidOut`, after the window has shipped and parity is shown, so
that one report has one rendering (OD-75.5).

## 10. Rule 9: the `documentView` node

What is open, which page, whether it is current, and whether a link may be followed are all session state. They
belong to a new node, `documentView`, modelled on `walkPlayback` (ticketed, generation-bound, with a frozen
definition) and on `evidenceCapture` (a single-moment capture that pauses Follow).

### 10.1 Events, effects and their answers

*r2 (R4): every effect names its synchronous answer, as `SessionDriver.Adapter.perform` requires ("never null … an
unanswered effect is a hole in the record").*

| Kind | Name | Carries / answers |
|---|---|---|
| intent | `DocumentOpenRequested(source)` | from the window, the tab, a menu, a drop, or `report {show}` |
| intent | `DocumentPageRequested(ticket, n)` | toolbar, an in-document link, an agent |
| intent | `DocumentLinkActivated(ticket, linkId)` · `DocumentActionActivated(ticket, actionId)` | a click |
| intent | `DocumentRefreshRequested(ticket)` · `DocumentCloseRequested(ticket)` | |
| effect | `AssembleDocumentEffect(opId, ticket, source, binding)` | answer **`DocumentAssemblyScheduled(opId, ticket)`**; later fact `DocumentAssembled` or `DocumentAssemblyFailed` |
| fact | `DocumentAssembled(ticket, binding, digest, pageCount, links, linkPolicy)` | `linkPolicy` = the resolution's fingerprint mismatch, the identity verdict, and the per-link verdicts the assembler computed (§10.3) |
| fact | `DocumentAssemblyFailed(ticket, reason)` | |
| effect | `ShowDocumentEffect(opId, ticket)` | answer `DocumentShown(opId, ticket)` |
| effect | `ScrollDocumentEffect(opId, ticket, page)` | answer `DocumentScrolled(opId, ticket, page)` |
| fact | `DocumentPageShown(ticket, n)` | the viewport's visible page, debounced |
| effect | `ApplyDocumentLinkEffect(opId, ticket, target, caption, tag)` | answer **`DocumentLinkApplied(opId, ticket, lit, reason)`** |
| effect | `RunDocumentActionEffect(opId, ticket, action)` | answer `DocumentActionRan(opId, ticket, ok, reason)` |
| effect | `CloseDocumentEffect(opId, ticket)` | answer `DocumentClosed(opId, ticket)` |
| fact | `DocumentDefinitionChanged(key, revision \| deleted \| renamed)` | from report save, delete or rename; lesson reload |

**Tickets are typed** *(r2 (R2))*. Document tickets are a `DocumentTicket` value, distinct from the `long` tickets
`walkPlayback` checks. A document answer can never satisfy a walk's staleness check, and a walk answer can never
satisfy a document's. A unit test posts a document fact carrying a walk's ticket value and asserts that
`walkPlayback` is unchanged, and the reverse.

### 10.2 Binding and staleness

*r2 (R3).* A document is about more than a log generation. The binding is declared by its source (§3.4). Each input
is a fact the node already receives or gains:

| Binding input | Fact | Why a report depends on it |
|---|---|---|
| log generation | `openLog` (existing) | everything |
| graph revision | `openGraph` (existing) | TOPOLOGY, coverage tables |
| view filter (`filterKey`) | `pairingQualifier.filterKey` (existing) | the VIEW callout, TABLE rows, chart scope (`scopeText`) — D-I3 |
| flag revision per referenced record | **new** `FindingsChanged(recordIndexes)` from the flag write site | FINDING is byte-identical to what `flag` wrote (D-I1) |
| chart, marker and focus definition revisions | **new** `ChartDefinitionChanged(name)`, `FocusDefinitionChanged(name)` | CHART reads the live `GraphPanel`; TOPOLOGY reads `namedFocuses` |
| producer findings revision | `ProducerFindingsObserved` (existing) | LOG FINDINGS can land after the load (D-MA0c) |
| file-identity verdict | `openLog.identity()` (existing) | as for walks: records are certified only while `recordsTrusted` |

**Staleness.** The node marks a SHOWN document STALE when any input that the document's `plan` names moves. The
reason names the input: "the finding on record #42 changed", "the view changed", "the log was replaced". A Follow
append changes the generation's record count, not its identity. It marks only documents whose plan counts records
(tables, coverage), and those only once per Refresh. It never churns the window.

**This is a recorded departure from D-I3** *(r2 (R3))*. D-I3 says a report re-renders live, and the Reports tab
re-renders on a filter change. The window instead **states** that it is stale and offers Refresh, because a page that
reflows under a reader's eyes loses their place and their selection. D-I3's guarantee — a report is never shown as
current when its evidence has moved — is kept by the STALE state. Its mechanism, immediate re-render, is not. That is
OD-75.3. If the owner prefers live re-render, the node issues `AssembleDocumentEffect` on the same facts instead of
marking STALE; nothing else changes.

### 10.3 Decisions the node makes, and nothing else does

- **When to assemble**: on open and on Refresh. A late `DocumentAssembled` with an old ticket or binding is dropped
  (the R8 rule from walks).
- **When it is stale**: §10.2. A delete closes the document and says so. A rename keeps it under the new key, as
  walks do.
- **Whether a link may be followed**:
  - The link must belong to the current ticket, and the document must be SHOWN. A STALE document's links are refused
    with "refresh first", because the record a link names may not be the record it meant.
  - Record and node links are **never followed into a log the report was not written against**. If `linkPolicy`
    carries a fingerprint mismatch (D-I3a), they are refused with the announce line's own wording (OD-75.8).
  - Record links are refused while the identity verdict says records are not trusted, as walks do.
  - Chart links name things by name and stay allowed.
  - A focus link is a view change (apply the focus) followed by a light. The spotlight vocabulary has no focus target
    *(r2 (R19))*.
  - A lesson's `menu:` and `toolbar:` links are refused unless the lesson is bundled (§8.3).
- **What a link does to a showing walk** *(r2 (R2))*: a followed document link **ends the walk**, through
  `walkPlayback`'s existing `WalkEndRequested` path, with the reason "a document link was followed". It is never lit
  alongside a walk, and never passed off as the walk's own light.
- **Page**: `DocumentPageRequested` moves it. Free scrolling reports `DocumentPageShown` as a fact, so
  `context.document.page` is what the person sees.

### 10.4 The adapter, and single-moment capture

*r2 (R5).* The assembler (`DocumentAssembler`, `ui`) performs `AssembleDocumentEffect` in three stages, so the
document describes one moment as today's single EDT call does:

1. **Pin (EDT, one turn).** Read the resolution, the coverage claim (`coverageForReport`), the record count, the
   filter, the flags the plan names, and the chart and focus definitions — one consistent read, as today. Follow is
   paused with `SetFollowEffect` and restored after stage 3, exactly as `evidenceCapture` does it.
2. **Extract (off-EDT, `core.Background`).** Store-scanning work — SERIES extraction (`SeriesExtractor`) and TABLE
   assembly (`ReportVerb.assembleTable`) — runs against the pinned inputs. The store is append-only under a paused
   Follow, so the pinned record count bounds every scan.
3. **Paint (EDT, one turn per figure).** Each picture is painted by an **off-screen** component built for that
   capture. Live components are not borrowed.
   - Today `ChartPanel.toImage` resizes the live chart and overwrites `lastPaint`, `GraphPanel.chartImage` hides the
     live *Edit series* button, and `exportFinding` sets a record marker on the live panel. Each of those becomes an
     off-screen copy built from the live panel's definition, as `ReportSeriesPicture` already does.
   - A test asserts that a walk's chart `drawnFact` and the live chart's size are unchanged by a document capture.

The assembler then lays out, off-EDT, and reports `DocumentAssembled`. It holds the `LaidOut` keyed by ticket. The
window draws the `LaidOut` whose ticket the snapshot names, and nothing for any other ticket.

`MainFrame.renderReportPdf` and the finding branch become callers of the same assembler, so the PDF path and the
window path cannot drift.

**Links are lit by a shared primitive** *(r2 (R2))*. `ApplyDocumentLinkEffect` does not reuse the walk performers.
`ApplyWalkViewEffect` needs a `WalkSpec` and a step, and `walkPresenter.light` sets `walkOwnSpotlight` and answers
`WalkTargetsLit`, which `walkPlayback` consumes. The walk-free part — resolve, select, light, renumber — is extracted
from `WalkPresenter` into a `SpotlightLighting` primitive. Walks and documents each call it, with their own answer
types and their own tag.

### 10.5 Snapshot, context, regeneration and the agent surface

**Snapshot** *(r2 (R4))*:

```
DocumentViewState(key, kind, title, provenance, phase, page, pageCount, binding, stale, staleReason, statusLine)
```

It is published on `SessionSnapshot`. `statusLine` is composed by the node, never by the window.

**Context.** `context.document` is published. *r2 (R20).* It sits **above** `MainFrame.context()`'s fresh-start early
return (`filter == null`), behind `need.test("document")`, because a lesson opens with no log loaded. That is the
ONBOARDING rule ("anything a fresh start should report goes ABOVE that return"). Per ONBOARDING, the commit adding
the key names:

- its human surface: the window's status line and the Project panel's report row;
- its docs page: *Reports ▸ Viewing a report*;
- whether a report that outlives the session carries it: no, because the window is a view.

**What stays in the window**: zoom, scroll offset, find query and selection. These are per-viewer presentation that
nothing else reads and no decision depends on; the review agreed that is acceptable. If a later feature needs one of
them (an agent asking "what is selected?"), it moves into the node then, with its own fact.

**Regenerate** with `mvn -Pregen process-classes`. The `OneDispatchModelTest` additions are in §13.3.

**Agent surface.** The verb set stays at seventeen, pinned by `VerbSchemasTest`. The recommendation is one parameter,
`report {name, show: true}`, posting `DocumentOpenRequested`, plus the read-only `context.document`.

*r2 (R14).* `show` is a **read-only form, recognised before `ReportVerb.parse`**. Otherwise the build/replace path
(`parse` → `onto(existing)` → `putReport`) would create and save an empty report for a mistyped name. `show` refuses
an unknown name, names the reports that exist, and writes nothing. A `VerbSchemasTest` case covers it, as does a
witness: a `show` of a missing name leaves the report store byte-identical. Whether to add even this is OD-75.6.

## 11. Security

**Foreign text is plain text, everywhere.** Report titles, notes, narrative, flag notes, chart names, node ids,
record lines, bundle notes and lesson files are all drawn as glyphs by our own layout.

- There is no `JEditorPane`, `JTextPane`, `JLabel` HTML or `HTMLEditorKit` in the `doc` package or the window.
- The window's few Swing labels (title, tooltip, status) go through SF-2's `UiText`.
- The lesson parser has no HTML mode, so an `<img>` is eight characters on a page.
- M75 lands after SF-2 merges, and extends `ForeignTextIsPlainTest` to the new window.

**No network, by construction.**

- The `doc` package and the window reference no `java.net.URL` stream, `URLConnection`, `HttpClient`,
  `ImageIO.read(URL|InputStream)`, `Toolkit.getImage` or `Toolkit.createImage(URL)`.
- Images reach a document only as `BufferedImage`s painted in-process. A lesson cannot embed an image at all.
- `Desktop.browse` is on the list too, **unless** OD-75.7 allows the browser hand-off. In that case it is allowed in
  exactly one method, `DocumentWindow.offerExternalLink`, which asks first. *r2 (R16).* The test is written after
  OD-75.7 is decided, against that decision.

**PDFs are inert.** They contain no `/URI`, `/Launch`, `/JavaScript`, `/JS`, `/SubmitForm`, `/ImportData`,
`/OpenAction` or `/AA`, and their links point only to destinations inside the document (OD-75.2).

*r2 (R11).* Every string the writer puts in a dictionary (`/Title`, outline titles, destination names) is written as
**hex UTF-16BE with a BOM** (`<FEFF…>`), never as a literal string. A title cannot break out of its string. Content
streams keep using the escaped literal path they already use, as glyph ids under Identity-H.

**Size caps.** Each cap is a named refusal or a visible "N more, not shown" (D-M3's rule), never a silent cut:

- 2,000 blocks per document;
- 5,000 table rows per document (the existing per-table caps still apply first);
- 64 KiB per text block, and 4 MiB of text per document;
- 500 pages;
- 40 figures;
- 4096 px per raster side;
- 48 Mpx of raster in total, held compressed (§7.1, §12);
- 256 KiB per lesson file;
- diagrams per §7.3.

Layout refuses on exceeding the page cap rather than allocating.

**Deceptive text** is neutralised (§5.4): there is no bidi reordering, and controls and invisible characters are
shown.

**Authority cannot be spoofed.**

- The standing labels (`NARRATIVE — …`, `LESSON — …`) are drawn by layout from the block's authority. A narrative
  that *contains* the words "LOG FINDINGS" is still drawn in narrative styling, under the narrative label.
- *r2 (R9).* A lesson may *request* a `LiveGraph` figure, which the analyser draws and labels as EVIDENCE. It can
  never author an EVIDENCE or APP block, nor place its words in evidence colours.
- A dropped lesson is labelled foreign on every page, and may not light `menu:` or `toolbar:` targets (§8.3).

**Machine paths in exported documents** *(r2 (R12))*. A PDF leaves the machine, as a bundle does. Narrative, notes,
flag text, record lines and captions can all carry paths. Every word an *exported* document carries passes through
`BundleProfile.redactProse`, the one copy of the rule, as `WalkReel` does. Its last page states what was redacted,
and that pictures are not redacted (the reel's `FRAMES_NOT_REDACTED` wording). The window shows words unredacted,
because it is the person's own machine. Whether a chooser export redacts by default is OD-75.16; the recommendation
is yes, with an opt-out.

**Writes.**

- *r2 (R1).* The window's *Export PDF…* is a file chooser. **The chooser is the consent, and there is no guard**,
  matching `exportReportPdfWithChooser` (E17).
- An agent's `report {path}` rides `ExportGuard`, unchanged (D-I5).
- A static check asserts that the window's export does not call `ExportGuard`. A later "harmonise on the guard"
  change would otherwise refuse a person's own save when the assistant opt-in is off.

## 12. Performance

Budgets are measured on the demo set and on a 30-page synthetic report, and recorded at P2. The tests assert generous
multiples; the measured numbers go in the PR. *r2 (R5, R7).*

- **Layout** of 30 pages takes under 50 ms. The `LaidOut` excluding rasters is under 5 MB.
- **Rasters** are held Flate-compressed (a typical 2400×1600 topology picture is a few hundred KB) and decoded for the
  visible pages ±1. The decoded working set is at most 3 pages × 3 figures × 15 MB ≈ 135 MB worst case, and typically
  under 40 MB. The 48 Mpx total cap bounds the compressed set.
- **The EDT** does stage 1 (one turn) and one turn per figure in stage 3. The budget per turn is **measured** at P2 on
  the demo set and the 30-page report, and recorded: there is no figure in advance. Stage 2 is off the EDT. The
  window shows a page skeleton with "assembling…" at once.
- **Painting** a page at 100 % takes under 16 ms. Glyph vectors are cached per page per zoom, and only the visible
  pages ±1 are painted. Thumbnails are rendered lazily at low resolution.
- **The PDF write** of 30 pages with subset fonts takes under 300 ms. Size is **restated** for 2× rasters: about 1.5
  to 3× today's for a picture-heavy report, plus 60–120 KB of font subsets. Measure it at P1. If it is too large,
  OD-75.15 (`s = 1.5`) applies.

## 13. Tests and regression closure (rule 8)

### 13.1 Structural goldens (headless)

**`LaidOut` dumps** are committed as goldens, one line per op (page, op, face, size, position, text), so a diff is a
reviewable change. They cover a fixed set of documents:

- a report with every section kind;
- a finding export;
- a lesson with each diagram kind;
- a document at each size cap;
- a page-overflowing narrative.

**Content parity with today** *(r2 (R10))*. For each section kind, the whitespace-normalised text of the new PDF
equals the text of today's `ReportRenderer`/`FindingReport` output, modulo two allow-listed differences:

- the transliteration E5 removes;
- the corrections E15a–f.

Each correction is an explicit allow-list entry with its own regression check:

| Correction | Check |
|---|---|
| E15a | the markers cap note is on the page |
| E15b | the member file is in the RECORD heading |
| E15c | a long narrative splits, with its label on each page |
| E15d | an elided value is listed in the notes |
| E15e | the tab and the PDF use one label set; a source-text test finds no second literal |
| E15f | the finding export carries LOG FINDINGS and the "no declared node" callout |

Parity is removed when the old writer is deleted.

### 13.2 PDF equals screen

- **The PDF side.** A test-only PDF reader parses each produced PDF: the object graph, and content streams with `Tf`,
  `Td`/`Tm`, `TJ`/`Tj`, `re`, `m/l/c`, `Do` and `gs`. It checks every glyph run's position and font against the
  `LaidOut`.
- **The screen side.** `Java2dCanvas` is run against a recording `Graphics2D`, and its glyph positions are checked
  against the same `LaidOut`.
- **Together**, the two passing means PDF ≡ screen at the level of placement. A pixel comparison (pdftoppm against
  the window's PNG at 2×) is an optional `tools/` check with a tolerance, run by hand at release, not a gate:
  rasterisers differ.
- **Validity.** Every produced PDF is structurally validated: xref offsets, the object count, the font programs parse,
  and subset glyph ids match the `LaidOut`'s.

### 13.3 Static checks

| Check | What it asserts |
|---|---|
| `DocumentNetworkFreeTest` | The forbidden-API list in §11, over the source and bytecode of `doc` and the window, written against OD-75.7's answer. |
| `DocumentNoHtmlTest` | No Swing HTML components in `doc` or the window. `ForeignTextIsPlainTest` covers the window. |
| `LayoutMeasuresOnlyFontTablesTest` | No `FontMetrics`, `TextLayout` or `Font.getStringBounds` in layout. |
| `FontsPinnedTest` | The TTF SHA-256s are pinned, and `OFL.txt` is present in the jar. |
| `TokensContrastTest` | WCAG contrast for both token sets, over all four themes' surfaces. |
| `PdfInertTest` | *r2 (R11).* Parses the object graph of every produced PDF and asserts that no dictionary has an action, URI, JavaScript or open-action key. |
| `DocumentExportUnguardedTest` | *r2 (R1).* The window's export path does not reference `ExportGuard`. |
| `OneDispatchModelTest` | *r2 (R16).* Extended first. Today an `OnlyIn` rule's homes are `MainFrame` methods only, and it scans only `ui`. The extension lets a rule name homes in other classes (`Class#method`), and scans `doc` and the window's package. Then: the document window is opened, scrolled and closed only in `MainFrame#renderDocument`; a link or action click only posts `DocumentLinkActivated`/`DocumentActionActivated` (home `DocumentWindow#onActivate`); `renderReportPdf` assembles only through `DocumentAssembler#assemble`; the document status line is never composed outside the node (no `statusLine(` in `ui` but the existing homes). |

### 13.4 ★ boundaries and their wrong-result witnesses

| ★ | Boundary | Static check | Witness (must fail when the boundary is broken) |
|---|---|---|---|
| ★1 | Foreign text is never markup | `DocumentNoHtmlTest` | A report titled `<html><img src="http://127.0.0.1:PORT/x">`. Open the window: a local listener records **no** request, and the title paints as its characters. Mutate: render the title through a plain `JLabel`, and the listener gets a hit. |
| ★2 | No network fetch | `DocumentNetworkFreeTest` | *r2 (R16).* A lesson with a line `![x](http://127.0.0.1:PORT/y)` and a link to the same URL. Render it and click: no request. Mutate: make the lesson parser resolve image syntax with `ImageIO.read(new URL(…))`, or make a link click call `Desktop.browse` without the confirmation. The listener then gets a hit, and the static test fails. |
| ★3 | On screen equals PDF | §13.2 | Mutate `Java2dCanvas` to re-measure with `drawString`, or the PDF `/W` to Helvetica widths: the position check fails. |
| ★4 | Stale or mismatched links are refused | node unit tests | Open a report, reload a different log, click a record link: refused with the reason, no spotlight, canvas unchanged. Mutate the binding check out: the spotlight lights record #N of the wrong log. |
| ★5 | Document pictures are light | painters take tokens | **W-0**, before any port, across **all four capture paths** *(r2 (R6))*: `renderCycleViews`, `renderFocusForReport`, `GraphPanel.renderForReport` and `ReportSeriesPicture.of`. Under the Dark LAF, each returns an image whose canvas, margin and legend pixels are the light paper. Expected to fail on `main` today (E7). |
| ★6 | Authority labels cannot be spoofed | goldens | A narrative whose text is `LOG FINDINGS — …` renders under the narrative label and in its colours. |
| ★7 | Caps are named, never silent | goldens at each cap | A 6,000-row table shows 5,000 rows and "1,000 more rows, not shown". A chart with 250 markers prints "showing the first 200 of 250" (E15a). |
| ★8 | Missing glyphs are visible | golden | A record line with `中` renders a frame with `U+4E2D`; copy returns `中`. |
| ★9 | Bidi and control characters are neutralised | golden | A line with U+202E draws the escape and keeps logical order. |
| ★10 | Teaching diagrams cite a source and draw `inject` from outside | parser tests | A diagram without `source` is refused, naming the line. An `inject` from a node inside a `group` is refused. |
| ★11 | *r2 (R3).* A moved finding marks the document stale | node unit test + frame test | Flag-edit a record that an open report includes: the window shows STALE with "the finding on record #N changed". Mutate: drop `FindingsChanged` from the binding, and the old note stays on screen unmarked. |
| ★12 | *r2 (R5).* The capture is one moment | assembler test | During assembly a Follow append lands between stages: every section counts the pinned N. Mutate: unpin the count, and two sections disagree. A walk's chart `drawnFact` is unchanged by a capture. |
| ★13 | *r2 (R9).* A foreign lesson cannot borrow standing or run menus | goldens + node test | A dropped lesson's footer reads "not reviewed"; its `menu:` link is refused. Mutate: give dropped lessons `BUNDLED_REVIEWED`, and the golden and the refusal test fail. |
| ★14 | *r2 (R11).* PDF strings cannot inject objects | `PdfInertTest` | A report titled `) /OpenAction << /S /JavaScript /JS (x) >> (` produces a PDF with no `/OpenAction` key. Mutate: write `/Title` as a literal string, and the key appears. |
| ★15 | *r2 (R12).* Exported words are redacted | export test | A narrative with a home-directory path exports with the path redacted and named on the last page. Mutate: skip `redactProse`, and the path is on the page. |
| ★16 | *r2 (R14).* `show` never writes | verb test | `report {name: "typo", show: true}` refuses and leaves the report store byte-identical. Mutate: route `show` through `parse`, and an empty report appears. |
| ★17 | *r2 (R2).* Document and walk tickets do not cross | node unit test | A document fact with a walk's ticket value leaves `walkPlayback` unchanged, and the reverse. |

The frame tests (window, find, copy, keyboard, the accessible tree) join the registered frame suites under Xvfb.
Acceptance of G1 and G5 is a built-jar run through the action socket plus the owner's visual check, as for M73.

## 14. Phasing and size

*r2 (R21): re-estimated per phase against M69's evidence (one node of this shape, plus a presenter and UI, was
9,966 additions in PR #57).*

| Phase | Delivers | Estimate (incl. tests) |
|---|---|---|
| **P0** | W-0 across four paths. The E7 fix, widened: tokens with fonts; `fade`/`fade2`, the legend and the margins. The chart legend drawn by the painter for documents. `StyleTokens` adopted by `ReportRenderer`, `FindingReport`, `ReportsPanel` and `SpotlightOverlay` with no visual change. Fonts bundled and pinned, and their figure style and coverage verified. The TrueType reader. | ~2.5–3.5k lines |
| **P1** | The document model, the pure layout and `LaidOut`. `PdfCanvas`: embedded subset fonts keeping glyph ids, paths, ToUnicode, hex metadata strings. `ReportRenderer` and `FindingReport` ported, with the E15a–f corrections and their checks. The test PDF reader, content parity, and the structure tests. The old immediate-mode writers deleted. PDFs change typography and gain the corrections only. | ~4–5k |
| **P2** | `Java2dCanvas`; the document window (pages, zoom, find, copy, CSV and the authored-view action, keyboard, the accessible tree); the `documentView` node, events, effects, answers and facts, plus the new binding facts (`FindingsChanged`, chart and focus definition changes); regen; `DocumentAssembler` with the three-stage capture and off-screen copies replacing live-component borrowing; the `SpotlightLighting` primitive extracted from `WalkPresenter`; links; `context.document`; `report {show}` if OD-75.6 says yes; the `OneDispatchModelTest` extension; redaction on export; the docs page; CHANGELOG. | ~5–6.5k |
| **P3** | The teaching format and parser; the drop kind; provenance; target restriction; the diagram language and painter (graph form, the new sequence layout, `LiveGraph`); five bundled lessons reviewed against `claude.txt`; `RecordingGraphics2D` vector capture with raster fallback and the pinned operation set. | ~3.5–4.5k |
| **P4** *(optional)* | The Reports tab preview on the renderer (OD-75.5); walk handouts on the reel's rules (OD-75.14); Help page migration. | ~1.5–2k |

**Total: about 15–20k lines through P3.** r1 said ~10k, and the review was right that this was optimistic. P2 alone
is about M69's size, and P0's scope grew with R6 and R8.

The estimate is roughly one and a half to two M69s: four PRs, each independently reviewed (feature work goes through
a reviewed PR). P0 and P1 are useful on their own, even if P2 slips: they give better PDFs, fix the dark-picture
defect, and carry the E15 corrections.

The largest risks to the estimate are font subsetting, the three-stage assembler, and the recording `Graphics2D`
(§16).

## 15. Alternatives considered

- **An embedded browser** (JavaFX `WebView`, JCEF). It adds 40–150 MB and a JavaScript engine to a tool whose content
  is foreign text, and it reopens the HTML-rendering risk SF-2 closed as the *main* rendering path. Rejected.
- **Swing `JEditorPane` HTML**, as `HelpPanel` uses for our own HTML. It is HTML 3.2, with no reliable pagination and
  the same injection door. Rejected for documents.
- **A PDF library** (PDFBox, OpenPDF), rendering the PDF in the window. That would make the PDF the source of truth
  and the window a PDF viewer. It is larger than all of the app's dependencies together, and PDFBox's renderer is a
  second rasteriser with its own font handling. Rejected; `PdfDoc`'s javadoc already argues the dependency case.
- **Extending `WalkReel`'s HTML to reports** *(r2 (R13))*. The reel is right for a walk sent to someone with a
  browser. As the window's engine it would need a browser, which is the first alternative again.
- **Printing the window** (`Printable` over `Java2dCanvas`). It is nearly free once P2 exists. A stretch item, not a
  goal.

## 16. Risks

- **Font subsetting bugs** could produce PDFs some readers reject. Mitigation: ids are kept, so subsetting only
  empties outlines; every test PDF is structurally validated; `qpdf --check` runs in the tools script; full embedding
  is the fallback.
- **The recording `Graphics2D`** has a wide API. Mitigation: it rests on tokenised fonts and a painter-drawn legend;
  the strict recorder pins the operation set; there is a raster fallback per figure.
- **The three-stage assembler.** The pinning must be complete, or one PDF describes two moments. Mitigation: ★12; a
  paused Follow, as `evidenceCapture` does it; off-screen copies, never live components.
- **EDT time** during assembly of a picture-heavy report. Mitigation: extraction runs off the EDT, there is one paint
  per turn, and a skeleton shows first. The per-turn budget is measured, not assumed.
- **Window focus and the spotlight.** The spotlight is on the main frame's glass pane, and the document window sits
  in front of it. Following a link must bring the lit area into view without the document window losing its place,
  and must not trip the drag-cancellation-on-owned-window rule shipped in 1.26.1. This needs a frame test and a by-eye
  check.
- **Teaching diagrams teaching something false.** Mitigation: a required `source`; no counts the reference does not
  state; `inject` only from outside; every bundled lesson reviewed against `claude.txt` and the golden path; and
  `LiveGraph`, so the strongest picture is the person's own evidence.
- **Scope creep** towards a word processor or a browser. The block set, the action set and the diagram language are
  closed; growing any of them is a spec revision.
- **Jar size**, +~25 %. Measured at P0; OD-75.1.
- **PDF size** with 2× rasters. Measured at P1; OD-75.15.

## 17. Open owner decisions

| ID | Question | Recommendation |
|---|---|---|
| OD-75.1 | Font families, and accepting ~+1.2 MB of jar | IBM Plex Sans + Plex Mono (OFL 1.1), subject to the P0 checks (§5.1) |
| OD-75.2 | Links inside an exported PDF to in-app targets | None: in-document links only, with references printed as text. A PDF that can reach back into an app is a PDF that can be made to. |
| OD-75.3 | A shown document when its binding moves | Mark STALE, name the input, and offer Refresh. This is a recorded departure from D-I3's immediate re-render (§10.2). |
| OD-75.4 | Dark paper in the window | Offer it as a toggle, default light; PDFs are always light |
| OD-75.5 | The Reports tab preview | Adopt the layout's wording in P1; replace the preview with the renderer in P4, after parity |
| OD-75.6 | The agent surface | `report {name, show: true}` as a read-only form, plus `context.document`; no new verb |
| OD-75.7 | Lesson distribution and https links | Bundled `/teaching/` plus a dropped `.lesson.md`. The pack entry kind `read` is decided by the packs' owner. https links are copy-only, with no browser hand-off. |
| OD-75.8 | Record and node links under a fingerprint mismatch | Refuse, with the announce line's wording |
| OD-75.9 | Spotlight captions in the bundled sans | Yes. Otherwise "text matches" is only colour-deep. |
| OD-75.10 | Page size | A setting, defaulting to Letter (today's), with A4 offered |
| OD-75.11 | One document window or several | One in P2; several later if asked |
| OD-75.12 | Missing glyphs | A visible code-point frame, with no system fallback (keeps G2) |
| OD-75.13 | Tagged PDF / PDF/UA | Not in M75; revisit if a recipient needs it |
| OD-75.14 | Walk handouts | P4, optional, on `WalkReel`'s rules |
| OD-75.15 | *r2 (R7).* Raster device scale | `s = 2`; drop to 1.5 if P1's measured PDF sizes are too large |
| OD-75.16 | *r2 (R12).* Redaction on a person's chooser export | On by default, with an opt-out in the chooser; agent exports always redacted |
| OD-75.17 | *r2 (R2).* A document link while a walk is showing | End the walk, with the reason stated |

## Revision history

- **r1, 2026-10-17.** The first draft, from the owner's requirements and design direction of the same day, validated
  against `main` at `e09552dd`. Findings E1–E14 came from reading the code.
- **r2, 2026-10-17.** Folds in the independent review [k7q3](../handoff/review_m75_r1_k7q3.md). All 21 findings are
  fixed and none was declined; the reviewer confirmed E1–E14, including E7.
  - **R1** (§11, E17): a chooser export is unguarded, the verb is guarded, and there is a check.
  - **R2** (§10.1, §10.3, §10.4): typed tickets, a shared lighting primitive instead of the walk performers, and a
    document link ends a showing walk (OD-75.17).
  - **R3** (§10.2): a declared binding with seven inputs; the departure from D-I3 recorded; ★11.
  - **R4** (§10.1, §10.5): `linkPolicy` in the fact, a binding and a node-composed `statusLine` in the snapshot, and a
    synchronous answer for every effect.
  - **R5** (§10.4, §12): a three-stage, single-moment assembly with Follow paused, off-screen copies, and a measured
    EDT budget; ★12.
  - **R6** (E7, §6, ★5): the theme reads widened to helpers, the legend and the margins; W-0 across four paths.
  - **R7** (§7.1, §12): a device scale instead of a larger layout, one raster in both backends, a heap budget, and a
    restated PDF size (OD-75.15).
  - **R8** (§6, §7.2): fonts in the tokens, the recorder answering metrics, and the painter-drawn legend.
  - **R9** (§3.1, §7.3, §8.3, §11): a lesson requests, never authors, evidence; provenance on every page; no `menu:`
    or `toolbar:` targets from foreign lessons; ★13.
  - **R10** (E15, §3.1, §3.2, §13.1): six existing defects corrected rather than frozen; `Action` runs for the
    authored-view offer and CSV; split callouts; no silent elision.
  - **R11** (§11, ★14): hex UTF-16BE dictionary strings, and an object-graph `PdfInertTest`.
  - **R12** (§11, ★15): `redactProse` on export (OD-75.16).
  - **R13** (E16, §3.3, §15): `WalkReel` added and its rules adopted.
  - **R14** (§10.5, ★16): `show` is a read-only form before parse.
  - **R15** (§4.1, §5.1, §5.3, §5.4): the subsetting details, the figure and coverage checks, the right-to-left
    statement corrected, ASCII frames, and raw text into layout.
  - **R16** (§9.2, §11, §13.3, ★2): the `OnlyIn` extension, the network test bound to OD-75.7, a mutation for ★2, the
    accessible-tree claims only, and the shortcut mask.
  - **R17** (E18, §8.2): `DropKind.LESSON`, and an intent rather than a direct open.
  - **R18** (§6): role backgrounds as a mix rule, and card alpha.
  - **R19** (G5, §7.3, §10.3): `inject` from outside, `service`'s own shape, the `branch` edge, the sequence layout
    named as new, and focus links as view change plus light.
  - **R20** (§10.5): `context.document` above the fresh-start return.
  - **R21** (§14): re-estimated at 15–20k lines.
