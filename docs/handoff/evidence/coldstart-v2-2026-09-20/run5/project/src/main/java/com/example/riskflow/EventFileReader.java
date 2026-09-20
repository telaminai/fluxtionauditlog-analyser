package com.example.riskflow;

import com.example.riskflow.event.OrderEvent;
import com.example.riskflow.event.PriceUpdate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Parses the CSV feed into graph events. */
public final class EventFileReader {

    private EventFileReader() {
    }

    public static List<Object> read(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(EventFileReader::parse)
                    .toList();
        }
    }

    public static Object parse(String line) {
        String[] parts = line.split(",");
        return switch (parts[0]) {
            case "PRICE" -> new PriceUpdate(parts[1], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            case "ORDER" -> new OrderEvent(parts[1], parts[2],
                    OrderEvent.Side.valueOf(parts[3]), Integer.parseInt(parts[4]));
            default -> throw new IllegalArgumentException("unknown record type: " + line);
        };
    }
}
