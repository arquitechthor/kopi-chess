/**
 * Mobile menu toggle: the same logic as in kopi-web, aws-cert-study and
 * arquitechthor.github.io. It toggles .nav-links-open (the drop-down panel is
 * styled in kopi.css) and keeps aria-expanded in sync for screen readers.
 * No chess logic here (design §11.3).
 */
(function () {
  const toggle = document.getElementById('navToggle');
  const links = document.querySelector('.nav-links');
  if (!toggle || !links) return;

  toggle.setAttribute('aria-expanded', 'false');
  toggle.setAttribute('aria-controls', 'navLinks');
  links.id = links.id || 'navLinks';

  toggle.addEventListener('click', () => {
    const open = links.classList.toggle('nav-links-open');
    toggle.setAttribute('aria-expanded', String(open));
  });
})();

/**
 * The drop-down groups of the menu ("Play", "Analysis"): a click opens one and
 * closes the others; a click elsewhere or Escape closes them. Hover and focus
 * also open them, in kopi.css.
 */
(function () {
  const menus = Array.from(document.querySelectorAll('.nav-menu'));
  if (!menus.length) return;

  const setOpen = (menu, open) => {
    menu.classList.toggle('is-open', open);
    menu.querySelector('.nav-menu-button').setAttribute('aria-expanded', String(open));
  };
  const closeAll = (except) => menus.forEach((menu) => { if (menu !== except) setOpen(menu, false); });

  menus.forEach((menu) => {
    menu.querySelector('.nav-menu-button').addEventListener('click', () => {
      closeAll(menu);
      setOpen(menu, !menu.classList.contains('is-open'));
    });
  });
  document.addEventListener('click', (event) => {
    if (!event.target.closest('.nav-menu')) closeAll();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeAll();
  });
})();
