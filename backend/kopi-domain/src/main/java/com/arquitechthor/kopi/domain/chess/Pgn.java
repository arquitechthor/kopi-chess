package com.arquitechthor.kopi.domain.chess;

import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.AMBIGUOUS_MOVE;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.EMPTY;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.ILLEGAL_MOVE;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.INVALID_FEN;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.MALFORMED_MOVE;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.MALFORMED_TAG;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.MORE_THAN_ONE_GAME;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.UNBALANCED_VARIATION;
import static com.arquitechthor.kopi.domain.chess.InvalidPgnException.Problem.UNTERMINATED_COMMENT;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Portable Game Notation: how chess games travel between programs
/// (Lichess, Chess.com, databases, books).
///
/// ```
/// [Event "Paris"]
/// [White "Paul Morphy"]
/// [Result "1-0"]
///
/// 1. e4 e5 2. Nf3 d6 { Philidor } 3. d4 Bg4?! (3... exd4) 4. dxe5 $2 … 17. Rd8# 1-0
/// ```
///
/// [#parse] reads **one** game, tolerantly: it keeps the tags and the main
/// line, and skips what only matters to people (comments `{…}` and `;…`,
/// variations `(…)` however nested, NAGs `$1`, annotations `!?`), so the
/// clock and evaluation comments of Lichess and Chess.com exports
/// (`{ [%clk 0:03:00] }`) are no trouble. What it never does is guess a
/// move: every one must be legal SAN in its position (see [San#parse]), and
/// the first that is not is reported with its line, number and side.
///
/// [#format] writes a game back, in the PGN standard's export format:
/// reading what it writes gives the same game.
public final class Pgn {

    /// Movetext lines stay within this width (the standard's export format
    /// recommends 80).
    static final int LINE_WIDTH = 80;

    /// A move number before a move, possibly stuck to it: `12.`, `12...`,
    /// `12.e4`.
    private static final Pattern MOVE_NUMBER = Pattern.compile("^\\d+\\.+");

    /// An annotation written apart from its move: `e4 !?`.
    private static final Pattern ANNOTATION = Pattern.compile("[!?]+");

    private Pgn() {}

    /// Reads one game.
    ///
    /// The result is the game termination marker at the end of the moves
    /// (`1-0`, `0-1`, `1/2-1/2`, `*`), or the `Result` tag if the moves end
    /// without one.
    ///
    /// @throws InvalidPgnException with the problem and where it is
    public static PgnGame parse(String pgn) {
        return new Reader(pgn == null ? "" : pgn).game();
    }

    /// Writes `game` as PGN: its tags (with `SetUp` and `FEN` added if it
    /// does not start from the standard position and has no `FEN` tag),
    /// a blank line, and the moves numbered, `1... e5` if Black moves
    /// first, ending with the result, in lines of at most [#LINE_WIDTH].
    public static String format(PgnGame game) {
        var pgn = new StringBuilder();
        var tags = new LinkedHashMap<>(game.tags());
        var fen = game.initialPosition().toFen();
        if (!fen.equals(Fen.INITIAL) && !tags.containsKey("FEN")) {
            tags.put("SetUp", "1");
            tags.put("FEN", fen);
        }
        tags.forEach((name, value) -> pgn.append('[').append(name).append(" \"")
                .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\"]\n"));
        if (!tags.isEmpty()) {
            pgn.append('\n');
        }

        var words = new ArrayList<String>();
        var position = game.initialPosition();
        for (var move : game.moves()) {
            var san = San.format(position, move);
            if (position.sideToMove() == Color.WHITE) {
                words.add(position.fullmoveNumber() + ". " + san);
            } else if (words.isEmpty()) {
                words.add(position.fullmoveNumber() + "... " + san);
            } else {
                words.add(san);
            }
            position = position.apply(move);
        }
        words.add(game.result().token());

