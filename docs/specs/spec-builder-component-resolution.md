# Spec — builder-owned component resolution and Spring document

**Status:** PROPOSED for upstream review
**Target:** the existing `fluxtion-builder` jar and the Fluxtion Maven plugin
**Evidence:** rounds 48, 55 and 57; the reviewed prototype in `tools/bean-resolver.py`
**Input contract:** [`spec-component-catalogue.md`](spec-component-catalogue.md)
**Architecture:** [`spec-authoring-modes.md`](spec-authoring-modes.md), stages 2–5

## Decision

Component selection, construction ordering, identity allocation and Spring-document production belong
in the **single existing `fluxtion-builder` jar**. They do not belong in the analyser, the generated
runtime, or the browser starter.

The builder implementation MUST use its own small parser and writer for the supported Fluxtion Spring
authoring subset. This feature MUST NOT add a transitive Spring dependency. The existing
`FluxtionSpring` loading/compilation entry points remain the acceptance boundary for the XML produced.

`fluxtion-builder` main sources are a **Java 8 contract**, because the jar runs under CheerpJ in the
browser playground. Every type and implementation added by this feature MUST compile to Java 8
bytecode and use Java 8 language/library surfaces: no records, sealed types or Java 9+ library calls.
Test sources may use the module's newer test level; shipped main classes may not.

The Fluxtion starter remains the interactive editor. It consumes the same document contract and shares
conformance fixtures with the Java implementation; its layout, undo/redo and browser state do not move
into the builder.

This is one build-time capability with four parts:

1. read and validate component catalogues;
2. resolve requirements into one typed, constructible component plan;
3. parse and write a typed Spring bean document;
4. expose the capability through the Maven plugin and normal builder API.

No part is added to `fluxtion-runtime`. A generated application continues to run with the runtime jar
alone.

## Why this is now an upstream feature

In the measured round-48 fixture a deterministic resolver replaced 51 model turns, selected the same
components, emitted byte-identical XML, built successfully and reproduced the reference audit figures.
That is evidence for the boundary, not for a general-purpose resolver: one fixture family is measured,
and modes 2/3 remain unmeasured.

The prototype then exposed the architectural requirement. When validity lived in renderers, a cyclic
two-component candidate beat a valid three-component answer on minimality and the valid answer was
discarded. When identity lived in the XML writer, two fully-qualified classes sharing a simple name
received the same bean id. Both defects disappeared only when `Resolution` became the authority for
validity, order and identity.

The production design keeps that seam and moves it to the component that owns the build.

## Ownership

| Fact | Single authority |
|---|---|
| component capabilities, requirements and eligible construction | generated component catalogue |
| descriptions, conventions and explicitly selected constructor | component author/vendor |
| requested figures and site conventions | resolution request |
| selected components and exact requirement bindings | component resolver |
| constructibility and construction order | component resolver |
| stable bean identities | component resolver |
| Spring syntax and canonical bytes | Spring document writer |
| interactive layout, undo and user edits | starter UI |
| object construction and Fluxtion graph discovery | existing `FluxtionSpring` loader/compiler |
| graph relationships, propagation and dispatch order | Fluxtion compiler model |

A renderer MUST NOT rediscover any fact present in the resolution. A parser reports document facts; it
does not decide component selection. The starter may edit a document but does not become a second
resolver.

## 1. Public build-time model

Names and algebraic notation below specify responsibilities, not Java syntax. The implementation MUST
use Java-8-compatible interfaces/classes, explicit variants and factories. It MUST NOT use records or
sealed types. Value objects define stable equality and hash semantics explicitly where those semantics
participate in resolution, diagnostics or conformance fixtures.

### `ComponentDescriptor`

One catalogue entry carries at least:

- stable component identity: artifact coordinate or equivalent source identity plus entry-point FQN;
- provided figures and their published interface and exposed field name;
- required interfaces;
- eligible constructor and its parameter order;
- consumed event types and filters;
- description and declared conventions;
- catalogue format version and provenance.

