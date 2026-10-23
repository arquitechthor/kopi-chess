package com.arquitechthor.kopi.adapter.bots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.HashSet;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KnightBotTest {

    private static KnightBot seeded(long seed) {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        return new KnightBot(KnightBot.DEFAULT_DEPTH, () -> random);
    }

    private static String choose(String fen) {
        return seeded(1).choose(Fen.parse(fen)).toUci();
    }

    @Nested
    class Evaluating {

        @Test
        void theInitialPositionIsBalanced() {
            assertThat(Evaluation.of(Position.initial())).isZero();
        }

        @Test
        void countsMaterialFromTheSideToMove() {
            // White is a queen up.
            var whiteToMove = Fen.parse("4k3/8/8/8/8/8/8/3QK3 w - - 0 1");
            var blackToMove = Fen.parse("4k3/8/8/8/8/8/8/3QK3 b - - 0 1");

            assertThat(Evaluation.of(whiteToMove)).isGreaterThan(800);
            assertThat(Evaluation.of(blackToMove)).isEqualTo(-Evaluation.of(whiteToMove));
        }

        @Test
        void prefersKnightsInTheCentre() {
            var centre = Fen.parse("4k3/8/8/8/3N4/8/8/4K3 w - - 0 1");
            var corner = Fen.parse("4k3/8/8/8/8/8/8/N3K3 w - - 0 1");

            assertThat(Evaluation.of(centre)).isGreaterThan(Evaluation.of(corner));
        }

        @Test
        void isSymmetricBetweenTheColours() {
            // The same position with colours swapped and the board mirrored.
            var white = Fen.parse("4k3/8/8/8/3N4/8/4P3/4K3 w - - 0 1");
            var black = Fen.parse("4k3/4p3/8/3n4/8/8/8/4K3 b - - 0 1");

            assertThat(Evaluation.of(black)).isEqualTo(Evaluation.of(white));
        }
    }

    @Nested
    class Choice {

        @Test
        void takesAHangingPiece() {
            // The black queen on d5 is undefended; the knight on c3 takes it.
            assertThat(choose("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1")).isEqualTo("c3d5");
        }

        @Test
        void doesNotTakeADefendedPawnWithItsQueen() {
            // Qxd5 loses the queen to exd5; PAWN would happily do it.
            var move = choose("4k3/8/4p3/3p4/8/8/8/3QK3 w - - 0 1");

            assertThat(move).isNotEqualTo("d1d5");
        }

        @Test
        void movesAnAttackedPieceAway() {
            // The white queen on d4 is attacked by the pawn on e5.
            var position = Fen.parse("4k3/8/8/4p3/3Q4/8/8/4K3 w - - 0 1");

            var move = seeded(2).choose(position);

            assertThat(move.from().toString()).isEqualTo("d4");
            // And not onto a square the pawn still covers.
            assertThat(move.to().toString()).isNotIn("d4", "f4");
        }

        @Test
        void findsMateInOne() {
            // Back-rank mate: Ra8#.
            assertThat(choose("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")).isEqualTo("a1a8");
        }

        @Test
        void findsMateInOneForBlackToo() {
            // Fool's mate: Qh4#.
            assertThat(choose("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2"))
                    .isEqualTo("d8h4");
        }

        @Test
        void preventsAMateInOne() {
            // Black threatens Qxg2#, the queen backed by the bishop on h3.
            // Whatever White plays (hxg3 is best), it must not allow it.
            var position = Fen.parse("6k1/8/8/8/8/6qb/6PP/6NK w - - 0 1");

            var move = seeded(3).choose(position);
            var after = position.apply(move);

            assertThat(after.legalMoves().stream().map(reply -> after.apply(reply))
                    .noneMatch(reply -> reply.isInCheck() && reply.legalMoves().isEmpty())).isTrue();
        }

        @Test
        void promotesToAQueen() {
            assertThat(choose("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1")).isEqualTo("b7b8q");
        }

        @Test
        void playsTheOnlyMove() {
            assertThat(choose("k7/8/8/8/8/8/1q6/K7 w - - 0 1")).isEqualTo("a1b2");
        }

        @Test
        void variesBetweenEquallyGoodMoves() {
            // From the initial position several first moves are equal; over
            // a few seeds it should not always play the same one.
            var moves = new HashSet<String>();
            for (long seed = 0; seed < 12; seed++) {
                moves.add(seeded(seed).choose(Position.initial()).toUci());
            }

            assertThat(moves).hasSizeGreaterThan(1);
        }

        @Test
        void alwaysPlaysALegalMove() {
            var bot = seeded(4);
            var position = Position.initial();
            for (int ply = 0; ply < 60 && !position.legalMoves().isEmpty(); ply++) {
                var move = bot.choose(position);
                assertThat(position.legalMoves()).contains(move);
                position = position.apply(move);
            }
        }

        @Test
        void needsAPositionWithSomeMove() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> seeded(5).choose(Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")));
        }

        @Test
        void theDepthIsConfigurable() {
            assertThatIllegalArgumentException().isThrownBy(() -> new KnightBot(0, () -> null));
            // Depth 1 sees the hanging queen too.
            RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(6);
            assertThat(new KnightBot(1, () -> random).choose(Fen.parse("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1"))
                    .toUci()).isEqualTo("c3d5");
        }
    }

    @Test
    void theBotPlayerPortPlaysKnightWithIt() {
        var bots = new KopiBots(new PawnBot(() -> RandomGeneratorFactory.of("L64X128MixRandom").create(7)),
                seeded(8));

        var move = bots.chooseMove(Position.initial(), BotProfile.KNIGHT);

        assertThat(Position.initial().legalMoves()).contains(move);
    }
}
