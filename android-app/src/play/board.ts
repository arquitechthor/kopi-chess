// What the board needs from a game, worked out from the PC's answer. The app
// knows no chess rules: legal moves, check and the result all come from the
// PC (docs/plan-app-android.md §2).

import type { Key } from 'chessground/types';
import type { MessageKey } from '../i18n';
import type { Color, Game, PlayedMove } from '../remote/model';

export type CgColor = 'white' | 'black';

export function cgColor(color: Color): CgColor {
  return color === 'WHITE' ? 'white' : 'black';
}

/** Chessground's map from each square to the squares its piece may go to. */
export function destinations(legalMoves: readonly string[]): Map<Key, Key[]> {
  const dests = new Map<Key, Key[]>();
  for (const uci of legalMoves) {
    const from = uci.slice(0, 2) as Key;
    const to = uci.slice(2, 4) as Key;
    const targets = dests.get(from) ?? [];
    if (!targets.includes(to)) {
      targets.push(to);
    }
    dests.set(from, targets);
  }
  return dests;
}

export type PromotionPiece = 'q' | 'r' | 'b' | 'n';
const PROMOTION_ORDER: readonly PromotionPiece[] = ['q', 'r', 'b', 'n'];

/** The pieces a pawn may promote to on from→to, best first; empty when it is no promotion. */
export function promotionPieces(legalMoves: readonly string[], from: string, to: string): PromotionPiece[] {
  const found = new Set(legalMoves.filter((uci) => uci.length === 5 && uci.startsWith(from + to)).map((uci) => uci[4]));
  return PROMOTION_ORDER.filter((piece) => found.has(piece));
}

/** The last move as Chessground highlights it. */
export function lastMoveKeys(game: Pick<Game, 'lastMove'>): Key[] | undefined {
  const uci = game.lastMove?.uci;
  return uci ? [uci.slice(0, 2) as Key, uci.slice(2, 4) as Key] : undefined;
}

export function isOver(game: Pick<Game, 'status' | 'outcome'>): boolean {
  return game.status !== 'IN_PROGRESS' || game.outcome !== null;
}

/** The moves in pairs, for the list under the board: "1. e4 e5", "2. Nf3"… */
export function movePairs(moves: readonly PlayedMove[]): string[] {
  const lines: string[] = [];
  for (const move of moves) {
    const number = Math.ceil(move.ply / 2);
    if (move.ply % 2 === 1) {
      lines.push(`${number}. ${move.san}`);
    } else if (lines.length === 0) {
      lines.push(`${number}… ${move.san}`);
    } else {
      lines[lines.length - 1] += ` ${move.san}`;
    }
  }
  return lines;
}

const TERMINATIONS = [
  'CHECKMATE',
  'RESIGNATION',
  'STALEMATE',
  'INSUFFICIENT_MATERIAL',
  'THREEFOLD_REPETITION',
  'FIFTY_MOVE_RULE',
  'DRAW_AGREEMENT',
  'INACTIVITY',
] as const;

/** How the game ended, from the person's side: the sentence's key and its argument (the termination's key). */
export function outcomeMessage(game: Pick<Game, 'outcome' | 'humanColor'>): { key: MessageKey; termination: MessageKey } | null {
  const outcome = game.outcome;
  if (!outcome) {
    return null;
  }
  const termination: MessageKey = (TERMINATIONS as readonly string[]).includes(outcome.termination)
    ? (`termination.${outcome.termination}` as MessageKey)
    : 'termination.other';
  if (outcome.winner === null) {
    return { key: 'outcome.draw', termination };
  }
  return { key: outcome.winner === game.humanColor ? 'outcome.youWin' : 'outcome.youLose', termination };
}
