package com.arquitechthor.kopi.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/// What the record against a bot counts (plan §9, as agreed on
/// 2026-10-26): the person's side, resigning is losing, unfinished games
/// apart, imported games not at all.
class BotRecordTest {

    private static final Instant NOW = Instant.parse("2026-10-26T17:00:00Z");

    private static Game game(BotProfile bot, Color human) {
        return Game.start(new GameId(UUID.randomUUID()), human, bot, Position.initial(), NOW);
    }

    /// The fool's mate: Black mates on move 2.
    private static Game foolsMate(BotProfile bot, Color human) {
        return game(bot, human).play("f2f3", Color.WHITE, NOW).play("e7e5", Color.BLACK, NOW)
                .play("g2g4", Color.WHITE, NOW).play("d8h4", Color.BLACK, NOW);
    }

    @Test
    void countsFromThePersonsSide() {
        var records = BotRecord.tally(Stream.of(
                foolsMate(BotProfile.PAWN, Color.BLACK),          // Black mated: the person won
                foolsMate(BotProfile.PAWN, Color.WHITE),          // the person was mated: lost
                game(BotProfile.PAWN, Color.WHITE).resign(Color.WHITE, NOW),  // resigning is losing
                game(BotProfile.PAWN, Color.BLACK),                // still going
                game(BotProfile.KNIGHT, Color.WHITE).resign(Color.BLACK, NOW))); // the bot resigned? won

        assertThat(records.get(BotProfile.PAWN)).isEqualTo(new BotRecord(1, 2, 0, 1));
        assertThat(records.get(BotProfile.PAWN).played()).isEqualTo(4);
        assertThat(records.get(BotProfile.KNIGHT)).isEqualTo(new BotRecord(1, 0, 0, 0));
    }

    @Test
    void everyBotHasARecordEvenWithoutGames() {
        var records = BotRecord.tally(Stream.empty());

        assertThat(records).hasSize(5).allSatisfy((bot, record) -> assertThat(record).isEqualTo(BotRecord.NONE));
    }

    @Test
    void importedGamesDoNotCount() {
        var imported = Game.imported(new GameId(UUID.randomUUID()), Pgn.parse("1. e4 e5 1/2-1/2"), NOW);

        assertThat(BotRecord.tally(Stream.of(imported)).values()).containsOnly(BotRecord.NONE);
    }
}
