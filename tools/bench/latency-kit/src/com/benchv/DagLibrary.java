package com.benchv;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;

/** Round 62 §16 CONTROL — library arm, no shared tail. */
public class DagLibrary {

    public interface Step { void fire(); }

    public final DagNodes.R0 r0 = new DagNodes.R0();
    public final DagNodes.R1 r1 = new DagNodes.R1();
    public final DagNodes.R2 r2 = new DagNodes.R2();
    public final DagNodes.R3 r3 = new DagNodes.R3();
    public final DagNodes.R4 r4 = new DagNodes.R4();
    public final DagNodes.D1 c0_0;
    public final DagNodes.D1 c0_1;
    public final DagNodes.D1 c0_2;
    public final DagNodes.D1 c0_3;
    public final DagNodes.D1 c0_4;
    public final DagNodes.D1 c0_5;
    public final DagNodes.D1 c0_6;
    public final DagNodes.D1 c0_7;
    public final DagNodes.D1 c1_0;
    public final DagNodes.D1 c1_1;
    public final DagNodes.D1 c1_2;
    public final DagNodes.D1 c1_3;
    public final DagNodes.D1 c1_4;
    public final DagNodes.D1 c1_5;
    public final DagNodes.D1 c2_0;
    public final DagNodes.D1 c2_1;
    public final DagNodes.D1 c2_2;
    public final DagNodes.D1 c2_3;
    public final DagNodes.D1 c2_4;
    public final DagNodes.D1 c3_0;
    public final DagNodes.D1 c3_1;
    public final DagNodes.D1 c3_2;
    public final DagNodes.D1 c3_3;
    public final DagNodes.D1 c4_0;
    public final DagNodes.D1 c4_1;
    private final Step[] p0;
    private final Step[] p1;
    private final Step[] p2;
    private final Step[] p3;
    private final Step[] p4;

    public DagLibrary() {
        c0_0 = new DagNodes.D1(r0);
        c0_1 = new DagNodes.D1(c0_0);
        c0_2 = new DagNodes.D1(c0_1);
        c0_3 = new DagNodes.D1(c0_2);
        c0_4 = new DagNodes.D1(c0_3);
        c0_5 = new DagNodes.D1(c0_4);
        c0_6 = new DagNodes.D1(c0_5);
        c0_7 = new DagNodes.D1(c0_6);
        c1_0 = new DagNodes.D1(r1);
        c1_1 = new DagNodes.D1(c1_0);
        c1_2 = new DagNodes.D1(c1_1);
        c1_3 = new DagNodes.D1(c1_2);
        c1_4 = new DagNodes.D1(c1_3);
        c1_5 = new DagNodes.D1(c1_4);
        c2_0 = new DagNodes.D1(r2);
        c2_1 = new DagNodes.D1(c2_0);
        c2_2 = new DagNodes.D1(c2_1);
        c2_3 = new DagNodes.D1(c2_2);
        c2_4 = new DagNodes.D1(c2_3);
        c3_0 = new DagNodes.D1(r3);
        c3_1 = new DagNodes.D1(c3_0);
        c3_2 = new DagNodes.D1(c3_1);
        c3_3 = new DagNodes.D1(c3_2);
        c4_0 = new DagNodes.D1(r4);
        c4_1 = new DagNodes.D1(c4_0);
        p0 = new Step[]{c0_0::calc, c0_1::calc, c0_2::calc, c0_3::calc, c0_4::calc, c0_5::calc, c0_6::calc, c0_7::calc};
        p1 = new Step[]{c1_0::calc, c1_1::calc, c1_2::calc, c1_3::calc, c1_4::calc, c1_5::calc};
        p2 = new Step[]{c2_0::calc, c2_1::calc, c2_2::calc, c2_3::calc, c2_4::calc};
        p3 = new Step[]{c3_0::calc, c3_1::calc, c3_2::calc, c3_3::calc};
        p4 = new Step[]{c4_0::calc, c4_1::calc};
    }

    public void onEvent(Object e) {
        Step[] plan;
        if (e instanceof E0) { r0.on((E0) e); plan = p0; }
        else if (e instanceof E1) { r1.on((E1) e); plan = p1; }
        else if (e instanceof E2) { r2.on((E2) e); plan = p2; }
        else if (e instanceof E3) { r3.on((E3) e); plan = p3; }
        else if (e instanceof E4) { r4.on((E4) e); plan = p4; }
        else { return; }
        for (int i = 0; i < plan.length; i++) { plan[i].fire(); }
    }

    public double checksum() { return c0_7.v + c1_5.v + c2_4.v + c3_3.v + c4_1.v; }
}
