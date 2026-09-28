# Predictions — evidence bundle convergence review, before running anything

Written 2026-09-28 against `e0e67b6d..b50802d3` (3,457 insertions across 45 files), after reading only the
commit subjects, the diffstat and the review brief. Nothing built or run. Scored at the end.

I wrote the convergence proposal this implements, and I reviewed v1. Both are reasons to be harder on it, not
softer: I am predisposed to like a design that took my advice, and the previous round had four of ten
predictions wrong in the direction of the work being better than I expected.

| # | prediction | why |
|---|---|---|
| P1 | **`BundleWriter.delete` removing every `.capture-*` folder beside the output can destroy something it did not create** — a concurrent capture's folder at minimum. | A glob-and-delete keyed on a prefix rather than on the ticket that owns it is the classic shape. Deleting by pattern is how cleanup code eats a neighbour. |
| P2 | **Re-basing misses at least one record-index-carrying reference.** Most likely a spotlight target family other than `records:row:N`, or a report's `filter.from/to`. | `rebase` has to enumerate every place an index or a time hides. M69 already showed the target families are more varied than they look, and the brief lists six candidate sites — a list that long is rarely fully covered first time. |
| P3 | **`onePlainFile` computed in the frame is a decision, not an observation**, and so is `startCapture` posting `BundleWriteFailed` for an empty excerpt range. | Rule 9 says the node decides. "Is this one plain file?" and "is this range empty?" are both predicates over facts, which is what a decision is. The brief asking the question suggests the author already suspects it. |
| P4 | **The moved-generation provocation is a shortcut.** Provoking a CLOSE in the same event-thread task is not the race the rule exists for. | It is the exact trap I named in the v1 review, and the brief quotes it back. A test that can only reach the path by running on the thread the real code must not run on is testing its own scaffolding. |
| P5 | **The excerpt's "contiguous run" rule is wrong for a log that is not time-ordered**, and the analyser already knows such logs exist — it has a time-order report and counts violations. | First-at-or-after to last-at-or-before assumes monotonic time. The product's own `TimeOrderReport` exists because that assumption fails. |
| P6 | **`ProjectSession.flush()` inside an effect is unsafe in at least one path**, most likely because `preSave` syncs open charts while a dispatch is in progress. | I recommended the flush. Re-entrancy into UI state from inside an effect is exactly where that advice could be wrong, and I should be the one to check it. |
| P7 | **Something still references the deleted skills, `--pack` or `--bundle-profile`.** | 45 files, two deletions and a docs-site rewrite. Dangling references survive deletions in every codebase. |
| P8 | **`notes` being packed verbatim while profile prose is redacted is undocumented**, so a sender leaks a path in notes believing the bundle redacts paths. | The inconsistency is defensible; the silence about it is not. F2's whole point was that a stated guarantee must match behaviour. |
| P9 | **At least one number in the commit messages, spec §13 or the tracker will not reproduce** — most likely a control count or the driver's 43. | Not dishonesty: counts drift between the run that produced them and the commit that states them. |
| P10 | **The three declarations above the `cv-` block include at least one that is not a true equivalent mutant** but an untested behaviour dressed as one. | Declaring an equivalent mutant is the honest move I recommended, which makes it the easiest place to hide a control nobody could make bite. |

**What would show my priors wrong:** if `delete` is keyed on the capture's own ticket, `rebase` enumerates the
families exhaustively with a test per site, and the generation provocation runs off the event thread, then the
work is better than the brief's own hedging suggests and P1/P2/P4 are simply wrong.
