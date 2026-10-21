package com.arquitechthor.kopi.domain.chess;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/// Where every piece stands. Immutable: "changing" a board returns a new one.
///
/// ## The 0x88 representation
///
/// Internally the board is an array of 128 cells: 16 per rank, of which only
/// the first 8 are real squares. The cell of a square is `rank * 16 + file`.
///
/// ```
/// rank 8 | 112 ... 119 | 120 ... 127   <- right half: off the board
/// ...
/// rank 1 |   0 ...   7 |   8 ...  15
///          a  ...  h
/// ```
///
/// The trick: a cell is on the board if and only if `(cell & 0x88) == 0`.
/// Walking off any edge (left, right, top or bottom) sets one of those bits,
/// so the move generator can step in any direction and check validity with
/// a single operation, with no special cases for edges or wrap-around.
///
/// None of this leaks out: the public API speaks in [Square]s (ADR-004).
public final class Board {

    private static final int CELLS = 128;

    /// Indexed by 0x88 cell; `null` means empty. Never exposed, never mutated
    /// after construction.
    private final Piece[] cells;

    private Board(Piece[] cells) {
        this.cells = cells;
    }

    /// A board with no pieces.
    public static Board empty() {
        return new Board(new Piece[CELLS]);
    }

    /// A board with exactly these pieces.
    public static Board of(Map<Square, Piece> pieces) {
        var cells = new Piece[CELLS];
        pieces.forEach((square, piece) -> cells[cellOf(square)] = Objects.requireNonNull(piece, "piece"));
        return new Board(cells);
    }

    /// The piece on a square, if any.
    public Optional<Piece> pieceAt(Square square) {
        return Optional.ofNullable(cells[cellOf(square)]);
    }

    /// A copy of this board with `piece` on `square` (replacing whatever was there).
    public Board withPiece(Square square, Piece piece) {
        var copy = cells.clone();
        copy[cellOf(square)] = Objects.requireNonNull(piece, "piece");
        return new Board(copy);
    }

    /// A copy of this board with `square` empty.
    public Board withoutPiece(Square square) {
        var copy = cells.clone();
        copy[cellOf(square)] = null;
        return new Board(copy);
    }

    /// Every piece on the board, ordered from `a1` to `h8`.
    ///
    /// A `SequencedMap` (Java 21): the order is part of the contract, and
    /// callers get `firstEntry()`, `reversed()`, etc. for free.
    public SequencedMap<Square, Piece> pieces() {
        var pieces = new LinkedHashMap<Square, Piece>();
        for (var square : Square.ALL) {
            var piece = cells[cellOf(square)];
            if (piece != null) {
                pieces.put(square, piece);
            }
        }
        return Collections.unmodifiableSequencedMap(pieces);
    }

    // --- 0x88 helpers, package-private for the move generator ---

    /// The 0x88 cell of a square.
    static int cellOf(Square square) {
        return square.rank() * 16 + square.file();
    }

    /// Whether a 0x88 cell is a real square.
    static boolean isOnBoard(int cell) {
        return (cell & 0x88) == 0;
    }

    /// The square of an on-board 0x88 cell.
    static Square squareOf(int cell) {
        return Square.of(cell & 7, cell >> 4);
    }

    /// The piece on an on-board 0x88 cell, or `null`.
    Piece at(int cell) {
        return cells[cell];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Board board && Arrays.equals(cells, board.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    /// A text diagram, White at the bottom, handy in test failures:
    ///
    /// ```
    /// 8 r n b q k b n r
    /// 7 p p p p p p p p
    /// ...
    ///   a b c d e f g h
    /// ```
    @Override
    public String toString() {
        var diagram = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            diagram.append(rank + 1);
            for (int file = 0; file < 8; file++) {
                var piece = cells[rank * 16 + file];
                diagram.append(' ').append(piece == null ? '.' : piece.fenChar());
            }
            diagram.append('\n');
        }
        return diagram.append("  a b c d e f g h").toString();
    }
}
