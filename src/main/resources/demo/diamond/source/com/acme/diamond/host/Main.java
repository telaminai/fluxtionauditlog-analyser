package com.acme.diamond.host;

import com.acme.diamond.event.X;
import com.acme.diamond.node.D;
import com.telamin.fluxtion.runtime.DataFlow;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Feeds x = 2, 3, 3, 4 through the generated processor and writes its audit log.
 *
 * <pre>java -jar target/fluxtion-diamond.jar [out/diamond-audit.yaml] [spring]</pre>
 * With {@code spring} it runs the processor generated from the Spring XML instead of the Java builder.
 */
public final class Main {
    static final int[] SEQUENCE = {2, 3, 3, 4};

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "out/diamond-audit.yaml").toAbsolutePath();
        Files.createDirectories(file.getParent());
        // loaded by name: the class is generated at build time, so nothing here imports it
        String processor = args.length > 1 && args[1].equals("spring")
                ? "com.acme.diamond.generated.spring.DiamondSpringProcessor"
                : "com.acme.diamond.generated.DiamondProcessor";
        DataFlow flow = (DataFlow) Class.forName(processor)
                .getDeclaredConstructor().newInstance();
        try (Writer out = Files.newBufferedWriter(file)) {
            YamlAuditFile audit = new YamlAuditFile(out);
            flow.setAuditLogProcessor(audit);   // before init(), so the Init record reaches the file
            flow.init();
            D d = flow.getNodeById("d");
            for (int x : SEQUENCE) {
                int before = d.invocations();
                flow.onEvent(new X(x));
                System.out.printf("x=%d  d.value=%d  d ran %d time(s) for this event%n",
                        x, d.value(), d.invocations() - before);
            }
            audit.close();
            System.out.printf("%d audit records -> %s%n", audit.records(), file);
        }
    }
}
