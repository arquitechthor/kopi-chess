package com.arquitechthor.kopi.bootstrap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/// Kopi Chess answers only this machine's browser (design §15, ADR-016),
/// for the web pages and the API alike.
///
/// - **The `Host` must be a local name** (`localhost`, `127.0.0.1`, `[::1]`
///   by default). A page that rebinds its own domain to 127.0.0.1 (DNS
///   rebinding) reaches the port, but its requests still say
///   `Host: its-domain`, and are refused: it cannot read the games.
/// - **A change must come from a local page.** `POST`, `PUT`, `PATCH` and
///   `DELETE` are refused if their `Origin` is another site, or, without
///   `Origin`, if `Sec-Fetch-Site` says another site sent them. Any page
///   can submit a form to `localhost:8080`; this is what stops it, with no
///   tokens in the forms. A request with neither header (`curl`, a script)
///   is not a browser being misused, and goes through.
///
/// Refused requests get a `403` and a line in the log saying why.
final class LocalAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalAccessFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> SAME_SITE_FETCH = Set.of("same-origin", "none");

    private final Set<String> allowedHosts;

    /// @param allowedHosts the host names this machine is reached by, without
    ///                     port; IPv6 in brackets (`[::1]`)
    LocalAccessFilter(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream().map(host -> host.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var refusal = refusal(request);
        if (refusal.isPresent()) {
            log.warn("Refused {} {}: {}. Reaching Kopi Chess by another name? Add it to KOPI_ALLOWED_HOSTS.",
                    request.getMethod(), request.getRequestURI(), refusal.get());
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /// Why the request is refused, or nothing if it may go on.
    Optional<String> refusal(HttpServletRequest request) {
        // A browser always sends Host (HTTP/1.1); without it, the name the server was reached by.
        var host = Objects.requireNonNullElse(request.getHeader("Host"), request.getServerName());
        if (!isAllowed(hostName(host))) {
            return Optional.of("Host " + host);
        }
        if (SAFE_METHODS.contains(request.getMethod())) {
            return Optional.empty();
        }
        var origin = request.getHeader("Origin");
        if (origin != null) {
            return isAllowed(originHost(origin)) ? Optional.empty() : Optional.of("Origin " + origin);
        }
        var fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !SAME_SITE_FETCH.contains(fetchSite.toLowerCase(Locale.ROOT))) {
            return Optional.of("Sec-Fetch-Site " + fetchSite);
        }
        return Optional.empty();
    }

    private boolean isAllowed(String hostName) {
        return hostName != null && allowedHosts.contains(hostName);
    }

    /// The host name in a `Host` header, without the port: `localhost:8080`
    /// → `localhost`, `[::1]:8080` → `[::1]`.
    static String hostName(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        var lower = authority.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("[")) {
            var end = lower.indexOf(']');
            return end < 0 ? null : lower.substring(0, end + 1);
        }
        var colon = lower.indexOf(':');
        return colon < 0 ? lower : lower.substring(0, colon);
    }

    /// The host name of an `Origin` (`http://localhost:8080` → `localhost`);
    /// `null` for `Origin: null` (a sandboxed page, a file) or nonsense.
    static String originHost(String origin) {
        try {
            var host = URI.create(origin.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
