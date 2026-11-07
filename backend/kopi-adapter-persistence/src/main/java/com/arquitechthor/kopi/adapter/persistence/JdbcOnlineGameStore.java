package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.OnlineGameStore;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/// [OnlineGameStore] on PostgreSQL: `online_game` (V7).
public final class JdbcOnlineGameStore implements OnlineGameStore {

    private static final String COLUMNS =
            "o.game_id, o.relay_game_id, o.account_sub, o.desynced, o.last_move_at, o.draw_offer_by, o.acknowledged";

    private final JdbcClient jdbc;

    public JdbcOnlineGameStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<OnlineLink> findByGame(GameId gameId) {
        return jdbc.sql("select " + COLUMNS + " from online_game o where o.game_id = :id")
                .param("id", gameId.value())
                .query(JdbcOnlineGameStore::link)
                .optional();
    }

    @Override
    public Optional<OnlineLink> findByRelay(String relayGameId) {
        return jdbc.sql("select " + COLUMNS + " from online_game o where o.relay_game_id = :relay")
                .param("relay", relayGameId)
                .query(JdbcOnlineGameStore::link)
                .optional();
    }

    @Override
    public List<OnlineLink> inProgress(String accountSub) {
        return jdbc.sql("select " + COLUMNS + " from online_game o join game g on g.id = o.game_id"
                        + " where o.account_sub = :sub and g.status = 'IN_PROGRESS' order by g.created_at")
                .param("sub", accountSub)
                .query(JdbcOnlineGameStore::link)
                .list();
    }

    @Override
    public void save(OnlineLink link) {
        jdbc.sql("""
                insert into online_game (game_id, relay_game_id, account_sub, desynced, last_move_at, draw_offer_by,
                                         acknowledged)
                values (:game, :relay, :sub, :desynced, :lastMove, :offer, :acknowledged)
                on conflict (game_id) do update
                   set desynced = excluded.desynced, last_move_at = excluded.last_move_at,
                       draw_offer_by = excluded.draw_offer_by, acknowledged = excluded.acknowledged""")
                .param("game", link.gameId().value())
                .param("relay", link.relayGameId())
                .param("sub", link.accountSub())
                .param("desynced", link.desynced())
                .param("lastMove", link.lastMoveAt().atOffset(ZoneOffset.UTC))
                .param("offer", link.drawOfferBy().map(Color::name).orElse(null))
                .param("acknowledged", link.acknowledged())
                .update();
    }

    private static OnlineLink link(ResultSet rs, int rowNumber) throws SQLException {
        var offer = rs.getString("draw_offer_by");
        return new OnlineLink(new GameId(rs.getObject("game_id", UUID.class)), rs.getString("relay_game_id"),
                rs.getString("account_sub"), rs.getBoolean("desynced"),
                rs.getObject("last_move_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(offer).map(Color::valueOf), rs.getBoolean("acknowledged"));
    }
}
