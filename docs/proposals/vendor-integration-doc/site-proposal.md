# Proposal — document vendor integration on the analyser site

**Status:** proposed 2026-09-21 · **Evidence:** [the vendor integration record](README.md) ·
**Touches:** [`docs/site/composing-a-system.md`](../../site/composing-a-system.md) (published 2026-09-10) and
one new page

## Why

The site already has the right page. **Composing a system from supplier jars** explains the idea well: the
bean graph is the node graph, the composition is compiled rather than held in a runtime container, and the
trace names every node across supplier boundaries.

It was written before anyone had integrated a real third-party jar end to end. We now have that, with
predictions sealed in advance and preserved evidence. The measured record does three things the page does
not:

1. **It proves the claim.** One bean reference brought in a vendor sub-graph with its own events, ordering,
   configuration, exported service and audit trail. The vendor's arithmetic matched an independent
   calculation on 11 of 11 rows, and the host's 784 values and 33 messages were unchanged across two vendor
   upgrades.
2. **It contradicts three of the page's sentences**, each in a way a reader would act on (below).
3. **It surfaces the most dangerous trap in the whole integration path.** The page doesn't mention it, and
   it lands on exactly the reader this page is for.

A headline about evidence is measured against our own documentation too. A page that overstates what the
toolchain protects is the fastest way to lose the reader it was written to win.

## Claim audit — the published page against the measured record

| Page says | Measured | Verdict | Action |
|---|---|---|---|
| "Every selected bean becomes a graph node, named by its bean id" (line 18); "a supplier's node appears in your log under the name *you* gave it" (lines 93–95) | True when the integrator declares every supplier class as a bean, which is the page's example. **False** when the supplier ships a pre-wired sub-graph behind one root bean: the bean id `acmeRisk` was lost and the internals were named `riskEngine_13`, `varCalculator_14`… Stable names came from the **supplier** implementing `NamedNode`, not from the integrator (P8) | half true | describe both composition patterns and what each does to names |
| "mistakes in it are build failures rather than silence" (lines 58–60) | **False for the worst mistake available.** Listing a supplier class in the Spring design's `nodeBeans` made the starter write an empty class that shadows the supplier's. `validate ok · regenerate ok · preflight ok · build ok`, zero diagnostics, and the component deleted (P4, feedback #29) | false in the case that matters most | qualify the sentence, and add the warning in the box below |
| "What they do not need is … any knowledge of the graph they will be part of" (lines 111–112) | True of the graph. But the generated processor rebuilds every node with `new` from another package, so a supplier must make **every internal node public with a public constructor** matching its `final` fields. The encapsulated build was rejected (P6) | true, incomplete | add the supplier's obligations |
| "Stepping through a recorded cycle is then an account of what ran, in order" (lines 89–90) | True for declared routes. When a host event implements a supplier's interface, the event runs down a route the topology does not draw (`MarketPrice → acmeQuoteFeed`) | true, with a gap | add a caveat until TA-9 in [the tool-agreement spec](../../specs/spec-tool-agreement.md) ships, then remove it |
| C++ equivalence at 1,981,480 decision points (lines 99–106) | Not tested by this work. The C++ target shipped as a preview | out of scope | owner to confirm the wording still reflects the preview status before the page grows around it |

## Proposed changes to `composing-a-system.md`

**1. Two ways to compose a supplier's component** — new section after "The bean graph is the node graph".

- *Declare each class.* The integrator lists every supplier class as a bean; names are the integrator's.
  This is the existing example.
- *Reference a root.* The supplier ships a pre-wired sub-graph and the integrator references one class; the
  compiler discovers the rest through its fields. It needs less wiring, but the names are the supplier's,
  so the supplier should implement `NamedNode`. `NamedNode` names are global, so two suppliers choosing the
  same name will collide.

**2. Components that speak your events** — new section. This is the best integration pattern we have, and
none of our documentation describes it. The supplier types its handler on an interface it owns; the
customer's event implements it; the compiler merges the routes. Show the generated `handleEvent(MarketPrice)`
excerpt from the evidence record. *The supplier publishes interfaces, the customer's events implement them,
and nobody writes an adapter.*

**3. A warning box, directly under the Spring example:**

> **Never list a supplier's class in `nodeBeans`.** Reference it from one of your own nodes instead. The
> starter writes skeleton classes for `nodeBeans` entries it cannot find source for. A class that exists
> only in a dependency jar looks like a class that does not exist yet, and the skeleton silently replaces
> it: the build stays green and the supplier's component is gone.

Replace the box with the diagnostic's own text once the starter refuses to shadow a classpath class
(feedback #29).

**4. Extend "What this does not remove"** with two paragraphs:

- *The supplier's obligations:* public nodes and constructors, `NamedNode` for stable names, getters and
  setters for configurable properties, interface-typed handlers.
- *Integrated is not certified.* State the tampered-jar result as a result, not a warning. A build with the
  supplier's risk constant zeroed passed every freshness check with byte-identical receipt hashes. It was
  caught only by an independent calculation (9 of 11 rows wrong). The trace proves **which supplier nodes
  ran and in what order**. It does not prove **which build of the supplier's jar ran**, or that its
  arithmetic is right. That needs dependency digests in the run receipt and an independent check.

## One new page — "Integrating a vendor component: a worked example"

Placed under **The audit log**, directly after *Composing a system from supplier jars*, which stays
conceptual and links to it.

| Section | Content | Evidence |
|---|---|---|
| The component | the fictional Acme Risk jar, labelled fictional; what is inside it | `acme-risk/src` |
| The two lines | the Spring snippet: one bean, one reference, one property | README |
| What appeared | topology screenshot with the vendor sub-graph; the audit order `acmeQuoteFeed → acmeVarCalculator → acmeRiskEngine → riskLimitGuard` | `runs/vendor-topology.png`, `runs/risk-run3` |
| Control and configuration | the property in generated code; the exported service flipping breach state live | P9, P12 |
| Checking the computation | the independent calculation, 11 of 11 rows to 1e-6; how to run it | `check_var.py` |
| Upgrading safely | three jar versions, host validation unchanged | P13, `dist/` |
| When the jar is wrong | the tamper, and what the trace can and cannot tell you | `runs/risk-tampered` |
| Checklists | **for integrators:** reference, never `nodeBeans`; re-resolve the classpath after adding a jar; keep an independent check. **For suppliers:** public nodes and constructors, `NamedNode`, getters and setters, interface-typed handlers | P2, P4, P6, P8, P9, P17 |

The supplier checklist also closes feedback #35 ("no redistributable-component page").

## Before this is published

- **The P4 warning ships with the page** while feedback #29 is open. A page that makes integration look
  easy, without that warning, leads readers straight into the one silent failure.
- **No "certified" wording anywhere.** It is on positioning's *Do not claim yet* list, for exactly the
  reason this page's own evidence shows.
- **The TA-9 caveat stays** until the topology draws supertype routes.
- **Every number links to the evidence** in `docs/handoff/evidence/vendor-integration-2026-09-19/`. The
  independent check re-runs from that copy: genuine run 0 of 11 mismatched, tampered run 9 of 11.
- The fictional vendor is labelled as fictional.

## Acceptance

- `mkdocs build --strict` passes, which is the Pages workflow's own gate.
- Each row of the claim audit is resolved on the page: corrected, qualified, or explicitly confirmed by the
  owner.
- `runs/vendor-topology.png` is copied into `docs/site/assets/`; the worked example links its evidence.
- Owner review before merge. This page will be read by prospective suppliers and integrators, and it
  should not claim anything the evidence record does not show.
