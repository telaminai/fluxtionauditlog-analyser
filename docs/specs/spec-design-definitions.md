# Design shows the graph's definition, whatever its form (Design Spec)

**Status:** IMPLEMENTING, 2026-10-19. **Milestone: M80** (verified free: `M72`–`M79` are all assigned —
`spec-authoring-token-lowering.md`, `spec-create-project.md`, `spec-claude-code-provider.md`,
`spec-document-renderer.md`, `spec-authoring-journey.md`, `spec-ai-setup-wizard.md`,
`spec-quiet-assistant-panel.md`, `spec-first-minute.md` — none left `M80` taken; grep `M80\b` across the
tree returns nothing). **Tracker:** [tracker.md](tracker.md).
**Builds on:** [`completed/spec-design-render.md`](completed/spec-design-render.md) (M66 — the Spring XML on the
canvas; this spec extends it, nothing in it is revised), [`spec-source-adapters.md`](spec-source-adapters.md)
(M34 — FQCN provenance, source roots), [`spec-spotlight.md`](completed/spec-spotlight.md) (the closed-vocabulary
rule this spec's one addition must keep).

## 0 · Correction up front — this spec was written against the real `origin/main`, not the stale copy

Research for this spec started against a local checkout that was many releases behind (it believed the
latest release was 1.30.1 and the highest milestone was M71). A fresh clone of `origin/main` showed the real
state: **release 1.36.0**, milestones through **M79** shipped or specified, and a new onboard-assistant
subsystem (M74 *Claude Code as the analyser's assistant*, M77 *Set up AI*, M78 *the quiet assistant panel*,
M79 *the first minute*) that didn't exist in the stale copy at all — including `CustomerApiKeysTest`
(`src/test/java/.../mcp/CustomerApiKeysTest.java`), which turned out to be exactly the pinning test this
brief named, not a guess. Every citation below is checked against that fresh clone. Two claims in the
brief that this reading does **not** confirm are called out in §4 rather than carried forward silently.

## 1 · The problem, with evidence

M66 (`spec-design-render.md`) put one artefact on the Design canvas: Spring XML, read through
`DesignFiles`/`DesignDocument`/`DesignSession` and reachable by `open {design}`, `source {file|bean|line}`
and `spotlight source:design…`. That model is XML-shaped end to end:

- `DesignDocument.parse` is a SAX indexer over `<bean id>` elements (`DesignDocument.java:10,80-82`); there
  is no equivalent for a `.java` builder.
- `DesignSession.echo()` (`session/node/DesignSession.java:55-64`) — the exact map that
  `MainFrame`'s context assembly publishes verbatim as `context.design`
  (`ui/MainFrame.java:11180`, `out.put("design", session.processor().designSession.echo())`) — only ever
  has a `file` when `open.design` or `source {file}` resolved an XML document. A project authored with a
  Java `FluxtionGraphBuilder` has **no design file to open**, so this key stays
  `{relationship: "unverified", note: "..."}` forever, even though the graph's definition sits right next
  to the generated processor.
- `source {fqn: ...}` (the one existing path into Java) requires the caller to already know the class
  name. It resolves through `DesignFiles.fqn`/`DesignFiles.resolve`
  (`design/DesignFiles.java:29-63,104-117`), and a path outside the authorised roots is refused with a
  message that **names the fix but does not do it**: `"...to authorise its parent only, call source_root
  {\"add\":[...]}, then retry open {design}. No root was added."` (`DesignFiles.java:56-57`).

That refusal message is the evidence the brief points at. An assistant that does not already know the
builder's FQCN — which is the normal case, since nothing today *tells* it — goes through exactly this
loop: guess a class name with `source {fqn}` → refused, not under an authorised root → read the refusal's
root hint → call `source_root {add: [...]}` → retry. Three calls and one filesystem-widening approval to
reach a file whose location the project already knows (it is next to the generated processor that
*depends on it*). **Today's project context (`context.design`) says nothing that would shortcut this —
not the file, not its kind, not where to look.** §8's acceptance test turns this into a measured number.

## 2 · Goals and non-goals

**Goals**
1. `context.design` names the graph's definition file(s) regardless of form (Spring XML, a Java builder,
   both) **without being asked**, the same way `context.processors` already names the generated processor
   without being asked.
2. When there is more than one definition file, Design shows a chooser; `source {file}` (already
   general-purpose) is how a caller glances at any one of them.
3. Every form gets **node anchors** — name + file + line — so `spotlight` and a topology-node → definition
   jump land on the right line, in XML or Java alike.
4. Everything is additive: no existing `context.design` key changes shape, no existing verb parameter is
   renamed, no existing spotlight target's meaning changes.

**Non-goals (v1)**
- No editing, validating, or generating a builder from the analyser. Design stays read-only, exactly as
  M66 left it (`completed/spec-design-render.md` §D-6).
