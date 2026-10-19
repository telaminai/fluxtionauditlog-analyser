# Integration review F2 — the design acknowledgement was not its result

Owner: `feature/propose-starter`, PR #167; exposed by the optional design-root choice in `fix/starter-followups`.
Original code reviewed: `ddccd5c5`. Test executed before this fix on the F1-fixed tree `be4e1738`.

The actual frame sequence is hosted proposal → person's Apply → download the stub DEMO starter → combined review →
Open project Yes, design folder No. Its profile permits only the Java source directory, not the Spring design directory.
`openStarterDesign` called `openDesign` on the EDT (which returns a loading acknowledgement) and immediately reported
`StarterDesignOpened(ok=true)` merely because the file existed. The node also said the design was shown before any read.

Maintained regression: `StarterProposalFrameTest#aDeclinedDesignRootReportsTheActualReadOutcome`. It drives the same
real dialogs as the successful starter test, then reads the session audit outcome, the published explanation and the
design document. No live AI or remote download; isolated home, DEMO archive, fixture agent.

Before fix, display run **1 / 1 / 0 / 0**, named assertion:
`a refused source-root read must not be recorded as opened`; the actual audit entry was
`method: onDesignOpened, starterDesign: true, why: null`.

Fix: execute the existing blocking design adapter off the EDT and report its final result or exception as a fact.
`starterProject` publishes the pending sentence and the settled explanation, and requests any failure status effect.
The adapter no longer invents a success from file existence or composes the failure status itself.

After fix, locked display run with both headless properties false:
`StarterProposalFrameTest` **3 / 0 / 0 / 0**, `TemplateCatalogueFrameTest` **1 / 0 / 0 / 0**,
`DesignSpotlightFrameTest` **4 / 0 / 0 / 0**. The successful design-opening control still passes.
Full `mvn -o test`: **4187 / 0 / 0 / 327**. Counts are total / failures / errors / skips, JDK 21, macOS.

No handler signature, wiring or event type changed; no regeneration was required and no generated file was edited.
This does not claim to settle the separate policy for superseding a whole starter operation on a project switch.
