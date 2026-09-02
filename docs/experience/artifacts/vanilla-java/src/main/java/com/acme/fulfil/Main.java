package com.acme.fulfil;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java Main <scenario-file> <decisions-file>");
            System.exit(1);
        }

        String scenarioFile = args[0];
        String decisionsFile = args[1];

        // Read scenario file
        List<Event> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(scenarioFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Event event = Event.parse(line);
                if (event != null) {
                    events.add(event);
                }
            }
        }

        // Process events
        Engine engine = new Engine();
        engine.processAll(events);

        // Write decisions to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(decisionsFile))) {
            for (Decision decision : engine.getDecisions()) {
                writer.println(decision.toCsvLine());
            }
        }
    }
}
