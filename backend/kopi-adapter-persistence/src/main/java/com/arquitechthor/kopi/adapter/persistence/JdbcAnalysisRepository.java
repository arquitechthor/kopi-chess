package com.arquitechthor.kopi.adapter.persistence;

import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.MoveClassifier.Classification;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.game.GameId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/// [AnalysisRepository] on PostgreSQL: one `analysis` row per game and one
/// `move_analysis` row per move (`V3__analysis.sql`).
///
/// Saving deletes the game's earlier analysis and inserts the new one in a
/// single transaction, so a reader sees the old analysis or the new one,
/// never half of each.
public final class JdbcAnalysisRepository implements AnalysisRepository {

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;

    public JdbcAnalysisRepository(JdbcClient jdbc, TransactionOperations transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /// `limit_kind` and `limit_value`.
    private record LimitColumns(String kind, long value) {}

    /// The `analysis` row, before its moves are attached.
    private record AnalysisRow(GameId gameId, String engine, AnalysisSettings settings, Color firstMover,
                               Optional<Opening> opening, Instant startedAt, Instant completedAt) {}

    @Override
    public Optional<GameAnalysis> findByGameId(GameId id) {
        return jdbc.sql("""
                        select game_id, engine, limit_kind, limit_value, multi_pv, missed_mate_limit, first_mover,
                               opening_eco, opening_name, started_at, completed_at
                          from analysis where game_id = :id""")
                .param("id", id.value())
                .query(JdbcAnalysisRepository::analysisRow)
                .optional()
                .map(row -> new GameAnalysis(row.gameId(), row.engine(), row.settings(), row.firstMover(),
                        row.opening(), moves(id), row.startedAt(), row.completedAt()));
    }

    @Override
    public void save(GameAnalysis analysis) {
        var settings = analysis.settings();
        var limit = switch (settings.limits()) {
            case SearchLimits.Depth(var plies) -> new LimitColumns("DEPTH", plies);
            case SearchLimits.Nodes(var count) -> new LimitColumns("NODES", count);
        };
        transactions.executeWithoutResult(_ -> {
            jdbc.sql("delete from analysis where game_id = :id").param("id", analysis.gameId().value()).update();
            jdbc.sql("""
                    insert into analysis (game_id, engine, limit_kind, limit_value, multi_pv, missed_mate_limit,
                                          first_mover, opening_eco, opening_name, started_at, completed_at)
                    values (:gameId, :engine, :limitKind, :limitValue, :multiPv, :missedMateLimit,
                            :firstMover, :openingEco, :openingName, :startedAt, :completedAt)""")
                    .param("gameId", analysis.gameId().value())
                    .param("engine", analysis.engine())
                    .param("limitKind", limit.kind())
                    .param("limitValue", limit.value())
                    .param("multiPv", settings.multiPv())
                    .param("missedMateLimit", settings.missedMateLimit())
                    .param("firstMover", analysis.firstMover().name())
                    .param("openingEco", analysis.opening().map(Opening::eco).orElse(null))
                    .param("openingName", analysis.opening().map(Opening::name).orElse(null))
                    .param("startedAt", timestamp(analysis.startedAt()))
                    .param("completedAt", timestamp(analysis.completedAt()))
                    .update();
            for (var move : analysis.moves()) {
                var classification = move.classification();
                jdbc.sql("""
                        insert into move_analysis (game_id, ply, eval_before, eval_after, best_move_uci,
                                                   best_line_san, classification, win_chance_loss, missed_mate_in)
                        values (:gameId, :ply, :before, :after, :bestMove, :bestLine, :category, :loss,
                                :missedMate)""")
                        .param("gameId", analysis.gameId().value())
                        .param("ply", move.ply())
                        .param("before", EvaluationText.format(move.before()))
                        .param("after", EvaluationText.format(move.after()))
                        .param("bestMove", move.bestMove())
                        .param("bestLine", String.join(" ", move.bestLine()))
                        .param("category", classification.category().name())
                        .param("loss", classification.loss())
                        .param("missedMate", classification.missedMate().isPresent()
                                ? classification.missedMate().getAsInt() : null)
                        .update();
            }
        });
    }

    private List<MoveAnalysis> moves(GameId id) {
        return jdbc.sql("""
                        select ply, eval_before, eval_after, best_move_uci, best_line_san, classification,
                               win_chance_loss, missed_mate_in
                          from move_analysis where game_id = :id order by ply""")
                .param("id", id.value())
                .query((rs, _) -> {
                    var missedMate = rs.getObject("missed_mate_in", Integer.class);
                    return new MoveAnalysis(
                            rs.getInt("ply"),
                            EvaluationText.parse(rs.getString("eval_before")),
                            EvaluationText.parse(rs.getString("eval_after")),
                            rs.getString("best_move_uci"),
                            List.of(rs.getString("best_line_san").split(" ")),
                            new Classification(MoveCategory.valueOf(rs.getString("classification")),
                                    rs.getDouble("win_chance_loss"),
                                    missedMate == null ? OptionalInt.empty() : OptionalInt.of(missedMate)));
                })
                .list();
    }

    private static AnalysisRow analysisRow(ResultSet rs, int rowNumber) throws SQLException {
        var limitValue = rs.getLong("limit_value");
        SearchLimits limits = switch (rs.getString("limit_kind")) {
            case "DEPTH" -> new SearchLimits.Depth(Math.toIntExact(limitValue));
            case "NODES" -> new SearchLimits.Nodes(limitValue);
            default -> throw new IllegalStateException("Unknown limit kind: " + rs.getString("limit_kind"));
        };
        var eco = rs.getString("opening_eco");
        return new AnalysisRow(
                new GameId(rs.getObject("game_id", UUID.class)),
                rs.getString("engine"),
                new AnalysisSettings(limits, rs.getInt("multi_pv"), rs.getInt("missed_mate_limit")),
                Color.valueOf(rs.getString("first_mover")),
                eco == null ? Optional.empty() : Optional.of(new Opening(eco, rs.getString("opening_name"))),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                rs.getObject("completed_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
