package com.arquitechthor.kopi.adapter.rest;

import java.io.Serial;
import java.util.regex.Pattern;

/// The game version as an HTTP entity tag: responses carry it in `ETag`
/// (`"12"`), and changes must send it back in `If-Match` (design §10.1).
final class Versions {

    /// `"12"`, `W/"12"` or a bare `12`.
    private static final Pattern IF_MATCH = Pattern.compile("(?:W/)?\"?(\\d{1,18})\"?");

    private Versions() {}

    /// The `ETag` value for a version: `"12"`.
    static String etag(long version) {
        return "\"" + version + "\"";
    }

    /// The version in an `If-Match` header.
    ///
    /// @throws MissingVersionException if there is no header
    /// @throws InvalidRequestException if it does not hold a version
    static long fromIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new MissingVersionException();
        }
        var matcher = IF_MATCH.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new InvalidRequestException("If-Match must hold the game version, such as \"12\"");
        }
        return Long.parseLong(matcher.group(1));
    }

    /// A change sent without `If-Match` (REST: `428 Precondition Required`).
    static final class MissingVersionException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        MissingVersionException() {
            super("If-Match header missing");
        }
    }
}
