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
 * new #board-state, the bot's reply included). Between two people at this
 * computer the server also turns the board (data-orientation), so each one
 * plays from their own side. The board then slides the
 * bot's piece from where it was, and the page is never reloaded. If that
 * fails for any reason, the move form is submitted the classic way
 * (Post/Redirect/Get), which works without this script too.
 *
 * Two people here also have, under the board, *Undo* and *Redo* (sent the
 * same way, so the piece slides back) and a lock that keeps the board from
 * turning: while it is on, the board stays as it was when it was locked,
 * whatever data-orientation says. The lock is remembered in this browser
 * only (localStorage), with the side that stays at the bottom.
 */
import { Chessground } from '../vendor/chessground-9.2.1/chessground.min.js';

const element = document.getElementById('board');

if (element) {
  const form = document.getElementById('move-form');
  const promotion = document.getElementById('promotion');

  /** The current #board-state, read afresh each time (the panel is replaced). */
  const state = () => document.getElementById('board-state').dataset;

  // The lock on the board's turning (two people here only): the side kept
  // at the bottom, or null while the board turns after every move.
  const LOCK_KEY = 'kopi.lockOrientation';
  const lock = document.getElementById('lock-orientation');
  const storage = {
    get() {
      try { return localStorage.getItem(LOCK_KEY); } catch { return null; }
    },
    set(value) {
      try {
        if (value) localStorage.setItem(LOCK_KEY, value);
        else localStorage.removeItem(LOCK_KEY);
      } catch {
        // no storage: the lock lasts as long as the page
      }
    },
  };
  let locked = null;
  if (lock) {
    const remembered = storage.get();
    locked = remembered === 'white' || remembered === 'black' ? remembered : null;
    lock.checked = locked !== null;
    lock.closest('label').hidden = false;
  }

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
      orientation: locked ?? data.orientation,
      turnColor: data.turn,
      lastMove: data.lastMove ? [data.lastMove.slice(0, 2), data.lastMove.slice(2, 4)] : undefined,
      check: data.check === 'true',
      movable: {
        color: data.movable === 'true' ? data.movableColor : undefined,
        dests: destinations(JSON.parse(data.legalMoves)),
      },
    };
  }

  const board = Chessground(element, {
    ...config(),
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

  const undoButton = document.querySelector('[data-undo]');
  const redoButton = document.querySelector('[data-redo]');

  /** The board, the version of every form and the undo/redo buttons, after the panel changed. */
  function refreshed() {
    // Setting the new position animates every piece that changed square:
    // the bot's piece slides from its origin to its destination.
    board.set(config());
    const version = state().version;
    for (const input of document.querySelectorAll('input[name="version"]')) input.value = version;
    if (undoButton) undoButton.disabled = state().canUndo !== 'true';
    if (redoButton) redoButton.disabled = state().canRedo !== 'true';
  }

  /** Posts `params` to `url` and puts the answer in place of the panel; false if that failed. */
  async function post(url, params) {
    try {
      const response = await fetch(url, {
        method: 'POST',
        body: new URLSearchParams({ ...params, version: state().version }),
        headers: { Accept: 'text/html' },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      document.getElementById('game-live').outerHTML = await response.text();
      refreshed();
      return true;
    } catch {
      return false;
    }
  }

  /** Sends a move without reloading the page; falls back to the form. */
  async function send(uci) {
    board.set({ movable: { color: undefined } }); // no second move while waiting
    const status = document.querySelector('.game-status');
    if (status) status.textContent = state().thinking;
    if (!(await post(form.dataset.liveAction, { uci }))) {
      form.elements.uci.value = uci;
      form.submit();
    }
  }

  // Undo and redo, in the background; the form the classic way if that fails.
  for (const button of [undoButton, redoButton]) {
    const undoForm = button?.form;
    if (!undoForm) continue;
    undoForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      undoButton.disabled = true;
      redoButton.disabled = true;
      board.set({ movable: { color: undefined } });
      if (!(await post(undoForm.dataset.liveAction, {}))) undoForm.submit();
    });
  }

  // The lock: on, the board keeps the side now at the bottom; off, it
  // turns to the side to move again.
  if (lock) {
    lock.addEventListener('change', () => {
      locked = lock.checked ? board.state.orientation : null;
      storage.set(locked);
      board.set(config());
    });
  }

  // Online: when the relay says the friend has moved (or offered a draw,
  // or resigned), fetch the part of the page that changes, as after a move.
  const events = element.dataset.events;
  if (events) {
    const source = new EventSource(events);
    source.addEventListener('changed', async () => {
      try {
        const response = await fetch(element.dataset.live, { headers: { Accept: 'text/html' } });
        if (!response.ok) return;
        document.getElementById('game-live').outerHTML = await response.text();
        refreshed();
      } catch {
        // the next notification, or a reload, will catch up
      }
    });
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
