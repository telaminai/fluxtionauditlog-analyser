# M75 — The document renderer: one layout, a PDF and a window

**Status:** DRAFT r1, 2026-10-17 — written against `main` at `e09552dd` (1.35.1). Not yet reviewed.

**Owner requirements (2026-10-17).** A pop-up window that is a true renderer of professional quality, so diagrams and
text match the spotlights; used for the reports the analyser generates (see a rendered report without downloading and
opening the PDF) and for teaching content (the Fluxtion philosophy prompt packs need diagrams of events, triggers,
dispatch, the diamond and DI wiring); the twin of a future *intent ledger*, for which this spec leaves a seam and
nothing more.

**Builds on:** [investigation reports](completed/spec-investigation-reports.md) (D-I1…D-I8),
[spotlight](completed/spec-spotlight.md) (D-SP1…D-SP4), [spotlight walks](completed/spec-spotlight-walks.md),
[the session processor](spec-session-processor.md) (CLAUDE.md rule 9),
[the trust structure](spec-trust-structure.md) (D-T4), and
[evidence bundle packaging](completed/spec-evidence-bundle-packaging.md) (reports travel to other people).
Unmerged inputs, read but not linked because they are not on `main`: SF-2 (`UiText`, `ForeignTextIsPlainTest`) on
branch `fix/sf1-sf2-copy-prompt-html`; the philosophy packs under `docs/proposals/packs/` on branch
`docs/m74-virgin-onboarding-pack`.

---

## 1. What exists today, read from the code

The owner's design direction was "one layout, two backends". It is right, but the code is further from it than the
phrase suggests, and every claim below was checked by reading the named file.

| # | Fact | Where | Consequence for M75 |
|---|---|---|---|
| E1 | There are **two** PDF layouts, not one: `FindingReport` (the single-record `report {path, recordIndex}` sugar) and `ReportRenderer` (an investigation report). They share a palette by copy, not by reference. | `report/FindingReport.java`, `report/ReportRenderer.java` | Both port onto the one layout, or "on screen equals PDF" is true of one document type only. |
| E2 | There is a **third** rendering of a report: the Reports tab draws it again from Swing components (`JTextArea`, `JTable`) with its own `Theme` record and colours. M68.1 already found the two disagreeing ("one report, two verdicts"). | `ui/ReportsPanel.java` | The window must not become a fourth. §9.4 and OD-75.5. |
| E3 | Layout is **immediate-mode and interleaved with PDF emission**: `ReportRenderer` measures, paginates and calls `PdfDoc.text/fillRect` in one pass through a `Cursor`. There is no intermediate model to hand a second backend. | `ReportRenderer.section/callout/table/mono` | The layout pass must be lifted out into a pure function that produces a positioned display list (§4). This is most of the work. |
| E4 | Text widths are **guesses**: `Face.width = length × widthFactor × size`, a mean glyph width for Helvetica. Exact only for Courier. | `PdfDoc.Face` | Java2D would measure differently, so lines would break in different places on screen. Equality requires one metrics source, read from the font file itself (§5.2). |
| E5 | Only the standard-14 fonts: nothing embedded, WinAnsi only. Em dashes, curly quotes and arrows are **transliterated**; anything else above U+00FF prints `?`. So a finding is *not* byte-identical on the page (D-I1) whenever it holds such a character. | `PdfDoc.escape` | Embedded Unicode fonts fix this; the fallback for a glyph the font lacks must be visible, never `?` (§5.4). |
| E6 | Pictures are **rasters**: topology and chart painters are Swing components painted into a `BufferedImage` (1200×800, 1200×600), embedded under Flate. At 520 pt wide that is ~166 dpi — fine on paper, soft when zoomed on screen. | `TopologyPanel.paintOffscreen`, `ChartPanel.toImage`, `GraphPanel.renderForReport`, `ReportSeriesPicture` | Phase 1 keeps rasters (rendered at 2× for the window); vector capture is Phase 3 (§7.2). |
| E7 | The painters read the theme **globally**: `TopologyCanvas.paintComponent` and `ChartPanel.paintComponent` call `ThemeManager.isDark()`. An off-screen report picture taken while the app is in a dark theme is therefore painted dark, inside a document whose own javadoc says it is "deliberately always light". By reading; needs a witness (W-0). | `ui/TopologyCanvas.java:699`, `ui/ChartPanel.java:652` | A pre-existing defect M75 must fix first: painters take their palette as a parameter (§6). |
| E8 | Content is **assembled in `MainFrame`** (`renderReportPdf`, the `report {path, recordIndex}` branch): the frame reads records, paints pictures, assembles tables and calls the renderer inline. | `ui/MainFrame.java` ≈ l.1950–2200 | Under rule 9 this becomes an effect performer (`AssembleDocumentEffect`) whose result is a fact; the decision of *when* lives in a node (§10). |
| E9 | The spotlight's colours and font are **hard-coded in the overlay** (`#1B1F24`/`#FFFFFF` card, `#3D444D`/`#C2CAD3` border, `UiTheme.accent()`, a 22 px badge, the component's own UI font at 13 pt). The UI font is the platform's, not a bundled one. | `ui/SpotlightOverlay.java` | "Text matches the spotlights" needs the tokens extracted and *both* surfaces to use the bundled face (OD-75.9). |
| E10 | The spotlight's caption is **testimony**, tagged `assistant`, and transient (D-SP2, D-SP4). | `SpotlightOverlay.TAG` | A document's numbered markers share the *look*, never the *tag*: a report's words are the report author's, a lesson's are the lesson's (§8.2). |
| E11 | A **walk is not a report section.** `ReportSpec.Kind` is `FINDING, RECORD, CHART, TOPOLOGY, SERIES, TABLE, NARRATIVE`. Walks are `walk/WalkSpec`, played by the `walkPlayback` node. | `report/ReportSpec.java`, `walk/WalkSpec.java` | Walks fit the model only as a separate *handout* document, and only partly (§3.3). |
| E12 | `topology/LayeredLayout` is pure and deterministic (declaration order, never a hash). | `topology/LayeredLayout.java` | Teaching diagrams reuse it rather than growing a second graph layout (§7.3). |
| E13 | SF-2's `UiText` is **not on `main`** yet (branch `fix/sf1-sf2-copy-prompt-html`). `HelpPanel` renders the analyser's own bundled HTML through `JEditorPane`. | `ui/HelpPanel.java` | M75 depends on SF-2 merging; the window has no `JEditorPane` at all, so it does not inherit Swing's HTML door (§11). |
| E14 | The page is **US Letter, fixed** (`PdfDoc.PAGE_W/H`). | `PdfDoc` | Page size becomes a layout input (OD-75.10). |

