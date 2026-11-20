import 'chessground/assets/chessground.base.css';
import 'chessground/assets/chessground.brown.css';
import 'chessground/assets/chessground.cburnett.css';
import './style.css';
import { amplifyKopiAccount, configureKopiAccount } from './auth/kopiAccount';
import { pickLang, translator } from './i18n';
import { RelayClient } from './remote/relayClient';
import { RemotePc } from './remote/remotePc';
import { showGame, showHome, showLogin, showPc, type Context, type Route, type Screen } from './screens';

// Phase F3 (docs/plan-app-android.md §5 and §7): sign in with the Kopi account,
// see which PCs are in server mode, and play their bots through the relay.

const lang = pickLang(navigator.languages);
const t = translator(lang);
document.documentElement.lang = lang;

configureKopiAccount();
const account = amplifyKopiAccount;
const app = document.querySelector<HTMLElement>('#app')!;

const relay = new RelayClient({ token: () => account.accessToken() });
const pcs = new Map<string, RemotePc>();
let leave: (() => void) | null = null;
let email = '';

function screenFor(route: Route): Screen {
  switch (route.name) {
    case 'home':
      return showHome;
    case 'pc':
      return showPc(route.pc);
    case 'game':
      return showGame(route.pc, route.gameId);
  }
}

function show(route: Route): void {
  leave?.();
  const context: Context = {
    app,
    t,
    relay,
    email,
    pc(name) {
      let pc = pcs.get(name);
      if (!pc) {
        pc = new RemotePc(relay, name, lang, __APP_VERSION__);
        pcs.set(name, pc);
      }
      return pc;
    },
    // Each screen is a history entry, so Android's back button walks back.
    go(next) {
      history.pushState(next, '');
      show(next);
    },
    back() {
      history.back();
    },
    signOut() {
      void signOut();
    },
  };
  leave = screenFor(route)(context);
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', (event) => {
  if (email) show((event.state as Route | null) ?? { name: 'home' });
});

// No token any more (the 30-day session ran out): back to signing in.
relay.onState((state) => {
  if (state === 'signedOut' && email) void signOut();
});

// Android closes WebSockets in the background: reconnect on coming back.
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') relay.wake();
});

async function signOut(): Promise<void> {
  relay.stop();
  pcs.clear();
  await account.signOut();
  void start();
}

async function start(): Promise<void> {
  leave?.();
  leave = null;
  email = (await account.currentEmail()) ?? '';
  if (email) {
    relay.start();
    history.replaceState({ name: 'home' } satisfies Route, '');
    show({ name: 'home' });
  } else {
    showLogin(app, account, t, () => void start());
  }
}

void start();
