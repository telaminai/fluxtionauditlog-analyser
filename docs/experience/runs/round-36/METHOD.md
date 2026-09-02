# How the components must be prepared, before any author sees them

The owner's correction, and it is a real one: **build and validate each subsystem separately, then hand
over the binaries.** I validated all four together first, which is the wrong order — proving the
composition is solving the problem I am setting.

## The staging

**Stage 1 — each subsystem is its own Spring-Fluxtion build.** Its own bean file, its own generated
processor, its own tests proving it is a functioning graph over its own stages. It compiles and passes
on its own, with no knowledge of the others.

**Stage 2 — publish binaries.** Each subsystem becomes a jar. **No source.** That is what a prebuilt
component is, and it is the condition that makes the comparison fair: reading `evaluate()` bodies was
the whole of vanilla's method in round 34, and a jar removes it. Fluxtion reads constructor parameter
*types*, which survive compilation.

**Stage 3 — the author composes.** Given jars and a statement of what each subsystem provides and
requires, produce one engine with one correct global dispatch.

## Why the order matters

The same discipline as path-versus-node, one level up. If a component is not independently proven, an
integration failure is ambiguous — you cannot tell a broken component from a broken composition, and
you will spend cycles unable to separate them.

**Round 34 is the worked example of getting this wrong.** I shipped components with `Object`
constructor parameters, never validated them as graphs, and handed them over. `Object` is invisible to
the generator, so the dependency did not exist; the agent reached for `setAccessible()` on a private
final field to make it visible, and I recorded that as evidence about the framework. It was evidence
about my components. A stage-1 test would have failed on my side, before any author saw it.

## The two theses this is built to separate

**Build everything.** Every subsystem written fresh each time, global dispatch hand-maintained. Measured
neck-and-neck with the framework at small scale — four specs, four ties, and vanilla cheaper on two of
them. The claim against it is that it fails at scale, which this project has not yet demonstrated.

**Reuse trusted subsystems.** Components validated once, published as binaries, composed by declaration,
with global dispatch derived. The claim is that this is the only one of the two that survives more
components — and it is the claim four ties at small scale cannot address either way.

**The experiment is the composition, not the construction.** Both arms get the same validated binaries;
what differs is whether the global order is derived or worked out.
