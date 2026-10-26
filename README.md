# Kopi Chess

*Open source educational project in Chess and Code.*

[Leer en español](README.es.md)

Kopi Chess is a local web application to **play chess against five bots** and
**review every game afterwards**. Each move gets an evaluation, a classification
(inaccuracy / mistake / blunder) and the engine's best alternative, and the
review flags any mate you missed. You can also **import a PGN** from Lichess,
Chess.com or an over-the-board game and analyse it the same way.

And for work hours there is an **office mode**: the game looks like any
company's website, the board is a table of letters (`R`, `B`, `N`…) and you
type your moves in algebraic notation (`e4`, `Nf3`, `O-O`). It keeps a low
profile and trains your notation.

It is also a codebase you can learn from. The project is a reference example of:

- **Modern Java (25)**: records, sealed interfaces, pattern matching, virtual
  threads, stream gatherers, scoped values…
- **Spring Boot 4**
- **Hexagonal Architecture**: a pure-Java chess domain (rules verified with
  *perft*), use cases with no framework dependencies, and adapters for the web
  UI, REST API, PostgreSQL and Stockfish.

The UI is available in **English and Spanish**.

## Status

🚧 **In development: phase 7 of 7 (stabilisation and 1.0).** You can already
play against five bots, from `PAWN` to full-strength Stockfish, in the browser,
in **office mode** or through the REST API; import any finished game from PGN
(Lichess, Chess.com, over the board) and export yours; browse your history and
your record against each bot; and every finished game is analysed in the
background: a verdict on each move, accuracy, missed mates and an evaluation
graph ([`v0.6.0`](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.6.0)).
This phase polishes it for anyone to clone, run and use: version 1.0.

- Technical design (Spanish): [`docs/kopi-chess-diseno-tecnico.md`](docs/kopi-chess-diseno-tecnico.md)
- Development plan (Spanish): [`docs/kopi-chess-plan-desarrollo.md`](docs/kopi-chess-plan-desarrollo.md)
- Architecture Decision Records (Spanish): [`docs/adr/`](docs/adr/)

## Planned stack

Java 25 · Spring Boot 4 · Maven · PostgreSQL · Flyway · Stockfish (UCI) ·
Thymeleaf + htmx · Chessground · Docker Compose · JUnit 5 · ArchUnit ·
Testcontainers

## Running locally

Requires **Docker**.

```shell
docker compose up --build
```

Then check `http://localhost:8080/actuator/health`. To play through the API
(the game version goes in `If-Match`; the response includes the bot's reply):

```shell
curl -i -X POST localhost:8080/api/v1/games -H "Content-Type: application/json" \
     -d '{"humanColor": "WHITE", "botProfile": "PAWN"}'
curl -X POST localhost:8080/api/v1/games/<id>/moves -H 'If-Match: "0"' \
     -H "Content-Type: application/json" -d '{"uci": "e2e4"}'
```

If port 8080 is taken, pick another one:
`KOPI_PORT=8090 docker compose up --build` (PowerShell:
`$env:KOPI_PORT=8090; docker compose up --build`).

Stop with `docker compose down`; add `-v` to also delete the saved games.

**While developing**, run `TestKopiApplication` (in
`backend/kopi-bootstrap/src/test`) from your IDE instead: it starts the app
with a throwaway PostgreSQL via Testcontainers. Only Docker is needed.

## Building from source

Requires **JDK 25**. Maven is not needed: the repository ships the Maven
wrapper.

```shell
cd backend
./mvnw verify        # Windows: mvnw.cmd verify
```

Slow tests (the deep *perft* counts, about 15 s) are skipped locally and run
in CI. To run them too: `./mvnw verify -Pslow-tests`.

If your `JAVA_HOME` points to another JDK, set it for the current shell only,
e.g. in PowerShell: `$env:JAVA_HOME = "C:\path\to\jdk-25"`.

If your `~/.m2/settings.xml` routes everything through a private mirror that
is not reachable, build with an empty settings file:
`./mvnw -s path/to/empty-settings.xml verify`, where the file contains only
`<settings/>`.

## Contributing

The code, commits, issues and pull requests are in **English**. Design
documents are in **Spanish**. Contribution guidelines will be added before
v1.0.

## License

[GPL-3.0](LICENSE). Kopi Chess uses Stockfish and Chessground, both GPL-3.0.
