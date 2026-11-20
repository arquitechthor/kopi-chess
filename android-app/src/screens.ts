import { Chessground } from 'chessground';
import type { Api } from 'chessground/api';
import type { Key } from 'chessground/types';
import type { KopiAccount } from './auth/kopiAccount';
import { el, errorText, header, type T } from './dom';
import type { MessageKey } from './i18n';
import { cgColor, destinations, isOver, lastMoveKeys, movePairs, outcomeMessage, promotionPieces, type PromotionPiece } from './play/board';
import { BOT_PROFILES, isLost, type Bot, type BotProfile, type Game } from './remote/model';
import type { ConnectionState, RelayClient } from './remote/relayClient';
import { RemotePc, type HumanColor } from './remote/remotePc';

/** Sign-up and password recovery happen on the Kopi web, not in the app (plan §5). */
export const KOPI_WEB = 'https://app.kopitools.link';

export function showLogin(app: HTMLElement, account: KopiAccount, t: T, onSignedIn: () => void): void {
  const email = el('input', { type: 'email', name: 'email', autocomplete: 'username', required: true });
  const password = el('input', { type: 'password', name: 'password', autocomplete: 'current-password', required: true });
  const submit = el('button', { type: 'submit', className: 'primary' }, t('login.submit'));
  const message = el('p', { className: 'message', role: 'alert' });

  const form = el(
    'form',
    { className: 'login' },
    el('p', {}, t('login.intro')),
    el('label', {}, t('login.email'), email),
    el('label', {}, t('login.password'), password),
    message,
    submit,
    el('a', { href: KOPI_WEB, target: '_blank', rel: 'noopener' }, t('login.noAccount')),
  );

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    submit.disabled = true;
    submit.textContent = t('login.busy');
    message.textContent = '';
    const result = await account.signIn(email.value, password.value);
    submit.disabled = false;
    submit.textContent = t('login.submit');
    if (result.kind === 'signedIn') {
      onSignedIn();
    } else if (result.kind === 'finishOnWeb') {
      message.textContent = t('login.finishOnWeb');
    } else {
      message.textContent = t(`login.error.${result.reason}`);
    }
  });

  app.replaceChildren(header(t), form);
  email.focus();
}

// ---------------------------------------------------------------------------
// Signed in: home → PC → game (plan §5, screens 2–4).

export type Route = { name: 'home' } | { name: 'pc'; pc: string } | { name: 'game'; pc: string; gameId: string };

/** What every signed-in screen gets. */
export interface Context {
  app: HTMLElement;
  t: T;
  relay: RelayClient;
  email: string;
  pc(name: string): RemotePc;
  go(route: Route): void;
  back(): void;
  signOut(): void;
}

/** A screen draws itself and returns what to undo when leaving it. */
export type Screen = (ctx: Context) => () => void;

function connectionLine(t: T, state: ConnectionState): string {
  return state === 'open' ? '' : state === 'offline' ? t('conn.offline') : t('conn.connecting');
}

function backButton(ctx: Context): HTMLButtonElement {
  const back = el('button', { type: 'button', className: 'back' }, `← ${ctx.t('common.back')}`);
  back.addEventListener('click', () => ctx.back());
  return back;
}

/** Inicio: who is signed in, and which PCs are in server mode. */
export const showHome: Screen = (ctx) => {
  const { t, relay } = ctx;
  const signOut = el('button', { type: 'button' }, t('home.signOut'));
  signOut.addEventListener('click', () => {
    signOut.disabled = true;
    ctx.signOut();
  });
  const status = el('p', { className: 'status' });
  const body = el('section');

  function draw(): void {
    status.textContent = connectionLine(t, relay.state);
    if (relay.state !== 'open') {
      body.replaceChildren();
      return;
    }
    if (relay.servers.length === 0) {
      body.replaceChildren(el('h2', {}, t('home.noPc.title')), el('p', { className: 'help' }, t('home.noPc.help')));
      return;
    }
    const list = el('ul', { className: 'choices' });
    for (const server of relay.servers) {
      const button = el('button', { type: 'button', className: 'choice' }, server.name || '—');
      button.addEventListener('click', () => ctx.go({ name: 'pc', pc: server.name }));
      list.append(el('li', {}, button));
    }
    body.replaceChildren(el('h2', {}, t('home.choosePc')), list);
  }

  ctx.app.replaceChildren(header(t), el('p', { className: 'who' }, t('home.signedInAs', ctx.email), ' ', signOut), status, body);
  draw();
  const offPresence = relay.onPresence(draw);
  const offState = relay.onState(draw);
  return () => {
    offPresence();
    offState();
  };
};

