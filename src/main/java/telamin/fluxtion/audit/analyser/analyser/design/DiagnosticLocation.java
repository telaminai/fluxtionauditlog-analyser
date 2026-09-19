package telamin.fluxtion.audit.analyser.analyser.design;

import java.io.IOException;
import java.util.*;
import static telamin.fluxtion.audit.analyser.analyser.design.ProducerResult.*;

/** Pure location policy. Offending declaration first; ambiguity never selects an arbitrary match. */
public record DiagnosticLocation(String file, int line, String mode, boolean approximate, String reason, List<Integer> candidates) {
    public boolean available() { return file != null; }
    public static DiagnosticLocation unavailable(String reason) { return new DiagnosticLocation(null, 0, "", false, reason, List.of()); }
    public Map<String, Object> echo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available()); m.put("reason", reason); m.put("approximate", approximate);
        if (available()) { m.put("file", file); m.put("line", line); m.put("mode", mode); }
        if (!candidates.isEmpty()) m.put("candidateLines", candidates);
        m.put("relationship", "unverified");
        return m;
    }
    public static DiagnosticLocation resolve(ProducerResult result, ProducerResult.Finding finding, DesignDocument design, DesignFiles files) {
        return resolve(result, finding, design, files, design == null ? null : design.file());
    }
    public static DiagnosticLocation resolve(ProducerResult result, ProducerResult.Finding finding, DesignDocument design, DesignFiles files, String designPath) {
        if (!finding.sourceRef().isEmpty()) {
            try {
                var path = files.reportLocation(result.sourceRoot(), str(finding.sourceRef().get("file")));
                String name = path.toString();
                if (!name.endsWith(".xml") && !name.endsWith(".java")) return unavailable("location is not XML or Java");
                Object line = finding.sourceRef().get("line");
                if (!(line instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1) return unavailable("source location has no valid line");
                return new DiagnosticLocation(name, ((Number)line).intValue(), name.endsWith(".xml") ? "DESIGN" : "NODE", false, "producer source location", List.of());
            } catch (IOException | IllegalArgumentException e) { return unavailable("location outside authorised roots or unavailable: " + e.getMessage()); }
        }
        if (finding.kind().equals("SOURCE_MEMBER")) {
            try {
                var path = files.fqn(finding.field("className"));
                String source = files.read(path), member = finding.field("member");
                String name = member.replaceFirst("^(field:|implements:)", "").replaceFirst("\\(.*", "");
                int off = name.isBlank() ? -1 : source.indexOf(name);
                return new DiagnosticLocation(path.toString(), off < 0 ? 1 : DesignDocument.lineAt(source, off), "NODE", true,
                        off < 0 ? "class location; member unresolved" : "member name match; not an exact producer location", List.of());
            } catch (IOException e) { return unavailable("class not under an authorised root"); }
        }
        if (design == null) {
            if (finding.kind().equals("SPRING_DOCUMENT") && designPath != null) {
                try { return new DiagnosticLocation(files.resolve(designPath).toString(), 1, "DESIGN", true, finding.message(), List.of()); }
                catch (IOException e) { return unavailable(e.getMessage()); }
            }
            return unavailable("no parsed session design is available");
        }
        return switch (finding.kind()) {
            case "NODE" -> choose(design, design.beans(finding.field("nodeName")), false, "no bean with this node name — not a Spring-authored node?");
            case "SPRING_BEAN" -> {
                List<DesignDocument.Element> beans = design.beans(finding.field("beanName"));
                // A duplicate declaration must remain ambiguous even when one is earlier in the file.
                if (beans.isEmpty()) yield choose(design, design.entries("nodeBeans").stream().filter(e -> e.contains(finding.field("beanName"))).toList(), true, "bean not declared in this design");
                if (finding.code().equals("SPRING_DANGLING_BEAN_REF") && beans.size() == 1) {
                    Set<String> targets = new HashSet<>();
                    for (Object related : finding.related()) {
                        var r = object(related);
                        for (String key : List.of("beanName", "nodeName", "name", "target")) if (!str(r.get(key)).isBlank()) targets.add(str(r.get(key)));
                    }
                    var refs = beans.getFirst().descendants().stream().filter(e -> (e.name().equals("ref") || !e.attr("ref").isBlank()) && targets.stream().anyMatch(e::contains)).toList();
                    if (!refs.isEmpty()) yield choose(design, refs, false, "reference not found");
                }
                yield choose(design, beans, false, "bean not declared in this design");
            }
            case "SPRING_SERVICE_BINDING" -> {
                List<DesignDocument.Element> bindings = new ArrayList<>(design.entries("serviceRegistrations"));
                if (finding.code().equals("SPRING_UNKNOWN_BINDING_NODE")) bindings.addAll(design.entries("eventHandlers"));
                var entries = bindings.stream().filter(e -> e.name().equals("bean"))
                        .filter(e -> finding.field("serviceInterface").isBlank() || e.contains(finding.field("serviceInterface")))
                        .filter(e -> finding.field("beanName").isBlank() || e.contains(finding.field("beanName")))
                        .filter(e -> finding.field("serviceName").isBlank() || e.contains(finding.field("serviceName"))).toList();
                yield entries.isEmpty() ? choose(design, design.configs(), true, "binding not found in this design")
                        : choose(design, entries, false, "binding not found in this design");
            }
            case "SPRING_CONFIG" -> design.configs().isEmpty() ? start(design, "configuration block unavailable") : choose(design, design.configs(), false, "configuration unavailable");
            case "SPRING_DOCUMENT" -> start(design, finding.message());
            case "SPRING_TYPE", "EVENT", "SERVICE" -> {
                String type = finding.field("typeName");
                if (type.isBlank()) type = finding.field("eventType");
                if (type.isBlank()) type = finding.field("serviceInterface");
                final String name = type;
                List<DesignDocument.Element> entries = new ArrayList<>(design.entries("eventTypes"));
                entries.addAll(design.entries("serviceTypes"));
                var matches = entries.stream().filter(e -> !name.isBlank() && e.contains(name)).toList();
                yield matches.isEmpty() ? choose(design, design.configs(), true, "type not listed in this design") : choose(design, matches, false, "type not listed in this design");
            }
            default -> unavailable("no source mapping for diagnostic element " + finding.kind());
        };
    }
    private static DiagnosticLocation start(DesignDocument d, String why) { return new DiagnosticLocation(d.file(), 1, "DESIGN", true, why, List.of()); }
    public static DiagnosticLocation related(ProducerResult.Finding finding, DesignDocument design, DesignFiles files) {
        if (finding.kind().equals("NODE") && !finding.field("nodeClass").isEmpty()) {
            try { return new DiagnosticLocation(files.fqn(finding.field("nodeClass")).toString(), 1, "NODE", true, "node class; relationship unverified", List.of()); }
            catch (IOException e) { return unavailable("node class not under authorised roots"); }
        }
        if (finding.kind().equals("SOURCE_MEMBER") && design != null) {
            var matcher = java.util.regex.Pattern.compile("\\bid\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(finding.field("xmlDeclaration"));
            if (matcher.find()) return choose(design, design.beans(matcher.group(1)), true, "quoted bean not declared in this design");
        }
        return unavailable("no secondary source location");
    }
    private static DiagnosticLocation choose(DesignDocument d, List<DesignDocument.Element> matches, boolean approximate, String missing) {
        if (matches.isEmpty()) return unavailable(missing);
        if (matches.size() > 1) return new DiagnosticLocation(null, 0, "DESIGN", false, "ambiguous declaration", matches.stream().map(DesignDocument.Element::line).toList());
        return new DiagnosticLocation(d.file(), matches.getFirst().line(), "DESIGN", approximate, approximate ? "approximate declaration" : "declaration matched by name; relationship unverified", List.of());
    }
}
