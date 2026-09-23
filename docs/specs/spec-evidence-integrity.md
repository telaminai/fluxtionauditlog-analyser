# Spec — evidence integrity: the instrument never says more than it established

**Status:** PROPOSED 2026-09-24 · **Milestone:** M68 · **Tracker:** [tracker.md](tracker.md) ▸ M68.
**Sibling of** [`spec-tool-agreement.md`](spec-tool-agreement.md): that spec says tools describing one application
must not contradict each other; this one says a single tool must not state a verdict it has not established.
**Distinct from** [the Mongoose audit format proposal](../proposals/mongoose-audit-format/README.md), which is about
how a producer writes and delivers its audit file. They meet at exactly one point, D-E9.

## Why now

Three releases have carried evidence work, and on 2026-09-24 a held-out client was still told something untrue.
In the recovery packet the coverage response calls a node absent from the topology and suggests a different build,
while the preserved GraphML declares that node and the window status agrees all three logged nodes are declared.
The subject reported what the instrument told it. That is the exact failure this product exists to prevent, met by
the first outsider to look, and it is now holding a release gate rather than a review paragraph.

The same packet records a chart that says there is no data under the current filter while the series response
yields a point, and a requested topology illustration that is simply absent from an export that reported success.

These are not new findings. They have been open as DX-02 to DX-05, WS-1 and WS-2 since 2026-09-19 and have been
first in the delivery order for five releases.

## D-E1 · The governing rule

**Every verdict names what established it, and anything not established says so rather than defaulting to a
confident answer.** A verdict with no evidence behind it is reported as unknown, never as a negative. This is the
same rule the stream-end marker already applies to completeness, where a file that makes no claim reads as unknown
and never as complete; D-E1 generalises it from the log's shape to every statement the instrument makes.

Two consequences that decide most of the cases below. Absence of a record is not absence of execution, and absence
of a declaration is not absence of the thing declared. The denominator must come from the complete declared set,
never from the subset that happens to be in hand.

## D-E2 · One verdict, every surface

A verdict reads identically in `context`, the human panel, the status bar and an exported report. Where a surface
cannot show the whole verdict it shows the part it can and names what it omitted. Divergence between surfaces is a
defect of the same severity as a wrong verdict, because a reader who sees two of them cannot tell which to believe.

## D-E3 · A request is honoured whole or refused whole

A call that carries several parameters either applies all of them or refuses, naming what it would have ignored.
No reply reports success while discarding part of what it was asked. This is already the canvas-write rule; it
becomes general.

## D-E4 · A declared parameter acts or refuses

A parameter published in the manifest never silently does nothing. Where state is required before it can act, it
either establishes that state or refuses and says what is missing. The echo and the visible result always agree.

## D-E5 · Names and addresses share one grammar

Anything the product lets a user or an assistant name must be addressable by every surface that can point at it.
Where a grammar cannot express a name, creation refuses at the point of naming rather than producing an object
that later cannot be referenced. Already-saved names stay reachable; a compatibility path is chosen explicitly and
recorded.

## D-E6 · An input that changed underneath is announced, not served stale

Identity, not path, decides whether the thing open is the thing on disk. When identity changes the reader is told
before it is served anything, and no view continues to present superseded content as current. Detection already
exists in `context`; what is missing is the action.

## D-E7 · A pointer that cannot resolve says why

The root a project resolves against is recorded rather than inferred, and a pointer that fails reports the root it
tried. A panel of unresolved pointers must be diagnosable from what is on screen.

## D-E8 · Rendered evidence is checked, not assumed

An export reporting success establishes that a file was produced, not that its contents are right. Every requested
section either renders or carries an explicit statement of why it could not, and a rendered artefact whose content
contradicts the data it was built from is a failure of the export, not a cosmetic issue. Acceptance for this
decision is by inspection of the artefact, not by exit status.

## D-E9 · A producer learns its output is unreadable when it writes it

**This is the seam with the Mongoose audit format work and is owned jointly.** In the recovery packet an
application wrote nine record blocks and no separators; the analyser correctly read one record and the client
correctly reported that rather than inventing a chart. Both halves behaved well and the evidence was still
useless, because nothing told the writer at the moment of writing. The analyser's part is to state the framing
verdict prominently wherever such a file is opened; the producer's part belongs to the audit format work. Neither
half alone closes it.

## Acceptance

1. A graph and log that agree produce no mismatch warning on any surface, with membership compared against every
   declared node and the coverage denominator unchanged. The recovery packet's own artefacts are the fixture.
2. Genuine mismatches still warn, naming the ids and the artefacts compared.
3. A combined request naming a rolled set and a graph either loads both, with pairing reported, or refuses naming
   what it would have dropped. Tested on load completion, not on the first asynchronous echo.
4. From a freshly loaded log with nothing selected, the topology record parameter selects the named record or
   refuses with what is missing. Echo and visible record agree.
5. A chart name that no spotlight grammar can address is refused at creation or rename, with existing saved names
   still reachable and the compatibility choice recorded.
6. Replacing an open log file with a new file of the same path, while following, produces an announcement before
   any further content is served, and never presents the superseded content as current.
7. A project whose settings live below its root resolves every shipped pointer, and a pointer that fails names the
   root that was tried.
8. Every requested report section renders or states why not; a chart that reports no data is contradicted by no
   successful series response over the same inputs; the artefact is inspected, not inferred from exit status.
9. A file with record blocks and no separators is reported with the same prominence to a person and to an
   assistant, at open and under follow, and the producer-side half is tracked against the audit format work.

## Slices

- **M68.1** the coverage and pairing verdict (acceptance 1, 2) — first, because it is what a held-out client was
  told and what blocks G14.
- **M68.2** report and chart rendering (acceptance 8) — second, same reason.
- **M68.3** the framing verdict's analyser half (acceptance 9), with the producer half filed against the audit
  format work.
- **M68.4** whole-or-refused requests and the declared parameter (acceptance 3, 4).
- **M68.5** identity change under follow, and the project root (acceptance 6, 7).
- **M68.6** the naming grammar (acceptance 5), last because it needs a compatibility decision.

## What this does not cover

The producer's encoding, persistence and delivery, which are the audit format proposal's. Agreement between
separate tools, which is the tool agreement spec's. New rendering primitives, new verbs, or anything that adds a
surface rather than making an existing one honest.

## Open for the owner

- **Q1** whether a genuine mismatch warning should block an operation or only annotate it.
- **Q2** the compatibility choice for names that no grammar can address: refuse at creation, or accept and map.
- **Q3** whether D-E8's inspection requirement becomes a standing release gate or applies only to this milestone.