## 2. Goals and non-goals

**Goals.**

- **G1 — See a report without the PDF.** Any saved report, and the single-record finding export, opens in a document
  window from the Reports tab, from the Project panel's report row, and after `report {path}` writes a PDF.
- **G2 — On screen equals PDF, by construction.** One layout pass produces one positioned display list; the PDF
  writer and the window are two consumers of it. Line breaks, page breaks, positions and colours are identical; only
  rasterisation differs (that is the viewer's business).
- **G3 — Professional typography.** One bundled proportional family and one bundled monospace family, embedded in
  every PDF, measured from the font files themselves; real tabular figures; Unicode punctuation as written.
- **G4 — Teaching documents with diagrams.** A small, plain-text document format with a box-and-arrow diagram block,
  rendered by the same layout and the same backends, for the philosophy packs' concepts: event → handler → trigger,
  dispatch order, the diamond, constructor wiring and `@ServiceRegistered` injection.
- **G5 — Live links.** Clicking a record, node, chart or focus reference in a document lights it on the main canvas
  with a numbered spotlight, through the session processor.
- **G6 — One visual language.** Numbered markers, callout cards, colours and captions come from one set of style
  tokens shared with the spotlight overlay, in light and dark.
- **G7 — A seam for the intent ledger.** A new document *source* plugs in without touching layout, backends or
  window (§3.4).

**Non-goals.**

- No HTML, CSS, SVG, JavaScript or embedded browser (JavaFX `WebView`, JCEF or similar). See §11 and §15 for why.
- No editing in the window. Reports are authored as today (verbs, menus); the window is a view.
- No network access of any kind, from the renderer, the window or the PDF it writes.
- No complex-script shaping, bidirectional reordering, hyphenation or kerning. Text is drawn in logical order, left to
  right (§5.4 says what that costs).
- No tagged PDF or PDF/A in M75 (OD-75.13).
- Not a Markdown engine for arbitrary files, not a replacement for the docs site, and not the Help page (that may
  migrate later; §14 P4).
- Not the intent ledger.

## 3. The document model

### 3.1 Blocks

A `Document` is pure data (package `doc`, no Swing, no AWT beyond `java.awt.Color` and `BufferedImage`):

```
Document(kind, identity, title, furniture, blocks)
  kind       REPORT | FINDING | TEACHING | WALK_HANDOUT   (closed enum; a ledger adds a value by spec revision)
  identity   source key + revision + content digest       (what the session node names, §10)
  furniture  header band (eyebrow, title), meta rows, footer label — repeated page furniture
  blocks     ordered list of:
    Heading(level 1..3, text)
    Paragraph(runs)                       runs: Text(style ∈ body|strong|emph|code) · Link(text, target) · Symbol(id)
    Callout(role, label, runs)            role ∈ NARRATIVE | WARN | PROBLEM | FIX | NOTE | LOG_FINDINGS
                                                 | NOT_RENDERED | DID_NOT_RESOLVE | LESSON
    Meta(rows: label, value)
    Mono(heading, lines, lineLinks?)      evidence as log lines; a line may carry a link (a node line → node:<id>)
    Table(columns, rows, highlighted[], rowWhen, rowWhenLabel, scalarLine, emptyReason, notes, rowLinks?)
    Figure(heading, caption, picture, markers)
         picture ∈ Raster(image, pxPerPt) | Vector(displayList) | Diagram(diagramSpec)
         markers: numbered anchors over the picture, each (n, x%, y%, label) — the spotlight's numbered look
    ListBlock(ordered, items of runs)
    Code(lines)                            monospace, never interpreted
    Rule / PageBreak / KeepWithNext        layout hints, never content
```

Every block carries an **authority** — the trust label that decides its styling and its standing label:

| Authority | Means | Examples |
|---|---|---|
| `EVIDENCE` | derived from the log, the graph or a stored call, re-rendered live (D-I3) | record lines, tables, chart and topology pictures |
| `TESTIMONY` | someone's words, attributed | report notes and narrative (D-I2), flag notes as written (D-I1), a lesson's prose, a walk caption |
| `APP` | the analyser's own statement about the evidence | fingerprint/view/unresolved announce lines, NOT RENDERED, LOG FINDINGS, WARNING |

The authority is not decoration: `TESTIMONY` always renders with a standing label (`NARRATIVE — the author's account,
not log evidence` for reports, unchanged; `LESSON — explanation, not this run's evidence` for teaching), so D-I2
holds on every page of every document type, and the ledger seam (§3.4) is already there.

### 3.2 Do today's reports fit? Section by section

| Today (`ReportRenderer`/`FindingReport`) | M75 blocks | Fits? |
|---|---|---|
| header band "INVESTIGATION" + title | furniture | yes |
| meta strip (WRITTEN AGAINST, AUTHORED VIEW, LOG, CREATED, RENDERED) | `Meta` | yes |
| announce lines: fingerprint, VIEW, UNRESOLVED REFERENCES, LOG FINDINGS, notes | `Callout` (APP, or TESTIMONY for notes), in today's order, before any section | yes — order is a test (D-I3a acceptance 9) |
| FINDING: note + fix + record lines | `Callout PROBLEM` + `Callout FIX` + `Mono` | yes; the strings pass through untouched (D-I1) and are now drawn as written, not transliterated (E5) |
| RECORD | optional `Figure` + `Mono` | yes |
| CHART + markers table (M32.7) | `Figure(Raster)` + `Table` | yes |
| TOPOLOGY focus, or "not defined" | `Figure(Raster)` or `Callout NOT_RENDERED` | yes |
| SERIES | `Figure(Raster)` or `Callout NOT_RENDERED` | yes |
| TABLE from aggregate / series / coverage (M33.7), with notes (M68.1), highlight rule printed (D-I8), header repeated per page, empty reason, scalar line, caps named (D-M3) | `Table` (+ notes, `rowLinks` from the record column) | yes — every field of `ReportRenderer.TableData` has a home |
| NARRATIVE | `Callout NARRATIVE` (TESTIMONY) | yes |
| DID NOT RESOLVE (D-I3), WARNING per section | `Callout` (APP) | yes |
| footer label + "n / m" | furniture, second pass | yes — the layout knows the page count before any backend runs, so the two-pass `selectPage` trick disappears |
| `FindingReport`: event summary strip, raw event lines, node log, "The cycle", "Where it sits", trend with dashed record marker | `Meta` + `Mono` ×2 + `Figure` ×≤3 | yes |
| **Walks** | not a report section (E11) | see §3.3 |

Nothing in today's reports needs a block the model lacks. The two things that do not carry over unchanged are
deliberate: transliteration (E5) goes, and the Letter page size becomes a parameter.

### 3.3 Walks: a handout, partly

A walk is a sequence of steps, each restoring a view and lighting up to six targets with captions. A *handout*
(`kind = WALK_HANDOUT`) maps naturally: per step a `Heading`, the step sentence as TESTIMONY, and a `Figure` with
numbered markers at the targets. But a step's view can be rendered off-screen only for the topology and chart
targets; `records:row:N`, `detail:node:X`, `source:…`, `menu:…` and `tab:…` targets have no off-screen painter, and
restoring the live view to screenshot it would move the person's canvas (the trap `TopologyPanel.paintOffscreen`'s
comment already names). So a handout states, per step, which targets it could draw and lists the rest as text. It is
P4 and optional (OD-75.14); the model needs nothing new for it.

