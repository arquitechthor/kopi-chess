package com.arquitechthor.kopi.adapter.web;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// What a person typed in office mode, matched against the legal moves
/// (design §11.5, ADR-013).
///
/// The text is compared with the legal moves *already written* in the
/// visitor's SAN, so only a legal move can ever be accepted, and no second
/// SAN parser is needed. The comparison forgives what does not change the
/// meaning: case (`E4` = `e4`), the capture sign (`ed5` = `exd5`), the
/// equals sign of a promotion, check marks, `0` for `O` in castling, and
/// spaces. UCI (`g1f3`) is accepted too.
///
/// Forgiving case has a price: some piece letters are also files (`A`, `C`,
/// `D` in Spanish; `B` in English), so `axb5` may be a pawn or a bishop.
/// If the text written exactly (case included) is one of the moves, that is
/// the move; if it only fits several moves loosely, the person is asked.
final class OfficeMoveInput {

    private static final Pattern UCI = Pattern.compile("[a-h][1-8][a-h][1-8][qrbn]?");

    /// What may follow a move without changing it: check and mate marks,
    /// annotations.
    private static final Pattern SUFFIX = Pattern.compile("[+#!?]+$");

    private OfficeMoveInput() {}

    /// A legal move, in UCI and in the visitor's SAN.
    record Candidate(String uci, String san) {}

    /// The outcome of matching.
    sealed interface Resolution {
        /// Exactly one legal move fits.
        record Match(String uci, String san) implements Resolution {}

        /// No legal move fits.
        record NoMatch() implements Resolution {}

        /// Several fit; their SAN, to ask "did you mean…?".
        record Ambiguous(List<String> sans) implements Resolution {
            public Ambiguous {
                sans = List.copyOf(sans);
            }
        }
    }

    static Resolution resolve(String text, List<Candidate> moves) {
        Objects.requireNonNull(moves, "moves");
        if (text == null || text.isBlank()) {
            return new Resolution.NoMatch();
        }
        var exact = SUFFIX.matcher(text.replace(" ", "")).replaceFirst("");

        // Written exactly as a move: no doubt, even if loosely it fits more.
        var exactMatches = moves.stream().filter(move -> SUFFIX.matcher(move.san()).replaceFirst("").equals(exact))
                .toList();
        if (exactMatches.size() == 1) {
            return match(exactMatches.getFirst());
        }

        var loose = loosely(exact);
        if (UCI.matcher(loose).matches()) {
            var byUci = moves.stream().filter(move -> move.uci().equals(loose)).findFirst();
            if (byUci.isPresent()) {
                return match(byUci.get());
            }
        }

        var looseMatches = moves.stream().filter(move -> loosely(move.san()).equals(loose)).toList();
        return switch (looseMatches.size()) {
            case 0 -> new Resolution.NoMatch();
            case 1 -> match(looseMatches.getFirst());
            default -> new Resolution.Ambiguous(looseMatches.stream().map(Candidate::san).toList());
        };
    }

    private static Resolution match(Candidate move) {
        return new Resolution.Match(move.uci(), move.san());
    }

    /// A move written without anything that does not change its meaning:
    /// lower case, no spaces, no `x`, `=`, `+`, `#`, and `0` read as `o`.
    private static String loosely(String text) {
        return SUFFIX.matcher(text.replace(" ", "")).replaceFirst("")
                .toLowerCase(Locale.ROOT)
                .replace("x", "")
                .replace("=", "")
                .replace('0', 'o');
    }
}
