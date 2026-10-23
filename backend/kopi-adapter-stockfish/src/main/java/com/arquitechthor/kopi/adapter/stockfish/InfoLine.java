package com.arquitechthor.kopi.adapter.stockfish;

import com.arquitechthor.kopi.domain.chess.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// One `info` line of a search that carries a score:
/// `info depth 15 seldepth 25 multipv 1 score cp 32 … pv e2e4 c7c5`.
///
/// Lines without a score (`info string …`, `info currmove …`) are not
/// `InfoLine`s: [#parse] gives nothing for them.
///
/// @param depth   the search depth of the line
/// @param multiPv which candidate line it is, 1 for the best
/// @param score   the evaluation, **from the side to move** as the engine
///                gives it; see [Score#forWhite]
/// @param bound   whether the score is exact or only a bound (`lowerbound`,
///                `upperbound`, sent while a search is still settling)
/// @param pv      the principal variation in UCI, possibly empty
public record InfoLine(int depth, int multiPv, Score score, Bound bound, List<String> pv) {

    public InfoLine {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(bound, "bound");
        pv = List.copyOf(pv);
    }

    /// An engine score: centipawns, or a forced mate in some moves.
    public sealed interface Score {

        /// `score cp 34`: a third of a pawn better for the side to move.
        record Centipawns(int value) implements Score {}

        /// `score mate 3`: the side to move mates in three moves;
        /// `score mate -2`: it is mated in two; `mate 0`: it is mated now.
        record MateIn(int moves) implements Score {}

        /// The same score from White's point of view (design §6.1: every
        /// evaluation is stored that way). The engine scores from the side
        /// to move, so Black's scores change sign.
        default Score forWhite(Color sideToMove) {
            if (sideToMove == Color.WHITE) {
                return this;
            }
            return switch (this) {
                case Centipawns(var value) -> new Centipawns(-value);
                case MateIn(var moves) -> new MateIn(-moves);
            };
        }
    }

    /// Whether a score is exact.
    public enum Bound {
        EXACT,
        LOWER,
        UPPER
    }

    /// The line, if it is an `info` line with a score.
    static Optional<InfoLine> parse(String line) {
        var tokens = line.strip().split("\\s+");
        if (tokens.length == 0 || !tokens[0].equals("info")) {
            return Optional.empty();
        }
        int depth = -1;
        int multiPv = 1;
        Score score = null;
        var bound = Bound.EXACT;
        var pv = new ArrayList<String>();
        try {
            for (int i = 1; i < tokens.length; i++) {
                switch (tokens[i]) {
                    // Free text until the end of the line: nothing to parse.
                    case "string" -> {
                        return Optional.empty();
                    }
                    case "depth" -> depth = Integer.parseInt(tokens[++i]);
                    case "multipv" -> multiPv = Integer.parseInt(tokens[++i]);
                    case "score" -> {
                        var kind = tokens[++i];
                        var value = Integer.parseInt(tokens[++i]);
                        score = switch (kind) {
                            case "cp" -> new Score.Centipawns(value);
                            case "mate" -> new Score.MateIn(value);
                            default -> null;
                        };
                    }
                    case "lowerbound" -> bound = Bound.LOWER;
                    case "upperbound" -> bound = Bound.UPPER;
                    case "pv" -> {
                        // The pv is always the last field.
                        pv.addAll(List.of(tokens).subList(i + 1, tokens.length));
                        i = tokens.length;
                    }
                    default -> {} // seldepth, nodes, nps, time, wdl, hashfull…: not needed
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException _) {
            return Optional.empty(); // a malformed line is not worth failing a game
        }
        if (score == null || depth < 0) {
            return Optional.empty();
        }
        return Optional.of(new InfoLine(depth, multiPv, score, bound, pv));
    }
}
