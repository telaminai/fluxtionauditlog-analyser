package telamin.fluxtion.audit.analyser.analyser.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A report's DEFINITION (spec-investigation-reports §A): an ordered list of REFERENCES with
 * connective prose — never a free-form document. Nothing but a {@code NARRATIVE} section stores its
 * own content; every other section is a reference that re-renders live (D-I3) and can therefore be
 * re-verified or fail loudly, and the authoring context (D-I3a: {@link LogFingerprint},
 * {@link FilterSnapshot}) is captured so the renderer can tell re-verification from misapplication.
 *
 * @param name        the report's identity — {@code report {name}} REPLACES by name, like graphs
 * @param title       the headline
 * @param createdAt   ISO instant of authoring, display-only
 * @param notes       optional free text ABOUT the report (shown as narrative styling, D-I2)
 * @param fingerprint the log this was authored against (D-I3a)
 * @param filter      the view it was authored under (D-I3a)
 * @param sections    the ordered sections
 */
public record ReportSpec(String name, String title, String createdAt, String notes,
                         LogFingerprint fingerprint, FilterSnapshot filter,
                         List<SectionSpec> sections) {

    public ReportSpec {
        name = name == null || name.isBlank() ? "report" : name.trim();
        title = title == null || title.isBlank() ? name : title.trim();
        createdAt = createdAt == null ? "" : createdAt;
        notes = notes == null ? "" : notes;
        filter = filter == null ? FilterSnapshot.all() : filter;
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public enum Kind { FINDING, RECORD, CHART, TOPOLOGY, SERIES, TABLE, NARRATIVE }

    /**
     * Column presentation for a TABLE section (D-I7/D-I8): declared literals, shown as declared.
     *
     * @param key      which value column feeds this column
     * @param heading  the printed heading (defaults to the key)
     * @param format   "" | decimals ("0", "0.00") | "percent" | "duration" | "time" (epoch→UTC)
     * @param align    "" (numeric→right, text→left) | "left" | "right"
     * @param emphasis "" | "bold" | "muted"
     * @param width    declared width in points; 0 = sized from content
     */
    public record ColumnSpec(String key, String heading, String format, String align,
                             String emphasis, int width) {
        public ColumnSpec {
            key = key == null ? "" : key.trim();
            heading = heading == null || heading.isBlank() ? key : heading.trim();
            format = format == null ? "" : format.trim();
            align = align == null ? "" : align.trim();
            emphasis = emphasis == null ? "" : emphasis.trim();
            width = Math.max(0, width);
        }

        public ColumnSpec(String key, String heading, String format, String align, String emphasis) {
            this(key, heading, format, align, emphasis, 0);
        }
    }

    /**
     * One section. A single record with a {@link Kind} discriminator rather than a sealed hierarchy,
     * because these round-trip through flat properties files (the GraphSpec precedent) and every
     * field is a reference or a declared presentation literal.
     *
     * <p><b>D-I1 is structural here:</b> a {@code FINDING} section has no text field to set — the
     * compact constructor DROPS any text supplied for a non-narrative kind, so the report verb cannot
     * author or override a finding no matter what parameters arrive. {@code flag} stays the one
     * write site; a finding section renders what the flag wrote, byte-identical.
     *
     * @param kind         which section this is
     * @param recordIndex  FINDING/RECORD anchor; -1 otherwise
     * @param file         RECORD on a rolled set: the member file, or null
     * @param ref          CHART: the graph name · TOPOLOGY: the named focus
     * @param call         SERIES/TABLE: the verb parameters that produce the data (rows are DERIVED)
     * @param text         NARRATIVE only: the prose — and it renders AS narrative (D-I2)
     * @param columns      TABLE: the declared column spec (D-I7)
     * @param rowWhen      TABLE: the row-highlight condition — an Expr, evaluated STRICTLY against
     *                     each row's own record, no LOCF carry (D-I8: a rule that cannot be checked
     *                     against its own row is a colour, not a rule)
     * @param rowWhenLabel TABLE: what the highlight MEANS — printed with the table (D-I8)
     */
    public record SectionSpec(Kind kind, int recordIndex, String file, String ref,
                              Map<String, String> call, String text,
                              List<ColumnSpec> columns, String rowWhen, String rowWhenLabel) {

        public SectionSpec {
            if (kind == null) throw new IllegalArgumentException("a section needs a kind");
            file = blankToNull(file);
            ref = blankToNull(ref);
            call = call == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(call));
            // D-I1: only narrative carries its own content. Text supplied on any other kind is
            // DROPPED here, not stored — the model has nowhere to put a second account of a finding.
            text = kind == Kind.NARRATIVE ? (text == null ? "" : text) : null;
            columns = columns == null ? List.of() : List.copyOf(columns);
            rowWhen = blankToNull(rowWhen);
            rowWhenLabel = blankToNull(rowWhenLabel);
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s.trim();
        }

        public static SectionSpec finding(int recordIndex) {
            return new SectionSpec(Kind.FINDING, recordIndex, null, null, null, null, null, null, null);
        }

        public static SectionSpec record(int recordIndex, String file) {
            return new SectionSpec(Kind.RECORD, recordIndex, file, null, null, null, null, null, null);
        }

        public static SectionSpec chart(String graphName) {
            return new SectionSpec(Kind.CHART, -1, null, graphName, null, null, null, null, null);
        }

        public static SectionSpec topology(String focusName) {
            return new SectionSpec(Kind.TOPOLOGY, -1, null, focusName, null, null, null, null, null);
        }

        public static SectionSpec series(Map<String, String> call) {
            return new SectionSpec(Kind.SERIES, -1, null, null, call, null, null, null, null);
        }

        public static SectionSpec table(Map<String, String> call, List<ColumnSpec> columns,
                                        String rowWhen, String rowWhenLabel) {
            return new SectionSpec(Kind.TABLE, -1, null, null, call, null, columns, rowWhen, rowWhenLabel);
        }

        public static SectionSpec narrative(String text) {
            return new SectionSpec(Kind.NARRATIVE, -1, null, null, null, text, null, null, null);
        }
    }
}
