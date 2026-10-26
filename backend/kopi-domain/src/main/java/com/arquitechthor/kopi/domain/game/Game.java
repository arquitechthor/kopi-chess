package com.arquitechthor.kopi.domain.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.PgnGame;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.Rules;
import com.arquitechthor.kopi.domain.chess.San;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// A game of chess, played here against a bot or imported from PGN: the
/// aggregate root of the domain.
///
/// It guards the invariants of design §5.2, so no controller or use case
/// has to:
///
/// - moves are only played while the game is in progress, and only by the
///   side to move;
/// - every move played is legal in the current position;
/// - after each move the game checks whether it has ended;
/// - every change increases the version (optimistic locking).
///
/// Immutable, like [Position]: [#play] and [#resign] return the game as it
/// is afterwards and leave this one untouched. The time of each change is
/// passed in (from the application's `Clock` port) instead of read here,
/// so the domain stays deterministic.
///
/// An imported game ([#imported]) has no person and no bot, arrives
/// finished, and keeps its PGN tags (`White`, `Event`…).
public final class Game {

    private final GameId id;
    private final GameOrigin origin;
    /// `null` for an imported game.
    private final Color humanColor;
    /// `null` for an imported game.
    private final BotProfile botProfile;
    private final Position initialPosition;
    private final List<PlayedMove> moves;
    private final Optional<GameOutcome> outcome;
    private final long version;
    private final Instant createdAt;
    private final Optional<Instant> finishedAt;
    private final Map<String, String> pgnTags;

    private Game(GameId id, GameOrigin origin, Color humanColor, BotProfile botProfile, Position initialPosition,
            List<PlayedMove> moves, Optional<GameOutcome> outcome, long version,
            Instant createdAt, Optional<Instant> finishedAt, Map<String, String> pgnTags) {
        this.id = id;
        this.origin = origin;
        this.humanColor = humanColor;
        this.botProfile = botProfile;
        this.initialPosition = initialPosition;
        this.moves = List.copyOf(moves);
        this.outcome = outcome;
        this.version = version;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.pgnTags = Collections.unmodifiableMap(new LinkedHashMap<>(pgnTags));
    }

    /// A new game against `botProfile` from `initialPosition` (usually
    /// [Position#initial]), with the human playing `humanColor`.
    ///
    /// If the bot's colour is to move, the bot plays first: the application
    /// asks it for a move right away.
    ///
    /// A position where the game is already over (mate, stalemate…) gives
    /// a game that is finished from the start.
    public static Game start(GameId id, Color humanColor, BotProfile botProfile, Position initialPosition,
            Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(humanColor, "humanColor");
        Objects.requireNonNull(botProfile, "botProfile");
        Objects.requireNonNull(initialPosition, "initialPosition");
        Objects.requireNonNull(now, "now");
        var outcome = ending(initialPosition, List.of(initialPosition));
        return new Game(id, GameOrigin.PLAYED, humanColor, botProfile, initialPosition, List.of(), outcome, 0, now,
                outcome.map(_ -> now), Map.of());
    }

    /// A game imported from PGN at `now` (plan §9): finished, with no
    /// person and no bot, its moves stamped with the import time (a PGN
    /// does not say when each move was played).
    ///
    /// The board has the last word on how it ended:
    ///
    /// - a final position that is mate or stalemate is that ending, and the
    ///   PGN's result must agree with it (or be `*`);
    /// - otherwise the PGN's result stands, as [GameOutcome.Declared]: a
    ///   resignation, a loss on time, an agreed draw. A draw the board also
    ///   shows at the end (insufficient material, repetition, fifty moves)
    ///   is named as such;
    /// - `*` with no ending on the board is an unfinished game: refused.
    ///
    /// The automatic draws of ADR-008 are **not** applied in the middle of
    /// the game: in real chess they must be claimed, and many real games
    /// carry on past them.
    ///
    /// @throws InvalidImportException if the game has no moves, is not
    ///                                finished or its result contradicts
    ///                                the board
    public static Game imported(GameId id, PgnGame pgn, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pgn, "pgn");
        Objects.requireNonNull(now, "now");
        if (pgn.moves().isEmpty()) {
            throw new InvalidImportException(InvalidImportException.Problem.NO_MOVES, "no moves");
        }
        var position = pgn.initialPosition();
        var played = new ArrayList<PlayedMove>();
        var history = new ArrayList<Position>();
        history.add(position);
        for (var move : pgn.moves()) {
            if (!position.legalMoves().contains(move)) {
                throw new IllegalArgumentException(move + " is not legal in " + position.toFen());
            }
            var after = position.apply(move);
            played.add(new PlayedMove(played.size() + 1, move, San.format(position, move), after, now));
            history.add(after);
            position = after;
        }

