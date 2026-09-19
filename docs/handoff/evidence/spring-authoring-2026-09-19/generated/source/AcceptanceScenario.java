package com.example.myapp;

import com.example.myapp.event.Checked;
import com.example.myapp.event.PriceUpdate;
import com.example.myapp.generated.MyProcessor;
import com.example.myapp.node.Child;
import com.example.myapp.service.Commands;
import com.telamin.fluxtion.runtime.DataFlow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Runs the acceptance inputs and records independent host observations of live state and sink calls. */
public final class AcceptanceScenario {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        List<Checked> checked = new ArrayList<>();
        try (PrintWriter audit = new PrintWriter(Files.newBufferedWriter(output.resolve("audit.yaml")));
             PrintWriter states = new PrintWriter(Files.newBufferedWriter(output.resolve("state.csv")));
             PrintWriter sinks = new PrintWriter(Files.newBufferedWriter(output.resolve("checked.csv")))) {
            System.out.println("pid=" + ProcessHandle.current().pid());
            DataFlow flow = new MyProcessor();
            flow.setAuditLogProcessor(record -> { audit.println("---"); audit.println(record.toString()); audit.flush(); });
            flow.init();
            flow.start();
            Child child = flow.getNodeById("child");
            states.println("time,step,processedCount,lastProcessedPrice,paused,sinkTotal");
            sinks.println("time,price,count");
            flow.<Checked>addSink("checked", result -> {
                checked.add(result);
                sinks.println(System.currentTimeMillis() + "," + result.price() + "," + result.count());
                sinks.flush();
            });
            measure(states, "initial", child, checked, 0, 0, false, 0);
            Thread.sleep(25);
            flow.onEvent(new PriceUpdate("DEMO", 10, 7));
            measure(states, "price10_filter7", child, checked, 1, 10, false, 1);
            Thread.sleep(25);
            flow.onEvent(new PriceUpdate("DEMO", 20, 9));
            measure(states, "price20_filter9", child, checked, 1, 10, false, 1);
            Thread.sleep(25);
            flow.publishSignal("pause");
            measure(states, "pause", child, checked, 1, 10, true, 1);
            Thread.sleep(25);
            flow.onEvent(new PriceUpdate("DEMO", 30, 7));
            measure(states, "price30_paused", child, checked, 1, 10, true, 1);
            Thread.sleep(25);
            Commands commands = flow.getExportedService(Commands.class);
            if (commands == null) throw new AssertionError("Commands not exported");
            commands.reset();
            measure(states, "exported_reset", child, checked, 0, 0, false, 1);
            Thread.sleep(25);
            flow.onEvent(new PriceUpdate("DEMO", 40, 7));
            measure(states, "price40_after_reset", child, checked, 1, 40, false, 2);
            if (!checked.equals(List.of(new Checked(10, 1), new Checked(40, 1)))) {
                throw new AssertionError("Unexpected Checked outputs: " + checked);
            }
            flow.stop();
            flow.tearDown();
            System.out.println("PASS: all seven state/sink checkpoints and exact Checked sequence " + checked);
            System.out.println("descriptor=" + flow.getDescriptor());
        }
    }

    private static void measure(PrintWriter states, String step, Child child, List<Checked> checked,
                                int count, double price, boolean paused, int sinkTotal) {
        String row = System.currentTimeMillis() + "," + step + "," + child.getProcessedCount()
                + "," + child.getLastProcessedPrice() + "," + child.isPaused() + "," + checked.size();
        states.println(row);
        states.flush();
        System.out.println(row);
        if (child.getProcessedCount() != count || child.getLastProcessedPrice() != price
                || child.isPaused() != paused || checked.size() != sinkTotal) {
            throw new AssertionError("Unexpected live state: " + row);
        }
    }
}
