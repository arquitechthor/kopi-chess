package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import java.util.Optional;

/// The opening book: which named opening a position belongs to (design §9).
///
/// Looked up by position, not by move order, so transpositions are found:
/// 1.d4 Nf6 2.c4 e6 and 1.c4 e6 2.d4 Nf6 reach the same opening.
public interface OpeningBook {

    /// The opening whose position this is, if it has a name. Only the
    /// position counts: the move clocks are ignored, and so is an en passant
    /// square no pawn can capture on.
    Optional<Opening> lookup(Position position);
}
