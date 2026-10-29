package com.arquitechthor.kopi.adapter.stockfish;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// A [UciConnection] to an engine process.
///
/// A **virtual thread** reads the engine's output line by line into a
/// queue, so a blocking `readLine` never holds a platform thread and
/// [#receive] can wait with a timeout. (Virtual threads are final since
/// Java 21; design §4.4.)
///
/// The whole conversation can be logged, every line both ways, with the
/// `kopi.uci` logger at `DEBUG` (`LOGGING_LEVEL_KOPI_UCI=DEBUG`): off by
/// default, as a search writes hundreds of `info` lines. A line is logged
/// by the thread that sends or receives it, so it carries that thread's
/// `gameId`.
final class UciProcess implements UciConnection {

    private static final Logger trace = LoggerFactory.getLogger("kopi.uci");

    /// Put in the queue when the engine closes its output.
    private static final String END = "\u0000end";

    private final Process process;
    private final PrintWriter input;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    private UciProcess(Process process) {
        this.process = process;
        this.input = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
        Thread.ofVirtual().name("uci-reader-" + process.pid()).start(this::readOutput);
    }

    /// Starts the engine at `binary`.
    ///
    /// @throws UciException if it cannot be started (missing file, no
    ///                      permission…)
    static UciProcess start(Path binary) {
        try {
            var process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
            return new UciProcess(process);
        } catch (IOException e) {
            throw new UciException("Cannot start the engine at " + binary, e);
        }
    }

    private void readOutput() {
        try (var output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = output.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException _) {
            // The process died; END below tells whoever is waiting.
        } finally {
            lines.add(END);
        }
    }

    @Override
    public void send(String command) {
        if (!process.isAlive()) {
            throw new UciException("The engine is not running (exit code " + process.exitValue() + ")");
        }
        trace.debug("{} > {}", process.pid(), command);
        input.println(command);
        if (input.checkError()) {
            throw new UciException("Cannot write to the engine");
        }
    }

    @Override
    public String receive(Duration timeout) {
        try {
            var line = lines.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (line == null) {
                throw new UciException("No answer from the engine within " + timeout);
            }
            if (line.equals(END)) {
                lines.add(END); // keep reporting the end to later calls
                throw new UciException("The engine closed its output");
            }
            trace.debug("{} < {}", process.pid(), line);
            return line;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UciException("Interrupted while waiting for the engine", e);
        }
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            input.println("quit");
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
