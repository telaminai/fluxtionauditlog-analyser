public class DispatchProbe {
    public sealed interface Ev permits E0, E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11, E12, E13, E14, E15, E16, E17, E18, E19, E20, E21, E22, E23, E24, E25, E26, E27, E28, E29, E30, E31, E32, E33, E34, E35, E36, E37, E38, E39, E40, E41, E42, E43, E44, E45, E46, E47, E48, E49, E50, E51, E52, E53, E54, E55, E56, E57, E58, E59, E60, E61, E62, E63 { int id(); }
    public static final class E0 implements Ev { public int id(){ return 0; } }
    public static final class E1 implements Ev { public int id(){ return 1; } }
    public static final class E2 implements Ev { public int id(){ return 2; } }
    public static final class E3 implements Ev { public int id(){ return 3; } }
    public static final class E4 implements Ev { public int id(){ return 4; } }
    public static final class E5 implements Ev { public int id(){ return 5; } }
    public static final class E6 implements Ev { public int id(){ return 6; } }
    public static final class E7 implements Ev { public int id(){ return 7; } }
    public static final class E8 implements Ev { public int id(){ return 8; } }
    public static final class E9 implements Ev { public int id(){ return 9; } }
    public static final class E10 implements Ev { public int id(){ return 10; } }
    public static final class E11 implements Ev { public int id(){ return 11; } }
    public static final class E12 implements Ev { public int id(){ return 12; } }
    public static final class E13 implements Ev { public int id(){ return 13; } }
    public static final class E14 implements Ev { public int id(){ return 14; } }
    public static final class E15 implements Ev { public int id(){ return 15; } }
    public static final class E16 implements Ev { public int id(){ return 16; } }
    public static final class E17 implements Ev { public int id(){ return 17; } }
    public static final class E18 implements Ev { public int id(){ return 18; } }
    public static final class E19 implements Ev { public int id(){ return 19; } }
    public static final class E20 implements Ev { public int id(){ return 20; } }
    public static final class E21 implements Ev { public int id(){ return 21; } }
    public static final class E22 implements Ev { public int id(){ return 22; } }
    public static final class E23 implements Ev { public int id(){ return 23; } }
    public static final class E24 implements Ev { public int id(){ return 24; } }
    public static final class E25 implements Ev { public int id(){ return 25; } }
    public static final class E26 implements Ev { public int id(){ return 26; } }
    public static final class E27 implements Ev { public int id(){ return 27; } }
    public static final class E28 implements Ev { public int id(){ return 28; } }
    public static final class E29 implements Ev { public int id(){ return 29; } }
    public static final class E30 implements Ev { public int id(){ return 30; } }
    public static final class E31 implements Ev { public int id(){ return 31; } }
    public static final class E32 implements Ev { public int id(){ return 32; } }
    public static final class E33 implements Ev { public int id(){ return 33; } }
    public static final class E34 implements Ev { public int id(){ return 34; } }
    public static final class E35 implements Ev { public int id(){ return 35; } }
    public static final class E36 implements Ev { public int id(){ return 36; } }
    public static final class E37 implements Ev { public int id(){ return 37; } }
    public static final class E38 implements Ev { public int id(){ return 38; } }
    public static final class E39 implements Ev { public int id(){ return 39; } }
    public static final class E40 implements Ev { public int id(){ return 40; } }
    public static final class E41 implements Ev { public int id(){ return 41; } }
    public static final class E42 implements Ev { public int id(){ return 42; } }
    public static final class E43 implements Ev { public int id(){ return 43; } }
    public static final class E44 implements Ev { public int id(){ return 44; } }
    public static final class E45 implements Ev { public int id(){ return 45; } }
    public static final class E46 implements Ev { public int id(){ return 46; } }
    public static final class E47 implements Ev { public int id(){ return 47; } }
    public static final class E48 implements Ev { public int id(){ return 48; } }
    public static final class E49 implements Ev { public int id(){ return 49; } }
    public static final class E50 implements Ev { public int id(){ return 50; } }
    public static final class E51 implements Ev { public int id(){ return 51; } }
    public static final class E52 implements Ev { public int id(){ return 52; } }
    public static final class E53 implements Ev { public int id(){ return 53; } }
    public static final class E54 implements Ev { public int id(){ return 54; } }
    public static final class E55 implements Ev { public int id(){ return 55; } }
    public static final class E56 implements Ev { public int id(){ return 56; } }
    public static final class E57 implements Ev { public int id(){ return 57; } }
    public static final class E58 implements Ev { public int id(){ return 58; } }
    public static final class E59 implements Ev { public int id(){ return 59; } }
    public static final class E60 implements Ev { public int id(){ return 60; } }
    public static final class E61 implements Ev { public int id(){ return 61; } }
    public static final class E62 implements Ev { public int id(){ return 62; } }
    public static final class E63 implements Ev { public int id(){ return 63; } }
    static Object[] EVS; static double acc;
    static void work(long k){ acc = acc*0.5 + (k & 15); }
    static void fill(int types){ EVS=new Object[256]; Object[] all=new Object[]{
        new E0(), new E1(), new E2(), new E3(), new E4(), new E5(), new E6(), new E7(), new E8(), new E9(), new E10(), new E11(), new E12(), new E13(), new E14(), new E15(), new E16(), new E17(), new E18(), new E19(), new E20(), new E21(), new E22(), new E23(), new E24(), new E25(), new E26(), new E27(), new E28(), new E29(), new E30(), new E31(), new E32(), new E33(), new E34(), new E35(), new E36(), new E37(), new E38(), new E39(), new E40(), new E41(), new E42(), new E43(), new E44(), new E45(), new E46(), new E47(), new E48(), new E49(), new E50(), new E51(), new E52(), new E53(), new E54(), new E55(), new E56(), new E57(), new E58(), new E59(), new E60(), new E61(), new E62(), new E63()};
        for(int i=0;i<256;i++) EVS[i]=all[i%types]; }
    static void chain(Object o,long k){
        if (o instanceof E0) { work(k); return; }
        if (o instanceof E1) { work(k); return; }
        if (o instanceof E2) { work(k); return; }
        if (o instanceof E3) { work(k); return; }
        if (o instanceof E4) { work(k); return; }
        if (o instanceof E5) { work(k); return; }
        if (o instanceof E6) { work(k); return; }
        if (o instanceof E7) { work(k); return; }
        if (o instanceof E8) { work(k); return; }
        if (o instanceof E9) { work(k); return; }
        if (o instanceof E10) { work(k); return; }
        if (o instanceof E11) { work(k); return; }
        if (o instanceof E12) { work(k); return; }
        if (o instanceof E13) { work(k); return; }
        if (o instanceof E14) { work(k); return; }
        if (o instanceof E15) { work(k); return; }
        if (o instanceof E16) { work(k); return; }
        if (o instanceof E17) { work(k); return; }
        if (o instanceof E18) { work(k); return; }
        if (o instanceof E19) { work(k); return; }
        if (o instanceof E20) { work(k); return; }
        if (o instanceof E21) { work(k); return; }
        if (o instanceof E22) { work(k); return; }
        if (o instanceof E23) { work(k); return; }
        if (o instanceof E24) { work(k); return; }
        if (o instanceof E25) { work(k); return; }
        if (o instanceof E26) { work(k); return; }
        if (o instanceof E27) { work(k); return; }
        if (o instanceof E28) { work(k); return; }
        if (o instanceof E29) { work(k); return; }
        if (o instanceof E30) { work(k); return; }
        if (o instanceof E31) { work(k); return; }
        if (o instanceof E32) { work(k); return; }
        if (o instanceof E33) { work(k); return; }
        if (o instanceof E34) { work(k); return; }
        if (o instanceof E35) { work(k); return; }
        if (o instanceof E36) { work(k); return; }
        if (o instanceof E37) { work(k); return; }
        if (o instanceof E38) { work(k); return; }
        if (o instanceof E39) { work(k); return; }
        if (o instanceof E40) { work(k); return; }
        if (o instanceof E41) { work(k); return; }
        if (o instanceof E42) { work(k); return; }
        if (o instanceof E43) { work(k); return; }
        if (o instanceof E44) { work(k); return; }
        if (o instanceof E45) { work(k); return; }
        if (o instanceof E46) { work(k); return; }
        if (o instanceof E47) { work(k); return; }
        if (o instanceof E48) { work(k); return; }
        if (o instanceof E49) { work(k); return; }
        if (o instanceof E50) { work(k); return; }
        if (o instanceof E51) { work(k); return; }
        if (o instanceof E52) { work(k); return; }
        if (o instanceof E53) { work(k); return; }
        if (o instanceof E54) { work(k); return; }
        if (o instanceof E55) { work(k); return; }
        if (o instanceof E56) { work(k); return; }
        if (o instanceof E57) { work(k); return; }
        if (o instanceof E58) { work(k); return; }
        if (o instanceof E59) { work(k); return; }
        if (o instanceof E60) { work(k); return; }
        if (o instanceof E61) { work(k); return; }
        if (o instanceof E62) { work(k); return; }
        if (o instanceof E63) { work(k); return; }
    }
    static void idSwitch(Object o,long k){ switch(((Ev)o).id()){
        case 0: work(k); return;
        case 1: work(k); return;
        case 2: work(k); return;
        case 3: work(k); return;
        case 4: work(k); return;
        case 5: work(k); return;
        case 6: work(k); return;
        case 7: work(k); return;
        case 8: work(k); return;
        case 9: work(k); return;
        case 10: work(k); return;
        case 11: work(k); return;
        case 12: work(k); return;
        case 13: work(k); return;
        case 14: work(k); return;
        case 15: work(k); return;
        case 16: work(k); return;
        case 17: work(k); return;
        case 18: work(k); return;
        case 19: work(k); return;
        case 20: work(k); return;
        case 21: work(k); return;
        case 22: work(k); return;
        case 23: work(k); return;
        case 24: work(k); return;
        case 25: work(k); return;
        case 26: work(k); return;
        case 27: work(k); return;
        case 28: work(k); return;
        case 29: work(k); return;
        case 30: work(k); return;
        case 31: work(k); return;
        case 32: work(k); return;
        case 33: work(k); return;
        case 34: work(k); return;
        case 35: work(k); return;
        case 36: work(k); return;
        case 37: work(k); return;
        case 38: work(k); return;
        case 39: work(k); return;
        case 40: work(k); return;
        case 41: work(k); return;
        case 42: work(k); return;
        case 43: work(k); return;
        case 44: work(k); return;
        case 45: work(k); return;
        case 46: work(k); return;
        case 47: work(k); return;
        case 48: work(k); return;
        case 49: work(k); return;
        case 50: work(k); return;
        case 51: work(k); return;
        case 52: work(k); return;
        case 53: work(k); return;
        case 54: work(k); return;
        case 55: work(k); return;
        case 56: work(k); return;
        case 57: work(k); return;
        case 58: work(k); return;
        case 59: work(k); return;
        case 60: work(k); return;
        case 61: work(k); return;
        case 62: work(k); return;
        case 63: work(k); return;
        default: return; } }
    static void classSwitch(Object o,long k){ Class<?> c=o.getClass();
        if (c==E0.class) { work(k); return; }
        if (c==E1.class) { work(k); return; }
        if (c==E2.class) { work(k); return; }
        if (c==E3.class) { work(k); return; }
        if (c==E4.class) { work(k); return; }
        if (c==E5.class) { work(k); return; }
        if (c==E6.class) { work(k); return; }
        if (c==E7.class) { work(k); return; }
        if (c==E8.class) { work(k); return; }
        if (c==E9.class) { work(k); return; }
        if (c==E10.class) { work(k); return; }
        if (c==E11.class) { work(k); return; }
        if (c==E12.class) { work(k); return; }
        if (c==E13.class) { work(k); return; }
        if (c==E14.class) { work(k); return; }
        if (c==E15.class) { work(k); return; }
        if (c==E16.class) { work(k); return; }
        if (c==E17.class) { work(k); return; }
        if (c==E18.class) { work(k); return; }
        if (c==E19.class) { work(k); return; }
        if (c==E20.class) { work(k); return; }
        if (c==E21.class) { work(k); return; }
        if (c==E22.class) { work(k); return; }
        if (c==E23.class) { work(k); return; }
        if (c==E24.class) { work(k); return; }
        if (c==E25.class) { work(k); return; }
        if (c==E26.class) { work(k); return; }
        if (c==E27.class) { work(k); return; }
        if (c==E28.class) { work(k); return; }
        if (c==E29.class) { work(k); return; }
        if (c==E30.class) { work(k); return; }
        if (c==E31.class) { work(k); return; }
        if (c==E32.class) { work(k); return; }
        if (c==E33.class) { work(k); return; }
        if (c==E34.class) { work(k); return; }
        if (c==E35.class) { work(k); return; }
        if (c==E36.class) { work(k); return; }
        if (c==E37.class) { work(k); return; }
        if (c==E38.class) { work(k); return; }
        if (c==E39.class) { work(k); return; }
        if (c==E40.class) { work(k); return; }
        if (c==E41.class) { work(k); return; }
        if (c==E42.class) { work(k); return; }
        if (c==E43.class) { work(k); return; }
        if (c==E44.class) { work(k); return; }
        if (c==E45.class) { work(k); return; }
        if (c==E46.class) { work(k); return; }
        if (c==E47.class) { work(k); return; }
        if (c==E48.class) { work(k); return; }
        if (c==E49.class) { work(k); return; }
        if (c==E50.class) { work(k); return; }
        if (c==E51.class) { work(k); return; }
        if (c==E52.class) { work(k); return; }
        if (c==E53.class) { work(k); return; }
        if (c==E54.class) { work(k); return; }
        if (c==E55.class) { work(k); return; }
        if (c==E56.class) { work(k); return; }
        if (c==E57.class) { work(k); return; }
        if (c==E58.class) { work(k); return; }
        if (c==E59.class) { work(k); return; }
        if (c==E60.class) { work(k); return; }
        if (c==E61.class) { work(k); return; }
        if (c==E62.class) { work(k); return; }
        if (c==E63.class) { work(k); return; }
    }
    static void patternSwitch(Object o,long k){ switch(o){
        case E0 x -> work(k);
        case E1 x -> work(k);
        case E2 x -> work(k);
        case E3 x -> work(k);
        case E4 x -> work(k);
        case E5 x -> work(k);
        case E6 x -> work(k);
        case E7 x -> work(k);
        case E8 x -> work(k);
        case E9 x -> work(k);
        case E10 x -> work(k);
        case E11 x -> work(k);
        case E12 x -> work(k);
        case E13 x -> work(k);
        case E14 x -> work(k);
        case E15 x -> work(k);
        case E16 x -> work(k);
        case E17 x -> work(k);
        case E18 x -> work(k);
        case E19 x -> work(k);
        case E20 x -> work(k);
        case E21 x -> work(k);
        case E22 x -> work(k);
        case E23 x -> work(k);
        case E24 x -> work(k);
        case E25 x -> work(k);
        case E26 x -> work(k);
        case E27 x -> work(k);
        case E28 x -> work(k);
        case E29 x -> work(k);
        case E30 x -> work(k);
        case E31 x -> work(k);
        case E32 x -> work(k);
        case E33 x -> work(k);
        case E34 x -> work(k);
        case E35 x -> work(k);
        case E36 x -> work(k);
        case E37 x -> work(k);
        case E38 x -> work(k);
        case E39 x -> work(k);
        case E40 x -> work(k);
        case E41 x -> work(k);
        case E42 x -> work(k);
        case E43 x -> work(k);
        case E44 x -> work(k);
        case E45 x -> work(k);
        case E46 x -> work(k);
        case E47 x -> work(k);
        case E48 x -> work(k);
        case E49 x -> work(k);
        case E50 x -> work(k);
        case E51 x -> work(k);
        case E52 x -> work(k);
        case E53 x -> work(k);
        case E54 x -> work(k);
        case E55 x -> work(k);
        case E56 x -> work(k);
        case E57 x -> work(k);
        case E58 x -> work(k);
        case E59 x -> work(k);
        case E60 x -> work(k);
        case E61 x -> work(k);
        case E62 x -> work(k);
        case E63 x -> work(k);
        default -> {} } }
    public static void main(String[] a){
        String arm=System.getProperty("arm"); int types=Integer.getInteger("types",16);
        long warm=Long.getLong("warm",5_000_000L), it=Long.getLong("iters",200_000_000L);
        fill(types); long t0,ns;
        switch(arm){
          case "chain": for(long k=0;k<warm;k++) chain(EVS[(int)(k&255)],k);
              t0=System.nanoTime(); for(long k=0;k<it;k++) chain(EVS[(int)(k&255)],k); ns=System.nanoTime()-t0; break;
          case "idSwitch": for(long k=0;k<warm;k++) idSwitch(EVS[(int)(k&255)],k);
              t0=System.nanoTime(); for(long k=0;k<it;k++) idSwitch(EVS[(int)(k&255)],k); ns=System.nanoTime()-t0; break;
          case "classSwitch": for(long k=0;k<warm;k++) classSwitch(EVS[(int)(k&255)],k);
              t0=System.nanoTime(); for(long k=0;k<it;k++) classSwitch(EVS[(int)(k&255)],k); ns=System.nanoTime()-t0; break;
          case "patternSwitch": for(long k=0;k<warm;k++) patternSwitch(EVS[(int)(k&255)],k);
              t0=System.nanoTime(); for(long k=0;k<it;k++) patternSwitch(EVS[(int)(k&255)],k); ns=System.nanoTime()-t0; break;
          default: ns=0; }
        System.out.printf("RESULT %s types=%d %.4f acc=%.1f%n",arm,types,(double)ns/it,acc); } }
