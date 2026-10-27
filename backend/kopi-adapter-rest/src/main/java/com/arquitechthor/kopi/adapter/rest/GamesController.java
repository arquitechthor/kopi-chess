package com.arquitechthor.kopi.adapter.rest;

import static com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration.API;

import com.arquitechthor.kopi.adapter.rest.ApiModel.CreateGameRequest;
import com.arquitechthor.kopi.adapter.rest.ApiModel.GamePageResponse;
import com.arquitechthor.kopi.adapter.rest.ApiModel.GameResponse;
import com.arquitechthor.kopi.adapter.rest.ApiModel.ImportPgnRequest;
import com.arquitechthor.kopi.adapter.rest.ApiModel.PlayMoveRequest;
import com.arquitechthor.kopi.application.game.CreateGameCommand;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.DeleteGameUseCase;
import com.arquitechthor.kopi.application.game.ExportPgnUseCase;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.ImportPgnUseCase;
import com.arquitechthor.kopi.application.game.ListGamesQuery;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignCommand;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// `/api/v1/games`: create, read, play, resign, and import and export PGN
/// (design §10).
///
/// Thin on purpose: it turns HTTP into commands, calls a use case and turns
/// the view into JSON. Rules, turns and versions are decided further in;
/// errors become `ProblemDetail`s in [ApiExceptionHandler].
@RestController
@RequestMapping(API + "/games")
class GamesController {

    /// The registered media type of PGN files.
    static final String PGN_MEDIA_TYPE = "application/x-chess-pgn";

    private final CreateGameUseCase createGame;
    private final GetGameUseCase getGame;
    private final ListGamesUseCase listGames;
    private final PlayMoveUseCase playMove;
    private final ResignUseCase resign;
    private final DeleteGameUseCase deleteGame;
    private final ExportPgnUseCase exportPgn;
    private final ImportPgnUseCase importPgn;

    GamesController(CreateGameUseCase createGame, GetGameUseCase getGame, ListGamesUseCase listGames,
            PlayMoveUseCase playMove, ResignUseCase resign, DeleteGameUseCase deleteGame,
            ExportPgnUseCase exportPgn, ImportPgnUseCase importPgn) {
        this.createGame = createGame;
        this.getGame = getGame;
        this.listGames = listGames;
        this.playMove = playMove;
        this.resign = resign;
        this.deleteGame = deleteGame;
        this.exportPgn = exportPgn;
        this.importPgn = importPgn;
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

    /// A page of games, newest first, optionally only those of an `origin`
    /// (`PLAYED`, `IMPORTED`) or a `result` (`WHITE_WIN`, `BLACK_WIN`,
    /// `DRAW`, `IN_PROGRESS`).
    @GetMapping
    GamePageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) GameOrigin origin,
            @RequestParam(required = false) GameFilter.ResultFilter result) {
        var filter = new GameFilter(Optional.ofNullable(origin), Optional.ofNullable(result));
        return GamePageResponse.of(listGames.list(new ListGamesQuery(page, size, filter)));
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

    /// `204 No Content`: the game is gone, with its moves and its analysis.
    /// No `If-Match`: a game is deleted whatever its version.
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteGame.delete(new GameId(id));
        return ResponseEntity.noContent().build();
    }

    /// Imports a finished game from PGN (plan §9): `201 Created` with the
    /// game, its analysis already queued. The PGN comes as the body, as
    /// plain text (`curl --data-binary @game.pgn`) or the PGN media type.
    @PostMapping(path = "/import", consumes = {MediaType.TEXT_PLAIN_VALUE, PGN_MEDIA_TYPE})
    ResponseEntity<GameResponse> importText(@RequestBody(required = false) String pgn) {
        return imported(pgn);
    }

    /// The same, with the PGN in JSON: `{"pgn": "1. e4 e5 … 1-0"}`.
    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<GameResponse> importJson(@RequestBody(required = false) ImportPgnRequest request) {
        return imported(request == null ? null : request.pgn());
    }

    private ResponseEntity<GameResponse> imported(String pgn) {
        if (pgn == null || pgn.isBlank()) {
            throw new InvalidRequestException("the PGN is missing");
        }
        var view = importPgn.importPgn(pgn);
        return ResponseEntity.created(URI.create(API + "/games/" + view.id()))
                .eTag(Versions.etag(view.version()))
                .body(GameResponse.of(view));
    }

    /// The game as a `.pgn` file (plan §9): `application/x-chess-pgn`,
    /// saved as `kopi-chess-<first 8 characters of the id>.pgn`.
    @GetMapping(path = "/{id}/pgn", produces = PGN_MEDIA_TYPE)
    ResponseEntity<String> pgn(@PathVariable UUID id) {
        var pgn = exportPgn.export(new GameId(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(PGN_MEDIA_TYPE + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("kopi-chess-" + id.toString().substring(0, 8) + ".pgn").build().toString())
                .body(pgn);
    }

    private static ResponseEntity<GameResponse> ok(GameView view) {
        return ResponseEntity.ok().eTag(Versions.etag(view.version())).body(GameResponse.of(view));
    }
}
