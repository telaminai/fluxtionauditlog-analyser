package telamin.fluxtion.audit.analyser.analyser.design;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

/** An inert, location-preserving XML index. No schema loading, external entities or Spring evaluation. */
public record DesignDocument(String file, String text, String revision, List<Element> elements) {
    public record Element(String name, Map<String, String> attributes, int line, int start, int end,
                          String value, List<Element> children) {
        public String attr(String key) { return attributes.getOrDefault(key, ""); }
        public List<Element> descendants() {
            List<Element> list = new ArrayList<>();
            for (Element child : children) { list.add(child); list.addAll(child.descendants()); }
            return List.copyOf(list);
        }
        public boolean contains(String word) {
            return attributes.containsValue(word) || value.trim().equals(word)
                    || children.stream().anyMatch(c -> c.contains(word));
        }
    }
    public static final class ParseFailure extends Exception {
        private final int line;
        public ParseFailure(int line, String message) { super("parse error at line " + line + ": " + message); this.line = line; }
        public int line() { return line; }
    }
    public static DesignDocument parse(String file, String text) throws ParseFailure {
        List<Element> all = new ArrayList<>();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var parser = factory.newSAXParser();
            var reader = parser.getXMLReader();
            reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var handler = new DefaultHandler() {
                private Locator locator;
                private final Deque<Building> stack = new ArrayDeque<>();
                @Override public void setDocumentLocator(Locator l) { locator = l; }
                @Override public void startElement(String uri, String local, String q, Attributes attrs) {
                    int after = offset(text, locator.getLineNumber(), locator.getColumnNumber());
                    int start = Math.max(0, text.lastIndexOf('<', Math.max(0, after - 1)));
                    Map<String, String> a = new LinkedHashMap<>();
                    for (int i = 0; i < attrs.getLength(); i++) a.put(attrs.getLocalName(i).isEmpty() ? attrs.getQName(i) : attrs.getLocalName(i), attrs.getValue(i));
                    stack.push(new Building(local.isEmpty() ? q : local, a, lineAt(text, start), start));
                }
                @Override public void characters(char[] chars, int start, int length) {
                    if (!stack.isEmpty()) stack.peek().value.append(chars, start, length);
                }
                @Override public void endElement(String uri, String local, String q) {
                    Building b = stack.pop();
                    Element node = new Element(b.name, Map.copyOf(b.attrs), b.line, b.start,
                            Math.min(text.length(), offset(text, locator.getLineNumber(), locator.getColumnNumber())),
                            b.value.toString(), List.copyOf(b.children));
                    all.add(node);
                    if (!stack.isEmpty()) stack.peek().children.add(node);
                }
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            };
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new InputSource(new StringReader(text)));
            all.sort(Comparator.comparingInt(Element::start));
            return new DesignDocument(file, text, DesignFiles.sha256(text), List.copyOf(all));
        } catch (SAXParseException e) { throw new ParseFailure(e.getLineNumber(), e.getMessage()); }
        catch (Exception e) { throw new ParseFailure(1, e.getMessage()); }
    }
    private static class Building {
        final String name; final Map<String, String> attrs; final int line, start;
        final StringBuilder value = new StringBuilder(); final List<Element> children = new ArrayList<>();
        Building(String name, Map<String, String> attrs, int line, int start) { this.name=name; this.attrs=attrs; this.line=line; this.start=start; }
    }
    public List<Element> beans(String id) { return elements.stream().filter(e -> e.name.equals("bean") && e.attr("id").equals(id)).toList(); }
    public List<String> beanIds() { return elements.stream().filter(e -> e.name.equals("bean") && !e.attr("id").isEmpty()).map(e -> e.attr("id")).distinct().toList(); }
    public List<Element> configs() { return elements.stream().filter(e -> e.name.equals("bean") && e.attr("class").endsWith("FluxtionSpringConfig")).toList(); }
    public List<Element> entries(String property) {
        List<Element> entries = new ArrayList<>();
        for (Element config : configs()) for (Element child : config.children()) {
            if (child.name().equals("property") && child.attr("name").equals(property)) collectEntries(child, entries);
        }
        return List.copyOf(entries);
    }
    private static void collectEntries(Element parent, List<Element> entries) {
        for (Element child : parent.children()) {
            if (Set.of("bean", "value", "ref").contains(child.name())) entries.add(child);
            else collectEntries(child, entries);
        }
    }
    public static int offset(String text, int line, int column) {
        int pos = 0;
        for (int i = 1; i < line; i++) { int next = text.indexOf('\n', pos); if (next < 0) return text.length(); pos = next + 1; }
        return Math.min(text.length(), pos + Math.max(0, column - 1));
    }
    public static int lineAt(String text, int offset) { return 1 + (int) text.substring(0, Math.min(offset, text.length())).chars().filter(c -> c == '\n').count(); }
    public int lines() { return 1 + (int) text.chars().filter(c -> c == '\n').count(); }
}
