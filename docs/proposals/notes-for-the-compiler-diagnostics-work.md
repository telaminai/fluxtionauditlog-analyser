# Notes for the compiler-diagnostics work

**From:** the audit-log analyser session · 2026-08-30
**Status:** input, not a specification. The diagnostics branch owns the design; this is evidence and
argument it can use or discard.

Everything here comes from running **nine fresh LLM sessions** against real generated Fluxtion projects
over two days and recording what they hit, plus reading `fluxtion-builder` 1.0.64 and `fluxtion-runtime`
1.0.13 directly. Where I say *measured*, a run produced it. Where I say *I think*, it is an opinion and
labelled as one.

Already filed, so this does not repeat them:
[#19](https://github.com/telaminai/fluxtion/issues/19) (constructor match),
[#20](https://github.com/telaminai/fluxtion/issues/20) (silently unselected bean),
[#22](https://github.com/telaminai/fluxtion/issues/22) (the audit contract),
[#23](https://github.com/telaminai/fluxtion/issues/23) (sibling dispatch order),
[#25](https://github.com/telaminai/fluxtion/issues/25) (tracing fixed at generation time).

---

## 1 · The finding that should shape the work

An author **holding the correct, published rule still got it wrong and had to fail a build to find out.**

The `transient` rule is documented — in `claude.txt` *and* in the playground orientation's source-gen
triage table, correctly, with both remedies. In round 05 an agent had that table fetched and in context,
indexed by the very error string it was about to see. It still wrote the field wrong, failed the build,
and only then applied the fix by looking the message up.

**So the case for coded diagnostics is not "the rule is undocumented".** It is that a message is read at
the moment of failure by construction, and a document is read only if someone chooses to. Prose that
pre-empts a good error message is dead weight in every session that never hits it.

The corollary is worth stating because it protects the branch's scope: **when a diagnostic lands, the prose
that compensates for it should be deleted, not kept "for completeness".** Otherwise you have two sources
and one of them drifts.

## 2 · Three agents, three different fixes, none of them the documented one

Across rounds, six agents hit the constructor-match failure. The unresourced ones produced **three
distinct working fixes**:

| fix | who found it |
|---|---|
| mark the field `transient` | the documented remedy — only agents with the triage table |
| **remove `final`** | two agents, independently |
| **leave it null and initialise lazily** | one agent |

All three are sound against the field-inclusion predicate. **Not one agent used the JavaBean setter
route**, which is a supported way to supply the field and which the message never mentions.

**Sharpened 2026-08-31 by a baseline run, and the correction matters.** Asked to *state the rule*
rather than fix a build, **two of two agents named the setter route unprompted**. So the knowledge is
present and does not survive contact with a failing build — which moves the diagnosis. The message is
not failing to teach something unknown; it is **crowding out something already known** by naming one
strategy at the moment of failure. Neither of those two agents named `final` as the trigger, though,
so the half that is genuinely missing is *which* fields get mapped, not *how* they can be supplied.
See `docs/experience/runs/baseline-2026-08-31/`.

Two conclusions I would defend:

**Enumerating remedies invites a fourth.** I first proposed "name both remedies" and that was wrong. A
message should state the **rule** — *which fields must be supplied, and how they can be* — because each
author who papers over the symptom without learning the constraint will paper over the next one too. Every
one of those three agents shipped a fix it could not explain.

**The message currently narrows the reader's options rather than widening them.** `LiveGraphSourceGenExtractor`
runs three assignment strategies — constructor, JavaBean setter, public member — and only the constructor
one throws. So the message names the strategy that failed and says nothing about the two that would have
worked. That is worse than terse; it is misleading by omission.

## 3 · The codebase already knows how to do this, in one place

Thirty bytecodes from the throw in `generateComplexConstructors`, a sibling `throw` handles
`validateNoTypeClash` and builds its message as:

```
"cannot find matching constructor for:" + node + " use @" + AssignToField.getSimpleName()
    + " to resolve clashing types these fields:" + [...]
```

**One failure names the annotation that fixes it; its neighbour, under the same message prefix, names
nothing.** The neighbour is the one that cost measured build cycles. Whatever house style the branch
settles on, the precedent for "name the remedy" is already there and did not need inventing.

And the information is in scope: the predicate that decided which fields must be constructor-matched is a
lambda **of the throwing method** and reads `@ConstructorArg`, `@FluxtionIgnore`, `Modifier.isStatic` and
`Modifier.isTransient` directly. The failing list is, by construction, fields the compiler has just
finished deciding are not transient and not ignored.

## 4 · The highest-value codes are the ones where the build goes GREEN

This is the argument I would press hardest, because it is where the loop's expensive findings clustered.

Every costly failure we measured was **silent**:

- a bean declared in the Spring context but in neither `nodeBeans` nor `ignoredBeans` → green build, node
  simply absent, never fires, logs nothing (#20);
- a node that cannot record its own values → appears in the record with only a method name, and *"ran and
  said nothing"* is indistinguishable from *"is fine"* (#22);
- `addEventAudit(null)` → **no auditor at all**. Green build, running application, no record.

The loud ones cost a build cycle each. The silent ones cost a debugging session, and in the audit case they
cost the thing the audit exists to prove. **A diagnostic that fires where the build currently succeeds is
worth more than a better message on one that already fails** — and it is also the harder engineering, so
it is worth deciding early whether it is in scope.

## 5 · A suggested shape, offered rather than asserted

Consistent with #19 and with the existing sibling message:

```
FLX-1009  no constructor matches this node's mapped fields
  node       priceStats : com.example.myapp.node.PriceStats
  unmatched  [statsBySymbol: Map, rootNode: RootNode]
  rule       Each non-transient instance field must be supplied by one of:
             a constructor argument, a JavaBean setter, or a public member.
  fix        If the field is node-local STATE (a counter, a map, a running total):
               mark it `transient` or @FluxtionIgnore.
             If it is a REFERENCE the graph should supply:
               add a constructor parameter, a setter, or make it public.
  see        https://fluxtion.dev/errors/FLX-1009
```

**The `fix` fork is the load-bearing part.** Everything above it is already in the message or in scope at
the throw site. And the stable URL matters more than it looks: it is what makes the semantics reachable
**at the moment of failure** rather than requiring an author to have read them beforehand — which is
precisely where inference creeps in.

A machine-readable sidecar (`target/fluxtion-diagnostics.json` with `{code, severity, element, rule,
suggestedFix, sourceRef}`) turns "read English that changes between versions, guess, retry" into a
deterministic loop. That is #19/UP-FLX-01's original ask and I still think it is the single
highest-leverage item.

## 6 · How to know a diagnostic actually works — the part I got wrong twice

I offer this because I learned it badly today, not because I did it well.

**A test whose failure you have not witnessed is a claim, not evidence.** I twice wrote a test for a fix,
asserted it was covered, and was wrong: once the test exercised a guard I already had rather than the one I
had just added; once the fixture declared a version that made the mutated branch unreachable, so the
mutation passed and I nearly reported the check as sound. Both were found by **mutating the fix and
watching the test fail** — and only then.

For diagnostics specifically I would add: **a message is untested until someone who does not already know
the answer has read it.** The author of a diagnostic is the worst possible judge of whether it is
followable, because they know what it means. That is not a hypothetical — the `transient` message reads
perfectly to someone who knows the rule, and produced three different wrong fixes in people who did not.

## 7 · An offer, not a suggestion

We have a working harness for exactly that test. Round 06 ran fresh agents against a real generated
project, with no knowledge of the answer, and recorded build failures, what they could not find, where they
went looking outside the project, and **what they came away believing**.

Pointed at a candidate diagnostic, it answers questions the branch cannot answer from inside:

- does an author recover in **one step** after seeing the new message, or still guess?
- do they end up understanding the **rule**, or just the trick that made the error go away?
- does the message send anyone in a wrong direction, as *"run `./mvnw package`"* did when that command
  could not fix the thing it was suggested for?

If that is useful, say so and we will run it against your branch's messages before they ship. It costs us
two agents and about twenty minutes per candidate. **The measurement that produced these notes is the one
thing here that we can offer rather than merely argue.**

## 9 · A WORKFLOW finding, measured — develop bean-style, harden to constructors

Owner-raised 2026-08-31 and **verified by experiment**, because it is exactly the kind of "transferable
lesson" that is worth nothing if it is wrong.

**The friction.** A generated processor is committed, and it constructs each node. Change a node's
CONSTRUCTOR SIGNATURE and the committed source stops compiling — which blocks the regeneration that
would fix it. `mvn -Pregen` compiles before it scans, so the build cannot bootstrap out of it. Hit four
times in one session.

**The measurement.** Two probes against a real graph:

| probe | result |
|---|---|
| convert a node FROM constructor TO bean style | breaks — the committed source calls a constructor that no longer exists |
| **add a NEW dependency to an already-bean-style node** | **compiles, regenerates, wires it** |

Bean style emits `new AuditInstallation()` at the declaration and
`auditInstallation.setOpenGraph(openGraph)` in an init block. Because the constructor signature never
changes, **the committed generated source stays valid while dependencies come and go** — the
chicken-and-egg simply does not arise.

**The rule this gives an author**, and it matches how graphs actually evolve — structure churns early
and then settles, while node LOGIC keeps changing:

1. **While the shape is moving, use JavaBean style** — non-final field, setter. Adding or removing a
   dependency never blocks regeneration.
2. **Harden to constructor injection when the shape settles.** Final fields make the dependencies
   explicit and immutable, and by then you are not changing them.
3. **The migration is a one-time break** — do it deliberately, once, not by accident mid-slice.

This also explains why the friction is front-loaded rather than permanent, which is what the owner said
and what this repo experienced: four constructor-shape breaks during M44's early slices, none once the
node set stabilised.

**For the bootstrap docs this is worth more than a diagnostic**, because no compiler message can tell an
author "you would have had a better week if you had started with setters".

## 8 · What I am not confident about

- ~~**I have read bytecode, not compiler source.**~~ **RESOLVED 2026-08-31, from two directions at once.**
  The diagnostics branch read the mapping loop and reported that it skips **static, non-final and
  transient** fields — so what reaches the constructor match is exactly the **final, non-transient,
  non-ignored** set. Independently, M44 built a real six-node graph in this repo whose nodes are all
  mutable private state, and the `transient` rule never fired once. Two different methods, same
  predicate.

  **That changes §2 above, and in the branch's favour.** *"Remove `final`"* — which two agents found
  independently and which these notes filed as an undocumented workaround — is a **first-class fix**: a
  non-final field is wired through its setter and was never constructor-mapped at all. So a message that
  names only `transient` is steering authors away from the simpler remedy, not merely omitting one. The
  owner's framing is the one to ship: *bean patterns are supported for non-final fields; use `transient`
  or `@FluxtionIgnore` for final fields derived at construction.*

  The one thing still worth attacking is the classification itself, because it decides which of **two
  opposite** fixes an author is told to apply, and it has been wrong once already.
- **n ≤ 9, one model family, one machine.** Enough to say three agents found three different fixes; not
  enough for an effect size on anything.
- **I do not know your constraints** — backward compatibility of message text, tooling that parses it
  today, or whether a sidecar file is acceptable in the plugin's execution model. Any of those could make
  the shape in §5 wrong, and the evidence would survive a different shape.
- **The dispatch-order and audit-contract items (#23, #22) are documentation, not diagnostics.** They are
  here only because they came from the same measurement, and because #23 is the one where the gap does not
  produce uncertainty but a **specific false belief**: four of six authors independently concluded
  declaration order, because the obvious experiment cannot distinguish it from the real rule.
