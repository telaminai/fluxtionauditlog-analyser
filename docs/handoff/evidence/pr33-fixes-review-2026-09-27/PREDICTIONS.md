# Independent PR 33 fix review predictions

Recorded before the additional review probes. The first full baseline has already run: 2487 / 0 / 1 / 120, with a source-navigation test error; that is an observation, not a prediction.

- A static link beneath the accepted exchange directory will pass ExportGuard and let an export write outside the project. Direct project-directory links should be refused; links remaining inside and a linked project root should work.
- The machine save overload with a global tier should preserve the bin; saving an empty bin should remove its previous keys. Fresh all-category exports and imports should not transfer the bin.
- A report named `true` should be restorable through the UI but the verb may interpret its name as a listing request. A restore combined with sections may restore without applying those sections.
- The actual delete and restore dialogs should work when the project is unchanged. The empty-bin dialog should state that it is empty.
- All five registered p33 controls should fail at their named assertions and restore byte-identically.

## Fix predictions (after the owner authorised fixes)

The review probes have now reproduced the nested-link escape, the `true` name collision and restore ignoring sections. Native mouse input did not activate Delete; inspecting the real frame shows the toolbar wrapping below its fixed height. Programmatic button clicks exercised the actual dialogs successfully; this is not a claim that the clipped buttons were mouse-accessible.

Before implementation:
- Canonicalising the actual read/write target (including an existing ancestor of a new path) will refuse a nested outside link, while an internal link and missing descendant directories remain usable. Returning canonical paths will avoid following a subsequently replaced exchange alias. This is not a race-free filesystem sandbox against concurrent local writers.
- Boolean true will list recoverable reports; every string, including `true`, will identify a report. The manifest must accept both types. A restore combined with another operation will refuse before mutation or output-path processing.
- A vertical report action toolbar will keep all four actions visible at the default 1200 by 800 frame. The regression will check visible rectangles, then operate real dialog buttons. A one-row FlowLayout mutation should fail the visible-actions assertion.
- Reverting canonical containment should fail the nested-link test; reverting the restore name check should fail its named frame assertion; removing the restore-only guard should fail a no-partial-effect assertion.
- The global-tier bin save, empty-bin clearing and all-category share behaviour passed direct review probes. These will become permanent headless regressions.
- Existing test totals were 2487 / 0 / 0 / 120 on the full retry. Added tests will increase both totals and display skips; exact final counts will be taken from fresh XML rather than inferred.
