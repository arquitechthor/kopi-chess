package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.MaterialBalance;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A game as the adapters show it: everything a page or a JSON response
/// needs, already computed by the domain, so no adapter applies chess rules
/// (design §10.2, §11.3).
///
/// @param id          the game
/// @param origin      played here or imported from PGN
/// @param status      in progress or finished
/// @param mode        against a bot, two people here, online or imported
/// @param white       who plays White; empty for an imported game
/// @param black       who plays Black; empty for an imported game
/// @param humanColor  the colour of the one person here (against a bot or
///                    online); empty for two people here and for an
///                    imported game
/// @param botProfile  the bot, if a bot plays
/// @param fen         the current position
/// @param sideToMove  whose turn it is
/// @param inCheck     whether the side to move is in check
/// @param localToMove whether a person at this computer may move now
/// @param material    who is ahead in material on the board, and by what
/// @param moves       every move played, in order
/// @param legalMoves  the legal moves of the current position, sorted by
///                    UCI; empty when the game is over
/// @param outcome     how it ended, once it has
/// @param result      the score, once it has ended
/// @param version     for optimistic locking (`If-Match`)
/// @param createdAt   when it started
/// @param finishedAt  when it ended, once it has
/// @param pgnTags     an imported game's PGN tags (`White`, `Event`…); empty
///                    for a game played here
public record GameView(
        GameId id,
        GameOrigin origin,
        GameStatus status,
        GameMode mode,
        Optional<Player> white,
        Optional<Player> black,
        Optional<Color> humanColor,
        Optional<BotProfile> botProfile,
        String fen,
        Color sideToMove,
        boolean inCheck,
        boolean localToMove,
        MaterialBalance material,
        List<PlayedMoveView> moves,
        List<LegalMoveView> legalMoves,
        Optional<GameOutcome> outcome,
        Optional<GameResult> result,
        long version,
        Instant createdAt,
        Optional<Instant> finishedAt,
        Map<String, String> pgnTags) {

    public GameView {
        moves = List.copyOf(moves);
        legalMoves = List.copyOf(legalMoves);
        pgnTags = Collections.unmodifiableMap(new LinkedHashMap<>(pgnTags));
    }

    /// A move of the game.
    ///
    /// @param ply 1 for White's first move…
    /// @param uci `e2e4`
    /// @param san `e4`
    public record PlayedMoveView(int ply, String uci, String san) {}

    /// A move that may be played now. Both notations: UCI for the board and
    /// the API, SAN for the move list and office mode.
    ///
    /// @param uci `g1f3`
    /// @param san `Nf3`
    public record LegalMoveView(String uci, String san) {}

    /// The last move played, if any: the board highlights it.
    public Optional<PlayedMoveView> lastMove() {
        return moves.isEmpty() ? Optional.empty() : Optional.of(moves.getLast());
    }

    /// Whether it is the turn of a person at this computer in a game still
    /// in progress (in a game between two people here, always).
    public boolean isHumanToMove() {
        return localToMove;
    }

    /// Who plays `color`, if anyone does (not in an imported game).
    public Optional<Player> player(Color color) {
        return color == Color.WHITE ? white : black;
    }

    static GameView of(Game game) {
        var position = game.currentPosition();
        var moves = game.moves().stream()
                .map(played -> new PlayedMoveView(played.ply(), played.move().toUci(), played.san()))
                .toList();
        var legalMoves = game.status() == GameStatus.FINISHED
                ? List.<LegalMoveView>of()
                : position.legalMoves().stream()
                        .sorted(Comparator.comparing(Move::toUci))
                        .map(move -> new LegalMoveView(move.toUci(), San.format(position, move)))
                        .toList();
        return new GameView(game.id(), game.origin(), game.status(), game.mode(), game.player(Color.WHITE),
                game.player(Color.BLACK), game.humanColor(), game.botProfile(), position.toFen(),
                position.sideToMove(), position.isInCheck(), game.isLocalToMove(), position.materialBalance(), moves,
                legalMoves, game.outcome(), game.result(), game.version(), game.createdAt(), game.finishedAt(),
                game.pgnTags());
    }
}
