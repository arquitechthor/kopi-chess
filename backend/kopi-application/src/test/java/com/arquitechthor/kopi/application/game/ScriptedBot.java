package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/// A [BotPlayer] that plays the moves it is told (in UCI), or else the first
/// legal move in UCI order. It can also fail, or run something while it
/// "thinks".
final class ScriptedBot implements BotPlayer {

    private final Deque<String> script = new ArrayDeque<>();
    private boolean unavailable;
    private Runnable whileThinking = () -> {};
    int calls;

    ScriptedBot playing(String... uciMoves) {
        script.addAll(List.of(uciMoves));
        return this;
    }

    ScriptedBot unavailable() {
        unavailable = true;
        return this;
    }

    ScriptedBot whileThinking(Runnable action) {
        whileThinking = action;
        return this;
    }

    @Override
    public Move chooseMove(Position position, BotProfile profile) {
        calls++;
        whileThinking.run();
        if (unavailable) {
            throw new BotUnavailableException(profile, new IllegalStateException("engine down"));
        }
        var legal = position.legalMoves();
        if (script.isEmpty()) {
            return legal.stream().min(Comparator.comparing(Move::toUci)).orElseThrow();
        }
        var uci = script.removeFirst();
        return legal.stream()
                .filter(move -> move.toUci().equals(uci))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Scripted bot move " + uci + " is not legal"));
    }
}
