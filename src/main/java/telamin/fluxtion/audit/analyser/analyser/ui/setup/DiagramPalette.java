package telamin.fluxtion.audit.analyser.analyser.ui.setup;

import telamin.fluxtion.audit.analyser.analyser.ui.UiTheme;

import java.awt.Color;

/**
 * M77-5 (spec-ai-setup-wizard.md §6.1a, OD-77.15): every colour the option diagram paints, as named tokens. Each token
 * is one {@link UiTheme} method, light and dark, so the theme owns the values and the diagram owns only the names.
 * {@link #TWO_WAY_LINE} and {@link #TWO_WAY_TEXT} are the product blue ({@link UiTheme#accent(boolean)},
 * {@link UiTheme#accentText(boolean)}); Full is amber, its own set.
 *
 * <p>Text reaches 4.5:1 on what it sits on; lines, icons and the boundary 3:1 — except {@link #OFF_LINE}, deliberately
 * fainter, because it always carries its word ("off", "closed", "another window"). ★ T27 and ★ T31 hold this.
 */
public enum DiagramPalette {
    ANALYSER_FILL, ANALYSER_INK,
    CLAUDE_FILL, CLAUDE_INK,
    CLIENT_FILL, CLIENT_INK,
    SERVICE_FILL, SERVICE_INK,
    BOUNDARY_WASH, BOUNDARY_EDGE,
    TWO_WAY_LINE, TWO_WAY_TEXT,
    FULL_AMBER_LINE, FULL_AMBER_TEXT, FULL_AMBER_TAG, ON_FULL_AMBER_TAG,
    CLIENT_LINE, CLIENT_TEXT,
    BY_HAND_LINE, BY_HAND_TEXT,
    OFF_LINE, OFF_TEXT,
    SERVICE_LINE,
    NO_SIGN;

    /** The token's colour in the given theme. */
    public Color color(boolean dark) {
        return switch (this) {
            case ANALYSER_FILL -> UiTheme.diagramAnalyserFill(dark);
            case ANALYSER_INK -> UiTheme.diagramAnalyserInk(dark);
            case CLAUDE_FILL -> UiTheme.diagramClaudeFill(dark);
            case CLAUDE_INK -> UiTheme.diagramClaudeInk(dark);
            case CLIENT_FILL -> UiTheme.diagramClientFill(dark);
            case CLIENT_INK -> UiTheme.diagramClientInk(dark);
            case SERVICE_FILL -> UiTheme.diagramServiceFill(dark);
            case SERVICE_INK -> UiTheme.diagramServiceInk(dark);
            case BOUNDARY_WASH -> UiTheme.diagramBoundaryWash(dark);
            case BOUNDARY_EDGE -> UiTheme.diagramBoundaryEdge(dark);
            case TWO_WAY_LINE -> UiTheme.accent(dark);
            case TWO_WAY_TEXT -> UiTheme.accentText(dark);
            case FULL_AMBER_LINE -> UiTheme.diagramFullAmberLine(dark);
            case FULL_AMBER_TEXT -> UiTheme.diagramFullAmberText(dark);
            case FULL_AMBER_TAG -> UiTheme.diagramFullAmberTag(dark);
            case ON_FULL_AMBER_TAG -> UiTheme.diagramOnFullAmberTag(dark);
            case CLIENT_LINE -> UiTheme.diagramClientLine(dark);
            case CLIENT_TEXT -> UiTheme.diagramClientText(dark);
            case BY_HAND_LINE -> UiTheme.diagramByHandLine(dark);
            case BY_HAND_TEXT -> UiTheme.diagramByHandText(dark);
            case OFF_LINE -> UiTheme.diagramOffLine(dark);
            case OFF_TEXT -> UiTheme.diagramOffText(dark);
            case SERVICE_LINE -> UiTheme.diagramServiceLine(dark);
            case NO_SIGN -> UiTheme.diagramNoSign(dark);
        };
    }
}
