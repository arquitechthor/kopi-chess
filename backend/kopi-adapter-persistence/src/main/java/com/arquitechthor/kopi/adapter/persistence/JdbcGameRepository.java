package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.adapter.persistence.GameRows.GameRow;
import com.arquitechthor.kopi.adapter.persistence.GameRows.MoveRow;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/// [GameRepository] on PostgreSQL, with plain SQL through `JdbcClient`.
///
/// Moves are append-only: an update stores the game row and inserts the
/// moves played since the loaded version. Both happen in one transaction,
/// so a game is never stored with half of its moves. If the use case runs
/// inside a transaction already, this one joins it.
///
/// Optimistic locking: `update game … where id = ? and version = ?`. No
/// row updated means someone else saved first.
public final class JdbcGameRepository implements GameRepository {

    private static final String GAME_COLUMNS = """
            id, origin, status, human_color, bot_profile, initial_fen, result, termination,
            version, created_at, finished_at, pgn_tags""";

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;

    public JdbcGameRepository(JdbcClient jdbc, TransactionOperations transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Optional<Game> findById(GameId id) {
        return jdbc.sql("select " + GAME_COLUMNS + " from game where id = :id")
                .param("id", id.value())
                .query(JdbcGameRepository::gameRow)
                .optional()
                .map(row -> GameRows.game(row, moves(List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public void insert(Game game) {
        var row = GameRows.gameRow(game);
        transactions.executeWithoutResult(_ -> {
            jdbc.sql("""
                    insert into game (id, origin, status, human_color, bot_profile, initial_fen, result,
                                      termination, version, created_at, finished_at, pgn_tags)
                    values (:id, :origin, :status, :humanColor, :botProfile, :initialFen, :result,
                            :termination, :version, :createdAt, :finishedAt, :pgnTags)""")
                    .param("id", row.id())
                    .param("origin", row.origin())
                    .param("status", row.status())
                    .param("humanColor", row.humanColor())
                    .param("botProfile", row.botProfile())
                    .param("initialFen", row.initialFen())
                    .param("result", row.result())
                    .param("termination", row.termination())
                    .param("version", row.version())
                    .param("createdAt", timestamp(row.createdAt()))
                    .param("finishedAt", timestamp(row.finishedAt()))
                    .param("pgnTags", row.pgnTags())
                    .update();
            insertMoves(row.id(), GameRows.moveRows(game));
        });
    }

    @Override
    public void update(Game game, long loadedVersion) {
        var row = GameRows.gameRow(game);
        transactions.executeWithoutResult(_ -> {
            var updated = jdbc.sql("""
                    update game
                       set status = :status, result = :result, termination = :termination,
                           version = :version, finished_at = :finishedAt
                     where id = :id and version = :loadedVersion""")
                    .param("status", row.status())
                    .param("result", row.result())
                    .param("termination", row.termination())
                    .param("version", row.version())
                    .param("finishedAt", timestamp(row.finishedAt()))
                    .param("id", row.id())
                    .param("loadedVersion", loadedVersion)
                    .update();
            if (updated == 0) {
                throw new ConcurrentGameUpdateException(game.id(), loadedVersion);
            }
            var stored = jdbc.sql("select coalesce(max(ply), 0) from game_move where game_id = :id")
                    .param("id", row.id())
                    .query(Integer.class)
                    .single();
            insertMoves(row.id(), GameRows.moveRows(game).stream().filter(move -> move.ply() > stored).toList());
        });
    }

    @Override
    public List<Game> findRecent(GameFilter filter, int offset, int limit) {
        var where = Where.of(filter);
        var rows = where.bind(jdbc.sql("select " + GAME_COLUMNS + " from game" + where.sql()
                        + " order by created_at desc, id limit :limit offset :offset"))
                .param("limit", limit)
                .param("offset", offset)
                .query(JdbcGameRepository::gameRow)
                .list();
        // All the moves of the page in one query, not one per game.
        var moves = moves(rows.stream().map(GameRow::id).toList());
        return rows.stream().map(row -> GameRows.game(row, moves.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public long count(GameFilter filter) {
        var where = Where.of(filter);
        return where.bind(jdbc.sql("select count(*) from game" + where.sql())).query(Long.class).single();
    }

    /// A [GameFilter] in SQL: the `where` clause and its parameters. The
    /// same criteria as `GameFilter.matches`, which the in-memory test
    /// repositories use.
    private record Where(String sql, Map<String, Object> params) {

        static Where of(GameFilter filter) {
            var conditions = new ArrayList<String>();
            var params = new LinkedHashMap<String, Object>();
            filter.origin().ifPresent(origin -> {
                conditions.add("origin = :origin");
                params.put("origin", origin.name());
            });
            filter.result().ifPresent(result -> {
                if (result == GameFilter.ResultFilter.IN_PROGRESS) {
                    conditions.add("status = 'IN_PROGRESS'");
                } else {
                    conditions.add("result = :result");
                    params.put("result", result.name());
                }
            });
            return new Where(conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions), params);
        }

        JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement) {
            var bound = statement;
            for (var param : params.entrySet()) {
                bound = bound.param(param.getKey(), param.getValue());
            }
            return bound;
        }
    }

    private void insertMoves(UUID gameId, List<MoveRow> moves) {
        for (var move : moves) {
            jdbc.sql("""
                    insert into game_move (game_id, ply, uci, san, fen_after, played_at)
                    values (:gameId, :ply, :uci, :san, :fenAfter, :playedAt)""")
                    .param("gameId", gameId)
                    .param("ply", move.ply())
                    .param("uci", move.uci())
                    .param("san", move.san())
                    .param("fenAfter", move.fenAfter())
                    .param("playedAt", timestamp(move.playedAt()))
                    .update();
        }
    }

    /// A `game_move` row with the game it belongs to.
    private record GameMove(UUID gameId, MoveRow move) {}

    /// The moves of these games, by game, each list in ply order.
    private Map<UUID, List<MoveRow>> moves(List<UUID> gameIds) {
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("select game_id, ply, uci, san, fen_after, played_at from game_move"
                        + " where game_id in (:ids) order by game_id, ply")
                .param("ids", gameIds)
                .query((rs, _) -> new GameMove(rs.getObject("game_id", UUID.class),
                        new MoveRow(rs.getInt("ply"), rs.getString("uci"), rs.getString("san"),
                                rs.getString("fen_after"), instant(rs, "played_at"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(GameMove::gameId, LinkedHashMap::new,
                        Collectors.mapping(GameMove::move, Collectors.toList())));
    }

    private static GameRow gameRow(ResultSet rs, int rowNumber) throws SQLException {
        return new GameRow(
                rs.getObject("id", UUID.class),
                rs.getString("origin"),
                rs.getString("status"),
                rs.getString("human_color"),
                rs.getString("bot_profile"),
                rs.getString("initial_fen"),
                rs.getString("result"),
                rs.getString("termination"),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "finished_at"),
                rs.getString("pgn_tags"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /// `timestamptz` parameters as `OffsetDateTime`, which the PostgreSQL
    /// driver maps without guessing a time zone.
    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
