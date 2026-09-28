package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The replay codec round-trips (review of M70, R1 and R2). What {@code ReplayCapture} writes, BOTH readers must read back
 * as an equal record: the DEMO's {@code ReplayReader} and the recipient's runner. A value that does not round-trip is a
 * wrong INPUT on replay, and every difference downstream would be blamed on the build.
 *
 * <p>Found by the review: a null String replayed as {@code "ul"} (the reader cut two characters off {@code null} as if
 * they were quotes); a newline in a String split the record, and the analyser then refused the whole file; a
 * {@code char ','} crashed the splitter; {@code BigDecimal} passed the build-time check and failed on read.
 */
class ReplayCodecRoundTripTest {

    static final String PROBE = """
            package com.acme.demo.probe;
            public record Probe(String s, char c, Character boxedC, int i, long l, double d, Double boxedD, float f,
                                boolean b, short sh, byte by) { }
            """;
    static final String BAD = """
            package com.acme.demo.probe;
            public record Bad(java.math.BigDecimal amount) { }
            """;

    static URLClassLoader codec;
    static Class<?> probe;
    static Method encode, demoRead, runnerRead;
    static Constructor<?> make;

    @BeforeAll
    static void compile(@TempDir Path tmp) throws Exception {
        Path src = Files.createDirectories(tmp.resolve("src/com/acme/demo/probe"));
        Files.writeString(src.resolve("Probe.java"), PROBE);
        Files.writeString(src.resolve("Bad.java"), BAD);
        List<String> files = new ArrayList<>(List.of(src.resolve("Probe.java").toString(), src.resolve("Bad.java").toString()));
        try (var walk = Files.list(ReplayRunnerEndToEndTest.DEMO_SRC.resolve("com/acme/demo/replay"))) {
            walk.forEach(p -> files.add(p.toString()));
        }
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        ReplayRunnerEndToEndTest.compile(classes, files);
        Path runner = Files.createDirectories(tmp.resolve("runner"));
        ReplayRunnerEndToEndTest.compile(runner, List.of(ReplayRunnerEndToEndTest.RUNNER.toString()));

        codec = new URLClassLoader(new URL[]{classes.toUri().toURL(), runner.toUri().toURL()},
                ReplayCodecRoundTripTest.class.getClassLoader());
        probe = codec.loadClass("com.acme.demo.probe.Probe");
        make = probe.getRecordComponents().length == 11
                ? probe.getDeclaredConstructor(String.class, char.class, Character.class, int.class, long.class,
                double.class, Double.class, float.class, boolean.class, short.class, byte.class) : null;
        Class<?> capture = codec.loadClass("com.acme.demo.replay.ReplayCapture");
        encode = capture.getDeclaredMethod("encode", Object.class);
        encode.setAccessible(true);
        demoRead = codec.loadClass("com.acme.demo.replay.ReplayReader").getMethod("read", String.class, Set.class);
        runnerRead = codec.loadClass("ReplayBundle").getDeclaredMethod("read", String.class, Map.class);
        runnerRead.setAccessible(true);
    }

    static Object probe(String s, char c, Character boxedC, double d, Double boxedD) throws Exception {
        return make.newInstance(s, c, boxedC, -7, Long.MIN_VALUE, d, boxedD, -1.5f, true, (short) -3, (byte) 127);
    }

    static String yaml(Object event) throws Exception {
        String body = (String) encode.invoke(null, event);
        // one line by every rule a reader might split on: no control character, no Unicode line or paragraph separator
        assertTrue(body.chars().noneMatch(c -> c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029),
                "a record is one line, whatever its strings hold: " + body.chars().filter(c -> c < 0x20 || c > 0x7e)
                        .mapToObj(c -> String.format("U+%04X", c)).toList());
        return "---\n!!com.telamin.fluxtion.runtime.event.ReplayRecord\nevent: !!" + event.getClass().getName() + " "
                + body + "\nwallClockTime: 5\n";
    }

