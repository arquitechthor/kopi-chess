package com.arquitechthor.kopi.adapter.remote;

import com.arquitechthor.kopi.adapter.remote.RemoteModel.BotMessage;
import com.arquitechthor.kopi.adapter.remote.RemoteModel.GameMessage;
import com.arquitechthor.kopi.adapter.remote.RemoteModel.RecordMessage;
import com.arquitechthor.kopi.application.game.CreateGameCommand;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.InvalidUciException;
import com.arquitechthor.kopi.application.game.ListGamesQuery;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignCommand;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.game.VersionMismatchException;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.application.port.GameFilter.ResultFilter;
import com.arquitechthor.kopi.application.port.RemoteRequestHandler;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameNotInProgressException;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.IllegalMoveException;
import com.arquitechthor.kopi.domain.game.NotYourTurnException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.context.MessageSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// The remote protocol, version 1 (`docs/protocolo-remoto.md`): one request
/// payload in, one response payload out.
///
/// - **Allow list** (ADR-022): only `hello`, `bots`, `games`, `new`, `game`,
///   `move` and `resign`, and only on games against a bot. Anything else
///   is `NOT_ALLOWED`: from the phone nothing is deleted, no settings
///   change and the history is not shown.
/// - **Language:** the one each device sent in `hello`, English until then.
/// - Errors have a stable `error` code for the app and a `detail` in
///   English for debugging (§4).
final class RemoteProtocol implements RemoteRequestHandler {

    private static final System.Logger log = System.getLogger(RemoteProtocol.class.getName());

    /// The version of the protocol: a request with another `v` gets
    /// `VERSION_MISMATCH`.
    static final int VERSION = 1;

    /// The most games `games` lists, newest first, so the response stays
    /// well under the relay's 32,000 characters.
    static final int MAX_GAMES = 30;

    /// The most devices whose language is remembered; past that the memory
    /// starts again (a device without one gets English until its next
    /// `hello`).
    private static final int MAX_DEVICES = 100;

    private static final List<Locale> LANGUAGES = List.of(Locale.ENGLISH, Locale.of("es"));

    /// The use cases the allow list reaches, and no others.
    record UseCases(
            GetBotRecordsUseCase botRecords,
            ListGamesUseCase listGames,
            GetGameUseCase getGame,
            CreateGameUseCase createGame,
            PlayMoveUseCase playMove,
            ResignUseCase resign) {

        UseCases {
            Objects.requireNonNull(botRecords, "botRecords");
            Objects.requireNonNull(listGames, "listGames");
            Objects.requireNonNull(getGame, "getGame");
            Objects.requireNonNull(createGame, "createGame");
            Objects.requireNonNull(playMove, "playMove");
            Objects.requireNonNull(resign, "resign");
        }
    }

    private final JsonMapper json;
    private final MessageSource messages;
    private final UseCases useCases;
    private final String kopiChessVersion;
    private final Supplier<String> computerName;
    private final Map<String, Locale> languages = new ConcurrentHashMap<>();

    /// @param kopiChessVersion this Kopi Chess's version, for `hello`
    /// @param computerName     the name the app shows for this computer
    RemoteProtocol(JsonMapper json, MessageSource messages, UseCases useCases, String kopiChessVersion,
            Supplier<String> computerName) {
        this.json = Objects.requireNonNull(json, "json");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.kopiChessVersion = Objects.requireNonNull(kopiChessVersion, "kopiChessVersion");
        this.computerName = Objects.requireNonNull(computerName, "computerName");
    }

    @Override
    public String handle(String device, String payload) {
        ObjectNode response;
        try {
            response = answer(device, request(payload));
        } catch (RemoteException e) {
            response = error(e.code, e.getMessage());
        } catch (GameNotFoundException e) {
            response = error(ErrorCode.NOT_FOUND, "no game " + e.gameId());
        } catch (VersionMismatchException | ConcurrentGameUpdateException e) {
            response = error(ErrorCode.CONFLICT, e.getMessage());
        } catch (NotYourTurnException e) {
            response = error(ErrorCode.CONFLICT, "it is the bot's turn");
        } catch (IllegalMoveException e) {
            response = error(ErrorCode.ILLEGAL_MOVE, e.getMessage());
        } catch (GameNotInProgressException e) {
            response = error(ErrorCode.GAME_OVER, e.getMessage());
        } catch (BotUnavailableException e) {
            response = error(ErrorCode.BOT_UNAVAILABLE, e.getMessage());
        } catch (InvalidUciException e) {
            response = error(ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.WARNING, "A remote request failed", e);
            response = error(ErrorCode.INTERNAL, "internal error");
        }
        return json.writeValueAsString(response);
    }

