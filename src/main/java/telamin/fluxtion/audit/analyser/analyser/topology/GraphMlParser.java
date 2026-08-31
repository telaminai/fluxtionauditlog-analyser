package telamin.fluxtion.audit.analyser.analyser.topology;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the GraphML Fluxtion emits for a processor into a {@link ProcessorTopology} (M21.1).
 *
 * <p>Derived from {@code fluxtion-visualiser}'s {@code GraphMlTopologyParser} — same document shape, same
 * label conventions — with its IntelliJ logging dependency removed and the model widened from the
 * LLM-context use (adjacency only) to what rendering and step-through need (node kind, class, edges as
 * first-class values).
 *
 * <p>The document jGraph emits looks like:
 * <pre>{@code
 * <node id="pricer_1">
 *   <data key="vertex_label">
 *     <jGraph:ShapeNode>
 *       <jGraph:label text="<<EventHandle>>&#10;id:pricer_1&#10;class:com.acme.Pricer"/>
 *       <jGraph:Style properties="EVENTHANDLER"/>
 * <edge id="3" source="pricer_1" target="book_2"/>
 * }</pre>
 *
 * <p>Two things to know about it. The label is newline-joined {@code key:value} lines with an optional
 * {@code <<Stereotype>>} first — the node's identity is in there as {@code id:}, matching the
 * {@code id} attribute and the log's {@code instanceId}. And {@code <graph edgedefault="undirected">} is
 * <b>misleading</b>: edges carry {@code source}/{@code target} and are read as directed, because
 * dispatch order is the whole point.
 *
 * <p>Lenient by design, like the log parser: a malformed document yields an
 * {@link ProcessorTopology#empty() empty topology} rather than throwing, and individual unparseable
 * nodes are skipped instead of failing the file.
 */
public final class GraphMlParser {
    private GraphMlParser() { }

    /** Newline forms seen in the label attribute once the XML entity is decoded, plus the raw entity. */
    private static final String LABEL_LINE_SPLIT = "\\r\\n|\\n|\\r|&#10;";

    /** Swallows parser diagnostics; {@link #parse(String)} already reports failure by returning empty. */
    private static final org.xml.sax.ErrorHandler SILENT = new org.xml.sax.ErrorHandler() {
        @Override public void warning(org.xml.sax.SAXParseException e) { }

        @Override public void error(org.xml.sax.SAXParseException e) { }

        @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
            throw e;   // still abort the parse — just without printing it first
        }
    };

    /** Parse GraphML text. Never throws; returns an empty topology if the document is unusable. */
    public static ProcessorTopology parse(String graphMlText) {
        if (graphMlText == null || graphMlText.isBlank()) return ProcessorTopology.empty();
        try {
            javax.xml.parsers.DocumentBuilder builder = secureBuilderFactory().newDocumentBuilder();
            // The default handler prints "[Fatal Error] …" to stderr before the exception we already
            // handle. This parser is lenient by contract — a bad file becomes an empty topology — so it
            // must not also shout on the console, and stderr is the MCP bridge's diagnostic channel.
            builder.setErrorHandler(SILENT);
            Document doc = builder.parse(
                    new ByteArrayInputStream(graphMlText.getBytes(StandardCharsets.UTF_8)));
            java.util.List<ProcessorTopology.Edge> edges = readEdges(doc);
            boolean aggregated = edges.stream()
                    .anyMatch(e -> e.fact("fluxtion.relationshipCount") != null);
            java.util.Map<String, ProcessorTopology.Node> nodes = readNodes(doc);
            boolean anyFact = aggregated
                    || edges.stream().anyMatch(e -> !e.facts().isEmpty())
                    || nodes.values().stream().anyMatch(n -> !n.facts().isEmpty());
            java.util.Map<String, String> graphFacts = readFluxtionData(firstGraphElement(doc));
            anyFact = anyFact || !graphFacts.isEmpty();
            return new ProcessorTopology(nodes, edges,
                    GraphVocabulary.of(graphFacts, aggregated, anyFact));
        } catch (Exception e) {
            return ProcessorTopology.empty();
        }
    }

    /** Parse a {@code .graphml} file. An unreadable file reads as an empty topology. */
    public static ProcessorTopology parse(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return ProcessorTopology.empty();
        }
    }

    /** True if the text looks like GraphML at all — used before offering a file as a topology. */
    public static boolean looksLikeGraphMl(String text) {
        return text != null && text.contains("<graphml");
    }

    // ---- document reading -------------------------------------------------------------------------

    private static Map<String, ProcessorTopology.Node> readNodes(Document doc) {
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        NodeList list = doc.getElementsByTagName("node");
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element node)) continue;
            String id = node.getAttribute("id");
            if (id == null || id.isBlank()) continue;

            String label = firstDescendantAttribute(node, "text");
            String style = firstDescendantAttribute(node, "properties");
            Map<String, String> fields = labelFields(label);

            // the label's id: wins if present — it is the authoritative instanceId — but the attribute
            // is the fallback, and in every emitted graph seen so far they agree
            String instanceId = fields.getOrDefault("id", id);
            java.util.Map<String, String> facts = readFluxtionData(node);
            nodes.put(instanceId, new ProcessorTopology.Node(
                    instanceId,
                    label == null ? "" : label,
                    // the vocabulary's fluxtion.class is authoritative where present: it is the
                    // compiler's own answer, where the label text is a rendering of it
                    facts.getOrDefault("fluxtion.class", fields.get("class")),
                    ProcessorTopology.Kind.fromStyle(style),
                    facts));
        }
        return nodes;
    }

    private static List<ProcessorTopology.Edge> readEdges(Document doc) {
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("edge");
        for (int i = 0; i < list.getLength(); i++) {
            if (!(list.item(i) instanceof Element edge)) continue;
            String source = edge.getAttribute("source");
            String target = edge.getAttribute("target");
            if (source == null || target == null || source.isBlank() || target.isBlank()) continue;
            String id = edge.getAttribute("id");
            edges.add(new ProcessorTopology.Edge(
                    id == null || id.isBlank() ? source + "->" + target : id, source, target,
                    readFluxtionData(edge)));
        }
        return edges;
    }

    /**
     * Every {@code <data key="fluxtion.*">} that belongs to THIS element.
     *
     * <p>Direct children only. {@code getElementsByTagName} would descend, and on the {@code <graph>}
     * element that would sweep up every node's and edge's facts into the graph-level set.
     */
    private static Map<String, String> readFluxtionData(Element owner) {
        Map<String, String> out = new LinkedHashMap<>();
        if (owner == null) return out;
        org.w3c.dom.NodeList kids = owner.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element child)) continue;
            if (!"data".equals(child.getTagName())) continue;
            String key = child.getAttribute("key");
            if (key == null || !key.startsWith("fluxtion.")) continue;
            String value = child.getTextContent();
            out.put(key, value == null ? "" : value.trim());
        }
        return out;
    }

    private static Element firstGraphElement(Document doc) {
        NodeList graphs = doc.getElementsByTagName("graph");
        return graphs.getLength() > 0 && graphs.item(0) instanceof Element g ? g : null;
    }

    /** The first non-empty value of {@code attribute} on any descendant element — the jGraph shape is nested. */
    private static String firstDescendantAttribute(Element parent, String attribute) {
        NodeList all = parent.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node child = all.item(i);
            if (!(child instanceof Element el)) continue;
            String value = el.getAttribute(attribute);
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    /**
     * Split a label into its {@code key:value} lines. A leading {@code <<Stereotype>>} carries no data we
     * need (the style attribute says the same thing more reliably) and is skipped; so is any line without
     * a colon. Only the first occurrence of a key counts, since a class name may itself contain a colon-
     * free package path but never a second {@code class:} line.
     */
    static Map<String, String> labelFields(String label) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (label == null || label.isBlank()) return fields;
        for (String raw : label.split(LABEL_LINE_SPLIT)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("<<") || line.startsWith("&lt;&lt;")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) fields.putIfAbsent(key, value);
        }
        return fields;
    }

    /**
     * A parser that will not resolve external entities. A {@code .graphml} can arrive from a shared log
     * store or a server endpoint, so it is untrusted input and XXE is a real exposure, not a formality.
     */
    private static DocumentBuilderFactory secureBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);   // jGraph: prefixes are matched by local name
        return factory;
    }
}
