package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CA-1: runs a platform credential tool. The secret travels on STDIN only — never in {@code argv}, which any local
 * user can read from the process table — and stdout is the only channel read back. Injected so tests can assert, for
 * every call a store makes, that no secret is on its command line.
 */
public interface CommandRunner {

    /** Exit status and standard output of one finished command. */
    record Result(int exit, String stdout) {
        @Override
        public String toString() {
            return "Result[exit=" + exit + ", stdout=<" + (stdout == null ? 0 : stdout.length()) + " chars>]";
        }
    }

    Result run(List<String> argv, String stdin) throws IOException;

    /** The real runner: no shell, stderr discarded, a bounded wait. */
    static CommandRunner system() {
        return (argv, stdin) -> {
            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            try (OutputStream in = p.getOutputStream()) {
                if (stdin != null) in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            byte[] out;
            try (InputStream o = p.getInputStream()) {
                out = o.readNBytes(64 * 1024);
            }
            try {
                if (!p.waitFor(20, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    throw new IOException("the credential tool did not finish");
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for the credential tool");
            }
            return new Result(p.exitValue(), new String(out, StandardCharsets.UTF_8));
        };
    }
}
