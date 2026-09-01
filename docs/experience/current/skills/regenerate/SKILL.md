---
name: regenerate
description: Regenerate the processor after changing the graph, and check the generated output before trusting a green build.
x-analyser-min-version: 1.12.0
---

# Regenerate after a graph change

Running the project needs **no key**. Regenerating needs one **only when the compiler is not on the
classpath** — with `fluxtion-builder` present the generation is local and no key is involved. Check the
project's own dependencies before concluding a build failure is a licence problem; measured rounds have
produced confident, wrong reports in both directions.

## Check the key the way the BUILD resolves it

The build reads, in this order:

```
-Dfluxtion.apiKey=…                system property   [WINS when set]
~/.fluxtion/fluxtion.apiKeyFile    apiKey=…          [used when the property is absent]
```

**`FLUXTION_API_KEY` is not read by the build.** A preflight script that checks that variable can report
success on a value the build never receives, so check the file:

```bash
grep -s '^apiKey=' ~/.fluxtion/fluxtion.apiKeyFile >/dev/null \
  && echo "key file present" || echo "NO key file — regeneration will fail"
```

## Regenerate

**Use whatever this project actually binds.** Check `pom.xml` first — the `fluxtion-maven-plugin` goal
and the phase it is bound to:

```bash
# the common case: the scan goal is bound to process-classes, so this is all it takes
mvn process-classes

# only if this project defines such a profile (many do not):
./mvnw -Pgenerate-fluxtion package
```

## ALWAYS read the generated source afterwards — a green build is not a correct graph

This is the step that catches what nothing else does. `mvn process-classes` can report SUCCESS having
produced a processor that silently drops values, because `compile` runs **before** `process-classes`, so
the file just written is not compiled until the next run.

Measured example: a node reachable only through a `List` constructor argument was regenerated as
`Arrays.asList(new ZoneLimit(), new ZoneLimit())` — **default-constructed, with the operator limits
thrown away** — and the build passed. Reading the generated constructor is what found it.

```bash
# does the generated processor construct your nodes with the values the builder passed?
grep -nE "new [a-z0-9.]*\.[A-Z][A-Za-z]*\(" src/main/java/**/generated/*.java | head -20
```

**If a generation goes bad, the next build fails on the OLD output.** Delete the generated directories
before re-running, or the error will point at generated code rather than at your builder:

```bash
rm -rf src/main/java/**/generated src/main/resources/**/generated && mvn process-classes
```

## If the build stops at `process-classes`, READ THE ERROR — the key is only one of the causes

The `fluxtion-maven-plugin:scan` goal is bound to that phase, so **everything** the generator can object
to fails there. An earlier version of this skill said the key was why; that is wrong often enough to send
you hunting a licence problem that does not exist.

**Check which route this project uses before reading the table** — a `nodeBeans` cause cannot apply to a
project whose goal is `scan`.

| The error says | Cause |
|---|---|
| missing/invalid API key | the key — check the file above |
| `cannot find matching constructor for: Field{…} failed to match for these fields:[…]` | a node field is **not reachable from a constructor**. Mark your node's own state `transient` — see `CLAUDE.md` §4.3. The message names constructors; the fix is usually `transient`. |
| a node you added is absent from the generated output | the bean is not in `fluxtionSpringConfig.nodeBeans` |

## Confirm your change is in the graph

Both generated copies are rewritten. Read one — this is the fastest way to know a node was actually wired
rather than silently dropped for not being in `nodeBeans`:

```bash
grep -n "YourNodeName" src/main/java/com/example/myapp/generated/MarketProcessor.java
grep -n "YourNodeName" src/main/resources/com/example/myapp/generated/MarketProcessor.graphml
```

Absent from both means the bean is not in `fluxtionSpringConfig.nodeBeans`.