function botName(t: T, profile: BotProfile | null): string {
  return profile ? t(`bot.${profile}` as MessageKey) : '—';
}

/** Un PC: the games to resume, and a new game against a bot. */
export const showPc =
  (name: string): Screen =>
  (ctx) => {
    const { t, relay } = ctx;
    const pc = ctx.pc(name);
    const status = el('p', { className: 'status' });
    const message = el('p', { className: 'message', role: 'alert' });
    const body = el('section');
    let loaded = false;
    let loading = false;
    let left = false;

    async function load(): Promise<void> {
      if (loading || !pc.server || relay.state !== 'open') {
        return;
      }
      loading = true;
      message.textContent = '';
      status.textContent = t('pc.loading');
      try {
        const games = await pc.games();
        const bots = await pc.bots();
        if (left) return;
        loaded = true;
        status.textContent = '';
        body.replaceChildren(...resumeList(games), ...newGameForm(bots));
      } catch (error) {
        if (left) return;
        status.textContent = '';
        message.textContent = errorText(t, error);
        const retry = el('button', { type: 'button' }, t('common.retry'));
        retry.addEventListener('click', () => void load());
        body.replaceChildren(retry);
      } finally {
        loading = false;
      }
    }

    function resumeList(games: Game[]): HTMLElement[] {
      if (games.length === 0) {
        return [el('h2', {}, t('pc.resume')), el('p', { className: 'help' }, t('pc.noGames'))];
      }
      const list = el('ul', { className: 'choices' });
      for (const game of games) {
        const moveNumber = String(Math.max(1, Math.ceil(((game.lastMove?.ply ?? 0) + 1) / 2)));
        const line = t('pc.gameLine', botName(t, game.botProfile), game.humanColor ? t(`color.${game.humanColor}`) : '—', moveNumber);
        const button = el('button', { type: 'button', className: 'choice' }, line);
        button.addEventListener('click', () => ctx.go({ name: 'game', pc: name, gameId: game.id }));
        list.append(el('li', {}, button));
      }
      return [el('h2', {}, t('pc.resume')), list];
    }

    function newGameForm(bots: Bot[]): HTMLElement[] {
      const form = el('form', { className: 'new-game' });
      const known = bots.filter((bot) => BOT_PROFILES.includes(bot.profile));
      const botField = el('fieldset', {}, el('legend', {}, t('pc.bot')));
      known.forEach((bot, i) => {
        const input = el('input', { type: 'radio', name: 'bot', value: bot.profile, checked: i === 0 });
        const record = t('pc.record', String(bot.record.won), String(bot.record.lost), String(bot.record.drawn));
        botField.append(
          el(
            'label',
            { className: 'option' },
            input,
            el('span', {}, el('strong', {}, `${botName(t, bot.profile)} (${bot.level})`), el('small', {}, bot.description), el('small', {}, record)),
          ),
        );
      });
      const colorField = el('fieldset', { className: 'segmented' }, el('legend', {}, t('pc.color')));
      (['WHITE', 'BLACK', 'RANDOM'] as const).forEach((color) => {
        colorField.append(el('label', {}, el('input', { type: 'radio', name: 'color', value: color, checked: color === 'WHITE' }), el('span', {}, t(`color.${color}`))));
      });
      const submit = el('button', { type: 'submit', className: 'primary' }, t('pc.start'));
      form.append(botField, colorField, submit);
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const data = new FormData(form);
        submit.disabled = true;
        submit.textContent = t('pc.starting');
        message.textContent = '';
        try {
          const game = await pc.newGame(data.get('bot') as BotProfile, data.get('color') as HumanColor);
          if (!left) ctx.go({ name: 'game', pc: name, gameId: game.id });
        } catch (error) {
          if (left) return;
          submit.disabled = false;
          submit.textContent = t('pc.start');
          // A lost request may still have created the game: show the list again.
          message.textContent = errorText(t, error);
          if (isLost(error)) void load();
        }
      });
      return [el('h2', {}, t('pc.new')), form];
    }

    function update(): void {
      if (left) return;
      if (relay.state !== 'open') {
        status.textContent = connectionLine(t, relay.state);
      } else if (!pc.server) {
        status.textContent = t('pc.gone', name);
      } else if (!loaded) {
        void load();
      } else if (status.textContent !== t('pc.loading')) {
        status.textContent = '';
      }
    }

    ctx.app.replaceChildren(el('nav', { className: 'bar' }, backButton(ctx), el('h2', {}, name)), status, message, body);
    update();
    const offPresence = relay.onPresence(update);
    const offState = relay.onState(update);
    return () => {
      left = true;
      offPresence();
      offState();
    };
  };

