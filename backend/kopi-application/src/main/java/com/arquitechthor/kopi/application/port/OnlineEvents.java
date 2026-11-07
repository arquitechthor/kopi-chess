package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;

/// Tells the pages that something online changed, so they refresh without
/// a reload (plan of player versus player, phase D): a friend's move, an
/// invitation, an answer. Best effort: a page that misses it catches up when
/// it is reloaded.
public interface OnlineEvents {

    /// A game here changed because of the relay.
    void gameChanged(GameId gameId);

    /// The invitations or the list of online games changed.
    void lobbyChanged();

    /// Nobody listens.
    OnlineEvents NONE = new OnlineEvents() {
        @Override
        public void gameChanged(GameId gameId) {}

        @Override
        public void lobbyChanged() {}
    };
}
