package com.arquitechthor.kopi.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/// Where the Kopi session is kept on this computer (ADR-019): the tokens
/// of the `kopi-chess-client` app client, which only open the person's Kopi
/// id and online games. One session at a time.
public interface KopiSessionStore {

    Optional<StoredSession> load();

    /// Replaces the stored session, if any.
    void save(StoredSession session);

    void clear();

    /// The tokens and who they belong to.
    ///
    /// @param accessToken  sent with every call to the backend Kopi
    /// @param refreshToken to get a new access token; 30 days
    /// @param expiresAt    when `accessToken` stops working
    /// @param sub          the account's id in Cognito
    /// @param email        shown to the person on the account page
    record StoredSession(String accessToken, String refreshToken, Instant expiresAt, String sub, String email) {
        public StoredSession {
            Objects.requireNonNull(accessToken, "accessToken");
            Objects.requireNonNull(refreshToken, "refreshToken");
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(sub, "sub");
            email = email == null ? "" : email;
        }
    }
}
