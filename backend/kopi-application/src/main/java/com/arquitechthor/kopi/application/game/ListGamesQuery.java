package com.arquitechthor.kopi.application.game;

/// Page `page` (from 0) of the list of games, `size` games per page.
public record ListGamesQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public ListGamesQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Pages start at 0: " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE + ": " + size);
        }
    }
}
