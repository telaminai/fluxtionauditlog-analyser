package com.vendor.marketdata;
/** The published face of the marketdata subsystem: one class, its whole subtree inside. */
public class MarketData {
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final MdTick   tick   = new MdTick();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final MdConfig config = new MdConfig();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Mid      mid    = new Mid(tick);
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Depth    depth  = new Depth(tick);
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Vol      vol    = new Vol(config, mid);
}
