package com.arquitechthor.kopi.adapter.bots;

import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.MoveKind;
import com.arquitechthor.kopi.domain.chess.PieceType;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.Rules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/// `KNIGHT`, level 2: looks two half-moves ahead (design §7).
///
/// A negamax search with alpha-beta pruning over [Evaluation]: it no longer
/// hangs a piece in one move or misses a mate in one, but it does not see
/// longer tactics. Captures (most valuable victim, least valuable attacker)
/// and promotions are searched first, so the pruning cuts more.
///
/// Among the moves that score within [#MARGIN] of the best, it picks one
/// at random, so it does not play the same game every time.
final class KnightBot {

    static final int DEFAULT_DEPTH = 2;

    /// Moves this close to the best one (in centipawns) are equally good.
    static final int MARGIN = 10;

    /// Score of being mated now; a mate further away scores a little less,
    /// so the bot prefers the quickest mate and the slowest defeat.
    private static final int MATE = 100_000;

    private final int depth;
    private final Supplier<? extends RandomGenerator> random;

    /// @param depth  half-moves to look ahead, at least 1
    /// @param random where ties are broken: `ThreadLocalRandom::current` in
    ///               production, seeded in tests
    KnightBot(int depth, Supplier<? extends RandomGenerator> random) {
        if (depth < 1) {
            throw new IllegalArgumentException("The search depth must be at least 1: " + depth);
        }
        this.depth = depth;
        this.random = Objects.requireNonNull(random, "random");
    }

    /// One of the best legal moves of `position` at this depth.
    ///
    /// @throws IllegalArgumentException if `position` has no legal move
    Move choose(Position position) {
        var moves = ordered(position, position.legalMoves());
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("No legal move in " + position.toFen());
        }
        // Every root move gets an exact score (full window), so that moves
        // close to the best can be told apart from worse ones.
        var scores = new int[moves.size()];
        var best = -MATE - 1;
        for (int i = 0; i < moves.size(); i++) {
            scores[i] = -negamax(position.apply(moves.get(i)), depth - 1, -MATE - 1, MATE + 1, 1);
            best = Math.max(best, scores[i]);
        }
        var good = new ArrayList<Move>();
        for (int i = 0; i < moves.size(); i++) {
            if (scores[i] >= best - MARGIN) {
                good.add(moves.get(i));
            }
        }
        return good.size() == 1 ? good.getFirst() : good.get(random.get().nextInt(good.size()));
    }

    /// The score of `position` for its side to move, `depth` half-moves
    /// ahead, `ply` half-moves from the root.
    private int negamax(Position position, int depth, int alpha, int beta, int ply) {
        var moves = position.legalMoves();
        if (moves.isEmpty()) {
            return position.isInCheck() ? -(MATE - ply) : 0; // mated, or stalemate
        }
        if (Rules.isInsufficientMaterial(position)) {
            return 0;
        }
        if (depth == 0) {
            return Evaluation.of(position);
        }
        for (var move : ordered(position, moves)) {
            var score = -negamax(position.apply(move), depth - 1, -beta, -alpha, ply + 1);
            if (score >= beta) {
                return beta; // the opponent will not allow this line
            }
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    /// Captures and promotions first, the most promising first.
    private static List<Move> ordered(Position position, List<Move> moves) {
        return moves.stream().sorted(Comparator.comparingInt((Move move) -> promise(position, move)).reversed())
                .toList();
    }

    /// MVV-LVA: capturing a queen with a pawn beats capturing a pawn with a
    /// queen; promoting adds the new piece's value.
    private static int promise(Position position, Move move) {
        var promise = 0;
        if (move.isCapture()) {
            var victim = move.kind() instanceof MoveKind.EnPassant
                    ? PieceType.PAWN
                    : position.pieceAt(move.to()).orElseThrow().type();
            var attacker = position.pieceAt(move.from()).orElseThrow().type();
            promise += 10 * Evaluation.value(victim) - Evaluation.value(attacker);
        }
        if (move.kind() instanceof MoveKind.Promotion(var piece, _)) {
            promise += Evaluation.value(piece);
        }
        return promise;
    }
}
