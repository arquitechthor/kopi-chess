package com.arquitechthor.kopi.domain.chess;

import java.util.List;
import java.util.stream.IntStream;

/// One of the 64 squares of the board.
///
/// Squares are numbered from `a1` = 0 to `h8` = 63, rank by rank:
///
/// ```
/// rank 8 | 56 57 58 59 60 61 62 63
/// ...
/// rank 2 |  8  9 10 11 12 13 14 15
/// rank 1 |  0  1  2  3  4  5  6  7
///        +------------------------
///           a  b  c  d  e  f  g  h
/// ```
///
/// This is the public, human-friendly coordinate. How the board stores
/// pieces internally (0x88, see ADR-004) is a detail hidden behind it.
///
/// @param index 0 (`a1`) to 63 (`h8`)
public record Square(int index) {

    /// All 64 squares, from `a1` to `h8`.
    public static final List<Square> ALL = IntStream.range(0, 64).mapToObj(Square::new).toList();

    // A compact canonical constructor: it validates the argument, and the
    // record then assigns the field. Invalid squares cannot exist.
    public Square {
        if (index < 0 || index > 63) {
            throw new IllegalArgumentException("Square index must be between 0 and 63: " + index);
        }
    }

    /// The square at a file (0 = `a` … 7 = `h`) and rank (0 = `1` … 7 = `8`).
    ///
    /// @throws IllegalArgumentException if either coordinate is off the board
    public static Square of(int file, int rank) {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Off the board: file " + file + ", rank " + rank);
        }
        return ALL.get(rank * 8 + file);
    }

    /// The square for algebraic notation such as `e4`.
    ///
    /// @throws IllegalArgumentException if the text is not a square name
    public static Square parse(String name) {
        if (name == null || name.length() != 2) {
            throw new IllegalArgumentException("Not a square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Not a square: " + name);
        }
        return of(file, rank);
    }

    /// 0 for the `a` file … 7 for the `h` file.
    public int file() {
        return index % 8;
    }

    /// 0 for the first rank … 7 for the eighth.
    public int rank() {
        return index / 8;
    }

    /// Algebraic notation: `e4`.
    @Override
    public String toString() {
        return "" + (char) ('a' + file()) + (char) ('1' + rank());
    }
}
