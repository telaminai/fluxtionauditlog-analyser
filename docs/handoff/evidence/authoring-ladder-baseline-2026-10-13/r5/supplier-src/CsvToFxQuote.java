package com.acme.fx;

import java.util.function.Function;

/** Mongoose value mapper for ACME FX feed lines: {@code PAIR,RATE}, for example {@code EURUSD,1.0850}. */
public class CsvToFxQuote implements Function<Object, Object> {
    @Override
    public Object apply(Object line) {
        String[] f = String.valueOf(line).trim().split(",");
        if (f.length != 2 || f[0].isBlank()) {
            return null;
        }
        return new FxQuote(f[0].trim(), Double.parseDouble(f[1].trim()));
    }
}
