/**
 * Office mode (design §11.5): a click on a valid code types it into the
 * "Operation code" field, ready to be recorded. Nothing else: the form still
 * works the same without JavaScript (ADR-013), and no chess logic lives here.
 */
(function () {
  document.addEventListener('click', function (event) {
    const code = event.target.closest('button.code');
    const field = document.getElementById('move');
    if (!code || !field) return;
    field.value = code.dataset.code;
    field.focus();
  });
})();
