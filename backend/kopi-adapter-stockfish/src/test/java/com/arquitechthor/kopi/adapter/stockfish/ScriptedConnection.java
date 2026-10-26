package com.arquitechthor.kopi.adapter.stockfish;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/// A [UciConnection] that answers each command from a script, instantly. An
/// empty queue on [#receive] counts as a timeout, so timeouts are tested
/// without waiting.
final class ScriptedConnection implements UciConnection {

    /// What a small Stockfish says to `uci`.
    static final List<String> UCI_ANSWER = List.of(
            "id name Stockfish 19",
            "id author the Stockfish developers (see AUTHORS file)",
            "",
            "option name Threads type spin default 1 min 1 max 1024",
            "option name Hash type spin default 16 min 1 max 33554432",
            "option name MultiPV type spin default 1 min 1 max 256",
            "option name Skill Level type spin default 20 min 0 max 20",
            "option name UCI_LimitStrength type check default false",
            "option name UCI_Elo type spin default 1320 min 1320 max 3190",
            "option name Clear Hash type button",
            "uciok");

    private final Function<String, List<String>> script;
    private final Deque<String> output = new ArrayDeque<>();
    final List<String> sent = new ArrayList<>();
    boolean closed;

    /// @param script the lines the engine writes after each command
    ScriptedConnection(Function<String, List<String>> script) {
        this.script = script;
    }

    /// An engine that introduces itself, is always ready, and answers `go`
    /// with these lines.
    static ScriptedConnection answeringGo(List<String> goAnswer) {
        return new ScriptedConnection(command -> {
            if (command.equals("uci")) {
                return UCI_ANSWER;
            }
            if (command.equals("isready")) {
                return List.of("readyok");
            }
            if (command.startsWith("go")) {
                return goAnswer;
            }
            return List.of();
        });
    }

    @Override
    public void send(String command) {
        if (closed) {
            throw new UciException("closed");
        }
        sent.add(command);
        output.addAll(script.apply(command));
    }

    @Override
    public String receive(Duration timeout) {
        if (output.isEmpty()) {
            throw new UciException("No answer within " + timeout);
        }
        return output.removeFirst();
    }

    @Override
    public boolean isAlive() {
        return !closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
