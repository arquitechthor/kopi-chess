import type { MessageKey } from './i18n';
import { RemoteError, isLost } from './remote/model';

export type T = (key: MessageKey, ...args: string[]) => string;

/** Builds an element; text goes in as text, never as HTML. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Partial<HTMLElementTagNameMap[K]> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children);
  return node;
}

export function header(t: T): HTMLElement {
  return el('header', { className: 'top' }, el('h1', {}, t('app.title')), el('p', {}, t('app.tagline')));
}

const KNOWN_ERRORS = ['ILLEGAL_MOVE', 'CONFLICT', 'GAME_OVER', 'NOT_FOUND', 'BOT_UNAVAILABLE', 'VERSION_MISMATCH', 'SERVER_MODE_OFF'];

/** What to tell the person about a failed request. */
export function errorText(t: T, error: unknown): string {
  if (isLost(error)) {
    return t('error.lost');
  }
  if (error instanceof RemoteError && KNOWN_ERRORS.includes(error.code)) {
    return t(`error.${error.code}` as MessageKey);
  }
  return t('error.other');
}
