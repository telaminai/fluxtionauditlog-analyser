package com.benchv;

import com.bench.MatrixEvent;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * Round 62 — build-time node selection against runtime selection.
 *
 * <p><b>Why there are four identical order-4 implementations.</b> The owner's point: a call site with
 * one implementation loaded is devirtualised by the JIT and there is no branch left to mispredict, so
 * the comparison would be a straw man. {@code Mat4A}–{@code Mat4D} carry <b>byte-identical
 * arithmetic</b> and exist only to make the hand-written library's call site genuinely megamorphic —
 * the work is held constant so the measurement isolates dispatch, not maths.
 *
 * <p>Node state is {@code transient}: Fluxtion reads a node's non-transient fields as references to
 * other nodes, and rejected an earlier version with FLX-1009, "the fields [a, b, out] look like
 * node-local state rather than references to other nodes". It was right.
 */
public class MatrixNodes {

    public interface MatMul {
        void multiply(double[] a, double[] b, double[] out);
    }

    /** Order 4, unrolled. Identical arithmetic to Mat4B — a distinct class, not distinct work. */
    public static final class Mat4A implements MatMul {
        @Override
        public void multiply(double[] a, double[] b, double[] out) {
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
        }
    }

    /** Order 4, unrolled. Identical arithmetic to Mat4A — a distinct class, not distinct work. */
    public static final class Mat4B implements MatMul {
        @Override
        public void multiply(double[] a, double[] b, double[] out) {
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
        }
    }

    /** Order 4, unrolled. Identical arithmetic to Mat4A — a distinct class, not distinct work. */
    public static final class Mat4C implements MatMul {
        @Override
        public void multiply(double[] a, double[] b, double[] out) {
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
        }
    }

    /** Order 4, unrolled. Identical arithmetic to Mat4A — a distinct class, not distinct work. */
    public static final class Mat4D implements MatMul {
        @Override
        public void multiply(double[] a, double[] b, double[] out) {
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
        }
    }

    /** No specialisation available: the order is a variable, so nothing folds. */
    public static final class MatGeneric implements MatMul {
        private final int n;

        public MatGeneric(int n) {
            this.n = n;
        }

        @Override
        public void multiply(double[] a, double[] b, double[] out) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double s = 0.0;
                    for (int k = 0; k < n; k++) {
                        s += a[i * n + k] * b[k * n + j];
                    }
                    out[i * n + j] = s;
                }
            }
        }
    }

    /** Build-time selected node for order 2: arithmetic inline, no interface, nothing to resolve. */
    public static final class Mat2Node {
        private final transient double[] a = new double[4];
        private final transient double[] b = new double[4];
        private final transient double[] out = new double[4];
        public transient double checksum;

        public Mat2Node() {
            for (int i = 0; i < 4; i++) {
                a[i] = 1.0 + i;
                b[i] = 0.5 + i;
            }
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            out[0] = a[0] * b[0] + a[1] * b[2];
            out[1] = a[0] * b[1] + a[1] * b[3];
            out[2] = a[2] * b[0] + a[3] * b[2];
            out[3] = a[2] * b[1] + a[3] * b[3];
            checksum += out[0] + out[3];
        }
    }

    /** Build-time selected node for order 3: arithmetic inline, no interface, nothing to resolve. */
    public static final class Mat3Node {
        private final transient double[] a = new double[9];
        private final transient double[] b = new double[9];
        private final transient double[] out = new double[9];
        public transient double checksum;

        public Mat3Node() {
            for (int i = 0; i < 9; i++) {
                a[i] = 1.0 + i;
                b[i] = 0.5 + i;
            }
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            out[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
            out[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
            out[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];
            out[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
            out[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
            out[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];
            out[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
            out[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
            out[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];
            checksum += out[0] + out[8];
        }
    }

    /** Build-time selected node for order 4: arithmetic inline, no interface, nothing to resolve. */
    public static final class Mat4Node {
        private final transient double[] a = new double[16];
        private final transient double[] b = new double[16];
        private final transient double[] out = new double[16];
        public transient double checksum;

        public Mat4Node() {
            for (int i = 0; i < 16; i++) {
                a[i] = 1.0 + i;
                b[i] = 0.5 + i;
            }
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
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
}
