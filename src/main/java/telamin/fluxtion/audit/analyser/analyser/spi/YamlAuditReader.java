package telamin.fluxtion.audit.analyser.analyser.spi;

import telamin.fluxtion.audit.analyser.analyser.parse.RecordFramer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The shipped text format, behind the same SPI (M31.1's acceptance: the seam proven on the format
 * that matters). The registry special-cases this reader's STORE construction — text files keep the
 * existing size-thresholded Heap/Mapped backends with real byte anchors and follow support — but its
 * identity, {@code canOpen} and capability flags live here like any other reader's, so the open path
 * consults one registry, not a registry plus a special case.
 */
public final class YamlAuditReader implements AuditLogReader {

    public static final String FORMAT_ID = "yaml";

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public String displayName() {
        return "Fluxtion audit log (YAML)";
    }

    @Override
    public boolean canOpen(Path source) {
        // content, not extension: audit logs arrive as .yaml, .yml, .log and bare names alike.
        // The head of a real file reaches "eventLogRecord:" within the first record.
        try {
            byte[] head = new byte[4096];
            try (var in = Files.newInputStream(source)) {
                int n = in.read(head);
                // NOTHING TO SNIFF. A zero-byte or whitespace-only file has no content to recognise, so
                // content-sniffing cannot decide and the extension is the only signal there is.
                //
                // Rejecting it used to cap MA-0: three of the six empty shapes — zero bytes, whitespace,
                // and an empty export — were refused by every reader, so the app said "no installed
                // reader recognises this file" and the empty-log finding never appeared. It also made
                // MA-0.5 impossible: a file opened in Follow BEFORE its first record could not be opened
                // at all, which is exactly when a person most wants to be told the file is still empty.
                //
                // Accepting it by extension is deliberate and narrow: only names this reader would own
                // anyway, so an empty file of some other type is not claimed.
                if (n <= 0) return hasAuditLogExtension(source);
                String text = new String(head, 0, n, StandardCharsets.UTF_8);
                if (text.isBlank()) return hasAuditLogExtension(source);
                return text.contains("eventLogRecord:");
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The names this reader owns, used ONLY when a file has no content to recognise.
     *
     * <p>Deliberately not used for files that do have content: a real audit log is recognised by what
     * is in it, whatever it is called, and that must not regress to an extension check.
     */
    private static boolean hasAuditLogExtension(Path source) {
        String name = source.getFileName() == null
                ? "" : source.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".log");
    }

    @Override
    public TimeBase timeBase() {
        // the native log declares nothing (UP-FLX-25 asks upstream to fix that) — this is the
        // analyser's long-standing working assumption, now stated in one place instead of six
        return TimeBase.wallClockMillisUtc();
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true);
    }

    /**
     * The generic path — used only if someone routes a text file through {@code SpiLogStore}; the
     * registry sends text files to the thresholded Heap/Mapped stores instead.
     *
     * <p><b>§1a rule 1 is the PLUGIN's duty, not the store's.</b> A plugin hands items over one at a
     * time, so nothing above it can tell where the container ended or whether the last item closed.
     * Round six found this reader handing over an unterminated final marker as an ordinary item, so the
     * SPI path read six files as complete where the built-in reader said the claim was unfinished — and
     * that breaks the conformance suite's promise that the two paths agree. An unterminated final item
     * is withheld here, exactly as {@code requireTerminator} withholds it in the built-in framer.
     */
    @Override
    public void read(Path source, Consumer<String> recordText) throws IOException {
        RecordFramer.frameForPlugin(Files.readString(source, StandardCharsets.UTF_8),
                raw -> recordText.accept(raw.text()));
    }
}
