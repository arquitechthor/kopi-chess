/**
 * The review page (plan §8.5). Two jobs, depending on what the page shows:
 *
 * - While the analysis runs: follow it through the API's Server-Sent Events
 *   (GET /api/v1/games/{id}/analysis/events) and fill the progress bar;
 *   when it completes or fails, reload the page, which then shows the
 *   result or the reason.
 * - Once done: step through the game on a read-only board, with the
 *   engine's choice drawn as an arrow and the move's verdict as a badge on
 *   its square, and show the verdict on each move.
 *   The positions come from the server (#review-state); no chess rules here
 *   (design §11.3).
 */
import { Chessground } from '../vendor/chessground-9.2.1/chessground.min.js';

const progress = document.getElementById('analysis-progress');
if (progress) {
  const text = document.getElementById('analysis-progress-text');
  const events = new EventSource(progress.dataset.events);
  events.addEventListener('progress', (event) => {
    const { analysedPositions, totalPositions } = JSON.parse(event.data);
    progress.max = totalPositions;
    progress.value = analysedPositions;
    text.textContent = progress.dataset.progressText
      .replace('{0}', analysedPositions)
      .replace('{1}', totalPositions);
  });
  const reload = () => {
    events.close();
    window.location.reload();
  };
  events.addEventListener('completed', reload);
  events.addEventListener('failed', reload);
}

const element = document.getElementById('board');
if (element) {
  const steps = JSON.parse(document.getElementById('review-state').dataset.steps);
  const details = [...document.querySelectorAll('.review-detail')];
  const moves = [...document.querySelectorAll('.review-move')];
  const markers = [...document.querySelectorAll('.eval-graph-marker')];

  const board = Chessground(element, {
    orientation: element.dataset.orientation,
    viewOnly: true,
    coordinates: true,
    animation: { enabled: true, duration: 200 },
    drawable: {
      enabled: false,
      visible: true,
      // Kopi's cyan for the engine's choice.
      brushes: { engine: { key: 'k', color: '#22d3ee', opacity: 0.85, lineWidth: 10 } },
    },
  });

  let current = 0;

  /**
   * The verdict on move `index` as a badge in a corner of the square it
   * went to. Glyph and colour are the move list's (kopi.css `.cat-*`),
   * read from its entry for this move: one place defines them.
   */
  function verdict(index) {
    const lastMove = steps[index].lastMove;
    const glyph = moves.find((move) => Number(move.dataset.ply) === index)?.querySelector('.cat');
    if (!lastMove || !glyph) return [];
    const category = [...glyph.classList].find((name) => name.startsWith('cat-'));
    const text = getComputedStyle(glyph, '::before').content.replace(/^"|"$/g, '');
    // One square is 100 × 100 here.
    const html = `<g class="${category}">`
      + '<circle cx="78" cy="22" r="19" style="fill: var(--bg); fill-opacity: 0.9; stroke: currentColor; stroke-width: 4"/>'
      + '<text x="78" y="22" text-anchor="middle" dominant-baseline="central" font-size="21" font-weight="800"'
      + ` style="fill: currentColor">${text}</text></g>`;
    return [{ orig: lastMove.slice(2, 4), customSvg: { html } }];
  }

  /** Shows step `index`: 0 is the start, n is the position after move n. */
  function show(index) {
    current = Math.max(0, Math.min(steps.length - 1, index));
    const step = steps[current];
    board.set({
      fen: step.fen.split(' ')[0],
      lastMove: step.lastMove ? [step.lastMove.slice(0, 2), step.lastMove.slice(2, 4)] : undefined,
    });
    board.setAutoShapes([
      ...(step.bestMove
        ? [{ orig: step.bestMove.slice(0, 2), dest: step.bestMove.slice(2, 4), brush: 'engine' }]
        : []),
      ...verdict(current),
    ]);
    for (const detail of details) detail.hidden = Number(detail.dataset.ply) !== current;
    for (const move of moves) {
      const selected = Number(move.dataset.ply) === current;
      move.classList.toggle('is-current', selected);
      if (selected) move.setAttribute('aria-current', 'step');
      else move.removeAttribute('aria-current');
    }
  }

  const go = {
    first: () => show(0),
    previous: () => show(current - 1),
    next: () => show(current + 1),
    last: () => show(steps.length - 1),
  };
  for (const button of document.querySelectorAll('[data-go]')) {
    button.addEventListener('click', () => go[button.dataset.go]());
  }
  for (const target of [...moves, ...markers]) {
    target.addEventListener('click', () => show(Number(target.dataset.ply)));
  }
  document.addEventListener('keydown', (event) => {
    if (event.target.closest('input, textarea, select')) return;
    const keys = { ArrowLeft: go.previous, ArrowRight: go.next, Home: go.first, End: go.last };
    if (keys[event.key]) {
      event.preventDefault();
      keys[event.key]();
    }
  });

  show(0);
}
