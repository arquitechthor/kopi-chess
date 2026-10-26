package com.arquitechthor.kopi.domain.chess;

import java.util.Objects;
import java.util.regex.Pattern;

/// A named opening: `B90 Sicilian Defense: Najdorf Variation`.
///
/// Names are in English in every language of the UI: the dataset they come
/// from only has them in English (design §12).
///
/// @param eco  the Encyclopaedia of Chess Openings code, `A00` to `E99`
/// @param name the name, `Family: Variation, Subvariation`
public record Opening(String eco, String name) {

    private static final Pattern ECO = Pattern.compile("[A-E][0-9]{2}");

    public Opening {
        Objects.requireNonNull(eco, "eco");
        Objects.requireNonNull(name, "name");
        if (!ECO.matcher(eco).matches()) {
            throw new IllegalArgumentException("Not an ECO code: " + eco);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("An opening needs a name");
        }
    }
}
