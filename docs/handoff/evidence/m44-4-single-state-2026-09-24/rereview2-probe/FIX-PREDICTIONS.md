# F1 correction — frozen before changing implementation or running its new test

The owner authorised fixing and pushing with the focused review.

1. Add one test of the production report adapter's rendered values for literal keys. With `v=100`
   and `v+1=7`, its image must equal an independently constructed one-point series at 7.
   On 85a3f598 this will fail at the literal-value assertion because the image plots 101.
2. Keep literal references as `Expr.Ref(GraphKey)` while sharing the existing series-call scope and
   resolution parser. Include punctuation, whitespace and a backtick in the test's literal keys;
   none may be parsed as expression syntax or require quoting by the caller.
3. A mutation that replaces literal-reference construction with expression parsing must fail the
   new named assertion. Restore source/classes byte-identically, then rerun green.
4. Existing STRICT/filter controls must remain caught. The valid verb-call comparison should stay
   identical; existing `expr` semantics must not change.
5. Expected headless total: 2211 / 0 / 0 / 101 (one new test); display total remains 102.
   These are predictions, not results. The old empty-plot advice and lone-dot presentation remain
   optional follow-ups, outside this correction.
