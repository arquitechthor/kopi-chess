package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// The online page: the account, the invitations and the games.
///
/// @param account  the Kopi account (signed in, Kopi id…)
/// @param problem  why the relay could not be asked, if it could not
/// @param received invitations friends sent me
/// @param sent     invitations I sent, still unanswered
/// @param declined invitations of mine a friend declined
/// @param games    online games in progress here
public record LobbyView(AccountView account, Optional<KopiOnlineException.Problem> problem,
                        List<Invitation> received, List<Invitation> sent, List<Invitation> declined,
                        List<OnlineGame> games) {

    public LobbyView {
        received = List.copyOf(received);
        sent = List.copyOf(sent);
        declined = List.copyOf(declined);
        games = List.copyOf(games);
    }

    /// Just the account: nothing asked of the relay.
    static LobbyView of(AccountView account, Optional<KopiOnlineException.Problem> problem) {
        return new LobbyView(account, problem, List.of(), List.of(), List.of(), List.of());
    }

    /// An invitation.
    ///
    /// @param relayGameId    to answer it
    /// @param opponentKopiId the friend
    /// @param myColor        my colour in it
    public record Invitation(String relayGameId, String opponentKopiId, Color myColor, Instant createdAt) {}

    /// An online game in progress here.
    ///
    /// @param myTurn whether I am to move
    public record OnlineGame(GameId gameId, String opponentKopiId, Color myColor, int plies, boolean myTurn,
                             boolean desynced) {}
}
