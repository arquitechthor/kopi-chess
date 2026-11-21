import { describe, expect, it } from 'vitest';
import { destinations, lastMoveKeys, movePairs, outcomeMessage, promotionPieces } from './board';

describe('destinations', () => {
  it('groups the legal moves by their starting square', () => {
    const dests = destinations(['e2e3', 'e2e4', 'g1f3', 'g1h3']);
    expect([...dests]).toEqual([
      ['e2', ['e3', 'e4']],
      ['g1', ['f3', 'h3']],
    ]);
  });

  it('lists a promotion square once', () => {
    expect(destinations(['a7a8q', 'a7a8r', 'a7a8b', 'a7a8n']).get('a7')).toEqual(['a8']);
  });
});

describe('promotionPieces', () => {
  const moves = ['a7a8n', 'a7a8q', 'a7a8b', 'a7a8r', 'a7b8q', 'e2e4'];

  it('offers the pieces of that promotion, queen first', () => {
    expect(promotionPieces(moves, 'a7', 'a8')).toEqual(['q', 'r', 'b', 'n']);
  });

  it('is empty for an ordinary move', () => {
    expect(promotionPieces(moves, 'e2', 'e4')).toEqual([]);
  });
});

describe('lastMoveKeys', () => {
  it('highlights the two squares of the last move', () => {
    expect(lastMoveKeys({ lastMove: { ply: 1, uci: 'e7e8q', san: 'e8=Q' } })).toEqual(['e7', 'e8']);
    expect(lastMoveKeys({ lastMove: null })).toBeUndefined();
  });
});

describe('movePairs', () => {
  it('numbers the moves in pairs', () => {
    const moves = ['e4', 'e5', 'Nf3'].map((san, i) => ({ ply: i + 1, uci: '', san }));
    expect(movePairs(moves)).toEqual(['1. e4 e5', '2. Nf3']);
  });
});

describe('outcomeMessage', () => {
  it('tells the person whether they won, lost or drew', () => {
    expect(outcomeMessage({ humanColor: 'WHITE', outcome: { termination: 'CHECKMATE', winner: 'WHITE' } })).toEqual({
      key: 'outcome.youWin',
      termination: 'termination.CHECKMATE',
    });
    expect(outcomeMessage({ humanColor: 'WHITE', outcome: { termination: 'RESIGNATION', winner: 'BLACK' } })?.key).toBe('outcome.youLose');
    expect(outcomeMessage({ humanColor: 'BLACK', outcome: { termination: 'STALEMATE', winner: null } })?.key).toBe('outcome.draw');
  });

  it('has a sentence for terminations it does not know', () => {
    expect(outcomeMessage({ humanColor: 'WHITE', outcome: { termination: 'NEW_ONE', winner: null } })?.termination).toBe('termination.other');
  });

  it('is null while the game goes on', () => {
    expect(outcomeMessage({ humanColor: 'WHITE', outcome: null })).toBeNull();
  });
});
