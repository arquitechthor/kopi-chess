/// Post-game analysis: how good each move of a finished game was.
///
/// Pure arithmetic over the engine's evaluations (design §8): an engine
/// scores each position, and this package turns those scores into win
/// chances, classifies every move ([MoveClassifier]) and sums the game up
/// ([AnalysisSummary]). Talking to the engine is an adapter's job; nothing
/// here searches a position.
package com.arquitechthor.kopi.domain.analysis;
