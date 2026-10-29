# Kopi Chess

*Open source educational project in Chess and Code.*

[Leer en español](README.es.md)

Play chess against five bots in your browser, then **review every game**: each
move gets a verdict, the engine's best alternative, and the review flags any
mate you missed. It runs on your machine; nothing leaves it.

![The review of Morphy's Opera Game (1858): the board at 16.Qb8+, the evaluation graph, accuracy for both sides and every move with its verdict](docs/img/review.png)

## Quick start

You need **Docker** with Compose (Docker Desktop on Windows and macOS), on an
x86-64 or ARM64 machine, and about 1 GB of disk.

```shell
git clone https://github.com/arquitechthor/kopi-chess.git
cd kopi-chess
docker compose up --build
```

Open <http://localhost:8080>, choose a bot and play. The first start takes a
few minutes (it builds the application); later ones, seconds.

## What it does

- **Five bots**, from `PAWN` (plays like a child who has just learnt the
  moves) to Stockfish at full strength. Your record against each one is next
  to it.
- **A review of every finished game**, in the background: a verdict on each
  move (book, best, good, inaccuracy, mistake, blunder, missed mate), the opening,
  accuracy for both sides and a graph of the winning chances.
- **PGN import and export**: analyse a game from Lichess, Chess.com or over
  the board the same way, and take yours anywhere.
- **History** of all your games, filtered by origin and result.
- **Office mode**: the game looks like any company's website, the board is a
  table of letters (`R`, `B`, `N`…) and you type your moves in algebraic
  notation (`e4`, `Nf3`, `O-O`). It keeps a low profile and trains your
  notation.
- **A REST API** for all of it, and the UI in **English and Spanish**.

## Learning from the code

Kopi Chess is also meant to be read. It is a reference example of:

- **Modern Java (25)**: records, sealed interfaces, pattern matching, virtual
  threads, stream gatherers, scoped values…
- **Spring Boot 4**
- **Hexagonal Architecture**: a pure-Java chess domain (rules verified with
  *perft*), use cases with no framework dependencies, and adapters for the web
  UI, the REST API, PostgreSQL and Stockfish. The boundaries are checked by
  ArchUnit.

Stack: Java 25 · Spring Boot 4 · Maven · PostgreSQL · Flyway · Stockfish
(UCI) · Thymeleaf · Chessground · Docker Compose · JUnit 5 · ArchUnit ·
Testcontainers.

- Where to start reading (Spanish): [`docs/arquitectura.md`](docs/arquitectura.md)
- Technical design (Spanish): [`docs/kopi-chess-diseno-tecnico.md`](docs/kopi-chess-diseno-tecnico.md)
- Development plan (Spanish): [`docs/kopi-chess-plan-desarrollo.md`](docs/kopi-chess-plan-desarrollo.md)
- Architecture Decision Records (Spanish): [`docs/adr/`](docs/adr/)

## Status

🚧 **Phase 7 of 7: stabilisation and 1.0.** Everything above works
([`v0.7.0`](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.7.0));
this phase polishes it so that anyone can clone, run and use it.

## Running it

**Port 8080 taken?** Pick another one: `KOPI_PORT=8090 docker compose up --build`
(PowerShell: `$env:KOPI_PORT=8090; docker compose up --build`).

**Stop** with `docker compose down`. Your games are kept; `docker compose down -v`
deletes them too.

**Without building**: from `v0.7.0` on, every release publishes the image to
GitHub Container Registry, for x86-64 and ARM64. In the cloned repository:

```shell
KOPI_IMAGE=ghcr.io/arquitechthor/kopi-chess:0.7 docker compose up --no-build
```

**With metrics**: the `metrics` profile adds Prometheus and Grafana, with a
dashboard of the bots' move times, the analyses and the JVM, at
<http://localhost:3000>:

```shell
docker compose --profile metrics up --build
```

The raw metrics are at <http://localhost:8080/actuator/prometheus>.

**Through the API** (the game's version goes in `If-Match`; the answer
includes the bot's reply):

```shell
curl -i -X POST localhost:8080/api/v1/games -H "Content-Type: application/json" \
     -d '{"humanColor": "WHITE", "botProfile": "PAWN"}'
curl -X POST localhost:8080/api/v1/games/<id>/moves -H 'If-Match: "0"' \
     -H "Content-Type: application/json" -d '{"uci": "e2e4"}'
```

## Building from source

Requires **JDK 25**. Maven is not needed: the repository ships the Maven
wrapper. The integration tests need Docker (Testcontainers).

```shell
cd backend
./mvnw verify        # Windows: mvnw.cmd verify
```

Slow tests (the deep *perft* counts, about 15 s) are skipped locally and run
in CI. To run them too: `./mvnw verify -Pslow-tests`.

**While developing**, run `TestKopiApplication` (in
`backend/kopi-bootstrap/src/test`) from your IDE: it starts the app with a
throwaway PostgreSQL via Testcontainers.

If your `JAVA_HOME` points to another JDK, set it for the current shell only,
e.g. in PowerShell: `$env:JAVA_HOME = "C:\path\to\jdk-25"`.

If your `~/.m2/settings.xml` routes everything through a private mirror that
is not reachable, build with an empty settings file:
`./mvnw -s path/to/empty-settings.xml verify`, where the file contains only
`<settings/>`.

## Contributing

The code, commits, issues and pull requests are in **English**. Design
documents are in **Spanish**. How to set up, the workflow and the definition
of done are in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

[GPL-3.0](LICENSE). Kopi Chess uses Stockfish and Chessground, both GPL-3.0;
see [`NOTICE`](NOTICE).
