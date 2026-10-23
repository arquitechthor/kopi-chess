package com.arquitechthor.kopi.domain.game;

import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import java.time.Instant;
import java.util.Objects;

/// A move as it happened in a game: what was played, how it is written,
/// where it led and when. One row of the `game_move` table (design §6.1).
///
/// @param ply           1 for White's first move, 2 for Black's reply…
/// @param move          the move itself
/// @param san           its SAN in the position it was played in (`Nf3`)
/// @param positionAfter the position it led to
/// @param playedAt      when it was played
public record PlayedMove(int ply, Move move, String san, Position positionAfter, Instant playedAt) {

    public PlayedMove {
        if (ply < 1) {
            throw new IllegalArgumentException("Plies start at 1: " + ply);
        }
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(san, "san");
        Objects.requireNonNull(positionAfter, "positionAfter");
        Objects.requireNonNull(playedAt, "playedAt");
    }
}