    /// The request as a JSON object, checked for its version.
    private JsonNode request(String payload) {
        JsonNode request;
        try {
            request = json.readTree(payload == null ? "" : payload);
        } catch (JacksonException _) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "the payload is not JSON");
        }
        if (request == null || !request.isObject()) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "the payload is not a JSON object");
        }
        var version = request.path("v");
        if (!version.isIntegralNumber() || version.longValue() != VERSION) {
            throw new RemoteException(ErrorCode.VERSION_MISMATCH,
                    "this computer speaks version " + VERSION + " of the remote protocol");
        }
        return request;
    }

    private ObjectNode answer(String device, JsonNode request) {
        var op = request.path("op");
        if (!op.isString()) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "op is missing");
        }
        return switch (op.stringValue()) {
            case "hello" -> hello(device, request);
            case "bots" -> bots(device);
            case "games" -> games();
            case "new" -> newGame(request);
            case "game" -> ok().set("game", tree(GameMessage.of(botGame(request))));
            case "move" -> move(request);
            case "resign" -> resign(request);
            default -> throw new RemoteException(ErrorCode.NOT_ALLOWED, "op not allowed: " + op.stringValue());
        };
    }

    private ObjectNode hello(String device, JsonNode request) {
        var lang = request.path("lang");
        if (lang.isString() && device != null) {
            if (languages.size() >= MAX_DEVICES) {
                languages.clear();
            }
            languages.put(device, locale(lang.stringValue()));
        }
        return ok().put("kopiChessVersion", kopiChessVersion)
                .put("remoteVersion", VERSION)
                .put("name", computerName.get());
    }

    private ObjectNode bots(String device) {
        var locale = device == null ? Locale.ENGLISH : languages.getOrDefault(device, Locale.ENGLISH);
        var records = useCases.botRecords().records();
        var bots = Arrays.stream(BotProfile.values())
                .map(profile -> new BotMessage(profile, profile.level(),
                        messages.getMessage("remote.bot." + profile + ".description", null, locale),
                        RecordMessage.of(records.get(profile))))
                .toList();
        return ok().set("bots", tree(bots));
    }

    /// The games in progress against a bot, newest first, at most
    /// [#MAX_GAMES].
    private ObjectNode games() {
        var filter = new GameFilter(Optional.of(GameOrigin.PLAYED), Optional.of(ResultFilter.IN_PROGRESS),
                Optional.empty());
        var games = new ArrayList<GameMessage>();
        for (int page = 0; games.size() < MAX_GAMES; page++) {
            var found = useCases.listGames().list(new ListGamesQuery(page, ListGamesQuery.MAX_SIZE, filter));
            found.games().stream()
                    .filter(summary -> summary.mode() == GameMode.AGAINST_BOT)
                    .limit(MAX_GAMES - games.size())
                    .forEach(summary -> games.add(GameMessage.withoutMoves(useCases.getGame().get(summary.id()))));
            if (page + 1 >= found.totalPages()) {
                break;
            }
        }
        return ok().set("games", tree(games));
    }

    private ObjectNode newGame(JsonNode request) {
        var bot = parse(BotProfile.class, text(request, "botProfile"), "botProfile");
        var color = switch (text(request, "humanColor")) {
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "RANDOM" -> ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.BLACK;
            default -> throw new RemoteException(ErrorCode.BAD_REQUEST, "humanColor must be WHITE, BLACK or RANDOM");
        };
        var game = useCases.createGame().create(new CreateGameCommand(color, bot));
        return ok().set("game", tree(GameMessage.of(game)));
    }

    private ObjectNode move(JsonNode request) {
        var game = botGame(request);
        var uci = text(request, "uci");
        var played = useCases.playMove().play(new PlayMoveCommand(game.id(), uci, version(request)));
        return ok().set("game", tree(GameMessage.of(played)));
    }

    private ObjectNode resign(JsonNode request) {
        var game = botGame(request);
        var resigned = useCases.resign().resign(new ResignCommand(game.id(), version(request)));
        return ok().set("game", tree(GameMessage.of(resigned)));
    }

    /// The game in `gameId`, if it is one against a bot: the only games the
    /// phone may see or play.
    private GameView botGame(JsonNode request) {
        GameId id;
        try {
            id = GameId.parse(text(request, "gameId"));
        } catch (IllegalArgumentException _) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "gameId is not a game id");
        }
        var game = useCases.getGame().get(id);
        if (game.mode() != GameMode.AGAINST_BOT) {
            throw new RemoteException(ErrorCode.NOT_ALLOWED, "only games against a bot");
        }
        return game;
    }

    private static String text(JsonNode request, String field) {
        var value = request.path(field);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, field + " is missing");
        }
        return value.stringValue();
    }

    private static long version(JsonNode request) {
        var value = request.path("version");
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "version is missing");
        }
        return value.longValue();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String text, String field) {
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException _) {
            throw new RemoteException(ErrorCode.BAD_REQUEST, "unknown " + field + ": " + text);
        }
    }

    /// `en` or `es`, English for anything else.
    private static Locale locale(String lang) {
        try {
            var match = Locale.lookup(Locale.LanguageRange.parse(lang), LANGUAGES);
            return match == null ? Locale.ENGLISH : match;
        } catch (IllegalArgumentException _) {
            return Locale.ENGLISH;
        }
    }

    private JsonNode tree(Object value) {
        return json.valueToTree(value);
    }

    private ObjectNode ok() {
        return json.createObjectNode().put("v", VERSION).put("ok", true);
    }

    private ObjectNode error(ErrorCode code, String detail) {
        return json.createObjectNode().put("v", VERSION).put("ok", false).put("error", code.name())
                .put("detail", detail);
    }

    /// The stable codes of `error` (§4). `SERVER_MODE_OFF` is the relay
    /// side's: it answers that one when server mode is off.
    enum ErrorCode {
        NOT_ALLOWED,
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        ILLEGAL_MOVE,
        GAME_OVER,
        BOT_UNAVAILABLE,
        VERSION_MISMATCH,
        SERVER_MODE_OFF,
        INTERNAL
    }

    /// A request this protocol turns down before reaching a use case.
    private static final class RemoteException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final ErrorCode code;

        RemoteException(ErrorCode code, String detail) {
            super(detail);
            this.code = code;
        }
    }
}
