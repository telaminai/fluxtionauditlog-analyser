package telamin.fluxtion.audit.analyser.analyser.parse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Test helper: loads the bundled {@code sample.yml} audit log from the test classpath. */
public final class Samples {

    private Samples() {
    }

    public static String sample() {
        try (InputStream in = Samples.class.getResourceAsStream("/sample.yml")) {
            if (in == null) throw new IllegalStateException("sample.yml not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
