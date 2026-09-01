
## Structural requirements — these are checked mechanically

Round 13 accepted a design that reported twenty-six nodes and contained none: a single class that wrote
its own node names into the log as string literals. Its log was perfect in all 28 cycles, necessarily,
because the thing doing the reporting was the thing being reported on. These rules exist to make that
impossible, and they apply identically however you build the engine.

1. **One class per node.** Every node is its own public class in package `com.acme.surveillance.node`.
   Event/DTO/record types do not count as nodes and do not belong there.

2. **No node names as literals.** The code that emits the audit log must contain **no string literal
   equal to a node's name**. Node identity comes from the node itself — its class name, or a name the
   node declares about itself. `path.add("orderBook")` is exactly what this forbids.

3. **`path` is derived, not written.** The `path` and `pathLength` of a cycle must be assembled by the
   dispatch mechanism from the nodes it **actually invoked** in that cycle. It must be impossible for
   `path` to disagree with what ran, because nothing types it by hand.

4. **Adding a node must not require editing the reporting code.** If a new node were added tomorrow, the
   audit log should describe it without a single edit to the emitter.

**How this is checked.** After you finish, an automated check greps your emitter for node-name literals
and confirms each node is its own class. A design that fails it has not met the specification, however
many tests pass — so satisfy it structurally rather than by renaming things.
