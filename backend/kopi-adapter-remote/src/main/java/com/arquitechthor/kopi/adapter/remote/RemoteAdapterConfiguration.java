package com.arquitechthor.kopi.adapter.remote;

import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import com.arquitechthor.kopi.application.servermode.ServerModeUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/// Spring configuration of the remote adapter, imported explicitly by
/// `kopi-bootstrap`. It only answers requests: whether they arrive at all
/// (server mode, the connection to the relay) is decided elsewhere.
@Configuration(proxyBeanMethods = false)
public class RemoteAdapterConfiguration {

    @Bean
    RemoteRequestHandler remoteRequestHandler(MessageSource messages, ObjectProvider<BuildProperties> build,
            GetBotRecordsUseCase botRecords, ListGamesUseCase listGames, GetGameUseCase getGame,
            CreateGameUseCase createGame, PlayMoveUseCase playMove, ResignUseCase resign,
            ServerModeUseCase serverMode) {
        var version = build.getIfAvailable() == null ? "dev" : build.getIfAvailable().getVersion();
        return new RemoteProtocol(JsonMapper.builder().build(), messages,
                new RemoteProtocol.UseCases(botRecords, listGames, getGame, createGame, playMove, resign), version,
                () -> serverMode.view().name());
    }
}
