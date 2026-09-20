import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.session.resume.SessionResumeStore;
import java.nio.file.*;
import java.util.*;

/** Warm-cache relative timings. No threshold or skipped identity: all file bytes are hashed. */
class IdentityLoadBenchmark {
    static int baseline(Path p) throws Exception {
        var index = new LogIndex();
        ByteRecordFramer.frame(p, (offset, length, text) -> index.add(RecordParser.parse(text, offset, length)));
        return index.size();
    }
    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]);
        baseline(p); // warm parser and file cache
        String expected = SessionResumeStore.identity("log",p.toString()).sha256();
        long[] old = new long[3], current = new long[3], raw = new long[3];
        for(int i=0;i<3;i++) {
            long start=System.nanoTime(); int count=baseline(p); raw[i]=System.nanoTime()-start;
            start=System.nanoTime();
            SessionResumeStore.identity("log",p.toString()); baseline(p); SessionResumeStore.identity("log",p.toString());
            old[i]=System.nanoTime()-start;
            start=System.nanoTime();
            try(var store=new MappedLogStore(p)) {
                current[i]=System.nanoTime()-start;
                if(store.size()!=count || !expected.equals(store.readIdentities().getFirst().sha256())) throw new AssertionError();
            }
            System.out.printf(Locale.ROOT,"round %d: parse %.1f ms, previous triple-pass %.1f ms, indexed SHA %.1f ms; %d records%n",
                    i+1,raw[i]/1e6,old[i]/1e6,current[i]/1e6,count);
        }
        Arrays.sort(raw); Arrays.sort(old); Arrays.sort(current);
        System.out.printf(Locale.ROOT,"bytes=%d sha256=%s%nmedian added vs parse: %.1f%%; improvement vs triple-pass: %.1f%%%n",
                Files.size(p),expected,100.0*(current[1]-raw[1])/raw[1],100.0*(old[1]-current[1])/old[1]);
    }
}