### 3.4 Sources, and the ledger seam

```
interface DocumentSource {
    DocumentKey key();                         // e.g. report:<name>, finding:<recordIndex>, teaching:<id>, walk:<name>
    long revision();                           // bumps when the definition changes
    Binding binding();                         // what the document is ABOUT: log generation + graph revision, or NONE
    AssemblyPlan plan();                       // which captures the adapter must make (pictures, tables), declared
}
```

The session node (§10) knows only `DocumentKey`, revision and binding. An intent ledger would be one more source
(stated intent = TESTIMONY, evidence check = EVIDENCE, verdict = APP) and, if it needs a verdict block, one more block
type added by a revision of this spec. Nothing else changes. That is the whole seam.

## 4. One layout, two backends

### 4.1 The pass

```
Document ──layout(document, PageGeometry, FontSet, StyleTokens)──▶ LaidOut
LaidOut = pages[ Page(size, ops[]) ], links[], textSpans[], outline[], diagnostics[]
```

`layout` is a pure function: no Swing, no I/O, no clock (the RENDERED time is a field of the document). It owns every
decision `ReportRenderer` makes today — wrap, keep-together, table header repetition, picture fitting, the `ensure`
thresholds — and nothing else does. Its output is deterministic: the same inputs give the same `LaidOut`, which is
what makes the golden tests in §13 possible.

### 4.2 The page canvas

The display list is a small closed set of ops, and the "page-canvas interface" is the visitor over it:

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

- **`PdfCanvas`** — `PdfDoc` grown: Type0/CIDFontType2 embedded fonts with `/W` widths taken from the same font
  tables layout used, a `ToUnicode` CMap so copy and search work in any PDF reader, paths and dashes, `ExtGState`
  for alpha. The coordinate flip stays inside it, as today.
- **`Java2dCanvas`** — draws each op onto a `Graphics2D` under the window's zoom transform, with
  `FRACTIONALMETRICS_ON`, hinting off, and glyphs placed by `GlyphVector.setGlyphPosition` from the layout's own
  advances — never `drawString`, which would re-measure and re-break.

Links, text spans (for find, selection and copy) and the outline are *not* drawing ops: they are side tables of the
`LaidOut`, in page coordinates, which the window hit-tests and the PDF writer uses for in-document destinations.

**Why equality holds.** Both backends receive identical glyph ids at identical positions with identical advances,
from identical font bytes. Neither measures text. A PDF viewer advances by `/W`, which is the font's own `hmtx`; the
window places each glyph explicitly. What can still differ is pixels (hinting, anti-aliasing, a viewer's own
smoothing), which is why the equivalence tests compare positions, not images (§13.2).

## 5. Fonts

### 5.1 Choice and licence

Recommended: **IBM Plex Sans** (Regular, Italic, SemiBold) and **IBM Plex Mono** (Regular, SemiBold), all under the
**SIL Open Font License 1.1**. One designer family, so sans and mono share proportions; tabular figures in the sans;
coverage of Latin, Latin Extended, Greek and Cyrillic. Alternatives with the same licence: Inter + JetBrains Mono;
Source Sans 3 + Source Code Pro (OD-75.1).

