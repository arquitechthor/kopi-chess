package com.arquitechthor.kopi.adapter.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PawnBotTest {

    private static PawnBot seeded(long seed) {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        return new PawnBot(() -> random);
    }

    /// How often each move (UCI) is chosen in `draws` tries.
    private static Map<String, Integer> frequencies(PawnBot bot, Position position, int draws) {
        var counts = new HashMap<String, Integer>();
        for (int i = 0; i < draws; i++) {
            counts.merge(bot.choose(position).toUci(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void alwaysPlaysALegalMove() {
        var bot = seeded(1);
        var position = Position.initial();
        // A whole game's worth of moves, both sides, until it ends or 300 plies.
        for (int ply = 0; ply < 300 && !position.legalMoves().isEmpty(); ply++) {
            var move = bot.choose(position);
            assertThat(position.legalMoves()).contains(move);
            position = position.apply(move);
        }
    }

    @Test
    void sometimesPlaysEveryMove() {
        // Random, not fixed: over many tries every legal move turns up.
        var position = Position.initial();

        var counts = frequencies(seeded(2), position, 2_000);

        assertThat(counts.keySet()).containsExactlyInAnyOrderElementsOf(
                position.legalMoves().stream().map(Move::toUci).toList());
    }

    @Test
    void likesCaptures() {
        // Thirteen legal moves (eight knight moves, five king moves), one of
        // them the capture Nxe6. Picked uniformly it would come up about 8%
        // of the time; with its weight of 4 against 1 it comes up about 25%.
        var position = Fen.parse("4k3/8/4r3/8/3N4/8/8/6K1 w - - 0 1");
        assertThat(position.legalMoves()).hasSize(13);

        var share = frequencies(seeded(3), position, 4_000).get("d4e6") / 4_000.0;

        assertThat(share).isBetween(0.18, 0.32);
    }

    @Test
    void likesChecks() {
        // Ra8+ is the only check among the rook and king moves.
        var position = Fen.parse("4k3/8/8/8/8/8/8/R5K1 w - - 0 1");
        var moves = position.legalMoves().size();

        var share = frequencies(seeded(4), position, 4_000).get("a1a8") / 4_000.0;

        assertThat(share).isGreaterThan(2.0 / moves);
    }

    @Test
    void promotesToAQueenMostOfTheTime() {
        var position = Fen.parse("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");

        var counts = frequencies(seeded(5), position, 4_000);

        assertThat(counts.get("b7b8q")).isGreaterThan(counts.getOrDefault("b7b8n", 0) * 3);
    }

    @Test
    void playsTheOnlyMoveThereIs() {
        // In check from the queen, the king has a single way out.
        var position = Fen.parse("k7/8/8/8/8/8/1q6/K7 w - - 0 1");

        assertThat(seeded(6).choose(position).toUci()).isEqualTo("a1b2");
    }

    @Test
    void needsAPositionWithSomeMove() {
        var stalemate = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");

        assertThatIllegalArgumentException().isThrownBy(() -> seeded(7).choose(stalemate));
    }

    @Test
    void theBotPlayerPortPlaysPawnWithIt() {
        var bots = new KopiBots(seeded(8), new KnightBot(KnightBot.DEFAULT_DEPTH, () -> null));

        var move = bots.chooseMove(Position.initial(), BotProfile.PAWN);

        assertThat(Position.initial().legalMoves()).contains(move);
    }

    @ParameterizedTest
    @EnumSource(value = BotProfile.class, names = {"PAWN", "KNIGHT"}, mode = EnumSource.Mode.EXCLUDE)
    void theStockfishProfilesAreNotAvailableYet(BotProfile profile) {
        // BISHOP, ROOK and QUEEN arrive with the Stockfish adapter.
        var bots = new KopiBots(seeded(9), new KnightBot(KnightBot.DEFAULT_DEPTH, () -> null));

        assertThatThrownBy(() -> bots.chooseMove(Position.initial(), profile))
                .isInstanceOfSatisfying(BotUnavailableException.class, e -> assertThat(e.profile()).isEqualTo(profile));
    }
}
