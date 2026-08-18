package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.Comparator;

/**
 * Alphabetical ordering that reads numbers as numbers.
 *
 * <p>Instance ids in a real graph are generated in families — {@code CHILL-1 … CHILL-24},
 * {@code till-1 … till-14}, {@code zoneRollup2}. Plain lexicographic order files those as
 * {@code CHILL-1, CHILL-10, CHILL-11, … CHILL-2}, which turns an index into a puzzle exactly when the
 * graph is big enough to need one. This compares digit runs by VALUE and everything else as text, so
 * {@code CHILL-2} precedes {@code CHILL-10}.
 *
 * <p>Case-insensitive first, then case-sensitive as a tie-break, so ordering is total and stable:
 * two ids differing only in case must not compare equal, or a {@code Set}-backed list can drop one.
 */
public final class NaturalOrder {

    /** Compare ids the way a reader scanning a list expects them to be filed. */
    public static final Comparator<String> ID = NaturalOrder::compare;

    private NaturalOrder() {
    }

    static int compare(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            boolean da = Character.isDigit(ca), db = Character.isDigit(cb);
            if (da && db) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                int cmp = compareNumeric(a, si, i, b, sj, j);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        if (i < a.length()) return 1;
        if (j < b.length()) return -1;
        return a.compareTo(b);          // identical ignoring case — order by case so it is total
    }

    /**
     * Compare two digit runs by value without parsing them: an id may carry a run longer than any
     * integer type ({@code build-20260818120000000000}), and a parse would overflow into a wrong
     * answer rather than a slow one.
     */
    private static int compareNumeric(String a, int as, int ae, String b, int bs, int be) {
        while (as < ae && a.charAt(as) == '0') as++;      // leading zeros carry no value
        while (bs < be && b.charAt(bs) == '0') bs++;
        int la = ae - as, lb = be - bs;
        if (la != lb) return Integer.compare(la, lb);     // more significant digits = larger
        for (int k = 0; k < la; k++) {
            int cmp = Character.compare(a.charAt(as + k), b.charAt(bs + k));
            if (cmp != 0) return cmp;
        }
        return 0;                                         // equal in value; zero-padding is not order
    }
}
