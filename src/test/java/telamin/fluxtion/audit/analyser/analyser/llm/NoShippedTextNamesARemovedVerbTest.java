package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Nothing the app SHIPS — code, echo text, the in-app help, the system prompt — may tell anyone to call a verb
 * that does not exist. {@code handoff} was a verb for one day and was folded into {@code open} before 1.14.0;
 * review found four stale mentions by searching for the words "handoff verb", and missed the one written as an
 * INSTRUCTION — "set it with handoff {posture}" — which shipped in an agent-facing echo. So this searches for the
 * call SHAPE, {@code handoff {…}}, which is what an agent would copy. (Reading stays {@code context.handoff}, and
 * {@code open {close: "handoff"}} is the real way to clear it: neither has that shape.)
 */
class NoShippedTextNamesARemovedVerbTest {

    private static final Pattern REMOVED_VERB_CALL = Pattern.compile("(?<![.\\w\"])handoff\\s*\\{");

    @Test
    void noShippedSourceOrResourceShowsACallToTheRemovedHandoffVerb() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/main/resources"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(f -> f.toString().matches(".*\\.(java|md|html|json|txt|properties)")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (REMOVED_VERB_CALL.matcher(lines.get(i)).find()) hits.add(file + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            }
        }
        assertEquals(List.of(), hits, "shipped text showing a call to `handoff {…}` — the canvas is written with open {posture | record}");
    }
}
