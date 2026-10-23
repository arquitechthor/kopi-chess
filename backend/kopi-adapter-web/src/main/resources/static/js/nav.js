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
