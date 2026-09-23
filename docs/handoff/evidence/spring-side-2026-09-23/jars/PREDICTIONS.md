# Frozen before implementation/trials — 2026-09-23

Scope: M67.1 and BETA-B4 share this source/build collection. No binaries published.
The remote named in D-X7 was unavailable at intake. The owner selected the feed
adapter as the pre-existing component for D-X9; its source identity remains due.
Do not substitute a new adapter and call it pre-existing.

1. Spec-derived one-day risk oracle: authentic A annualises the supplied daily
   volatility; B uses the stipulated daily horizon. Both jars are built from their
   recorded sources, with matching digests. Predict A fails every nonzero row and
   passes the zero-exposure row; B passes all rows. This shows correctness is a
   separate property from artifact identity, not a defect in a digest check.
2. Sensitivity control: replace B's multiplier with zero in a disposable source
   tree. Predict the same nonzero rows fail. Never label this mutant jar A.
3. Catalogue limit node: size equal to the per-symbol limit passes, one above it
   publishes a breach, unknown symbols refuse. Disable the comparison and predict
   the scenario assertion fails. Notifier implements an exported service; tests
   must observe the delivered message. Disable publishing and expect failure.
4. Build twice with fixed archive metadata: matching bytes. Modify a jar after
   build: manifest validation refuses its digest. Unknown catalogue version is
   refused. These checks establish packaging, not receipt integrity or generation.
5. Run direct callback tests with actual EventLogManager and recorded logger
   registration. A generated-dispatch integration is a separate check and is not
   established by these tests. No owner key, paid generation or publication.

The beta's oracle is written from SPEC.md, with a distinct Decimal implementation,
not by reading a component's output. Inputs and expected comparisons are retained.

Owner clarification before the first trial: no previous feed adapter is available;
build a new example or leave a tracked dependency. We build a new CSV adapter, label
it new, and leave the D-X9 provenance requirement open. Predict ordered input produces
ordered QuoteView events, and malformed/negative input is refused before publishing.
Mutating the size parser must fail the event-value assertion.
