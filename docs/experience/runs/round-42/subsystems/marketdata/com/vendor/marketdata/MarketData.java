package com.vendor.marketdata;
/** The published face of the marketdata subsystem: one class, its whole subtree inside. */
public class MarketData {
    public final MdTick   tick   = new MdTick();
    public final MdConfig config = new MdConfig();
    public final Mid      mid    = new Mid(tick);
    public final Depth    depth  = new Depth(tick);
    public final Vol      vol    = new Vol(config, mid);
}