Simple class names and `Implementation-Title` are display values, never identity. **A simple name is
never an identity.** Every renderer resolves component and type references from the full identity and
makes the same qualification decision for a type's declaration and every emitted use.

### `ComponentResolutionRequest`

The request carries:

- one or more catalogues;
- required figures/capabilities;
- site conventions/policy;
- optional build policy that affects the emitted `FluxtionSpringConfig`;
- an explicit output mode, if more than Spring XML is added later.

Goal interpretation is outside this API. Turning “build a risk engine” into required figures is the
unmeasured stage 1 problem; the resolver receives the resulting formal requirements.

### `ComponentResolutionResult`

The resolver returns one typed result with a stable status:

- **RESOLVED** — one semantic answer, including its ordered construction plan and allocated ids;
- **AMBIGUOUS** — two or more equally valid semantic answers, with the differing decisions named;
- **UNSATISFIED** — the catalogue cannot cover or consistently bind the request;
- **INVALID_CATALOGUE** — malformed, contradictory or unsupported metadata.

A RESOLVED result carries:

- selected component identities;
- exact binding for every constructor requirement;
- topologically ordered construction steps;
- one allocated bean id per selected entry point;
- the exposed-field reference used for each dependency;
- effective conventions and their declared source;
- diagnostics and warnings;
- enough provenance to reproduce the decision.

The result is immutable to consumers. Spring XML, a future Java-builder renderer, CLI text, JSON and
diagnostics all consume it. None performs additional validity checks or allocates identities.

## 2. Resolution rules

Resolution is deterministic for identical inputs. Catalogue iteration order, jar filesystem order,
reflection order and hash-map order MUST NOT affect the result or its bytes.

The resolver evaluates candidates in this order:

1. validate catalogue structure and version;
2. apply site conventions — where a figure is profiled, silence is not a match;
3. require complete figure coverage;
4. bind every required constructor parameter to an exact provider;
5. reject or report an ambiguous binding where more than one provider can satisfy that parameter;
6. establish constructibility and reject constructor cycles;
7. only then apply minimality and over-provisioning policy;
8. if equivalent candidates remain, return AMBIGUOUS rather than selecting lexically;
9. allocate stable unique ids and return the final construction order.

Constructibility is a validity condition, not an emitter concern. A smaller cyclic candidate cannot
eliminate a larger valid candidate. Cycle diagnostics SHOULD name an actual strongly connected
component or cycle path, rather than every node left blocked by it.

Provider uniqueness is a **binding** question, not a global ban on shared interfaces. Two selected
components may implement the same interface if no required parameter is ambiguous. Where a parameter
could bind to both and policy does not decide, the resolver refuses; it never selects the first or last
map entry.

Ranking policy MUST be explicit and tested. The initial policy is:

1. fewest selected entry points;
2. fewest figures beyond those requested;
3. semantic equality means one answer; otherwise tied answers are AMBIGUOUS.

A lexical key may order diagnostics and serialization. It MUST NOT silently settle a semantic tie.

## 3. Diagnostics and refusals

The refusal vocabulary is part of the product. At minimum it distinguishes:

- requested figure has no provider;
- required interface has no provider;
- constructor binding has several providers;
- declared convention has no matching component;
- constructor cycle;
- malformed or unsupported catalogue version;
- duplicate component identity;
- no unique safe bean id can be allocated;
- unsupported Spring authoring construct.

Use the compiler diagnostics transport and element model. Allocate final diagnostic codes in the
compiler tracker rather than creating a parallel namespace here.

Every diagnostic states:

- the rule that failed;
- the component, capability or binding involved using full identity;
- the evidence that made the resolver refuse;
- whether changing catalogue metadata, site policy or the requested figures can resolve it.

It does not enumerate speculative fixes. Ambiguity is not an error hidden behind an arbitrary choice;
it is a first-class result that a human or model can settle once and record as policy.

## 4. Identity allocation

Identity is allocated once, after a valid selection exists and before RESOLVED is returned.

