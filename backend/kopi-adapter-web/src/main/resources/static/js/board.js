/**
 * The game board: Chessground, fed with what the server wrote into the
 * board's data-* attributes.
 *
 * No chess rules here (design §11.3). The server says where each piece may
 * go (data-legal-moves, in UCI); this script only lets the person drag or
 * click one of those moves and submits it in the move form. The server
 * answers with the game page again, the bot's reply included
 * (Post/Redirect/Get).
 */
import { Chessground } from '../vendor/chessground-9.2.1/chessground.min.js';

const element = document.getElementById('board');

if (element) {
  const data = element.dataset;
  const placement = data.fen.split(' ')[0];
  const legalMoves = JSON.parse(data.legalMoves);
  const lastMove = data.lastMove ? [data.lastMove.slice(0, 2), data.lastMove.slice(2, 4)] : undefined;

  // Chessground wants the destinations as a Map: origin square -> target squares.
  const dests = new Map();
  for (const uci of legalMoves) {
    const from = uci.slice(0, 2);
    const to = uci.slice(2, 4);
    const targets = dests.get(from) ?? [];
    if (!targets.includes(to)) targets.push(to);
    dests.set(from, targets);
  }

  const form = document.getElementById('move-form');
  const promotion = document.getElementById('promotion');

  const board = Chessground(element, {
    fen: placement,
    orientation: data.orientation,
    turnColor: data.turn,
    lastMove,
    check: data.check === 'true',
    coordinates: true,
    premovable: { enabled: false },
    movable: {
      free: false,
      color: data.movable === 'true' ? data.orientation : undefined,
      dests,
      showDests: true,
      events: { after: (from, to) => chosen(from, to) },
    },
  });

  /** Sends a move and locks the board while the page reloads. */
  function send(uci) {
    board.set({ movable: { color: undefined } });
    form.elements.uci.value = uci;
    form.submit();
  }

  /** Puts the pieces back as the server sent them (a cancelled promotion). */
  function restore() {
    board.set({ fen: placement, lastMove });
  }

  function chosen(from, to) {
    const moves = legalMoves.filter((uci) => uci.startsWith(from + to));
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
    promotion.onclose = () => (piece ? send(from + to + piece) : restore());
    promotion.showModal();
  }
}
