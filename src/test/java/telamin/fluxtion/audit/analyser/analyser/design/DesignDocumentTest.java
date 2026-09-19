package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignDocumentTest {
    @Test void indexesNamespacedXmlAndIgnoresCommentedDeclarations() throws Exception {
        String xml = "<b:beans xmlns:b='http://www.springframework.org/schema/beans'>\n<!-- <bean id='ghost'/> -->\n<b:bean\n id='gate' class='com.acme.Gate'>\n<b:property name='target' ref='book'/>\n</b:bean>\n<b:bean id='book'/>\n</b:beans>";
        var doc = DesignDocument.parse("design.xml", xml);
        assertEquals(java.util.List.of("gate", "book"), doc.beanIds());
        var gate = doc.beans("gate").getFirst();
        assertEquals(3, gate.line());
        assertTrue(xml.substring(gate.start(), gate.end()).startsWith("<b:bean"));
        assertTrue(xml.substring(gate.start(), gate.end()).endsWith("</b:bean>"));
        assertEquals(7, doc.beans("book").getFirst().line());
    }
    @Test void duplicatesRemainDistinctInsteadOfLastWriterWinning() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans>\n<bean id='x'/>\n<bean id='x'/>\n</beans>");
        assertEquals(java.util.List.of(2, 3), doc.beans("x").stream().map(DesignDocument.Element::line).toList());
    }
    @Test void refusesExternalEntitiesAndReportsMalformedPosition() {
        assertThrows(DesignDocument.ParseFailure.class, () -> DesignDocument.parse("design.xml", "<!DOCTYPE beans [<!ENTITY x SYSTEM 'file:///missing'>]><beans>&x;</beans>"));
        var e = assertThrows(DesignDocument.ParseFailure.class, () -> DesignDocument.parse("design.xml", "<beans>\n<bean>\n</beans>"));
        assertEquals(3, e.line());
    }
    @Test void configIndexStopsAtAnEntryAndDoesNotIndexItsNestedBindingValuesTwice() throws Exception {
        var doc = DesignDocument.parse("design.xml", "<beans><bean class='FluxtionSpringConfig'><property name='serviceRegistrations'><list><bean><property name='nodeBeans'><list><value>x</value></list></property></bean></list></property></bean></beans>");
        assertEquals(1, doc.entries("serviceRegistrations").size());
        assertTrue(doc.entries("serviceRegistrations").getFirst().contains("x"));
        assertTrue(doc.entries("nodeBeans").isEmpty());
    }
    @Test void revisionIncludesWhitespaceAndComments() throws Exception {
        var a = DesignDocument.parse("a.xml", "<beans/>");
        var b = DesignDocument.parse("a.xml", "<!-- edit -->\n<beans/>");
        assertNotEquals(a.revision(), b.revision());
    }
}
