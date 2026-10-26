package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Map;
import java.util.Objects;

/// [GetBotRecordsUseCase]: one query to the repository, which counts
/// without loading the games.
public final class GetBotRecords implements GetBotRecordsUseCase {

    private final GameRepository repository;

    public GetBotRecords(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Map<BotProfile, BotRecord> records() {
        return repository.recordsAgainstBots();
    }
}
