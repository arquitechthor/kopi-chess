/// The `Game` aggregate: a game of chess from its first position to its
/// result, and the rules about who may do what and when.
///
/// The rules of chess themselves (moves, check, FEN, SAN) live in
/// [com.arquitechthor.kopi.domain.chess]; this package uses them and adds
/// what a *game* needs: turns, history, endings and versions.
package com.arquitechthor.kopi.domain.game;
