package com.example.myapp;

import com.telamin.mongoose.MongooseServer;

import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point — boots a Mongoose server from config/server-config.yml.
 * The server runs on its own (non-daemon) agent threads, so main can return.
 * The audit log is written to -Daudit.file (default evidence/mongoose/audit.yaml) in the same "---" separated
 * form the scenarios write. It must be passed to bootServer: Mongoose installs its own LogRecordListener on
 * every hosted processor, so one set in the Supplier is silently replaced.
 */
public final class MongooseMain {

    public static void main(String[] args) throws Exception {
        String cfg = System.getProperty("mongooseServer.config.file", "config/server-config.yml");
        System.setProperty("mongooseServer.config.file", cfg);
        Path auditFile = Path.of(System.getProperty("audit.file", "evidence/mongoose/audit.yaml"));
        Files.createDirectories(auditFile.toAbsolutePath().getParent());
        PrintWriter audit = new PrintWriter(Files.newBufferedWriter(auditFile));
        MongooseServer server;
        try (FileReader reader = new FileReader(cfg)) {
            server = MongooseServer.bootServer(reader, record -> {
                audit.println("---");
                audit.println(record.toString());
                audit.flush();
            });
        }
        // Stop the server on SIGTERM/Ctrl-C so services receive Lifecycle.stop().
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("stopping Mongoose server...");
            server.stop();
            audit.close();
        }, "mongoose-shutdown"));
        System.out.println("Mongoose server started from " + cfg + ", audit -> " + auditFile);
    }
}
