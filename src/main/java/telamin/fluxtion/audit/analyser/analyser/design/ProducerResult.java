package telamin.fluxtion.audit.analyser.analyser.design;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import java.util.*;

/** One explicitly opened producer result. Findings have no dependency on a log record. */
public record ProducerResult(String file, String stage, String sourceRoot, String inputHash,
                             List<Finding> findings, Map<String, Object> receipt,
                             Map<String, Object> inputChecks) {
    public record Finding(String code, String severity, String message, String why, String fix,
                          Map<String, Object> element, Map<String, Object> sourceRef, List<Object> related) {
        public String kind() { return str(element.get("kind")); }
        public String field(String name) { return str(element.get(name)); }
    }
    public static ProducerResult parse(String file, String text, Map<String, Object> receipt, Map<String, Object> checks) {
        Map<String, Object> outer = object(Json.parse(text));
        String stage;
        Map<String, Object> report;
        if (outer.containsKey("diagnosticReport")) {
            report = object(outer.get("diagnosticReport"));
            if (outer.get("valid") instanceof Boolean) { stage = "validate"; version(outer.get("contractVersion"), "contractVersion"); }
            else if (outer.get("classes") instanceof Map) { stage = "regenerate"; version(outer.get("schemaVersion"), "schemaVersion"); }
            else throw new IllegalArgumentException("unrecognised producer result wrapper");
        } else if (outer.containsKey("diagnostics")) { stage = "build"; report = outer; }
        else throw new IllegalArgumentException("unrecognised producer result wrapper");
        version(report.get("diagnosticsVersion"), "diagnosticsVersion");
        if (outer.containsKey("schemaVersion")) version(outer.get("schemaVersion"), "schemaVersion");
        if (!(report.get("diagnostics") instanceof List<?> items)) throw new IllegalArgumentException("diagnostics must be an array");
        List<Finding> findings = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> d = object(item), element = object(d.get("element"));
            for (String required : List.of("code", "severity", "message")) if (str(d.get(required)).isBlank()) throw new IllegalArgumentException("diagnostic is missing " + required);
            if (str(element.get("kind")).isBlank()) throw new IllegalArgumentException("diagnostic element has no kind");
            findings.add(new Finding(str(d.get("code")), str(d.get("severity")), str(d.get("message")),
                    str(d.get("why")), str(d.get("suggestedFix")), Collections.unmodifiableMap(element),
                    Collections.unmodifiableMap(object(d.get("sourceRef"))), d.get("related") instanceof List<?> r ? List.copyOf(r) : List.of()));
        }
        return new ProducerResult(file, stage, str(report.get("sourceRoot")), str(outer.get("inputHash")),
                List.copyOf(findings), Collections.unmodifiableMap(new LinkedHashMap<>(receipt)), Map.copyOf(checks));
    }
    public Map<String, Object> relationship(DesignDocument design) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> stageReceipt = object(object(receipt.get("stages")).get(stage));
        Map<String, Object> inputs = comparableHashes(stageReceipt);
        String hash = inputHash.isEmpty() ? str(inputs.get("xmlHash")) : inputHash;
        String status = design == null || hash.isEmpty() ? "unknown"
                : sameHash(hash, design.revision()) ? "input-current" : "input-stale";
        out.put("relationship", status);
        out.put("scope", "XML input only; relationship to the loaded log is unverified");
        out.put("stage", stage);
        out.put("receipt", stageReceipt);
        out.put("hashBasis", stageReceipt.get("outputs") instanceof Map ? "outputs" : "inputs");
        Map<String, Object> checks = new LinkedHashMap<>(inputChecks);
        checks.put("sourceHash", hashState(str(inputs.get("sourceHash")), str(inputChecks.get("currentSourceHash"))));
        checks.put("recordHash", hashState(str(inputs.get("recordHash")), str(inputChecks.get("currentRecordHash"))));
        out.put("inputChecks", checks);
        out.put("logRelationship", "unverified");
        Map<String, Object> build = object(object(receipt.get("stages")).get("build"));
        out.put("build", build);
        Map<String, Object> buildInputs = comparableHashes(build);
        checks.put("buildSourceHash", hashState(str(buildInputs.get("sourceHash")), str(inputChecks.get("currentSourceHash"))));
        var freshness = telamin.fluxtion.audit.analyser.analyser.core.FileObservation.compare(
                telamin.fluxtion.audit.analyser.analyser.core.FileObservation.observations(inputChecks.get("observedInputs")));
        out.put("freshness", freshness);
        checks.put("scope", "hash comparisons as of intake at " + inputChecks.getOrDefault("checkedAt", "unknown time"));
        if ("changed-on-disk".equals(freshness.get("state"))) {
            for (String key : List.of("sourceHash", "recordHash", "buildSourceHash")) checks.put(key,"stale-check — reopen diagnostics");
            out.put("resultStatus", "loaded result or inputs changed on disk — use open {discover: diagnostics}, then explicitly reopen diagnostics");
            return out;
        }
        if (stage.equals("build") && Boolean.FALSE.equals(build.get("compilerRan"))) {
            out.put("resultStatus", "sidecar predates the last attempt");
        } else if (!stageReceipt.isEmpty() && !str(stageReceipt.get("outcome")).equals("ok")) {
            out.put("resultStatus", "attempt outcome: " + str(stageReceipt.get("outcome")) + "; input match does not mean success");
        } else out.put("resultStatus", stageReceipt.isEmpty() ? "attempt unknown" : "see input checks; XML match alone does not establish result freshness");
        return out;
    }
    public static boolean sameHash(String a, String b) { return a.replaceFirst("^sha256:", "").equals(b.replaceFirst("^sha256:", "")); }
    public String description(DesignDocument design) {
        Map<String, Object> state = relationship(design), checks = object(state.get("inputChecks"));
        String note = "XML input: " + state.get("relationship") + " · Java source at intake: " + checks.get("sourceHash")
                + " · authoring record: " + checks.get("recordHash") + "\n" + state.get("resultStatus")
                + " · loaded run: unverified";
        Map<String, Object> build = object(state.get("build"));
        if (!build.isEmpty()) note += "\nLoaded build (as of intake): " + str(build.get("outcome")) + " · compiler ran: " + str(build.get("compilerRan"))
                + " · build Java source: " + checks.get("buildSourceHash");
        if (checks.containsKey("checkedAt")) note += "\nInputs read at " + checks.get("checkedAt") + "; reopen diagnostics after a source or build change.";
        return note;
    }
    private static Map<String, Object> comparableHashes(Map<String, Object> stage) {
        return object(stage.get(stage.get("outputs") instanceof Map ? "outputs" : "inputs"));
    }
    private static String hashState(String expected, String current) {
        return expected.isBlank() || current.isBlank() ? "unknown" : sameHash(expected, current) ? "match" : "mismatch";
    }
    public static String str(Object o) { return o == null ? "" : o.toString(); }
    public static Map<String, Object> object(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) map.forEach((k, v) -> m.put(k.toString(), v));
        return m;
    }
    public static void version(Object version, String name) {
        if (!str(version).equals("1.0")) throw new IllegalArgumentException("unsupported " + name + ": " + version);
    }
}
