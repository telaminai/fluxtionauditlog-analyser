package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.*;

/** Project intent, independent of which processor source or evidence currently exists. */
public record ProcessorDeclaration(String name, Kind kind, String fqcn) {
    public enum Kind { DECLARED, RUNTIME, UNSPECIFIED }
    private static final String PREFIX = "processorDeclaration.";

    public ProcessorDeclaration {
        if (name == null || name.isBlank() || kind == null)
            throw new IllegalArgumentException("processor declaration requires a name and kind");
        name = name.strip();
        fqcn = fqcn == null ? "" : fqcn.strip();
        if (kind == Kind.DECLARED && !fqcn.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)+"))
            throw new IllegalArgumentException("declared processor requires a qualified class name");
        if (kind != Kind.DECLARED && !fqcn.isEmpty())
            throw new IllegalArgumentException("runtime/unspecified processor must not invent a fixed class name");
    }

    public static List<ProcessorDeclaration> read(Properties p) {
        if (p.stringPropertyNames().stream().noneMatch(k -> k.startsWith(PREFIX))) return List.of();
        if (!"1".equals(p.getProperty(PREFIX + "version")))
            throw new IllegalArgumentException("unsupported processorDeclaration.version; supports 1");
        int count;
        try { count = Integer.parseInt(p.getProperty(PREFIX + "count", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("processorDeclaration.count must be an integer"); }
        if (count < 0 || count > 1000) throw new IllegalArgumentException("processorDeclaration.count outside 0..1000");
        List<ProcessorDeclaration> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String prefix = PREFIX + i + ".";
            String rawKind = p.getProperty(prefix + "kind", "");
            Kind kind = switch (rawKind) {
                case "declared" -> Kind.DECLARED;
                case "runtime" -> Kind.RUNTIME;
                case "unspecified" -> Kind.UNSPECIFIED;
                default -> throw new IllegalArgumentException("unknown processor declaration kind: " + rawKind);
            };
            var declaration = new ProcessorDeclaration(p.getProperty(prefix + "name"), kind, p.getProperty(prefix + "fqcn"));
            if (!names.add(declaration.name())) throw new IllegalArgumentException("duplicate processor declaration: " + declaration.name());
            result.add(declaration);
        }
        return List.copyOf(result);
    }

    public static void write(Properties p, List<ProcessorDeclaration> declarations) {
        p.setProperty(PREFIX + "version", "1");
        p.setProperty(PREFIX + "count", Integer.toString(declarations.size()));
        for (int i = 0; i < declarations.size(); i++) {
            var d = declarations.get(i);
            String prefix = PREFIX + i + ".";
            p.setProperty(prefix + "name", d.name());
            p.setProperty(prefix + "kind", d.kind().name().toLowerCase(Locale.ROOT));
            if (!d.fqcn().isEmpty()) p.setProperty(prefix + "fqcn", d.fqcn());
        }
    }
}
