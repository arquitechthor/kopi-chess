package com.arquitechthor.kopi.domain.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.San;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// A player per colour (ADR-018): who may face whom, what each mode means,
/// and the two endings that only people can reach.
class GamePlayersTest {

    private static final GameId ID = new GameId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant START = Instant.parse("2026-11-07T10:00:00Z");
    private static final Player ANA = new Player.Local("Ana");
    private static final Player LUIS = new Player.Local("Luis");
    private static final Player FRIEND = new Player.Remote("luis_92");
    private static final Player PAWN = new Player.Bot(BotProfile.PAWN);

    private static Game start(Player white, Player black) {
        return Game.start(ID, white, black, Position.initial(), START);
    }

    private static Game play(Game game, String moves) {
        for (var san : moves.split(" ")) {
            var position = game.currentPosition();
            game = game.play(San.parse(position, san), position.sideToMove(),
                    START.plusSeconds(game.moves().size() + 1L));
        }
        return game;
    }

    @Nested
    class Players {

        @Test
        void aNameIsTrimmedAndMayBeEmpty() {
            assertThat(new Player.Local("  Ana  ").name()).isEqualTo("Ana");
            assertThat(new Player.Local(null).named()).isFalse();
            assertThat(Player.Local.YOU.named()).isFalse();
        }

        @Test
        void aNameHasAtMostThirtyCharacters() {
            assertThatThrownBy(() -> new Player.Local("x".repeat(31))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aRemotePlayerHasAKopiId() {
            assertThat(new Player.Remote("Ana_92").kopiId()).isEqualTo("Ana_92");
            assertThatThrownBy(() -> new Player.Remote("an")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Player.Remote("añá")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Modes {

        @Test
        void aPersonAgainstABot() {
            var game = start(PAWN, ANA);

            assertThat(game.mode()).isEqualTo(GameMode.AGAINST_BOT);
            assertThat(game.humanColor()).hasValue(Color.BLACK);
            assertThat(game.botColor()).hasValue(Color.WHITE);
            assertThat(game.botProfile()).hasValue(BotProfile.PAWN);
            assertThat(game.player(Color.BLACK)).hasValue(ANA);
            assertThat(game.isLocalToMove()).isFalse(); // the bot opens
        }

        @Test
        void theOldStartIsTheUnnamedPersonAgainstTheBot() {
            var game = Game.start(ID, Color.WHITE, BotProfile.ROOK, Position.initial(), START);

            assertThat(game.player(Color.WHITE)).hasValue(Player.Local.YOU);
            assertThat(game.player(Color.BLACK)).hasValue(new Player.Bot(BotProfile.ROOK));
        }

        @Test
        void twoPeopleHereHaveNoSinglePersonAndNoBot() {
            var game = start(ANA, LUIS);

            assertThat(game.mode()).isEqualTo(GameMode.LOCAL);
            assertThat(game.humanColor()).isEmpty();
            assertThat(game.botColor()).isEmpty();
            assertThat(game.botProfile()).isEmpty();
        }

        @Test
        void inALocalGameEitherPersonMovesOnTheirTurn() {
            var game = start(ANA, LUIS);
            assertThat(game.isLocalToMove()).isTrue();

            game = play(game, "e4");
            assertThat(game.isLocalToMove()).isTrue();
            assertThat(game.currentPosition().sideToMove()).isEqualTo(Color.BLACK);
        }

        @Test
        void anOnlineGameHasOnePersonHere() {
            var game = start(FRIEND, ANA);

            assertThat(game.mode()).isEqualTo(GameMode.ONLINE);
            assertThat(game.humanColor()).hasValue(Color.BLACK);
            assertThat(game.botProfile()).isEmpty();
            assertThat(game.isLocalToMove()).isFalse(); // the friend opens
        }

        @Test
        void someOpponentsCannotFaceEachOther() {
            assertThatThrownBy(() -> start(PAWN, new Player.Bot(BotProfile.QUEEN)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> start(FRIEND, new Player.Remote("other")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> start(PAWN, FRIEND)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aFinishedGameHasNobodyToMove() {
            var game = play(start(ANA, LUIS), "f3 e5 g4 Qh4#");

            assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(game.isLocalToMove()).isFalse();
        }
    }

    @Nested
    class DrawByAgreement {

        @Test
        void twoPeopleHereCanAgreeToADraw() {
            var game = play(start(ANA, LUIS), "e4 e5");
            var drawn = game.agreeDraw(START.plusSeconds(60));

            assertThat(drawn.outcome()).hasValue(new GameOutcome.DrawByAgreement());
            assertThat(drawn.result()).hasValue(GameResult.DRAW);
            assertThat(drawn.finishedAt()).hasValue(START.plusSeconds(60));
            assertThat(drawn.version()).isEqualTo(game.version() + 1);
        }

        @Test
        void onlinePlayersCanAgreeToADraw() {
            assertThat(start(ANA, FRIEND).agreeDraw(START).result()).hasValue(GameResult.DRAW);
        }

        @Test
        void aBotNeverAgrees() {
            assertThatThrownBy(() -> start(ANA, PAWN).agreeDraw(START)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void notInAFinishedGame() {
            var game = start(ANA, LUIS).resign(Color.WHITE, START);

            assertThatThrownBy(() -> game.agreeDraw(START)).isInstanceOf(GameNotInProgressException.class);
        }
    }

    @Nested
    class Inactivity {

        @Test
        void whoeverMovedLastWins() {
            var game = play(start(ANA, FRIEND), "e4");

            var cold = game.endByInactivity(START.plusSeconds(30L * 24 * 3600));

            assertThat(cold.outcome()).hasValue(new GameOutcome.Inactivity(Color.WHITE));
            assertThat(cold.result()).hasValue(GameResult.WHITE_WIN);
        }

        @Test
        void withNoMovesTheSideToMoveLoses() {
            assertThat(start(ANA, FRIEND).endByInactivity(START).result()).hasValue(GameResult.BLACK_WIN);
        }

        @Test
        void onlyOnline() {
            assertThatThrownBy(() -> start(ANA, LUIS).endByInactivity(START))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> start(ANA, PAWN).endByInactivity(START))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
