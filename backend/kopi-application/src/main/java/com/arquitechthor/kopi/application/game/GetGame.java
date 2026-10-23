package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Objects;

/// [GetGameUseCase].
public final class GetGame implements GetGameUseCase {

    private final GameRepository repository;

    public GetGame(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public GameView get(GameId id) {
        return GameView.of(Games.load(repository, id));
    }
}
