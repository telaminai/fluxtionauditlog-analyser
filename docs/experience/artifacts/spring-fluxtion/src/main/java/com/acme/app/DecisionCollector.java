package com.acme.app;

import java.util.ArrayList;
import java.util.List;

/** Collects decisions from all decision nodes in the current event cycle. */
public class DecisionCollector {
    private static final List<Decision> decisions = new ArrayList<>();

    public record Decision(String decisionType, String key) {}

    public static void emit(String decisionType, String key) {
        decisions.add(new Decision(decisionType, key));
    }

    public static List<Decision> getAndClear() {
        List<Decision> result = new ArrayList<>(decisions);
        decisions.clear();
        return result;
    }
}
