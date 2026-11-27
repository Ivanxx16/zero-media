/**
 * 0Media Digital Signage — Landing Page Interactive Scripts
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Mobile Menu Toggle
  const mobileBtn = document.getElementById('mobile-menu-btn');
  const landingNav = document.getElementById('landing-nav');

  if (mobileBtn && landingNav) {
    mobileBtn.addEventListener('click', () => {
      landingNav.classList.toggle('open');
    });

    // Close menu when clicking any nav link
    landingNav.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        landingNav.classList.remove('open');
      });
    });
  }

  // 2. Navigation Active State on Scroll
  const navLinks = document.querySelectorAll('.nav-link');
  const sections = document.querySelectorAll('section[id]');

  function updateActiveNav() {
    const scrollPos = window.scrollY + 120;

    sections.forEach(section => {
      const top = section.offsetTop;
      const height = section.offsetHeight;
      const id = section.getAttribute('id');

      if (scrollPos >= top && scrollPos < top + height) {
        navLinks.forEach(link => {
          link.classList.remove('active');
          if (link.getAttribute('href') === `#${id}`) {
            link.classList.add('active');
          }
        });
      }
    });
  }

  window.addEventListener('scroll', updateActiveNav, { passive: true });
  updateActiveNav();

  // 3. Close Lightbox on Escape Key
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeImageLightbox();
    }
  });
});

/**
 * Open Image Lightbox Modal
 * @param {string} src 
 * @param {string} caption 
 */
function openImageLightbox(src, caption) {
  const modal = document.getElementById('lightbox-modal');
  const img = document.getElementById('lightbox-img');
  const captionEl = document.getElementById('lightbox-caption');

  if (modal && img) {
    img.src = src;
    if (captionEl) captionEl.textContent = caption || 'Vista previa de televisor en circuito comercial';
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
  }
}

/**
 * Close Image Lightbox Modal
 */
function closeImageLightbox() {
  const modal = document.getElementById('lightbox-modal');
  if (modal) {
    modal.classList.remove('active');
    document.body.style.overflow = '';
  }
}
