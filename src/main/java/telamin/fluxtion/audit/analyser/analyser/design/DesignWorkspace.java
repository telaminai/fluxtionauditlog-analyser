package telamin.fluxtion.audit.analyser.analyser.design;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import static telamin.fluxtion.audit.analyser.analyser.design.ProducerResult.*;

/** Read adapter. The session graph owns state; this class reads files and reports completed facts. */
public final class DesignWorkspace {
    public static final String SHAPES = "{file}, {file,line}, {line}, {bean}, {file,bean}, {fqn}, {fqn,method}";
    private final Supplier<DesignFiles> files;
    public interface State { String path(); DesignDocument document(); long generation(); }
    public record Snapshot(String path, DesignDocument document, long generation) implements State { }
    @FunctionalInterface public interface Read<T> { T run(DesignWorkspace workspace) throws IOException; }
    public record Prepared<T>(T value, List<Object> facts, String error) { }
    /** Called off the UI thread with immutable inputs; only the caller may commit the returned facts. */
    public static <T> Prepared<T> prepare(DesignFiles files, Snapshot state, Read<T> read) {
        List<Object> facts = new ArrayList<>();
        var workspace = new DesignWorkspace(() -> files, () -> state, facts::add);
        try { return new Prepared<>(read.run(workspace), List.copyOf(facts), ""); }
        catch (IOException | IllegalArgumentException e) { return new Prepared<>(null, List.copyOf(facts), e.getMessage()); }
    }
    private final Supplier<? extends State> state;
    private final Consumer<Object> dispatch;
    public DesignWorkspace(Supplier<DesignFiles> files, Supplier<? extends State> state, Consumer<Object> dispatch) {
        this.files = files; this.state = state; this.dispatch = dispatch;
    }
    public record View(String file, String text, String mode, int line, String bean, DesignDocument document, String problem) {
        public View(String file, String text, String mode, int line, String bean, DesignDocument document) {
            this(file, text, mode, line, bean, document, "");
        }
        public Map<String, Object> echo() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", file); m.put("mode", mode); m.put("line", line);
            if (bean != null) m.put("bean", bean);
            if (document != null) m.put("revision", document.revision());
            else if (mode.equals("DESIGN")) m.put("revision", DesignFiles.sha256(text));
            if (!problem.isEmpty()) m.put("parseError", problem);
            m.put("relationship", "unverified");
            return m;
        }
    }
    public View open(String requested) throws IOException {
        Path path = files.get().resolve(requested);
        if (!path.toString().endsWith(".xml")) throw new IOException("a design must be an XML file");
        var fact = read(files.get(), path.toString(), true, state.get().generation());
        dispatch.accept(fact);
        if (fact.document() == null) {
            if (state.get().document() != null && path.toString().equals(state.get().path())) return view(state.get().document(), 1, null);
            return new View(path.toString(), files.get().read(path), "DESIGN", 1, null, null, fact.error());
        }
        return view(fact.document(), 1, null);
    }
    public static DesignEvents.ReadCompleted read(DesignFiles files, String path, boolean opening, long generation) {
        String canonical = path;
        try {
            Path resolved = files.resolve(path);
            canonical = resolved.toString();
            return new DesignEvents.ReadCompleted(resolved.toString(), DesignDocument.parse(resolved.toString(), files.read(resolved)), "", opening, generation);
        } catch (IOException | DesignDocument.ParseFailure e) { return new DesignEvents.ReadCompleted(canonical, null, e.getMessage(), opening, generation); }
    }
    public void refreshed(DesignEvents.ReadCompleted fact) { dispatch.accept(fact); }
    public void clear(String reason) { dispatch.accept(new DesignEvents.Cleared(reason)); }

    public View source(Map<String, Object> params) throws IOException {
        Set<String> keys = params.keySet();
        if (!List.of(Set.of("file"), Set.of("file", "line"), Set.of("line"), Set.of("bean"), Set.of("file", "bean"), Set.of("fqn"), Set.of("fqn", "method")).contains(keys))
            throw new IOException("accepted source selectors: " + SHAPES);
        for (String key : keys) if (params.get(key) == null || str(params.get(key)).isBlank()) throw new IOException("empty source selector: " + key);
        DesignFiles access = files.get();
        Path path = keys.contains("fqn") ? access.fqn(str(params.get("fqn")))
                : access.resolve(keys.contains("file") ? str(params.get("file")) : state.get().path());
        String text = access.read(path);
        int line = 1;
        if (keys.contains("line")) {
            Object n = params.get("line");
            if (!(n instanceof Number num) || num.doubleValue() != num.intValue() || num.intValue() < 1) throw new IOException("line must be a positive integer");
            line = ((Number)n).intValue();
        }
        if (path.toString().endsWith(".java")) {
            if (keys.contains("bean")) throw new IOException("bean selects XML only");
            if (keys.contains("method")) {
                int offset = SourceNavigation.methodDeclOffset(text, str(params.get("method")));
                if (offset < 0) throw new IOException("method not found: " + params.get("method"));
                line = DesignDocument.lineAt(text, offset);
            }
            if (line > 1 + text.chars().filter(c -> c == '\n').count()) throw new IOException("line exceeds source length");
            return new View(path.toString(), text, "NODE", line, null, null);
        }
        if (!path.toString().endsWith(".xml")) throw new IOException("source supports XML and Java files");
        DesignDocument doc;
        try { doc = DesignDocument.parse(path.toString(), text); }
        catch (DesignDocument.ParseFailure e) {
            if (path.toString().equals(state.get().path())) {
                dispatch.accept(new DesignEvents.ReadCompleted(path.toString(), null, e.getMessage(), false, state.get().generation()));
            }
            if (keys.contains("bean")) throw new IOException("bean index unavailable: " + e.getMessage());
            if (line > 1 + text.chars().filter(c -> c == '\n').count()) throw new IOException("line exceeds design length");
            return new View(path.toString(), text, "DESIGN", line, null, null, e.getMessage());
        }
        if (path.toString().equals(state.get().path())) dispatch.accept(new DesignEvents.ReadCompleted(path.toString(), doc, "", false, state.get().generation()));
        String bean = keys.contains("bean") ? str(params.get("bean")) : null;
        if (bean != null) {
            var matches = doc.beans(bean);
            if (matches.isEmpty()) throw new IOException("unknown bean '" + bean + "'; available ids: " + doc.beanIds());
            if (matches.size() > 1) throw new IOException("ambiguous bean '" + bean + "'; lines: " + matches.stream().map(DesignDocument.Element::line).toList());
            line = matches.getFirst().line();
        }
        if (line > doc.lines()) throw new IOException("line exceeds design length");
        return view(doc, line, bean);
    }
    public static View view(DesignDocument doc, int line, String bean) { return new View(doc.file(), doc.text(), "DESIGN", line, bean, doc); }

    public ProducerResult diagnostics(String requested) throws IOException {
        long generation = state.get().generation();
        try {
            DesignFiles access = files.get();
            Path path = access.resolve(requested);
            Map<String, Object> receipt = new LinkedHashMap<>(), checks = new LinkedHashMap<>();
            var observed = new ArrayList<Map<String,Object>>();
            observed.add(telamin.fluxtion.audit.analyser.analyser.core.FileObservation.capture(path));
            String resultText = access.read(path);
            ProducerResult envelope = ProducerResult.parse(path.toString(), resultText, Map.of(), Map.of());
            checks.put("checkedAt", java.time.Instant.now().toString());
            // Receipt discovery is bounded to the declared project, never a search up arbitrary parents.
            if (access.project() != null) {
                try {
                    Path receiptPath = access.resolve(access.project().resolve("target/fluxtion-run.json").toString());
                    observed.add(telamin.fluxtion.audit.analyser.analyser.core.FileObservation.capture(receiptPath));
                    receipt = object(Json.parse(access.read(receiptPath)));
                    version(receipt.get("schemaVersion"), "receipt schemaVersion");
                } catch (IOException | IllegalArgumentException e) { receipt = Map.of(); checks.put("receipt", "unavailable: " + e.getMessage()); }
                try {
                    Path recordPath = access.resolve(access.project().resolve("fluxtion-authoring.json").toString());
                    observed.add(telamin.fluxtion.audit.analyser.analyser.core.FileObservation.capture(recordPath));
                    String recordText = access.read(recordPath);
                    var record = object(Json.parse(recordText));
                    Map<String, Object> options = object(object(object(receipt.get("stages")).get(envelope.stage())).get("options"));
                    String sourceRoot = str(options.getOrDefault("sourceRoot", record.get("sourceRoot")));
                    if (!sourceRoot.isBlank()) {
                        Path root = access.directory(access.project().resolve(sourceRoot).normalize());
                        StringBuilder sourceHashes = new StringBuilder();
                        try (var paths = Files.walk(root)) {
                            var entries = paths.sorted().limit(20_001).toList();
                            if (entries.size() > 20_000) throw new IOException("source metadata exceeds 20,000 entries");
                            for (Path entry : entries) if (Files.isDirectory(entry))
                                observed.add(telamin.fluxtion.audit.analyser.analyser.core.FileObservation.capture(entry));
                            var sources = entries.stream().filter(p -> p.toString().endsWith(".java")).toList();
                            if (sources.size() > 10_000) throw new IOException("source hash exceeds 10,000 files; comparison unavailable");
                            for (Path java : sources) {
                                observed.add(telamin.fluxtion.audit.analyser.analyser.core.FileObservation.capture(java));
                                if (sourceHashes.length() > MAX_HASH_INPUT) throw new IOException("source hash input exceeds limit");
                                sourceHashes.append(root.relativize(java).toString().replace('\\', '/')).append('\0')
                                        .append("sha256:").append(DesignFiles.sha256(access.read(java))).append('\n');
                            }
                        }
                        checks.put("currentSourceHash", "sha256:" + DesignFiles.sha256(sourceHashes.toString()));
                    }
                    checks.put("currentRecordHash", "sha256:" + DesignFiles.sha256(recordText));
                } catch (IOException | IllegalArgumentException e) { checks.put("sourceAndRecord", "unavailable: " + e.getMessage()); }
            }
            checks.put("observedInputs", List.copyOf(observed));
            ProducerResult result = ProducerResult.parse(path.toString(), resultText, receipt, checks);
            dispatch.accept(new DesignEvents.ResultReadCompleted(result, "", generation));
            return result;
        } catch (IOException | IllegalArgumentException | java.io.UncheckedIOException e) {
            dispatch.accept(new DesignEvents.ResultReadCompleted(null, e.getMessage(), generation));
            throw new IOException(e.getMessage());
        }
    }
    private static final int MAX_HASH_INPUT = 2 * 1024 * 1024;
}
