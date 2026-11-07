package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Optional;

/// Playing online against a friend (plan of player versus player, phase D):
/// the lobby (invitations and games), inviting and answering, keeping in
/// step with the relay, and the draw offers. Moves and resignations go
/// through the usual use cases, which send them to the relay for an online
/// game.
///
/// Every method may throw
/// [com.arquitechthor.kopi.application.port.KopiOnlineException]
/// (`NOT_SIGNED_IN`, `NO_KOPI_ID`, `UNREACHABLE`, the relay's refusals…).
public interface OnlineUseCase {

    /// The invitations and online games, after bringing the games here in
    /// step with the relay. Never throws: problems are in the view.
    LobbyView lobby();

    /// Invites the friend with `kopiId`; `color` is mine, or empty for
    /// random.
    void invite(String kopiId, Optional<Color> color);

    /// Accepts an invitation: the game starts here and in the relay.
    GameId accept(String relayGameId);

    void decline(String relayGameId);

    /// Withdraws an invitation I sent.
    void cancel(String relayGameId);

    /// Dismisses an invitation the friend declined.
    void dismiss(String relayGameId);

    /// Brings every online game here in step with the relay. Quiet when
    /// signed out.
    void sync();

    /// How an online game stands beyond the board: the friend, a draw
    /// offer, whether it is desynced. Empty for a game that is not online.
    Optional<OnlineStatus> status(GameId gameId);

    GameView offerDraw(GameId gameId);

    GameView acceptDraw(GameId gameId);

    GameView declineDraw(GameId gameId);

    /// An online game beyond the board.
    ///
    /// @param opponentKopiId the friend
    /// @param myColor        my colour
    /// @param drawOfferBy    a pending draw offer, and whose
    /// @param desynced       the game stopped fitting the relay's
    record OnlineStatus(String opponentKopiId, Color myColor, Optional<Color> drawOfferBy, boolean desynced) {

        /// Whether the friend offers me a draw.
        public boolean drawOffered() {
            return drawOfferBy.filter(color -> color != myColor).isPresent();
        }

        /// Whether I offered one and wait for the answer.
        public boolean drawPending() {
            return drawOfferBy.filter(color -> color == myColor).isPresent();
        }
    }
}
