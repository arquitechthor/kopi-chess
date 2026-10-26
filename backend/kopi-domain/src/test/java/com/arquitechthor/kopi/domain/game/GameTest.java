package com.arquitechthor.kopi.domain.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Move;
import com.arquitechthor.kopi.domain.chess.MoveKind;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.San;
import com.arquitechthor.kopi.domain.chess.Square;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameTest {

    private static final GameId ID = new GameId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final Instant START = Instant.parse("2026-10-23T10:00:00Z");

    /// A new game against `PAWN`, the human playing White.
    private static Game newGame() {
        return Game.start(ID, Color.WHITE, BotProfile.PAWN, Position.initial(), START);
    }

    private static Game newGame(String fen) {
        return Game.start(ID, Color.WHITE, BotProfile.PAWN, Fen.parse(fen), START);
    }

    /// Plays `moves` (SAN, space-separated), each by the side to move, one
    /// second apart.
    private static Game play(Game game, String moves) {
        for (var san : moves.split(" ")) {
            var position = game.currentPosition();
            game = game.play(San.parse(position, san), position.sideToMove(),
                    START.plusSeconds(game.moves().size() + 1L));
        }
        return game;
    }

    @Nested
    class Start {

        @Test
        void beginsInProgressWithNoMoves() {
            var game = newGame();

            assertThat(game.id()).isEqualTo(ID);
            assertThat(game.origin()).isEqualTo(GameOrigin.PLAYED);
            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(game.humanColor()).hasValue(Color.WHITE);
            assertThat(game.botColor()).hasValue(Color.BLACK);
            assertThat(game.botProfile()).hasValue(BotProfile.PAWN);
            assertThat(game.origin()).isEqualTo(GameOrigin.PLAYED);
            assertThat(game.pgnTags()).isEmpty();
            assertThat(game.initialPosition()).isEqualTo(Position.initial());
            assertThat(game.currentPosition()).isEqualTo(Position.initial());
            assertThat(game.moves()).isEmpty();
            assertThat(game.outcome()).isEmpty();
            assertThat(game.result()).isEmpty();
            assertThat(game.version()).isZero();
            assertThat(game.createdAt()).isEqualTo(START);
            assertThat(game.finishedAt()).isEmpty();
        }

        @Test
        void mayStartFromAnyPosition() {
            var game = newGame("4k3/8/8/8/8/8/4P3/4K3 b - - 0 40");

            assertThat(game.currentPosition().sideToMove()).isEqualTo(Color.BLACK);
            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void aPositionWhereTheGameIsAlreadyOverFinishesAtOnce() {
            var game = newGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");

            assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(game.outcome()).contains(new GameOutcome.Stalemate());
            assertThat(game.finishedAt()).contains(START);
        }
    }

    @Nested
    class Play {

        @Test
        void appliesTheMoveAndRecordsIt() {
            var at = START.plusSeconds(5);

            var game = newGame().play(San.parse(Position.initial(), "e4"), Color.WHITE, at);

            assertThat(game.currentPosition().toFen())
                    .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
            assertThat(game.moves()).singleElement().satisfies(played -> {
                assertThat(played.ply()).isEqualTo(1);
                assertThat(played.move().toUci()).isEqualTo("e2e4");
                assertThat(played.san()).isEqualTo("e4");
                assertThat(played.positionAfter()).isEqualTo(game.currentPosition());
                assertThat(played.playedAt()).isEqualTo(at);
            });
            assertThat(game.version()).isEqualTo(1);
            assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void leavesThePreviousGameUntouched() {
            var before = newGame();

            play(before, "e4");

            assertThat(before.moves()).isEmpty();
            assertThat(before.version()).isZero();
        }

        @Test
        void bothSidesMoveThroughTheSameRules() {
            var game = play(newGame(), "e4 e5 Nf3 Nc6");

            assertThat(game.moves()).extracting(PlayedMove::san).containsExactly("e4", "e5", "Nf3", "Nc6");
            assertThat(game.moves()).extracting(PlayedMove::ply).containsExactly(1, 2, 3, 4);
            assertThat(game.version()).isEqualTo(4);
        }

        @Test
        void onlyTheSideToMoveMayPlay() {
            var game = newGame();
            var e4 = San.parse(Position.initial(), "e4");

            assertThatThrownBy(() -> game.play(e4, Color.BLACK, START))
                    .isInstanceOfSatisfying(NotYourTurnException.class, e -> {
                        assertThat(e.gameId()).isEqualTo(ID);
                        assertThat(e.color()).isEqualTo(Color.BLACK);
                    });
        }

        @Test
        void onlyLegalMovesArePlayed() {
            // Well-formed, but a pawn cannot jump three squares.
            var e2e5 = new Move(Square.parse("e2"), Square.parse("e5"), MoveKind.QUIET);

            assertThatThrownBy(() -> newGame().play(e2e5, Color.WHITE, START))
                    .isInstanceOfSatisfying(IllegalMoveException.class, e -> {
                        assertThat(e.gameId()).isEqualTo(ID);
                        assertThat(e.uci()).isEqualTo("e2e5");
                    });
        }

        @Test
        void acceptsAMoveWrittenInUci() {
            var game = newGame().play("g1f3", Color.WHITE, START);

            assertThat(game.moves()).singleElement().extracting(PlayedMove::san).isEqualTo("Nf3");
        }

        @Test
        void aPromotionInUciNamesThePiece() {
            var game = newGame("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1").play("b7b8n", Color.WHITE, START);

            assertThat(game.moves().getFirst().san()).isEqualTo("b8=N");
        }

        @Test
        void uciTextThatIsNoLegalMoveIsRefused() {
            assertThatThrownBy(() -> newGame().play("e2e5", Color.WHITE, START))
                    .isInstanceOfSatisfying(IllegalMoveException.class, e -> assertThat(e.uci()).isEqualTo("e2e5"));
            // A promotion needs its piece.
            assertThatThrownBy(() -> newGame("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1").play("b7b8", Color.WHITE, START))
                    .isInstanceOf(IllegalMoveException.class);
        }

        @Test
        void aFinishedGameIsReportedBeforeTheTurnOrTheMove() {
            var finished = newGame().resign(Color.WHITE, START);

            assertThatThrownBy(() -> finished.play("e7e5", Color.BLACK, START))
                    .isInstanceOf(GameNotInProgressException.class);
        }

        @Test
        void aMoveLegalElsewhereIsNotLegalHere() {
            // exd5 is legal after 1.e4 d5, but after 1.e4 e6 there is
            // nothing to capture on d5.
            var exd5 = San.parse(play(newGame(), "e4 d5").currentPosition(), "exd5");

            assertThatThrownBy(() -> play(newGame(), "e4 e6").play(exd5, Color.WHITE, START))
                    .isInstanceOf(IllegalMoveException.class);
        }
    }

    @Nested
    class Endings {

        @Test
        void checkmateFinishesTheGameWithAWinner() {
            var game = play(newGame(), "f3 e5 g4 Qh4#");

            assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
            assertThat(game.outcome()).contains(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(game.result()).contains(GameResult.BLACK_WIN);
            assertThat(game.finishedAt()).contains(game.moves().getLast().playedAt());
        }

        @Test
        void stalemateIsADraw() {
            var game = play(newGame("7k/8/4Q3/6K1/8/8/8/8 w - - 0 1"), "Qf7");

            assertThat(game.outcome()).contains(new GameOutcome.Stalemate());
            assertThat(game.result()).contains(GameResult.DRAW);
        }

        @Test
        void insufficientMaterialIsADraw() {
            var game = play(newGame("4k3/8/8/8/8/8/4q3/4K3 w - - 0 1"), "Kxe2");

            assertThat(game.outcome()).contains(new GameOutcome.InsufficientMaterial());
            assertThat(game.result()).contains(GameResult.DRAW);
        }

        @Test
        void theThirdOccurrenceOfAPositionIsADraw() {
            // The initial position, again after 4 and after 8 plies.
            var twice = play(newGame(), "Nf3 Nf6 Ng1 Ng8");
            var thrice = play(twice, "Nf3 Nf6 Ng1 Ng8");

            assertThat(twice.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(thrice.outcome()).contains(new GameOutcome.ThreefoldRepetition());
            assertThat(thrice.result()).contains(GameResult.DRAW);
        }

        @Test
        void repetitionsCountFromTheStartingPositionToo() {
            var game = newGame("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");

            var finished = play(game, "Ra2 Kd8 Ra1 Ke8 Ra2 Kd8 Ra1 Ke8");

            assertThat(finished.moves()).hasSize(8);
            assertThat(finished.outcome()).contains(new GameOutcome.ThreefoldRepetition());
        }

        @Test
        void fiftyMovesWithoutCaptureOrPawnMoveIsADraw() {
            var game = play(newGame("4k3/8/8/8/8/8/8/R3K3 w - - 99 80"), "Ra2");

            assertThat(game.outcome()).contains(new GameOutcome.FiftyMoveRule());
            assertThat(game.result()).contains(GameResult.DRAW);
        }

        @Test
        void checkmateWinsOverTheFiftyMoveRule() {
            // The mating move is also the hundredth half-move (ADR-008).
            var game = play(newGame("6k1/5ppp/8/8/8/8/8/R5K1 w - - 99 80"), "Ra8#");

            assertThat(game.outcome()).contains(new GameOutcome.Checkmate(Color.WHITE));
        }

        @Test
        void aFinishedGameTakesNoMoreMoves() {
            var game = play(newGame(), "f3 e5 g4 Qh4#");
            var anyMove = new Move(Square.parse("e1"), Square.parse("f2"), MoveKind.QUIET);

            assertThatThrownBy(() -> game.play(anyMove, Color.WHITE, START))
                    .isInstanceOfSatisfying(GameNotInProgressException.class,
                            e -> assertThat(e.gameId()).isEqualTo(ID));
        }
    }

    @Nested
    class Resign {

        @Test
        void theOpponentWins() {
            var at = START.plusSeconds(60);

            var game = play(newGame(), "e4").resign(Color.WHITE, at);

            assertThat(game.outcome()).contains(new GameOutcome.Resignation(Color.WHITE));
            assertThat(game.result()).contains(GameResult.BLACK_WIN);
            assertThat(game.finishedAt()).contains(at);
            assertThat(game.version()).isEqualTo(2);
        }

        @Test
        void eitherSideMayResignAtAnyTime() {
            // It is White's turn, but Black resigns.
            var game = newGame().resign(Color.BLACK, START);

            assertThat(game.result()).contains(GameResult.WHITE_WIN);
        }

        @Test
        void aFinishedGameCannotBeResigned() {
            var game = newGame().resign(Color.BLACK, START);

            assertThatThrownBy(() -> game.resign(Color.WHITE, START))
                    .isInstanceOf(GameNotInProgressException.class);
        }
    }

    @Test
    void theRuleViolationsAreASealedFamily() {
        // Adapters can switch over them exhaustively (REST: 409 vs 422).
        GameRuleException violation = new NotYourTurnException(ID, Color.BLACK);

        var code = switch (violation) {
            case GameNotInProgressException _ -> "finished";
            case NotYourTurnException _ -> "turn";
            case IllegalMoveException _ -> "illegal";
        };

        assertThat(code).isEqualTo("turn");
    }

    @Test
    void outcomesKnowTheirResult() {
        assertThat(new GameOutcome.Checkmate(Color.WHITE).result()).isEqualTo(GameResult.WHITE_WIN);
        assertThat(new GameOutcome.Resignation(Color.WHITE).result()).isEqualTo(GameResult.BLACK_WIN);
        assertThat(new GameOutcome.Stalemate().result()).isEqualTo(GameResult.DRAW);
        assertThat(new GameOutcome.FiftyMoveRule().result()).isEqualTo(GameResult.DRAW);
    }

    @Test
    void botProfilesGoFromLevelOneToFive() {
        assertThat(BotProfile.values()).extracting(BotProfile::level).containsExactly(1, 2, 3, 4, 5);
        assertThat(BotProfile.values()).containsExactly(
                BotProfile.PAWN, BotProfile.KNIGHT, BotProfile.BISHOP, BotProfile.ROOK, BotProfile.QUEEN);
    }
}
