package com.benchv;

import com.bench.MatrixEvent;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * Round 62 §9 — <b>does the advantage scale with node count?</b>
 *
 * <p>The costs measured in §5–§7 sit on different axes: the Fluxtion event wrapper is paid once per
 * EVENT, an unprovable receiver is paid once per NODE. One node is therefore the worst case for the
 * generator and the best case for hand-written code — which is how this round came to quote 1.48×
 * against the generator, a figure true only of a single-node graph.
 *
 * <p>Both arms here use the SAME implementation class doing the SAME work. The only difference is
 * whether the receiver is a concrete field (generated) or an array element (library). Class diversity
 * is a separate variable and was measured in §7: it is worth 17% between 1 and 16 receivers, against
 * the 535% that provability is worth.
 */
public class MatrixFleet {

    public interface Runner {
        void run(double scale);
    }

    /** One node's worth of real work: a 4x4 multiply over its own state. */
    public static final class Cell implements Runner {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        public double checksum;

        public Cell() {
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        @Override
        public void run(double scale) {
            b[0] = scale;
            out[0] = a[0] * b[0] + a[1] * b[4] + a[2] * b[8] + a[3] * b[12];
            out[1] = a[0] * b[1] + a[1] * b[5] + a[2] * b[9] + a[3] * b[13];
            out[2] = a[0] * b[2] + a[1] * b[6] + a[2] * b[10] + a[3] * b[14];
            out[3] = a[0] * b[3] + a[1] * b[7] + a[2] * b[11] + a[3] * b[15];
            out[4] = a[4] * b[0] + a[5] * b[4] + a[6] * b[8] + a[7] * b[12];
            out[5] = a[4] * b[1] + a[5] * b[5] + a[6] * b[9] + a[7] * b[13];
            out[6] = a[4] * b[2] + a[5] * b[6] + a[6] * b[10] + a[7] * b[14];
            out[7] = a[4] * b[3] + a[5] * b[7] + a[6] * b[11] + a[7] * b[15];
            out[8] = a[8] * b[0] + a[9] * b[4] + a[10] * b[8] + a[11] * b[12];
            out[9] = a[8] * b[1] + a[9] * b[5] + a[10] * b[9] + a[11] * b[13];
            out[10] = a[8] * b[2] + a[9] * b[6] + a[10] * b[10] + a[11] * b[14];
            out[11] = a[8] * b[3] + a[9] * b[7] + a[10] * b[11] + a[11] * b[15];
            out[12] = a[12] * b[0] + a[13] * b[4] + a[14] * b[8] + a[15] * b[12];
            out[13] = a[12] * b[1] + a[13] * b[5] + a[14] * b[9] + a[15] * b[13];
            out[14] = a[12] * b[2] + a[13] * b[6] + a[14] * b[10] + a[15] * b[14];
            out[15] = a[12] * b[3] + a[13] * b[7] + a[14] * b[11] + a[15] * b[15];
            checksum += out[0] + out[15];
        }
    }

    /** A Fluxtion node wrapping one cell. The builder adds N of these; each becomes a concrete field. */
    public static final class CellNode {
        private final transient Cell cell = new Cell();

        public double checksum() {
            return cell.checksum;
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onEvent(MatrixEvent e) {
            cell.run(e.scale);
        }
    }

    /** The library shape: N cells behind an array, so no receiver is statically provable. */
    public static final class Fleet {
        private final Runner[] cells;

        public Fleet(int n) {
            cells = new Runner[n];
            for (int i = 0; i < n; i++) { cells[i] = new Cell(); }
        }

        public void onEvent(MatrixEvent e) {
            double s = e.scale;
            for (int i = 0; i < cells.length; i++) {
                cells[i].run(s);
            }
        }

        public double checksum() {
            double t = 0.0;
            for (Runner r : cells) { t += ((Cell) r).checksum; }
            return t;
        }
    }
}
