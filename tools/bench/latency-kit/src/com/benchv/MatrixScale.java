package com.benchv;

import com.bench.MatrixEvent;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * Round 62 §6 — receiver-count sweep and order sweep.
 *
 * <p>{@code R0}–{@code R15} are sixteen implementations carrying <b>byte-identical</b> order-4
 * arithmetic. They exist so a call site can be made to see k distinct receiver types with the work
 * held exactly constant — what is measured is the number of types, nothing else.
 */
public class MatrixScale {

    public interface MatMul {
        void multiply(double[] a, double[] b, double[] out);
    }

    public static final class R0 implements MatMul {
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

    public static final class R1 implements MatMul {
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

    public static final class R2 implements MatMul {
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

    public static final class R3 implements MatMul {
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

    public static final class R4 implements MatMul {
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

    public static final class R5 implements MatMul {
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

    public static final class R6 implements MatMul {
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

    public static final class R7 implements MatMul {
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

    public static final class R8 implements MatMul {
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

    public static final class R9 implements MatMul {
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

    public static final class R10 implements MatMul {
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

    public static final class R11 implements MatMul {
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

    public static final class R12 implements MatMul {
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

    public static final class R13 implements MatMul {
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

    public static final class R14 implements MatMul {
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

    public static final class R15 implements MatMul {
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

    private static final MatMul[] ALL = {
            new R0(), new R1(), new R2(), new R3(), new R4(), new R5(), new R6(), new R7(),
            new R8(), new R9(), new R10(), new R11(), new R12(), new R13(), new R14(), new R15()};

    /**
     * A call site that sees exactly k receiver types. <b>Every k pays the identical selector
     * computation and the identical array index</b>, so k is the only variable — the two-ALU-op bias
     * charged against the polymorphic arm in §5 is gone.
     */
    public static final class PolyK {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        private final MatMul[] impls;
        private final int mask;
        public double checksum;

        public PolyK(int k) {
            this.impls = new MatMul[k];
            System.arraycopy(ALL, 0, this.impls, 0, k);
            this.mask = k - 1;                       // k is a power of two
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            int pick = (int) ((e.seq * 0x9E3779B97F4A7C15L) >>> 32) & mask;
            impls[pick].multiply(a, b, out);
            checksum += out[0] + out[15];
        }
    }

    /** Build-time selected node, order 2. */
    public static final class Node2 {
        private final transient double[] a = new double[4];
        private final transient double[] b = new double[4];
        private final transient double[] out = new double[4];
        public transient double checksum;