- No Java AST beyond what an anchor needs — no `javac`/JavaParser/Eclipse-JDT dependency. §5 specifies a
  bounded lexical scan, not a parser.
- No detection of a "helper/config class a builder calls" as its own file role. §5.4 found no generalisable
  signal for this in the one multi-file builder example available (`fluxtion-diamond`); it is an open
  question (OD-5), not a shipped heuristic.
- No anchors for `addAuditor`/`addFrameworkAuditor` registrations — those wire framework auditors, not
  domain nodes, and are out of scope until the Project panel (M37) needs them.
- No change to how `NODE` mode (a node's own source) works. Design stays the wiring; Source stays the
  logic — the brief's own framing, and already true for XML.

## 3 · The real `FluxtionGraphBuilder` API (read from the jar, not inferred)

```
$ javap -classpath fluxtion-builder-api-1.1.2.jar com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder
public interface com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder {
  public abstract void buildGraph(com.telamin.fluxtion.builder.generation.config.EventProcessorConfig);
  public abstract void configureGeneration(com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig);
}
```

`EventProcessorConfig`'s node-registration surface (same jar, `~/.m2/repository/com/telamin/fluxtion/
fluxtion-builder-api/1.1.2/`):

```
public <T> T addNode(T);
public EventProcessorConfig addNode(java.lang.Object, java.lang.Object...);
public <T> T addNode(T, java.lang.String);
public <T> T addPublicNode(T, java.lang.String);
public <T extends Auditor> T addAuditor(T, java.lang.String);
public <T extends Auditor> T addFrameworkAuditor(T, java.lang.String);
```

Confirmed against a real, public, multi-file example — **`telaminai/fluxtion-diamond`**, cloned fresh for
this spec — which ships *both* a Java-builder graph and a Spring-XML graph of the same topology, plus
**four independent domain builders** (exactly the brief's "multiple files" case, already real):

```java
// src/main/java/com/acme/diamond/builder/DiamondBuilder.java
public class DiamondBuilder implements FluxtionGraphBuilder {
    @Override public void buildGraph(EventProcessorConfig cfg) {
        cfg.addEventAudit(LogLevel.INFO);
        A a = cfg.addNode(new A(), "a");
        B b = cfg.addNode(new B(a), "b");
        C c = cfg.addNode(new C(a), "c");
        cfg.addNode(new D(b, c), "d");
    }
    @Override public void configureGeneration(FluxtionCompilerConfig cfg) { ... }
}
```

and, at `src/main/java/com/acme/diamond/domain/{logistics,accounting,mathematics,trading}/builder/
*Builder.java`, four more `FluxtionGraphBuilder` implementors with the identical shape, each a separate
file, each with its own `addNode(new X(...), "name")` calls and its own generated processor. This is the
spec's primary grounding fixture (§8) and the shape §5 is built to parse.

**Cross-check against `docs/claude.txt`:** the brief flagged `@AfterEvent` (issue #46) as a point where
that document was wrong; this spec does not depend on `@AfterEvent` at all, so that defect does not touch
it. `FluxtionGraphBuilder`/`EventProcessorConfig`/`addNode` as described in `docs/claude.txt` match the
`javap` output above exactly — no second defect found here.

## 4 · Discovery — what the evidence actually supports (correcting the brief)

The brief's item 4 claims: *"The generated processor's header names its builder... the GraphML and
processor carry FQCNs."* Checked against three real generated files, this is **half confirmed**:

- **GraphML carries per-node FQCNs, not the builder's.** `DiamondProcessor.graphml`'s node entries each
  carry `<data key="fluxtion.class">com.acme.diamond.node.A</data>` etc. — this is the existing M34
  provenance mechanism (`fluxtion.class` per node, `spec-source-adapters.md`), already consumed for `NODE`
  mode. There is **no graph-level field naming the builder or XML file** anywhere in the GraphML; only
  `fluxtion.sourceFingerprint` and `fluxtion.toolchainVersion`.
- **The generated-processor header does not reliably name the builder.** `fluxtion-diamond`'s committed
  `DiamondProcessor.java` carries a header the repo's own `regen.sh` **writes itself** —
  `"Generated source: Fluxtion AOT compiler output for builder.DiamondBuilder (DEMO project)"` — explicitly
  to *replace* "the generator's own licence header", which `regen.sh`'s comment says it does because the
  real one is confidential. Checked against the **actual, unmodified compiler output already shipped in
  this repo** — `src/main/java/.../session/generated/SessionProcessor.java` and
  `src/main/resources/demo/.../DemoQuoteProcessor.java`, both real hosted-compiler output, neither
  regenerated for this spec — the real header is a generic copyright notice with **no builder or XML
  reference at all**:
  ```
  /*
   * This source code is protected under international copyright law.  All rights
   * reserved and protected by the copyright holders. ...
   */
  ```
  So: **header-based discovery is not confirmed to work against any generated processor available to this
  repo.** It may work on a newer or differently-templated toolchain the owner saw directly (the exact
  phrase in the brief), but nothing checkable here proves it. §5.1 treats it as a cheap, best-effort,
  **last-priority** signal — try it, use it if it parses, never block on it — and §9 OD-1 asks the owner
  to confirm which toolchain version, if any, actually emits it before any PR relies on it alone.
- **The scan fallback is therefore not a fallback — it is the primary mechanism.** This reorders the
  brief's priority (header first, scan as fallback) to: scan first, header as a free bonus check.

## 5 · Design

### 5.1 · `DesignDiscovery` — the adapter effect (rule 9: an adapter reports a fact; it decides nothing)

A new, pure, off-UI-thread read, same shape as `DesignWorkspace.read` (`design/DesignWorkspace.java`) and
`DesignFiles.discoverDiagnostics()` (`design/DesignFiles.java:127-135`, the existing "scan under the
project for named candidates" precedent — same 2 MiB and existence checks, same "never silently pick one"
rule as `open {discover: "diagnostics"}`):

```java
// design/DesignDiscovery.java — new
public record DesignDiscovery(String kind, List<FileRef> files, String source) {
    public record FileRef(String path, String role) { }         // role: "spring" | "builder"
}
```

Computed by a static `DesignDiscovery.scan(DesignFiles files)`:

1. **Enumerate** every `.java` and `.xml` file under the project's authorised source roots (reuse
   `DesignFiles.roots()`/`directory()`; the existing scan-size cap pattern from
   `completed/spec-design-render.md` D-5 — *"A scan exceeding 10,000 files is unavailable, never truncated
   into a matching hash"* — applies here too: past the cap, `source` reports `unavailable` with the reason,
   never a partial, silently-truncated list).
2. **Classify** each file by a cheap text test, not a parse:
   - `.xml` → role `"spring"` when it contains a `<bean` element whose `class` attribute ends
     `FluxtionSpringConfig` (same test `DesignDocument.configs()` already makes, run on raw text here since
     a full parse is unneeded for discovery).
   - `.java` → role `"builder"` when it contains, textually, both `implements` and `FluxtionGraphBuilder`
     on the same `class ... implements ...` line (accepting the simple name alone when an
     `import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;` line is present, or the
     FQN directly in `implements`). This is a text test deliberately weaker than a parser: a false
     positive (a file that merely mentions the words) is caught at the next step when §5.2's anchor scan
     finds no `addNode` calls and the file is demoted to "mentions `FluxtionGraphBuilder`, no nodes found"
     rather than asserted as a builder with zero nodes.
3. **Opportunistically** check the open or discovered generated processor's header (if one exists per M34)
   for a resolvable path/class reference; when it parses to a file under an authorised root, that file's
   role is confirmed/promoted and `source` is `"processor-header"` for that entry. This never blocks: if no
   header is present, parses to nothing, or names something outside the roots, discovery proceeds on the
   scan results alone and `source` is `"source-scan"`.
4. **`kind`**: `"spring"` (only spring files found), `"builder"` (only builder files), `"mixed"` (both),
   `"none"` (neither). `files` lists every file found, in path order; `source` is `"processor-header"` when
   step 3 contributed at least one entry, else `"source-scan"`, else (nothing found) `"none"`.

Fired as a fact, not a decision: `DesignEvents.DiscoveryRequested` (new, same shape as the existing
`ReadRequested`/`ResultReadCompleted` in `design/DesignEvents.java:6,10`) on project-open
(`SessionEvents.ProfileApplied`) and on a `source_root` change — the two events `DesignSession` already
listens for to `clear()` (`session/node/DesignSession.java:46-48`). The read runs off the EDT exactly like
`MainFrame.pollDesign()` (`ui/MainFrame.java:1639-1649`) and reports `DesignEvents.DiscoveryCompleted
(DesignDiscovery discovery, long generation)`. `DesignSession` gains one field (`discovery`), one handler
(apply `DiscoveryCompleted` when `generation` matches, same staleness guard every other `DesignEvents`
handler already uses), and clears it in the existing `clear()`. **No new node, no new thread policy, no
new filesystem code outside `design/` — this is the smallest unit rule 9 allows**: one adapter effect, one
fact, the existing session node holds the state, Design renders it.

### 5.2 · Node anchors for Java — a bounded lexical scan, not a parser

New sibling type to `DesignDocument` (that type is untouched — XML stays XML):

```java
// design/BuilderDocument.java — new
public record BuilderDocument(String file, String text, String revision, List<NodeAnchor> nodes) {
    public record NodeAnchor(String name, int line, boolean anchored, String kind) { }
    // kind: "addNode" | "addPublicNode" | "discovered"; name is null when !anchored
}
```

`BuilderDocument.parse(file, text)` algorithm (deterministic, no javac, bounded by file size like
`DesignFiles.MAX_BYTES`):

1. Find the `buildGraph` method body: the first `void buildGraph(` to its balanced closing `}` (brace-depth
   counter that is string/char-literal aware — the only state this scanner needs). Anchors are only found
   inside this span; this is what keeps a `configureGeneration` call to `cfg.setClassName("...")` or an
   unrelated method's text from ever being mistaken for a node.
2. Within that span, find every call site matching `\baddNode\s*\(` or `\baddPublicNode\s*\(`. For each:
   - Split the parenthesised argument list at top level (depth-aware over `(`/`)`, ignoring commas inside
     nested parens or string literals) to get the arguments.
   - **Literal name** (the common, intended case — every example in `fluxtion-diamond` and in this repo's
     own `tools/bench/latency-kit/dsl/*.java` is this shape): the last argument is a bare Java string
     literal (`"[^"\\]*(\\.[^"\\]*)*"`, no `+` concatenation at that position) → `NodeAnchor(name=<decoded
     literal>, line=<call's starting line>, anchored=true, kind=<"addNode"|"addPublicNode">)`. The call's
     line is the line of the method name token (`addNode`/`addPublicNode`), not the line of the string
     literal — the two differ on a wrapped multi-line call, and the line that matters for "where do I click
     to see this declaration" is where the call starts (§8 ★T4 tests exactly this with a wrapped call).
   - **Non-literal name** (`addNode(new X(a, b), computedName)` — a variable, a field, a method call,
     string concatenation, or the 1-argument `addNode(instance)` form that has no name argument at all):
     `NodeAnchor(name=null, line=<call line>, anchored=false, kind=...)`. **Never guess.** The UI shows
     this row as *"a node is added here; its name is not a literal in source"* with the line as the only
     anchor — honest, not silent (§8 ★T5).
3. **Discovered children** (the brief's phrase: a node reached only as another node's constructor argument,
   never itself passed to `addNode`/`addPublicNode`) — real Fluxtion behaviour: the compiler discovers a
   node transitively from object-graph fields, so `cfg.addNode(new Outer(new Inner()), "outer")` puts
   `Inner` in the graph with no call of its own. Within the same `buildGraph` span, every `new
   <SimpleName>(` occurrence that is **not** the direct first argument of an already-matched `addNode`/
   `addPublicNode` call gets `NodeAnchor(name=null, line=<the "new" token's line>, anchored=false,
   kind="discovered")`. This is a location-only anchor — there is no node name to show, by construction,
   since nothing in `buildGraph` ever names it; the UI's caption says so.

`nodes()` returns anchors in call order. `revision()` is `DesignFiles.sha256(text)`, identical function to
`DesignDocument`'s, so the existing revision/Follow-diff logic in `MainFrame.refreshDesignView`
(`ui/MainFrame.java:1665-1690`) — re-anchor on a document-revision change, go-out when a bean/node
disappears, never rebind — applies to `BuilderDocument` unchanged; it is written against `revision()` and
an id→line lookup, both of which `BuilderDocument` also provides.

### 5.3 · `kind`/role in `context.design` — additive, nested inside the existing key

`DesignSession.echo()` (`session/node/DesignSession.java:55-64`) gains four keys. Every key it already
puts stays, same order, same meaning:

```jsonc
{
  // existing, unchanged: relationship, note, file?, revision?, beans?, error?, diagnosticsError?,
  // diagnosticsFile?, inputs?

  "kind": "spring" | "builder" | "mixed" | "none",      // NEW — DesignDiscovery.kind
  "files": [ { "path": "...", "role": "spring"|"builder" } ],  // NEW — DesignDiscovery.files; omitted when empty
  "nodes": [ { "name": "a", "file": "...", "line": 20, "anchored": true } ],  // NEW — the OPEN file's anchors
  "source": "processor-header" | "source-scan" | "explicit" | "none"         // NEW — how kind/files were found
}
```

`"explicit"` covers the case this spec does not change: a file opened by name through `open {design}` or
`source {file}` needs no discovery at all; `source` reports that the question was never asked, not that
discovery drew a blank. `nodes` reflects whichever file is currently open in the session design (XML's
existing `beans` key is untouched and keeps meaning "XML bean ids of the open file" — `nodes` is the new,
form-agnostic shape; a caller reading a builder file sees `nodes`, not `beans`, which stays empty for a
non-XML file exactly as it is today for a file with no `<bean>` elements).

**Pin, following the exact precedent this repo already uses for the identical problem** —
`CustomerApiKeysTest` (`src/test/java/.../mcp/CustomerApiKeysTest.java:28-46`) already pins
`ContextSections.SECTIONS` key-for-key against the last release and lists every addition since by name in
`CONTEXT_ADDITIVE`, with the comment *"Renaming, removing or moving an existing key... fails. Adding one
fails too until it is listed here as additive."* `design`/`restoration`/`designSpotlights` stay the
section's three top-level keys — unchanged, so `CONTEXT_ADDITIVE` itself needs no new section entry — but
`CustomerApiKeysTest` has no equivalent list for `design`'s **own** (nested) keys today, unlike `hidden`,
which it already pins by name (`"hidden's own keys: count, byFilter[]..."`, same class, header comment).
This spec adds that list, same pattern:

```java
/** design's own keys, released: relationship, note, file, revision, beans, error, diagnosticsError,
  * diagnosticsFile, inputs (session/node/DesignSession.java). */
static final Set<String> DESIGN_ECHO_RELEASED = Set.of("relationship", "note", "file", "revision", "beans",
        "error", "diagnosticsError", "diagnosticsFile", "inputs");
/** M80: discovery across any definition form. */
static final Set<String> DESIGN_ECHO_ADDITIVE = Set.of("kind", "files", "nodes", "source");
```

plus one test method in the same style as `verbsKeepTheirReleasedParameters` (§8 ★T9).

### 5.4 · File roles — `"spring"` and `"builder"` only in v1

The brief's "a builder plus helper/config classes it calls" has no concrete example to design against:
`fluxtion-diamond`'s multi-file case is **independent builders for separate sub-graphs**, not one builder
delegating to a non-`FluxtionGraphBuilder` helper. `v1` ships the two roles it can test (§8), and OD-5
below asks the owner for a real example before a `"helper"` role is invented from guesswork.

### 5.5 · The chooser (UI)

A single-row strip at the top of the Design view (`SourcePanel`'s `designPane`,
`ui/SourcePanel.java:70,162-194`), **shown only when `context.design.files.size() > 1`** — the brief's exact
condition. Each entry is `path` (project-relative when possible) with a small role badge (`spring` /
`builder`); clicking one calls the existing `source {file: <path>}` (`VerbSchemas.java` `"source"` schema,
unchanged — no new parameter) to glance at it without disturbing the session design, exactly as `source
{file: B}` already does for a second XML file today (M66 D-1: *"`source {file: B}` does not change the
session design"*). A double-click (or an explicit "make this the session design" action) issues `open
{design: <path>}`, also unchanged.

**Zero files (`kind: "none"`):** Design states it, stated not hidden (the M66 D-5 house style): *"no Spring
XML or Java builder found under the authorised source roots"*, with the two actions already on offer
elsewhere — `source_root {add: [...]}` and `open {design: <path>}` for an explicit file the scan cannot
see (outside every root, or a form the scan does not recognise).

**One file:** no chooser row; Design opens directly in `DESIGN` mode, Java or XML, exactly like today's
single-XML case.

### 5.6 · Rendering a builder file

No new `SourcePanel.Mode` — `DESIGN` already covers "a design file, read-only, with an index and the
existing back stack" (`completed/spec-design-render.md` D-2) for *any* file `source {file}` resolves,
XML or not. The **bean-index sidebar** generalises: for an XML file it lists `<bean id>`s exactly as today;
for a Java builder file it lists `BuilderDocument.nodes()`, each row showing `name` (or the honest
*"name not literal"* / *"discovered, no name"* caption from §5.2) and jumping to `line`. Java text itself
reuses the existing Java highlighter already used for `NODE` mode — no new syntax highlighting to write.

## 6 · Verb and spotlight grammar additions — additive, minimal footprint

**No new verb parameter.** `source {file, bean: "a"}` already means "the declaration named `a` in `file`
(XML only, per the exclusive-selector table in `completed/spec-design-render.md` D-1)." This spec widens
*what `bean` can match*, not the parameter list: when `file` (or the session design) is a Java builder,
`bean` matches a `NodeAnchor.name` instead of a `<bean id>`. `VerbSchemas`'s `"source"` entry gets one
sentence added to its description; `VERB_PARAMS_RELEASED["source"]` in `CustomerApiKeysTest` is unchanged
(still `file, bean, line, fqn, method`) since no parameter name moves or is added — only confirmed by
re-running that test, not editing its list.

**One new spotlight token, additive, a synonym:** `SpotlightTarget.Family.DESIGN_BEAN`
(`ui/SpotlightTarget.java:46`) currently parses only `source:design:bean:<id>`
(`ui/SpotlightTarget.java:140-145`). The brief's own wording (`spotlight design:node:<name>`) names the
better word for a builder; XML callers keep `bean` (no deprecation, no behaviour change for any existing
caller). Add `source:design:node:<id>` as a second literal the same `switch` branch accepts, parsing to
the *same* `Family.DESIGN_BEAN` with the *same* argument semantics — `node` and `bean` are two spellings of
one family, not two families, so no new `SpotlightVocabulary` entry, only the existing one's form string
gaining the alternative: `"source:design[:bean|node:<id>]"`. This keeps the vocabulary closed-and-small
(the explicit design goal `SpotlightTarget`'s own class comment states) while giving the builder case its
natural word. `source:design:line:<n>` (`DESIGN_LINE`) is unchanged and already form-agnostic (a 1-based
line in whatever file is open).

**Impact to re-run, not rewrite:** `SpotlightTargetTest`'s vocabulary-equality assertion
(`ui/SpotlightTargetTest.java:187`, `assertEquals(SpotlightTarget.vocabulary(), SpotlightVocabulary.TEXT,
...)`) and the M66-era "manifest verb/parameter inventory" and "spotlight vocabulary equality" tests named
in `completed/spec-design-render.md` D-1's *Publication and root policy* — both must pass with the one
form-string edit above; neither test's assertions change shape.

## 7 · Rule 9 — names, for the record

- **Fact:** `DesignEvents.DiscoveryRequested()` (project opened / source root changed), `DesignEvents.
  DiscoveryCompleted(DesignDiscovery discovery, long generation)` (the scan finished). Same file,
  `design/DesignEvents.java`, same pattern as the four records already there.
- **Effect (adapter):** `DesignDiscovery.scan(DesignFiles)` — filesystem only, no Swing, no session access,
  run off the EDT by `MainFrame` exactly where `pollDesign()` already runs `DesignWorkspace.read` off-EDT
  (`ui/MainFrame.java:1639-1649`).
- **Session node:** `DesignSession` (`session/node/DesignSession.java`) — one new field (`discovery`), one
  new `@OnEventHandler` for `DiscoveryCompleted` with the same `generation`-staleness guard every other
  handler in that class already has, one more line in `clear()`. No new node class; this is the "route new
  state through a node plus an effect plus a fact" CLAUDE.md asks for, at the smallest size that satisfies
  it — the existing `DesignSession` already owns design lifecycle, and discovery is design lifecycle.
- **Render:** `MainFrame`'s existing `out.put("design", session.processor().designSession.echo())`
  (`ui/MainFrame.java:11180`) needs no change — `echo()` growing four keys is enough. `SourcePanel`'s
  `designPane` renders what `echo()`/`BuilderDocument` says; it decides nothing about what *is* the
  definition.

## 8 · Tests, each with its wrong-result witness

1. **★ Discovery from the header.** A fixture generated-processor file carrying a resolvable header
   reference to a builder under an authorised root → `kind: "builder"`, `source: "processor-header"`.
   *Witness:* delete the header-parse step and only the scan runs — if the same builder is also found by
   the scan, `source` still ends up `"source-scan"` and the test must assert the *value*, not just
   non-empty `files`, or a regression that silently stops trying the header never fails.
2. **★ Fallback scan, no header.** The real, unmodified-header case (modelled on the demo/`SessionProcessor`
   evidence in §4): a `.java` file `implements FluxtionGraphBuilder` under a root, no generated processor
   present at all → `kind: "builder"`, `source: "source-scan"`. *Witness:* remove the scan step with only
   the header-parse left in → `context.design` stays `kind: "none"` though the builder sits right there —
   this is the exact defect the brief's refused-calls evidence names; the test must fail loudly if
   discovery silently depends on a header that §4 showed is not reliably present.
3. **★ Multi-file chooser.** Point authorised roots at `fluxtion-diamond`'s `builder/` *and*
   `domain/*/builder/` (or a trimmed, `com.acme`-named two-file fixture committed to this repo, consistent
   with the hygiene rule) → `files.size() == 5`, chooser shown; narrow the roots to one builder only →
   `files.size() == 1`, chooser hidden. *Witness:* a chooser that defaults to "always show" passes the
   first case and silently fails the second — assert the UI's visibility flag, not just the file count.
4. **★ Literal anchors, including a wrapped call.** `cfg.addNode(\n    new A(),\n    "a");` (name literal,
   call wrapped across lines) → `NodeAnchor("a", line=<the addNode line>, anchored=true)`. *Witness:* a
   scanner keyed off the string literal's own line instead of the call's would anchor one line lower here
   — the test must use a wrapped call specifically, a single-line fixture cannot catch this.
5. **★ Non-literal names, said honestly.** `cfg.addNode(new A(), nameVar);` → `anchored=false`, `name ==
   null`. *Witness:* a scanner that takes "the second argument, whatever it is" as the name would produce
   a fabricated node named `"nameVar"` — assert no anchor is ever named `nameVar`, not merely that one
   named `"a"` is absent.
6. **★ Discovered children.** `cfg.addNode(new Outer(new Inner()), "outer");` → `"outer"` anchored
   `true`; a second anchor for `Inner`, `anchored=false`, `kind="discovered"`. *Witness:* a scanner that
   only inspects `addNode`'s own first argument never emits the `Inner` row at all — assert its presence,
   not just `outer`'s correctness.
7. **★ Spotlight lands on the line.** `spotlight {target: "source:design:node:a"}` and `spotlight {target:
   "source:design:bean:a"}` against the same open builder file both select `DESIGN` mode and scroll to
   `a`'s anchor line. *Witness:* implementing `node` as a *new* family instead of a synonym would pass this
   test in isolation but fail the vocabulary-equality pin (§6) — run both.
8. **★ Spring unchanged.** The full existing M66 acceptance suite (`completed/spec-design-render.md`
   Acceptance 1–9) re-run byte-for-byte against an XML-only project: `kind: "spring"`, `files` lists the one
   XML path with role `"spring"`, every pre-existing echo key (`relationship`, `beans`, …) unchanged.
   *Witness:* any diff in a pre-existing key's value (not just its presence) on an XML-only fixture is the
   regression this test exists to catch.
9. **★ `context.design` additive pin.** `CustomerApiKeysTest`-style: assert `DesignSession.echo()`'s key
   set is exactly `DESIGN_ECHO_RELEASED ∪ DESIGN_ECHO_ADDITIVE` (§5.3) on both an XML and a builder fixture.
   *Witness:* renaming `nodes` to, say, `anchors` after this ships must fail this test by name, the same
   guarantee `CustomerApiKeysTest` already gives every other customer-facing key.
10. **★ Efficiency acceptance — the stub-assistant scenario.** A project whose only authorised root
    contains one `FluxtionGraphBuilder` (no XML, no generated processor, so the header path is moot) is
    opened; a stub assistant calls `context {sections: ["design"]}` then `source {file: <the path
    context.design.files[0].path names>}` — **two calls, zero refusals.** *Witness (the "wrong result" this
    whole spec exists to prevent):* run the identical scenario against **unmodified `main`** first and
    record what actually happens — per §1, `source {fqn: <guessed>}` refused, `source_root {add}`, retry —
    and assert that sequence is strictly longer and contains a refusal; the acceptance test for the new
    behaviour is only meaningful alongside this "before" run, not on its own.

## 9 · PR phasing and open owner decisions

- **PR1 (S) — discovery + additive context, scan only.** §5.1 (scan-only, no header step yet), §5.3,
  `CustomerApiKeysTest` additions, §5.5 zero/one-file states. Ships `kind`/`files`/`source: "source-scan"|
  "none"|"explicit"`; `nodes` stays empty (no anchors yet) — a builder file opens in `DESIGN` mode showing
  the Java text with no index, same as an unparseable XML today. Tests 2, 8, 9, (10 against the "before"
  half only).
- **PR2 (M) — anchors + spotlight.** §5.2 (`BuilderDocument`, the three anchor cases), §6 (`node` synonym,
  the sidebar index generalisation in §5.6), the topology-node → definition jump for builder nodes. Tests
  4, 5, 6, 7, the remainder of 10.
- **PR3 (S) — the chooser + the header check.** §5.5's multi-file strip and role badges; §5.1 step 3
  (processor-header, gated on OD-1 below — ship only once a real example confirms the format, else drop
  this slice and keep `source` as `"source-scan"`/`"none"` permanently). Tests 1, 3.

**Open owner decisions**

- **OD-1 (blocks PR3's header step).** §4 found no generated processor in this repo — not the demo
  fixtures, not the analyser's own `SessionProcessor` — whose header names its builder; the brief's claim
  may describe a toolchain version or template not available here. Before PR3: either point at a real
  generated file that does carry this, or drop the header step and ship discovery as scan-only
  permanently (no loss — §4 already shows scan is the primary mechanism, not the fallback).
- **OD-2.** Confirm §5.4's scoping: no `"helper"` file role in v1, pending a concrete multi-file-with-a-
  non-`FluxtionGraphBuilder`-helper example.
- **OD-3.** `bean`/`node` as permanent synonyms (§6), or deprecate `bean` later once XML is no longer the
  default mental model? Recommend: permanent synonyms, no deprecation — cheaper, and XML isn't going away.
- **OD-4.** Confirm `addAuditor`/`addFrameworkAuditor` stay out of anchor scope (non-goals, §2) until the
  Project panel (M37) has a use for them.
- **OD-5.** The discovery scan's root/time budget: reuse the existing 10,000-file cap verbatim (§5.1 step
  1), or something smaller given this scan runs proactively on project-open rather than only on an explicit
  request like `open {discover: "diagnostics"}` does today?

## 10 · Owner decisions and implementation record (2026-10-19)

The owner approved OD-2–OD-5: no helper role in v1, permanent bean/node synonyms, no auditor anchors, and the
10,000-file budget, with unavailable rather than partial discovery when the cap is exceeded. OD-1 remains gated: all three implementation
PRs are source-scan only; no processor header is inspected. The scanner additionally bounds filesystem metadata
entries and per-file reads using the existing source limit. All regular files count toward the file budget.

PR1 preserves an empty Java anchor list: the text-only `BuilderDocument` shell enables Java DESIGN rendering;
PR2 adds its bounded lexical index. Scan scheduling and zero/one selection belong to DesignSession, through typed
effects performed by MainFrame. Discovery has its own generation: an unrelated explicit source read must not
retire a valid root scan. A discovered one-file presentation is a glance, not implicit adoption as session design.

The unchanged-main T10 stub actually made two calls, one refused because no file was discoverable in context;
its authorised root was already present. No longer sequence or unnecessary source-root approval is invented.

Independent review at `6c31ef53` refined the per-file policy: unreadable, undecodable and oversized individual
files are disclosed in the existing `context.design.note` and human Design note, while readable candidates remain
available. No extra context key is introduced. Source stays `source-scan` when files failed; the note discloses
that the scan did not establish absence. File/metadata cap exhaustion still clears the list and names its
specific limit; an inaccessible root or traversal remains unavailable rather than inventing coverage.
The review's small pre-merge correction caps displayed file names at ten and states `and N more` when others
were omitted; the internal failure list and its total are not truncated.

A failed first read with no loaded XML or builder omits `kind` instead of inferring Spring from a null builder.
A failed refresh of the same file retains the last good revision and its form. The human zero-file line is short
plain text. The additive-key check extracts only the brace-matched `echo()` body, not later methods.

PR2 indexes calls only inside the lexical `buildGraph` body. Only the two-argument registration overload with a
single string-literal second argument yields a named anchor; a string trailing a three-or-more-argument varargs
registration is not a name. Text blocks used as names stay unanchored. A continued line inside a text block still
counts toward the next declaration's source line. Constructor locations say `discovered`, not runtime membership.
Delimiter partners and constructor-type suffixes are indexed once. Argument scanning skips indexed nested
spans, so deeply nested registrations do not repeatedly rescan every enclosing argument list. A structural
regression pins that skip and a 5,000-registration fixture checks all locations; no machine-specific time
threshold is used as a CI gate.

The owner separately approved the exact API metadata delta: advertise the permanent node/bean spotlight synonym,
add the Java-builder selector sentence, and clarify the existing bean parameter description. No parameter or verb
is added. `M80DesignApiDeltaTest` keeps all eleven pre-M80 hashes literally and proves that undoing only those edits
restores them. Exactly the two schema maps, two tool lists and in-process manifest move; the MCP instructions,
REST manifest and four handoff contexts do not. Historical source-text, graph-restore and hosted-starter delta
checks first undo this independently pinned later metadata change, retaining their original baseline digests.

Java Follow has a separate completed-observation fact. `DesignSession` accepts or retires that fact and requests
`PresentDesignRefreshEffect`; the adapter does not predict whether the pre-read generation will still match
after acceptance. The real-frame witness accepted the new Java revision into session state but still displayed
the old text before this correction. After the node-owned effect, a named target rebinds to the moved declaration;
stale or wrong-file completions request no presentation, while an accepted read failure presents its qualification
and retains the last good revision. The processor and its GraphML are regenerated together, never hand-edited.

PR2's maintained wrong-result witnesses are:

| Boundary | Regression | Wrong result caught |
|---|---|---|
| ★T4 literal location | `aWrappedLiteralAnchorsTheCallNotTheNameLine` | Registration points one line below its call token. |
| ★T5 non-literal names | `nonLiteralAndUnnamedAndVarargRegistrationsNeverInventNames` | A computed/unnamed registration invents a node name. |
| ★T6 children | `nestedNewExpressionsHaveHonestDiscoveredLocations` | A nested constructor disappears from the discovered locations. |
| ★T7 permanent synonym | `nodeAndBeanSynonymsLightTheSameJavaDeclaration` | The node spelling fails while the bean spelling lights the declaration. |
| Approved metadata only | `undoingOnlyTheApprovedMetadataRestoresEveryPreviousHash` | An unrelated argument-description edit moves a historical API digest. |
| Text-block line accounting | `aTextBlockContinuationCannotMoveTheNextDeclarationOrCreateFakeNodes` | A continued text-block line shifts the next anchor from line 7 to 6. |
| Accepted Follow presentation | `anAcceptedJavaFollowReadRefreshesAndRebindsTheVisibleDeclaration` | The session accepts edited Java but the visible pane retains the old revision. |
| Follow supersession | `followPresentationBelongsOnlyToAcceptedReadsIncludingFailures` | A stale observation still requests a presentation. |

The three PR1 scan/XML/additive-key controls remain neighbours; its unchanged-main two-call stub is unchanged.
The exact metadata-advertisement control additionally rejects losing the advertised node spelling even while
the parser still accepts it. Input is limited to 2 MiB; this is a lexical location aid, not Java type/overload
resolution, helper-method analysis, execution evidence or a claim of graph membership.