What the OFL requires and how M75 meets it:

- **Bundling with software** is permitted; the licence text must travel with the fonts: `OFL.txt` beside the TTFs
  under `src/main/resources/fonts/`, and listed in the jar's third-party notices.
- **Embedding in documents** is permitted. Subsetting inside a PDF is a document-embedding act, not distribution of a
  modified font, in the OFL FAQ's reading — **to be confirmed against the FAQ text at implementation** and recorded
  in the PR, in the spirit of rule 6. The TTFs in the jar are shipped byte-for-byte unmodified, pinned by SHA-256 in a
  test, so no Reserved Font Name question arises for them.
- **Selling the fonts alone** is forbidden; we do not.

Cost: about 1.0–1.3 MB on a ~5.0 MB jar (measure at P0 and record it; OD-75.1).

### 5.2 Metrics

A small TrueType reader (`doc.font.TrueTypeFont`: `head`, `hhea`, `hmtx`, `cmap` formats 4 and 12, `OS/2`, `post`,
and `glyf`/`loca` for subsetting) gives advances in font units. Layout uses only these. The window builds its
`java.awt.Font` from the same bytes (`Font.createFont(TRUETYPE_FONT, …)`), so glyph ids agree. No AWT font metrics
are consulted anywhere in layout; a source-text test enforces it (§13.3).

### 5.3 Embedding

Fonts are subset per PDF (only glyphs used, `glyf`/`loca`/`hmtx` rewritten, a six-letter subset tag) to keep a report
small; a test validates every produced PDF with a structural parser (§13.2), and a `tools/` script runs `qpdf --check`
and a rasteriser where available (optional, not a gate). If subsetting proves fragile, full embedding is the fallback
(about +600 KB per PDF): correctness first, size second.

### 5.4 Characters the fonts cannot draw, and characters that lie

- A character with no glyph renders as a **visible box with its code point** (`⟨U+4E2D⟩`-style, in the mono face),
  never `?` and never a silent system-font fallback (that would break G2). Copy and search return the original
  character. The document's last page carries a line counting them ("12 characters this font cannot draw").
- **Format and control characters are made visible**, not obeyed: bidi controls (U+202A–U+202E, U+2066–U+2069),
  zero-width characters, and C0/C1 controls render as their escapes. Text is never reordered, so a log line cannot
  use an override to make `profit` read as `tiforp`, or hide part of itself. The cost, stated in the docs: right-to-left
  scripts appear in logical order.
- Symbols the codebase uses (flag, check, arrows, the numbered badge) are drawn as **vector shapes** by layout
  (`Symbol` runs), not taken from a font, so they do not depend on coverage.

## 6. Style tokens shared with the spotlights

One immutable `StyleTokens` (pure, package `doc.style`) with a `LIGHT` and a `DARK` set, consumed by the layout, by
`SpotlightOverlay`, by `ReportsPanel` (until P4 replaces it), by the topology and chart painters, and by the diagram
painter. It holds:

- **Ink and surfaces**: ink, muted, rule, panel, paper — today's `#1F2A44 / #57606A / #D8DEE4 / #F6F8FA` for light.
- **Roles**: warn, problem, fix, narrative, lesson, highlight — each an accent and a background, the light set equal to
  today's PDF values and the dark set equal to today's `ReportsPanel.Theme`, so nothing moves on day one.