The identity input is the complete component identity, including entry-point FQN. A readable base may
come from the artifact id, but any disambiguating suffix is derived from full identity, never from the
simple class name alone. If human-readable escalation cannot prove uniqueness, use a stable short digest
of full identity. Collision handling itself is deterministic and tested.

The allocation map is carried in `ComponentResolutionResult`. Declaration emission and every
dependency reference read the same map. Calling a `beanId(component)` naming heuristic independently
from two renderers is forbidden.

Changing catalogue iteration order does not change allocated ids. Adding an unrelated, non-colliding
component does not rename existing beans. A collision may lengthen only the colliding identities.

## 5. Typed Spring document

The builder owns a small format-neutral object model:

```text
SpringBeanDocument
  beans: ordered SpringBean[]
  fluxtionConfig: optional FluxtionSpringConfigDocument

SpringBean
  id
  className (FQN)
  constructorArguments[]
  properties[]

SpringValue
  ScalarValue(value, optionalType)
  BeanReference(beanId)
  ExposedFieldReference(beanId, fieldName)
  ListValue / SetValue / MapValue
  InlineBean
```

This is a closed conceptual value algebra, not sealed-interface/record syntax. Its shipped Java form
uses Java-8-compatible interfaces or an abstract base with explicit final variants.

`ExposedFieldReference` is load-bearing for bought-in Fluxtion components. It renders as:

```xml
<constructor-arg value="#{marketdata.mid}"/>
```

It is not an opaque string and not the same relationship as `ref="marketdata"`. The former reaches a
published node inside a component holder; the latter injects the holder itself. Confusing them can
compile and produce a smaller, wrong graph.

The emitted `#{bean.field}` is Spring SpEL. The builder writer serialises that structural reference but
does not evaluate it; the existing `FluxtionSpring` context loader evaluates it when loading the bean
document. `spring-context` remains `provided`, so this feature adds no transitive Spring dependency.
A consumer that parses the document without Spring can inspect and preserve the reference but cannot
obtain its runtime value.

The existing starter parser currently derives edges from `ref` but treats `#{bean.field}` as a scalar.
The shared contract and conformance corpus must close that gap before the starter claims it can edit a
resolver-produced component graph.

### Supported XML subset

The builder parser/writer supports only the subset it declares:

- namespace-qualified or unqualified `<beans>` root;
- top-level `<bean id|name class>`;
- `<constructor-arg>` with `index`, `name`, `ref`, `value`, `value-ref`, or supported nested value;
- `<property name>` with the same supported value forms;
- nested `<ref>`, `<value>`, `<list>`, `<set>`, `<map>` and `<bean>`;
- the exact `#{bean.field}` exposed-field expression;
- the `FluxtionSpringConfig` shapes emitted by the builder.

Arbitrary SpEL, factory methods, aliases, imports, profiles, custom namespaces and other Spring
features are outside this authoring subset. The parser MUST refuse an unsupported construct with its
location; it must not drop, reinterpret or approximately round-trip it. Existing `FluxtionSpring`
loading of authored Spring files is unchanged and may support a broader surface.

### Parser security

The parser uses JDK XML facilities only and explicitly disables external entities, external DTD/schema
fetches, XInclude and entity expansion. It applies bounded input, nesting, bean and collection limits.
Tests include XXE, entity-expansion and oversized-document probes. Parsing never performs class loading
or instantiates a bean.

The Java parser is a desktop build/Maven/API capability; the browser starter continues to use its
JavaScript parser and does not invoke this entry point. Because the parser classes still ship inside the
CheerpJ-loaded builder jar, slice 3 MUST run a playground/CheerpJ load-and-compile smoke test to prove
they introduce no eager unsupported JAXP linkage. This is not evidence that the Java parser works under
CheerpJ. If browser code is ever to call it, a separate functional XML probe is required first.

### Canonical writer

The writer emits:

