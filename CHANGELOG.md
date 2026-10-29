# Changelog

Every change to Kopi Chess, release by release, newest first. It is generated
from the commit messages by [git-cliff](https://git-cliff.org) (`cliff.toml`);
each release's highlights are in its
[GitHub release](https://github.com/arquitechthor/kopi-chess/releases).

## Unreleased

### Features

- **review:** The move's verdict as a badge on the board ([d80380d](https://github.com/arquitechthor/kopi-chess/commit/d80380da9fd25f4367b11ade571b547a43c89dc3))
- **metrics:** Micrometer metrics, Prometheus endpoint and Grafana profile ([f0b3b87](https://github.com/arquitechthor/kopi-chess/commit/f0b3b87e72d7f6dcf2f165250d1ca7ff47abdd00))
- **web:** Link Kopi Chess in the shared footer ([ea210b0](https://github.com/arquitechthor/kopi-chess/commit/ea210b0959fb77265d1b50af784f5ff8d04ad219))
- **logs:** JSON logs with the game's id, and a UCI trace ([de38c7f](https://github.com/arquitechthor/kopi-chess/commit/de38c7f8d806aeee14ff275234ab4b4364a10778))
- **docker:** Java 25 AOT cache in the image, startup halved ([6a721fa](https://github.com/arquitechthor/kopi-chess/commit/6a721fa2e56bf5fe4a6ba2448c621030a2aff1db))

### Fixes

- **stockfish:** Start every analysed position from a clean engine ([e526bd0](https://github.com/arquitechthor/kopi-chess/commit/e526bd0d31d5102b6ad582825546b211c2b3fa4f))

### Documentation

- Analysis on a modest machine (half a core) ([d6f92bf](https://github.com/arquitechthor/kopi-chess/commit/d6f92bf04fa1af6767ebf9ccb598f7a3dc28863a))

### Tests

- **web:** Expect Kopi Chess in the shared footer line ([bb10f08](https://github.com/arquitechthor/kopi-chess/commit/bb10f08ea486eac2830adec7a3368123c3ad7214))
- **e2e:** Play, resign, review and delete a game in a real browser ([84d47f1](https://github.com/arquitechthor/kopi-chess/commit/84d47f1ddd6a4aa05c35009ea73a61a0d9083208))

### Build and dependencies

- Start 1.0.0-SNAPSHOT, the rest of phase 7 ([7417f53](https://github.com/arquitechthor/kopi-chess/commit/7417f53227456006d592695687a6ec4720909d8f))
- Bump org.jacoco:jacoco-maven-plugin ([54bd9a4](https://github.com/arquitechthor/kopi-chess/commit/54bd9a467e4e2aec6096013f3277c5ba38756d2e))

### Continuous integration

- Bump actions/upload-artifact from 4 to 7 ([7511b9e](https://github.com/arquitechthor/kopi-chess/commit/7511b9e32ea721e92450ac30d4465e7b24918dc2))
- Bump actions/checkout from 5 to 7 ([7a78d6a](https://github.com/arquitechthor/kopi-chess/commit/7a78d6ae07e6b8937a2162df022de3ad412bb3ef))
- Bump docker/metadata-action from 5 to 6 ([d6f9624](https://github.com/arquitechthor/kopi-chess/commit/d6f962472255afaf1d430976638174c201d36f38))

## [0.7.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.7.0) - 2026-10-28

### Features

- **web:** Bot avatars, and the record against each bot as a small table ([c62e1ec](https://github.com/arquitechthor/kopi-chess/commit/c62e1ec7f29a45d6e0458762c086095fd04915f5))
- **web:** One bot per row on the new-game screen, using the page's width ([43878ba](https://github.com/arquitechthor/kopi-chess/commit/43878ba35043fcf79fb1a467f8a53e6dc277c71d))
- Delete a game from its pages, and DELETE /api/v1/games/{id} ([8037ced](https://github.com/arquitechthor/kopi-chess/commit/8037cedff1b7cce019fa70dd18d278e36ad8aa65))
- Answer only this machine, and listen on localhost by default ([303ec60](https://github.com/arquitechthor/kopi-chess/commit/303ec601b8cf7c394aa02bd74851367b927db2d5))

### Fixes

- **web:** The review's moves no longer break in two or scroll sideways ([227469a](https://github.com/arquitechthor/kopi-chess/commit/227469a36cb312ac7cd46242230916f50a251104))

### Documentation

- **plan:** Bot cards one per row, and the verdict badge on the review board ([d74e070](https://github.com/arquitechthor/kopi-chess/commit/d74e070830d4bae09405d6dc6edaaa12491316bb))
- **plan:** Delete a game from its page; bot cards done ([bd2aba1](https://github.com/arquitechthor/kopi-chess/commit/bd2aba1e4e7bcf71dfcda904e15738b62a955955))
- README with a screenshot and a three-line start ([4386051](https://github.com/arquitechthor/kopi-chess/commit/438605139efefc0c95df6f79fa64e2a2fb613aba))
- CONTRIBUTING.md and a guide to reading the code ([cdc52cf](https://github.com/arquitechthor/kopi-chess/commit/cdc52cf9f994bc7db8d914c4af3f5cc88c4737b7))
- ADR-010, the web UI with Thymeleaf and without htmx ([9f4254d](https://github.com/arquitechthor/kopi-chess/commit/9f4254d3cb515459760fce5bfc596b058819480b))

### Build and dependencies

- Start 0.7.0-SNAPSHOT for phase 7 ([5f4fc58](https://github.com/arquitechthor/kopi-chess/commit/5f4fc58f2cfc23d5f4688776b3a5ecc08cc9d081))
- Publish the image to GHCR on a version tag, for amd64 and arm64 ([9cb6d38](https://github.com/arquitechthor/kopi-chess/commit/9cb6d3840d38de011787e43967d8e3f70867311a))
- Bump the maven group in /backend with 5 updates ([720cf31](https://github.com/arquitechthor/kopi-chess/commit/720cf3153e7dc1a5d4f9559c4dc83e1101a6257c))
- Store mvnw.cmd with LF in the repository ([0dacd74](https://github.com/arquitechthor/kopi-chess/commit/0dacd743994277f8fc1d5ebfc40bbfb084b29d2c))

### Continuous integration

- Dependabot, weekly and grouped ([2741feb](https://github.com/arquitechthor/kopi-chess/commit/2741feb57ac496cb33b48a79cb863d652f106614))
- Bump docker/setup-buildx-action from 3 to 4 ([6d2c639](https://github.com/arquitechthor/kopi-chess/commit/6d2c639f64a6c3d845d36813ec9a61bb57ac0d89))
- Bump docker/login-action from 3 to 4 ([52d2cf2](https://github.com/arquitechthor/kopi-chess/commit/52d2cf2272aec3e970ed8384f4e0850330a1c27c))
- Bump actions/setup-java from 5 to 6 ([fb2fa28](https://github.com/arquitechthor/kopi-chess/commit/fb2fa282dbab55a2b142c7a8afb8feda3daea976))
- Bump docker/build-push-action from 6 to 7 ([45fcc2d](https://github.com/arquitechthor/kopi-chess/commit/45fcc2de09e5490f7a77e7a3991171275db1297c))
- Bump docker/setup-qemu-action from 3 to 4 ([0bd7122](https://github.com/arquitechthor/kopi-chess/commit/0bd712221b82bc5228afff4d03ae6565586be6b3))
- Dependabot does not propose new major Temurin versions ([77d9ba5](https://github.com/arquitechthor/kopi-chess/commit/77d9ba59fad90c22dacda37498b556bab141bb2f))

## [0.6.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.6.0) - 2026-10-26

### Features

- **domain:** Read a game from PGN ([a4ceb85](https://github.com/arquitechthor/kopi-chess/commit/a4ceb855ece1dbf639163b217a979f23063bcf53))
- Export a game as PGN ([8dc4dc7](https://github.com/arquitechthor/kopi-chess/commit/8dc4dc78517f8aa899bb25135f4b3d2b59e3874e))
- Import a finished game from PGN ([acf5796](https://github.com/arquitechthor/kopi-chess/commit/acf5796525c7efc5b567ce5fcf4244688b6c27d3))
- **web:** An "Import PGN" page ([1e55f08](https://github.com/arquitechthor/kopi-chess/commit/1e55f08124fb8f7cf813eca46ba6368fa201152d))
- The game history, filtered by origin and result ([e4bebca](https://github.com/arquitechthor/kopi-chess/commit/e4bebca57eca18bac858b09b8fbee2a2ea19d216))
- Your record against each bot, where you choose one ([12610cd](https://github.com/arquitechthor/kopi-chess/commit/12610cd14e359c18fe44fd73ffa68af98269b378))

### Build and dependencies

- Start 0.6.0-SNAPSHOT for phase 6 ([60ec437](https://github.com/arquitechthor/kopi-chess/commit/60ec43706fcc13d6aa01d74154b74d26d1d352e3))

## [0.5.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.5.0) - 2026-10-26

### Features

- **domain:** Classify moves and sum up a game by win chance ([bc6dc9d](https://github.com/arquitechthor/kopi-chess/commit/bc6dc9db55648bed652d9e26330f2c6e917ef20d))
- **openings:** A local opening book from lichess-org/chess-openings ([b77b33e](https://github.com/arquitechthor/kopi-chess/commit/b77b33ee737b0cd7659c075549317a3fa8e407f2))
- **web:** Office mode tells the sides apart by colour ([91980c4](https://github.com/arquitechthor/kopi-chess/commit/91980c45178f3ac8b951bbaca96f0cfec44c9966))
- **stockfish:** ChessEngine.analyse, on its own pool of engines ([ecf04a7](https://github.com/arquitechthor/kopi-chess/commit/ecf04a7fdfbcd54897691e0434449e1908e8ce64))
- **web:** Office mode without a key, and codes that fill the field ([d071254](https://github.com/arquitechthor/kopi-chess/commit/d07125463a0be3eab154abaafbaa306e6c54a2d5))
- **persistence:** Analysis results and a SKIP LOCKED job queue ([a6f75f5](https://github.com/arquitechthor/kopi-chess/commit/a6f75f50029fa3e883012aec25b17709e92954cd))
- Analyse finished games in the background ([9d97e16](https://github.com/arquitechthor/kopi-chess/commit/9d97e16cca0e82f7b5ce6c0b50546e3f0bb8bfd3))
- **rest:** Request, read and follow a game's analysis ([92f7250](https://github.com/arquitechthor/kopi-chess/commit/92f72507e7fe4c0f14d430fa2f91afd3d86671fe))
- **web:** The review screen of a finished game ([baecb9a](https://github.com/arquitechthor/kopi-chess/commit/baecb9a888fbb56f2bdde1cba537ed8cec91bb03))

### Build and dependencies

- Start 0.5.0-SNAPSHOT for phase 5 ([98b8aac](https://github.com/arquitechthor/kopi-chess/commit/98b8aacb17c854d63cd7a8469bb9313986476068))

## [0.4.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.4.0) - 2026-10-26

### Features

- **bots:** KNIGHT, a two-ply alpha-beta bot ([6eebd85](https://github.com/arquitechthor/kopi-chess/commit/6eebd85674a6d8e2e83dee9489703a3afd477861))
- **stockfish:** BISHOP, ROOK and QUEEN on a UCI engine pool ([3068a89](https://github.com/arquitechthor/kopi-chess/commit/3068a891240b12f3f2ec7ecad7c00aae2c49f351))
- **web:** Play without reloading, and animate the bot's reply ([4b29d7e](https://github.com/arquitechthor/kopi-chess/commit/4b29d7e447e4eddd888d143d3508067035aee32f))
- Stockfish in the Docker image, and its health indicator ([fa0c722](https://github.com/arquitechthor/kopi-chess/commit/fa0c72217385f157eb5d27dc557bb9b896b1a4b9))

### Fixes

- **bootstrap:** Make the bot router the primary BotPlayer ([7364da9](https://github.com/arquitechthor/kopi-chess/commit/7364da93c91b415c09219e7f4bdabb02654a2b05))
- The board fits the screen's height, without scrolling ([94ff524](https://github.com/arquitechthor/kopi-chess/commit/94ff5248d00dc193ae79f310dcbec121394b0ba0))

### Tests

- Calibrate the five bots, and retune the Stockfish profiles ([870f2ea](https://github.com/arquitechthor/kopi-chess/commit/870f2ea3c9b076c000ea9b486445ba34fd0a298e))

### Build and dependencies

- Start 0.4.0-SNAPSHOT for phase 4 ([784d6d2](https://github.com/arquitechthor/kopi-chess/commit/784d6d2c3316ad09b115d7c956f3002525edb97e))

## [0.3.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.3.0) - 2026-10-23

### Features

- **domain:** Game aggregate, GameOutcome and BotProfile ([6f999f6](https://github.com/arquitechthor/kopi-chess/commit/6f999f6259d9264883dcf7d254d702be0720fa90))
- **application:** Game use cases and their ports ([739bb87](https://github.com/arquitechthor/kopi-chess/commit/739bb87d73330d06a73b62706411c0a6ef07bde6))
- **bots:** PAWN, a weighted random bot ([cf97d2d](https://github.com/arquitechthor/kopi-chess/commit/cf97d2d1314186f4d390a3c889f54be9d373e8b9))
- **persistence:** Store games in PostgreSQL ([7a8124c](https://github.com/arquitechthor/kopi-chess/commit/7a8124c4e1717c660091f8ec0d472df6c24fcf59))
- **rest:** The games API under /api/v1 ([ef180f8](https://github.com/arquitechthor/kopi-chess/commit/ef180f881ca20af6e4ef164d6a71bcbde1fbc0cf))
- **bootstrap:** Wire the use cases and serve the games API ([ba99401](https://github.com/arquitechthor/kopi-chess/commit/ba99401acf80334afa3ea2b9ff395f69e777220b))
- **web:** Play against PAWN on a Chessground board ([c45e724](https://github.com/arquitechthor/kopi-chess/commit/c45e7243053eedb0dea4dff8d0815979eaefd676))
- **web:** Office mode, a text board and typed moves ([84a3151](https://github.com/arquitechthor/kopi-chess/commit/84a3151223b58490d591d03674029e32d0b32ad5))

### Documentation

- Fix the curl example in the Spanish README ([1f8d6e5](https://github.com/arquitechthor/kopi-chess/commit/1f8d6e52321f3ce4c607307bb1fc2243bb090caa))

### Tests

- **web:** Fail on untranslated text in the templates ([545f012](https://github.com/arquitechthor/kopi-chess/commit/545f012d0bec7caf775101b9b2ef6a2e430830c4))

### Build and dependencies

- Start 0.3.0-SNAPSHOT for phase 3 ([28398b1](https://github.com/arquitechthor/kopi-chess/commit/28398b1cd91472d92bf8afe4f651a30f0d2e46e6))

## [0.2.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.2.0) - 2026-10-23

### Features

- **domain:** Add basic chess types and ADR-004 ([a943403](https://github.com/arquitechthor/kopi-chess/commit/a9434037be3b7e21ebb5211be153936d07cf5cbe))
- **domain:** 0x88 board, immutable position and FEN ([4fb75c9](https://github.com/arquitechthor/kopi-chess/commit/4fb75c9dedccdd33e6f43d219637d0746a1b4b2d))
- **domain:** Generate knight and king moves on the 0x88 board ([d364f8c](https://github.com/arquitechthor/kopi-chess/commit/d364f8c526c996e1be6ff94845ce1349f02bc3ca))
- **domain:** Generate bishop, rook and queen moves ([f61ebc3](https://github.com/arquitechthor/kopi-chess/commit/f61ebc3e7f8891ca05e1df769ed5ee05b96f388b))
- **domain:** Generate pawn moves, promotions and en passant ([cc136d1](https://github.com/arquitechthor/kopi-chess/commit/cc136d179064ee38f7bfcbd29706d9ab459a75a6))
- **domain:** Detect attacked squares and generate castling ([32c0073](https://github.com/arquitechthor/kopi-chess/commit/32c0073b9ed70ed695e4171618074ae341ac812e))
- **domain:** Apply moves and filter legal moves ([3d7b628](https://github.com/arquitechthor/kopi-chess/commit/3d7b6284fc7db4fc7228c9a68039c322217c581b))
- **domain:** Perft count and divide, verified on six reference positions ([c3288f3](https://github.com/arquitechthor/kopi-chess/commit/c3288f3433e6b993639b71188abb852b38ecf4c8))
- **web:** Adopt the visual identity of the family's sites ([fa692e0](https://github.com/arquitechthor/kopi-chess/commit/fa692e0334887eef8827747040a6cf2a12a9d7eb))
- **domain:** Write and read moves in SAN ([827fc2e](https://github.com/arquitechthor/kopi-chess/commit/827fc2eb49c1acb862f2d38778b25a6c985429f5))
- **domain:** Game-ending rules, Zobrist hash and ADR-008 ([243c920](https://github.com/arquitechthor/kopi-chess/commit/243c920fbe6d7a802c948c7ed685636c8ea638e4))

### Documentation

- Add office mode to the design and the plan ([9008606](https://github.com/arquitechthor/kopi-chess/commit/90086069402fd21fc7da36832cc0bb54a36e4946))

### Tests

- **domain:** Differential test against Stockfish ([5782dd6](https://github.com/arquitechthor/kopi-chess/commit/5782dd651a375b955b5c0ab304a8b14edebab825))

### Build and dependencies

- Start 0.2.0-SNAPSHOT for phase 2 ([30ee951](https://github.com/arquitechthor/kopi-chess/commit/30ee95170fb43860a9b76395bfe7af3a3e60daa7))

### Continuous integration

- Download a pinned, checksum-verified Stockfish instead of apt ([1097678](https://github.com/arquitechthor/kopi-chess/commit/10976782c863a168f6dacfe2f5bd9de67364f581))

## [0.1.0](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.1.0) - 2026-10-21

### Features

- Run Kopi Chess on Spring Boot with PostgreSQL and Flyway ([fb296b3](https://github.com/arquitechthor/kopi-chess/commit/fb296b3ba2d73089e66cbc681c57f0bea535e62f))
- **web:** Bilingual home page with Thymeleaf and a language switch ([571a101](https://github.com/arquitechthor/kopi-chess/commit/571a101b95026c536ca5ae94af03954056477b02))
- **labs:** Add kopi-labs module for Java preview features ([14f04c5](https://github.com/arquitechthor/kopi-chess/commit/14f04c5c88f98f52ed6fc18e30e53580b35e514f))

### Documentation

- Record branch protection on main in the plan ([bc35287](https://github.com/arquitechthor/kopi-chess/commit/bc352877771a5d8ca88e954df0d555047a56c6b0))

### Build and dependencies

- Add hexagonal Maven multi-module skeleton ([f11f9dc](https://github.com/arquitechthor/kopi-chess/commit/f11f9dcf66f588bdefe42c396a7e4c3100271e7f))

## [0.0.1](https://github.com/arquitechthor/kopi-chess/releases/tag/v0.0.1) - 2026-10-21

### Documentation

- Add Kopi Chess technical design and development plan ([0e76094](https://github.com/arquitechthor/kopi-chess/commit/0e7609402d5a22708f342b7dc573c685cd60e92d))
- Remove original Chess Arena proposal ([d2f4817](https://github.com/arquitechthor/kopi-chess/commit/d2f48175600aa6d5353fd66bafff6b844377f3bc))
- Record Stockfish 19 UCI session notes ([1f57c36](https://github.com/arquitechthor/kopi-chess/commit/1f57c36925cc62c12fd9c44d5bb042328d6d331d))

### Other

- Set up phase 0 project conventions ([1dc8630](https://github.com/arquitechthor/kopi-chess/commit/1dc86303e0f114a3d3f1c095094e0b3a348e3d6b))

<!-- generated by git-cliff from cliff.toml: do not edit by hand -->
