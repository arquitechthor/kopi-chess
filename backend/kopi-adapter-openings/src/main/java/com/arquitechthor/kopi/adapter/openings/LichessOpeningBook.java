package com.arquitechthor.kopi.adapter.openings;

import com.arquitechthor.kopi.application.port.OpeningBook;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.chess.San;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The [OpeningBook] port, over the `lichess-org/chess-openings` dataset
/// (CC0, ADR-009): about 3,900 named openings, kept in memory.
///
/// The dataset's five TSV files (`eco`, `name`, `pgn`) ship as resources in
/// `openings/`, exactly as published at the commit named in `NOTICE`.
/// Each line's moves are replayed with Kopi's own rules and the position
/// they reach is the key, as [Position#zobristHash]: the same "same
/// position" as the repetition rule, so the move clocks and an en passant
/// square nobody can use do not get in the way.
///
/// In the bundled version every line reaches a different position (a test
/// checks it). Should a later version repeat one, the first line wins.
///
/// Loading replays every line, about a second on a cold JVM: create it
/// once.
public final class LichessOpeningBook implements OpeningBook {

    /// The dataset's files, one per ECO volume.
    static final List<String> FILES = List.of("a.tsv", "b.tsv", "c.tsv", "d.tsv", "e.tsv");

    private static final String HEADER = "eco\tname\tpgn";

    private final Map<Long, Opening> byPosition;

    private LichessOpeningBook(Map<Long, Opening> byPosition) {
        this.byPosition = Map.copyOf(byPosition);
    }

    /// The book, read from the bundled dataset.
    ///
    /// @throws IllegalStateException if a file is missing or a line cannot
    ///                               be replayed: the bundled data is part
    ///                               of the build, so that is a bug
    public static LichessOpeningBook load() {
        var byPosition = new HashMap<Long, Opening>();
        for (var file : FILES) {
            read(file, byPosition);
        }
        return new LichessOpeningBook(byPosition);
    }

    /// The book, read from the bundled dataset the first time a position is
    /// looked up: the application starts without paying for it (about a
    /// second), and only the analysis, in the background, ever waits.
    /// Loaded once, safely across threads (a holder class, initialised by
    /// the JVM on first use).
    public static OpeningBook loadedOnFirstUse() {
        return position -> Loaded.BOOK.lookup(position);
    }

    private static final class Loaded {
        static final LichessOpeningBook BOOK = load();
    }

    /// How many distinct positions have a name.
    public int size() {
        return byPosition.size();
    }

    @Override
    public Optional<Opening> lookup(Position position) {
        return Optional.ofNullable(byPosition.get(position.zobristHash()));
    }

    private static void read(String file, Map<Long, Opening> byPosition) {
        var resource = "openings/" + file;
        var stream = LichessOpeningBook.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("The opening book is missing " + resource);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new IllegalStateException(resource + " does not start with \"" + HEADER + "\": " + header);
            }
            int lineNumber = 1;
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                var fields = line.split("\t", -1);
                if (fields.length != 3) {
                    throw new IllegalStateException(resource + ":" + lineNumber + " has " + fields.length + " fields");
                }
                try {
                    var opening = new Opening(fields[0], fields[1]);
                    byPosition.putIfAbsent(replay(fields[2]).zobristHash(), opening);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(resource + ":" + lineNumber + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    /// The position after `pgn`'s moves, played from the start:
    /// `1. e4 c5 2. Nf3`. Move numbers are skipped.
    static Position replay(String pgn) {
        var position = Position.initial();
        for (var token : Objects.requireNonNull(pgn, "pgn").strip().split("\\s+")) {
            if (!token.endsWith(".")) {
                position = position.apply(San.parse(position, token));
            }
        }
        return position;
    }
}
