package com.arquitechthor.kopi.bootstrap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/// Every log line written while a request about one game is handled
/// carries the game's id: `gameId` in the MDC (design §14), a field of its
/// own in the JSON logs.
///
/// The id is read from the path, the same for the pages and the API
/// (`/games/{id}…`, `/api/v1/games/{id}…`), so no adapter has to know about
/// it. A path that is not about a game logs as before.
///
/// The MDC is the thread's, and Tomcat reuses its threads: the id is
/// removed when the request ends, whatever happens.
final class GameLogFilter extends OncePerRequestFilter {

    /// The MDC key, also used by the analysis worker.
    static final String GAME_ID = "gameId";

    private static final Pattern GAME_PATH = Pattern.compile(
            "^/(?:api/v1/)?games/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var gameId = gameId(request.getRequestURI());
        if (gameId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try (var _ = MDC.putCloseable(GAME_ID, gameId.get())) {
            chain.doFilter(request, response);
        }
    }

    /// The game a path is about, if any. Only a well-formed UUID, so
    /// nothing a client makes up ends in the logs.
    static Optional<String> gameId(String path) {
        var matcher = GAME_PATH.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }
}
