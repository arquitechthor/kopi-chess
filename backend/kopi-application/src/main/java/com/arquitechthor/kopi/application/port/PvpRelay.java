package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Color;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The relay of online games in the backend Kopi (ADR-020,
/// `docs/protocolo-rele.md`, §4 and §5): it orders and hands out moves, and
/// knows nothing about the rules of chess. Kopi Chess validates everything
/// with its domain.
///
/// Every method may throw [KopiOnlineException] with the relay's code.
public interface PvpRelay {

    /// Invites the friend with `opponentKopiId`; `color` is mine, or `null`
    /// for random.
    RelayGame invite(String opponentKopiId, Color color, Optional<String> initialFen);

    /// Every live game of the account, without moves.
    List<RelayGame> myGames();

    /// One game with its moves from `fromPly` (1 for all).
    RelayGame game(String relayGameId, int fromPly);

    RelayGame accept(String relayGameId);

    RelayGame decline(String relayGameId);

    void cancel(String relayGameId);

    /// Sends my move number `ply`, with the Zobrist hash of the position it
    /// leads to, and how the game ends if it does (by the rules).
    ///
    /// @throws KopiOnlineException `WRONG_PLY` if another move came first
    RelayGame move(String relayGameId, int ply, String uci, String zobrist, Optional<Ending> ending);

    RelayGame resign(String relayGameId);

    RelayGame offerDraw(String relayGameId);

    RelayGame acceptDraw(String relayGameId);

    RelayGame declineDraw(String relayGameId);

    /// "I have seen the end": once both players have, the relay forgets the
    /// game. Already gone counts as done.
    void acknowledge(String relayGameId);

    /// A game as the relay has it, seen by this account.
    ///
    /// @param initialFen  empty for the usual initial position
    /// @param firstMover  the colour that plays move 1
    /// @param lastPly     how many moves the relay has accepted
    /// @param result      `1-0`, `0-1` or `1/2-1/2` once finished
    /// @param termination how it finished (the relay's names)
    /// @param finishedBy  the colour that ended it
    /// @param moves       only from [#game], from the ply asked for
    record RelayGame(
            String id,
            Status status,
            String whiteKopiId,
            String blackKopiId,
            Color myColor,
            Color invitedBy,
            Optional<String> initialFen,
            Color firstMover,
            int lastPly,
            Optional<Instant> lastMoveAt,
            Optional<Color> drawOfferBy,
            Optional<String> result,
            Optional<String> termination,
            Optional<Color> finishedBy,
            Instant createdAt,
            Instant expiresAt,
            List<RelayMove> moves) {

        public RelayGame {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(myColor, "myColor");
            moves = List.copyOf(moves);
        }

        /// The friend's Kopi id.
        public String opponentKopiId() {
            return myColor == Color.WHITE ? blackKopiId : whiteKopiId;
        }

        /// Whether this account sent the invitation.
        public boolean invitedByMe() {
            return invitedBy == myColor;
        }
    }

    /// Where a relay game stands.
    enum Status { INVITED, ACTIVE, FINISHED, DECLINED }

    /// A move as the relay keeps it.
    record RelayMove(int ply, String uci, String zobrist, Instant at) {}

    /// How a move ends the game by the rules: what the relay records with it.
    ///
    /// @param result      `1-0`, `0-1` or `1/2-1/2`
    /// @param termination `CHECKMATE`, `STALEMATE`, `INSUFFICIENT_MATERIAL`,
    ///                    `THREEFOLD_REPETITION` or `FIFTY_MOVE_RULE`
    record Ending(String result, String termination) {}
}