        var onBoard = ending(position, history);
        var declared = switch (pgn.result()) {
            case WHITE_WINS -> Optional.of(GameResult.WHITE_WIN);
            case BLACK_WINS -> Optional.of(GameResult.BLACK_WIN);
            case DRAW -> Optional.of(GameResult.DRAW);
            case UNKNOWN -> Optional.<GameResult>empty();
        };
        var forced = onBoard.filter(ending -> ending instanceof GameOutcome.Checkmate
                || ending instanceof GameOutcome.Stalemate);
        GameOutcome outcome;
        if (forced.isPresent()) {
            if (declared.isPresent() && declared.get() != forced.get().result()) {
                throw new InvalidImportException(InvalidImportException.Problem.RESULT_CONTRADICTS_BOARD,
                        pgn.result().token() + " after " + forced.get());
            }
            outcome = forced.get();
        } else if (declared.isPresent()) {
            outcome = onBoard.filter(ending -> ending.result() == declared.get())
                    .orElse(new GameOutcome.Declared(declared.get()));
        } else {
            outcome = onBoard.orElseThrow(() -> new InvalidImportException(
                    InvalidImportException.Problem.NOT_FINISHED, "* and no ending on the board"));
        }
        return new Game(id, GameOrigin.IMPORTED, null, null, pgn.initialPosition(), played, Optional.of(outcome),
                0, now, Optional.of(now), pgn.tags());
    }

    /// The game after `color` plays `move` at `now`.
    ///
    /// @throws GameNotInProgressException if the game is over
    /// @throws NotYourTurnException if `color` is not the side to move
    /// @throws IllegalMoveException if `move` is not legal in the current
    ///                              position
    public Game play(Move move, Color color, Instant now) {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(now, "now");
        var position = requireTurn(color);
        // Equality includes the kind, so a well-formed move that is not
        // the one the rules produce here (a "capture" on an empty square)
        // is refused too.
        if (!position.legalMoves().contains(move)) {
            throw new IllegalMoveException(id, move.toUci(),
                    "(" + move.kind() + ") is not legal in " + position.toFen());
        }
        return apply(position, move, now);
    }

    /// The game after `color` plays the move written as `uci` (`e2e4`,
    /// `e7e8q`) at `now`.
    ///
    /// The text is looked up among the legal moves, never turned into a
    /// move on its own (ADR-004): what it means depends on the position.
    ///
    /// @throws GameNotInProgressException if the game is over
    /// @throws NotYourTurnException if `color` is not the side to move
    /// @throws IllegalMoveException if no legal move is written `uci`
    public Game play(String uci, Color color, Instant now) {
        Objects.requireNonNull(uci, "uci");
        Objects.requireNonNull(now, "now");
        var position = requireTurn(color);
        var move = position.legalMoves().stream()
                .filter(candidate -> candidate.toUci().equals(uci))
                .findFirst()
                .orElseThrow(() -> new IllegalMoveException(id, uci, "is not a legal move in " + position.toFen()));
        return apply(position, move, now);
    }

    /// The current position, after checking that the game is in progress
    /// and that `color` is to move. In that order: a move in a finished
    /// game is refused as such, whoever's turn it was (and an imported
    /// game, which has no person to play, is always finished).
    private Position requireTurn(Color color) {
        requireInProgress();
        Objects.requireNonNull(color, "color");
        var position = currentPosition();
        if (color != position.sideToMove()) {
            throw new NotYourTurnException(id, color);
        }
        return position;
    }

    /// The game after the legal `move` is played from `position` at `now`.
    private Game apply(Position position, Move move, Instant now) {
        var after = position.apply(move);
        var played = new ArrayList<>(moves);
        played.add(new PlayedMove(moves.size() + 1, move, San.format(position, move), after, now));
        var history = new ArrayList<Position>(played.size() + 1);
        history.add(initialPosition);
        played.forEach(each -> history.add(each.positionAfter()));

        var nextOutcome = ending(after, history);
        return new Game(id, origin, humanColor, botProfile, initialPosition, played, nextOutcome, version + 1,
                createdAt, nextOutcome.map(_ -> now), pgnTags);
    }

    /// The game after `color` resigns at `now`. Either side may resign at
    /// any moment, not only on its turn.
    ///
    /// @throws GameNotInProgressException if the game is over
    public Game resign(Color color, Instant now) {
        requireInProgress();
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(now, "now");
        return new Game(id, origin, humanColor, botProfile, initialPosition, moves,
                Optional.of(new GameOutcome.Resignation(color)), version + 1, createdAt, Optional.of(now), pgnTags);
    }

    private void requireInProgress() {
        if (outcome.isPresent()) {
            throw new GameNotInProgressException(id);
        }
    }

    /// How the game ends in `position`, if it does, given every position of
    /// the game so far (`history`, ending with `position`).
    ///
    /// The order matters when several endings apply at once (ADR-008): mate
    /// first, so a mating move that also reaches the fifty-move limit or
    /// repeats a position still wins.
    private static Optional<GameOutcome> ending(Position position, List<Position> history) {
        if (Rules.isCheckmate(position)) {
            return Optional.of(new GameOutcome.Checkmate(position.sideToMove().opposite()));
        }
        if (Rules.isStalemate(position)) {
            return Optional.of(new GameOutcome.Stalemate());
        }
        if (Rules.isInsufficientMaterial(position)) {
            return Optional.of(new GameOutcome.InsufficientMaterial());
        }
        if (occurrences(position, history) >= 3) {
            return Optional.of(new GameOutcome.ThreefoldRepetition());
        }
        if (Rules.isFiftyMoveRule(position)) {
            return Optional.of(new GameOutcome.FiftyMoveRule());
        }
        return Optional.empty();
    }

    /// How many positions of `history` are the same as `position` for the
    /// repetition rule: same Zobrist hash (ADR-008).
    private static long occurrences(Position position, List<Position> history) {
        var hash = position.zobristHash();
        return history.stream().filter(each -> each.zobristHash() == hash).count();
    }

    public GameId id() {
        return id;
    }

    /// Played here or imported from PGN.
    public GameOrigin origin() {
        return origin;
    }

    public GameStatus status() {
        return outcome.isPresent() ? GameStatus.FINISHED : GameStatus.IN_PROGRESS;
    }

    /// The colour the person plays; empty for an imported game.
    public Optional<Color> humanColor() {
        return Optional.ofNullable(humanColor);
    }

    /// The colour the bot plays; empty for an imported game.
    public Optional<Color> botColor() {
        return humanColor().map(Color::opposite);
    }

    /// The opponent; empty for an imported game.
    public Optional<BotProfile> botProfile() {
        return Optional.ofNullable(botProfile);
    }

    /// The tags of the PGN an imported game came from, in their order;
    /// empty for a game played here.
    public Map<String, String> pgnTags() {
        return pgnTags;
    }

    public Position initialPosition() {
        return initialPosition;
    }

    /// Where the game stands now: after the last move, or the initial
    /// position if none has been played.
    public Position currentPosition() {
        return moves.isEmpty() ? initialPosition : moves.getLast().positionAfter();
    }

    /// Every move played, in order. Unmodifiable.
    public List<PlayedMove> moves() {
        return moves;
    }

    /// How the game ended; empty while it is in progress.
    public Optional<GameOutcome> outcome() {
        return outcome;
    }

    /// The score; empty while the game is in progress.
    public Optional<GameResult> result() {
        return outcome.map(GameOutcome::result);
    }

    /// 0 for a new game, plus one for every move or resignation. The
    /// repository saves only if the stored version is the one it loaded.
    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /// When the game ended; empty while it is in progress.
    public Optional<Instant> finishedAt() {
        return finishedAt;
    }
}
