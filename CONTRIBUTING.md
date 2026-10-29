# Contributing to Kopi Chess

Thank you for wanting to help. Kopi Chess is a small project that is meant to
be read and learnt from, so a clear change with good tests is worth more than
a big one.

**Languages.** Code, comments, commits, issues and pull requests are in
**English**. The design documents in `docs/` are in **Spanish**. The UI exists
in both.

## Before you start

- **Small fixes** (a typo, a clear bug): open the pull request directly.
- **Anything larger** (a feature, a new dependency, a change of design): open
  an issue first, so we can agree on the approach before you spend time on it.
  Issues labelled [`good first issue`](https://github.com/arquitechthor/kopi-chess/labels/good%20first%20issue)
  are a good way in.

To find your way around the code, read
[`docs/arquitectura.md`](docs/arquitectura.md) (Spanish): the modules, the
order to read them in, and two requests followed from end to end.

## Setting up

You need **JDK 25** and **Docker** (the integration tests use Testcontainers).
Maven is not needed: the repository ships the wrapper.

```shell
cd backend
./mvnw verify          # Windows: mvnw.cmd verify
```

- To run the app while developing, start `TestKopiApplication` (in
  `backend/kopi-bootstrap/src/test`) from your IDE: it brings up a throwaway
  PostgreSQL.
- The bots `BISHOP`, `ROOK` and `QUEEN`, the analysis and some tests need
  **Stockfish 19**: download it from <https://stockfishchess.org> and point
  `KOPI_STOCKFISH` to the binary (or have `stockfish` on your `PATH`).
  `tools/` is ignored by Git, a handy place for it. Without it, the Stockfish
  adapter's tests are skipped, but the end-to-end tests in `kopi-bootstrap`
  (`GameApiIT`, `AnalysisIT`) fail.
- The slow tests (deep *perft*) and the browser test run in CI; locally add
  `-Pslow-tests`. The browser test, `PlayAndReviewE2EIT`, plays, resigns,
  reviews and deletes a game in Chromium through Playwright; on its own:
  `./mvnw -pl kopi-bootstrap -am verify -Pe2e`. Playwright downloads the
  browser the first time, and leaves a trace of each run in
  `kopi-bootstrap/target/playwright/` (open it at
  <https://trace.playwright.dev>).

## How a change goes in

1. **A branch per change**, from `main`: `feat/delete-game`,
   `fix/31-castling-rights` (with the issue number if there is one),
   `docs/…`, `build/…`.
2. **Commits** follow [Conventional Commits](https://www.conventionalcommits.org):
   `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `build:`, `ci:`, with an
   optional scope (`feat(web): …`). The subject says what changes for the
   person using it; the body, why. [`CHANGELOG.md`](CHANGELOG.md) is
   generated from these subjects, so write them for its readers.
3. **A pull request** to `main`, filling in the template. CI must be green:
   build, all tests, the ArchUnit rules and the Docker image.
4. The maintainer reviews it and merges it with a **merge commit**, so your
   commits stay as they are: make each one make sense on its own.

## Definition of done

A change is done when:

- `./mvnw verify` passes locally and in CI.
- It has tests at the right level: the domain with unit tests, the use cases
  with in-memory ports, an adapter with integration tests (PostgreSQL,
  Stockfish or MockMvc).
- The ArchUnit rules still pass: the domain depends on nothing, the use cases
  only on the domain, adapters never on each other.
- There is no `TODO` without an issue.
- Every new text a person reads exists in English and in Spanish (the
  `messages*.properties` files). `MessageBundlesTest` and
  `TemplatesAreTranslatedTest` check it.
- A relevant design decision has its ADR (see below).
- The README or the docs reflect it, if it changes how Kopi Chess is used.

## Writing code

- **Match the code around you**: its naming, its idioms and how much it
  comments. Documentation comments use Markdown (`///`).
- **The domain stays plain Java.** No Spring, no persistence, nothing outside
  `kopi-domain` itself.
- **No chess rules in JavaScript**: the browser only shows the board and sends
  moves; the server decides.
- **New dependencies** need a reason in the pull request (and usually an
  issue first).

## Writing tests

- Descriptive names, in English, that read as a sentence:
  `blackCannotCastleKingsideThroughAttackedSquare()`.
- *Given / When / Then* in the body, separated by blank lines.
- Positions are always literal **FEN**, never built piece by piece.
- The use cases are tested with the in-memory ports already in the test
  sources (`InMemoryGameRepository`, `ScriptedBot`…).

## Architecture Decision Records

A decision that shapes the code (a library, a data model, a trade-off someone
will ask about later) gets a short record in `docs/adr/NNN-title.md`, in
Spanish, with this shape:

```markdown
# ADR-NNN: Título

- **Estado:** propuesto | aceptado | sustituido por ADR-XXX
- **Fecha:** AAAA-MM-DD

## Contexto
Qué problema hay y qué restricciones existen.

## Decisión
Qué se decide, en una o dos frases.

## Consecuencias
Qué se gana, qué se pierde, qué queda pendiente.
```

If you are not comfortable writing Spanish, write it in English in the pull
request and it will be translated.

## Releases (maintainers)

1. A pull request that sets the release version in the `pom.xml` files and
   regenerates the changelog with git-cliff (`cliff.toml`; through Docker,
   nothing to install):

   ```shell
   docker run --rm -v "$PWD":/app -w /app orhunp/git-cliff:2.14.2 --tag vX.Y.Z -o CHANGELOG.md
   ```

2. Once merged, the tag `vX.Y.Z` on `main`. `release.yml` checks that
   `CHANGELOG.md` has that version, then publishes the image to GHCR.
3. The GitHub release, with the highlights in a few lines; the full list is
   the changelog.

## License

Kopi Chess is [GPL-3.0-or-later](LICENSE). By contributing, you agree that
your contribution is licensed the same way.
