# Opening names (vendored)

The named openings of [lichess-org/chess-openings](https://github.com/lichess-org/chess-openings),
copied here so Kopi Chess names openings without internet (design §9, ADR-009).

- **Source:** `a.tsv` … `e.tsv` at commit
  [`a6189a30dc273ccb21fc2536a9a2fefd5592a67a`](https://github.com/lichess-org/chess-openings/tree/a6189a30dc273ccb21fc2536a9a2fefd5592a67a)
  (2026-10-06), from `https://raw.githubusercontent.com/lichess-org/chess-openings/<commit>/<file>`.
- **SHA-256:**
  - `a.tsv` `3282e4c9155289a29224f9a85fba0decb46efa35c2fa5e39662d2ac48fb0f793`
  - `b.tsv` `1d5ed134ebbd87915ead5683416e37583bb3e5ae3582b4d8cb5cb4aa7ef4f623`
  - `c.tsv` `45e843c7b7e79a0d04d5bda879c9bd23f5a10974488bd8615e4f4c5d0749986f`
  - `d.tsv` `1cf4057a8aece94f27bb3f278ad410ee2844831a642364eeceaafa091dbcceda`
  - `e.tsv` `48c6f6645b031a5937c8386839debab0e2e48837251687ba1afb6369677f8546`
- **Licence:** CC0 1.0 (public domain). Credited in `NOTICE` anyway.
- **Files kept, unmodified:** the five source files (`eco`, `name`, `pgn`),
  3,865 openings. Not the generated `dist/` version: Kopi replays each
  line with its own rules to find the position, so it needs no `epd`
  column.

To upgrade: download the five files at a newer commit, update the commit
and checksums here and in `NOTICE`, and run `LichessOpeningBookTest`. It
replays every line and expects a fixed number of openings, so update that
count too.
