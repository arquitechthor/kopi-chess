package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameFilter;
import java.util.Objects;

/// Page `page` (from 0) of the list of games that pass `filter`, `size`
/// games per page.
public record ListGamesQuery(int page, int size, GameFilter filter) {

    public static final int MAX_SIZE = 100;

    /// Every game.
    public ListGamesQuery(int page, int size) {
        this(page, size, GameFilter.ALL);
    }

    public ListGamesQuery {
        Objects.requireNonNull(filter, "filter");
        if (page < 0) {
            throw new IllegalArgumentException("Pages start at 0: " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE + ": " + size);
        }
    }
}
