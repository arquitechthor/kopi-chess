package com.arquitechthor.kopi.domain.chess;

import java.util.random.RandomGeneratorFactory;

/// Zobrist hashing: a 64-bit fingerprint of a position, used to detect
/// repetitions without comparing whole boards.
///
/// Each feature of a position (a white knight on `f3`, Black to move, White
/// may castle kingside, an en passant capture on the `d` file…) gets a fixed
/// random 64-bit number, and the hash is the XOR of the numbers of the
/// features present. Two equal positions always get the same hash; two
/// different ones almost never do (one chance in 2^64 per pair).
///
/// What counts is what makes positions "the same" for the repetition rule
/// (ADR-008): pieces, side to move, castling rights, and the en passant
/// file **only if an en passant capture is legal**. The clocks never count.
final class Zobrist {

    /// The seed of the table. Fixed, so a position hashes the same in every
    /// run and on every JVM: hashes could be stored and compared later.
    private static final long SEED = 0x4B6F7069_43686573L; // "KopiChes"

    /// One number per piece (6 types × 2 colours) and square.
    private static final long[][] PIECE_SQUARE = new long[12][64];
    private static final long BLACK_TO_MOVE;
    /// White kingside, white queenside, black kingside, black queenside.
    private static final long[] CASTLING = new long[4];
    /// One per file of the en passant target.
    private static final long[] EN_PASSANT_FILE = new long[8];

    static {
        // L64X128MixRandom is specified by its algorithm (JEP 356), so the
        // same seed gives the same sequence on any conforming JDK.
        var random = RandomGeneratorFactory.of("L64X128MixRandom").create(SEED);
        for (var piece : PIECE_SQUARE) {
            for (int square = 0; square < 64; square++) {
                piece[square] = random.nextLong();
            }
        }
        BLACK_TO_MOVE = random.nextLong();
        for (int i = 0; i < CASTLING.length; i++) {
            CASTLING[i] = random.nextLong();
        }
        for (int i = 0; i < EN_PASSANT_FILE.length; i++) {
            EN_PASSANT_FILE[i] = random.nextLong();
        }
    }

    private Zobrist() {}

    /// The hash of `position`.
    ///
    /// Computed from scratch, scanning the board. Updating it move by move
    /// (XOR out the old square, XOR in the new one) is the classic speed-up,
    /// left for when something needs it: a game hashes one position per
    /// move.
    static long hash(Position position) {
        long hash = 0;
        for (var entry : position.board().pieces().entrySet()) {
            hash ^= PIECE_SQUARE[pieceIndex(entry.getValue())][entry.getKey().index()];
        }
        if (position.sideToMove() == Color.BLACK) {
            hash ^= BLACK_TO_MOVE;
        }
        var rights = position.castlingRights();
        if (rights.whiteKingside()) {
            hash ^= CASTLING[0];
        }
        if (rights.whiteQueenside()) {
            hash ^= CASTLING[1];
        }
        if (rights.blackKingside()) {
            hash ^= CASTLING[2];
        }
        if (rights.blackQueenside()) {
            hash ^= CASTLING[3];
        }
        if (position.enPassantTarget().isPresent() && hasLegalEnPassant(position)) {
            hash ^= EN_PASSANT_FILE[position.enPassantTarget().get().file()];
        }
        return hash;
    }

    /// Whether the side to move can actually capture en passant. A target
    /// square alone is not enough: FEN writes one after every double push,
    /// even when no pawn stands next to it, or when capturing would expose
    /// the king.
    private static boolean hasLegalEnPassant(Position position) {
        return position.legalMoves().stream().anyMatch(move -> move.kind() instanceof MoveKind.EnPassant);
    }

    /// 0–5 for White's pawn … king, 6–11 for Black's.
    private static int pieceIndex(Piece piece) {
        return piece.type().ordinal() + (piece.color() == Color.WHITE ? 0 : 6);
    }
}
