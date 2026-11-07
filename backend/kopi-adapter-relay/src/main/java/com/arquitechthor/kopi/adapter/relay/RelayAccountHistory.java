package com.arquitechthor.kopi.adapter.relay;

import com.arquitechthor.kopi.application.history.GameRecord;
import com.arquitechthor.kopi.application.history.GameRecord.RecordedMove;
import com.arquitechthor.kopi.application.port.AccountHistory;
import com.arquitechthor.kopi.domain.game.GameId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/// [AccountHistory] over the backend Kopi's `/chess/history` routes
/// (`docs/protocolo-rele.md` §9).
///
/// A game travels in `data` as this Kopi Chess's own JSON, with `format: 1`:
/// a [GameRecord], moves as `{ "uci", "at" }`. A `data` with another format
/// (from a newer Kopi Chess) or that cannot be read comes back as an entry
/// without a game, and the sync leaves it alone.
final class RelayAccountHistory implements AccountHistory {

    static final int FORMAT = 1;
    private static final String CONFLICT = "REV_CONFLICT";

    private final KopiApi api;

    RelayAccountHistory(KopiApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public Changes changes(String since) {
        var path = "/chess/history?limit=200"
                + (since.isEmpty() ? "" : "&since=" + URLEncoder.encode(since, StandardCharsets.UTF_8));
        var node = api.call("GET", path, null);
        var entries = new ArrayList<Entry>();
        for (var entry : node.path("entries")) {
            entries.add(entry(entry));
        }
        return new Changes(entries, CognitoTokens.text(node, "cursor"), node.path("more").asBoolean(false),
                node.path("tombstoneDays").asInt(180));
    }

    @Override
    public PutResult put(GameId gameId, long rev, GameRecord game) {
        var body = new LinkedHashMap<String, Object>();
        body.put("rev", rev);
        body.put("data", data(game));
        var answer = api.callAllowingConflict("PUT", "/chess/history/" + gameId.value(), body, CONFLICT);
        if (answer.status() == 409) {
            return new PutResult.Conflict(current(answer.body()));
        }
        return new PutResult.Stored(answer.body().path("rev").asLong());
    }

    @Override
    public DeleteResult delete(GameId gameId, long rev) {
        var answer = api.callAllowingConflict("DELETE", "/chess/history/" + gameId.value() + "?rev=" + rev, null,
                CONFLICT);
        if (answer.status() == 409) {
            return current(answer.body()).<DeleteResult>map(DeleteResult.Conflict::new)
                    .orElse(new DeleteResult.Done());
        }
        return new DeleteResult.Done();
    }

    @Override
    public void deleteAll() {
        api.call("DELETE", "/chess/history", null);
    }

    private static Optional<Entry> current(JsonNode conflict) {
        var current = conflict.get("current");
        return current == null || current.isNull() ? Optional.empty() : Optional.of(entry(current));
    }

    static Entry entry(JsonNode node) {
        var id = new GameId(UUID.fromString(CognitoTokens.text(node, "gameId")));
        var deleted = node.path("deleted").asBoolean(false);
        var data = node.get("data");
        Optional<GameRecord> game = Optional.empty();
        if (!deleted && data != null && data.isObject() && data.path("format").asInt() == FORMAT) {
            try {
                game = Optional.of(record(data));
            } catch (RuntimeException e) {
                game = Optional.empty();
            }
        }
        return new Entry(id, node.path("rev").asLong(), deleted, game);
    }

    /// The JSON of a game in `data`.
    static Map<String, Object> data(GameRecord game) {
        var data = new LinkedHashMap<String, Object>();
        data.put("format", FORMAT);
        data.put("id", game.id());
        data.put("origin", game.origin());
        data.put("white", game.white());
        data.put("black", game.black());
        data.put("initialFen", game.initialFen());
        data.put("moves", game.moves().stream()
                .map(m -> Map.of("uci", m.uci(), "at", m.playedAt().toString())).toList());
        data.put("termination", game.termination());
        data.put("result", game.result());
        data.put("createdAt", game.createdAt().toString());
        data.put("finishedAt", game.finishedAt() == null ? null : game.finishedAt().toString());
        data.put("pgnTags", game.pgnTags());
        return data;
    }

    static GameRecord record(JsonNode data) {
        var moves = new ArrayList<RecordedMove>();
        for (var move : data.path("moves")) {
            moves.add(new RecordedMove(CognitoTokens.text(move, "uci"), Instant.parse(CognitoTokens.text(move, "at"))));
        }
        var tags = new LinkedHashMap<String, String>();
        var pgnTags = data.get("pgnTags");
        if (pgnTags != null && pgnTags.isObject()) {
            for (var name : pgnTags.propertyNames()) {
                tags.put(name, pgnTags.get(name).asString());
            }
        }
        var finished = CognitoTokens.text(data, "finishedAt");
        return new GameRecord(CognitoTokens.text(data, "id"), CognitoTokens.text(data, "origin"),
                CognitoTokens.text(data, "white"), CognitoTokens.text(data, "black"),
                CognitoTokens.text(data, "initialFen"), List.copyOf(moves), CognitoTokens.text(data, "termination"),
                CognitoTokens.text(data, "result"), Instant.parse(CognitoTokens.text(data, "createdAt")),
                finished == null ? null : Instant.parse(finished), tags);
    }
}