const PIECE_SIGNS: Record<PromotionPiece, string> = { q: '♛', r: '♜', b: '♝', n: '♞' };

/** El tablero: one game against a bot, played on the PC. */
export const showGame =
  (name: string, gameId: string): Screen =>
  (ctx) => {
    const { t, relay } = ctx;
    const pc = ctx.pc(name);
    const versus = el('p', { className: 'versus' });
    const status = el('p', { className: 'status', role: 'status' });
    const message = el('p', { className: 'message', role: 'alert' });
    const boardEl = el('div', { className: 'board' });
    const moves = el('ol', { className: 'moves' });
    const resign = el('button', { type: 'button' }, t('game.resign'));
    const newGame = el('button', { type: 'button', className: 'primary', hidden: true }, t('game.newGame'));
    const promotion = el('dialog', { className: 'promotion' });
    let game: Game | null = null;
    let busy = false;
    let left = false;
    let resignArmed: ReturnType<typeof setTimeout> | null = null;

    const board: Api = Chessground(boardEl, {
      fen: '8/8/8/8/8/8/8/8', // empty until the PC says where the pieces are
      coordinates: true,
      viewOnly: true,
      premovable: { enabled: false },
      movable: { free: false, showDests: true, events: { after: (from, to) => void chosen(from, to) } },
    });

    function draw(): void {
      if (!game) return;
      const over = isOver(game);
      const human = game.humanColor ? cgColor(game.humanColor) : 'white';
      const canMove = !over && game.humanToMove;
      if (busy) {
        // Waiting for the PC: leave the piece where the person dropped it.
        board.set({ movable: { color: undefined } });
      } else {
        board.set({
          viewOnly: false,
          fen: game.fen,
          orientation: human,
          turnColor: cgColor(game.sideToMove),
          check: game.inCheck,
          lastMove: lastMoveKeys(game),
          movable: { color: canMove ? human : undefined, dests: canMove ? destinations(game.legalMoves) : new Map() },
        });
      }
      versus.textContent = t('game.versus', botName(t, game.botProfile), game.humanColor ? t(`color.${game.humanColor}`) : '—');
      const outcome = outcomeMessage(game);
      if (outcome) {
        status.textContent = t(outcome.key, t(outcome.termination));
      } else if (busy) {
        status.textContent = game.humanToMove ? t('game.waitingPc') : t('game.botThinking');
      } else if (game.humanToMove) {
        status.textContent = game.inCheck ? t('game.yourTurnCheck') : t('game.yourTurn');
      } else {
        status.textContent = t('game.botThinking');
      }
      moves.replaceChildren(...movePairs(game.moves ?? []).map((line) => el('li', {}, line)));
      moves.scrollTop = moves.scrollHeight;
      resign.hidden = over;
      resign.disabled = busy;
      newGame.hidden = !over;
    }

    /** Asks the PC for the game as it stands: after any doubt, never guess. */
    async function reload(): Promise<void> {
      if (busy || left) return;
      if (!pc.server || relay.state !== 'open') {
        status.textContent = relay.state === 'open' ? t('pc.gone', name) : connectionLine(t, relay.state);
        return;
      }
      busy = true;
      if (!game) status.textContent = t('game.loading');
      try {
        game = await pc.game(gameId);
      } catch (error) {
        if (!left) message.textContent = errorText(t, error);
      } finally {
        busy = false;
        if (!left) draw();
      }
    }

    /** Sends a move or a resignation, and draws the PC's answer. */
    async function send(action: (current: Game) => Promise<Game>): Promise<void> {
      if (!game || busy) return;
      busy = true;
      message.textContent = '';
      draw();
      try {
        game = await action(game);
        busy = false;
      } catch (error) {
        busy = false;
        if (left) return;
        message.textContent = errorText(t, error);
        // The PC may have played it anyway, or the game changed there.
        await reload();
        return;
      }
      if (!left) draw();
    }

    async function chosen(from: Key, to: Key): Promise<void> {
      if (!game) return;
      const pieces = promotionPieces(game.legalMoves, from, to);
      let uci = from + to;
      if (pieces.length > 0) {
        const piece = await askPromotion(pieces);
        if (!piece) {
          draw(); // cancelled: put the pawn back
          return;
        }
        uci += piece;
      }
      await send((current) => pc.move(current, uci));
    }

    function askPromotion(pieces: PromotionPiece[]): Promise<PromotionPiece | null> {
      return new Promise((resolve) => {
        let picked: PromotionPiece | null = null;
        const white = game?.humanColor !== 'BLACK';
        promotion.replaceChildren(
          el('p', {}, t('game.promotion')),
          ...pieces.map((piece) => {
            const button = el('button', { type: 'button', className: white ? 'white' : 'black', ariaLabel: t(`piece.${piece}`) }, PIECE_SIGNS[piece]);
            button.addEventListener('click', () => {
              picked = piece;
              promotion.close();
            });
            return button;
          }),
        );
        promotion.onclose = () => resolve(picked);
        promotion.onclick = (event) => {
          if (event.target === promotion) promotion.close(); // a tap outside cancels
        };
        promotion.showModal();
      });
    }

    resign.addEventListener('click', () => {
      if (resignArmed === null) {
        resign.textContent = t('game.resignConfirm');
        resign.classList.add('danger');
        resignArmed = setTimeout(disarm, 4000);
        return;
      }
      disarm();
      void send((current) => pc.resign(current));
    });

    function disarm(): void {
      if (resignArmed !== null) clearTimeout(resignArmed);
      resignArmed = null;
      resign.textContent = t('game.resign');
      resign.classList.remove('danger');
    }

    newGame.addEventListener('click', () => ctx.go({ name: 'pc', pc: name }));

    ctx.app.replaceChildren(
      el('nav', { className: 'bar' }, backButton(ctx), el('h2', {}, name)),
      versus,
      el('div', { className: 'board-wrap' }, boardEl),
      status,
      message,
      el('div', { className: 'actions' }, resign, newGame),
      moves,
      promotion,
    );
    status.textContent = t('game.loading');
    resign.hidden = true;
    void reload();

    // After reconnecting, or when the PC comes back, the game may have
    // changed meanwhile: ask again.
    let wasReachable = relay.state === 'open' && pc.server !== null;
    const recheck = (): void => {
      const reachable = relay.state === 'open' && pc.server !== null;
      if (reachable && !wasReachable) {
        void reload();
      } else if (!reachable && !busy) {
        status.textContent = relay.state === 'open' ? t('pc.gone', name) : connectionLine(t, relay.state);
      }
      wasReachable = reachable;
    };
    const offPresence = relay.onPresence(recheck);
    const offState = relay.onState(recheck);
    const onVisible = (): void => {
      if (document.visibilityState === 'visible') void reload();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      left = true;
      disarm();
      offPresence();
      offState();
      document.removeEventListener('visibilitychange', onVisible);
      if (promotion.open) promotion.close();
      board.destroy();
    };
  };