- **Accent**: `UiTheme.accent()`'s two values (`#1F6FEB` / `#6CB6FF`).
- **Callout card**: fill, border, text, corner radius, padding — today's overlay values (E9).
- **Numbered marker**: 22 px diameter (16 pt on the page), accent fill, white SemiBold numeral, and the numbering rule
  ("a number is kept for the life of its document": the spotlight's high-water rule, D-SP M64.6).
- **Type scale**: title 16, heading 11.5, body 10.5, caption 8.5, table 8.2, mono 8.2 — today's sizes — in the bundled
  faces.

**Painters take tokens as a parameter** (fixes E7): `TopologyCanvas` and `ChartPanel` gain a `StyleTokens` input that
defaults to the theme's set on screen and is `LIGHT` for every document picture. A document is light on paper; the
window offers dark paper as a palette swap of the same `LaidOut` (OD-75.4) — palette is not layout, so G2 is
unaffected.

"Text matches the spotlights" needs one more step: the overlay draws its captions in the bundled sans (OD-75.9).
Without it, colours and shapes match and the face does not.

## 7. Diagrams

### 7.1 Evidence pictures (P1–P2): rasters, honestly

Topology and chart pictures stay rasters from today's off-screen painters, now with `LIGHT` tokens and at 2× today's
pixel size for the window (2400×1600; an `ImageRef` carries `pxPerPt` so both backends place it in the same box).
They are evidence and are drawn by exactly the code that draws the canvas — which is the point.

### 7.2 Vector capture (P3)

A `RecordingGraphics2D` (a `Graphics2D` that records shapes, strokes, fills, alpha, transforms, clips and text into
display-list ops) lets the existing painters draw vector figures into a document without being rewritten. Text the
painters draw is mapped to the bundled faces via the tokens. Anything the recorder does not support (gradients,
arbitrary `Composite`s, `drawImage` of a volatile image) makes it **fall back to a raster for that figure and say so
in the diagnostics**, never draw it wrong. Painters that already draw through `paint(g)` need no other change.

### 7.3 Teaching diagrams: the description format

A fenced block in a teaching document (§8.1):

````
```diagram
title   The diamond
layout  down                         # down | right — LayeredLayout's two orientations
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

- **Node kinds** (closed): `event`, `handler`, `trigger`, `service`, `sink`, `external`, `note`. Each kind has one
  token-defined shape and colour, the same as the topology canvas's node classes where one exists.
- **Edge kinds** (closed): `dispatch` (solid, arrow), `ctor` (the constructor-reference wiring the framework topo-sorts,
  dashed), `inject` (`@ServiceRegistered`, dotted, from a `service`), `returns` (a labelled back-arrow for a handler's
  boolean), `flow` (plain).
- **Sequence form**: `layout sequence` draws lifelines for the named nodes and numbered calls in the order written —
  for "who calls my methods?" and dispatch order. Positions are always computed; there are no coordinates in the
  language, so a diagram cannot be laid out to suggest something its edges do not say.
- **Markers**: `mark n node "label"` — the spotlight's numbered look, up to six, as on the canvas.
- **Limits**: ≤ 24 nodes, ≤ 48 edges, labels ≤ 60 characters, ids `[A-Za-z][A-Za-z0-9_]{0,31}`. A breach is a
  refusal that names the line, like the pack linter.
- **`source` is required.** A teaching diagram asserts framework semantics, and rule 6 says those are read, not
  inferred. The parser refuses a diagram with no `source`; the caption prints it. The reference read for this spec
  (`claude.txt`, 2026-10-17) states topological ordering from constructor references and the boolean as the
  dirty/propagation signal, and **does not state how many times the diamond's join node runs per event** — so no
  diagram may assert a count; the packs already phrase that as "check it against the records", and a diagram does
  the same.
- **Live form**: `from loaded-graph [focus <name>]` instead of nodes and edges draws the *loaded* processor's
  topology through the topology painter (EVIDENCE, not TESTIMONY), and says "no graph is loaded" when none is. A
  lesson can then show the person's own diamond, which is better than any drawing of one.

Rendering: nodes and edges become a `ProcessorTopology`-shaped input to `LayeredLayout`; the diagram painter draws the
result onto the `PageCanvas` directly — vector from day one, in both backends.

## 8. Teaching documents

### 8.1 Format

A teaching document is UTF-8 plain text in a **closed Markdown subset** parsed by our own parser: `#`–`###` headings,
paragraphs, `-` and `1.` lists, `**strong**`, `*emph*`, `` `code` ``, fenced code, fenced `diagram`, and
`> LABEL: text` callouts (role `LESSON` or `NOTE`). Anything else — raw HTML, images, tables, footnotes, reference
links — renders **as the literal characters written**. Links are `[text](target)` where target is either an analyser
target in the spotlight vocabulary (`records:row:12`, `topology:node:handlerA`, `graph:spread`) or an `https:` URL;
URLs are shown, copyable, and never fetched (§11; OD-75.7 for whether a click may hand one to the system browser).
Limits: 256 KiB per file, 400 blocks, 24 diagrams.

### 8.2 Where they come from

- **Bundled lessons** in the jar under `/teaching/` — one per philosophy concept the packs teach (events, handlers and
  triggers; dispatch order; the diamond; constructor wiring and injection; reading a record). Each is reviewed against
  `claude.txt` and the golden path before it ships, with the source line on every diagram.
- **Dropped files**: a `.lesson.md` dropped on the start page or opened from the menu. Foreign text, rendered plain.
- How a pack entry points at a lesson is a pack-format change (an entry kind `read`), which belongs to the packs'
  owner and is OD-75.7, not decided here.

Every lesson block is TESTIMONY and carries the `LESSON` label; a `from loaded-graph` figure is EVIDENCE. Spotlights
lit from a lesson link are tagged `lesson`, not `assistant` (E10): the overlay gains a tag parameter, nothing else.

## 9. The window

### 9.1 Shape

A non-modal, resizable `JFrame` titled `Document — <title>` (title through `UiText`), one per open document (one at
a time in P2; OD-75.11), remembered size and position. It never blocks the main frame (M35.9: no modal in the load
path).

- **Toolbar**: page `n / m` field, previous / next, zoom (fit width, fit page, 50–400 %), find, Export PDF…, Refresh
  (enabled when stale), dark paper toggle (if OD-75.4 says yes).
- **Body**: continuous vertical scroll of pages on a neutral backdrop; optional page thumbnails at the left.
- **Status line**: the document's binding ("about demo.yml, generation 3") and, when stale, why — rendered from the
  snapshot, never composed in the window (rule 9).
- **Find** (⌘F / Ctrl-F): searches the `LaidOut`'s text spans, highlights every hit, steps with Enter / Shift-Enter.
- **Select and copy**: drag selects text in reading order across blocks; ⌘C copies the logical text (original
  characters, §5.4); "Copy page text" in the context menu.
- **Links**: hover cursor and an underline; a tooltip (through `UiText.tooltip`) shows the target.

### 9.2 Accessibility

- Every action has a key: PageUp/PageDown, Home/End, ⌘+/⌘−/⌘0, Tab / Shift-Tab through links with a visible focus
  ring drawn from the tokens, Enter or Space to follow, Esc to close find.
- Each page exposes an `AccessibleContext` with role `PAGE_TAB`-like text and `AccessibleText` over its spans, so a
  screen reader reads it in reading order; links are accessible hyperlinks.
- Both token sets meet WCAG 2.1 AA contrast for body text and 3:1 for markers and rules; a test computes it (§13.3).
- Zoom to 400 % without horizontal clipping of the page chrome; no animation.
- PDFs carry `/Title`, `/Lang` and extractable text; tagged PDF is OD-75.13.

### 9.3 Opening

From the Reports tab (a *View* button beside *Export PDF*), the Project panel's report row (reveal-only, D-L3: it
navigates to an existing report), the finding export, after `report {path}` (an offer in the status line, not an
auto-pop), Help ▸ Learn for lessons, and a dropped lesson file. Every route is one intent to the session node (§10).

