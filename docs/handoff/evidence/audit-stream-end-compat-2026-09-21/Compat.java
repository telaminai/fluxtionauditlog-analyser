import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import java.nio.file.*;
public class Compat {
  public static void main(String[] a) throws Exception {
    for (String f : a) {
      var store = new HeapLogStore(Files.readString(Path.of(f)));
      int parseErrors = 0;
      StringBuilder kinds = new StringBuilder();
      for (int i = 0; i < store.size(); i++) {
        var k = store.record(i).kind();
        kinds.append(k).append(' ');
        if (String.valueOf(k).contains("PARSE")) parseErrors++;
      }
      System.out.printf("%-14s records=%d parseErrors=%d  minLogTime=%s maxLogTime=%s  kinds=[%s]%n",
          f, store.size(), parseErrors, store.minLogTime(), store.maxLogTime(), kinds.toString().trim());
    }
  }
}
