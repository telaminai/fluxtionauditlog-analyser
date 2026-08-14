package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionParser;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.Conversation;
import telamin.fluxtion.audit.analyser.analyser.llm.LlmClient;
import telamin.fluxtion.audit.analyser.analyser.llm.LogFileInfo;
import telamin.fluxtion.audit.analyser.analyser.llm.Message;
import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;
import telamin.fluxtion.audit.analyser.analyser.llm.RenderExecutor;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Two-way LLM conversation about the selected record(s) (spec §10). Assembles context via
 * {@link PromptBuilder}. With an API key it calls the provider; without one it produces a
 * ready-to-paste prompt (Copy prompt). Reset clears the conversation.
 */
public final class LlmPanel extends JPanel {

    private final JTextArea transcript = new JTextArea();
    private final JTextArea input = new JTextArea(3, 40);
    private final JButton send = new JButton("Send");
    private final JButton cancel = new JButton("Cancel");
    private final JButton copyPrompt = new JButton("Copy prompt");
    private final JButton reset = new JButton("Reset");
    private final Conversation conversation = new Conversation();

    private Supplier<AppConfig> configSupplier = () -> null;
    private Supplier<List<LogRecord>> selectionSupplier = List::of;
    private Supplier<String> epFqnSupplier = () -> null;
    private Supplier<LogFileInfo> fileInfoSupplier = () -> null;
    private Supplier<LogStore> storeSupplier = () -> null;
    private RenderExecutor renderExecutor;   // render verbs (filter/graph/goto/flag); null → not enabled
    private SourceService sourceService;
    private volatile boolean cancelled;   // set by Cancel; the loop stops after the in-flight call
    private boolean manifestSent;         // seed the action manifest on the first ENABLED turn
    private String restUrl, restToken;    // set when the REST transport is running (for copy-prompt seeding)

