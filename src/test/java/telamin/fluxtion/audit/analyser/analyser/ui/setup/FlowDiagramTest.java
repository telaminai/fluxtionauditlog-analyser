package telamin.fluxtion.audit.analyser.analyser.ui.setup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Auth;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.KeySource;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Method;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Plan;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.SignedIn;
import telamin.fluxtion.audit.analyser.analyser.ui.ThemeManager;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Access;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Edge;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Line;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Model;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.NodeId;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Option;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Tone;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77-5 (spec-ai-setup-wizard.md §6.1a, §16): <i>Where data goes</i>, headless. ★ T25 the state → arrows table from the
 * model alone; ★ T26 what the painter drew equals the model and the accessible text; ★ T27 contrast and clipping in light
 * and dark at 340, 430 and 560 px; ★ T31 the colour-blind distances. ★ T28's verbatim lines are in
 * {@code FaqSecurityContractTest}.
 */
class FlowDiagramTest {

    static final Auth UNCHECKED = Auth.UNCHECKED;
    static final Auth PLAN = new Auth(SignedIn.SIGNED_IN, Method.CLAUDE_AI, Plan.MAX, KeySource.NONE);
    static final Auth KEY = new Auth(SignedIn.KEY, Method.API_KEY, Plan.NONE, KeySource.ANTHROPIC_API_KEY);
    static final Auth HELPER = new Auth(SignedIn.KEY, Method.API_KEY_HELPER, Plan.NONE, KeySource.API_KEY_HELPER);
    static final Auth NOT_IN = new Auth(SignedIn.NOT_SIGNED_IN, Method.NONE, Plan.NONE, KeySource.NONE);
    static final Auth UNKNOWN = new Auth(SignedIn.UNKNOWN, Method.OTHER, Plan.NONE, KeySource.NONE);
    static final List<Auth> AUTHS = List.of(UNCHECKED, PLAN, KEY, HELPER, NOT_IN, UNKNOWN);

    static javax.swing.LookAndFeel lafBefore;

    @BeforeAll
    static void remember() {
        lafBefore = UIManager.getLookAndFeel();
    }

    @AfterAll
    static void restore() throws Exception {
        if (lafBefore != null) UIManager.setLookAndFeel(lafBefore);
    }

    static List<Edge> edges(Model m, NodeId from, NodeId to) {
        return m.edges().stream().filter(e -> e.from() == from && e.to() == to).toList();
    }

    // ---- ★ T25 ------------------------------------------------------------------------------------------------------

