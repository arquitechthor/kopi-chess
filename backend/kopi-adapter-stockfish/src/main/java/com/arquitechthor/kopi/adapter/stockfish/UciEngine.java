package com.arquitechthor.kopi.adapter.stockfish;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// The UCI protocol over a [UciConnection]: what to send, and what to wait
/// for, always with a timeout.
///
/// ```
/// uci        → id …, option …, uciok
/// setoption  → (nothing)
/// isready    → readyok
/// position … + go … → info …, bestmove …
/// ```
///
/// Options are checked against the ranges the engine announced, because
/// Stockfish silently clamps an out-of-range value instead of refusing it
/// (`UCI_Elo 1000` becomes 1320; see docs/notas/stockfish-opciones.md).
final class UciEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UciEngine.class);

    /// An option the engine announced. `min` and `max` are only set for
    /// `spin` options.
    record Option(String name, String type, Integer min, Integer max) {}

    /// What a search ended with.
    ///
    /// @param bestMove the move in UCI; empty for `bestmove (none)`
    ///                 (the position was already over)
    /// @param lines    the last complete line of each candidate (by
    ///                 `multipv`), best first
    record SearchResult(Optional<String> bestMove, List<InfoLine> lines) {
        SearchResult {
            lines = List.copyOf(lines);
        }
    }

    private final UciConnection connection;
    private final Map<String, Option> options;
    private final String name;
    private final Duration timeout;

    private UciEngine(UciConnection connection, String name, Map<String, Option> options, Duration timeout) {
        this.connection = connection;
        this.name = name;
        this.options = options;
        this.timeout = timeout;
    }

    /// Opens the conversation (`uci` … `uciok`) and waits until the engine
    /// is ready. Every answer must arrive within `timeout`.
    ///
    /// @throws UciException if the engine does not answer properly in time
    static UciEngine start(UciConnection connection, Duration timeout) {
        try {
            connection.send("uci");
            var options = new LinkedHashMap<String, Option>();
            var name = "unknown engine";
            String line;
            while (!(line = connection.receive(timeout)).equals("uciok")) {
                if (line.startsWith("id name ")) {
                    name = line.substring("id name ".length());
                } else if (line.startsWith("option name ")) {
                    var option = option(line);
                    options.put(option.name(), option);
                }
            }
            var engine = new UciEngine(connection, name, Map.copyOf(options), timeout);
            engine.waitUntilReady();
            return engine;
        } catch (RuntimeException e) {
            connection.close();
            throw e instanceof UciException uci ? uci : new UciException("Cannot start the engine", e);
        }
    }

    /// `option name UCI_Elo type spin default 1320 min 1320 max 3190`. Names
    /// may have spaces (`Skill Level`), so the fields are found by keyword.
    static Option option(String line) {
        var tokens = line.substring("option name ".length()).split(" ");
        var name = new StringBuilder();
        String type = null;
        Integer min = null;
        Integer max = null;
        var inName = true;
        for (int i = 0; i < tokens.length; i++) {
            if (inName && tokens[i].equals("type")) {
                inName = false;
                type = i + 1 < tokens.length ? tokens[++i] : null;
            } else if (inName) {
                name.append(name.isEmpty() ? "" : " ").append(tokens[i]);
            } else if (tokens[i].equals("min") && i + 1 < tokens.length) {
                min = Integer.valueOf(tokens[++i]);
            } else if (tokens[i].equals("max") && i + 1 < tokens.length) {
                max = Integer.valueOf(tokens[++i]);
            }
        }
        return new Option(name.toString(), type, min, max);
    }

    /// The engine's name and version, as it introduced itself (`Stockfish 19`).
    String name() {
        return name;
    }

    /// Sets a numeric option, checked against the engine's range.
    ///
    /// @throws IllegalArgumentException if the engine has no such option or
    ///                                  the value is out of its range
    void setOption(String option, int value) {
        var announced = announced(option);
        if (announced.min() != null && value < announced.min() || announced.max() != null && value > announced.max()) {
            throw new IllegalArgumentException(option + " must be between " + announced.min() + " and "
                    + announced.max() + ": " + value);
        }
        connection.send("setoption name " + option + " value " + value);
    }

    /// Sets a true/false option.
    ///
    /// @throws IllegalArgumentException if the engine has no such option
    void setOption(String option, boolean value) {
        announced(option);
        connection.send("setoption name " + option + " value " + value);
    }

    private Option announced(String option) {
        var announced = options.get(option);
        if (announced == null) {
            throw new IllegalArgumentException("The engine has no option " + option);
        }
        return announced;
    }

    /// `ucinewgame`: forget every earlier search (the hash above all), so
    /// the next one does not depend on them. Clearing the hash takes a
    /// moment: [#waitUntilReady()] waits for it.
    void newGame() {
        connection.send("ucinewgame");
    }

    /// `isready` … `readyok`: the engine has applied every option sent.
    void waitUntilReady() {
        connection.send("isready");
        while (!connection.receive(timeout).equals("readyok")) {
            // skip anything else (info strings)
        }
    }

    /// Searches `fen` with the `go` arguments given (`movetime 300`,
    /// `depth 12`…) and waits for `bestmove`, at most `limit`.
    ///
    /// If the time runs out, the engine is told to `stop` and the search
    /// fails: an engine that ignores its time limit is not trusted again.
    SearchResult search(String fen, String go, Duration limit) {
        connection.send("position fen " + fen);
        connection.send("go " + go);
        var deadline = System.nanoTime() + limit.toNanos();
        // The last line of each candidate, by multipv number.
        var latest = new TreeMap<Integer, InfoLine>();
        while (true) {
            var left = Duration.ofNanos(deadline - System.nanoTime());
            String line;
            try {
                line = connection.receive(left.isNegative() ? Duration.ZERO : left);
            } catch (UciException e) {
                try {
                    connection.send("stop");
                } catch (UciException _) {
                    // already dead: the original failure says why
                }
                throw new UciException("No bestmove within " + limit + " for " + fen, e);
            }
            if (line.startsWith("bestmove")) {
                var move = line.split(" ")[1];
                return new SearchResult(move.equals("(none)") ? Optional.empty() : Optional.of(move),
                        new ArrayList<>(latest.values()));
            }
            if (line.startsWith("Unknown command")) {
                log.warn("Engine refused a command: {}", line); // a bug of ours, not of the engine
            }
            InfoLine.parse(line).ifPresent(info -> latest.put(info.multiPv(), info));
        }
    }

    boolean isAlive() {
        return connection.isAlive();
    }

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public String toString() {
        return Objects.toString(name);
    }
}
