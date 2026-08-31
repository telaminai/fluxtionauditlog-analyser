# Transfer run — does the rule survive to a node the diagnostic never explained?

**Run 2026-08-31 by the compiler session.** Compiler under test:
`fluxtion-compiler` `feature/compiler_diagnostics` @ `dbcbe17`.

Follows [`comparison-2026-08-31`](../comparison-2026-08-31/RESULTS.md), whose self-assessment records
why it was needed: that run handed agents the rule in the prompt and then scored whether they had it,
which is close to circular.

## The question

Not *"can an author read the sidecar"* — they can. **Does reading FLX-1009 on node A leave an author
able to answer correctly about node B, which carries no diagnostic at all?**

Both arms see case A (the `SymbolStats` failure). Control gets the legacy console message only;
treatment also gets the sidecar. Both are then shown a **different** node with no error message and no
diagnostics file, and asked per-field whether Fluxtion will require a constructor to supply it, whether
the class builds, and the minimal fix.

n=3 per arm, one model, one sitting, abstention explicitly permitted, no web access.

**Every ground truth below was established by compiling the fixture, not by assertion.**

## Fixture v1 — a NULL result, and the fixture is why

First fixture had five fields including `@ConstructorArg private String region` and a
`venue`/`getVenue`/`setVenue` bean property.

**Result: 14/15 control, 15/15 treatment. No usable difference.**

**The fixture was the problem: both discriminators were self-labelling.** `@ConstructorArg` states its
own answer in its name, and a field with a setter signals the setter route. An agent does not need the
rule to answer either. Recorded rather than discarded, because a null result on a broken instrument is
worth exactly as much as the instrument.

One control did abstain on `region`, doubting the annotation was even legal on a field. It is —
verified by compiling.

## Fixture v2 — two fields differing ONLY in `final`

```java
public class Ledger {
    private final Parent parent;
    private final String venueA = "LSE";   // final     -> constructor-mapped, NOT supplied
    private String venueB = "NYSE";        // non-final -> setter-wired, not required
    public Ledger(Parent parent) { this.parent = parent; }
    public String getVenueB() { return venueB; }
    public void setVenueB(String v) { this.venueB = v; }
    @OnTrigger public boolean onTick() { return true; }
}
```

`venueA` and `venueB` are the same type, both with initialisers, and differ in one keyword. Nothing
about either field's name, type or annotations carries the answer.

**Ground truth, compiled:** REJECTED, `failed to match for these fields:[venueA, parent]`. The fixed
version, with a constructor accepting `venueA`, builds.

| | control | treatment |
|---|---|---|
| `venueA` correct (the discriminator) | **2 of 3** | **3 of 3** |
| build outcome correct | **2 of 3** | **3 of 3** |
| `venueB` correct | 3 of 3 | 3 of 3 |

## The finding is qualitative, and it is sharper than the count

**Both control failures — across both fixtures — used the SAME wrong rule.**

> *"A literal `String` field is the kind of simple, immutable value Fluxtion can render/re-establish
> without a constructor argument."* — v2 control 3, concluding the class **builds**. It does not.

That is the **type-renderability** rule: *values Fluxtion can render as literals do not need a
constructor.* It is wrong — finality is the trigger, not renderability — and it is exactly the rule the
legacy message invites, because the message's own example happens to involve a `Map`, which is both
final and unrenderable. The two properties are confounded in the only example the author is given.

The same rule appeared in fixture v1, where it produced a *right answer for the wrong reason* and was
therefore invisible. v2 is what it looks like when the confound is removed: a confident wrong answer,
and a build cycle spent.

All three treatment agents cited finality instead, unprompted, on a node the diagnostic never mentioned.

## Why the control does as well as it does — worth stating

The controls are not guessing. They **induce** the rule from case A's worked example: `rootNode` is
final and matched, `statsBySymbol` is final and unmatched, so finality looks load-bearing. Two of three
got there.

**So the legacy message EXHIBITS the rule without STATING it.** For a small fixture a capable reader
can often recover it. The induction fails where the example under-determines the rule — which is
precisely the `final String` case, and precisely where the sidecar's `why` is doing work.

That reframes the value: not *"authors cannot work it out"*, but *"the example they are given does not
distinguish finality from renderability, and one of those is right."*

## Aggregate, and what it does not support

Across both fixtures, on the discriminating field: **control 4 of 6, treatment 6 of 6.**

That is directionally consistent and **not statistically established** — Fisher's exact on 4/6 vs 6/6
is around p≈0.45. Six observations per arm cannot carry an effect-size claim and none is made here.

**Licensed:** the wrong rule is real, identifiable, and named — type-renderability instead of finality.
It produced a confident wrong build prediction once in six control trials, and never in six treatment
trials.

**Not licensed:** any rate. "2 of 3 vs 3 of 3" is a description of six agents, not a measurement.

## Limits

- n=3 per arm per fixture; one model, one sitting, one harness.
- I implemented the diagnostic and designed both fixtures. The v1 flaw is direct evidence that my
  fixture design is a source of error; v2 was built to remove it but was designed by the same party.
- Abstention permitted; real authoring gives no such permission.
- Ceiling, not floor: the sidecar is opt-in and the console message is unchanged.
- Both fixtures are small. The induction that rescues the control arm gets harder as graphs grow, which
  would flatter the treatment — untested.

## What would settle it

More agents on fixture v2 is the cheap answer, and would give a rate rather than a description.

The better answer is a fixture where finality and renderability point in *opposite* directions — a
final field of a renderable type that must be constructor-supplied, and a non-final field of an
unrenderable type that must not. v2 is half of that. The other half would make the wrong rule fail
loudly rather than occasionally.
