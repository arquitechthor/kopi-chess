import { describe, expect, it } from 'vitest';
import { pickLang, translator } from './i18n';

describe('pickLang', () => {
  it('takes Spanish from any regional variant', () => {
    expect(pickLang(['es-MX', 'en-US'])).toBe('es');
  });

  it('takes the first supported language in the list', () => {
    expect(pickLang(['fr-FR', 'en-GB', 'es-ES'])).toBe('en');
  });

  it('falls back to English', () => {
    expect(pickLang(['de-DE'])).toBe('en');
    expect(pickLang([])).toBe('en');
  });
});

describe('translator', () => {
  it('gives every key in both languages', () => {
    const en = translator('en');
    const es = translator('es');
    expect(en('app.tagline')).not.toBe(es('app.tagline'));
  });
});
