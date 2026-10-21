package com.arquitechthor.kopi.domain.chess;

/// The two sides of a chess game.
public enum Color {
    WHITE,
    BLACK;

    /// The other side.
    public Color opposite() {
        // An exhaustive switch over an enum needs no `default`: if a constant
        // were added, this would stop compiling instead of failing at runtime.
        return switch (this) {
            case WHITE -> BLACK;
            case BLACK -> WHITE;
        };
    }
}
