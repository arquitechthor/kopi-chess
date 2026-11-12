import { Chessground } from 'chessground';
import 'chessground/assets/chessground.base.css';
import 'chessground/assets/chessground.brown.css';
import 'chessground/assets/chessground.cburnett.css';
import './style.css';
import { pickLang, translator } from './i18n';

// First step of phase F3: the app shell and a static board. Login, presence
// and playing against the PC come in the next steps (docs/plan-app-android.md §7).

const lang = pickLang(navigator.languages);
const t = translator(lang);
document.documentElement.lang = lang;

const app = document.querySelector<HTMLElement>('#app')!;
app.innerHTML = `
  <header class="top">
    <h1>${t('app.title')}</h1>
    <p>${t('app.tagline')}</p>
  </header>
  <div class="board-wrap"><div id="board" class="board"></div></div>
`;

Chessground(document.querySelector<HTMLElement>('#board')!, {
  viewOnly: true,
  coordinates: true,
});
