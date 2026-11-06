package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.PieceType;

/// SAN in figurine notation: `Nf3` → `♘f3`, `e8=Q` → `e8=♕`.
///
/// Figurines do not depend on the language (design §11.4), so the same move
/// list reads well in English and in Spanish. The domain keeps standard SAN
/// with English letters (ADR-011); this is presentation.
final class Figurines {

    private Figurines() {}

    static String of(String san) {
        var figurine = new StringBuilder(san.length());
        for (int i = 0; i < san.length(); i++) {
            var c = san.charAt(i);
            // Only upper-case letters stand for pieces; files are lower case
            // and castling is written with the letter O.
            figurine.append(switch (c) {
                case 'K' -> '♔';
                case 'Q' -> '♕';
                case 'R' -> '♖';
                case 'B' -> '♗';
                case 'N' -> '♘';
                default -> c;
            });
        }
        return figurine.toString();
    }

    /// One piece as a figurine in its own colour: `♘` for a white knight,
    /// `♞` for a black one. The black pawn carries U+FE0E (text style), as
    /// some systems draw U+265F alone as an emoji.
    static String piece(Color color, PieceType type) {
        var white = color == Color.WHITE;
        return switch (type) {
            case PAWN -> white ? "♙" : "♟︎";
            case KNIGHT -> white ? "♘" : "♞";
            case BISHOP -> white ? "♗" : "♝";
            case ROOK -> white ? "♖" : "♜";
            case QUEEN -> white ? "♕" : "♛";
            case KING -> white ? "♔" : "♚";
        };
    }
}
