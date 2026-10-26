package com.arquitechthor.kopi.domain.analysis;

/// How far the engine searches each position (design §8.2): a fixed depth
/// or a fixed number of nodes. Both give the same result on any machine,
/// unlike a time limit, which is what makes an analysis reproducible.
public sealed interface SearchLimits {

    /// Searches to `plies` half-moves deep (design §8.2 suggests 16–18).
    record Depth(int plies) implements SearchLimits {
        public Depth {
            if (plies < 1) {
                throw new IllegalArgumentException("Depth must be at least 1: " + plies);
            }
        }
    }

    /// Searches `count` positions.
    record Nodes(long count) implements SearchLimits {
        public Nodes {
            if (count < 1) {
                throw new IllegalArgumentException("Nodes must be at least 1: " + count);
            }
        }
    }
}