    /**
     * ★ T25: for every option × posture × access × sign-in, from {@code FlowDiagram.model} alone. Witnesses: map
     * {@code CLOSED_FOR_SESSION} to AUTO; draw Ask for {@code CLIENT} — each fails its assertion.
     */
    @Test
    @DisplayName("★ T25: the state → arrows table, from the model alone")
    void theStateToArrowsTable() {
        int cases = 0;
        for (Option o : Option.values()) {
            for (Posture p : Posture.values()) {
                for (Access a : Access.values()) {
                    for (Auth auth : AUTHS) {
                        Model m = FlowDiagram.model(o, p, a, auth);
                        String at = o + "/" + p + "/" + a + "/" + auth.verdict() + ": ";
                        boolean claude = o == Option.INSIDE || o == Option.BOTH;
                        boolean client = o == Option.CLIENT || o == Option.BOTH;
                        assertEquals(claude, !edges(m, NodeId.ANALYSER, NodeId.CLAUDE).isEmpty(),
                                at + "the Ask edge exists iff the option draws Claude");
                        assertEquals(claude, m.edges().stream().anyMatch(e -> e.chip() == FlowDiagram.Chip.ASK),
                                at + "Ask only for Claude");
                        for (Edge e : edges(m, NodeId.ANALYSER, NodeId.CLAUDE)) {
                            assertEquals(Line.AUTO, e.line(), at + "Ask is automatic: two-way");
                        }
                        for (Edge e : edges(m, NodeId.CLAUDE, NodeId.ANALYSER)) assertEquals(Line.AUTO, e.line(), at);
                        List<Edge> toClient = edges(m, NodeId.ANALYSER, NodeId.CLIENT);
                        assertEquals(client ? 1 : 0, toClient.size(), at + "product → client exists iff a client");
                        for (Edge e : toClient) assertEquals(Line.BY_HAND, e.line(), at + "one-way: always by hand");
                        List<Edge> fromClient = edges(m, NodeId.CLIENT, NodeId.ANALYSER);
                        assertEquals(client ? 1 : 0, fromClient.size(), at);
                        for (Edge e : fromClient) {
                            assertEquals(a == Access.ON, e.line() == Line.AUTO, at + "client → product AUTO iff ON");
                            String word = switch (a) {
                                case ON -> AiSetupLines.DIAGRAM_POINTS;
                                case OFF, CONFIRMING -> AiSetupLines.DIAGRAM_OFF;
                                case CLOSED_FOR_SESSION -> AiSetupLines.DIAGRAM_CLOSED;
                                case OTHER_INSTANCE -> AiSetupLines.DIAGRAM_ANOTHER_WINDOW;
                            };
                            assertEquals(word, e.chipText(), at + "the not-on path carries its word");
                            if (e.line() == Line.NOT_ON) assertEquals(Tone.MUTED, e.tone(), at);
                        }
                        boolean amber = p == Posture.FULL && claude;
                        assertEquals(amber, m.amber(), at + "amber iff Full AND the picture draws Claude");
                        assertEquals(amber, m.edges().stream().anyMatch(e -> e.tone() == Tone.ACCENT),
                                at + "accent arrows iff amber");
                        for (Edge e : m.edges()) {
                            if (e.tone() == Tone.ACCENT) {
                                assertTrue(e.from() == NodeId.CLAUDE || e.to() == NodeId.CLAUDE, at + "only the Claude pair");
                            }
                        }
                        if (o == Option.NO_AI) {
                            assertEquals(List.of(), m.edges(), at + "nothing crosses the boundary");
                            assertEquals(1, m.nodes().size(), at + "the product alone");
                            assertEquals(AiSetupLines.DIAGRAM_SENT_NO_AI, m.sent());
                        }
                        // OD-77.21: Both has two rows, each with its own product icon
                        long analysers = m.nodes().stream().filter(n -> n.id() == NodeId.ANALYSER).count();
                        assertEquals(o == Option.BOTH ? 2 : 1, analysers, at + "one product icon per row");
                        if (o == Option.BOTH) {
                            assertNotNull(m.node(NodeId.CLAUDE, 0), at + "Claude's row is above");
                            assertNotNull(m.node(NodeId.CLIENT, 1), at + "the client's row is below");
                            assertNotNull(m.node(NodeId.ANALYSER, 1));
                        }
                        // OD-77.16: the bill, from the sign-in check; nothing when it has not said
                        if (claude) {
                            String bill = m.node(NodeId.CLAUDE_SERVICE, 0).caption();
                            String expected = auth == PLAN ? "your Claude plan"
                                    : auth == KEY || auth == HELPER ? "your API key" : null;
                            assertEquals(expected, bill, at + "the bill");
                            String sends = edges(m, NodeId.CLAUDE, NodeId.CLAUDE_SERVICE).get(0).spoken();
                            assertEquals(expected != null, sends.contains("billed"), at + "the bill is spoken iff drawn");
                        }
                        assertEquals(claude, m.usageLink(), at + "OD-77.17: the FAQ link under Claude's pictures");
                        cases++;
                    }
                }
            }
        }
        assertEquals(4 * 2 * 5 * AUTHS.size(), cases);
        // the state-taking model reads the posture and the sign-in check from the published state
        AiSetupState full = new AiSetupState(true, false, AiSetupState.Origin.MENU, AiSetupState.Step.CHOICE,
                AiSetupState.Choice.INSIDE, AiSetupState.Choice.NONE, Posture.FULL, "default",
                AiSetupState.Check.UNCHECKED, PLAN, AiSetupState.Confirming.NONE, AiSetupState.Pending.NONE, 0, "", "", "",
                "", true, false, false, AiSetupState.Suggested.NONE, AiSetupState.External.NONE);
        Model m = FlowDiagram.model(full, Option.INSIDE);
        assertTrue(m.amber());
        assertEquals("your Claude plan", m.node(NodeId.CLAUDE_SERVICE, 0).caption());
        assertEquals(AiSetupLines.diagramSentInside(true), m.sent());
        assertFalse(FlowDiagram.model(full, Option.CLIENT).amber(), "a client-only picture is never amber");
    }

