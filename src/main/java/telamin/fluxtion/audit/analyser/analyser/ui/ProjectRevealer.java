package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * What the Project panel's row actions actually do — the adapter between {@link ProjectPanel.Navigator}
 * and the frame.
 *
 * <p>It was an anonymous class inside {@code MainFrame}, and an independent review noted that gutting its
 * two reveal methods to {@code { }} left the whole suite green: the panel-side test stops at the
 * Navigator boundary and proves only that the panel ASKS for the right thing. Named and given seams, the
 * asking and the doing are both covered.
 *
 * <p>Everything here reveals something that already exists — D-L3 as amended. Opening a saved chart that
 * is not currently a tab is the one call that could be mistaken for authoring: it is not, because the
 * definition is already in the profile and this only asks for it to be shown. Nothing here creates,
 * edits or discards a definition; deletion lives on the Graph toolbar for exactly that reason.
 */
final class ProjectRevealer implements ProjectPanel.Navigator {

    /** The frame's capabilities this adapter needs, so a test can supply them. */
    interface Surface {
        void selectTab(String title);

        void openSettings(String page);

        /** Select one report by NAME in the Reports tab (the row shows a title; they differ). */
        void selectReport(String name);

        /** Open a saved chart from its definition, selecting it; false when it could not be opened. */
        boolean openSaved(GraphSpec spec);

        /** Select an already-open chart tab by name; false when there is no such tab. */
        boolean selectGraph(String name);

        /**
         * Tell the person something, without changing anything. Still reveal-only under D-L3: a row that
         * cannot act must say why, or it is the silent Open this whole round of work began with.
         */
        void say(String message);
    }

    static final String REPORTS_TAB = "Reports";
    static final String GRAPH_TAB = "Graph";

    private final Surface surface;
    private final Supplier<List<GraphSpec>> savedGraphs;

    ProjectRevealer(Surface surface, Supplier<List<GraphSpec>> savedGraphs) {
        this.surface = surface;
        this.savedGraphs = savedGraphs;
    }

    @Override
    public void showTab(String title) {
        surface.selectTab(title);
    }

    @Override
    public void openSettings(String page) {
        surface.openSettings(page);
    }

    @Override
    public void showReport(String name) {
        surface.selectTab(REPORTS_TAB);
        if (name != null) surface.selectReport(name);
    }

    @Override
    public void showGraph(String name) {
        surface.selectTab(GRAPH_TAB);
        if (name == null) return;
        // a saved definition is opened from the profile — which also covers a chart that IS open, since
        // openSaved selects an existing tab rather than rebuilding it and losing edits made since
        for (GraphSpec g : saved()) {
            if (name.equals(g.name())) {
                if (!surface.openSaved(g)) {
                    // the usual cause is no log: a chart cannot be plotted against nothing, and the row
                    // itself said "waiting for input". Saying so beats revealing an empty tab in silence.
                    surface.say("\"" + name + "\" cannot open until a log is loaded — open one first, "
                            + "then use Open on the chart again.");
                }
                return;
            }
        }
        if (!surface.selectGraph(name)) {
            surface.say("No chart called \"" + name + "\" is open, and the project has no saved definition "
                    + "for it.");
        }
    }

    private List<GraphSpec> saved() {
        List<GraphSpec> list = savedGraphs == null ? null : savedGraphs.get();
        return list == null ? List.of() : list;
    }
}
