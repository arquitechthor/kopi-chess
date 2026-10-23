package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import java.util.Objects;

/// [ListGamesUseCase].
public final class ListGames implements ListGamesUseCase {

    private final GameRepository repository;

    public ListGames(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public GamePage list(ListGamesQuery query) {
        var games = repository.findRecent(query.page() * query.size(), query.size()).stream()
                .map(GameSummary::of)
                .toList();
        return new GamePage(games, query.page(), query.size(), repository.count());
    }
}
