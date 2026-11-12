// D5 of docs/plan-app-android.md: English and Spanish, by the phone's language.

const messages = {
  en: {
    'app.title': 'Kopi Chess',
    'app.tagline': "Play your PC's bots from your phone.",
  },
  es: {
    'app.title': 'Kopi Chess',
    'app.tagline': 'Juega contra los bots de tu PC desde el móvil.',
  },
} as const;

export type Lang = keyof typeof messages;
export type MessageKey = keyof (typeof messages)['en'];

/** Picks the first supported language from the phone's list; English otherwise. */
export function pickLang(preferred: readonly string[]): Lang {
  for (const tag of preferred) {
    const base = tag.toLowerCase().split('-')[0];
    if (base === 'es' || base === 'en') {
      return base;
    }
  }
  return 'en';
}

export function translator(lang: Lang): (key: MessageKey) => string {
  return (key) => messages[lang][key];
}