    /** What each reader makes of one written event. */
    @SuppressWarnings("unchecked")
    static List<Object> readBoth(String yaml) throws Exception {
        List<Object> out = new ArrayList<>();
        var demo = (List<Object>) demoRead.invoke(null, yaml, Set.of(probe));
        out.add(demo.get(0).getClass().getMethod("event").invoke(demo.get(0)));
        var runner = (List<Object[]>) runnerRead.invoke(null, yaml, Map.of(probe.getName(), probe));
        out.add(runner.get(0)[0]);
        return out;
    }

    @Test
    @DisplayName("every supported value, the hard strings and chars included, reads back equal through both readers")
    void everythingRoundTrips() throws Exception {
        List<String> strings = new ArrayList<>(List.of("", "plain", "null", "a\"b", "a\\b", "a\\\\b\\", "a, b", "a}b",
                "{x: y}", "a\nb", "a\r", "\r\n", "a\tb", " ", "\u0085", " ", "\u0001\u001f\u007f",
                "---\n---", "é 漢 😀", "\\n literally", "\"", "\\"));
        strings.add(null);
        List<Object> cases = new ArrayList<>();
        for (String s : strings) cases.add(probe(s, 'x', 'y', 1.25, 2.5));
        for (char c : new char[]{',', ' ', '"', '\n', '\\', '}', ' '}) cases.add(probe("c", c, c, 0.0, null));
        for (double d : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, 1e300, -1e-300}) {
            cases.add(probe("d", 'd', null, d, d));
        }
        for (Object event : cases) {
            String yaml = yaml(event);
            List<Object> back;
            try {
                back = readBoth(yaml);
            } catch (InvocationTargetException e) {
                // an assertion, not an error: a reader that cannot read what the writer wrote is the defect itself
                throw new AssertionError("a reader could not read what the writer wrote: " + e.getCause(), e);
            }
            for (Object b : back) assertEquals(event, b, "round trip of " + yaml);
        }
    }

    @Test
    @DisplayName("witness: null and the word \"null\" stay different values")
    void nullIsNotTheWordNull() throws Exception {
        Object none = probe(null, 'a', null, 0, null), word = probe("null", 'a', 'n', 0, 0.0);
        assertTrue(yaml(none).contains("s: null,"), yaml(none));
        assertTrue(yaml(word).contains("s: \"null\","), yaml(word));
        assertEquals(List.of(none, none), readBoth(yaml(none)));
        assertEquals(List.of(word, word), readBoth(yaml(word)));
    }

    @Test
    @DisplayName("witness: a type the readers cannot decode fails at BUILD time, as the writer promises")
    void anUnreadableTypeFailsAtBuildTime() throws Exception {
        Class<?> capture = codec.loadClass("com.acme.demo.replay.ReplayCapture");
        Method check = capture.getDeclaredMethod("checkEncodable", Class.class);
        check.setAccessible(true);
        var e = assertThrows(InvocationTargetException.class, () -> check.invoke(null, codec.loadClass("com.acme.demo.probe.Bad")));
        assertTrue(e.getCause().getMessage().contains("Bad.amount: java.math.BigDecimal"), e.getCause().getMessage());
        check.invoke(null, probe);                                    // every probe component is decodable
    }

    @Test
    @DisplayName("witness: an unquoted string is refused by both readers, never guessed at")
    void anUnquotedStringIsRefused() throws Exception {
        String yaml = yaml(probe("x", 'a', 'b', 0, 0.0)).replace("s: \"x\"", "s: x");
        var demo = assertThrows(InvocationTargetException.class, () -> demoRead.invoke(null, yaml, Set.of(probe)));
        assertTrue(demo.getCause().getMessage().contains("a string must be quoted"), demo.getCause().getMessage());
        var runner = assertThrows(InvocationTargetException.class, () -> runnerRead.invoke(null, yaml, Map.of(probe.getName(), probe)));
        assertTrue(runner.getCause().getMessage().contains("a string must be quoted"), runner.getCause().getMessage());
    }

    @Test
    @DisplayName("RB-7: the writer stamps the instant the cycle ran at, never a fresh reading of a clock that moved since")
    @SuppressWarnings("unchecked")
    void theWriterStampsTheReceiptInstant() throws Exception {
        long[] now = {1000};
        var clock = new com.telamin.fluxtion.runtime.time.Clock();
        clock.setClockStrategy(com.telamin.fluxtion.runtime.time.ClockStrategy.registerClockEvent(() -> now[0]++));  // ticks per read
        Class<?> capture = codec.loadClass("com.acme.demo.replay.ReplayCapture");
        Object writer = capture.getConstructor(com.telamin.fluxtion.runtime.time.Clock.class).newInstance(clock);
        capture.getMethod("setHandled", Set.class).invoke(writer, Set.of(probe));
        var out = new java.io.StringWriter();
        capture.getMethod("setTarget", java.io.Writer.class).invoke(writer, out);
        Object event = probe("x", 'a', 'b', 1, 1.0);

        clock.eventReceived(event);                  // the processor's clock auditor hears it first, and fixes the instant
        long cycle = clock.getProcessTime();
        capture.getMethod("expect", Object.class).invoke(writer, event);
        capture.getMethod("eventReceived", Object.class).invoke(writer, event);

        assertTrue(out.toString().contains("\nwallClockTime: " + cycle + "\n"),
                "recorded the cycle's instant " + cycle + ", not a later read (the clock is now at " + now[0] + "): " + out);
        assertFalse(out.toString().contains("wallClockTime: " + (cycle + 1)), out.toString());
    }

    @Test
    @DisplayName("PR #70 review 2: both readers refuse a document that is not wholly replay records")
    void bothReadersRequireTheWholeGrammar() throws Exception {
        String good = yaml(probe("x", 'a', 'b', 1, 1.0));
        String eventLine = good.lines().filter(l -> l.startsWith("event: ")).findFirst().orElseThrow();
        for (String bad : List.of("preamble\n" + good, good + "trailing\n", good.replace("wallClockTime: 5", "wallClockTime: 5 extra"),
                good.replace("!!com.telamin.fluxtion.runtime.event.ReplayRecord", "!!Other"), good.substring(0, good.indexOf("wallClockTime")),
                // PR #70 review 4: two records with no separator between them; a field twice in one record; a
                // component with no name. Each once read as fewer inputs, or failed as an exception, not a refusal
                good + good.substring("---\n".length()),
                good.replace("wallClockTime: 5\n", "wallClockTime: 5\n" + eventLine + "\nwallClockTime: 6\n"),
                good.replace(eventLine, eventLine + "\n" + eventLine),
                good.replaceFirst("\\{(\\w+): ", "{$1 "))) {
            assertNotEquals(good, bad);
            var demo = assertThrows(InvocationTargetException.class, () -> demoRead.invoke(null, bad, Set.of(probe)), bad);
            assertTrue(demo.getCause() instanceof IllegalArgumentException, String.valueOf(demo.getCause()));
            var runner = assertThrows(InvocationTargetException.class,
                    () -> runnerRead.invoke(null, bad, Map.of(probe.getName(), probe)), bad);
            assertTrue(runner.getCause().getClass().getSimpleName().equals("Refused"), String.valueOf(runner.getCause()));
        }
    }

    @Test
    @DisplayName("both readers accept a byte-order mark and CRLF line endings")
    void aBomAndCrlfAreRead() throws Exception {
        Object event = probe("x", 'a', 'b', 1, 1.0);
        String crlf = "﻿" + yaml(event).replace("\n", "\r\n");
        assertEquals(List.of(event, event), readBoth(crlf));
    }
}
