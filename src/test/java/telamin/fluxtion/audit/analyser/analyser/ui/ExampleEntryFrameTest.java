package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.ExampleArchiveClient;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M74 P2b (owner request 2026-10-17) on a REAL frame: a dropped pack's example entry shows its download badge; dropping
 * the pack and choosing the entry fetch NOTHING (a recording transport sees no request); only the entry's own button
 * asks, the confirmation is answered with a folder, the archive is fetched once, unpacked through the template archive
 * boundary, and the M73 project offer is opened for the unpacked root. No network: the transport is a recorder.
 */
class ExampleEntryFrameTest {

    static final String C = "0123456789abcdef0123456789abcdef01234567";

    static byte[] archive() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (String[] e : new String[][]{{"demo-" + C + "/README.md", "# DEMO"},
                    {"demo-" + C + "/run.sh", "#!/bin/sh\necho ran"}}) {
                out.putNextEntry(new ZipEntry(e[0]));
                out.write(e[1].getBytes());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * ★ Witness: fetch on choose (call host.downloadExample from StartPromptView.choose) — the no-request assertion after
     * choosing fails; or skip the offer — the offered-root assertion fails.
     */
    @Test
    @DisplayName("★ drop and choose fetch nothing; the button confirms, fetches once, unpacks, and opens the M73 offer")
    void exampleEntryEndToEnd(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        byte[] zip = archive();
        String digest = ExampleArchiveClient.sha256(zip);
        Path pack = Files.writeString(tmp.resolve("examples.md"), "# DEMO examples\n\n"
                + "1. **Breach** — Where did the application first log a breach?\n"
                + "2. **DEMO example** — example: https://github.com/example/demo/archive/" + C + ".zip sha256:" + digest + "\n");
        List<URI> requests = new CopyOnWriteArrayList<>();
        List<Path> offered = new CopyOnWriteArrayList<>();
        AtomicReference<String> confirmedPublisher = new AtomicReference<>();
        Path target = tmp.resolve("unpacked-demo");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.exampleClient = new ExampleArchiveClient((uri, max) -> {
                    requests.add(uri);
                    return uri.getHost().equals("github.com")
                            ? new ExampleArchiveClient.Reply(302, "https://codeload.github.com/example/demo/zip/" + C, new byte[0])
                            : new ExampleArchiveClient.Reply(200, null, zip);
                });
                f.frame.exampleConfirm = (source, thirdParty, suggested) -> {
                    confirmedPublisher.set(source.publisher() + (thirdParty ? " third-party" : ""));
                    return target;
                };
                f.frame.exampleProjectOffer = offered::add;
                f.frame.setSize(1200, 800);
                f.frame.setVisible(true);
            });
            StartPromptFrameTest.showStart(f.frame);
            SessionDriver session = StartPromptFrameTest.session(f.frame);
            StartPanel page = StartPromptFrameTest.startPanel(f.frame);
            AtomicReference<Boolean> dropped = new AtomicReference<>();
            onEdt(() -> dropped.set(PromptPackFrameTest.drop(page, pack)));
            assertTrue(dropped.get());
            AtomicReference<String> label = new AtomicReference<>();
            onEdt(() -> label.set(page.prompt().promptList().getModel().getElementAt(1).label()));
            assertEquals(StartPromptView.DOWNLOAD_BADGE + " DEMO example", label.get(), "the download badge");
            assertTrue(requests.isEmpty(), "dropping fetches nothing");
            onEdt(() -> page.prompt().choose(1));
            onEdt(() -> { });
            assertTrue(requests.isEmpty(), "choosing fetches nothing");
            onEdt(() -> page.prompt().actionButton().doClick());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (offered.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(25);
                onEdt(() -> { });
            }
            assertEquals("github.com / example / demo @ 0123456 third-party", confirmedPublisher.get(),
                    "the confirmation leads with the publisher, marked third-party");
            assertEquals(2, requests.size(), "fetched once: the pinned address and GitHub's codeload redirect");
            assertEquals(List.of(target.toAbsolutePath().normalize()), offered, "the M73 offer, for the unpacked root");
            assertTrue(Files.isRegularFile(target.resolve("README.md")));
            assertFalse(Files.isExecutable(target.resolve("run.sh")), "nothing is made runnable");
            assertEquals("DONE", session.snapshot().example().phase());
            AtomicReference<String> note = new AtomicReference<>();
            onEdt(() -> note.set(page.prompt().exampleNote().getText()));
            assertTrue(note.get().contains("nothing was built or run"), note.get());
            assertTrue(session.snapshot().localAgent().extra().setupDirRef() == 0, "no folder trust or set-up implied");
        }
    }
}
