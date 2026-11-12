import { Chessground } from 'chessground';
import type { KopiAccount } from './auth/kopiAccount';
import type { MessageKey } from './i18n';

type T = (key: MessageKey, ...args: string[]) => string;

/** Sign-up and password recovery happen on the Kopi web, not in the app (plan §5). */
export const KOPI_WEB = 'https://app.kopitools.link';

/** Builds an element; text goes in as text, never as HTML. */
function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Partial<HTMLElementTagNameMap[K]> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children);
  return node;
}

function header(t: T): HTMLElement {
  return el('header', { className: 'top' }, el('h1', {}, t('app.title')), el('p', {}, t('app.tagline')));
}

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

export function showHome(app: HTMLElement, account: KopiAccount, t: T, emailAddress: string, onSignedOut: () => void): void {
  const signOut = el('button', { type: 'button' }, t('home.signOut'));
  signOut.addEventListener('click', async () => {
    signOut.disabled = true;
    await account.signOut();
    onSignedOut();
  });

  const board = el('div', { className: 'board' });
  app.replaceChildren(
    header(t),
    el('p', { className: 'who' }, t('home.signedInAs', emailAddress), ' ', signOut),
    el('p', {}, t('home.next')),
    el('div', { className: 'board-wrap' }, board),
  );
  Chessground(board, { viewOnly: true, coordinates: true });
}
