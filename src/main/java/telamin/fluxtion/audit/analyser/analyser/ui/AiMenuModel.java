package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

/**
 * M43.1 — what the AI menu's items should say and whether they are usable, as DATA.
 *
 * <h2>Why this is not just inline in the listener</h2>
 * It was, and that made D-AI3 untestable: the rule that an item with an unmet precondition is disabled
 * <i>with the reason in its tooltip</i> lived inside a Swing callback, where the house rules say no test
 * can reach it. A rule that cannot be tested is a rule that quietly stops being true — and this one is
 * the direct descendant of M35, which spent a milestone removing six dialogs that explained themselves
 * after the click instead of before it.
 *
 * <p>So the decisions are here and pure; {@link MainFrame} only paints them.
 */
public final class AiMenuModel {

    private AiMenuModel() {
    }

    /**
     * @param enabled whether the item can be used now
     * @param tooltip what to show — when disabled this must NAME THE REMEDY, not restate the problem
     */
    public record Item(boolean enabled, String tooltip) {
    }

    /**
     * Runbooks… and Domain glossary… — both need an open project.
     *
     * <p>D-AI7: pointers are portable context whose whole value is travelling to a colleague's checkout,
     * so they belong to the project. With no project the honest answer is "not yet, here is how", not a
     * quiet write into personal settings that would help nobody and surprise whoever later opens a
     * project.
     */
    public static Item pointers(boolean hasProject) {
        return hasProject
                ? new Item(true, "Pointers stored in this project's profile — locations only, never contents")
                : new Item(false, "Needs an open project — Project ▸ Open project");
    }

    /**
     * Show exchange directory — needs the exchange to be on AND to have somewhere to point.
     *
     * <p>#21: it points at the EFFECTIVE directory, which the open project may have supplied, and says
     * when the project asked for one and did not get it. A menu item that opened the machine's
     * directory while the assistant wrote to the project's would be the wrong kind of confident.
     */
    public static Item showExchange(AppConfig config) {
        if (config == null || !config.assistantExports) {
            return new Item(false, "File exchange is off — turn it on in Report exchange directory…");
        }
        var exchange = telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.of(config);
        if (exchange.dir() == null || exchange.dir().isBlank()) {
            return new Item(false, exchange.refusal() != null ? exchange.refusal()
                    : "No exchange directory is set — choose one in Report exchange directory…");
        }
        return new Item(true, exchange.fromProject()
                ? exchange.dir() + "  (this project's)"
                : exchange.dir());
    }

    /** Where "Show exchange directory" opens — the effective directory, never the label. */
    public static String exchangePath(AppConfig config) {
        return telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.of(config).dir();
    }

    /**
     * The MCP/REST checkbox. It is BOUND to the config value rather than holding one (D-AI2): this
     * returns what the box should show, and the only writer is the user ticking it.
     */
    public static boolean transportTicked(AppConfig config) {
        return config != null && config.assistantActionsRest;
    }
}
