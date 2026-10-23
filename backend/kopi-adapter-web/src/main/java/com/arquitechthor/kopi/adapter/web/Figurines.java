package com.arquitechthor.kopi.adapter.web;

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
}
