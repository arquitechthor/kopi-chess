package com.arquitechthor.kopi.domain.chess;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// A game read from PGN: what [Pgn#parse] gives.
///
/// The moves are legal [Move]s, checked one by one from the initial
/// position; nothing else in the text (comments, variations, clocks,
/// evaluations) is kept.
///
/// @param tags            the tag pairs, in the order written
///                        (`Event`, `White`, `Result`…)
/// @param initialPosition the standard start, or the `FEN` tag's position
/// @param moves           the moves, in order
/// @param result          the game termination marker (`1-0`…), or the
///                        `Result` tag if the movetext has none
public record PgnGame(Map<String, String> tags, Position initialPosition, List<Move> moves, Result result) {

    public PgnGame {
        tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        Objects.requireNonNull(initialPosition, "initialPosition");
        moves = List.copyOf(moves);
        Objects.requireNonNull(result, "result");
    }

    /// How a PGN says the game ended.
    public enum Result {
        /// `1-0`
        WHITE_WINS("1-0"),
        /// `0-1`
        BLACK_WINS("0-1"),
        /// `1/2-1/2`
        DRAW("1/2-1/2"),
        /// `*`: unknown, unfinished or abandoned.
        UNKNOWN("*");

        private final String token;

        Result(String token) {
            this.token = token;
        }

        /// How it is written in PGN.
        public String token() {
            return token;
        }

        /// The result written as `token`, if it is one.
        public static Optional<Result> of(String token) {
            for (var result : values()) {
                if (result.token.equals(token)) {
                    return Optional.of(result);
                }
            }
            return Optional.empty();
        }
    }

    /// The value of a tag, if the PGN has it.
    public Optional<String> tag(String name) {
        return Optional.ofNullable(tags.get(name));
    }

    /// The position after every move.
    public Position finalPosition() {
        var position = initialPosition;
        for (var move : moves) {
            position = position.apply(move);
        }
        return position;
    }
}
