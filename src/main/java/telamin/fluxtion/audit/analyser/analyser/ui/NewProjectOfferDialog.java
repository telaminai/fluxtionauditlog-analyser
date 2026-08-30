package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One confirmation for M19.13's source-root, skill and topology offers. Every control starts off. */
final class NewProjectOfferDialog {

    private NewProjectOfferDialog() {
    }

    static NewProjectDiscovery.Selection show(Component owner, NewProjectDiscovery.Offer offer) {
        Box list = Box.createVerticalBox();
        Map<Path, JCheckBox> roots = new LinkedHashMap<>();
        Map<String, JCheckBox> skills = new LinkedHashMap<>();
        Map<Path, JRadioButton> graphs = new LinkedHashMap<>();
        JCheckBox guide = new JCheckBox("Create "
                + telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet.FILE_NAME
                + " pointing at the canonical Fluxtion authoring docs");

        if (offer.empty()) {
            list.add(new JLabel("Nothing discoverable was found. Create an empty project profile?"));
        } else {
            list.add(new JLabel("Found these project files. Select only what this analyser should adopt:"));
            addHeading(list, "Source roots");
            for (Path root : offer.sourceRoots()) {
                JCheckBox box = new JCheckBox(offer.root().relativize(root).toString());
                box.setSelected(false);
                roots.put(root, box);
                list.add(box);
            }
            addHeading(list, "Skills as runbook pointers");
            for (var skill : offer.skills().candidates()) {
                JCheckBox box = new JCheckBox(skill.name() + " — " + skill.path()
                        + (skill.description() == null ? "" : " — " + skill.description()));
                box.setSelected(false);
                skills.put(skill.path(), box);
                list.add(box);
            }
            addHeading(list, "Topology (at most one opens now)");
            ButtonGroup graphGroup = new ButtonGroup();
            for (GraphmlDiscovery.Candidate graph : offer.graphs().candidates()) {
                JRadioButton button = new JRadioButton(graph.describe());
                button.setSelected(false);
                graphGroup.add(button);
                graphs.put(graph.file(), button);
                list.add(button);
            }
            if (offer.sourceRoots().isEmpty()) list.add(new JLabel("No Java source-root layout found."));
            if (offer.skills().candidates().isEmpty()) list.add(new JLabel("No SKILL.md files found."));
            if (offer.graphs().candidates().isEmpty()) list.add(new JLabel("No GraphML under the offered source roots."));
            if (offer.skills().truncated() || offer.graphs().truncated()) {
                list.add(new JLabel("Discovery reached its safety cap; this is a partial offer."));
            }
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(list);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(720, Math.min(480, Math.max(160, panel.getPreferredSize().height + 20))));
        list.add(new JLabel(" "));
        switch (offer.referenceGuide()) {
            case CAN_CREATE -> {
                guide.setSelected(false);           // M35.4: offered, never pre-selected
                guide.setToolTipText("Links only — it restates nothing, so improving those pages "
                    + "improves this project too.");
                list.add(guide);
            }
            case EXISTS -> list.add(new JLabel(
                "This project already has a " + telamin.fluxtion.audit.analyser.analyser.config
                        .ReferenceSet.FILE_NAME + " — it will not be touched."));
            case NOTHING_AGREED -> { }   // nothing signed off: say nothing rather than show a dead box
        }

        int answer = JOptionPane.showConfirmDialog(owner, scroll, "New project — what should be added?",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return null;

        Set<Path> chosenRoots = new LinkedHashSet<>();
        roots.forEach((path, box) -> { if (box.isSelected()) chosenRoots.add(path); });
        Set<String> chosenSkills = new LinkedHashSet<>();
        skills.forEach((path, box) -> { if (box.isSelected()) chosenSkills.add(path); });
        Path chosenGraph = graphs.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(null);
        return new NewProjectDiscovery.Selection(chosenRoots, chosenSkills, chosenGraph,
                guide.isSelected());
    }

    private static void addHeading(Box list, String text) {
        list.add(Box.createVerticalStrut(10));
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(label);
    }
}