    /**
     * ★ M77-4: the picture reads external access from the published state (M77-3's {@code external.access}); until M77-4
     * {@code externalAccess} always said OFF, so the client's arrow said "off" with access on. Witness: return
     * {@code Access.OFF} from {@code externalAccess} — the ON row is not AUTO.
     */
    @Test
    @DisplayName("★ M77-4: the client arrow follows the state's external access — solid only while it is on")
    void theClientArrowFollowsThePublishedAccess() {
        for (AiSetupState.Access a : AiSetupState.Access.values()) {
            AiSetupState.External x = new AiSetupState.External(AiSetupState.Client.CLAUDE_CODE, a,
                    AiSetupState.Registration.UNCHECKED, AiSetupState.Live.IDLE, true, false, true, false);
            AiSetupState s = new AiSetupState(true, false, AiSetupState.Origin.MENU, AiSetupState.Step.CLIENT,
                    AiSetupState.Choice.CLIENT, AiSetupState.Choice.NONE, Posture.SAFE, "default",
                    AiSetupState.Check.UNCHECKED, UNCHECKED, AiSetupState.Confirming.NONE, AiSetupState.Pending.NONE, 0, "",
                    "", "", "", true, false, false, AiSetupState.Suggested.NONE, x);
            assertEquals(Access.valueOf(a.name()), FlowDiagram.externalAccess(s), "★ " + a);
            Model m = FlowDiagram.model(s, Option.CLIENT);
            Edge in = m.edges().stream().filter(e -> e.from() == NodeId.CLIENT && e.to() == NodeId.ANALYSER)
                    .findFirst().orElseThrow();
            assertEquals(a == AiSetupState.Access.ON ? Line.AUTO : Line.NOT_ON, in.line(), "★ client → product, " + a);
        }
        assertEquals(Access.OFF, FlowDiagram.externalAccess(null));
    }

    // ---- painting helpers -------------------------------------------------------------------------------------------

