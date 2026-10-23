/**
 * The game board: Chessground, fed with what the server wrote into the
 * #board-state element of the page.
 *
 * No chess rules here (design §11.3). The server says where each piece may
 * go (data-legal-moves, in UCI); this script only lets the person drag or
 * click one of those moves and sends it.
 *
 * The move is sent in the background (fetch to …/live-moves): the server
 * answers with the part of the page that changes (status, move list and a
 * new #board-state, the bot's reply included). The board then slides the
 * bot's piece from where it was, and the page is never reloaded. If that
 * fails for any reason, the move form is submitted the classic way
 * (Post/Redirect/Get), which works without this script too.
 */
import { Chessground } from '../vendor/chessground-9.2.1/chessground.min.js';

const element = document.getElementById('board');

if (element) {
  const form = document.getElementById('move-form');
  const promotion = document.getElementById('promotion');

  /** The current #board-state, read afresh each time (the panel is replaced). */
  const state = () => document.getElementById('board-state').dataset;

  /** Chessground's destinations from the legal moves in UCI: origin -> targets. */
  function destinations(legalMoves) {
    const dests = new Map();
    for (const uci of legalMoves) {
      const from = uci.slice(0, 2);
      const to = uci.slice(2, 4);
      const targets = dests.get(from) ?? [];
      if (!targets.includes(to)) targets.push(to);
      dests.set(from, targets);
    }
    return dests;
  }

  /** The board configuration for the current state. */
  function config() {
    const data = state();
    return {
      fen: data.fen.split(' ')[0],
      turnColor: data.turn,
      lastMove: data.lastMove ? [data.lastMove.slice(0, 2), data.lastMove.slice(2, 4)] : undefined,
      check: data.check === 'true',
      movable: {
        color: data.movable === 'true' ? element.dataset.orientation : undefined,
        dests: destinations(JSON.parse(data.legalMoves)),
      },
    };
  }

  const board = Chessground(element, {
    ...config(),
    orientation: element.dataset.orientation,
    coordinates: true,
    // A little slower than the default, so the bot's reply is easy to follow.
    animation: { enabled: true, duration: 320 },
    premovable: { enabled: false },
    movable: {
      ...config().movable,
      free: false,
      showDests: true,
      events: { after: (from, to) => chosen(from, to) },
    },
  });

  /** Sends a move without reloading the page; falls back to the form. */
  async function send(uci) {
    board.set({ movable: { color: undefined } }); // no second move while waiting
    const status = document.querySelector('.game-status');
    if (status) status.textContent = state().thinking;
    try {
      const response = await fetch(form.dataset.liveAction, {
        method: 'POST',
        body: new URLSearchParams({ uci, version: state().version }),
        headers: { Accept: 'text/html' },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      document.getElementById('game-live').outerHTML = await response.text();
      // Setting the new position animates every piece that changed square:
      // the bot's piece slides from its origin to its destination.
      board.set(config());
      form.elements.version.value = state().version;
    } catch {
      form.elements.uci.value = uci;
      form.submit();
    }
  }

  function chosen(from, to) {
    const moves = JSON.parse(state().legalMoves).filter((uci) => uci.startsWith(from + to));
    if (moves.length === 1) {
      send(moves[0]);
      return;
    }
    // Several legal moves from one square to another can only be a
    // promotion: ask which piece. Escape or a click outside cancels.
    let piece = null;
    promotion.onclick = (event) => {
      piece = event.target.closest('button')?.dataset.piece ?? null;
      promotion.close();
    };
    promotion.onclose = () => (piece ? send(from + to + piece) : board.set(config()));
    promotion.showModal();
  }
}
