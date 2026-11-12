// D5 of docs/plan-app-android.md: English and Spanish, by the phone's language.

const en = {
  'app.title': 'Kopi Chess',
  'app.tagline': "Play your PC's bots from your phone.",
  'login.intro': 'Sign in with your Kopi account, the same one as on your PC.',
  'login.email': 'E-mail',
  'login.password': 'Password',
  'login.submit': 'Sign in',
  'login.busy': 'Signing in…',
  'login.noAccount': 'No account yet, or forgot your password? Go to Kopi Tools.',
  'login.finishOnWeb': 'Your account needs one more step: finish it on Kopi Tools, then sign in here.',
  'login.error.wrongCredentials': 'Wrong e-mail or password.',
  'login.error.notConfirmed': 'Confirm your account first, with the code sent to your e-mail, on Kopi Tools.',
  'login.error.tooManyAttempts': 'Too many attempts. Try again in a few minutes.',
  'login.error.network': 'No connection. Check your network and try again.',
  'login.error.unknown': 'Something went wrong. Try again.',
  'home.signedInAs': 'Signed in as {0}',
  'home.next': 'Next: connecting to your PC.',
  'home.signOut': 'Sign out',
} as const;

export type MessageKey = keyof typeof en;

const es: Record<MessageKey, string> = {
  'app.title': 'Kopi Chess',
  'app.tagline': 'Juega contra los bots de tu PC desde el móvil.',
  'login.intro': 'Entra con tu cuenta Kopi, la misma que en tu PC.',
  'login.email': 'Correo',
  'login.password': 'Contraseña',
  'login.submit': 'Entrar',
  'login.busy': 'Entrando…',
  'login.noAccount': '¿Aún no tienes cuenta o has olvidado la contraseña? Ve a Kopi Tools.',
  'login.finishOnWeb': 'A tu cuenta le falta un paso: complétalo en Kopi Tools y vuelve a entrar aquí.',
  'login.error.wrongCredentials': 'Correo o contraseña incorrectos.',
  'login.error.notConfirmed': 'Primero confirma tu cuenta, con el código que te llegó al correo, en Kopi Tools.',
  'login.error.tooManyAttempts': 'Demasiados intentos. Vuelve a probar en unos minutos.',
  'login.error.network': 'Sin conexión. Revisa la red y vuelve a probar.',
  'login.error.unknown': 'Algo ha fallado. Vuelve a probar.',
  'home.signedInAs': 'Has entrado como {0}',
  'home.next': 'Lo siguiente: conectar con tu PC.',
  'home.signOut': 'Salir',
};

const messages = { en, es } as const;

export type Lang = keyof typeof messages;

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

/** Translates a key, filling {0}, {1}... with the arguments. */
export function translator(lang: Lang): (key: MessageKey, ...args: string[]) => string {
  return (key, ...args) => messages[lang][key].replace(/\{(\d+)\}/g, (all, i: string) => args[Number(i)] ?? all);
}
