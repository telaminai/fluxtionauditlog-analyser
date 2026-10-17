# Getting a Mongoose-hosted processor's audit log into the analyser

[Producing an audit log](producing-a-log.md) covers an **embedded** processor. There, your code attaches the audit
listener and sets the level. In a **Mongoose server**, the server does both. This page explains how a hosted
processor's records end up in a `---`-separated text file that the analyser opens.

No Mongoose docs page describes this path end to end. This page is built from Mongoose's configuration docs and its
source, as listed under [Sources](#sources). Where the sources do not settle a step, it is marked **To confirm**
rather than filled in. The versions read are Mongoose `main` at `6e8c7ce7` (newest tag `v1.0.32`) and
mongoose-plugins `main` at `ace31f30` (release `v1.0.45`), both on 2026-10-17.

!!! note "The analyser explains this; it does not do it"
    The analyser never starts, stops or reconfigures a server. Every step below is one you take in your own
    configuration and tools.

## What must be true, hosted or not

The three load-bearing pieces from [Producing an audit log](producing-a-log.md#the-shortest-complete-example) still
apply:

1. **Your nodes can audit.** They extend `EventLogNode` and call `auditLog.info(key, value)`.
2. **The processor has the audit auditor installed.** For a processor generated at build time, your
   `FluxtionGraphBuilder` calls `cfg.addEventAudit(...)`. Open the processor's `.graphml` and the Project panel's
   Graph section tells you whether it did.
3. **Something writes each record to a file with a `---` separator.** `record.toString()` does not include the
   separator ([Format specification §1](format-spec.md)).

!!! question "To confirm"
    Does Mongoose install the audit auditor itself for a processor declared as a `customHandler`
    (an `ObjectEventHandlerNode`), or must that handler's own build install it? The sources read here do not say.

In a server, piece 3 is the server's job. The rest of this page is about piece 3 and the level.

## The audit level

Mongoose sets each processor's audit level from configuration:

- **Per handler**: add `logLevel` beside the handler under `eventHandlers` in the YAML. This is shown in the Mongoose
  *Configuration and composition* page and its file-and-memory-feeds example:

    ```yaml
    eventHandlers:
      - agentName: processor-agent
        eventHandlers:
          example-processor:
            customHandler: !!com.acme.demo.MyHandler { }
            logLevel: INFO
    ```

- **Per agent group**: a `logLevel` on the group entry. It is the default for the group's handlers.
- **Neither set**: `INFO`. Mongoose's `ServerConfigurator` applies the handler's level, then the group's, then `INFO`.
- **At runtime**: `setAuditLogLevel(...)` on the processor, as in the Mongoose *Deployment Guide* ▸ *Logging*. The
  `svc-admin-web` plugin exposes the same call as `POST /api/processors/{group}/{name}/audit/level` with a body of
  `{"level": "DEBUG"}`. It accepts `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` and `NONE`.

!!! warning "Expect a control record"
    `ServerConfigurator` attaches the listener first and then sets the level. *Producing an audit log* warns that
    this order puts the level change's own `EventLogControlEvent` record into the stream. When a log contains
    nothing else, the analyser names that as a finding.

    **To confirm:** the level is set before the processor's `init()`. Whether the control record is actually
    emitted at that point has not been checked against a running server.

## Route 1: your own listener at boot

`MongooseServer.bootServer(Reader, LogRecordListener)` takes the listener that the server attaches to every
processor it creates. A listener that writes the separator itself produces exactly the file the analyser reads,
just as the embedded example does:

```java
Path out = Path.of("audit/server-audit.yaml");
Files.createDirectories(out.getParent());
BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

LogRecordListener toFile = record -> {
    synchronized (w) {                        // see "To confirm" below
        try {
            w.write("---");
            w.newLine();
            w.write(record.toString());
            w.newLine();
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
};

MongooseServer server = MongooseServer.bootServer(new FileReader("server.yaml"), toFile);
```

Without this argument, the server's default listener logs each record at `INFO` through its own logger, with no
`---` separator. That output goes to the application log, not to a file the analyser can read as a container.

!!! question "To confirm"
    - **Threads.** A processor calls its listener during its own event cycle. The Mongoose *Threading model* page
      says that cycle runs on the processor's agent thread. One listener shared by processors in several agent
      groups can therefore be called from several threads, so the sketch serialises its writes. Nothing read here
      states whether Mongoose already does that. Writing one file per processor avoids the question.
    - **Record reuse.** The capture service's javadoc says the runtime assembles each record's text once, in the
      record's own buffer. Take `record.toString()` inside the callback, and do not keep the record object.
    - **An appender instead.** You could route the default listener's logger to a file appender whose pattern
      writes `---` before each message. That pattern is not documented anywhere read here.

## Route 2: Mongoose audit capture, exported by svc-admin-web

Mongoose has an opt-in **audit capture** service, configured under `performanceMonitoring.auditCapture` and present
in releases since `v1.0.16`. It records a processor's audit stream into a Chronicle queue, one YAML document per
excerpt. Its settings, from the `AuditCaptureConfig` javadoc:

```yaml
performanceMonitoring:
  enabled: true
  auditCapture:
    enabled: true            # default false: no capture service is installed
    backend: chronicle       # the only backend implemented
    rollSize: 64m
    retainHours: 24          # older files are pruned
    directory: ./audit
    autoStart: [ example-processor ]   # processors captured from boot; [] = none
```

Recording is **off for each processor** until it is started. You can start it with `autoStart`, the `audit.start`
admin command, or `POST /api/audit/{processor}/start` on `svc-admin-web`. Starting capture does not replace the
listener from route 1: the capture service puts a fan-out in front of it and restores it on stop.

The analyser does not read the Chronicle files. *Producing an audit log* explains that it reads the text file sink.
`svc-admin-web` converts them:

1. List the captures. `GET /api/audit/files` returns each one's `id`, `processorName`, `path`, `recordCount` and
   `isLive`. The `audit.list` admin command prints the same captures with their paths, but not their ids.
2. Download one with `GET /api/audit/file/{id}/export`. The default format, `yaml`, writes `---`-separated
   documents. The source calls this the "drop-in for the analyser reader"; `?format=jsonl` writes one JSON object
   per line instead.
3. Open the downloaded `.yaml` with **Audit log ▸ Open log…**.

Since mongoose-plugins `v1.0.44`, the export terminates the last record. Since `v1.0.45`, it also escapes any line
inside a record that a reader would take for a separator, so text an operator typed cannot split one record into
two. The export writes no stream-end marker ([§1a](format-spec.md#1a-saying-the-file-is-whole-format-11-additive)),
so the analyser reports whether the file is whole as *unknown*.

!!! question "To confirm"
    The `/api/audit/...` routes do not appear in `svc-admin-web`'s documented HTTP table. Which authentication and
    CSRF rules apply to them, and whether they are a supported surface or an internal one, needs the plugin's
    owner. The `audit.*` admin commands are registered by Mongoose's own `MongooseServerAdmin`.

## Pair it with the graph

The analyser explains a log best next to the processor's `.graphml` from the same build ([Topology](user-guide/topology.md)).
If you deploy that file beside the processor class, at `<class-FQN-with-slashes>.graphml`, `svc-admin-web` serves it
at `GET /api/processors/{group}/{name}/graphml`.

## Sources

- This site: [Producing an audit log](producing-a-log.md), [Format specification](format-spec.md) §1, §1a and §2.
- Fluxtion `docs/claude.txt` ▸ *Build-time generation*, which shows `cfg.addEventAudit(LogLevel.INFO)`, and
  *Browser-runtime constraint*, which notes that `setAuditLogProcessor` replaces the previous listener. Fluxtion
  `how-to/audit-logging-dataflow.md` ▸ *Enabling and configuring logging*.
- Mongoose docs: `overview/configuration-and-composition.md`, `guide/file-and-memory-feeds-yaml-example.md` (the
  handler `logLevel`), `architecture/deployment.md` ▸ *Logging* (`setAuditLogLevel`), and
  `architecture/threading-model.md`.
- Mongoose source: `MongooseServer.bootServer(Reader, LogRecordListener)`, `internal/ServerConfigurator.java` (the
  level defaults and the attach-then-set order), `config/AuditCaptureConfig.java`,
  `internal/ChronicleAuditCaptureService.java` (the class javadoc) and
  `service/servercontrol/MongooseServerAdmin.java` (the `audit.*` commands).
- mongoose-plugins source: `service/svc-admin-web/.../WebAdminService.java` (the audit routes, `handleAuditExport`
  and `YamlContainerWriter`), and `README.md` (the graphml resource convention).
