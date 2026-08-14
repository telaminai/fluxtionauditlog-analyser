package telamin.fluxtion.audit.analyser.analyser.source;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A light model of a generated Fluxtion {@code EventProcessor} source file (spec §9). It maps a
 * node's {@code instanceId} (its field name, as seen in {@code nodeLogs}) to the field's declared
 * type and, via the file's {@code import}s, to a fully-qualified class name — the link that lets us
 * open the source behind a node. Uses a regex/line scan (no full Java parser).
 */
public final class EventProcessorModel {

    // field decl: [modifiers]* Type name (= ... | ;)   — the trailing '=' or ';' excludes methods
    private static final Pattern FIELD = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|transient|volatile)\\s+)+" +
            "([A-Za-z_$][\\w$.]*(?:<[^;>]*>)?(?:\\[\\])?)\\s+" +   // 1: type (may have generics/array)
            "([A-Za-z_$]\\w*)\\s*(?:=|;)");                       // 2: field name
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.$]+)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.$]+)\\s*;");

    private final String fqn;
    private final String packageName;
    private final Map<String, String> imports;      // simpleName -> FQN
    private final Map<String, String> fieldTypes;   // instanceId -> simple type (generics/array stripped)

    private EventProcessorModel(String fqn, String pkg, Map<String, String> imports, Map<String, String> fields) {
        this.fqn = fqn;
        this.packageName = pkg;
        this.imports = imports;
        this.fieldTypes = fields;
    }

    public static EventProcessorModel parse(String fqn, String source) {
        String pkg = first(PACKAGE, source);
        Map<String, String> imports = new HashMap<>();
        Matcher im = IMPORT.matcher(source);
        while (im.find()) {
            String f = im.group(1);
            int dot = f.lastIndexOf('.');
            if (dot >= 0) imports.put(f.substring(dot + 1), f);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher fm = FIELD.matcher(source);
        while (fm.find()) {
            String type = stripType(fm.group(1));
            String name = fm.group(2);
            fields.putIfAbsent(name, type);   // first (class-level) declaration wins
        }
        return new EventProcessorModel(fqn, pkg, imports, fields);
    }

    public String fqn() {
        return fqn;
    }

    public Set<String> instanceIds() {
        return fieldTypes.keySet();
    }

    /** True if {@code instanceId} is a declared field of this processor. */
    public boolean hasInstance(String instanceId) {
        return fieldTypes.containsKey(instanceId);
    }

    /** Resolves a node instanceId to the FQN of its declared type, or {@code null} if unknown. */
    public String fieldTypeFqn(String instanceId) {
        String simple = fieldTypes.get(instanceId);
        return simple == null ? null : resolveSimpleType(simple);
    }

    /** Resolves a simple type name to an FQN using imports, else a same-package guess. */
    public String resolveSimpleType(String simple) {
        if (simple == null) return null;
        String s = stripType(simple);
        if (s.contains(".")) return s;                       // already qualified
        if (imports.containsKey(s)) return imports.get(s);   // imported
        if (packageName != null && !packageName.isEmpty()) return packageName + "." + s;  // same package guess
        return s;
    }

    /** How many of the observed instanceIds are fields here — used to infer the processor. */
    public int coverage(Set<String> observedInstanceIds) {
        int n = 0;
        for (String id : observedInstanceIds) if (fieldTypes.containsKey(id)) n++;
        return n;
    }

    private static String stripType(String t) {
        int lt = t.indexOf('<');
        String s = lt >= 0 ? t.substring(0, lt) : t;
        return s.replace("[]", "").trim();
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
