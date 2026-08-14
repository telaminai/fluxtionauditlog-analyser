package telamin.fluxtion.audit.analyser.analyser.diff;

import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.Change;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.DiffRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffExportTest {

    private static final List<DiffRow> ROWS = List.of(
            new DiffRow("node.price", "1.0", "2.0", Change.CHANGED),
            new DiffRow("node.qty", "5", "5", Change.SAME),
            new DiffRow("node.only,a", "x", null, Change.ONLY_A),
            new DiffRow("node.onlyB", null, "y", Change.ONLY_B));

    @Test
    void csvHasHeaderAndQuotesCommas() {
        String csv = DiffExport.toCsv(ROWS, "recA", "recB");
        String[] lines = csv.split("\r\n");
        assertEquals("key,recA,recB,change", lines[0]);
        assertEquals("node.price,1.0,2.0,CHANGED", lines[1]);
        assertTrue(csv.contains("\"node.only,a\""), "a comma in a field must be quoted");
        assertTrue(csv.contains(",ONLY_A"));
    }

    @Test
    void jsonIsWellFormedish() {
        String json = DiffExport.toJson(ROWS, "recA", "recB");
        assertTrue(json.contains("\"a\": \"recA\""));
        assertTrue(json.contains("\"differences\": 3"), "SAME rows aren't differences");
        assertTrue(json.contains("\"key\": \"node.price\""));
        assertTrue(json.contains("\"b\": null"));   // ONLY_A row has null b
        assertTrue(json.trim().endsWith("}"));
    }

    @Test
    void pdfIsAWellFormedDocument() {
        byte[] pdf = TextPdf.render("Record diff", DiffExport.toTextLines(ROWS, "recA", "recB"));
        String head = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
        String tail = new String(pdf, pdf.length - 5, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-1.4", head);
        assertEquals("%%EOF", tail);
        String body = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("/Type /Catalog"));
        assertTrue(body.contains("BaseFont /Courier"));
        assertTrue(body.contains("startxref"));
    }

    @Test
    void pdfPaginatesManyLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) lines.add("line " + i);
        byte[] pdf = TextPdf.render("big", lines);
        String body = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("/Count "), "has a pages tree");
        // more than one page → Count > 1
        int idx = body.indexOf("/Count ");
        int count = Integer.parseInt(body.substring(idx + 7, body.indexOf(' ', idx + 7)).trim());
        assertTrue(count > 1, "500 lines should span multiple pages");
    }
}