- UTF-8 and LF line endings;
- fixed namespace and schema declarations;
- XML-escaped ids, class names and scalar values;
- beans in the resolution's construction order;
- constructor arguments in declared constructor order;
- properties and configuration in deterministic documented order;
- no timestamps, machine paths or generated-at values;
- one final newline.

`write(parse(write(model)))` is byte-identical. `parse(write(model))` is model-identical. A parser is
not required to preserve formatting from an imported document; it canonicalises supported input and
refuses unsupported input.

## 6. Existing consumers and migration

### Fluxtion starter

The starter's browser parser, writer, round-trip tests and builder-state logic are prior art and a
downstream consumer. They are referenced, not copied into the Java jar.

The browser keeps:

- canvas positions and layout JSON;
- undo/redo;
- interactive add/remove/rename operations;
- UI notifications and dirty state.

The shared contract owns bean/value semantics. Java and JavaScript implementations run the same
conformance cases: XML input, normalized document facts, canonical XML and expected refusal. The
starter must reject duplicate ids and propagate a renamed id through both ordinary references and
exposed-field references.

### `StitchToSpring`

The verification pipeline's `StitchToSpring` is another existing projection. General escaping,
ordering and `FluxtionSpringConfig` emission move behind the builder writer. Stitch remains an adapter
from its domain document to `SpringBeanDocument`; it does not retain a private XML authority.
`TransformContractTest` already pins byte-identical output across runs, including flattened
compositions. The migration preserves those bytes or deliberately re-baselines the fixtures in the
same reviewed commit; it must not accept an incidental regeneration diff.

### Existing Spring applications

No existing `FluxtionSpring.compile*` behaviour changes. The new parser is used by the component
resolution/manipulation API, not inserted in front of every existing Spring application. Adoption is
additive.

## 7. Builder and Maven surfaces

The builder exposes a programmatic operation equivalent to:

```text
resolve(ComponentResolutionRequest) -> ComponentResolutionResult
toSpringDocument(ResolvedPlan)       -> SpringBeanDocument
writeSpring(SpringBeanDocument)      -> bytes
parseSpring(bytes)                   -> parsed document or diagnostics
```

The Maven plugin invokes those same operations. Configuration supplies required figures, conventions,
catalogue sources and output location. It does not contain another solver or XML template.

Only RESOLVED produces an output file. AMBIGUOUS, UNSATISFIED and INVALID_CATALOGUE fail the goal with
structured diagnostics and leave no partial replacement. Output is written atomically.

The generated Spring document is a reviewable build artefact. Whether a project commits it or writes it
under `target/` is project policy; the bytes and resolution provenance are the same.

## 8. Catalogue generation is part of completion

Productisation is not complete when the resolver moves. The measured manifests are hand-authored.
Until Fluxtion emits the catalogue during component build, the resolver consumes a convention that no
toolchain enforces and the ecosystem loop remains open.

The paired catalogue work MUST:

- derive every mechanically knowable field from compiled classes and the compiler model;
- require declarations only for facts compilation cannot know;
- select constructor intent explicitly where bytecode offers several eligible constructors;
- write manifest attributes using the versioned contract;
- reopen the jar and validate the attributes that actually shipped;
- fail on truncation, duplicate identities or internally inconsistent metadata.

Catalogue generation and resolution live in the same builder jar. The Maven plugin supplies lifecycle
goals; it does not duplicate their implementation.

## 9. Compatibility

- The new APIs are additive to the builder jar.
- No class is added to the generated runtime dependency surface.
- No Spring dependency becomes transitive because of this feature.
- Existing Spring XML compilation remains unchanged.
- The catalogue format is explicitly versioned. A v1 catalogue lacking `figure=Interface` mappings is
  refused with that cause, rather than reported as a missing business figure.
- Result and document models use full class identity; simple names are display-only.
- If remote generation carries the resolved plan, the frozen DTO payload remains byte-identical.
  New facts travel in the negotiated `WireEnvelope` side-band (or another explicitly versioned carrier),
  because Kryo is positional and adding even a nullable DTO field has already failed the cross-version
  evolution test. Java and Kryo compatibility goldens must remain green.