    public LlmPanel() {
        super(new BorderLayout(4, 4));
        setBorder(UiTheme.pad());

        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(new JScrollPane(transcript), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        bottom.add(new JScrollPane(input), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(send);
        buttons.add(cancel);
        buttons.add(copyPrompt);
        buttons.add(reset);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        cancel.setEnabled(false);
        send.addActionListener(e -> onSend());
        cancel.addActionListener(e -> cancelled = true);
        copyPrompt.addActionListener(e -> onCopyPrompt());
        reset.addActionListener(e -> onReset());
    }

    /** Record the live REST endpoint (or null when stopped) so the copied prompt can hand it to an agent. */
    public void setRestEndpoint(String url, String token) {
        this.restUrl = url;
        this.restToken = token;
    }

    /** Prime the input with a default question and focus it (used by the detail panel's Explain button). */
    public void prepareExplain() {
        if (input.getText().isBlank()) {
            input.setText("Explain this record — what happened in this cycle and why?");
        }
        input.requestFocusInWindow();
    }

    public void bind(Supplier<AppConfig> configSupplier,
                     Supplier<List<LogRecord>> selectionSupplier,
                     Supplier<String> epFqnSupplier,
                     Supplier<LogFileInfo> fileInfoSupplier,
                     Supplier<LogStore> storeSupplier,
                     RenderExecutor renderExecutor,
                     SourceService sourceService) {
        this.configSupplier = configSupplier;
        this.selectionSupplier = selectionSupplier;
        this.epFqnSupplier = epFqnSupplier;
        this.fileInfoSupplier = fileInfoSupplier;
        this.storeSupplier = storeSupplier;
        this.renderExecutor = renderExecutor;
        this.sourceService = sourceService;
    }

    private String buildContext() {
        List<LogRecord> records = selectionSupplier.get();
        if (records == null || records.isEmpty()) return "";
        return PromptBuilder.recordContext(records, epFqnSupplier.get(), sourceService, fileInfoSupplier.get());
    }

    private void onSend() {
        String question = input.getText().trim();
        if (question.isEmpty()) return;
        AppConfig cfg = configSupplier.get();
        String key = cfg == null ? "" : cfg.apiKey;
        String context = buildContext();

        if (key == null || key.isBlank()) {
            append("(no API key set — showing the prompt to copy instead)\n");
            onCopyPrompt();
            return;
        }

        boolean actionsOn = cfg.assistantActionsInProcess && storeSupplier.get() != null;

        // attach the record context to the first user turn only
        String content = question;
        if (conversation.isEmpty() && !context.isEmpty()) {
            content = "Context follows.\n\n" + context + "\n\nQuestion: " + question;
        }
        // seed the action manifest on the first turn where actions are ENABLED (not the first turn
        // absolutely) — so a chat started before a log is loaded still learns about actions later
        if (actionsOn && !manifestSent) {
            content = PromptBuilder.inProcessActionManifest(cfg.maxActionsPerReply) + "\n\n" + content;
            manifestSent = true;
        }
        conversation.add(Message.user(content));
        append("You: " + question + "\n\n");
        input.setText("");
        cancelled = false;
        setBusy(true);
        int maxRounds = actionsOn ? Math.max(1, cfg.maxActionRounds) : 1;
        runRound(cfg, key, 1, maxRounds, actionsOn);
    }

    /**
     * One turn of the bounded agent loop (spec §5.1.1): call the model, run any {@code analyser-action}
     * blocks in its reply (capped), then — if there were results — feed them back and go again, up to
     * {@code maxRounds}. Both query <b>and error</b> results are fed back so the model can self-correct.
     */
    private void runRound(AppConfig cfg, String key, int round, int maxRounds, boolean actionsOn) {
        final String system = PromptBuilder.systemPrompt();
        final String provider = cfg.llmProvider, model = cfg.llmModel, base = cfg.llmBaseUrl;
        final int perReplyCap = cfg.maxActionsPerReply;
        final LogStore store = storeSupplier.get();

        Background.run(
                () -> {
                    try {
                        LlmClient client = LlmClient.forProvider(provider, key, model, base);
                        String reply = client.complete(system, conversation.messages());
                        List<ActionResult> results = new ArrayList<>();
                        boolean capped = false;
                        if (actionsOn && !cancelled && store != null) {
                            ActionDispatcher d = new ActionDispatcher(false, null,
                                    () -> store.index().snapshot(), store::rawText, renderExecutor);
                            int executed = 0;
                            for (String block : ActionParser.extract(reply)) {
                                if (executed >= perReplyCap) { capped = true; break; }
                                results.add(d.dispatch(block));
                                executed++;
                            }
                        }
                        return new RoundOutcome(reply, results, capped);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                outcome -> {
                    conversation.add(Message.assistant(outcome.reply()));
                    append("Assistant: " + outcome.reply() + "\n\n");

                    if (!outcome.results().isEmpty()) append(actionSummary(outcome.results()));
                    if (outcome.capped()) {
                        append("[note] hit the per-reply action cap (" + perReplyCap + "); extra actions skipped.\n\n");
                    }

                    // resend only if there's something to reason on: a query result or ANY error.
                    // a render SUCCESS (filter/graph/goto/flag applied) has nothing to feed back.
                    boolean feedback = outcome.results().stream()
                            .anyMatch(r -> !r.ok() || "result".equals(r.payloadKey()));
                    boolean more = actionsOn && feedback && round < maxRounds && !cancelled;
                    if (more) {
                        append("↻ round " + (round + 1) + "/" + maxRounds + " — feeding results back…\n\n");
                        conversation.add(Message.user(resultsFeedback(outcome.results())));
                        runRound(cfg, key, round + 1, maxRounds, actionsOn);
                        return;
                    }
                    if (actionsOn && feedback && round >= maxRounds) {
                        append("[note] reached the action-round limit (" + maxRounds + ").\n\n");
                    }
                    if (cancelled) append("[cancelled]\n\n");
                    setBusy(false);
                },
                err -> {
                    append("[error] " + rootMessage(err) + "\n\n");
                    setBusy(false);
                });
    }

    /** One round's action results: model reply, the executed results, and whether the per-reply cap was hit. */
    private record RoundOutcome(String reply, List<ActionResult> results, boolean capped) {
    }

    /** Visible, clearly-marked transcript rendering of the results (so the human sees the numbers too). */
    private static String actionSummary(List<ActionResult> results) {
        StringBuilder sb = new StringBuilder("▸ ran " + results.size() + " action(s):\n");
        for (ActionResult r : results) sb.append("   ").append(r.toJson()).append('\n');
        return sb.append('\n').toString();
    }

    /** The turn fed back to the model — the same JSON, so results and errors both drive self-correction. */
    private static String resultsFeedback(List<ActionResult> results) {
        StringBuilder sb = new StringBuilder("Action results (JSON) — use these to answer:\n");
        for (ActionResult r : results) sb.append(r.toJson()).append('\n');
        return sb.toString();
    }

    private static final String COPY_HEADER =
            "\n╔═══════ COPIED PROMPT — paste into any LLM (NOT sent from here) ═══════╗\n\n";
    private static final String COPY_FOOTER =
            "\n╚═══════ END COPIED PROMPT — also on your clipboard ═══════╝\n\n";

    private void onCopyPrompt() {
        String question = input.getText().trim();
        String prompt = PromptBuilder.fullPrompt(buildContext(), question.isEmpty() ? "Explain this record." : question);
        if (restUrl != null) {   // hand an agentic external client the live endpoint it can drive
            AppConfig cfg = configSupplier.get();
            int cap = cfg == null ? 20 : cfg.maxActionsPerReply;
            prompt = prompt + "\n\n" + PromptBuilder.restActionManifest(restUrl, restToken, cap);
        }
        copyToClipboard(prompt);
        // show the whole prompt in the transcript, clearly marked as copied (not a sent/received turn)
        append(COPY_HEADER);
        append(prompt);
        append(COPY_FOOTER);
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private void onReset() {
        conversation.reset();
        transcript.setText("");
        manifestSent = false;
    }

    private void append(String s) {
        transcript.append(s);
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    private void setBusy(boolean busy) {
        send.setEnabled(!busy);
        cancel.setEnabled(busy);
        copyPrompt.setEnabled(!busy);
        reset.setEnabled(!busy);
        if (busy) append("…thinking…\n");
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }
}
