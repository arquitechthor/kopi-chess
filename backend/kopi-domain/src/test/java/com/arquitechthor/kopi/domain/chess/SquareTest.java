package com.arquitechthor.kopi.domain.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SquareTest {

    @ParameterizedTest
    @CsvSource({"a1, 0, 0, 0", "h1, 7, 7, 0", "e4, 28, 4, 3", "a8, 56, 0, 7", "h8, 63, 7, 7"})
    void namesIndexesFilesAndRanksAgree(String name, int index, int file, int rank) {
        var square = Square.parse(name);

        assertThat(square.index()).isEqualTo(index);
        assertThat(square.file()).isEqualTo(file);
        assertThat(square.rank()).isEqualTo(rank);
        assertThat(square).isEqualTo(Square.of(file, rank)).isEqualTo(new Square(index));
        assertThat(square).hasToString(name);
    }

    @Test
    void everySquareRoundTripsThroughItsName() {
        assertThat(Square.ALL)
                .hasSize(64)
                .allSatisfy(square -> assertThat(Square.parse(square.toString())).isEqualTo(square));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "e", "e44", "i1", "a0", "a9", "E4", "4e"})
    void rejectsTextThatIsNotASquare(String notASquare) {
        assertThatIllegalArgumentException().isThrownBy(() -> Square.parse(notASquare));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 64, 128})
    void rejectsIndexesOffTheBoard(int index) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Square(index));
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "8, 0", "0, -1", "0, 8"})
    void rejectsCoordinatesOffTheBoard(int file, int rank) {
        assertThatIllegalArgumentException().isThrownBy(() -> Square.of(file, rank));
    }
}