        var line = new StringBuilder();
        for (var word : words) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > LINE_WIDTH) {
                pgn.append(line).append('\n');
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        return pgn.append(line).append('\n').toString();
    }

    /// A one-pass reader over the text, keeping count of lines.
    private static final class Reader {

        private final String text;
        private int at;
        private int line = 1;

        private final Map<String, String> tags = new LinkedHashMap<>();
        private final List<Move> moves = new ArrayList<>();
        /// The starting position, once the tags are read; then the current one.
        private Position initial;
        private Position position;
        private PgnGame.Result result;
        private boolean started;

        Reader(String text) {
            // A byte order mark is not part of the game.
            this.text = text.startsWith("﻿") ? text.substring(1) : text;
        }

        PgnGame game() {
            while (skipSpaceAndComments()) {
                var c = text.charAt(at);
                if (result != null) {
                    // Anything after the termination marker is a second game.
                    throw new InvalidPgnException(MORE_THAN_ONE_GAME, line, word());
                }
                if (c == '[') {
                    if (started) {
                        throw new InvalidPgnException(MORE_THAN_ONE_GAME, line, word());
                    }
                    tag();
                } else if (c == '(') {
                    variation();
                } else if (c == ')') {
                    throw new InvalidPgnException(UNBALANCED_VARIATION, line, ")");
                } else if (c == '$') {
                    word(); // a NAG: $1, $14…
                } else {
                    symbol(word());
                }
            }
            if (!started && tags.isEmpty()) {
                throw new InvalidPgnException(EMPTY, line, "");
            }
            var ending = result != null ? result
                    : PgnGame.Result.of(tags.getOrDefault("Result", "*")).orElse(PgnGame.Result.UNKNOWN);
            start();
            return new PgnGame(tags, initial, moves, ending);
        }

        /// A word of movetext: a move number, a move, a result or an
        /// annotation.
        private void symbol(String word) {
            var endOfGame = PgnGame.Result.of(word);
            if (endOfGame.isPresent()) {
                result = endOfGame.get();
                started = true;
                return;
            }
            var san = MOVE_NUMBER.matcher(word).replaceFirst("");
            if (san.isEmpty() || ANNOTATION.matcher(san).matches()) {
                return;
            }
            move(san);
        }

        private void move(String san) {
            var current = start();
            started = true;
            try {
                var move = San.parse(current, san);
                moves.add(move);
                position = current.apply(move);
            } catch (InvalidSanException e) {
                var problem = switch (e.problem()) {
                    case MALFORMED -> MALFORMED_MOVE;
                    case NO_SUCH_MOVE -> ILLEGAL_MOVE;
                    case AMBIGUOUS -> AMBIGUOUS_MOVE;
                };
                throw new InvalidPgnException(problem, line, san, current.fullmoveNumber(), current.sideToMove());
            }
        }

        /// The position the next move is played in: the start (the `FEN`
        /// tag's, if any) before the first move.
        private Position start() {
            if (position == null) {
                var fen = Optional.ofNullable(tags.get("FEN"));
                try {
                    initial = fen.map(Fen::parse).orElseGet(Position::initial);
                    position = initial;
                } catch (InvalidFenException e) {
                    throw new InvalidPgnException(INVALID_FEN, line, fen.get());
                }
            }
            return position;
        }

        /// `[Name "value"]`, with `\"` and `\\` escapes in the value.
        private void tag() {
            var startLine = line;
            var end = text.indexOf(']', at);
            var open = text.indexOf('"', at);
            if (end < 0 || open < 0 || open > end) {
                throw new InvalidPgnException(MALFORMED_TAG, startLine, restOfLine());
            }
            var name = text.substring(at + 1, open).strip();
            var value = new StringBuilder();
            var i = open + 1;
            for (; i < text.length() && text.charAt(i) != '"'; i++) {
                var c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length()) {
                    c = text.charAt(++i);
                }
                if (c == '\n') {
                    throw new InvalidPgnException(MALFORMED_TAG, startLine, restOfLine());
                }
                value.append(c);
            }
            var close = i < text.length() ? text.indexOf(']', i) : -1;
            if (name.isEmpty() || !name.matches("[A-Za-z0-9_]+") || close < 0
                    || !text.substring(i + 1, close).isBlank()) {
                throw new InvalidPgnException(MALFORMED_TAG, startLine, restOfLine());
            }
            tags.put(name, value.toString());
            at = close + 1;
        }

        /// Skips a variation `(…)` with everything in it, nested ones and
        /// comments included.
        private void variation() {
            var startLine = line;
            var depth = 0;
            do {
                if (!skipSpaceAndComments()) {
                    throw new InvalidPgnException(UNBALANCED_VARIATION, startLine, "(");
                }
                var c = text.charAt(at);
                if (c == '(') {
                    depth++;
                    at++;
                } else if (c == ')') {
                    depth--;
                    at++;
                } else {
                    word();
                }
            } while (depth > 0);
        }

        /// Moves past spaces, `{…}` and `;…` comments and `%` escape lines.
        ///
        /// @return whether there is anything left
        private boolean skipSpaceAndComments() {
            while (at < text.length()) {
                var c = text.charAt(at);
                if (c == '\n') {
                    line++;
                    at++;
                } else if (Character.isWhitespace(c)) {
                    at++;
                } else if (c == '{') {
                    var startLine = line;
                    var end = text.indexOf('}', at);
                    if (end < 0) {
                        throw new InvalidPgnException(UNTERMINATED_COMMENT, startLine, "{");
                    }
                    line += (int) text.substring(at, end).chars().filter(ch -> ch == '\n').count();
                    at = end + 1;
                } else if (c == ';' || (c == '%' && (at == 0 || text.charAt(at - 1) == '\n'))) {
                    var end = text.indexOf('\n', at);
                    at = end < 0 ? text.length() : end;
                } else {
                    return true;
                }
            }
            return false;
        }

        /// The word starting here, up to a space or a delimiter; at least
        /// one character.
        private String word() {
            var start = at;
            do {
                at++;
            } while (at < text.length() && !Character.isWhitespace(text.charAt(at))
                    && "{}()[];$".indexOf(text.charAt(at)) < 0);
            return text.substring(start, at);
        }

        private String restOfLine() {
            var end = text.indexOf('\n', at);
            return text.substring(at, end < 0 ? text.length() : end).strip();
        }
    }
}
