package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.port.PvpRelay;
import com.arquitechthor.kopi.domain.chess.Color;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/// [PvpRelay] over the backend Kopi's HTTP API (`docs/protocolo-rele.md`,
/// §4 and §5).
final class RelayPvp implements PvpRelay {

    private final KopiApi api;

    RelayPvp(KopiApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public RelayGame invite(String opponentKopiId, Color color, Optional<String> initialFen) {
        var body = new LinkedHashMap<String, Object>();
        body.put("opponent", opponentKopiId);
        body.put("color", color == null ? "random" : name(color));
        body.put("initialFen", initialFen.orElse(null));
        return game(api.call("POST", "/chess/games", body));
    }

    @Override
    public List<RelayGame> myGames() {
        var games = new ArrayList<RelayGame>();
        for (var node : api.call("GET", "/chess/games", null).path("games")) {
            games.add(game(node));
        }
        return games;
    }

    @Override
    public RelayGame game(String relayGameId, int fromPly) {
        return game(api.call("GET", path(relayGameId) + "?fromPly=" + Math.max(1, fromPly), null));
    }

    @Override
    public RelayGame accept(String relayGameId) {
        return action(relayGameId, "accept", null);
    }

    @Override
    public RelayGame decline(String relayGameId) {
        return action(relayGameId, "decline", null);
    }

    @Override
    public void cancel(String relayGameId) {
        api.call("POST", path(relayGameId) + "/cancel", null);
    }

    @Override
    public RelayGame move(String relayGameId, int ply, String uci, String zobrist, Optional<Ending> ending) {
        var body = new LinkedHashMap<String, Object>();
        body.put("ply", ply);
        body.put("uci", uci);
        body.put("zobrist", zobrist);
        body.put("end", ending.map(end -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("result", end.result());
            map.put("termination", end.termination());
            return map;
        }).orElse(null));
        return action(relayGameId, "move", body);
    }

    @Override
    public RelayGame resign(String relayGameId) {
        return action(relayGameId, "resign", null);
    }

    @Override
    public RelayGame offerDraw(String relayGameId) {
        return action(relayGameId, "offer-draw", null);
    }

    @Override
    public RelayGame acceptDraw(String relayGameId) {
        return action(relayGameId, "accept-draw", null);
    }

    @Override
    public RelayGame declineDraw(String relayGameId) {
        return action(relayGameId, "decline-draw", null);
    }

    @Override
    public void acknowledge(String relayGameId) {
        try {
            api.call("POST", path(relayGameId) + "/ack", null);
        } catch (com.arquitechthor.kopi.application.port.KopiOnlineException e) {
            if (e.problem() != com.arquitechthor.kopi.application.port.KopiOnlineException.Problem.GAME_NOT_FOUND) {
                throw e;
            }
            // Already gone: done (protocol, §5.6).
        }
    }

    private RelayGame action(String relayGameId, String action, Object body) {
        return game(api.call("POST", path(relayGameId) + "/" + action, body));
    }

    private static String path(String relayGameId) {
        return "/chess/games/" + URLEncoder.encode(relayGameId, StandardCharsets.UTF_8);
    }

    /// A game of the protocol (§4).
    static RelayGame game(JsonNode node) {
        var moves = new ArrayList<RelayMove>();
        for (var move : node.path("moves")) {
            moves.add(new RelayMove(move.path("ply").asInt(), text(move, "uci"), text(move, "zobrist"),
                    instant(move, "at").orElse(Instant.EPOCH)));
        }
        return new RelayGame(
                text(node, "gameId"),
                Status.valueOf(text(node, "status")),
                node.path("white").path("kopiId").asString(),
                node.path("black").path("kopiId").asString(),
                color(node, "myColor").orElseThrow(),
                color(node, "invitedBy").orElse(Color.WHITE),
                Optional.ofNullable(text(node, "initialFen")),
                color(node, "firstMover").orElse(Color.WHITE),
                node.path("lastPly").asInt(),
                instant(node, "lastMoveAt"),
                color(node, "drawOfferBy"),
                Optional.ofNullable(text(node, "result")),
                Optional.ofNullable(text(node, "termination")),
                color(node, "finishedBy"),
                instant(node, "createdAt").orElse(Instant.EPOCH),
                instant(node, "expiresAt").orElse(Instant.EPOCH),
                moves);
    }

    private static String text(JsonNode node, String field) {
        return CognitoTokens.text(node, field);
    }

    private static Optional<Color> color(JsonNode node, String field) {
        return Optional.ofNullable(text(node, field)).map(value -> Color.valueOf(value.toUpperCase(Locale.ROOT)));
    }

    private static Optional<Instant> instant(JsonNode node, String field) {
        return Optional.ofNullable(text(node, field)).map(Instant::parse);
    }

    private static String name(Color color) {
        return color.name().toLowerCase(Locale.ROOT);
    }
}
