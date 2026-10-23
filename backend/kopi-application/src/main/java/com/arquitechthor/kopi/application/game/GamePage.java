package com.arquitechthor.kopi.application.game;

import java.util.List;

/// A page of the list of games, newest first.
///
/// @param games the games on this page
/// @param page  its number, from 0
/// @param size  how many games a full page holds
/// @param total how many games there are in all
public record GamePage(List<GameSummary> games, int page, int size, long total) {

    public GamePage {
        games = List.copyOf(games);
    }

    /// How many pages there are in all (at least one, even if empty).
    public int totalPages() {
        return (int) Math.max(1, (total + size - 1) / size);
    }
}
