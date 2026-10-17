import telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Records the analyser's own PromptPack.parse verdict for every {@code <case>.bin} in a directory, one line each:
 * {@code <case>\t<json>}. Run by tools/m74_record_pack_crosscheck.py as a single-file source program against an
 * analyser checkout's compiled classes ({@code java -cp <checkout>/target/classes RecordPackVerdicts.java <dir>}).
 * No JSON library: the classes directory is all it needs.
 */
public class RecordPackVerdicts {
    public static void main(String[] args) throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(args[0]))) {
            files = s.filter(p -> p.toString().endsWith(".bin")).sorted().toList();
        }
        for (Path f : files) {
            String name = f.getFileName().toString().replaceAll("\\.bin$", "");
            PromptPack.Result r = PromptPack.parse(Files.readAllBytes(f));
            System.out.println(name + "\t" + verdict(r));
        }
    }

    static String verdict(PromptPack.Result r) {
        if (!r.ok()) return "{\"line\":" + r.refusal().line() + ",\"cause\":" + q(r.refusal().cause().name()) + "}";
        PromptPack.Pack p = r.pack();
        List<String> labels = new ArrayList<>(), texts = new ArrayList<>(), fixes = new ArrayList<>();
        boolean third = false;
        for (PromptPack.Entry e : p.entries()) {
            labels.add(q(e.label()));
            texts.add(e.isExample() ? "null" : q(e.text()));
            third |= e.isExample() && e.example().thirdParty();
        }
        for (PromptPack.Normalised n : p.normalised()) fixes.add("[" + n.line() + "," + q(n.fix().name()) + "]");
        return "{\"entries\":" + p.entries().size() + ",\"thirdParty\":" + third + ",\"title\":" + q(p.title())
                + ",\"description\":" + q(p.description()) + ",\"labels\":[" + String.join(",", labels)
                + "],\"texts\":[" + String.join(",", texts) + "],\"normalised\":[" + String.join(",", fixes) + "]}";
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20 || c > 0x7e) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
