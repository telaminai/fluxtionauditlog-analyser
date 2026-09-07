package com.benchv;

import com.bench.MatrixEvent;

/**
 * The shapes a HAND-WRITTEN implementation can take, against the one the builder produces.
 *
 * <p>The owner's constraint on this round: <i>"the conditional branching has to be large enough to
 * confound branch prediction in the cpu for hand-rolled"</i>. A call site with one implementation
 * loaded is devirtualised and there is nothing left to mispredict, so {@code RuntimeMono} and
 * {@code RuntimePoly} are both measured and the truth is bracketed by them.
 *
 * <ul>
 *   <li>{@code Fixed} — a human who knew the order was 4 and unrolled it. <b>Not a library.</b> The
 *       ceiling the generator is trying to reach.</li>
 *   <li>{@code Generic} — a library that does not specialise: triple loop, nothing folds.</li>
 *   <li>{@code RuntimeMono} — a library whose call site happens to see one implementation.</li>
 *   <li>{@code RuntimePoly} — a library whose call site sees four, selected unpredictably. All four
 *       carry <b>identical arithmetic</b>, so what is measured is the inability to bind, not extra
 *       work.</li>
 * </ul>
 *
 * <p><b>Known bias, stated rather than hidden:</b> {@code RuntimePoly} pays two ALU ops per event to
 * derive its unpredictable index that no other arm pays. That is charged against it, so treat it as an
 * upper bound on the dispatch cost rather than an exact figure.
 */
public class HandMatrix {

    public static final class Fixed {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        public double checksum;

        public Fixed() {
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
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

    public static final class Generic {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        /**
         * <b>Taken from the constructor on purpose.</b> An earlier version wrote
         * {@code private final int n = 4;} — a compile-time constant, which javac and the JIT fold,
         * unrolling the loop into exactly the specialised code this arm exists to be slower than. It
         * measured 1.03x the hand-unrolled arm and the round nearly concluded that generic loops cost
         * nothing. They do; that arm was not generic.
         */
        private final int n;
        public double checksum;

        public Generic(int n) {
            this.n = n;
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double s = 0.0;
                    for (int k = 0; k < n; k++) {
                        s += a[i * n + k] * b[k * n + j];
                    }
                    out[i * n + j] = s;
                }
            }
            checksum += out[0] + out[15];
        }
    }

    public static final class RuntimeMono {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        private final MatrixNodes.MatMul impl = new MatrixNodes.Mat4A();
        public double checksum;

        public RuntimeMono() {
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            impl.multiply(a, b, out);
            checksum += out[0] + out[15];
        }
    }

    public static final class RuntimePoly {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        private final MatrixNodes.MatMul[] impls = {
                new MatrixNodes.Mat4A(), new MatrixNodes.Mat4B(),
                new MatrixNodes.Mat4C(), new MatrixNodes.Mat4D()};
        public double checksum;

        public RuntimePoly() {
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            // multiplicative hash of the sequence: a pattern the branch predictor cannot learn, and
            // cheaper than a table lookup. Two ALU ops, charged against this arm.
            int pick = (int) ((e.seq * 0x9E3779B97F4A7C15L) >>> 62);
            impls[pick].multiply(a, b, out);
            checksum += out[0] + out[15];
        }
    }
}
