package com.arquitechthor.kopi.adapter.remote;

import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
            CreateGameUseCase createGame, PlayMoveUseCase playMove, ResignUseCase resign) {
        var version = build.getIfAvailable() == null ? "dev" : build.getIfAvailable().getVersion();
        return new RemoteProtocol(JsonMapper.builder().build(), messages,
                new RemoteProtocol.UseCases(botRecords, listGames, getGame, createGame, playMove, resign), version,
                RemoteAdapterConfiguration::computerName);
    }

    /// This computer's name, until the person can choose one on the account
    /// page.
    static String computerName() {
        var name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            name = System.getenv("HOSTNAME");
        }
        if (name == null || name.isBlank()) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException _) {
                name = "Kopi Chess";
            }
        }
        return name.length() > 40 ? name.substring(0, 40) : name;
    }
}