        public Node2() {
            for (int i = 0; i < 4; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
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

    /** Hand-unrolled, order 2 — the human who knew and gave up generality. */
    public static final class Fixed2 {
        public final double[] a = new double[4], b = new double[4], out = new double[4];
        public double checksum;

        public Fixed2() {
            for (int i = 0; i < 4; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            out[0] = a[0] * b[0] + a[1] * b[2];
            out[1] = a[0] * b[1] + a[1] * b[3];
            out[2] = a[2] * b[0] + a[3] * b[2];
            out[3] = a[2] * b[1] + a[3] * b[3];
            checksum += out[0] + out[3];
        }
    }

    /** Build-time selected node, order 3. */
    public static final class Node3 {
        private final transient double[] a = new double[9];
        private final transient double[] b = new double[9];
        private final transient double[] out = new double[9];
        public transient double checksum;

        public Node3() {
            for (int i = 0; i < 9; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
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

    /** Hand-unrolled, order 3 — the human who knew and gave up generality. */
    public static final class Fixed3 {
        public final double[] a = new double[9], b = new double[9], out = new double[9];
        public double checksum;

        public Fixed3() {
            for (int i = 0; i < 9; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

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

    /** Build-time selected node, order 4. */
    public static final class Node4 {
        private final transient double[] a = new double[16];
        private final transient double[] b = new double[16];
        private final transient double[] out = new double[16];
        public transient double checksum;

        public Node4() {
            for (int i = 0; i < 16; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
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

    /** Hand-unrolled, order 4 — the human who knew and gave up generality. */
    public static final class Fixed4 {
        public final double[] a = new double[16], b = new double[16], out = new double[16];
        public double checksum;

        public Fixed4() {
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

    /** Build-time selected node, order 8. */
    public static final class Node8 {
        private final transient double[] a = new double[64];
        private final transient double[] b = new double[64];
        private final transient double[] out = new double[64];
        public transient double checksum;

        public Node8() {
            for (int i = 0; i < 64; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            out[0] = a[0] * b[0] + a[1] * b[8] + a[2] * b[16] + a[3] * b[24] + a[4] * b[32] + a[5] * b[40] + a[6] * b[48] + a[7] * b[56];
            out[1] = a[0] * b[1] + a[1] * b[9] + a[2] * b[17] + a[3] * b[25] + a[4] * b[33] + a[5] * b[41] + a[6] * b[49] + a[7] * b[57];
            out[2] = a[0] * b[2] + a[1] * b[10] + a[2] * b[18] + a[3] * b[26] + a[4] * b[34] + a[5] * b[42] + a[6] * b[50] + a[7] * b[58];
            out[3] = a[0] * b[3] + a[1] * b[11] + a[2] * b[19] + a[3] * b[27] + a[4] * b[35] + a[5] * b[43] + a[6] * b[51] + a[7] * b[59];
            out[4] = a[0] * b[4] + a[1] * b[12] + a[2] * b[20] + a[3] * b[28] + a[4] * b[36] + a[5] * b[44] + a[6] * b[52] + a[7] * b[60];
            out[5] = a[0] * b[5] + a[1] * b[13] + a[2] * b[21] + a[3] * b[29] + a[4] * b[37] + a[5] * b[45] + a[6] * b[53] + a[7] * b[61];
            out[6] = a[0] * b[6] + a[1] * b[14] + a[2] * b[22] + a[3] * b[30] + a[4] * b[38] + a[5] * b[46] + a[6] * b[54] + a[7] * b[62];
            out[7] = a[0] * b[7] + a[1] * b[15] + a[2] * b[23] + a[3] * b[31] + a[4] * b[39] + a[5] * b[47] + a[6] * b[55] + a[7] * b[63];
            out[8] = a[8] * b[0] + a[9] * b[8] + a[10] * b[16] + a[11] * b[24] + a[12] * b[32] + a[13] * b[40] + a[14] * b[48] + a[15] * b[56];
            out[9] = a[8] * b[1] + a[9] * b[9] + a[10] * b[17] + a[11] * b[25] + a[12] * b[33] + a[13] * b[41] + a[14] * b[49] + a[15] * b[57];
            out[10] = a[8] * b[2] + a[9] * b[10] + a[10] * b[18] + a[11] * b[26] + a[12] * b[34] + a[13] * b[42] + a[14] * b[50] + a[15] * b[58];
            out[11] = a[8] * b[3] + a[9] * b[11] + a[10] * b[19] + a[11] * b[27] + a[12] * b[35] + a[13] * b[43] + a[14] * b[51] + a[15] * b[59];
            out[12] = a[8] * b[4] + a[9] * b[12] + a[10] * b[20] + a[11] * b[28] + a[12] * b[36] + a[13] * b[44] + a[14] * b[52] + a[15] * b[60];
            out[13] = a[8] * b[5] + a[9] * b[13] + a[10] * b[21] + a[11] * b[29] + a[12] * b[37] + a[13] * b[45] + a[14] * b[53] + a[15] * b[61];
            out[14] = a[8] * b[6] + a[9] * b[14] + a[10] * b[22] + a[11] * b[30] + a[12] * b[38] + a[13] * b[46] + a[14] * b[54] + a[15] * b[62];
            out[15] = a[8] * b[7] + a[9] * b[15] + a[10] * b[23] + a[11] * b[31] + a[12] * b[39] + a[13] * b[47] + a[14] * b[55] + a[15] * b[63];
            out[16] = a[16] * b[0] + a[17] * b[8] + a[18] * b[16] + a[19] * b[24] + a[20] * b[32] + a[21] * b[40] + a[22] * b[48] + a[23] * b[56];
            out[17] = a[16] * b[1] + a[17] * b[9] + a[18] * b[17] + a[19] * b[25] + a[20] * b[33] + a[21] * b[41] + a[22] * b[49] + a[23] * b[57];
            out[18] = a[16] * b[2] + a[17] * b[10] + a[18] * b[18] + a[19] * b[26] + a[20] * b[34] + a[21] * b[42] + a[22] * b[50] + a[23] * b[58];
            out[19] = a[16] * b[3] + a[17] * b[11] + a[18] * b[19] + a[19] * b[27] + a[20] * b[35] + a[21] * b[43] + a[22] * b[51] + a[23] * b[59];
            out[20] = a[16] * b[4] + a[17] * b[12] + a[18] * b[20] + a[19] * b[28] + a[20] * b[36] + a[21] * b[44] + a[22] * b[52] + a[23] * b[60];
            out[21] = a[16] * b[5] + a[17] * b[13] + a[18] * b[21] + a[19] * b[29] + a[20] * b[37] + a[21] * b[45] + a[22] * b[53] + a[23] * b[61];
            out[22] = a[16] * b[6] + a[17] * b[14] + a[18] * b[22] + a[19] * b[30] + a[20] * b[38] + a[21] * b[46] + a[22] * b[54] + a[23] * b[62];
            out[23] = a[16] * b[7] + a[17] * b[15] + a[18] * b[23] + a[19] * b[31] + a[20] * b[39] + a[21] * b[47] + a[22] * b[55] + a[23] * b[63];
            out[24] = a[24] * b[0] + a[25] * b[8] + a[26] * b[16] + a[27] * b[24] + a[28] * b[32] + a[29] * b[40] + a[30] * b[48] + a[31] * b[56];
            out[25] = a[24] * b[1] + a[25] * b[9] + a[26] * b[17] + a[27] * b[25] + a[28] * b[33] + a[29] * b[41] + a[30] * b[49] + a[31] * b[57];
            out[26] = a[24] * b[2] + a[25] * b[10] + a[26] * b[18] + a[27] * b[26] + a[28] * b[34] + a[29] * b[42] + a[30] * b[50] + a[31] * b[58];
            out[27] = a[24] * b[3] + a[25] * b[11] + a[26] * b[19] + a[27] * b[27] + a[28] * b[35] + a[29] * b[43] + a[30] * b[51] + a[31] * b[59];
            out[28] = a[24] * b[4] + a[25] * b[12] + a[26] * b[20] + a[27] * b[28] + a[28] * b[36] + a[29] * b[44] + a[30] * b[52] + a[31] * b[60];
            out[29] = a[24] * b[5] + a[25] * b[13] + a[26] * b[21] + a[27] * b[29] + a[28] * b[37] + a[29] * b[45] + a[30] * b[53] + a[31] * b[61];
            out[30] = a[24] * b[6] + a[25] * b[14] + a[26] * b[22] + a[27] * b[30] + a[28] * b[38] + a[29] * b[46] + a[30] * b[54] + a[31] * b[62];
            out[31] = a[24] * b[7] + a[25] * b[15] + a[26] * b[23] + a[27] * b[31] + a[28] * b[39] + a[29] * b[47] + a[30] * b[55] + a[31] * b[63];
            out[32] = a[32] * b[0] + a[33] * b[8] + a[34] * b[16] + a[35] * b[24] + a[36] * b[32] + a[37] * b[40] + a[38] * b[48] + a[39] * b[56];
            out[33] = a[32] * b[1] + a[33] * b[9] + a[34] * b[17] + a[35] * b[25] + a[36] * b[33] + a[37] * b[41] + a[38] * b[49] + a[39] * b[57];
            out[34] = a[32] * b[2] + a[33] * b[10] + a[34] * b[18] + a[35] * b[26] + a[36] * b[34] + a[37] * b[42] + a[38] * b[50] + a[39] * b[58];
            out[35] = a[32] * b[3] + a[33] * b[11] + a[34] * b[19] + a[35] * b[27] + a[36] * b[35] + a[37] * b[43] + a[38] * b[51] + a[39] * b[59];
            out[36] = a[32] * b[4] + a[33] * b[12] + a[34] * b[20] + a[35] * b[28] + a[36] * b[36] + a[37] * b[44] + a[38] * b[52] + a[39] * b[60];
            out[37] = a[32] * b[5] + a[33] * b[13] + a[34] * b[21] + a[35] * b[29] + a[36] * b[37] + a[37] * b[45] + a[38] * b[53] + a[39] * b[61];
            out[38] = a[32] * b[6] + a[33] * b[14] + a[34] * b[22] + a[35] * b[30] + a[36] * b[38] + a[37] * b[46] + a[38] * b[54] + a[39] * b[62];
            out[39] = a[32] * b[7] + a[33] * b[15] + a[34] * b[23] + a[35] * b[31] + a[36] * b[39] + a[37] * b[47] + a[38] * b[55] + a[39] * b[63];
            out[40] = a[40] * b[0] + a[41] * b[8] + a[42] * b[16] + a[43] * b[24] + a[44] * b[32] + a[45] * b[40] + a[46] * b[48] + a[47] * b[56];
            out[41] = a[40] * b[1] + a[41] * b[9] + a[42] * b[17] + a[43] * b[25] + a[44] * b[33] + a[45] * b[41] + a[46] * b[49] + a[47] * b[57];
            out[42] = a[40] * b[2] + a[41] * b[10] + a[42] * b[18] + a[43] * b[26] + a[44] * b[34] + a[45] * b[42] + a[46] * b[50] + a[47] * b[58];
            out[43] = a[40] * b[3] + a[41] * b[11] + a[42] * b[19] + a[43] * b[27] + a[44] * b[35] + a[45] * b[43] + a[46] * b[51] + a[47] * b[59];
            out[44] = a[40] * b[4] + a[41] * b[12] + a[42] * b[20] + a[43] * b[28] + a[44] * b[36] + a[45] * b[44] + a[46] * b[52] + a[47] * b[60];
            out[45] = a[40] * b[5] + a[41] * b[13] + a[42] * b[21] + a[43] * b[29] + a[44] * b[37] + a[45] * b[45] + a[46] * b[53] + a[47] * b[61];
            out[46] = a[40] * b[6] + a[41] * b[14] + a[42] * b[22] + a[43] * b[30] + a[44] * b[38] + a[45] * b[46] + a[46] * b[54] + a[47] * b[62];
            out[47] = a[40] * b[7] + a[41] * b[15] + a[42] * b[23] + a[43] * b[31] + a[44] * b[39] + a[45] * b[47] + a[46] * b[55] + a[47] * b[63];
            out[48] = a[48] * b[0] + a[49] * b[8] + a[50] * b[16] + a[51] * b[24] + a[52] * b[32] + a[53] * b[40] + a[54] * b[48] + a[55] * b[56];
            out[49] = a[48] * b[1] + a[49] * b[9] + a[50] * b[17] + a[51] * b[25] + a[52] * b[33] + a[53] * b[41] + a[54] * b[49] + a[55] * b[57];
            out[50] = a[48] * b[2] + a[49] * b[10] + a[50] * b[18] + a[51] * b[26] + a[52] * b[34] + a[53] * b[42] + a[54] * b[50] + a[55] * b[58];
            out[51] = a[48] * b[3] + a[49] * b[11] + a[50] * b[19] + a[51] * b[27] + a[52] * b[35] + a[53] * b[43] + a[54] * b[51] + a[55] * b[59];
            out[52] = a[48] * b[4] + a[49] * b[12] + a[50] * b[20] + a[51] * b[28] + a[52] * b[36] + a[53] * b[44] + a[54] * b[52] + a[55] * b[60];
            out[53] = a[48] * b[5] + a[49] * b[13] + a[50] * b[21] + a[51] * b[29] + a[52] * b[37] + a[53] * b[45] + a[54] * b[53] + a[55] * b[61];
            out[54] = a[48] * b[6] + a[49] * b[14] + a[50] * b[22] + a[51] * b[30] + a[52] * b[38] + a[53] * b[46] + a[54] * b[54] + a[55] * b[62];
            out[55] = a[48] * b[7] + a[49] * b[15] + a[50] * b[23] + a[51] * b[31] + a[52] * b[39] + a[53] * b[47] + a[54] * b[55] + a[55] * b[63];
            out[56] = a[56] * b[0] + a[57] * b[8] + a[58] * b[16] + a[59] * b[24] + a[60] * b[32] + a[61] * b[40] + a[62] * b[48] + a[63] * b[56];
            out[57] = a[56] * b[1] + a[57] * b[9] + a[58] * b[17] + a[59] * b[25] + a[60] * b[33] + a[61] * b[41] + a[62] * b[49] + a[63] * b[57];
            out[58] = a[56] * b[2] + a[57] * b[10] + a[58] * b[18] + a[59] * b[26] + a[60] * b[34] + a[61] * b[42] + a[62] * b[50] + a[63] * b[58];
            out[59] = a[56] * b[3] + a[57] * b[11] + a[58] * b[19] + a[59] * b[27] + a[60] * b[35] + a[61] * b[43] + a[62] * b[51] + a[63] * b[59];
            out[60] = a[56] * b[4] + a[57] * b[12] + a[58] * b[20] + a[59] * b[28] + a[60] * b[36] + a[61] * b[44] + a[62] * b[52] + a[63] * b[60];
            out[61] = a[56] * b[5] + a[57] * b[13] + a[58] * b[21] + a[59] * b[29] + a[60] * b[37] + a[61] * b[45] + a[62] * b[53] + a[63] * b[61];
            out[62] = a[56] * b[6] + a[57] * b[14] + a[58] * b[22] + a[59] * b[30] + a[60] * b[38] + a[61] * b[46] + a[62] * b[54] + a[63] * b[62];
            out[63] = a[56] * b[7] + a[57] * b[15] + a[58] * b[23] + a[59] * b[31] + a[60] * b[39] + a[61] * b[47] + a[62] * b[55] + a[63] * b[63];
            checksum += out[0] + out[63];
        }
    }

    /** Hand-unrolled, order 8 — the human who knew and gave up generality. */
    public static final class Fixed8 {
        public final double[] a = new double[64], b = new double[64], out = new double[64];
        public double checksum;

        public Fixed8() {
            for (int i = 0; i < 64; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
        }

        public void onEvent(MatrixEvent e) {
            b[0] = e.scale;
            out[0] = a[0] * b[0] + a[1] * b[8] + a[2] * b[16] + a[3] * b[24] + a[4] * b[32] + a[5] * b[40] + a[6] * b[48] + a[7] * b[56];
            out[1] = a[0] * b[1] + a[1] * b[9] + a[2] * b[17] + a[3] * b[25] + a[4] * b[33] + a[5] * b[41] + a[6] * b[49] + a[7] * b[57];
            out[2] = a[0] * b[2] + a[1] * b[10] + a[2] * b[18] + a[3] * b[26] + a[4] * b[34] + a[5] * b[42] + a[6] * b[50] + a[7] * b[58];
            out[3] = a[0] * b[3] + a[1] * b[11] + a[2] * b[19] + a[3] * b[27] + a[4] * b[35] + a[5] * b[43] + a[6] * b[51] + a[7] * b[59];
            out[4] = a[0] * b[4] + a[1] * b[12] + a[2] * b[20] + a[3] * b[28] + a[4] * b[36] + a[5] * b[44] + a[6] * b[52] + a[7] * b[60];
            out[5] = a[0] * b[5] + a[1] * b[13] + a[2] * b[21] + a[3] * b[29] + a[4] * b[37] + a[5] * b[45] + a[6] * b[53] + a[7] * b[61];
            out[6] = a[0] * b[6] + a[1] * b[14] + a[2] * b[22] + a[3] * b[30] + a[4] * b[38] + a[5] * b[46] + a[6] * b[54] + a[7] * b[62];
            out[7] = a[0] * b[7] + a[1] * b[15] + a[2] * b[23] + a[3] * b[31] + a[4] * b[39] + a[5] * b[47] + a[6] * b[55] + a[7] * b[63];
            out[8] = a[8] * b[0] + a[9] * b[8] + a[10] * b[16] + a[11] * b[24] + a[12] * b[32] + a[13] * b[40] + a[14] * b[48] + a[15] * b[56];
            out[9] = a[8] * b[1] + a[9] * b[9] + a[10] * b[17] + a[11] * b[25] + a[12] * b[33] + a[13] * b[41] + a[14] * b[49] + a[15] * b[57];
            out[10] = a[8] * b[2] + a[9] * b[10] + a[10] * b[18] + a[11] * b[26] + a[12] * b[34] + a[13] * b[42] + a[14] * b[50] + a[15] * b[58];
            out[11] = a[8] * b[3] + a[9] * b[11] + a[10] * b[19] + a[11] * b[27] + a[12] * b[35] + a[13] * b[43] + a[14] * b[51] + a[15] * b[59];
            out[12] = a[8] * b[4] + a[9] * b[12] + a[10] * b[20] + a[11] * b[28] + a[12] * b[36] + a[13] * b[44] + a[14] * b[52] + a[15] * b[60];
            out[13] = a[8] * b[5] + a[9] * b[13] + a[10] * b[21] + a[11] * b[29] + a[12] * b[37] + a[13] * b[45] + a[14] * b[53] + a[15] * b[61];
            out[14] = a[8] * b[6] + a[9] * b[14] + a[10] * b[22] + a[11] * b[30] + a[12] * b[38] + a[13] * b[46] + a[14] * b[54] + a[15] * b[62];
            out[15] = a[8] * b[7] + a[9] * b[15] + a[10] * b[23] + a[11] * b[31] + a[12] * b[39] + a[13] * b[47] + a[14] * b[55] + a[15] * b[63];
            out[16] = a[16] * b[0] + a[17] * b[8] + a[18] * b[16] + a[19] * b[24] + a[20] * b[32] + a[21] * b[40] + a[22] * b[48] + a[23] * b[56];
            out[17] = a[16] * b[1] + a[17] * b[9] + a[18] * b[17] + a[19] * b[25] + a[20] * b[33] + a[21] * b[41] + a[22] * b[49] + a[23] * b[57];
            out[18] = a[16] * b[2] + a[17] * b[10] + a[18] * b[18] + a[19] * b[26] + a[20] * b[34] + a[21] * b[42] + a[22] * b[50] + a[23] * b[58];
            out[19] = a[16] * b[3] + a[17] * b[11] + a[18] * b[19] + a[19] * b[27] + a[20] * b[35] + a[21] * b[43] + a[22] * b[51] + a[23] * b[59];
            out[20] = a[16] * b[4] + a[17] * b[12] + a[18] * b[20] + a[19] * b[28] + a[20] * b[36] + a[21] * b[44] + a[22] * b[52] + a[23] * b[60];
            out[21] = a[16] * b[5] + a[17] * b[13] + a[18] * b[21] + a[19] * b[29] + a[20] * b[37] + a[21] * b[45] + a[22] * b[53] + a[23] * b[61];
            out[22] = a[16] * b[6] + a[17] * b[14] + a[18] * b[22] + a[19] * b[30] + a[20] * b[38] + a[21] * b[46] + a[22] * b[54] + a[23] * b[62];
            out[23] = a[16] * b[7] + a[17] * b[15] + a[18] * b[23] + a[19] * b[31] + a[20] * b[39] + a[21] * b[47] + a[22] * b[55] + a[23] * b[63];
            out[24] = a[24] * b[0] + a[25] * b[8] + a[26] * b[16] + a[27] * b[24] + a[28] * b[32] + a[29] * b[40] + a[30] * b[48] + a[31] * b[56];
            out[25] = a[24] * b[1] + a[25] * b[9] + a[26] * b[17] + a[27] * b[25] + a[28] * b[33] + a[29] * b[41] + a[30] * b[49] + a[31] * b[57];
            out[26] = a[24] * b[2] + a[25] * b[10] + a[26] * b[18] + a[27] * b[26] + a[28] * b[34] + a[29] * b[42] + a[30] * b[50] + a[31] * b[58];
            out[27] = a[24] * b[3] + a[25] * b[11] + a[26] * b[19] + a[27] * b[27] + a[28] * b[35] + a[29] * b[43] + a[30] * b[51] + a[31] * b[59];
            out[28] = a[24] * b[4] + a[25] * b[12] + a[26] * b[20] + a[27] * b[28] + a[28] * b[36] + a[29] * b[44] + a[30] * b[52] + a[31] * b[60];
            out[29] = a[24] * b[5] + a[25] * b[13] + a[26] * b[21] + a[27] * b[29] + a[28] * b[37] + a[29] * b[45] + a[30] * b[53] + a[31] * b[61];
            out[30] = a[24] * b[6] + a[25] * b[14] + a[26] * b[22] + a[27] * b[30] + a[28] * b[38] + a[29] * b[46] + a[30] * b[54] + a[31] * b[62];
            out[31] = a[24] * b[7] + a[25] * b[15] + a[26] * b[23] + a[27] * b[31] + a[28] * b[39] + a[29] * b[47] + a[30] * b[55] + a[31] * b[63];
            out[32] = a[32] * b[0] + a[33] * b[8] + a[34] * b[16] + a[35] * b[24] + a[36] * b[32] + a[37] * b[40] + a[38] * b[48] + a[39] * b[56];
            out[33] = a[32] * b[1] + a[33] * b[9] + a[34] * b[17] + a[35] * b[25] + a[36] * b[33] + a[37] * b[41] + a[38] * b[49] + a[39] * b[57];
            out[34] = a[32] * b[2] + a[33] * b[10] + a[34] * b[18] + a[35] * b[26] + a[36] * b[34] + a[37] * b[42] + a[38] * b[50] + a[39] * b[58];
            out[35] = a[32] * b[3] + a[33] * b[11] + a[34] * b[19] + a[35] * b[27] + a[36] * b[35] + a[37] * b[43] + a[38] * b[51] + a[39] * b[59];
            out[36] = a[32] * b[4] + a[33] * b[12] + a[34] * b[20] + a[35] * b[28] + a[36] * b[36] + a[37] * b[44] + a[38] * b[52] + a[39] * b[60];
            out[37] = a[32] * b[5] + a[33] * b[13] + a[34] * b[21] + a[35] * b[29] + a[36] * b[37] + a[37] * b[45] + a[38] * b[53] + a[39] * b[61];
            out[38] = a[32] * b[6] + a[33] * b[14] + a[34] * b[22] + a[35] * b[30] + a[36] * b[38] + a[37] * b[46] + a[38] * b[54] + a[39] * b[62];
            out[39] = a[32] * b[7] + a[33] * b[15] + a[34] * b[23] + a[35] * b[31] + a[36] * b[39] + a[37] * b[47] + a[38] * b[55] + a[39] * b[63];
            out[40] = a[40] * b[0] + a[41] * b[8] + a[42] * b[16] + a[43] * b[24] + a[44] * b[32] + a[45] * b[40] + a[46] * b[48] + a[47] * b[56];
            out[41] = a[40] * b[1] + a[41] * b[9] + a[42] * b[17] + a[43] * b[25] + a[44] * b[33] + a[45] * b[41] + a[46] * b[49] + a[47] * b[57];
            out[42] = a[40] * b[2] + a[41] * b[10] + a[42] * b[18] + a[43] * b[26] + a[44] * b[34] + a[45] * b[42] + a[46] * b[50] + a[47] * b[58];
            out[43] = a[40] * b[3] + a[41] * b[11] + a[42] * b[19] + a[43] * b[27] + a[44] * b[35] + a[45] * b[43] + a[46] * b[51] + a[47] * b[59];
            out[44] = a[40] * b[4] + a[41] * b[12] + a[42] * b[20] + a[43] * b[28] + a[44] * b[36] + a[45] * b[44] + a[46] * b[52] + a[47] * b[60];
            out[45] = a[40] * b[5] + a[41] * b[13] + a[42] * b[21] + a[43] * b[29] + a[44] * b[37] + a[45] * b[45] + a[46] * b[53] + a[47] * b[61];
            out[46] = a[40] * b[6] + a[41] * b[14] + a[42] * b[22] + a[43] * b[30] + a[44] * b[38] + a[45] * b[46] + a[46] * b[54] + a[47] * b[62];
            out[47] = a[40] * b[7] + a[41] * b[15] + a[42] * b[23] + a[43] * b[31] + a[44] * b[39] + a[45] * b[47] + a[46] * b[55] + a[47] * b[63];
            out[48] = a[48] * b[0] + a[49] * b[8] + a[50] * b[16] + a[51] * b[24] + a[52] * b[32] + a[53] * b[40] + a[54] * b[48] + a[55] * b[56];
            out[49] = a[48] * b[1] + a[49] * b[9] + a[50] * b[17] + a[51] * b[25] + a[52] * b[33] + a[53] * b[41] + a[54] * b[49] + a[55] * b[57];
            out[50] = a[48] * b[2] + a[49] * b[10] + a[50] * b[18] + a[51] * b[26] + a[52] * b[34] + a[53] * b[42] + a[54] * b[50] + a[55] * b[58];
            out[51] = a[48] * b[3] + a[49] * b[11] + a[50] * b[19] + a[51] * b[27] + a[52] * b[35] + a[53] * b[43] + a[54] * b[51] + a[55] * b[59];
            out[52] = a[48] * b[4] + a[49] * b[12] + a[50] * b[20] + a[51] * b[28] + a[52] * b[36] + a[53] * b[44] + a[54] * b[52] + a[55] * b[60];
            out[53] = a[48] * b[5] + a[49] * b[13] + a[50] * b[21] + a[51] * b[29] + a[52] * b[37] + a[53] * b[45] + a[54] * b[53] + a[55] * b[61];
            out[54] = a[48] * b[6] + a[49] * b[14] + a[50] * b[22] + a[51] * b[30] + a[52] * b[38] + a[53] * b[46] + a[54] * b[54] + a[55] * b[62];
            out[55] = a[48] * b[7] + a[49] * b[15] + a[50] * b[23] + a[51] * b[31] + a[52] * b[39] + a[53] * b[47] + a[54] * b[55] + a[55] * b[63];
            out[56] = a[56] * b[0] + a[57] * b[8] + a[58] * b[16] + a[59] * b[24] + a[60] * b[32] + a[61] * b[40] + a[62] * b[48] + a[63] * b[56];
            out[57] = a[56] * b[1] + a[57] * b[9] + a[58] * b[17] + a[59] * b[25] + a[60] * b[33] + a[61] * b[41] + a[62] * b[49] + a[63] * b[57];
            out[58] = a[56] * b[2] + a[57] * b[10] + a[58] * b[18] + a[59] * b[26] + a[60] * b[34] + a[61] * b[42] + a[62] * b[50] + a[63] * b[58];
            out[59] = a[56] * b[3] + a[57] * b[11] + a[58] * b[19] + a[59] * b[27] + a[60] * b[35] + a[61] * b[43] + a[62] * b[51] + a[63] * b[59];
            out[60] = a[56] * b[4] + a[57] * b[12] + a[58] * b[20] + a[59] * b[28] + a[60] * b[36] + a[61] * b[44] + a[62] * b[52] + a[63] * b[60];
            out[61] = a[56] * b[5] + a[57] * b[13] + a[58] * b[21] + a[59] * b[29] + a[60] * b[37] + a[61] * b[45] + a[62] * b[53] + a[63] * b[61];
            out[62] = a[56] * b[6] + a[57] * b[14] + a[58] * b[22] + a[59] * b[30] + a[60] * b[38] + a[61] * b[46] + a[62] * b[54] + a[63] * b[62];
            out[63] = a[56] * b[7] + a[57] * b[15] + a[58] * b[23] + a[59] * b[31] + a[60] * b[39] + a[61] * b[47] + a[62] * b[55] + a[63] * b[63];
            checksum += out[0] + out[63];
        }
    }

    /** The library: order is a runtime value, so nothing folds. */
    public static final class GenericN {
        public final double[] a, b, out;
        private final int n, last;
        public double checksum;

        public GenericN(int n) {
            this.n = n;
            this.last = n * n - 1;
            this.a = new double[n * n];
            this.b = new double[n * n];
            this.out = new double[n * n];
            for (int i = 0; i < n * n; i++) { a[i] = 1.0 + i; b[i] = 0.5 + i; }
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
            checksum += out[0] + out[last];
        }
    }
}
