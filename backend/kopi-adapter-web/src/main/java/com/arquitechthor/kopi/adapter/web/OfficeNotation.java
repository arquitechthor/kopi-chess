package com.arquitechthor.kopi.adapter.web;

import java.util.Locale;
import org.springframework.context.MessageSource;

/// SAN with the piece letters of the visitor's language: `Nf3` → `Cf3`,
/// `e8=Q` → `e8=D` in Spanish (design §11.5).
///
/// The domain always speaks standard SAN with English letters (ADR-011,
/// ADR-013); the letters of each language live in the message bundles
/// (`piece.letter.KING`…), so adding a language needs no code.
///
/// @param king   the letter of the king (`K`, `R`)
/// @param queen  the letter of the queen (`Q`, `D`)
/// @param rook   the letter of the rook (`R`, `T`)
/// @param bishop the letter of the bishop (`B`, `A`)
/// @param knight the letter of the knight (`N`, `C`)
/// @param pawn   the letter of the pawn, for the board (`P`)
record OfficeNotation(char king, char queen, char rook, char bishop, char knight, char pawn) {

    static OfficeNotation of(char king, char queen, char rook, char bishop, char knight) {
        return new OfficeNotation(king, queen, rook, bishop, knight, 'P');
    }

    static OfficeNotation of(MessageSource messages, Locale locale) {
        return new OfficeNotation(letter(messages, "KING", locale), letter(messages, "QUEEN", locale),
                letter(messages, "ROOK", locale), letter(messages, "BISHOP", locale),
                letter(messages, "KNIGHT", locale), letter(messages, "PAWN", locale));
    }

    private static char letter(MessageSource messages, String piece, Locale locale) {
        return messages.getMessage("piece.letter." + piece, null, locale).charAt(0);
    }

    /// `san` with these letters. Each English piece letter is replaced at
    /// once, so the Spanish king (`R`) never becomes a rook by mistake.
    String localise(String san) {
        var localised = new StringBuilder(san.length());
        for (int i = 0; i < san.length(); i++) {
            var c = san.charAt(i);
            localised.append(switch (c) {
                case 'K' -> king;
                case 'Q' -> queen;
                case 'R' -> rook;
                case 'B' -> bishop;
                case 'N' -> knight;
                default -> c; // files, ranks, x, =, +, #, and the O of castling
            });
        }
        return localised.toString();
    }

}
