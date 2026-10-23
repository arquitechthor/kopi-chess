# Chessground 9.2.1 (vendored)

The interactive board of [Lichess](https://github.com/lichess-org/chessground),
copied here so Kopi Chess runs without internet or a CDN (design §11.2).

- **Source:** npm package `chessground@9.2.1`,
  `https://registry.npmjs.org/chessground/-/chessground-9.2.1.tgz`
- **Integrity (npm):** `sha512-KIdxm0zD69e62XRwdnFB0XUNYSzn5HM3pMazRCwpfruRUowFI8y+1OAK5YSyBff29myeV1/4o5Q8T191+Irbog==`, checked
  when it was copied.
- **Licence:** GPL-3.0-or-later (see `LICENSE`), compatible with Kopi Chess.
  The piece set in `chessground.cburnett.css` is Colin M. L. Burnett's,
  under GPLv2 or later.
- **Files kept, unmodified:** `dist/chessground.min.js` (a single ES module
  with no imports and no network access), `assets/chessground.base.css`,
  `assets/chessground.cburnett.css`. The board colours are Kopi's own, in
  `css/kopi.css`.

To upgrade: download the new version from npm, check its integrity against
the registry, copy the same files into a new `chessground-<version>/` folder
and point `js/board.js` and the game template at it.
