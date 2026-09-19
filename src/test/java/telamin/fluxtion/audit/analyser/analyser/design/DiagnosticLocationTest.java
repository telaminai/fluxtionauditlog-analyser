package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticLocationTest {
    @TempDir Path root;
    ProducerResult.Finding finding(String kind, Map<String,Object> fields) {
        Map<String,Object> e = new HashMap<>(fields); e.put("kind",kind);
        return new ProducerResult.Finding("SPRING_UNKNOWN_BINDING_NODE", "ERROR", "bad binding", "why", "fix", e, Map.of(), List.of());
    }
    ProducerResult result() { return new ProducerResult("result.json", "validate", "", "", List.of(), Map.of(), Map.of()); }
    DesignFiles files() { return new DesignFiles(List.of(root.toString()), root); }
    @Test void bindingIsTheOffenderWhetherTargetExistsOrNot() throws Exception {
        for (String id : List.of("present", "absent")) {
            var doc = DesignDocument.parse("design.xml", "<beans>\n<bean id='present'/>\n<bean id='config' class='FluxtionSpringConfig'>\n<property name='serviceRegistrations'><list>\n<bean><property name='nodeBeans'><list><value>"+id+"</value></list></property></bean>\n</list></property></bean></beans>");
            var at = DiagnosticLocation.resolve(result(), finding("SPRING_SERVICE_BINDING", Map.of("beanName", id)), doc, files());
            assertTrue(at.available()); assertEquals(5, at.line()); assertFalse(at.approximate());
        }
    }
    @Test void multipleBindingsRemainAmbiguousAndIncompleteSingleBindingResolves() throws Exception {
        var single = DesignDocument.parse("design.xml", "<beans><bean class='FluxtionSpringConfig'><property name='serviceRegistrations'><list><bean/></list></property></bean></beans>");
        assertTrue(DiagnosticLocation.resolve(result(), finding("SPRING_SERVICE_BINDING", Map.of()), single, files()).available());
        var duplicate = DesignDocument.parse("design.xml", single.text().replace("<bean/>", "<bean/><bean/>"));
        var at = DiagnosticLocation.resolve(result(), finding("SPRING_SERVICE_BINDING", Map.of()), duplicate, files());
        assertFalse(at.available()); assertEquals(2, at.candidates().size());
    }
    @Test void unknownHandlerNodeUsesTheHandlerDeclarationRatherThanAnUnrelatedServiceBinding() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans>\n<bean class='FluxtionSpringConfig'>\n<property name='eventHandlers'><list><bean><property name='nodeBeans'><list><value>absent</value></list></property></bean></list></property>\n</bean></beans>");
        var at = DiagnosticLocation.resolve(result(), finding("SPRING_SERVICE_BINDING", Map.of("beanName", "absent")), doc, files());
        assertTrue(at.available()); assertEquals(3, at.line()); assertFalse(at.approximate());
    }
    @Test void nodeMapsToBeanAndSourceMemberMapsToJava() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans>\n<bean id='gate'/>\n</beans>");
        assertEquals(2, DiagnosticLocation.resolve(result(), finding("NODE", Map.of("nodeName", "gate")), doc, files()).line());
        Files.writeString(root.resolve("Node.java"), "class Node { int count; }");
        var at = DiagnosticLocation.resolve(result(), finding("SOURCE_MEMBER", Map.of("className", "Node", "member", "field:count")), doc, files());
        assertTrue(at.available()); assertEquals("NODE", at.mode());
    }
    @Test void sourceRootIsHonouredAndUnavailableLocationsKeepTheFinding() throws Exception {
        Files.createDirectory(root.resolve("java")); Files.writeString(root.resolve("java/Node.java"), "class Node {}\n");
        var r = new ProducerResult("r.json", "build", "java", "", List.of(), Map.of(), Map.of());
        var f = new ProducerResult.Finding("FLX-1009", "ERROR", "message", "why", "fix", Map.of("kind", "NODE"), Map.of("file", "Node.java", "line", 1), List.of());
        assertEquals(root.resolve("java/Node.java").toRealPath().toString(), DiagnosticLocation.resolve(r, f, null, files()).file());
        var foreign = new ProducerResult("r.json", "build", "/unavailable/source", "", List.of(f), Map.of(), Map.of());
        assertFalse(DiagnosticLocation.resolve(foreign, f, null, files()).available()); assertEquals(List.of(f), foreign.findings());
    }
    @Test void unchangedXmlDoesNotCertifyJavaOrTheLoadedRunAfterFailedBuild() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans/>");
        var inputs = Map.of("xmlHash", "sha256:" + doc.revision(), "sourceHash", "before-java-edit");
        var build = Map.of("inputs", inputs, "outcome", "failed", "compilerRan", false);
        var receipt = Map.<String,Object>of("schemaVersion","1.0", "stages", Map.of("build", build, "validate", Map.of("inputs", inputs,"outcome","ok")));
        var result = new ProducerResult("old-sidecar.json", "build", "", "", List.of(), receipt, Map.of("currentSourceHash", "after-java-edit"));
        var state = result.relationship(doc);
        assertEquals("input-current", state.get("relationship")); assertEquals("unverified", state.get("logRelationship"));
        assertEquals("mismatch", ((Map<?,?>)state.get("inputChecks")).get("sourceHash"));
        assertEquals("sidecar predates the last attempt", state.get("resultStatus"));
    }
    @Test void wrapperShapesAndVersionsAreExplicit() {
        assertEquals("validate", ProducerResult.parse("v", "{\"contractVersion\":\"1.0\",\"valid\":false,\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}", Map.of(), Map.of()).stage());
        assertEquals("regenerate", ProducerResult.parse("r", "{\"schemaVersion\":\"1.0\",\"classes\":{},\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}", Map.of(), Map.of()).stage());
        assertEquals("build", ProducerResult.parse("b", "{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}", Map.of(), Map.of()).stage());
        assertThrows(IllegalArgumentException.class, () -> ProducerResult.parse("bad", "{\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}", Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ProducerResult.parse("bad", "{\"diagnosticsVersion\":\"2.0\",\"diagnostics\":[]}", Map.of(), Map.of()));
    }

    @Test void outputsDescribePostReconciliationStateAndOwnInputHashCanDisproveReceipt() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans/>");
        var stage = Map.of("inputs", Map.of("xmlHash", "old", "sourceHash", "before"),
                "outputs", Map.of("xmlHash", doc.revision(), "sourceHash", "after"), "outcome", "ok");
        var receipt = Map.<String,Object>of("stages", Map.of("regenerate", stage));
        var result = new ProducerResult("r", "regenerate", "", "", List.of(), receipt, Map.of("currentSourceHash", "after"));
        assertEquals("input-current", result.relationship(doc).get("relationship"));
        assertEquals("match", ((Map<?,?>) result.relationship(doc).get("inputChecks")).get("sourceHash"));
        var oldResult = new ProducerResult("r", "regenerate", "", "old", List.of(), receipt, Map.of());
        assertEquals("input-stale", oldResult.relationship(doc).get("relationship"));
    }

    @Test void malformedDocumentFindingHasADocumentStartEvenWithoutAParsedRevision() throws Exception {
        Path xml = Files.writeString(root.resolve("broken.xml"), "<beans>");
        var at = DiagnosticLocation.resolve(result(), finding("SPRING_DOCUMENT", Map.of()), null, files(), xml.toString());
        assertTrue(at.available()); assertEquals(1, at.line()); assertTrue(at.approximate());
    }
    @Test void beanConfigAndTypeFamiliesKeepTheirOffendingLocationsAndMissingCases() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans>\n<bean id='gate'>\n<property name='target' ref='absent'/></bean>\n<bean id='config' class='FluxtionSpringConfig'>\n<property name='eventTypes'><list><value>com.acme.Event</value></list></property>\n<property name='serviceTypes'><list><value>com.acme.Service</value></list></property>\n</bean></beans>");
        var dangling = new ProducerResult.Finding("SPRING_DANGLING_BEAN_REF", "ERROR", "missing ref", "why", "fix", Map.of("kind","SPRING_BEAN","beanName","gate"), Map.of(), List.of(Map.of("name","absent")));
        assertEquals(3, DiagnosticLocation.resolve(result(), dangling, doc, files()).line());
        assertEquals(4, DiagnosticLocation.resolve(result(), finding("SPRING_CONFIG", Map.of()), doc, files()).line());
        assertEquals(5, DiagnosticLocation.resolve(result(), finding("SPRING_TYPE", Map.of("typeName","com.acme.Event")), doc, files()).line());
        assertEquals(5, DiagnosticLocation.resolve(result(), finding("EVENT", Map.of("eventType","com.acme.Event")), doc, files()).line());
        assertEquals(6, DiagnosticLocation.resolve(result(), finding("SERVICE", Map.of("serviceInterface","com.acme.Service")), doc, files()).line());
        assertFalse(DiagnosticLocation.resolve(result(), finding("SPRING_BEAN", Map.of("beanName","absent")), doc, files()).available());
        assertTrue(DiagnosticLocation.resolve(result(), finding("EVENT", Map.of("eventType","unknown")), doc, files()).approximate());
    }
    @Test void secondarySourceLinksRemainSeparateFromThePrimaryFinding() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans>\n<bean id='gate'/></beans>");
        Files.writeString(root.resolve("Node.java"), "class Node {}");
        var member = finding("SOURCE_MEMBER", Map.of("xmlDeclaration", "<bean id='gate'/>", "className", "Node"));
        assertEquals("NODE", DiagnosticLocation.resolve(result(), member, doc, files()).mode());
        assertEquals(2, DiagnosticLocation.related(member, doc, files()).line());
        assertEquals("NODE", DiagnosticLocation.related(finding("NODE", Map.of("nodeClass", "Node")), doc, files()).mode());
    }
}
