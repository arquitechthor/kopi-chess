package com.arquitechthor.kopi.domain.chess;

/// The two ways to castle.
public enum CastleSide {
    /// Short castling, towards the `h` file: `O-O`.
    KINGSIDE,
    /// Long castling, towards the `a` file: `O-O-O`.
    QUEENSIDE
}
