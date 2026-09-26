public class R7b {
    public static void main(String[] a) {
        // J: a READABLE control record for another node sits between the change and the unreadable one, past a marker
        R7.show("J WARN riskMonitor rec 1; marker; readable control for priceListener rec 3; unreadable rec 4; view rec 2",
                R7.ctl(1, "null", "WARN", "riskMonitor", "null") + R7.row(2, "null") + R7.MARKER
                        + R7.ctl(3, "null", "DEBUG", "priceListener", "null") + R7.unread(4, "null", "EventLogControlEvent") + R7.row(5, "null"), 1);
        // K: the same without a marker — does "It holds at least until" still hold?
        R7.show("K same, no marker",
                R7.ctl(1, "null", "WARN", "riskMonitor", "null") + R7.row(2, "null")
                        + R7.ctl(3, "null", "DEBUG", "priceListener", "null") + R7.unread(4, "null", "EventLogControlEvent") + R7.row(5, "null"), 1);
        // L: absent grouping, same groupId on both changes, closer across a marker: "which sets it to INFO", definite?
        R7.show("L absent grouping, groupId alpha on both, closer across a marker, view rec 2",
                R7.ctl(1, R7.ABSENT, "WARN", "riskMonitor", "alpha") + R7.row(2, R7.ABSENT) + R7.MARKER + R7.row(3, R7.ABSENT)
                        + R7.ctl(4, R7.ABSENT, "INFO", "riskMonitor", "alpha") + R7.row(5, R7.ABSENT), 1, 2);
    }
}
