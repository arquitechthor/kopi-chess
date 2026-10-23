package com.arquitechthor.kopi.adapter.stockfish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.arquitechthor.kopi.adapter.stockfish.InfoLine.Score.Centipawns;
import com.arquitechthor.kopi.domain.chess.Fen;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class UciEngineTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void introducesItselfAndAnnouncesItsOptions() {
        var connection = ScriptedConnection.answeringGo(List.of());

        var engine = UciEngine.start(connection, TIMEOUT);

        assertThat(engine.name()).isEqualTo("Stockfish 19");
        assertThat(connection.sent).containsExactly("uci", "isready");
    }

    @Test
    void readsOptionNamesWithSpacesAndTheirRanges() {
        assertThat(UciEngine.option("option name Skill Level type spin default 20 min 0 max 20"))
                .isEqualTo(new UciEngine.Option("Skill Level", "spin", 0, 20));
        assertThat(UciEngine.option("option name UCI_LimitStrength type check default false"))
                .isEqualTo(new UciEngine.Option("UCI_LimitStrength", "check", null, null));
        assertThat(UciEngine.option("option name Clear Hash type button"))
                .isEqualTo(new UciEngine.Option("Clear Hash", "button", null, null));
    }

    @Test
    void setsOptionsWithinTheAnnouncedRange() {
        var connection = ScriptedConnection.answeringGo(List.of());
        var engine = UciEngine.start(connection, TIMEOUT);

        engine.setOption("Skill Level", 5);
        engine.setOption("UCI_LimitStrength", true);

        assertThat(connection.sent).contains("setoption name Skill Level value 5",
                "setoption name UCI_LimitStrength value true");
    }

    @Test
    void refusesValuesTheEngineWouldClampSilently() {
        // Stockfish turns UCI_Elo 1000 into 1320 without a word.
        var engine = UciEngine.start(ScriptedConnection.answeringGo(List.of()), TIMEOUT);

        assertThatIllegalArgumentException().isThrownBy(() -> engine.setOption("UCI_Elo", 1000))
                .withMessageContaining("1320");
        assertThatIllegalArgumentException().isThrownBy(() -> engine.setOption("Skill Level", 21));
        assertThatIllegalArgumentException().isThrownBy(() -> engine.setOption("Contempt", 10));
    }

    @Test
    void searchesAndKeepsTheLastLineOfEachCandidate() {
        var connection = ScriptedConnection.answeringGo(List.of(
                "info string NNUE evaluation using nn-1a298aa575a0.nnue",
                "info depth 11 multipv 1 score cp 30 pv e2e4 e7e5",
                "info depth 11 multipv 2 score cp 20 pv d2d4",
                "info depth 12 multipv 1 score cp 35 pv e2e4 e7e5 g1f3",
                "info depth 12 currmove e2e4 currmovenumber 1",
                "bestmove e2e4 ponder e7e5"));
        var engine = UciEngine.start(connection, TIMEOUT);

        var result = engine.search(Fen.INITIAL, "depth 12", TIMEOUT);

        assertThat(result.bestMove()).contains("e2e4");
        assertThat(result.lines()).extracting(InfoLine::multiPv, InfoLine::depth, InfoLine::score)
                .containsExactly(
                        tuple(1, 12, new Centipawns(35)),
                        tuple(2, 11, new Centipawns(20)));
        assertThat(connection.sent).contains("position fen " + Fen.INITIAL, "go depth 12");
    }

    @Test
    void aFinishedPositionHasNoBestMove() {
        var engine = UciEngine.start(
                ScriptedConnection.answeringGo(List.of("info depth 0 score mate 0", "bestmove (none)")), TIMEOUT);

        assertThat(engine.search(Fen.INITIAL, "depth 10", TIMEOUT).bestMove()).isEmpty();
    }

    @Test
    void aSearchThatDoesNotEndInTimeIsStoppedAndFails() {
        // The engine thinks but never says bestmove.
        var connection = ScriptedConnection.answeringGo(List.of("info depth 1 score cp 10 pv e2e4"));
        var engine = UciEngine.start(connection, TIMEOUT);

        assertThatThrownBy(() -> engine.search(Fen.INITIAL, "movetime 100", TIMEOUT))
                .isInstanceOf(UciException.class).hasMessageContaining("No bestmove");
        assertThat(connection.sent).last().isEqualTo("stop");
    }

    @Test
    void anEngineThatDoesNotFinishItsIntroductionIsClosed() {
        var connection = new ScriptedConnection(command -> List.of("id name Mute"));

        assertThatThrownBy(() -> UciEngine.start(connection, TIMEOUT)).isInstanceOf(UciException.class);
        assertThat(connection.closed).isTrue();
    }
}
