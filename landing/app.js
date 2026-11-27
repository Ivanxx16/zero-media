/**
 * 0Media Digital Signage — Landing Page Interactive Scripts
 * Dynamic DOOH Screen Switcher, Active Navigation & Lightbox
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Mobile Menu Toggle
  const mobileBtn = document.getElementById('mobile-menu-btn');
  const landingNav = document.getElementById('landing-nav');

  if (mobileBtn && landingNav) {
    mobileBtn.addEventListener('click', () => {
      landingNav.classList.toggle('open');
    });

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
    const scrollPos = window.scrollY + 100;

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

  // 3. Interactive Hero DOOH TV Switcher
  const pillBtns = document.querySelectorAll('.tv-switch-pills .pill-btn');
  const heroImg = document.getElementById('hero-tv-img');
  const heroTag = document.getElementById('hero-tv-tag');
  const heroViews = document.getElementById('hero-tv-views');
  const heroLoc = document.getElementById('hero-tv-loc');
  const heroFreq = document.getElementById('hero-tv-freq');

  let autoCycleInterval = null;
  let currentPillIdx = 0;

  function setHeroScreen(pill) {
    if (!pill || !heroImg) return;

    pillBtns.forEach(p => p.classList.remove('active'));
    pill.classList.add('active');

    // Quick smooth crossfade
    heroImg.style.opacity = '0.3';
    heroImg.style.transform = 'scale(0.98)';

    setTimeout(() => {
      heroImg.src = pill.getAttribute('data-img');
      if (heroTag) heroTag.textContent = pill.getAttribute('data-tag');
      if (heroViews) heroViews.textContent = pill.getAttribute('data-views');
      if (heroLoc) heroLoc.textContent = pill.getAttribute('data-loc');
      if (heroFreq) heroFreq.textContent = pill.getAttribute('data-freq');

      heroImg.style.opacity = '1';
      heroImg.style.transform = 'scale(1)';
    }, 150);
  }

  pillBtns.forEach((pill, idx) => {
    pill.addEventListener('click', () => {
      currentPillIdx = idx;
      setHeroScreen(pill);
      // Restart interval on manual click
      startAutoCycle();
    });
  });

  function startAutoCycle() {
    if (autoCycleInterval) clearInterval(autoCycleInterval);
    autoCycleInterval = setInterval(() => {
      if (pillBtns.length === 0) return;
      currentPillIdx = (currentPillIdx + 1) % pillBtns.length;
      setHeroScreen(pillBtns[currentPillIdx]);
    }, 6000);
  }

  if (pillBtns.length > 0) {
    startAutoCycle();
  }

  // 4. Close Lightbox on Escape Key
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
    if (captionEl) captionEl.textContent = caption || 'Vista previa de pantalla en circuito comercial';
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
