import 'chessground/assets/chessground.base.css';
import 'chessground/assets/chessground.brown.css';
import 'chessground/assets/chessground.cburnett.css';
import './style.css';
import { amplifyKopiAccount, configureKopiAccount } from './auth/kopiAccount';
import { pickLang, translator } from './i18n';
import { showHome, showLogin } from './screens';

// Phase F3 (docs/plan-app-android.md §7): sign in with the Kopi account. The
// connection to the PC comes in the next steps.

const lang = pickLang(navigator.languages);
const t = translator(lang);
document.documentElement.lang = lang;

configureKopiAccount();
const account = amplifyKopiAccount;
const app = document.querySelector<HTMLElement>('#app')!;

async function start(): Promise<void> {
  const email = await account.currentEmail();
  if (email) {
    showHome(app, account, t, email, start);
  } else {
    showLogin(app, account, t, start);
  }
}

void start();