## 10. Required verification

### Resolver unit tests

- round-48 selection and XML remain byte-identical;
- input permutations produce the same result and bytes;
- a smaller cyclic candidate does not discard a larger valid candidate;
- an all-cyclic catalogue names a real cycle;
- missing provider and missing figure are distinct;
- duplicate providers produce ambiguity only at an unresolved binding;
- convention silence fails closed where a convention is required;
- tied semantic candidates return AMBIGUOUS;
- two FQNs sharing a simple name receive distinct stable ids;
- normalized artifact-name collisions receive distinct stable ids;
- declarations and references consume the identical allocated-id map.

### Spring document tests

- every supported value form parses and writes;
- `#{bean.field}` becomes `ExposedFieldReference`, produces an edge and round-trips;
- `ref="bean"` remains a different value kind;
- renaming a bean updates both reference kinds;
- duplicate ids and dangling references are refused;
- XML metacharacters are escaped;
- unsupported SpEL and unsupported Spring elements are refused without partial output;
- XXE, external DTD and entity-expansion probes cannot access external state;
- canonicalisation is byte-stable.

### Cross-surface tests

- Java builder and Maven goal return the same status, diagnostics and output;
- builder writer output loads through the real `FluxtionSpring` path;
- the starter JavaScript parser/writer passes the shared conformance corpus;
- the Stitch adapter produces its prior semantic document through the shared writer;
- a real resolver-produced application builds and reproduces the pinned audit expectation;
- dependency-tree verification proves no new Spring transitive dependency;
- shipped builder classes have Java 8 bytecode/API compatibility and the normal playground loads and
  compiles under CheerpJ with the parser classes present but unused;
- generated applications still run with only `fluxtion-runtime`.

## 11. Delivery slices

1. **Model and conformance corpus** — Java-8-compatible component/result types, Spring document types,
   shared fixtures and bytecode/API compatibility checks.
2. **Resolver port** — port the reviewed Python behaviour, then retire duplicated validation and id
   allocation in the prototype.
3. **JDK parser and canonical writer** — desktop build/API surface, supported subset, exposed-field
   references, security tests, plus the CheerpJ load-and-compile smoke described in §5.
4. **Builder API and Maven goal** — one implementation, atomic output, compiler diagnostics transport.
5. **Catalogue producer** — generated and read-back-validated manifest metadata.
6. **Starter and Stitch adoption** — shared fixtures, exposed-field edges, rename propagation, and
   byte-identical Stitch output or a deliberate reviewed re-baseline.
7. **End-to-end release gate** — catalogue → resolution → XML → `FluxtionSpring` → generated processor →
   audit expectation, plus builder/runtime dependency checks.

Each slice leaves one authority. The Python prototype remains evidence until the Java end-to-end gate
passes, then becomes a historical reference rather than a second supported resolver.

## Acceptance

The work is complete only when:

- one builder-jar result decides selection, bindings, constructibility, order and identity;
- its shipped API and implementation remain Java 8 compatible and the builder jar still loads and
  performs a normal playground compile under CheerpJ;
- every renderer consumes that result without revalidation;
- the supported Spring subset is explicit, securely parsed and canonically written without adding a
  Spring transitive dependency;
- the starter and builder agree on shared Spring-document fixtures, including exposed-field references;
- component catalogues are generated and read back by the build toolchain rather than hand-authored;
- the round-48 result remains byte-identical and its real application evidence remains equal;
- ambiguity and invalid inputs fail closed with structured compiler diagnostics;
- generated applications retain their runtime-only deployment footprint.

## Explicit non-goals

- implementing the whole Spring Beans language;
- moving starter UI state into the builder;
- asking a model to choose when declared metadata and policy already decide;
- solving goal-to-formal-requirements translation;
- specifying modes 2/3 component authoring;
- changing runtime dispatch or audit semantics;
- making the analyser a build tool.