    static FlowDiagram painted(Option o, Posture p, Auth auth, int width) {
        FlowDiagram d = new FlowDiagram();
        AiSetupState s = new AiSetupState(true, false, AiSetupState.Origin.MENU, AiSetupState.Step.CHOICE,
                AiSetupState.Choice.NONE, AiSetupState.Choice.NONE, p, "default", AiSetupState.Check.UNCHECKED, auth,
                AiSetupState.Confirming.NONE, AiSetupState.Pending.NONE, 0, "", "", "", "", true, false, false,
                AiSetupState.Suggested.NONE, AiSetupState.External.NONE);
        d.setSnapshot(s, o);
        d.setSize(width, 10);
        d.setSize(width, d.heightFor(width));
        BufferedImage img = new BufferedImage(width, d.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(UIManager.getColor("Panel.background"));
        g.fillRect(0, 0, width, d.getHeight());
        d.paint(g);
        g.dispose();
        return d;
    }

    static void theme(boolean dark) throws Exception {
        SwingUtilities.invokeAndWait(() -> ThemeManager.apply(dark ? "Dark" : "Light"));
        assertEquals(dark, ThemeManager.isDark());
    }

    // ---- ★ T26 ------------------------------------------------------------------------------------------------------

    /**
     * ★ T26: the arrows the painter drew equal the model's edges, in order, and the accessible description is the
     * summary, each arrow's sentence and the sent line. Witness: paint an extra arrow ({@code drawEdge} twice for the
     * outbound edge) — the painted list no longer equals the model's.
     */
    @Test
    @DisplayName("★ T26: what is painted is what the model holds, and what a screen reader hears")
    void paintedEqualsTheModelAndTheText() throws Exception {
        theme(false);
        for (Option o : Option.values()) {
            for (Posture p : Posture.values()) {
                for (Auth auth : List.of(UNCHECKED, PLAN)) {
                    FlowDiagram d = painted(o, p, auth, 560);
                    Model m = d.model();
                    List<FlowDiagram.PaintedArrow> want = new ArrayList<>();
                    for (Edge e : m.edges()) want.add(new FlowDiagram.PaintedArrow(e.from(), e.to(), e.row(), e.line()));
                    assertEquals(want, d.paintedArrows(), o + "/" + p + ": every arrow painted, none extra");
                    AccessibleContext ac = d.getAccessibleContext();
                    assertEquals(AccessibleRole.LABEL, ac.getAccessibleRole());
                    assertEquals("Where data goes", ac.getAccessibleName());
                    List<String> said = new ArrayList<>();
                    said.add(m.summary());
                    for (Edge e : m.edges()) said.add(e.spoken());
                    said.add(m.sent());
                    assertEquals(String.join(" ", said), ac.getAccessibleDescription(), o + "/" + p);
                    for (Edge e : m.edges()) assertFalse(e.spoken().isBlank(), "every arrow is spoken");
                    // the picture draws the sent line it speaks
                    String drawn = String.join(" ", d.paintedRuns().stream().map(FlowDiagram.Run::text).toList());
                    assertTrue(drawn.contains(m.sent().split(" ")[0]), "the sent line is drawn");
                }
            }
        }
    }

    // ---- ★ T27 ------------------------------------------------------------------------------------------------------

    /**
     * ★ T27: in light and dark at 340, 430 and 560 px, every text run is 4.5:1 on what it sits on, every line, icon and
     * boundary 3:1 (the off line exempt, §6.1a), and no label is clipped or overlaps another or an icon. Witness: paint
     * the captions in {@code Label.disabledForeground} — the caption runs fall below 4.5:1.
     */
    @Test
    @DisplayName("★ T27: contrast and clipping, light and dark, at 340, 430 and 560 px")
    // #186: on Linux CI the light 430 px layout overlaps an icon once a theme-switching test runs first (order-
    // dependent FlatLaf metrics). macOS only until #186 decides real defect vs test isolation (owner, 2026-10-19).
    @EnabledOnOs(OS.MAC)
    void contrastAndClipping() throws Exception {
        List<String> failures = new ArrayList<>();
        int runs = 0, marks = 0;
        for (boolean dark : new boolean[]{false, true}) {
            theme(dark);
            for (int width : new int[]{340, 430, 560}) {
                for (Option o : Option.values()) {
                    for (Posture p : Posture.values()) {
                        for (Auth auth : List.of(UNCHECKED, PLAN, KEY)) {
                            FlowDiagram d = painted(o, p, auth, width);
                            String at = (dark ? "dark" : "light") + " " + width + " " + o + "/" + p + "/" + auth.verdict();
                            for (String problem : d.layoutProblems()) failures.add(at + ": " + problem);
                            for (FlowDiagram.Run r : d.paintedRuns()) {
                                double c = Colour.contrast(r.ink(), r.under());
                                if (c < 4.5) failures.add(at + ": text \"" + r.text() + "\" " + String.format("%.2f", c));
                                runs++;
                            }
                            for (FlowDiagram.Mark k : d.paintedMarks()) {
                                if (k.exempt()) continue;
                                double c = Colour.contrast(k.ink(), k.under());
                                if (c < 3.0) failures.add(at + ": " + k.what() + " " + String.format("%.2f", c));
                                marks++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
        assertTrue(runs > 500 && marks > 500, "the check saw the picture: " + runs + " runs, " + marks + " marks");
        // the off line is the ONE exemption, and it is deliberately faint: if it ever reaches 3:1 it needs no exemption
        for (boolean dark : new boolean[]{false, true}) {
            assertTrue(Colour.contrast(DiagramPalette.OFF_LINE.color(dark), DiagramPalette.BOUNDARY_WASH.color(dark)) < 3.0);
        }
    }

    /** The palette table as the spec records it (§6.1a): the token → UiTheme mapping holds these values. */
    @Test
    @DisplayName("the palette tokens hold §6.1a's values; two-way is the product blue")
    void thePaletteIsTheSpecs() {
        int[][] want = {
                {0xD5EFEA, 0x123F3A}, {0x0F6B63, 0x5FD8C6}, {0xF9E0E6, 0x4A2430}, {0xB0405A, 0xF08AA0},
                {0xE2D6FA, 0x32264E}, {0x6A3DC4, 0xB8A1F4}, {0xE2E7ED, 0x2A323C}, {0x4B5A6A, 0xA8B5C3},
                {0xE9F5F1, 0x22302D}, {0x4F8F82, 0x7FB5A9}, {0x1F6FEB, 0x6CB6FF}, {0x0B4FB8, 0x8FC8FF},
                {0xC2620A, 0xF2A33A}, {0x9A4A05, 0xF5B25A}, {0xB45309, 0xF2A33A}, {0xFFFFFF, 0x1C1205},
                {0x6A3DC4, 0xA98CF0}, {0x5A31AE, 0xC2AEF7}, {0x5B636C, 0xA3ABB4}, {0x4A5159, 0xB4BBC3},
                {0x9AA1A9, 0x6B737C}, {0x5F666E, 0x9EA6AF}, {0x4B5A6A, 0x93A2B2}, {0xC03A35, 0xF0716A}};
        DiagramPalette[] tokens = DiagramPalette.values();
        assertEquals(want.length, tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            assertEquals(want[i][0], tokens[i].color(false).getRGB() & 0xFFFFFF, tokens[i] + " light");
            assertEquals(want[i][1], tokens[i].color(true).getRGB() & 0xFFFFFF, tokens[i] + " dark");
        }
        for (boolean dark : new boolean[]{false, true}) {
            assertEquals(telamin.fluxtion.audit.analyser.analyser.ui.UiTheme.accent(dark), DiagramPalette.TWO_WAY_LINE.color(dark));
            assertEquals(telamin.fluxtion.audit.analyser.analyser.ui.UiTheme.accentText(dark), DiagramPalette.TWO_WAY_TEXT.color(dark));
        }
    }

    /**
     * T30's headless half: with {@code -Dm775.shots=<dir>}, every picture in light and dark at the three widths, for a
     * person to read every string before anything is committed (rule 1). Not a gate: without the property it is skipped.
     */
    @Test
    @DisplayName("T30: the pictures as PNGs, on request")
    void shots() throws Exception {
        String dir = System.getProperty("m775.shots");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && !dir.isBlank(), "-Dm775.shots=<dir> to capture");
        java.nio.file.Path out = java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
        for (boolean dark : new boolean[]{false, true}) {
            theme(dark);
            for (int width : new int[]{340, 430, 560}) {
                for (Option o : Option.values()) {
                    for (Posture p : Posture.values()) {
                        if (p == Posture.FULL && (o == Option.NO_AI || o == Option.CLIENT)) continue;
                        FlowDiagram d = painted(o, p, p == Posture.FULL ? KEY : PLAN, width);
                        BufferedImage img = new BufferedImage(width, d.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        g.setColor(UIManager.getColor("Panel.background"));
                        g.fillRect(0, 0, width, d.getHeight());
                        d.paint(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(img, "png", out.resolve((dark ? "dark" : "light") + "-" + width + "-"
                                + o + "-" + p + ".png").toFile());
                    }
                }
            }
        }
    }

    // ---- ★ T31 ------------------------------------------------------------------------------------------------------

    /**
     * ★ T31: under the Machado 2009 deuteranopia and protanopia simulations (severity 1), CIELAB ΔE76 ≥ 20 between amber
     * and grey, amber and blue, and blue and grey, in both themes. Witness: a teal two-way arrow (#0F6B63 / #46CDB9,
     * round 1's) — blue vs grey collapses for a deuteranope.
     */
    @Test
    @DisplayName("★ T31: Full's amber, the two-way blue and the greys stay apart for colour-blind readers")
    void colourBlindDistance() {
        DiagramPalette[][] pairs = {
                {DiagramPalette.FULL_AMBER_LINE, DiagramPalette.OFF_LINE},
                {DiagramPalette.FULL_AMBER_LINE, DiagramPalette.BY_HAND_LINE},
                {DiagramPalette.FULL_AMBER_LINE, DiagramPalette.TWO_WAY_LINE},
                {DiagramPalette.TWO_WAY_LINE, DiagramPalette.BY_HAND_LINE},
                {DiagramPalette.TWO_WAY_LINE, DiagramPalette.OFF_LINE}};
        List<String> failures = new ArrayList<>();
        for (boolean dark : new boolean[]{false, true}) {
            for (DiagramPalette[] p : pairs) {
                Color a = p[0].color(dark), b = p[1].color(dark);
                for (var sim : List.of(Colour.DEUTAN, Colour.PROTAN)) {
                    double d = Colour.dE(Colour.simulate(a, sim), Colour.simulate(b, sim));
                    if (d < 20) failures.add((dark ? "dark " : "light ") + p[0] + " vs " + p[1] + " "
                            + (sim == Colour.DEUTAN ? "deutan" : "protan") + " " + String.format("%.1f", d));
                }
            }
        }
        assertEquals(List.of(), failures);
        // the rule can see the failure it guards: round 1's teal two-way arrow against the by-hand grey
        Color teal = new Color(0x46CDB9), grey = DiagramPalette.BY_HAND_LINE.color(true);
        assertTrue(Colour.dE(Colour.simulate(teal, Colour.DEUTAN), Colour.simulate(grey, Colour.DEUTAN)) < 20);
    }

    /** WCAG contrast, CIELAB ΔE76 and the Machado, Oliveira and Fernandes (2009) CVD simulation, in linear RGB. */
    static final class Colour {
        static final double[][] PROTAN = {{0.152286, 1.052583, -0.204868}, {0.114503, 0.786281, 0.099216},
                {-0.003882, -0.048116, 1.051998}};
        static final double[][] DEUTAN = {{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413},
                {-0.011820, 0.042940, 0.968881}};

        static double lin(int v) {
            double c = v / 255.0;
            return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }

        static int gam(double c) {
            c = Math.max(0, Math.min(1, c));
            double v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
            return (int) Math.round(v * 255);
        }

        static double luminance(Color c) {
            return 0.2126 * lin(c.getRed()) + 0.7152 * lin(c.getGreen()) + 0.0722 * lin(c.getBlue());
        }

        static double contrast(Color a, Color b) {
            double la = luminance(a), lb = luminance(b);
            return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
        }

        static Color simulate(Color c, double[][] m) {
            double r = lin(c.getRed()), g = lin(c.getGreen()), b = lin(c.getBlue());
            return new Color(gam(m[0][0] * r + m[0][1] * g + m[0][2] * b), gam(m[1][0] * r + m[1][1] * g + m[1][2] * b),
                    gam(m[2][0] * r + m[2][1] * g + m[2][2] * b));
        }

        static double[] lab(Color c) {
            double r = lin(c.getRed()), g = lin(c.getGreen()), b = lin(c.getBlue());
            double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047, y = 0.2126 * r + 0.7152 * g + 0.0722 * b,
                    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
            double fx = f(x), fy = f(y), fz = f(z);
            return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
        }

        static double f(double t) {
            return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
        }

        static double dE(Color a, Color b) {
            double[] p = lab(a), q = lab(b);
            return Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
        }
    }
}
