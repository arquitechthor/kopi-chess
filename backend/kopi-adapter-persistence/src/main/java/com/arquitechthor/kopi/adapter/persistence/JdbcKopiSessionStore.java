package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.KopiSessionStore;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/// [KopiSessionStore] on PostgreSQL: the single row of `kopi_session` (V6).
public final class JdbcKopiSessionStore implements KopiSessionStore {

    private final JdbcClient jdbc;

    public JdbcKopiSessionStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<StoredSession> load() {
        return jdbc.sql("select access_token, refresh_token, expires_at, sub, email from kopi_session where id = 1")
                .query((rs, _) -> new StoredSession(rs.getString("access_token"), rs.getString("refresh_token"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(), rs.getString("sub"),
                        rs.getString("email")))
                .optional();
    }

    @Override
    public void save(StoredSession session) {
        jdbc.sql("""
                insert into kopi_session (id, access_token, refresh_token, expires_at, sub, email, updated_at)
                values (1, :access, :refresh, :expires, :sub, :email, now())
                on conflict (id) do update
                   set access_token = excluded.access_token, refresh_token = excluded.refresh_token,
                       expires_at = excluded.expires_at, sub = excluded.sub, email = excluded.email,
                       updated_at = now()""")
                .param("access", session.accessToken())
                .param("refresh", session.refreshToken())
                .param("expires", session.expiresAt().atOffset(ZoneOffset.UTC))
                .param("sub", session.sub())
                .param("email", session.email())
                .update();
    }

    @Override
    public void clear() {
        jdbc.sql("delete from kopi_session").update();
    }
}
