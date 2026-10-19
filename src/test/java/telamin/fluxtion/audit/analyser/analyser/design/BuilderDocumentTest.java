package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuilderDocumentTest {
    static BuilderDocument doc(String body) {
        return BuilderDocument.parse("DEMO.java","class DEMO {\n void buildGraph(Config cfg) {\n"+body+"\n }\n}");
    }
    @Test void aWrappedLiteralAnchorsTheCallNotTheNameLine() {
        var nodes=doc("  cfg.addNode(\n    new A(),\n    \"a\");\n  cfg.addPublicNode(new B(), \"b\");").nodes();
        assertEquals(List.of(new BuilderDocument.NodeAnchor("a",3,true,"addNode"),
                new BuilderDocument.NodeAnchor("b",6,true,"addPublicNode")),nodes,"★ T4 call-token line, not literal line");
    }
    @Test void nonLiteralAndUnnamedAndVarargRegistrationsNeverInventNames() {
        var nodes=doc("cfg.addNode(new A(), nameVar);\ncfg.addNode(existing);\n"
                +"cfg.addNode(new B(), \"b\" + suffix);\ncfg.addNode(one, two, \"not-a-name\");").nodes();
        assertEquals(4,nodes.size());
        assertTrue(nodes.stream().allMatch(n->!n.anchored() && n.name()==null),"★ T5 no fabricated nameVar or varargs name");
    }
    @Test void nestedNewExpressionsHaveHonestDiscoveredLocations() {
        var nodes=doc("cfg.addNode(new Outer(\n new Inner()), \"outer\");").nodes();
        assertEquals(List.of(new BuilderDocument.NodeAnchor("outer",3,true,"addNode"),
                new BuilderDocument.NodeAnchor(null,4,false,"discovered")),nodes,"★ T6 child exists, without invented id");
    }
    @Test void commentsLiteralsAndOtherMethodsCannotCreateAnchorsOrCloseTheBody() {
        String text="class DEMO {\n void configureGeneration(Config cfg) { cfg.addNode(new Wrong(), \"wrong\"); }\n"
                +" void buildGraph(Config cfg) {\n // cfg.addNode(new Comment(), \"comment\"); }\n"
                +" String s=\"} addNode(new Text(), fake)\"; char c='}';\n"
                +" /* new Comment() } */ cfg.addNode(new Good(), \"good\");\n }\n"
                +" void other() { cfg.addNode(new Wrong(), \"other\"); }\n}";
        assertEquals(List.of(new BuilderDocument.NodeAnchor("good",6,true,"addNode")),BuilderDocument.parse("DEMO.java",text).nodes());
    }
    @Test void escapedLiteralsAndNestedArgumentsStayLexicalAndInSourceOrder() {
        var nodes=doc("cfg.addNode(new Box<A,B>(make(1,2)), \"a\\\"b\");\n"
                +"var child = new Child(); cfg.addPublicNode(child, \"child\");").nodes();
        assertEquals(3,nodes.size(),"all three source sites must exist before inspecting their names");
        assertEquals("a\"b",nodes.getFirst().name());
        assertEquals("discovered",nodes.get(1).kind()); assertNull(nodes.get(1).name());
        assertEquals("child",nodes.getLast().name());
    }
    @Test void aTextBlockContinuationCannotMoveTheNextDeclarationOrCreateFakeNodes() {
        String body = "String text = \"\"\"\n"
                + "still text \\" + "\n"
                + "cfg.addNode(new NotCode(), \"not-code\");\n"
                + "\"\"\";\n"
                + "cfg.addNode(new Good(), \"good\");";
        assertEquals(List.of(new BuilderDocument.NodeAnchor("good", 7, true, "addNode")), doc(body).nodes(),
                "a continued text-block line still occupies a source line");
    }
    @Test void javaTextBlockNamesStayUnanchoredAndOversizedUtf8IsRefused() {
        var nodes = doc("cfg.addNode(new A(), \"\"\"\nnot a supported literal name\n\"\"\");").nodes();
        assertEquals(1, nodes.size());
        assertFalse(nodes.getFirst().anchored());
        assertNull(nodes.getFirst().name());
        assertThrows(IllegalArgumentException.class,
                () -> BuilderDocument.parse("DEMO.java", "é".repeat(DesignFiles.MAX_BYTES / 2 + 1)));
    }
    @Test void nestedRegistrationsUseIndexedDelimiterAndArgumentSpans() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/design/BuilderDocument.java"));
        assertTrue(source.contains("int[] closing = closingDelimiters(tokens);"),
                "nested calls must use a precomputed delimiter index, not scan to each closing parenthesis");
        assertTrue(source.contains("i = closing[i];"),
                "nested argument spans must be skipped through the delimiter index");
        int count = 5000;
        var nodes = doc("cfg.addNode(".repeat(count) + "new DEMO()" + ", \"a\")".repeat(count) + ";").nodes();
        assertEquals(count, nodes.size(), "indexed scanning must still visit every nested registration");
        assertTrue(nodes.stream().allMatch(n -> n.anchored() && "a".equals(n.name())));
    }
}
