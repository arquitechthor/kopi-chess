// Perft divide from the console, to debug the move generator against
// Stockfish. A compact source file (Java 25): no class, no build, just
//
//     cd backend
//     ./mvnw -q -pl kopi-domain compile
//     java -cp kopi-domain/target/classes kopi-domain/dev/Perft.java "<fen>" 4
//
// The output has the same shape as Stockfish's `go perft 4` after
// `position fen <fen>`, so both can be compared line by line (or with diff).
// The time goes to stderr, out of the way of the diff.
//
// This file's own implicit class is called Perft too, so the domain class is
// referred to by its full name instead of being imported.

import com.arquitechthor.kopi.domain.chess.Fen;

void main(String[] args) {
    if (args.length != 2) {
        IO.println("Usage: java -cp kopi-domain/target/classes kopi-domain/dev/Perft.java \"<fen>\" <depth>");
        System.exit(2);
    }
    var position = Fen.parse(args[0]);
    var depth = Integer.parseInt(args[1]);

    var start = System.nanoTime();
    var divide = com.arquitechthor.kopi.domain.chess.Perft.divide(position, depth);
    var millis = (System.nanoTime() - start) / 1_000_000;

    divide.forEach((move, nodes) -> IO.println(move + ": " + nodes));
    IO.println("");
    IO.println("Nodes searched: " + divide.values().stream().mapToLong(Long::longValue).sum());
    System.err.println("Time: " + millis + " ms");
}
