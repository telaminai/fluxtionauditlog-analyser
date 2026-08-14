package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Headless test of the table model (AbstractTableModel — no GUI toolkit needed). */
class LogTableModelTest {

    private static LogTableModel model() {
        return new LogTableModel(new HeapLogStore(Samples.sample()));
    }

    @Test
    void columnsAndRowCount() {
        LogTableModel m = model();
        assertEquals(9, m.getColumnCount());
        assertEquals("eventTime", m.getColumnName(LogTableModel.COL_EVENT_TIME));
        assertEquals("callback", m.getColumnName(LogTableModel.COL_CALLBACK));
        assertEquals("eventToString", m.getColumnName(LogTableModel.COL_EVENT_TO_STRING));
        assertEquals("nodeLogs", m.getColumnName(LogTableModel.COL_NODE_LOGS));
        assertEquals(21, m.getRowCount());
        assertEquals(Long.class, m.getColumnClass(LogTableModel.COL_LOG_TIME));
        assertEquals(Integer.class, m.getColumnClass(LogTableModel.COL_NODE_LOGS));
    }

    @Test
    void firstRowValuesComeFromTheIndex() {
        LogTableModel m = model();
        assertInstanceOf(Long.class, m.getValueAt(0, LogTableModel.COL_EVENT_TIME));
        assertEquals("LifecycleEvent", m.getValueAt(0, LogTableModel.COL_EVENT));
        assertEquals("StartComplete", m.getValueAt(0, LogTableModel.COL_EVENT_TO_STRING));
        assertEquals("marketMaker-DEMO", m.getValueAt(0, LogTableModel.COL_THREAD));
        assertEquals(1, m.getValueAt(0, LogTableModel.COL_NODE_LOGS));
        assertEquals("", m.getValueAt(0, LogTableModel.COL_CALLBACK), "lifecycle event has no callback");
        assertNull(m.getValueAt(0, LogTableModel.COL_GROUPING), "groupingId is null in the sample");
    }

    @Test
    void exportFunctionEventsBlankTheEventAndShowShortCallback() {
        LogTableModel m = model();
        int exportRow = -1;
        for (int r = 0; r < m.getRowCount() && exportRow < 0; r++) {
            if (!((String) m.getValueAt(r, LogTableModel.COL_CALLBACK)).isEmpty()) exportRow = r;
        }
        assertTrue(exportRow >= 0, "some row is an export-function callback");
        assertEquals("", m.getValueAt(exportRow, LogTableModel.COL_EVENT), "ExportFunctionAuditEvent event is blanked");
        String shortName = (String) m.getValueAt(exportRow, LogTableModel.COL_EVENT_TO_STRING);
        String callback = (String) m.getValueAt(exportRow, LogTableModel.COL_CALLBACK);
        assertTrue(shortName.endsWith("." + callback), "eventToString shows Class.callback: " + shortName);
        assertFalse(shortName.startsWith("public "), "full signature is replaced by the short name");
    }

    @Test
    void nodeLogsCountMatchesParsedRecords() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        LogTableModel m = new LogTableModel(store);
        for (int row = 0; row < m.getRowCount(); row++) {
            int shown = (Integer) m.getValueAt(row, LogTableModel.COL_NODE_LOGS);
            assertEquals(store.record(row).nodeLogs().size(), shown, "nodeLogs count matches at row " + row);
        }
    }

    @Test
    void someRowHasEventTimeNullAndNanFlag() {
        LogTableModel m = model();
        boolean anyNullEventTime = false, anyNaN = false;
        for (int row = 0; row < m.getRowCount(); row++) {
            if (m.getValueAt(row, LogTableModel.COL_EVENT_TIME) == null) anyNullEventTime = true;
            if (m.hasNaN(row)) anyNaN = true;
        }
        assertTrue(anyNullEventTime, "records with eventTime -1 render as null");
        assertTrue(anyNaN, "sample has NaN hedgeQuantity values");
    }
}
