# Log-source plugins

!!! info "Writing a reader?"
    The records a reader hands over are defined by the **[format specification](../format-spec.md)**,
    and the conformance fixtures there are what the analyser will do with them. Run your reader through
    them before anything else.

The analyser understands one thing — the Fluxtion audit record — and **containers are plugins**. A
parquet file, a Chronicle queue or a database table holding audit records differs only in how bytes
become records; everything above that (index, filters, topology, graphs, the verbs, MCP) is
container-blind.

## The trust boundary, plainly

A plugin is a jar **you** place in `~/.fluxtion-analyser/plugins/`. Installing a jar is **arbitrary
code execution** — a plugin can do anything this application can. Install only jars you trust.
Nothing is bundled or downloaded; without plugins the analyser is byte-identical to a plain build.
A plugin can only ever be a *reader*: it cannot add verbs to the action socket.

## Writing a reader

Implement `AuditLogReader` and declare it as a `java.util.ServiceLoader` service
(`META-INF/services/telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader`):

- **`formatId()` / `displayName()`** — identity; `open {format: "yours"}` forces your reader.
- **`canOpen(Path)`** — claim your container, by content where possible.
- **`timeBase()`** — **mandatory**: the epoch unit, zone and clock source of the records you emit.
  A reader without one is refused at load. Your parquet reader *knows* its epoch unit — declare it.
- **`capabilities()`** — `follow` / `byteAnchors` / `randomAccess`. Say no honestly: features degrade
  loudly where you do (a source without byte anchors is anchored by `recordIndex`, and `read`/`goto`
  say so), and nothing is fabricated where you can't deliver.
- **`read(source, sink)`** — stream each record's **canonical text** (the standard `eventLogRecord`
  YAML shape) in **container order**. The analyser parses, indexes and stores; you never touch Swing,
  the index, or another plugin.

Your jar loads in an isolated classloader: the SPI package and the JDK come from the application
(so the hand-off stays typed), everything else resolves from your jar first — your dependency tree
cannot fight another plugin's, or the analyser's (which has almost none).

## Installing

Drop the jar in `~/.fluxtion-analyser/plugins/` and restart. **Settings ▸ Plugins** lists what
loaded — every reader with its time base and capabilities, and a note for anything that failed or
was refused.
