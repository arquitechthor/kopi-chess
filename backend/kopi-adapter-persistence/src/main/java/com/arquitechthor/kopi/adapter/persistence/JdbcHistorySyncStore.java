package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.HistorySyncStore;
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

/// [HistorySyncStore] on PostgreSQL: the single row of `history_sync` and the
/// rows of `history_game` (V8).
public final class JdbcHistorySyncStore implements HistorySyncStore {

    private final JdbcClient jdbc;

    public JdbcHistorySyncStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Settings settings() {
        return jdbc.sql("select enabled, account_sub, cursor_value, last_sync_at, last_problem from history_sync"
                        + " where id = 1")
                .query((rs, _) -> new Settings(rs.getBoolean("enabled"), rs.getString("account_sub"),
                        rs.getString("cursor_value"),
                        Optional.ofNullable(rs.getObject("last_sync_at", OffsetDateTime.class))
                                .map(OffsetDateTime::toInstant),
                        Optional.ofNullable(rs.getString("last_problem"))))
                .optional()
                .orElse(Settings.OFF);
    }

    @Override
    public void saveSettings(Settings settings) {
        jdbc.sql("""
                insert into history_sync (id, enabled, account_sub, cursor_value, last_sync_at, last_problem)
                values (1, :enabled, :sub, :cursor, :lastSync, :problem)
                on conflict (id) do update
                   set enabled = excluded.enabled, account_sub = excluded.account_sub,
                       cursor_value = excluded.cursor_value, last_sync_at = excluded.last_sync_at,
                       last_problem = excluded.last_problem""")
                .param("enabled", settings.enabled())
                .param("sub", settings.accountSub())
                .param("cursor", settings.cursor())
                .param("lastSync", settings.lastSyncAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null))
                .param("problem", settings.lastProblem().orElse(null))
                .update();
    }

    @Override
    public List<SyncedGame> syncedGames() {
        return jdbc.sql("select game_id, rev, synced_version from history_game").query(JdbcHistorySyncStore::row)
                .list();
    }

    @Override
    public Optional<SyncedGame> find(GameId gameId) {
        return jdbc.sql("select game_id, rev, synced_version from history_game where game_id = :id")
                .param("id", gameId.value())
                .query(JdbcHistorySyncStore::row)
                .optional();
    }

    @Override
    public void save(SyncedGame game) {
        jdbc.sql("""
                insert into history_game (game_id, rev, synced_version) values (:id, :rev, :version)
                on conflict (game_id) do update set rev = excluded.rev, synced_version = excluded.synced_version""")
                .param("id", game.gameId().value())
                .param("rev", game.rev())
                .param("version", game.syncedVersion())
                .update();
    }

    @Override
    public void remove(GameId gameId) {
        jdbc.sql("delete from history_game where game_id = :id").param("id", gameId.value()).update();
    }

    @Override
    public void clearGames() {
        jdbc.sql("delete from history_game").update();
    }

    private static SyncedGame row(ResultSet rs, int rowNum) throws SQLException {
        return new SyncedGame(new GameId(rs.getObject("game_id", UUID.class)), rs.getLong("rev"),
                rs.getLong("synced_version"));
    }
}
