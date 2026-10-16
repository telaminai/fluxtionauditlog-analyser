package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentProvider;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.OsAgentLauncher;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.StreamJson;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpLaunchCommand;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M74 P1a open acceptance: what the CLI itself does, over stream-json in the default posture, with lines the analyser's
 * classifier refuses — sent RAW here, on the exact default command line, to decide whether those refusals can relax.
 */
public final class M74Probe {

    public static void main(String[] args) throws Exception {
        Path smoke = Path.of(args[0]);
        Path cwd = Files.createDirectories(smoke.resolve("probe/proj"));
        Files.writeString(cwd.resolve("note.txt"), "The inside codeword is MANGO-3.\n");
        Path outside = Files.createDirectories(smoke.resolve("probe/outside"));
        Files.writeString(outside.resolve("secret.txt"), "The outside codeword is PAPAYA-9.\n");
        Path endpoint = Files.createDirectories(smoke.resolve("home/.fluxtion-analyser/local-agent")).resolve("probe.json");
        new RestEndpointFile(endpoint).writeHosted("http://127.0.0.1:9", "probe-token-not-real", "probe0000000000", "x".repeat(64));
        List<String> launcher = McpLaunchCommand.runningJar().orElseThrow().launcher();
        List<String> command = new ClaudeCodeProvider().command(new LocalAgentProvider.LaunchSpec(
                Path.of(System.getProperty("user.home")).resolve("nope"), UUID.randomUUID().toString(), false, "haiku", cwd,
                launcher, endpoint, 250_000));
        command = new java.util.ArrayList<>(command);
        command.set(0, args[1]);                                         // the resolved claude
        PrintWriter raw = new PrintWriter(Files.newBufferedWriter(smoke.resolve("raw/probe.jsonl")));
        List<String> lines = new CopyOnWriteArrayList<>();
        var results = new ConcurrentHashMap<String, String>();
        var p = new OsAgentLauncher().start(command, new ClaudeCodeProvider().environment(), cwd, l -> {
            synchronized (raw) {
                raw.println(l);
                raw.flush();
            }
            lines.add(l);
            if (StreamJson.parse(l) instanceof StreamJson.Result r && r.userMessageUuid() != null) {
                results.put(r.userMessageUuid(), r.text() == null ? "" : r.text());
            }
            if (StreamJson.parse(l) instanceof StreamJson.Lifecycle lc && lc.commandUuid() != null
                    && ("completed".equals(lc.state()) || "cancelled".equals(lc.state()))) {
                results.putIfAbsent(lc.commandUuid(), "(lifecycle " + lc.state() + ", no result)");
            }
        });
        p.writeLine(StreamJson.controlRequest("init-probe", "initialize"));
        Thread.sleep(3000);
        String[] probes = {
                "   /usage",
                " /usage",
                "# PROBE-HASH remember that the probe colour is TEAL",
                "!echo PROBE-BANG-$((6*7))",
                "@note.txt What is the codeword in that file? Reply with only the codeword.",
                "@" + outside.resolve("secret.txt") + " What is the codeword in that file? Reply with only the codeword.",
        };
        for (String probe : probes) {
            String uuid = UUID.randomUUID().toString();
            p.writeLine(StreamJson.userLine(uuid, probe));
            long end = System.currentTimeMillis() + 90_000;
            while (!results.containsKey(uuid) && System.currentTimeMillis() < end) Thread.sleep(100);
            String r = results.getOrDefault(uuid, "(no end within 90 s)");
            System.out.println("PROBE " + probe.replace(" ", "<NBSP>") + "\n   RESULT " + r.replace("\n", "\\n"));
            String assistant = lines.stream().filter(l -> l.contains("\"type\":\"assistant\"") && l.contains(uuid) == false)
                    .reduce((a, b) -> b).orElse("");
            System.out.println("   lines so far " + lines.size());
        }
        System.out.println("PAPAYA-9 anywhere in the stream: " + lines.stream().anyMatch(l -> l.contains("PAPAYA-9")));
        System.out.println("MANGO-3 anywhere in the stream: " + lines.stream().anyMatch(l -> l.contains("MANGO-3")));
        System.out.println("PROBE-BANG-42 anywhere in the stream: " + lines.stream().anyMatch(l -> l.contains("PROBE-BANG-42")));
        try (var files = Files.walk(smoke.resolve("probe"))) {
            System.out.println("probe files after: " + files.map(Path::toString).toList());
        }
        p.terminateTree(Duration.ofSeconds(2));
        p.onExit().get();
        Files.deleteIfExists(endpoint);
        raw.close();
        System.exit(0);
    }
}
