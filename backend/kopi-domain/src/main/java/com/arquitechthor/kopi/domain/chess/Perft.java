package com.arquitechthor.kopi.domain.chess;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

/// *Perft* (performance test, move path enumeration): counts the positions
/// reachable from a position in exactly `depth` moves, the leaves of the
/// move tree.
///
/// Those counts are known for well-studied positions, so a move generator
/// that reproduces them is correct: one missing en passant or a castling
/// right kept by mistake changes the number. This is how ADR-004 justifies
/// writing our own rules.
///
/// When a count is wrong, [#divide] says under which root move, and
/// comparing it with Stockfish's `go perft` narrows the bug down one move at
/// a time.
public final class Perft {

    private Perft() {}

    /// The number of leaf positions `depth` moves away from `position`.
    ///
    /// @throws IllegalArgumentException if `depth` is negative
    public static long count(Position position, int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("Perft depth cannot be negative: " + depth);
        }
        if (depth == 0) {
            return 1;
        }
        var moves = position.legalMoves();
        if (depth == 1) {
            // "Bulk counting": the leaves are the legal moves themselves,
            // so there is no need to apply each one.
            return moves.size();
        }
        long nodes = 0;
        for (var move : moves) {
            nodes += count(position.apply(move), depth - 1);
        }
        return nodes;
    }

    /// The count split by root move, keyed by the move in UCI and sorted by
    /// it. Stockfish prints the same lines (`e2e4: 20`) for `go perft`,
    /// so both outputs can be compared line by line.
    ///
    /// @throws IllegalArgumentException if `depth` is less than 1
    public static SequencedMap<String, Long> divide(Position position, int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("Perft divide needs a depth of at least 1: " + depth);
        }
        var divide = new LinkedHashMap<String, Long>();
        position.legalMoves().stream()
                .sorted(Comparator.comparing(Move::toUci))
                .forEach(move -> divide.put(move.toUci(), count(position.apply(move), depth - 1)));
        return Collections.unmodifiableSequencedMap(divide);
    }
}
