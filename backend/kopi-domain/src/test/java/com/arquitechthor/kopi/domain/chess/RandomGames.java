package com.arquitechthor.kopi.domain.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/// Random games with a fixed seed, to reach many varied positions the same
/// way on every run. Games start from the initial position and from the
/// perft positions, which are rich in castling, promotions and en passant.
final class RandomGames {

    static final List<String> STARTS = List.of(
            Fen.INITIAL,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10");

    /// A position of a random game, with how it was reached.
    ///
    /// @param start    the FEN the game started from
    /// @param moves    the moves played so far, in UCI
    /// @param position where they lead
    record Step(String start, List<String> moves, Position position) {}

    private RandomGames() {}

    /// Plays `games` random games of at most `maxPlies` plies and hands every
    /// position reached (the start included) to `visit`. A game stops early
    /// when it is over: no legal move, insufficient material or the
    /// fifty-move rule.
    static void play(long seed, int games, int maxPlies, Consumer<Step> visit) {
        var random = new Random(seed);
        for (int game = 0; game < games; game++) {
            var start = STARTS.get(game % STARTS.size());
            var position = Fen.parse(start);
            var moves = new ArrayList<String>();
            for (int ply = 0; ; ply++) {
                visit.accept(new Step(start, List.copyOf(moves), position));
                var legal = position.legalMoves();
                if (legal.isEmpty() || ply == maxPlies
                        || Rules.isInsufficientMaterial(position) || Rules.isFiftyMoveRule(position)) {
                    break;
                }
                var move = legal.get(random.nextInt(legal.size()));
                moves.add(move.toUci());
                position = position.apply(move);
            }
        }
    }

    /// The FEN of `position` the way Stockfish writes it: with an en passant
    /// target only when an en passant capture is legal.
    static String fenAsStockfishWritesIt(Position position) {
        var fields = position.toFen().split(" ");
        if (position.legalMoves().stream().noneMatch(move -> move.kind() instanceof MoveKind.EnPassant)) {
            fields[3] = "-";
        }
        return String.join(" ", fields);
    }
}
