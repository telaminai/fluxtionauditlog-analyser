package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;

import javax.swing.JTextField;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** The real dispatcher, reader, provider and frame boundary for assistant ownership and investigation scope. */
class AssistantScopeRaceFrameTest {

    private static void send(MainFrame frame, String question) throws Exception {
        onEdt(() -> {
            AssistantLiveFrameTest.panel(frame).composerArea().setText(question);
            AssistantLiveFrameTest.panel(frame).sendButton().doClick();
        });
    }

    private static void awaitPhase(MainFrame frame, String phase) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        AtomicReference<String> seen = new AtomicReference<>();
        do {
            onEdt(() -> seen.set(AssistantLiveFrameTest.assistant(frame).phase()));
            if (phase.equals(seen.get())) return;
            Thread.sleep(25);
        } while (System.nanoTime() < until);
        fail("assistant never reached " + phase + "; last=" + seen.get());
    }

    @Test
    void assistantsOwnOpenFinishesBeforeItsNextChartRuns(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path assistantLog = Files.writeString(tmp.resolve("assistant.slow"), "slow");
        DelayedReader held = new DelayedReader(false, "assistantNode");
        try (FakeProvider provider = new FakeProvider(); Frame f = new Frame(tmp, held)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            provider.replies.add("Opening.\n" + FakeProvider.action("{\"action\":\"open\",\"params\":{\"log\":\""
                    + assistantLog + "\",\"format\":\"test-slow\"}}") + "\n"
                    + FakeProvider.action("{\"action\":\"graph\",\"params\":{\"name\":\"OWN\",\"series\":[\"assistantNode.v\"]}}"));
            provider.replies.add("The chart is ready.");
            send(f.frame, "Open and chart the DEMO log");
            held.awaitEntered();
            onEdt(() -> assertNull(((GraphTabs) field(f.frame, "graphTabs")).graphNamed("OWN"),
                    "the chart must wait for the held reader"));
            held.release.countDown();
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> {
                assertEquals("COMPLETE", AssistantLiveFrameTest.assistant(f.frame).phase());
                assertFalse(AssistantLiveFrameTest.assistant(f.frame).frozen());
                assertNotNull(((GraphTabs) field(f.frame, "graphTabs")).graphNamed("OWN"),
                        "the next action ran against the assistant's completed log");
            });
            assertEquals(assistantLog.toString(), f.processorLog());
            assertEquals(2, provider.bodies.size(), "the own open continued to its follow-up round");
        } finally {
            held.release.countDown();
        }
    }

    @Test
    void personsOpenSupersedesHeldAssistantOpenAndItsLateResultCannotMakeAChart(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path assistantLog = Files.writeString(tmp.resolve("assistant.slow"), "slow");
        Path personLog = Files.writeString(tmp.resolve("person.yaml"), log("personNode"));
        DelayedReader held = new DelayedReader(false, "assistantNode");
        try (FakeProvider provider = new FakeProvider(); Frame f = new Frame(tmp, held)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            provider.replies.add("Opening.\n" + FakeProvider.action("{\"action\":\"open\",\"params\":{\"log\":\""
                    + assistantLog + "\",\"format\":\"test-slow\"}}") + "\n"
                    + FakeProvider.action("{\"action\":\"graph\",\"params\":{\"name\":\"WRONG\",\"series\":[\"personNode.v\"]}}"));
            send(f.frame, "Open the DEMO log and then chart it");
            held.awaitEntered();
            onEdt(() -> f.frame.openFile(personLog, OpenRequest.HUMAN));
            awaitPhase(f.frame, "SUPERSEDED");
            held.release.countDown();
            awaitStale(f);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!personLog.toString().equals(f.processorLog()) && System.nanoTime() < until) Thread.sleep(25);
            assertEquals(personLog.toString(), f.processorLog(), "the person's selected log is the actual workspace");
            onEdt(() -> {
                assertTrue(AssistantLiveFrameTest.assistant(f.frame).frozen());
                assertNull(((GraphTabs) field(f.frame, "graphTabs")).graphNamed("WRONG"),
                        "a late assistant result must not create the next chart on the person's log");
            });
            assertEquals(1, provider.bodies.size(), "the cancelled turn made no next provider request");
        } finally {
            held.release.countDown();
        }
    }

    @Test
    void changedFilterSupersedesHeldReplyBeforeItsChartCanUseTheNewView(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path logFile = Files.writeString(tmp.resolve("investigation.yaml"), log("nodeA"));
        try (FakeProvider provider = new FakeProvider(); Frame f = new Frame(tmp)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            onEdt(() -> f.frame.openFile(logFile, OpenRequest.HUMAN));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!logFile.toString().equals(f.processorLog()) && System.nanoTime() < until) Thread.sleep(25);
            assertEquals(logFile.toString(), f.processorLog());
            provider.gate = new CountDownLatch(1);
            provider.replies.add("Chart.\n" + FakeProvider.action("{\"action\":\"graph\",\"params\":{\"name\":\"WRONG\",\"series\":[\"nodeA.v\"]}}"));
            send(f.frame, "Chart the current filtered investigation");
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (provider.bodies.isEmpty() && System.nanoTime() < until) Thread.sleep(25);
            assertEquals(1, provider.bodies.size(), "the provider reply is held after the request");
            onEdt(() -> ((FilterState) field(f.frame, "filter")).setText("person changed scope"));
            awaitPhase(f.frame, "SUPERSEDED");
            provider.gate.countDown();
            onEdt(() -> {
                assertTrue(AssistantLiveFrameTest.assistant(f.frame).frozen());
                assertNull(((GraphTabs) field(f.frame, "graphTabs")).graphNamed("WRONG"),
                        "the held reply cannot chart the person's newly filtered view");
            });
        }
    }

    @Test
    void idleSearchFieldEditKeepsSendAvailableAfterTheDebounce(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path logFile = Files.writeString(tmp.resolve("investigation.yaml"), log("nodeA"));
        try (FakeProvider provider = new FakeProvider(); Frame f = new Frame(tmp)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            onEdt(() -> f.frame.openFile(logFile, OpenRequest.HUMAN));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!logFile.toString().equals(f.processorLog()) && System.nanoTime() < until) Thread.sleep(25);
            assertEquals(logFile.toString(), f.processorLog());
            provider.replies.add("First answer.");
            send(f.frame, "What is in this DEMO log?");
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> {
                assertEquals("COMPLETE", AssistantLiveFrameTest.assistant(f.frame).phase());
                HistoryComboBox search = (HistoryComboBox) field(f.frame, "searchField");
                ((JTextField) search.getEditor().getEditorComponent()).replaceSelection("x");
            });
            AtomicReference<String> filterText = new AtomicReference<>();
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                onEdt(() -> filterText.set(((FilterState) field(f.frame, "filter")).text()));
                if ("x".equals(filterText.get())) break;
                Thread.sleep(25);
            } while (System.nanoTime() < until);
            assertEquals("x", filterText.get(), "the real search editor reached the debounced filter");
            onEdt(() -> {
                assertEquals("COMPLETE", AssistantLiveFrameTest.assistant(f.frame).phase());
                assertFalse(AssistantLiveFrameTest.assistant(f.frame).frozen(),
                        "an idle search edit must not freeze the conversation");
                assertTrue(AssistantLiveFrameTest.panel(f.frame).sendButton().isEnabled(),
                        "Send remains available for a fresh-context follow-up");
            });
        }
    }

    @Test
    void assistantsOwnFilterThenGraphCompletesInOneReply(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path logFile = Files.writeString(tmp.resolve("investigation.yaml"), log("nodeA"));
        try (FakeProvider provider = new FakeProvider(); Frame f = new Frame(tmp)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            onEdt(() -> f.frame.openFile(logFile, OpenRequest.HUMAN));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!logFile.toString().equals(f.processorLog()) && System.nanoTime() < until) Thread.sleep(25);
            assertEquals(logFile.toString(), f.processorLog());
            provider.replies.add("Filter and chart.\n"
                    + FakeProvider.action("{\"action\":\"filter\",\"params\":{\"text\":\"nodeA\"}}") + "\n"
                    + FakeProvider.action("{\"action\":\"graph\",\"params\":{\"name\":\"OWN_FILTER\",\"series\":[\"nodeA.v\"]}}"));
            provider.replies.add("The filtered chart is ready.");
            send(f.frame, "Filter this DEMO investigation and chart it");
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> {
                assertEquals("COMPLETE", AssistantLiveFrameTest.assistant(f.frame).phase());
                assertFalse(AssistantLiveFrameTest.assistant(f.frame).frozen());
                assertEquals("nodeA", ((FilterState) field(f.frame, "filter")).text());
                assertNotNull(((GraphTabs) field(f.frame, "graphTabs")).graphNamed("OWN_FILTER"),
                        "the second action produced the chart under its own filter");
            });
            assertEquals(2, provider.bodies.size(), "the action results reached a second provider round");
        }
    }
}
