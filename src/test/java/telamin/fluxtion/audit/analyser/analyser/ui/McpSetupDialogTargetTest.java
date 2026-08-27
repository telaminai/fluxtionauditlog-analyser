package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The broad M42.2 Claude selection must retain a useful, explicit route after Code/Desktop split. */
class McpSetupDialogTargetTest {

    @Test
    void oldClaudeSelectionMigratesToClaudeCode() {
        assertEquals(McpSetupDialog.Target.CLAUDE_CODE,
                McpSetupDialog.Target.fromPersisted("CLAUDE", McpSetupDialog.Target.GENERIC));
        assertEquals(McpSetupDialog.Target.GENERIC,
                McpSetupDialog.Target.fromPersisted("not-a-target", McpSetupDialog.Target.GENERIC));
    }
}
