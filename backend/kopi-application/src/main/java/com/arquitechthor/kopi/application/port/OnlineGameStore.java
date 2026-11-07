package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// What ties a game here to its game in the relay (ADR-020), and how far
/// they are in step.
public interface OnlineGameStore {

    Optional<OnlineLink> findByGame(GameId gameId);

    Optional<OnlineLink> findByRelay(String relayGameId);

    /// The links of `accountSub` whose game here is still in progress.
    List<OnlineLink> inProgress(String accountSub);

    /// Inserts or replaces the link of its game.
    void save(OnlineLink link);

    /// One online game.
    ///
    /// @param relayGameId    the game in the relay
    /// @param accountSub     the Kopi account it was played with: if someone
    ///                       signs in with another account, these games are
    ///                       left alone
    /// @param desynced       a move from the relay did not fit (illegal, or
    ///                       another position): the game stops here, with a
    ///                       warning
    /// @param lastMoveAt     the relay's time of its last accepted move (or
    ///                       of the invitation), for the 30-day rule
    /// @param drawOfferBy    a pending draw offer
    /// @param acknowledged   the end was confirmed to the relay
    record OnlineLink(GameId gameId, String relayGameId, String accountSub, boolean desynced, Instant lastMoveAt,
                      Optional<Color> drawOfferBy, boolean acknowledged) {

        public OnlineLink {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(relayGameId, "relayGameId");
            Objects.requireNonNull(accountSub, "accountSub");
            Objects.requireNonNull(lastMoveAt, "lastMoveAt");
            Objects.requireNonNull(drawOfferBy, "drawOfferBy");
        }

        public OnlineLink withDesynced() {
            return new OnlineLink(gameId, relayGameId, accountSub, true, lastMoveAt, drawOfferBy, acknowledged);
        }

        public OnlineLink with(Instant lastMove, Optional<Color> offer) {
            return new OnlineLink(gameId, relayGameId, accountSub, desynced, lastMove, offer, acknowledged);
        }

        public OnlineLink withAcknowledged() {
            return new OnlineLink(gameId, relayGameId, accountSub, desynced, lastMoveAt, drawOfferBy, true);
        }
    }
}
