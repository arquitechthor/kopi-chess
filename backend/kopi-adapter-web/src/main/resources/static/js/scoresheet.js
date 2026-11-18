/**
 * The scoresheet: always blank when the page opens (nothing is kept, not
 * even in this browser), Enter to go to the next box (White's move, then Black's, then the next
 * move), "Print" and "Clear". No chess logic: the boxes take any text.
 */
(function () {
  const form = document.getElementById('scoresheet');
  if (!form) return;

  const fields = Array.from(form.querySelectorAll('input'));

  // Back to this page with the browser's Back button, it comes from the cache with what was typed: blank it too.
  window.addEventListener('pageshow', (event) => { if (event.persisted) form.reset(); });

  // Enter goes on to the next box, in reading order of the moves (w1, b1, w2, b2…).
  const moves = fields.filter((field) => /^[wb]\d+$/.test(field.name))
    .sort((a, b) => {
      const n = (f) => Number(f.name.slice(1)) * 2 + (f.name[0] === 'b' ? 1 : 0);
      return n(a) - n(b);
    });
  form.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' || event.target.tagName !== 'INPUT') return;
    event.preventDefault();
    const at = moves.indexOf(event.target);
    const next = at >= 0 ? moves[at + 1] : fields[fields.indexOf(event.target) + 1];
    if (next) next.focus();
  });

  document.getElementById('scoresheetPrint').addEventListener('click', () => window.print());

  // "Clear" asks for a second click instead of a dialog; it forgets the question after a few seconds.
  const clear = document.getElementById('scoresheetClear');
  const label = clear.textContent;
  let armed = null;
  clear.addEventListener('click', () => {
    if (!armed) {
      clear.textContent = clear.dataset.confirm;
      armed = setTimeout(() => { clear.textContent = label; armed = null; }, 4000);
      return;
    }
    clearTimeout(armed);
    armed = null;
    clear.textContent = label;
    form.reset();
    fields[0].focus();
  });
})();
