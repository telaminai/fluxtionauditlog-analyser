package com.benchi;
import com.bench.MarketTick;
/** K of the 10 node fields interface-typed; the remainder concrete. Generated. */
public class Mixed {
    public static final class P0 {
        private final Chain.TickInA _t = new Chain.TickInA();
        private final Chain.MidA _m = new Chain.MidA(_t);
        private final Chain.EwmaA _e = new Chain.EwmaA(_m);
        private final Chain.SpreadA _s = new Chain.SpreadA(_t);
        private final Chain.NotionalA _n = new Chain.NotionalA(_m,_s);
        private final Chain.VolA _v = new Chain.VolA(_m,_e);
        private final Chain.ExposureA _x = new Chain.ExposureA(_n,_v);
        private final Chain.LimitA _l = new Chain.LimitA(_x);
        private final Chain.ChargeA _c = new Chain.ChargeA(_x);
        private final Chain.BufferA _b = new Chain.BufferA(_c);
        private final Chain.TickInA tickIn = _t;
        private final Chain.MidA mid = _m;
        private final Chain.EwmaA ewma = _e;
        private final Chain.SpreadA spread = _s;
        private final Chain.NotionalA notional = _n;
        private final Chain.VolA vol = _v;
        private final Chain.ExposureA exposure = _x;
        private final Chain.LimitA limit = _l;
        private final Chain.ChargeA charge = _c;
        private final Chain.BufferA buffer = _b;
        public void handleEvent(MarketTick e){
            tickIn.onTick(e);
            mid.calc();
            ewma.calc();
            spread.calc();
            notional.calc();
            vol.calc();
            exposure.calc();
            limit.calc();
            charge.calc();
            buffer.calc();
        }
        public double buf(){ return _b.value(); }
        public long breaches(){ return _l.breaches(); }
        public long updates(){ return _b.updates(); }
    }
    public static final class P3 {
        private final Chain.TickInA _t = new Chain.TickInA();
        private final Chain.MidA _m = new Chain.MidA(_t);
        private final Chain.EwmaA _e = new Chain.EwmaA(_m);
        private final Chain.SpreadA _s = new Chain.SpreadA(_t);
        private final Chain.NotionalA _n = new Chain.NotionalA(_m,_s);
        private final Chain.VolA _v = new Chain.VolA(_m,_e);
        private final Chain.ExposureA _x = new Chain.ExposureA(_n,_v);
        private final Chain.LimitA _l = new Chain.LimitA(_x);
        private final Chain.ChargeA _c = new Chain.ChargeA(_x);
        private final Chain.BufferA _b = new Chain.BufferA(_c);
        private final Chain.ITickIn tickIn = _t;
        private final Chain.IMid mid = _m;
        private final Chain.IEwma ewma = _e;
        private final Chain.SpreadA spread = _s;
        private final Chain.NotionalA notional = _n;
        private final Chain.VolA vol = _v;
        private final Chain.ExposureA exposure = _x;
        private final Chain.LimitA limit = _l;
        private final Chain.ChargeA charge = _c;
        private final Chain.BufferA buffer = _b;
        public void handleEvent(MarketTick e){
            tickIn.onTick(e);
            mid.calc();
            ewma.calc();
            spread.calc();
            notional.calc();
            vol.calc();
            exposure.calc();
            limit.calc();
            charge.calc();
            buffer.calc();
        }
        public double buf(){ return _b.value(); }
        public long breaches(){ return _l.breaches(); }
        public long updates(){ return _b.updates(); }
    }
    public static final class P6 {
        private final Chain.TickInA _t = new Chain.TickInA();
        private final Chain.MidA _m = new Chain.MidA(_t);
        private final Chain.EwmaA _e = new Chain.EwmaA(_m);
        private final Chain.SpreadA _s = new Chain.SpreadA(_t);
        private final Chain.NotionalA _n = new Chain.NotionalA(_m,_s);
        private final Chain.VolA _v = new Chain.VolA(_m,_e);
        private final Chain.ExposureA _x = new Chain.ExposureA(_n,_v);
        private final Chain.LimitA _l = new Chain.LimitA(_x);
        private final Chain.ChargeA _c = new Chain.ChargeA(_x);
        private final Chain.BufferA _b = new Chain.BufferA(_c);
        private final Chain.ITickIn tickIn = _t;
        private final Chain.IMid mid = _m;
        private final Chain.IEwma ewma = _e;
        private final Chain.ISpread spread = _s;
        private final Chain.INotional notional = _n;
        private final Chain.IVol vol = _v;
        private final Chain.ExposureA exposure = _x;
        private final Chain.LimitA limit = _l;
        private final Chain.ChargeA charge = _c;
        private final Chain.BufferA buffer = _b;
        public void handleEvent(MarketTick e){
            tickIn.onTick(e);
            mid.calc();
            ewma.calc();
            spread.calc();
            notional.calc();
            vol.calc();
            exposure.calc();
            limit.calc();
            charge.calc();
            buffer.calc();
        }
        public double buf(){ return _b.value(); }
        public long breaches(){ return _l.breaches(); }
        public long updates(){ return _b.updates(); }
    }
    public static final class P10 {
        private final Chain.TickInA _t = new Chain.TickInA();
        private final Chain.MidA _m = new Chain.MidA(_t);
        private final Chain.EwmaA _e = new Chain.EwmaA(_m);
        private final Chain.SpreadA _s = new Chain.SpreadA(_t);
        private final Chain.NotionalA _n = new Chain.NotionalA(_m,_s);
        private final Chain.VolA _v = new Chain.VolA(_m,_e);
        private final Chain.ExposureA _x = new Chain.ExposureA(_n,_v);
        private final Chain.LimitA _l = new Chain.LimitA(_x);
        private final Chain.ChargeA _c = new Chain.ChargeA(_x);
        private final Chain.BufferA _b = new Chain.BufferA(_c);
        private final Chain.ITickIn tickIn = _t;
        private final Chain.IMid mid = _m;
        private final Chain.IEwma ewma = _e;
        private final Chain.ISpread spread = _s;
        private final Chain.INotional notional = _n;
        private final Chain.IVol vol = _v;
        private final Chain.IExposure exposure = _x;
        private final Chain.ILimit limit = _l;
        private final Chain.ICharge charge = _c;
        private final Chain.IBuffer buffer = _b;
        public void handleEvent(MarketTick e){
            tickIn.onTick(e);
            mid.calc();
            ewma.calc();
            spread.calc();
            notional.calc();
            vol.calc();
            exposure.calc();
            limit.calc();
            charge.calc();
            buffer.calc();
        }
        public double buf(){ return _b.value(); }
        public long breaches(){ return _l.breaches(); }
        public long updates(){ return _b.updates(); }
    }
}