### 9.4 The Reports tab

The tab keeps its list, its navigation buttons and the D-I3a *apply the authored view* offer. Its section preview is
the third rendering (E2). P4 replaces that preview with an embedded `Java2dCanvas` over the same `LaidOut`, after the
window has shipped and parity is shown — so one report has one rendering (OD-75.5).

## 10. Rule 9: the `documentView` node

What is open, which page, and whether a link may be followed are session state. A new node, modelled on
`walkPlayback` (ticketed, generation-bound, frozen definition):

| | |
|---|---|
| **State** | `key`, `kind`, `revision`, `ticket`, `binding` (log generation + graph revision at assembly), `phase` ∈ IDLE · ASSEMBLING · SHOWN · STALE · FAILED, `page`, `pageCount`, `reason` |
| **Intents (events)** | `DocumentOpenRequested(source)`, `DocumentPageRequested(ticket, n)`, `DocumentLinkActivated(ticket, linkId)`, `DocumentRefreshRequested(ticket)`, `DocumentCloseRequested(ticket)` |
| **Facts (events)** | `DocumentAssembled(ticket, generation, digest, pageCount, links)`, `DocumentAssemblyFailed(ticket, reason)`, `DocumentPageShown(ticket, n)` (the viewport's visible page, debounced), `DocumentDefinitionChanged(key, revision | deleted | renamed)`; and the existing log/graph generation changes from `openLog`/`openGraph` |
| **Effects** | `AssembleDocumentEffect(ticket, source, generation)`, `ShowDocumentEffect(ticket)`, `ScrollDocumentEffect(ticket, page)`, `ApplyDocumentLinkEffect(ticket, target, caption, tag)` → the same view + spotlight path walks use (`ApplyWalkViewEffect` / `LightWalkTargetsEffect`'s performer), `CloseDocumentEffect(ticket)` |
| **Snapshot** | `DocumentViewState(key, kind, title, phase, page, pageCount, stale, reason)` on `SessionSnapshot`; `context.document` |

**Decisions the node makes and nothing else does:**

- **When to assemble**: on open and on Refresh. A late `DocumentAssembled` with an old ticket or generation is
  dropped (the R8 rule from walks).
- **When it is stale**: a new log generation, a graph revision, or a definition change marks a SHOWN document STALE
  with a reason; it does not re-render under the reader's eyes, and a Follow append does not churn it (OD-75.3).
  A delete closes it and says so; a rename keeps it under the new key, as walks do.
- **Whether a link may be followed**: only for the current ticket; only while SHOWN (a STALE document's links are
  refused with "refresh first", because the record a link names may not be the record it meant); and **never into a
  log the report was not written against** — if the resolution carried a fingerprint mismatch (D-I3a), record and
  node links are refused with the announce line's own wording (OD-75.8). Chart and focus links name things by name
  and stay allowed.
- **Page**: `DocumentPageRequested` (toolbar, a link to an in-document destination, an agent) moves it; free scrolling
  reports `DocumentPageShown` as a fact, so `context.document.page` is what the person sees.

**What stays in the window**: zoom, scroll offset, find query, selection — per-viewer presentation that nothing else
reads and no decision depends on. If a later feature needs one of them (an agent asking "what is selected?"), it moves
into the node then, with its own fact.

**The adapter** (`DocumentAssembler`, `ui`): performs `AssembleDocumentEffect` — on the EDT for the painter captures,
one section per `invokeLater` turn so the UI stays responsive, then layout off-EDT through `core.Background` — and
reports `DocumentAssembled` or `DocumentAssemblyFailed`. It holds the `LaidOut` keyed by ticket; the window draws the
`LaidOut` whose ticket the snapshot names and draws nothing for any other. `MainFrame.renderReportPdf` and the finding
branch become callers of the same assembler, so the PDF path and the window path cannot drift.

**Regenerate** with `mvn -Pregen process-classes`; `OneDispatchModelTest` gains `OnlyIn` rules (§13.3).

**Agent surface** — the verb set stays at seventeen (pinned by `VerbSchemasTest`). Recommended: one parameter,
`report {name, show: true}`, posting `DocumentOpenRequested`; and `context.document` read-only. Whether to add even
that is OD-75.6. Per ONBOARDING, the commit adding `context.document` names its human surface (the window's status
line and the Project panel's report row) and its docs page (*Reports ▸ Viewing a report*).

## 11. Security

- **Foreign text is plain text, everywhere.** Report titles, notes, narrative, flag notes, chart names, node ids,
  record lines, bundle notes and lesson files are drawn as glyphs by our own layout. There is no `JEditorPane`,
  `JTextPane`, `JLabel` HTML or `HTMLEditorKit` in the `doc` package or the window; the window's few Swing labels
  (title, tooltip, status) go through SF-2's `UiText`. The lesson parser has no HTML mode; an `<img>` is eight
  characters on a page. M75 lands after SF-2 merges and extends `ForeignTextIsPlainTest` to the new window.
- **No network, by construction.** The `doc` package and the window reference no `java.net.URL` stream, `URLConnection`,
  `HttpClient`, `Desktop.browse`, `ImageIO.read(URL)` or `Toolkit.getImage(URL)`; images reach a document only as
  `BufferedImage`s painted in-process. A source-and-bytecode test enforces the list (§13.3). PDFs contain no `/URI`,
  `/Launch`, `/JavaScript`, `/SubmitForm` or `/OpenAction`; links in a PDF are in-document only (OD-75.2).
- **Size caps**, each a named refusal or a visible "N more, not shown" (D-M3's rule), never a silent cut: 2,000
  blocks; 5,000 table rows per document (existing per-table caps still apply first); 64 KiB per text block and 4 MiB
  of text per document; 500 pages; 40 figures and 64 Mpx of raster in total; 4096 px per raster side; lesson files
  256 KiB; diagrams per §7.3. Layout aborts with a refusal on exceeding the page cap rather than allocating.
- **Deceptive text** is neutralised (§5.4): no bidi reordering, controls and invisibles shown.
- **Authority is not spoofable**: the standing labels (`NARRATIVE — …`, `LESSON — …`) are drawn by layout from the
  block's authority; a narrative that *contains* the words "LOG FINDINGS" is still drawn in narrative styling under
  the narrative label. A lesson cannot produce an APP or EVIDENCE block.
- **Writes** (Export PDF…) ride `ExportGuard`, unchanged (D-I5).

## 12. Performance

Budgets, measured on the demo set and on a 30-page synthetic report, recorded at P2 (the tests assert generous
multiples; the numbers go in the PR):

- Layout of 30 pages: < 50 ms; `LaidOut` < 5 MB excluding rasters.
- Assembly is dominated by painter captures (as today); the window shows a page skeleton with "assembling…" at once
  and fills as `DocumentAssembled` lands. Each capture is one EDT turn, so no turn exceeds ~100 ms.
- Painting a page at 100 %: < 16 ms; glyph vectors cached per page per zoom; rasters scaled once per zoom step.
- Only visible pages ± 1 are painted; thumbnails are rendered lazily at low resolution.
- PDF write of 30 pages with subset fonts: < 300 ms; size within 1.5× of today's for the same report plus the fonts
  (~60–120 KB of subsets).

## 13. Tests and regression closure (rule 8)

### 13.1 Structural goldens (headless)

- `LaidOut` dumps (one line per op: page, op, face, size, position, text) for a fixed set of documents: a report with
  every section kind, a finding export, a lesson with each diagram kind, and a document at each size cap. Committed
  as goldens; a diff is a reviewable change.
- **Content parity with today**: for each section kind, the text of the new PDF equals the text of today's
  `ReportRenderer` output modulo the transliteration it removes and the page size — the port changes typography, not
  content. Removed once the old writer is deleted.

### 13.2 PDF equals screen

- A test-only PDF reader (content stream tokeniser, enough to read `Tf`, `Td`/`Tm`, `TJ`, `re`, `m/l/c`, `Do`) parses
  each produced PDF and checks every glyph run's position and font against the `LaidOut`.
- `Java2dCanvas` is run against a recording `Graphics2D` and its glyph positions checked against the same `LaidOut`.
- Both pass ⇒ PDF ≡ screen at the level of placement. Pixel comparison (pdftoppm vs the window's PNG at 2×) is an
  optional `tools/` check with a tolerance, run by hand at release, not a gate: rasterisers differ.
- Every produced PDF is structurally validated (xref offsets, object count, font programs parse).

### 13.3 Static checks

- `DocumentNetworkFreeTest` — the forbidden-API list in §11, over source and bytecode of `doc` and the window.
- `DocumentNoHtmlTest` — no Swing HTML components in `doc`/window; `ForeignTextIsPlainTest` covers the window.
- `LayoutMeasuresOnlyFontTablesTest` — no `FontMetrics`, `TextLayout` or `Font.getStringBounds` in layout.
- `FontsPinnedTest` — TTF SHA-256s pinned, `OFL.txt` present in the jar.
- `TokensContrastTest` — WCAG contrast for both token sets.
- `OneDispatchModelTest` `OnlyIn` rules: the document window is opened, scrolled and closed only in the
  `render…Document` method; a link click only posts `DocumentLinkActivated`; `renderReportPdf` assembles only
  through `DocumentAssembler`.
- `PdfInertTest` — no `/URI`, `/Launch`, `/JavaScript`, `/OpenAction`, `/SubmitForm` in any produced PDF.

### 13.4 ★ boundaries and their wrong-result witnesses

| ★ | Boundary | Static check | Witness (must fail when the boundary is broken) |
|---|---|---|---|
| ★1 | Foreign text never markup | `DocumentNoHtmlTest` | A report titled `<html><img src="http://127.0.0.1:PORT/x">`: open the window, a local listener records **no** request, the title paints as its characters. Mutate: render the title through a `JLabel` — the listener gets a hit. |
| ★2 | No network fetch | `DocumentNetworkFreeTest` | A lesson with an `https://127.0.0.1:PORT/` image-like line and link: render and click; no request. |
| ★3 | On screen equals PDF | §13.2 | Mutate `Java2dCanvas` to re-measure with `drawString`, or the PDF `/W` to Helvetica widths: the position check fails. |
| ★4 | Stale or mismatched links refused | node unit tests | Open a report, reload a different log, click a record link: refused with the reason, no spotlight, canvas unchanged. Mutate the generation check out: the spotlight lights record #N of the wrong log. |
| ★5 | Document pictures are light | `TopologyCanvas`/`ChartPanel` take tokens | **W-0** (before any port): under the Dark LAF, `renderFocusForReport` returns a light-background image. Expected to fail on `main` today (E7). |
| ★6 | Authority labels not spoofable | goldens | A narrative whose text is `LOG FINDINGS — …` renders under the narrative label and colours. |
| ★7 | Caps named, never silent | goldens at each cap | A 6,000-row table shows 5,000 rows and "1,000 more rows, not shown". |
| ★8 | Missing glyphs visible | golden | A record line with `中` renders a box with `U+4E2D`; copy returns `中`. |
| ★9 | Bidi/controls neutralised | golden | A line with U+202E draws the escape and keeps logical order. |
| ★10 | Teaching diagrams cite a source | parser test | A diagram without `source` is refused, naming the line. |

Frame tests (window, find, copy, keyboard, accessibility tree) join the registered frame suites under Xvfb; the
acceptance of G1 and G5 is a built-jar run through the action socket plus the owner's visual check, as for M73.

## 14. Phasing and size

| Phase | Delivers | Estimate (incl. tests) |
|---|---|---|
| **P0** | W-0 and the E7 fix (painters take tokens); `StyleTokens` extracted and adopted by `ReportRenderer`, `FindingReport`, `ReportsPanel` and `SpotlightOverlay` with no visual change; fonts bundled and pinned; TrueType reader. | ~1.5k lines |
| **P1** | Document model, pure layout, `LaidOut`; `PdfCanvas` (embedded subset fonts, paths, ToUnicode); `ReportRenderer` and `FindingReport` ported; content-parity and PDF-structure tests; the old immediate-mode writers deleted. PDFs change typography only. | ~3k |
| **P2** | `Java2dCanvas`; the document window (pages, zoom, find, copy, keyboard, a11y); `documentView` node, effects, facts, regen; `DocumentAssembler` replacing the frame's inline assembly; links with spotlight; `context.document`; `report {show}` if OD-75.6 says yes; docs page; CHANGELOG. | ~3k |
| **P3** | Teaching format and parser; diagram language and painter (graph and sequence forms, `from loaded-graph`); five bundled lessons reviewed against `claude.txt`; `RecordingGraphics2D` vector capture with raster fallback. | ~2.5k |
| **P4** *(optional)* | Reports tab preview on the renderer (OD-75.5); walk handouts (OD-75.14); Help page migration. | ~1–1.5k |

**Total: about 10k lines through P3**, the size of M69 spotlight walks (PR #57, ~10k insertions) and half again the
evidence-bundle first delivery (PR #63, ~6.8k). Four PRs, each independently reviewed (feature work goes through a
reviewed PR). P0 and P1 are useful on their own — better PDFs, the dark-picture defect fixed — even if P2 slips.
The largest single risks to the estimate are font subsetting and the recording `Graphics2D` (§16).

## 15. Alternatives considered

- **Embedded browser** (JavaFX `WebView`, JCEF): +40–150 MB, a JavaScript engine in a tool whose content is foreign
  text, and the HTML-rendering risk SF-2 closed reopened as the *main* rendering path. Rejected.
- **Swing `JEditorPane` HTML** (as `HelpPanel` uses for our own HTML): HTML 3.2, no reliable pagination, and the same
  injection door. Rejected for documents.
- **A PDF library** (PDFBox, OpenPDF) and render the PDF in the window: would make PDF the source of truth and the
  window a PDF viewer — larger than the whole app's dependencies, and PDFBox's renderer is a second rasteriser with
  its own font handling. Rejected; `PdfDoc`'s javadoc already argues the dependency case.
- **Print the window** (`Printable` over `Java2dCanvas`): nearly free once P2 exists; a stretch item, not a goal.

## 16. Risks

- **Font subsetting bugs** produce PDFs some readers reject. Mitigation: structural validation on every test PDF,
  `qpdf --check` in the tools script, full-embedding fallback.
- **The recording `Graphics2D`** is a wide API. Mitigation: support only what the two painters use (measured by
  running them against a strict recorder that throws on anything else), raster fallback per figure.
- **EDT time** during assembly of a picture-heavy report. Mitigation: one capture per EDT turn; a skeleton first.
- **Window focus and the spotlight**: the spotlight is on the main frame's glass pane; the document window sits in
  front of it. Following a link must bring the lit area into view without the document window losing its place, and
  must not trip the drag-cancellation-on-owned-window rule shipped in 1.26.1. Needs a frame test and a by-eye check.
- **Teaching diagrams teaching something false.** Mitigation: required `source`, no counts the reference does not
  state, review of every bundled lesson against `claude.txt` and the golden path, and `from loaded-graph` so the
  strongest picture is the person's own evidence.
- **Scope creep** towards a word processor or a browser. The block set and the diagram language are closed; growing
  either is a spec revision.
- **Jar size** +~25 %. Measured at P0; OD-75.1.

## 17. Open owner decisions

| ID | Question | Recommendation |
|---|---|---|
| OD-75.1 | Font families, and accepting ~+1.2 MB of jar | IBM Plex Sans + Plex Mono (OFL 1.1) |
| OD-75.2 | Links inside an exported PDF to in-app targets | None: in-document links only; references print as text. A PDF that can reach back into an app is a PDF that can be made to. |
| OD-75.3 | A shown document when the log changes | Mark STALE and offer Refresh; never re-render under the reader |
| OD-75.4 | Dark paper in the window | Offer it as a toggle, default light; PDFs always light |
| OD-75.5 | Reports tab preview | Replace with the renderer in P4, after parity |
| OD-75.6 | Agent surface | `report {name, show: true}` and `context.document`; no new verb |
| OD-75.7 | Lesson distribution and https links | Bundled `/teaching/` + dropped `.lesson.md`; pack entry kind `read` decided by the packs' owner; https links copy-only, no browser hand-off |
| OD-75.8 | Record/node links under a fingerprint mismatch | Refuse, with the announce line's wording |
| OD-75.9 | Spotlight captions in the bundled sans | Yes — otherwise "text matches" is only colour-deep |
| OD-75.10 | Page size | A setting, Letter default (today's), A4 offered |
| OD-75.11 | One document window or several | One in P2; several later if asked |
| OD-75.12 | Missing glyphs | Visible code-point box, no system fallback (keeps G2) |
| OD-75.13 | Tagged PDF / PDF/UA | Not in M75; revisit if a recipient needs it |
| OD-75.14 | Walk handouts | P4, optional |

## Revision history

- **r1, 2026-10-17** — first draft from the owner's requirements and design direction of the same day, validated
  against `main` at `e09552dd`; findings E1–E14 from reading the code.
