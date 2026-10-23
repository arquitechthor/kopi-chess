package com.arquitechthor.kopi.adapter.bots;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/// The [BotPlayer] port, implemented by Kopi's own bots: `PAWN` and
/// `KNIGHT` (ADR-006).
///
/// The three Stockfish profiles arrive with the Stockfish adapter; until
/// then they answer as an unavailable bot (REST: `503`), so a game against
/// them is never created half-way.
public final class KopiBots implements BotPlayer {

    private final PawnBot pawn;
    private final KnightBot knight;

    /// The bots as the application uses them, with a random generator per
    /// request thread.
    public KopiBots() {
        this(new PawnBot(ThreadLocalRandom::current),
                new KnightBot(KnightBot.DEFAULT_DEPTH, ThreadLocalRandom::current));
    }

    KopiBots(PawnBot pawn, KnightBot knight) {
        this.pawn = Objects.requireNonNull(pawn, "pawn");
        this.knight = Objects.requireNonNull(knight, "knight");
    }

    @Override
    public Move chooseMove(Position position, BotProfile profile) {
        return switch (profile) {
            case PAWN -> pawn.choose(position);
            case KNIGHT -> knight.choose(position);
            case BISHOP, ROOK, QUEEN ->
                throw new BotUnavailableException(profile,
                        new UnsupportedOperationException(profile + " plays with the Stockfish adapter"));
        };
    }
}
