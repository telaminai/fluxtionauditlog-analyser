package com.example.myapp.mapper;

import com.example.myapp.event.PriceUpdate;
import com.example.myapp.event.Trade;

import java.util.function.Function;

/**
 * Maps one CSV line of the market data file to a typed event. Wired as the file feed's valueMapper.
 * Trades and prices share ONE file so their relative order is the file order; two feeds would interleave
 * by thread timing and the run would no longer be reproducible.
 * <pre>
 * TRADE,symbol,quantity,price     quantity is signed: positive buys, negative sells
 * PRICE,symbol,price              routed with filter 7, the route rootNode handles
 * </pre>
 * Blank lines and lines starting with # are skipped (returned as null).
 */
public class CsvToMarketEvent implements Function<Object, Object> {

    @Override
    public Object apply(Object event) {
        String line = String.valueOf(event).trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] f = line.split(",");
        String kind = f[0].trim();
        if ("TRADE".equals(kind) && f.length == 4) {
            return new Trade(f[1].trim(), Double.parseDouble(f[2].trim()), Double.parseDouble(f[3].trim()));
        }
        if ("PRICE".equals(kind) && f.length == 3) {
            return new PriceUpdate(f[1].trim(), Double.parseDouble(f[2].trim()), 7);
        }
        throw new IllegalArgumentException("Unrecognised market data line: " + line);
    }
}
