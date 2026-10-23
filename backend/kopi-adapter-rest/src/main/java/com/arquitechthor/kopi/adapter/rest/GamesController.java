package com.arquitechthor.kopi.adapter.rest;

import static com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration.API;

import com.arquitechthor.kopi.adapter.rest.ApiModel.CreateGameRequest;
import com.arquitechthor.kopi.adapter.rest.ApiModel.GamePageResponse;
import com.arquitechthor.kopi.adapter.rest.ApiModel.GameResponse;
import com.arquitechthor.kopi.adapter.rest.ApiModel.PlayMoveRequest;
import com.arquitechthor.kopi.application.game.CreateGameCommand;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ListGamesQuery;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignCommand;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.domain.game.GameId;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// `/api/v1/games`: create, read, play and resign (design §10).
///
/// Thin on purpose: it turns HTTP into commands, calls a use case and turns
/// the view into JSON. Rules, turns and versions are decided further in;
/// errors become `ProblemDetail`s in [ApiExceptionHandler].
@RestController
@RequestMapping(API + "/games")
class GamesController {

    private final CreateGameUseCase createGame;
    private final GetGameUseCase getGame;
    private final ListGamesUseCase listGames;
    private final PlayMoveUseCase playMove;
    private final ResignUseCase resign;

    GamesController(CreateGameUseCase createGame, GetGameUseCase getGame, ListGamesUseCase listGames,
            PlayMoveUseCase playMove, ResignUseCase resign) {
        this.createGame = createGame;
        this.getGame = getGame;
        this.listGames = listGames;
        this.playMove = playMove;
        this.resign = resign;
    }

    /// `201 Created` with the new game; if the bot has White, its first move
    /// is already in it.
    @PostMapping
    ResponseEntity<GameResponse> create(@RequestBody CreateGameRequest request) {
        if (request == null || request.humanColor() == null || request.botProfile() == null) {
            throw new InvalidRequestException("humanColor and botProfile are required");
        }
        var view = createGame.create(new CreateGameCommand(request.humanColor(), request.botProfile()));
        return ResponseEntity.created(URI.create(API + "/games/" + view.id()))
                .eTag(Versions.etag(view.version()))
                .body(GameResponse.of(view));
    }

    @GetMapping("/{id}")
    ResponseEntity<GameResponse> get(@PathVariable UUID id) {
        return ok(getGame.get(new GameId(id)));
    }

    @GetMapping
    GamePageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return GamePageResponse.of(listGames.list(new ListGamesQuery(page, size)));
    }

    /// The person's move and the bot's reply, in one request (design §10.1).
    @PostMapping("/{id}/moves")
    ResponseEntity<GameResponse> play(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody PlayMoveRequest request) {
        var version = Versions.fromIfMatch(ifMatch);
        if (request == null || request.uci() == null) {
            throw new InvalidRequestException("uci is required");
        }
        return ok(playMove.play(new PlayMoveCommand(new GameId(id), request.uci(), version)));
    }

    @PostMapping("/{id}/resign")
    ResponseEntity<GameResponse> resign(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        var version = Versions.fromIfMatch(ifMatch);
        return ok(resign.resign(new ResignCommand(new GameId(id), version)));
    }

    private static ResponseEntity<GameResponse> ok(GameView view) {
        return ResponseEntity.ok().eTag(Versions.etag(view.version())).body(GameResponse.of(view));
    }
}
