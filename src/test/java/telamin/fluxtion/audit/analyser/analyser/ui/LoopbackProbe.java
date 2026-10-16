package telamin.fluxtion.audit.analyser.analyser.ui;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SF-2 test support: a loopback HTTP server that counts requests per path, so a test can tell which component's
 * {@code <img src=…>} was fetched. Each component under test gets its own path; a deliberately unprotected witness
 * component proves fetches are visible from this environment at all, so "no request" means something.
 */
final class LoopbackProbe implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    LoopbackProbe() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.computeIfAbsent(exchange.getRequestURI().getPath(), k -> new AtomicInteger()).incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    /** {@code path} on this server, at the address it is actually bound to. */
    String url(String path) {
        InetSocketAddress bound = server.getAddress();
        String host = bound.getAddress().getHostAddress();
        if (host.contains(":")) host = "[" + host + "]";                  // an IPv6 loopback
        return "http://" + host + ":" + bound.getPort() + path;
    }

    /** The spec's payload: text that Swing would render as HTML, fetching {@code path} from this server. */
    String payload(String path) {
        return "<html><img src='" + url(path) + "'>";
    }

    int hits(String path) {
        AtomicInteger n = hits.get(path);
        return n == null ? 0 : n.get();
    }

    int total() {
        return hits.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    /** Waits until {@code path} has been fetched (the witness), up to {@code millis}. */
    boolean awaitHit(String path, long millis) throws InterruptedException {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            if (hits(path) > 0) return true;
            Thread.sleep(25);
        }
        return hits(path) > 0;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
