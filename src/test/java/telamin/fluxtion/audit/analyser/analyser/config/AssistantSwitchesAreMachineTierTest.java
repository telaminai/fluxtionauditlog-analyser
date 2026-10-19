package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 (docs-IA product findings): a shared settings file could switch on External MCP/REST access. Its Assistant
 * category, ticked by default, carried {@code assistant.rest} and {@code assistant.inProcess}, and an import applied
 * them. Both are now machine tier, like {@code assistant.exports} (file exchange), {@code assistant.createProjects}
 * and the Claude Code switches ({@link LocalAgentConsentTest}): never exported, never applied on import, never in a
 * project profile; an import that carries one says so in its summary. Only the in-app assistant's two limits travel.
 */
class AssistantSwitchesAreMachineTierTest {

    @TempDir
    Path tmp;

    static final String SMUGGLED = String.join("\n",
            "share.version=1",
            "assistant.rest=true",
            "assistant.inProcess=false",
            "assistant.exports=true",
            "assistant.createProjects=true",
            "assistant.maxRounds=7",
            "");

    private static AppConfig sender() {
        AppConfig c = new AppConfig();
        c.assistantActionsRest = true;
        c.assistantActionsInProcess = false;
        c.assistantExports = true;
        c.assistantCreateProjects = true;
        c.maxActionRounds = 7;
        c.maxActionsPerReply = 9;
        return c;
    }

    /** ★ Witness: put back {@code ConfigStore.put(p, "assistant.rest", …)} in {@code SettingsShare.export} — this fails. */
    @Test
    @DisplayName("★ an export, with every category ticked, never contains an assistant switch; the limits still travel")
    void exportNeverContainsTheSwitches() throws Exception {
        SettingsShare share = new SettingsShare();
        String exported = share.export(sender(), EnumSet.allOf(SettingsShare.Category.class));
        for (String key : SettingsShare.MACHINE_ONLY_SWITCHES.keySet()) {
            assertFalse(exported.contains(key + "="), "never exported: " + key + " in\n" + exported);
        }
        assertTrue(exported.contains("assistant.maxRounds=7") && exported.contains("assistant.maxActionsPerReply=9"),
                "the two limits are the Assistant category's cargo: " + exported);

        Path profile = Files.createDirectories(tmp.resolve("repo/.analyser")).resolve("project.fluxtion-settings");
        String written = ProjectProfile.write(sender(), share, profile);
        assertFalse(written.contains("assistant.rest") || written.contains("assistant.inProcess"),
                "never a project profile key: " + written);
    }

    /**
     * ★ Witness: put back {@code target.assistantActionsRest = plan.…} in {@code SettingsShare.apply} (or read
     * {@code assistant.rest} in {@code preview}) — the receiver's switch turns on and this fails.
     */
    @Test
    @DisplayName("★ an import, a profile and a bundle's profile carrying the switches leave this machine's alone")
    void importNeverAppliesThem() throws Exception {
        SettingsShare share = new SettingsShare();
        AppConfig receiver = new AppConfig();
        var plan = share.preview(SMUGGLED, receiver);
        share.apply(plan, EnumSet.allOf(SettingsShare.Category.class), receiver);
        assertFalse(receiver.assistantActionsRest, "a shared file cannot turn External MCP/REST access on");
        assertTrue(receiver.assistantActionsInProcess, "nor turn the in-app assistant's actions off (or on)");
        assertFalse(receiver.assistantExports, "nor file exchange");
        assertFalse(receiver.assistantCreateProjects, "nor project creation");
        assertEquals(7, receiver.maxActionRounds, "the limit it carried is applied");

        Path profile = Files.createDirectories(tmp.resolve("clone/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, SMUGGLED);
        AppConfig fromProfile = new AppConfig();
        ProjectProfile.load(profile, fromProfile, share);
        assertFalse(fromProfile.assistantActionsRest, "a cloned repository's profile cannot turn it on either");
        assertTrue(fromProfile.assistantActionsInProcess);
    }

    /** ★ Witness: drop the {@code machineOnly} sentences in {@code SettingsShare.preview} — the summary is silent and this fails. */
    @Test
    @DisplayName("★ the import summary says External MCP/REST access is set on this machine only")
    void theSummarySaysSo() {
        SettingsShare share = new SettingsShare();
        var plan = share.preview(SMUGGLED, new AppConfig());
        assertTrue(plan.machineOnly().contains(
                        "External MCP/REST access is set on this machine only — this file's value was ignored"),
                "the summary line: " + plan.machineOnly());
        assertEquals(4, plan.machineOnly().size(), "one line per switch it carried: " + plan.machineOnly());
        assertEquals("applies the assistant's action limits", plan.summary().get(SettingsShare.Category.ASSISTANT));

        var onlyTheSwitch = share.preview("assistant.rest=true\n", new AppConfig());
        assertFalse(onlyTheSwitch.present().contains(SettingsShare.Category.ASSISTANT),
                "a file with only the switch offers no Assistant box to tick — there is nothing to apply");
        assertEquals(List.of("External MCP/REST access is set on this machine only — this file's value was ignored"),
                onlyTheSwitch.machineOnly());

        assertEquals(List.of(), share.preview("assistant.maxRounds=4\n", new AppConfig()).machineOnly(),
                "a file without a switch says nothing about one");
    }

    /** The Assistant checkbox names its cargo, because ticking it is consenting to what it carries. */
    @Test
    void theCategoryLabelSaysItIsNeverAnAccessSwitch() {
        String label = SettingsShare.Category.ASSISTANT.label;
        assertTrue(label.startsWith("Assistant limits") && label.contains("never an access switch"), label);
    }
}
