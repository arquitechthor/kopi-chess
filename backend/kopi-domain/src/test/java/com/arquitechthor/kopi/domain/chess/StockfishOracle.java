package com.arquitechthor.kopi.domain.chess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/// Stockfish as a reference for the rules, for the differential test only.
///
/// Just enough UCI for two questions about a position: its FEN and checkers
/// (`d`) and its legal moves (`go perft 1`). The real engine client, with
/// timeouts, pools and restarts, is the Stockfish adapter of phase 4; this
/// stays in test code and never ships.
final class StockfishOracle implements AutoCloseable {

    /// The binary: the `KOPI_STOCKFISH` environment variable (CI sets it),
    /// else a `stockfish*` file in the repository's gitignored `tools/`
    /// folder, where a local copy usually lives.
    static Optional<Path> locate() {
        var configured = System.getenv("KOPI_STOCKFISH");
        if (configured != null && !configured.isBlank()) {
            return Optional.of(Path.of(configured));
        }
        // Surefire runs in the module folder: backend/kopi-domain.
        var tools = Path.of("..", "..", "tools", "stockfish");
        if (!Files.isDirectory(tools)) {
            return Optional.empty();
        }
        try (var files = Files.list(tools)) {
            return files.filter(file -> file.getFileName().toString().startsWith("stockfish"))
                    .filter(Files::isRegularFile)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// What Stockfish says about a position.
    ///
    /// @param fen        the position in FEN
    /// @param inCheck    whether the side to move is in check
    /// @param legalMoves its legal moves, in UCI
    record Answer(String fen, boolean inCheck, Set<String> legalMoves) {}

    private static final Pattern PERFT_LINE = Pattern.compile("([a-h][1-8][a-h][1-8][qrbn]?): 1");

    private final Process process;
    private final PrintWriter input;
    private final BufferedReader output;

    StockfishOracle(Path binary) throws IOException {
        process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        input = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
        output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        send("uci");
        readUntil("uciok");
    }

    /// Asks about the position reached by playing `moves` (UCI) from `startFen`.
    /// Stockfish applies the moves itself, so its FEN is computed independently
    /// of ours.
    Answer ask(String startFen, List<String> moves) throws IOException {
        send("position fen " + startFen + (moves.isEmpty() ? "" : " moves " + String.join(" ", moves)));

        send("d");
        String fen = null;
        String line;
        while (!(line = readLine()).startsWith("Checkers:")) {
            if (line.startsWith("Fen: ")) {
                fen = line.substring(5).strip();
            }
        }
        var inCheck = !line.substring("Checkers:".length()).isBlank();

        send("go perft 1");
        var legalMoves = new HashSet<String>();
        while (!(line = readLine()).startsWith("Nodes searched")) {
            var matcher = PERFT_LINE.matcher(line.strip());
            if (matcher.matches()) {
                legalMoves.add(matcher.group(1));
            }
        }
        return new Answer(fen, inCheck, legalMoves);
    }

    private void send(String command) {
        input.println(command);
    }

    private void readUntil(String expected) throws IOException {
        while (!readLine().strip().equals(expected)) {
            // skip the engine's banner and options
        }
    }

    private String readLine() throws IOException {
        var line = output.readLine();
        if (line == null) {
            throw new IOException("Stockfish closed its output (exit code " + process.exitValue() + ")");
        }
        return line;
    }

    @Override
    public void close() {
        send("quit");
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
