// 0Media Digital Signage — Admin Web Panel Logic (v2.3 Real-Time Server Connected)
// Panel con 2 opciones principales: "Pantallas en Vivo" y "Lista de Campañas por Televisor"
// Soporta Videos (.mp4, .webm), Fotos (.png, .jpg, .webp) y GIFs animados (.gif) independientes por TV.
// Conectado al backend local para enviar las fotos directamente al televisor Android TV.

document.addEventListener('DOMContentLoaded', () => {

  // ================= 1. ESTADO DE TELEVISORES =================
  let devices = [];

  // Comprobar si la sesión de panel actual ya vinculó alguna pantalla
  const isSessionPaired = sessionStorage.getItem('0media_admin_session_paired') === 'true';

  // Recuperar estado previo solo de pantallas que estén vinculadas
  const cached = localStorage.getItem('0media_devices_v4');
  if (cached) {
    try {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed) && parsed.length > 0) {
        devices = parsed.filter(d => d.isPaired);
      }
    } catch (e) {}
  }

  let currentSelectedTvId = devices[0]?.id || null;
  let simulatorTimer = null;
  let currentSimIndex = 0;

  // Variables temporales de subida
  let currentPendingFile = null;
  let currentPendingDataUrl = null;
  let currentDetectedType = "IMAGE";
  let currentDetectedTag = "FOTO PNG";

  // ================= 2. DECLARACIÓN DE TODOS LOS ELEMENTOS DEL DOM =================
  // Navegación
  const navItems = document.querySelectorAll('.nav-item');
  const viewSections = document.querySelectorAll('.view-section');
  const pageTitle = document.getElementById('page-title');

  // Vista 1: Pantallas en Vivo
  const devicesContainer = document.getElementById('devices-container');
  const btnRefreshDevices = document.getElementById('btn-refresh-devices');
  const statTotalScreens = document.getElementById('stat-total-screens');
  const statOnlineScreens = document.getElementById('stat-online-screens');
  const statTotalAds = document.getElementById('stat-total-ads');
  const badgeScreenCount = document.getElementById('badge-screen-count');
  const badgeCampaignCount = document.getElementById('badge-campaign-count');

  // Vista 2: Campañas / Televisores
  const campaignTvSelect = document.getElementById('campaign-tv-select');
  const tvChipsContainer = document.getElementById('tv-chips-container');
  const tvBannerName = document.getElementById('tv-banner-name');
  const tvBannerMeta = document.getElementById('tv-banner-meta');
  const tvBannerAdCount = document.getElementById('tv-banner-ad-count');
  const tvBannerCycleTime = document.getElementById('tv-banner-cycle-time');
  const tvItemsBadge = document.getElementById('tv-items-badge');
  const simulatorTvTag = document.getElementById('simulator-tv-tag');
  const tvPlaylistItemsContainer = document.getElementById('tv-playlist-items-container');
  const btnPushToTv = document.getElementById('btn-push-to-selected-tv');
  const btnSyncAll = document.getElementById('btn-sync-all');

  // Simulador de TV
  const simulatorMediaContainer = document.getElementById('simulator-media-container');
  const simulatorIndicators = document.getElementById('simulator-indicators');
  const simTimerPill = document.getElementById('sim-timer-pill');
  const btnSimPrev = document.getElementById('btn-sim-prev');
  const btnSimNext = document.getElementById('btn-sim-next');

  // Subida de Archivos Multimedia
  const dropZoneUpload = document.getElementById('drop-zone-upload');
  const btnTriggerUpload = document.getElementById('btn-trigger-upload');
  const mediaFileInput = document.getElementById('media-file-input');

  // Modal de Subida / Configuración de Anuncio
  const uploadModal = document.getElementById('upload-config-modal');
  const btnCloseUploadModal = document.getElementById('btn-close-upload-modal');
  const btnCancelUploadModal = document.getElementById('btn-cancel-upload-modal');
  const formConfirmUpload = document.getElementById('form-confirm-upload');
  const uploadPreviewContainer = document.getElementById('upload-preview-container');
  const inputAdTitle = document.getElementById('input-ad-title');
  const inputAdType = document.getElementById('input-ad-type');
  const inputAdDuration = document.getElementById('input-ad-duration');
  const selectTargetTvModal = document.getElementById('select-target-tv-modal');
  const btnSaveAdToTv = document.getElementById('btn-save-ad-to-tv');

  // Modal de Previsualización Completa
  const previewModal = document.getElementById('preview-modal');
  const btnClosePreviewModal = document.getElementById('btn-close-preview-modal');
  const previewModalTitle = document.getElementById('preview-modal-title');
  const fullMediaViewer = document.getElementById('full-media-viewer');
  const previewMetaRow = document.getElementById('preview-meta-row');

  // Modal de Vinculación de Dispositivos (PIN)
  const pairingModal = document.getElementById('pairing-modal');
  const btnOpenModal = document.getElementById('btn-open-pairing-modal');
  const btnCloseModal = document.getElementById('btn-close-modal');
  const btnCancelModal = document.getElementById('btn-cancel-modal');
  const formPair = document.getElementById('form-pair-device');
  const btnSampleCode = document.getElementById('btn-sample-code');
  const pinInputs = [
    document.getElementById('pin-0'),
    document.getElementById('pin-1'),
    document.getElementById('pin-2'),
    document.getElementById('pin-3'),
    document.getElementById('pin-4'),
    document.getElementById('pin-5')
  ];

  // Panel de Duración de Publicidad y Tiempos del Carrusel
  const statCycleSeconds = document.getElementById('stat-cycle-seconds');
  const statAvgSeconds = document.getElementById('stat-avg-seconds');
  const globalDurationPills = document.getElementById('global-duration-pills');

  // Modal de Modificación de Duración de Publicidad
  const durationModal = document.getElementById('duration-modal');
  const btnCloseDurationModal = document.getElementById('btn-close-duration-modal');
  const btnCancelDurationModal = document.getElementById('btn-cancel-duration-modal');
  const formEditDuration = document.getElementById('form-edit-duration');
  const editDurationItemId = document.getElementById('edit-duration-item-id');
  const durationModalThumb = document.getElementById('duration-modal-thumb');
  const durationModalTitle = document.getElementById('duration-modal-title');
  const durationModalMeta = document.getElementById('duration-modal-meta');
  const inputSingleDuration = document.getElementById('input-single-duration');
  const modalDurationPills = document.getElementById('modal-duration-pills');
  const checkApplyDurationAll = document.getElementById('check-apply-duration-all');

  // Toast Container
  const toastContainer = document.getElementById('toast-container');

  // ================= 3. FUNCIONES DE PERSISTENCIA Y SINCRONIZACIÓN =================
  // Detección multi-host infalible (soporta file://, localhost, 127.0.0.1, LiveServer o IP de red local)
  function getApiCandidateBases() {
    const list = [];
    if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
      if (window.location.port === '3000') {
        list.push('');
      } else {
        list.push(`http://${window.location.hostname || '127.0.0.1'}:3000`);
      }
    }
    list.push('http://127.0.0.1:3000');
    list.push('http://localhost:3000');
    list.push('http://192.168.110.129:3000');
    return list.filter((v, i, a) => a.indexOf(v) === i);
  }

  let cachedWorkingApiBase = null;

  function openLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) {
      modal.classList.add('open');
      const inputPass = document.getElementById('login-password');
      if (inputPass) setTimeout(() => inputPass.focus(), 150);
    }
  }

  function closeLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.classList.remove('open');
  }

  async function apiFetch(endpoint, options = {}) {
    const bases = getApiCandidateBases();
    if (cachedWorkingApiBase && bases.includes(cachedWorkingApiBase)) {
      bases.splice(bases.indexOf(cachedWorkingApiBase), 1);
      bases.unshift(cachedWorkingApiBase);
    }

    const token = localStorage.getItem('0media_auth_token');
    const headers = { ...(options.headers || {}) };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    let lastError = null;
    for (const base of bases) {
      const url = `${base}${endpoint}`;
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 8000);
        const res = await fetch(url, {
          ...options,
          headers: headers,
          credentials: 'include',
          signal: controller.signal
        });
        clearTimeout(timeoutId);

        if (res.status === 401 && endpoint !== '/api/v1/admin/login' && endpoint !== '/api/v1/admin/auth-status') {
          openLoginModal();
          return res;
        }

        if (res.ok || res.status < 500) {
          cachedWorkingApiBase = base;
          return res;
        }
      } catch (err) {
        lastError = err;
      }
    }

    throw lastError || new Error("No se pudo conectar con el servidor 0Media en el puerto 3000.");
  }

  function saveDevices() {
    localStorage.setItem('0media_devices_v4', JSON.stringify(devices));
    renderDevices();
    updateStats();
    renderTvSelector();
  }

  let pairingModalDismissed = false;

  async function loadDevicesFromBackend() {
    try {
      const res = await apiFetch('/api/v1/admin/devices');
      const data = await res.json();
      if (data.success && Array.isArray(data.devices)) {
        devices = data.devices;
        if (!devices.some(d => d.id === currentSelectedTvId)) {
          currentSelectedTvId = devices.length > 0 ? devices[0].id : null;
        }

        saveDevices();
      }
    } catch (err) {
      console.log("[ADMIN BACKEND] Usando datos locales en caché.");
    }
  }

  function updateStats() {
    const total = devices.length;
    const online = devices.filter(d => d.status === 'ONLINE').length;
    let totalAds = 0;
    devices.forEach(d => { totalAds += (d.items || []).length; });

    if (statTotalScreens) statTotalScreens.textContent = total;
    if (statOnlineScreens) statOnlineScreens.textContent = online;
    if (statTotalAds) statTotalAds.textContent = `${totalAds} Anuncio${totalAds === 1 ? '' : 's'}`;
    if (badgeScreenCount) badgeScreenCount.textContent = total;
    if (badgeCampaignCount) badgeCampaignCount.textContent = totalAds;
  }

  function showToast(message, borderColor = '#2563EB') {
    if (!toastContainer) return;
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.style.borderColor = borderColor;
    toast.innerHTML = `<span>⚡</span><span>${message}</span>`;
    toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  // ================= 4. NAVEGACIÓN Y RUTAS =================
  const titlesMap = {
    devices: "Pantallas en Vivo — Monitoreo en Tiempo Real",
    playlists: "Lista de Campañas — Publicidad por Televisor"
  };

  function switchView(targetView) {
    navItems.forEach(nav => {
      if (nav.getAttribute('data-view') === targetView) {
        nav.classList.add('active');
      } else {
        nav.classList.remove('active');
      }
    });

    viewSections.forEach(section => {
      if (section.id === `view-${targetView}`) {
        section.classList.add('active');
      } else {
        section.classList.remove('active');
      }
    });

    if (pageTitle && titlesMap[targetView]) {
      pageTitle.textContent = titlesMap[targetView];
    }

    if (targetView === 'playlists') {
      renderTvSelector();
    }

    if (window.location.hash !== '#' + targetView) {
      window.location.hash = targetView;
    }
  }

  function handleHash() {
    const hash = window.location.hash.replace('#', '');
    if (hash === 'playlists') {
      switchView('playlists');
    } else {
      switchView('devices');
    }
  }

  // ================= 5. VISTA 1: RENDER DE PANTALLAS EN VIVO =================
  function renderDevices() {
    if (!devicesContainer) return;
    devicesContainer.innerHTML = '';

    const pairedDevices = devices.filter(d => d.isPaired);

    if (pairedDevices.length === 0) {
      devicesContainer.innerHTML = `
        <div class="empty-devices-state" style="grid-column: 1 / -1; text-align: center; padding: 48px 24px; background: rgba(30, 41, 59, 0.4); border-radius: 16px; border: 1px dashed var(--border-subtle); max-width: 620px; margin: 30px auto;">
          <div style="font-size: 3rem; margin-bottom: 12px;">📺 ✨</div>
          <h4 style="color: #FFF; font-size: 1.25rem; margin-bottom: 8px;">No hay pantallas vinculadas todavía</h4>
          <p style="color: var(--text-muted); font-size: 0.88rem; line-height: 1.5; margin: 0 auto 20px;">
            Abre la app <strong>0Media Signage</strong> en tu televisor o celular. Verás un <strong>código único de 6 caracteres</strong> en pantalla. Haz clic en el botón de abajo para autorizarla e iniciar el control independiente.
          </p>
          <button class="btn btn-primary" id="btn-empty-add-screen" style="padding: 12px 24px; font-size: 0.95rem; display: inline-flex; align-items: center; gap: 8px;">
            <span>➕</span> Vincular Mi Primera Pantalla
          </button>
        </div>
      `;
      const btnEmptyAdd = document.getElementById('btn-empty-add-screen');
      if (btnEmptyAdd) {
        btnEmptyAdd.addEventListener('click', () => openPairingModal());
      }
      return;
    }

    pairedDevices.forEach(device => {
      const card = document.createElement('div');
      card.className = 'device-card';
      card.id = `card-${device.id}`;

      const isOnline = device.isPaired && device.status === 'ONLINE';
      const firstItem = device.items ? device.items[0] : null;

      let mediaPreviewHtml = '';
      if (!firstItem) {
        mediaPreviewHtml = `
          <div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text-muted);font-size:0.9rem;text-align:center;padding:14px;background:#0f172a;">
            <div>
              <div style="font-size:2rem;margin-bottom:6px;">📺</div>
              <strong style="color:#FFF;">Sin Anuncios</strong>
              <div style="font-size:0.75rem;margin-top:4px;">Haz clic en 'Editar Publicidad' para subir una foto</div>
            </div>
          </div>
        `;
      } else if (firstItem.mediaType === 'VIDEO' && firstItem.url.endsWith('.mp4')) {
        mediaPreviewHtml = `<video src="${firstItem.url}" autoplay muted loop></video>`;
      } else {
        mediaPreviewHtml = `<img src="${firstItem.url}" alt="${device.name}">`;
      }

      const statusBadgeHtml = (device.isPaired && isOnline) ? `
        <div class="device-status-badge online">
          <span class="status-dot"></span>
          En Línea
        </div>
      ` : `
        <div class="device-status-badge offline" style="background: rgba(245, 158, 11, 0.15); color: #F59E0B; border-color: rgba(245, 158, 11, 0.4);">
          <span class="status-dot" style="background: #F59E0B;"></span>
          Esperando Código
        </div>
      `;

      card.innerHTML = `
        <div class="device-card-header">
          <div class="device-card-title-group">
            <div class="device-name">${device.name}</div>
            <div class="device-location">📍 ${device.location}</div>
          </div>
          ${statusBadgeHtml}
        </div>

        <div class="device-screen-preview">
          ${mediaPreviewHtml}
          <div class="preview-overlay">
            <span class="preview-format-tag">${firstItem ? (firstItem.mediaTag || firstItem.mediaType) : 'EN ESPERA'}</span>
            <span class="preview-res-tag">${device.resolution.split('@')[0]}</span>
          </div>
        </div>

        <div class="device-current-ad">
          <span class="current-ad-label">Emitiendo:</span>
          <span class="current-ad-title" title="${firstItem ? firstItem.title : 'Esperando asignación'}">${firstItem ? firstItem.title : 'Esperando tu primera foto o video'}</span>
        </div>

        <div class="device-specs-grid">
          <div class="spec-cell">
            <span class="spec-label">IP Red</span>
            <span class="spec-val">${device.ip}</span>
          </div>
          <div class="spec-cell">
            <span class="spec-label">RAM Activa</span>
            <span class="spec-val">${device.ramUsage.split('/')[0].trim()}</span>
          </div>
          <div class="spec-cell">
            <span class="spec-label">Disco Libre</span>
            <span class="spec-val">${device.freeDisk}</span>
          </div>
        </div>

        <div class="device-card-footer">
          <button class="btn btn-primary btn-manage-campaign" data-id="${device.id}" title="Añadir videos, fotos o GIFs a este televisor">
            <span>🎬</span> Editar Publicidad (${device.items.length})
          </button>
          <div class="device-secondary-btns">
            <button class="btn btn-secondary btn-alert-device" data-id="${device.id}" title="Enviar mensaje de alerta">📢</button>
            <button class="btn btn-secondary btn-sync-device" data-id="${device.id}" title="Sincronizar ahora">🔄</button>
            <button class="btn btn-danger btn-revoke-device" data-id="${device.id}" title="Desvincular">🗑️</button>
          </div>
        </div>
      `;

      devicesContainer.appendChild(card);
    });

    // Delegación y eventos de botones en cada card
    devicesContainer.querySelectorAll('.btn-quick-open-modal').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const code = e.currentTarget.getAttribute('data-code');
        const name = e.currentTarget.getAttribute('data-name');
        openPairingModal(code, name);
      });
    });

    devicesContainer.querySelectorAll('.btn-manage-campaign').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.getAttribute('data-id');
        currentSelectedTvId = id;
        switchView('playlists');
        const dev = devices.find(d => d.id === id);
        showToast(`📺 Abierta lista de campaña para: "${dev ? dev.name : id}"`);
      });
    });

    devicesContainer.querySelectorAll('.btn-sync-device').forEach(btn => {
      btn.addEventListener('click', () => {
        const dev = devices.find(d => d.id === currentSelectedTvId);
        fetch('/api/v1/devices/sync/manifest').catch(() => {});
        showToast(`🔄 Sincronización forzada enviada a "${dev ? dev.name : 'Televisor'}".`);
      });
    });

    devicesContainer.querySelectorAll('.btn-alert-device').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.getAttribute('data-id');
        const dev = devices.find(d => d.id === id);
        const msg = prompt(`Escribe el mensaje de cintillo urgente para "${dev.name}":`, "¡OFERTA ESPECIAL 2X1 SOLO POR HOY!");
        if (msg) {
          showToast(`📢 Cintillo enviado a ${dev.name}: "${msg}"`, '#2563EB');
        }
      });
    });

    devicesContainer.querySelectorAll('.btn-revoke-device').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const id = e.currentTarget.getAttribute('data-id');
        const dev = devices.find(d => d.id === id);
        if (confirm(`¿Estás seguro de que deseas desvincular "${dev ? dev.name : 'este dispositivo'}"? La pantalla regresará inmediatamente al código QR.`)) {
          try {
            await apiFetch('/api/v1/admin/revoke-pairing', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ deviceId: id })
            });
          } catch (err) {}
          await loadDevicesFromBackend();
          showToast(`🔒 "${dev ? dev.name : 'Pantalla'}" desvinculada con éxito.`, '#EF4444');
        }
      });
    });
  }

  // ================= 6. VISTA 2: SELECTOR DE TELEVISOR Y LISTA DE CAMPAÑAS =================
  function renderTvSelector() {
    if (!campaignTvSelect || devices.length === 0) {
      if (campaignTvSelect) campaignTvSelect.innerHTML = '<option value="">(No hay pantallas vinculadas)</option>';
      if (tvChipsContainer) tvChipsContainer.innerHTML = '';
      if (tvBannerName) tvBannerName.textContent = 'Ningún Televisor Seleccionado';
      if (tvBannerMeta) tvBannerMeta.innerHTML = '<span>Vincula una pantalla para comenzar a emitir publicidad</span>';
      if (tvBannerAdCount) tvBannerAdCount.textContent = '0 Activos';
      if (tvBannerCycleTime) tvBannerCycleTime.textContent = '0s';
      if (tvItemsBadge) tvItemsBadge.textContent = '0 en Carrusel';
      if (simulatorTvTag) simulatorTvTag.textContent = 'TV: Sin Pantalla';
      if (tvPlaylistItemsContainer) {
        tvPlaylistItemsContainer.innerHTML = `
          <div style="text-align: center; padding: 50px 20px; background: var(--bg-card); border-radius: 16px; border: 1px dashed var(--border-subtle);">
            <div style="font-size: 2.8rem; margin-bottom: 12px;">📺 🔒</div>
            <h5 style="color: #FFF; font-size: 1.15rem; margin-bottom: 8px;">No hay pantallas vinculadas todavía</h5>
            <p style="color: var(--text-muted); font-size: 0.88rem; max-width: 480px; margin: 0 auto 18px;">
              Primero vincula tu televisor o celular con su código de 6 caracteres para poder subir fotos y videos a su programación exclusiva.
            </p>
            <button class="btn btn-primary" id="btn-empty-link-screen-pl" style="padding: 10px 20px; font-size: 0.9rem;">
              <span>🔗</span> Vincular Pantalla Ahora
            </button>
          </div>
        `;
        const btnPl = document.getElementById('btn-empty-link-screen-pl');
        if (btnPl) btnPl.addEventListener('click', () => openPairingModal());
      }
      if (simulatorMediaContainer) {
        simulatorMediaContainer.innerHTML = `
          <div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text-muted);font-size:0.9rem;text-align:center;padding:20px;">
            <div>
              <div style="font-size:2.5rem;margin-bottom:8px;">📱 📺</div>
              <strong style="color:#FFF;">Esperando Pantalla</strong>
              <div style="font-size:0.75rem;margin-top:4px;color:#94a3b8;">Vincula un dispositivo para ver su emisión aquí</div>
            </div>
          </div>
        `;
      }
      return;
    }

    // 1. Selector desplegable
    campaignTvSelect.innerHTML = '';
    devices.forEach(dev => {
      const opt = document.createElement('option');
      opt.value = dev.id;
      opt.textContent = `📺 ${dev.name} (${dev.items.length} anuncios) — ${dev.location}`;
      if (dev.id === currentSelectedTvId) {
        opt.selected = true;
      }
      campaignTvSelect.appendChild(opt);
    });

    // 2. Barra de Chips interactivos
    if (tvChipsContainer) {
      tvChipsContainer.innerHTML = '';
      devices.forEach(dev => {
        const chip = document.createElement('div');
        chip.className = `tv-chip ${dev.id === currentSelectedTvId ? 'active' : ''}`;
        chip.innerHTML = `<span>📺</span> <span>${dev.name.split('—')[0].trim()}</span> <span style="font-size: 0.72rem; opacity: 0.8; font-family: var(--font-mono);">(${dev.items.length})</span>`;
        chip.addEventListener('click', () => {
          currentSelectedTvId = dev.id;
          renderTvSelector();
        });
        tvChipsContainer.appendChild(chip);
      });
    }

    // 3. Banner del TV seleccionado
    const currentDevice = devices.find(d => d.id === currentSelectedTvId) || devices[0];
    if (currentDevice) {
      currentSelectedTvId = currentDevice.id;
      campaignTvSelect.value = currentDevice.id;

      if (tvBannerName) tvBannerName.textContent = currentDevice.name;
      if (tvBannerMeta) {
        tvBannerMeta.innerHTML = `
          <span>📍 ${currentDevice.location}</span>
          <span>• IP: ${currentDevice.ip}</span>
          <span>• Código de Vinculación: <strong style="color:var(--primary); font-family:var(--font-mono); font-size:1rem;">${currentDevice.pairingCode || 'A7X9K2'}</strong></span>
        `;
      }

      let cycleTime = 0;
      let activeCount = 0;
      currentDevice.items.forEach(item => {
        if (item.isEnabled !== false) {
          cycleTime += (parseInt(item.durationSeconds) || 15);
          activeCount++;
        }
      });

      const avgTime = activeCount > 0 ? Math.round(cycleTime / activeCount) : 0;
      if (tvBannerAdCount) tvBannerAdCount.textContent = `${activeCount} Activos`;
      if (tvBannerCycleTime) tvBannerCycleTime.textContent = `${cycleTime}s`;
      if (tvItemsBadge) tvItemsBadge.textContent = `${activeCount} en Carrusel (${currentDevice.items.length} Totales)`;
      if (simulatorTvTag) simulatorTvTag.textContent = `TV: ${currentDevice.name.split('—')[0].trim()}`;
      if (statCycleSeconds) statCycleSeconds.textContent = `${cycleTime}s`;
      if (statAvgSeconds) statAvgSeconds.textContent = `${avgTime}s`;

      // Resaltar píldora global correspondiente si coincide
      if (globalDurationPills) {
        globalDurationPills.querySelectorAll('.btn-time-pill').forEach(btn => {
          const sec = parseInt(btn.getAttribute('data-seconds'));
          if (sec === avgTime) {
            btn.classList.add('active');
          } else {
            btn.classList.remove('active');
          }
        });
      }

      // 4. Renderizar lista secuencial de anuncios
      renderTvPlaylist(currentDevice);

      // 5. Configurar simulador de TV con los contenidos de este TV
      setupSimulatorForTv(currentDevice);
    }
  }

  function renderTvPlaylist(device) {
    if (!tvPlaylistItemsContainer) return;
    tvPlaylistItemsContainer.innerHTML = '';

    if (device.items.length === 0) {
      tvPlaylistItemsContainer.innerHTML = `
        <div style="text-align: center; padding: 40px; background: var(--bg-card); border-radius: 16px; border: 1px dashed var(--border-subtle);">
          <div style="font-size: 2.2rem; margin-bottom: 10px;">📭</div>
          <h5 style="color: #FFF; font-size: 1.1rem; margin-bottom: 6px;">Este televisor no tiene anuncios programados</h5>
          <p style="color: var(--text-muted); font-size: 0.85rem; max-width: 440px; margin: 0 auto 16px;">
            Haz clic en <strong>"📂 Seleccionar Videos o Fotos"</strong> a la izquierda o arrastra tus imágenes para comenzar la transmisión en carrusel en esta pantalla.
          </p>
        </div>
      `;
      return;
    }

    device.items.forEach((item, index) => {
      const card = document.createElement('div');
      const isBlocked = item.isEnabled === false;
      card.className = `playlist-item-card ${isBlocked ? 'is-blocked' : ''}`;

      const tagClass = item.mediaType === 'VIDEO' ? 'format-video' : (item.mediaType === 'GIF' ? 'format-gif' : 'format-image');
      const itemDur = parseInt(item.durationSeconds) || 15;

      card.innerHTML = `
        <div class="item-index-badge">#${index + 1}</div>
        <div class="item-thumb-box">
          ${item.mediaType === 'VIDEO' && item.url.endsWith('.mp4') ? 
            `<video src="${item.url}" muted style="width:100%;height:100%;object-fit:cover;"></video>` :
            `<img src="${item.url}" alt="${item.title}">`
          }
          <span class="item-thumb-tag">${item.mediaTag || item.mediaType}</span>
        </div>

        <div class="item-info-col">
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 3px;">
            <div class="item-title-text" style="margin-bottom: 0;">${item.title}</div>
            <span class="badge-status-pill ${isBlocked ? 'blocked' : 'active'}">
              ${isBlocked ? '🔒 Bloqueado / Oculto' : '🟢 En Carrusel'}
            </span>
          </div>

          <div class="item-meta-row">
            <span class="format-pill ${tagClass}" style="font-size: 0.68rem; padding: 2px 8px;">${item.mediaTag || item.mediaType}</span>
            <span>• ${item.sizeText || 'Optimizado'}</span>
          </div>

          <!-- Selector de Tiempo Simple: solo 5s, 10s, 15s, 20s, 30s, 45s, 60s -->
          <div class="item-duration-row">
            <span class="item-duration-label">⏱️ Duración:</span>
            <div class="duration-preset-pills duration-card-pills" data-idx="${index}">
              ${[5, 10, 15, 20, 30, 45, 60].map(sec => `
                <button type="button" class="btn-time-pill mini ${itemDur === sec ? 'active' : ''}" data-sec="${sec}">${sec}s</button>
              `).join('')}
            </div>
          </div>
        </div>

        <div class="item-actions-col">
          <button class="btn-icon-action btn-toggle-block ${isBlocked ? 'blocked' : 'active'}" data-idx="${index}" title="${isBlocked ? 'Desbloquear: proyectar en el carrusel de la TV' : 'Bloquear: pausar temporalmente de la TV'}">
            ${isBlocked ? '🔒' : '👁️'}
          </button>
          <button class="btn-icon-action btn-move-up" data-idx="${index}" title="Subir orden" ${index === 0 ? 'disabled style="opacity:0.3;cursor:default;"' : ''}>▲</button>
          <button class="btn-icon-action btn-move-down" data-idx="${index}" title="Bajar orden" ${index === device.items.length - 1 ? 'disabled style="opacity:0.3;cursor:default;"' : ''}>▼</button>
          <button class="btn-icon-action btn-preview-item" data-idx="${index}" title="Previsualizar en grande">🔍</button>
          <button class="btn-icon-action danger btn-delete-item" data-idx="${index}" title="Eliminar anuncio de este televisor">🗑️</button>
        </div>
      `;

      tvPlaylistItemsContainer.appendChild(card);
    });

    // Ajuste directo al hacer clic en cualquiera de las 7 opciones (5s, 10s, 15s, 20s, 30s, 45s, 60s)
    tvPlaylistItemsContainer.querySelectorAll('.duration-card-pills .btn-time-pill').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const parentPills = e.currentTarget.closest('.duration-card-pills');
        const idx = parseInt(parentPills.getAttribute('data-idx'));
        const item = device.items[idx];
        const newDur = parseInt(e.currentTarget.getAttribute('data-sec')) || 15;
        if (item) {
          item.durationSeconds = newDur;
          saveDevices();
          try {
            await apiFetch('/api/v1/devices/update-ad-duration', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ itemId: item.id, durationSeconds: newDur })
            });
            showToast(`⏱️ "${item.title}": tiempo cambiado a ${newDur}s.`, '#10B981');
          } catch (err) {}
        }
      });
    });

    // Toggle Bloquear / Activar Anuncio
    tvPlaylistItemsContainer.querySelectorAll('.btn-toggle-block').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-idx'));
        const item = device.items[idx];
        const newStatus = !(item.isEnabled !== false);
        item.isEnabled = newStatus;
        saveDevices();
        renderTvSelector();

        try {
          await apiFetch('/api/v1/devices/toggle-block-ad', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ itemId: item.id, isEnabled: newStatus })
          });
          showToast(newStatus ? `🟢 "${item.title}" activado en el carrusel.` : `🔒 "${item.title}" bloqueado del carrusel.`, newStatus ? '#10B981' : '#F59E0B');
        } catch (err) {
          showToast('⚠️ No se pudo guardar el cambio en el servidor.', '#EF4444');
        }
      });
    });

    // Acciones de los items de la lista
    tvPlaylistItemsContainer.querySelectorAll('.btn-move-up').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-idx'));
        if (idx > 0) {
          const temp = device.items[idx];
          device.items[idx] = device.items[idx - 1];
          device.items[idx - 1] = temp;
          saveDevices();
          showToast(`⬆️ Reordenado: "${temp.title}" ahora es el anuncio #${idx}`);
        }
      });
    });

    tvPlaylistItemsContainer.querySelectorAll('.btn-move-down').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-idx'));
        if (idx < device.items.length - 1) {
          const temp = device.items[idx];
          device.items[idx] = device.items[idx + 1];
          device.items[idx + 1] = temp;
          saveDevices();
          showToast(`⬇️ Reordenado: "${temp.title}" ahora es el anuncio #${idx + 2}`);
        }
      });
    });

    tvPlaylistItemsContainer.querySelectorAll('.btn-preview-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-idx'));
        const item = device.items[idx];
        openPreviewModal(item, device);
      });
    });

    tvPlaylistItemsContainer.querySelectorAll('.btn-delete-item').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const idx = parseInt(e.currentTarget.getAttribute('data-idx'));
        const item = device.items[idx];
        if (confirm(`¿Eliminar "${item.title}" de la programación de "${device.name}"?`)) {
          device.items.splice(idx, 1);
          saveDevices();

          try {
            await apiFetch('/api/v1/devices/delete-ad', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ itemId: item.id })
            });
          } catch (err) {}

          showToast(`🗑️ Anuncio eliminado de ${device.name}.`, '#EF4444');
        }
      });
    });
  }

  // ================= 7. SIMULADOR EN VIVO (Solo contenido activo en carrusel) =================
  function setupSimulatorForTv(device) {
    if (!simulatorMediaContainer || !simulatorIndicators) return;

    if (simulatorTimer) {
      clearInterval(simulatorTimer);
      simulatorTimer = null;
    }

    simulatorMediaContainer.innerHTML = '';
    simulatorIndicators.innerHTML = '';
    currentSimIndex = 0;

    // Solo se proyectan los elementos activos en el carrusel
    const items = (device.items || []).filter(it => it.isEnabled !== false);
    if (items.length === 0) {
      const hasBlocked = (device.items || []).length > 0;
      simulatorMediaContainer.innerHTML = `
        <div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text-muted);font-size:1.1rem;text-align:center;padding:20px;">
          <div>
            <div style="font-size:3rem;margin-bottom:10px;">${hasBlocked ? '🔒' : '📺'}</div>
            <strong style="color:#FFF;">${hasBlocked ? 'Todos los anuncios están bloqueados' : 'Carrusel Vacío'}</strong>
            <div style="font-size:0.85rem;margin-top:6px;">${hasBlocked ? 'Desbloquea algún anuncio con el botón 👁️ para proyectarlo' : 'Sube fotos o videos para proyectar en esta pantalla'}</div>
          </div>
        </div>
      `;
      if (simTimerPill) simTimerPill.textContent = hasBlocked ? "Carrusel Pausado" : "0 / 0";
      return;
    }

    items.forEach((item, idx) => {
      let el;
      if (item.mediaType === 'VIDEO' && item.url.endsWith('.mp4')) {
        el = document.createElement('video');
        el.src = item.url;
        el.muted = true;
        el.loop = true;
        el.autoplay = true;
      } else {
        el = document.createElement('img');
        el.src = item.url;
        el.alt = item.title;
      }
      el.className = idx === 0 ? 'active' : '';
      simulatorMediaContainer.appendChild(el);

      const dot = document.createElement('div');
      dot.style.width = '10px';
      dot.style.height = '10px';
      dot.style.borderRadius = '50%';
      dot.style.background = idx === 0 ? 'var(--primary)' : 'rgba(255,255,255,0.2)';
      dot.style.transition = 'all 0.3s';
      dot.style.cursor = 'pointer';
      dot.addEventListener('click', () => {
        showSimSlide(idx, items);
      });
      simulatorIndicators.appendChild(dot);
    });

    updateSimPill(0, items, device.items.length);
    startSimLoop(items);
  }

  function showSimSlide(index, items) {
    if (!items || items.length === 0) return;
    const mediaEls = simulatorMediaContainer.querySelectorAll('img, video');
    const dots = simulatorIndicators.children;

    if (mediaEls[currentSimIndex]) mediaEls[currentSimIndex].classList.remove('active');
    if (dots[currentSimIndex]) {
      dots[currentSimIndex].style.background = 'rgba(255,255,255,0.2)';
      dots[currentSimIndex].style.transform = 'scale(1)';
    }

    currentSimIndex = (index + items.length) % items.length;

    if (mediaEls[currentSimIndex]) {
      mediaEls[currentSimIndex].classList.add('active');
      if (mediaEls[currentSimIndex].tagName === 'VIDEO') {
        mediaEls[currentSimIndex].currentTime = 0;
        mediaEls[currentSimIndex].play().catch(() => {});
      }
    }

    if (dots[currentSimIndex]) {
      dots[currentSimIndex].style.background = 'var(--primary)';
      dots[currentSimIndex].style.transform = 'scale(1.3)';
    }

    updateSimPill(currentSimIndex, items);
  }

  function updateSimPill(idx, items, totalCount = items.length) {
    if (!simTimerPill) return;
    const item = items[idx];
    if (item) {
      simTimerPill.innerHTML = `Carrusel: <strong>${idx + 1} / ${items.length} activos</strong> (${totalCount} en lista) • ${item.durationSeconds}s`;
    }
  }

  function startSimLoop(items) {
    if (simulatorTimer) clearInterval(simulatorTimer);
    if (items.length <= 1) return;

    simulatorTimer = setInterval(() => {
      showSimSlide(currentSimIndex + 1, items);
    }, 4500);
  }

  // ================= 8. SISTEMA DE SUBIDA MULTIMEDIA (INDIVIDUAL O CARRUSEL MULTI-ARCHIVO) =================
  function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target.result);
      reader.onerror = () => reject(new Error("Error leyendo " + file.name));
      reader.readAsDataURL(file);
    });
  }

  function detectMediaAttributes(file) {
    const mime = (file.type || '').toLowerCase();
    const name = file.name || 'publicidad';
    let type = "IMAGE";
    let tag = "FOTO 4K";
    let defaultDuration = 15;

    if (mime.includes('gif') || name.toLowerCase().endsWith('.gif')) {
      type = "GIF";
      tag = "GIF ANIMADO";
      defaultDuration = 10;
    } else if (mime.includes('video') || name.toLowerCase().endsWith('.mp4') || name.toLowerCase().endsWith('.webm')) {
      type = "VIDEO";
      tag = "VIDEO MP4";
      defaultDuration = 20;
    } else if (name.toLowerCase().endsWith('.png') || mime.includes('png')) {
      type = "IMAGE";
      tag = "FOTO PNG";
      defaultDuration = 15;
    }
    return { type, tag, defaultDuration, title: name.replace(/\.[^/.]+$/, "") };
  }

  async function handleMultipleFilesSelection(files) {
    if (!files || files.length === 0) return;
    const fileList = Array.from(files);

    const targetTv = devices.find(d => d.id === currentSelectedTvId) || devices[0];
    const checkReplace = document.getElementById('check-replace-previous');
    const shouldReplaceAll = checkReplace ? checkReplace.checked : false;

    showToast(`⏳ Procesando ${fileList.length} archivo(s) para el carrusel...`, '#2563EB');

    let successCount = 0;
    for (let i = 0; i < fileList.length; i++) {
      const file = fileList[i];
      const isFirst = i === 0;
      const replaceThis = isFirst ? shouldReplaceAll : false;
      const meta = detectMediaAttributes(file);

      try {
        const dataUrl = await readFileAsDataUrl(file);
        const res = await apiFetch('/api/v1/devices/upload-ad', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: meta.title,
            durationSeconds: meta.defaultDuration,
            targetTvId: targetTv.id,
            mediaType: meta.type,
            mediaTag: meta.tag,
            fileName: file.name,
            dataUrl: dataUrl,
            replacePrevious: replaceThis
          })
        });

        const resData = await res.json();
        if (resData.success && resData.item) {
          if (replaceThis) {
            targetTv.items = [resData.item];
          } else {
            targetTv.items.push(resData.item);
          }
          successCount++;
        }
      } catch (err) {
        console.error(`[UPLOAD ERROR] ${file.name}:`, err);
      }
    }

    if (successCount > 0) {
      saveDevices();
      renderTvSelector();
      setupSimulatorForTv(targetTv);
      showToast(`🎉 ¡${successCount} archivo(s) añadidos al carrusel con éxito!`, '#10B981');
    } else {
      showToast(`⚠️ No se pudieron subir los archivos seleccionados.`, '#EF4444');
    }
  }

  function handleFileSelection(file) {
    handleMultipleFilesSelection([file]);
  }

  function openUploadModal(defaultTitle) {
    if (!uploadModal) return;

    if (inputAdTitle) inputAdTitle.value = defaultTitle;
    if (inputAdType) {
      const ext = currentPendingFile?.name ? currentPendingFile.name.split('.').pop().toUpperCase() : 'IMG';
      inputAdType.value = `${currentDetectedTag} (.${ext.toLowerCase()})`;
    }

    // Llenar selector con los televisores actuales
    if (selectTargetTvModal) {
      selectTargetTvModal.innerHTML = '';
      devices.forEach(dev => {
        const opt = document.createElement('option');
        opt.value = dev.id;
        opt.textContent = `${dev.name} (${dev.location})`;
        if (dev.id === currentSelectedTvId) {
          opt.selected = true;
        }
        selectTargetTvModal.appendChild(opt);
      });
    }

    // Renderizar previsualización en el modal
    if (uploadPreviewContainer) {
      uploadPreviewContainer.innerHTML = '';
      if (currentDetectedType === 'VIDEO') {
        const video = document.createElement('video');
        video.src = currentPendingDataUrl;
        video.controls = true;
        video.autoplay = true;
        video.muted = true;
        video.style.width = '100%';
        video.style.height = '100%';
        video.style.objectFit = 'contain';
        uploadPreviewContainer.appendChild(video);
      } else {
        const img = document.createElement('img');
        img.src = currentPendingDataUrl;
        img.style.width = '100%';
        img.style.height = '100%';
        img.style.objectFit = 'contain';
        uploadPreviewContainer.appendChild(img);
      }
    }

    uploadModal.classList.add('open');
  }

  function closeUploadModal() {
    if (uploadModal) uploadModal.classList.remove('open');
    if (mediaFileInput) mediaFileInput.value = '';
    currentPendingFile = null;
    currentPendingDataUrl = null;
  }

  // ================= 9. MODAL DE PREVISUALIZACIÓN MULTIMEDIA =================
  function openPreviewModal(item, device) {
    if (!previewModal) return;

    if (previewModalTitle) previewModalTitle.textContent = `${item.title} — ${device.name}`;
    if (fullMediaViewer) {
      fullMediaViewer.innerHTML = '';
      if (item.mediaType === 'VIDEO') {
        const vid = document.createElement('video');
        vid.src = item.url;
        vid.controls = true;
        vid.autoplay = true;
        vid.style.width = '100%';
        vid.style.height = '100%';
        vid.style.objectFit = 'contain';
        fullMediaViewer.appendChild(vid);
      } else {
        const img = document.createElement('img');
        img.src = item.url;
        img.style.width = '100%';
        img.style.height = '100%';
        img.style.objectFit = 'contain';
        fullMediaViewer.appendChild(img);
      }
    }

    if (previewMetaRow) {
      previewMetaRow.innerHTML = `
        <span>Tipo: <strong style="color:var(--primary);">${item.mediaTag || item.mediaType}</strong></span>
        <span>Duración: <strong>${item.durationSeconds} segundos</strong></span>
        <span>Televisor: <strong>${device.name}</strong></span>
      `;
    }

    previewModal.classList.add('open');
  }

  function closePreviewModal() {
    if (previewModal) previewModal.classList.remove('open');
    if (fullMediaViewer) fullMediaViewer.innerHTML = '';
  }

  // ================= 10. MODAL DE VINCULACIÓN CON PIN =================
  function openPairingModal(prefillCode = null, prefillName = null) {
    if (!pairingModal) return;
    pairingModalDismissed = false;
    sessionStorage.removeItem('0media_admin_pairing_dismissed');
    pairingModal.classList.add('open');

    if (prefillCode && prefillCode.length >= 6) {
      prefillCode.toUpperCase().split('').forEach((ch, idx) => {
        if (pinInputs[idx]) pinInputs[idx].value = ch;
      });
      if (pinInputs[5]) pinInputs[5].focus();
    } else {
      if (pinInputs[0]) pinInputs[0].focus();
    }

    if (prefillName) {
      const inputName = document.getElementById('input-device-name');
      if (inputName) inputName.value = prefillName;
    }
  }

  function closePairingModal() {
    if (!pairingModal) return;
    pairingModalDismissed = true;
    sessionStorage.setItem('0media_admin_pairing_dismissed', 'true');
    pairingModal.classList.remove('open');
    pinInputs.forEach(input => { if (input) input.value = ''; });
  }

  // ================= 10B. MODAL DE MODIFICACIÓN DE DURACIÓN =================
  let currentEditingItem = null;

  function openDurationModal(item) {
    if (!durationModal || !item) return;
    currentEditingItem = item;

    if (editDurationItemId) editDurationItemId.value = item.id;
    if (durationModalTitle) durationModalTitle.textContent = item.title;
    const currentDevice = devices.find(d => d.id === currentSelectedTvId) || devices[0];
    if (durationModalMeta) durationModalMeta.textContent = `${item.mediaTag || item.mediaType} • ${currentDevice ? currentDevice.name : 'Televisor'}`;

    if (durationModalThumb) {
      if (item.mediaType === 'VIDEO' && item.url.endsWith('.mp4')) {
        durationModalThumb.innerHTML = `<video src="${item.url}" muted style="width:100%;height:100%;object-fit:cover;"></video>`;
      } else {
        durationModalThumb.innerHTML = `<img src="${item.url}" alt="${item.title}" style="width:100%;height:100%;object-fit:cover;">`;
      }
    }

    const currentSec = parseInt(item.durationSeconds) || 15;
    if (inputSingleDuration) inputSingleDuration.value = currentSec;
    if (checkApplyDurationAll) checkApplyDurationAll.checked = false;

    // Actualizar píldora activa en el modal
    updateModalPresetHighlight(currentSec);

    durationModal.classList.add('open');
  }

  function closeDurationModal() {
    if (durationModal) durationModal.classList.remove('open');
    currentEditingItem = null;
  }

  function updateModalPresetHighlight(sec) {
    if (!modalDurationPills) return;
    modalDurationPills.querySelectorAll('.btn-time-pill').forEach(btn => {
      const bSec = parseInt(btn.getAttribute('data-sec'));
      if (bSec === sec) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });
  }

  // ================= 11. REGISTRO DE EVENT LISTENERS =================

  // Navegación Sidebar
  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      const targetView = item.getAttribute('data-view');
      switchView(targetView);
    });
  });

  window.addEventListener('hashchange', handleHash);

  // Refrescar pantallas
  if (btnRefreshDevices) {
    btnRefreshDevices.addEventListener('click', async () => {
      try {
        await apiFetch('/api/v1/devices/sync-now', { method: 'POST' });
      } catch (e) {}
      await loadDevicesFromBackend();
      renderDevices();
      updateStats();
      showToast("🔄 ¡Pantallas y sincronización actualizadas desde el servidor!", '#10B981');
    });
  }

  // Cambio de TV en selector
  if (campaignTvSelect) {
    campaignTvSelect.addEventListener('change', (e) => {
      currentSelectedTvId = e.target.value;
      renderTvSelector();
    });
  }

  // Controles del Simulador
  if (btnSimPrev) {
    btnSimPrev.addEventListener('click', () => {
      const currentDevice = devices.find(d => d.id === currentSelectedTvId);
      if (currentDevice && currentDevice.items.length > 0) {
        showSimSlide(currentSimIndex - 1, currentDevice.items);
      }
    });
  }

  if (btnSimNext) {
    btnSimNext.addEventListener('click', () => {
      const currentDevice = devices.find(d => d.id === currentSelectedTvId);
      if (currentDevice && currentDevice.items.length > 0) {
        showSimSlide(currentSimIndex + 1, currentDevice.items);
      }
    });
  }

  // --- SUBIDA DE ARCHIVOS ---
  if (mediaFileInput) {
    mediaFileInput.addEventListener('click', () => {
      mediaFileInput.value = ''; // Limpiar para permitir volver a subir el mismo archivo
    });

    mediaFileInput.addEventListener('change', (e) => {
      if (e.target.files && e.target.files.length > 0) {
        handleMultipleFilesSelection(e.target.files);
      }
    });
  }

  if (dropZoneUpload) {
    dropZoneUpload.addEventListener('dragover', (e) => {
      e.preventDefault();
      dropZoneUpload.classList.add('dragover');
    });

    dropZoneUpload.addEventListener('dragleave', () => {
      dropZoneUpload.classList.remove('dragover');
    });

    dropZoneUpload.addEventListener('drop', (e) => {
      e.preventDefault();
      dropZoneUpload.classList.remove('dragover');
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        handleMultipleFilesSelection(e.dataTransfer.files);
      }
    });
  }

  // Eventos del modal de configuración de subida
  if (btnCloseUploadModal) btnCloseUploadModal.addEventListener('click', closeUploadModal);
  if (btnCancelUploadModal) btnCancelUploadModal.addEventListener('click', closeUploadModal);

  if (formConfirmUpload) {
    formConfirmUpload.addEventListener('submit', async (e) => {
      e.preventDefault();

      const title = (inputAdTitle ? inputAdTitle.value.trim() : '') || "Nueva Publicidad";
      const duration = (inputAdDuration ? parseInt(inputAdDuration.value) : 15) || 15;
      const targetTvId = selectTargetTvModal ? selectTargetTvModal.value : currentSelectedTvId;

      const targetTv = devices.find(d => d.id === targetTvId) || devices[0];
      if (!targetTv) return;

      if (btnSaveAdToTv) {
        btnSaveAdToTv.disabled = true;
        btnSaveAdToTv.innerHTML = `<span>⏳</span> Subiendo y Transmitiendo...`;
      }

      try {
        // Enviar la imagen directamente al backend servidor-local.js
        const response = await apiFetch('/api/v1/devices/upload-ad', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: title,
            durationSeconds: duration,
            targetTvId: targetTvId,
            mediaType: currentDetectedType,
            mediaTag: currentDetectedTag,
            fileName: currentPendingFile ? currentPendingFile.name : 'foto.png',
            dataUrl: currentPendingDataUrl,
            replacePrevious: document.getElementById('check-replace-previous') ? document.getElementById('check-replace-previous').checked : true
          })
        });

        const resData = await response.json();
        if (resData.success && resData.item) {
          const shouldReplace = document.getElementById('check-replace-previous') ? document.getElementById('check-replace-previous').checked : true;
          if (shouldReplace) {
            targetTv.items = [resData.item];
          } else {
            targetTv.items.unshift(resData.item);
          }
          currentSelectedTvId = targetTv.id;
          saveDevices();
          setupSimulatorForTv(targetTv);
          closeUploadModal();
          showToast(`🎉 ¡"${title}" subido al servidor! El televisor lo descargará y mostrará inmediatamente.`, '#10B981');
        } else {
          throw new Error(resData.error || "Error al procesar la subida");
        }
      } catch (err) {
        console.warn("[UPLOAD ERROR] Guardando en modo local:", err.message);
        const sizeKb = currentPendingFile ? Math.round(currentPendingFile.size / 1024) : 800;
        const sizeText = sizeKb > 1024 ? `${(sizeKb / 1024).toFixed(1)} MB` : `${sizeKb} KB`;

        const newItem = {
          id: "item-" + Date.now().toString(36),
          title: title,
          mediaType: currentDetectedType,
          mediaTag: currentDetectedTag,
          url: currentPendingDataUrl,
          durationSeconds: duration,
          displayOrder: targetTv.items.length + 1,
          sizeText: `${sizeText} • Adaptado`
        };

        targetTv.items.push(newItem);
        currentSelectedTvId = targetTv.id;
        saveDevices();
        closeUploadModal();
        showToast(`🎉 ¡"${title}" guardado en la lista del televisor!`, '#10B981');
      } finally {
        if (btnSaveAdToTv) {
          btnSaveAdToTv.disabled = false;
          btnSaveAdToTv.innerHTML = `<span>➕</span> Añadir Publicidad al Televisor`;
        }
      }
    });
  }

  // Botones de Sincronización y Publicación en TV
  if (btnPushToTv) {
    btnPushToTv.addEventListener('click', async () => {
      try {
        btnPushToTv.disabled = true;
        btnPushToTv.innerHTML = `<span>⏳</span> Enviando al TV...`;
        await apiFetch('/api/v1/devices/sync-now', { method: 'POST' });
        await loadDevicesFromBackend();
        const currentDevice = devices.find(d => d.id === currentSelectedTvId);
        showToast(`🚀 ¡Sincronización enviada a "${currentDevice ? currentDevice.name : 'el televisor'}"! Actualizará en segundos.`, '#10B981');
      } catch (err) {
        showToast(`⚠️ No se pudo conectar con el servidor.`, '#EF4444');
      } finally {
        btnPushToTv.disabled = false;
        btnPushToTv.innerHTML = `<span>🚀</span> Publicar y Sincronizar en este TV`;
      }
    });
  }

  if (btnSyncAll) {
    btnSyncAll.addEventListener('click', async () => {
      try {
        btnSyncAll.disabled = true;
        await apiFetch('/api/v1/devices/sync-now', { method: 'POST' });
        await loadDevicesFromBackend();
        showToast(`🔄 ¡Sincronización global enviada a todas las pantallas!`, '#10B981');
      } catch (err) {
        showToast(`⚠️ Error al sincronizar.`, '#EF4444');
      } finally {
        btnSyncAll.disabled = false;
      }
    });
  }

  // Botón para borrar todas las fotos de la pantalla
  const btnClearAllTvAds = document.getElementById('btn-clear-all-tv-ads');
  if (btnClearAllTvAds) {
    btnClearAllTvAds.addEventListener('click', async () => {
      if (confirm(`¿Estás seguro de eliminar todas las publicidades asignadas a esta pantalla? El televisor pasará a modo de espera.`)) {
        try {
          btnClearAllTvAds.disabled = true;
          await apiFetch('/api/v1/devices/clear-all-ads', { method: 'POST' });
          const currentDevice = devices.find(d => d.id === currentSelectedTvId);
          if (currentDevice) {
            currentDevice.items = [];
          }
          saveDevices();
          await loadDevicesFromBackend();
          showToast(`🗑️ Todas las publicidades han sido eliminadas. Televisor en espera.`, '#EF4444');
        } catch (err) {
          showToast(`⚠️ Error al eliminar publicidades.`, '#EF4444');
        } finally {
          btnClearAllTvAds.disabled = false;
        }
      }
    });
  }

  // Modal de Previsualización
  if (btnClosePreviewModal) btnClosePreviewModal.addEventListener('click', closePreviewModal);

  // Modal de Vinculación
  if (btnOpenModal) btnOpenModal.addEventListener('click', openPairingModal);
  if (btnCloseModal) btnCloseModal.addEventListener('click', closePairingModal);
  if (btnCancelModal) btnCancelModal.addEventListener('click', closePairingModal);

  pinInputs.forEach((input, index) => {
    if (!input) return;
    input.addEventListener('input', (e) => {
      const val = e.target.value.toUpperCase();
      input.value = val;
      if (val && index < 5 && pinInputs[index + 1]) {
        pinInputs[index + 1].focus();
      }
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !input.value && index > 0 && pinInputs[index - 1]) {
        pinInputs[index - 1].focus();
      }
    });
  });

  if (formPair) {
    formPair.addEventListener('submit', async (e) => {
      e.preventDefault();
      const pin = pinInputs.map(input => input ? input.value : '').join('').trim().toUpperCase();
      if (pin.length < 6) {
        alert("Por favor ingresa los 6 caracteres del código de vinculación que muestra la pantalla.");
        return;
      }

      const inputName = document.getElementById('input-device-name');
      const inputLocation = document.getElementById('input-device-location');

      const name = (inputName ? inputName.value.trim() : '') || "Mi Pantalla TV";
      const location = (inputLocation ? inputLocation.value.trim() : '') || "Sala Principal";

      // Autorizar en el backend servidor-local.js
      try {
        const response = await apiFetch('/api/v1/admin/authorize-pairing', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code: pin, name: name, location: location })
        });
        const resData = await response.json();
        if (!resData.success) {
          alert(resData.error || `No se encontró ningún dispositivo con el código "${pin}".`);
          return;
        }
        if (resData.device) {
          currentSelectedTvId = resData.device.id;
        }
      } catch (err) {
        alert("Error al conectar con el servidor: " + err.message);
        return;
      }

      // Marcar sesión de navegador como vinculada
      sessionStorage.setItem('0media_admin_session_paired', 'true');

      await loadDevicesFromBackend();
      closePairingModal();

      // Cambiar inmediatamente a la vista de listas de campañas para añadir anuncios
      switchView('playlists');

      showToast(`🎉 ¡Pantalla "${name}" vinculada con éxito! Inició sin publicidad (0 anuncios). Añade tus fotos o videos.`, '#10B981');
    });
  }

  // Botón en topbar para desvincular / volver a pedir código
  const btnRevokeSession = document.getElementById('btn-revoke-session');
  if (btnRevokeSession) {
    btnRevokeSession.addEventListener('click', async () => {
      if (confirm('¿Deseas desvincular el televisor y cerrar la sesión actual? El televisor volverá a pedir escanear el QR o ingresar el código de 6 caracteres.')) {
        try {
          await apiFetch('/api/v1/admin/revoke-pairing', { method: 'POST' });
        } catch (e) {}
        sessionStorage.removeItem('0media_admin_session_paired');
        devices.forEach(d => {
          d.isPaired = false;
          d.status = 'STANDBY';
        });
        saveDevices();
        openPairingModal();
        showToast('🔒 Sesión cerrada. El televisor y el panel vuelven a pedir código o QR.', '#EF4444');
      }
    });
  }

  // Al cerrar la pestaña o el navegador del panel de administración, revocar la sesión
  window.addEventListener('beforeunload', () => {
    sessionStorage.removeItem('0media_admin_session_paired');
    try {
      navigator.sendBeacon('/api/v1/admin/revoke-pairing');
    } catch (e) {}
  });

  // Detección automática al escanear QR (URL: ?code=A7X9K2)
  const urlParams = new URLSearchParams(window.location.search);
  const codeParam = urlParams.get('code');
  if (codeParam && codeParam.length >= 6) {
    codeParam.substring(0, 6).toUpperCase().split('').forEach((ch, idx) => {
      if (pinInputs[idx]) pinInputs[idx].value = ch;
    });
    openPairingModal();
  }

  // --- CONTROL DE DURACIÓN DE PUBLICIDAD ---
  // Modal de Duración
  if (btnCloseDurationModal) btnCloseDurationModal.addEventListener('click', closeDurationModal);
  if (btnCancelDurationModal) btnCancelDurationModal.addEventListener('click', closeDurationModal);

  // Píldoras de selección en el modal de duración: solo 5s, 10s, 15s, 20s, 30s, 45s, 60s
  if (modalDurationPills) {
    modalDurationPills.querySelectorAll('.btn-time-pill').forEach(btn => {
      btn.addEventListener('click', (e) => {
        modalDurationPills.querySelectorAll('.btn-time-pill').forEach(b => b.classList.remove('active'));
        e.currentTarget.classList.add('active');
        const sec = e.currentTarget.getAttribute('data-sec');
        if (inputSingleDuration) inputSingleDuration.value = sec;
      });
    });
  }

  // Guardar duración en modal
  if (formEditDuration) {
    formEditDuration.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!currentEditingItem) return;

      const newDur = parseInt(inputSingleDuration ? inputSingleDuration.value : 15) || 15;
      const applyToAll = checkApplyDurationAll ? checkApplyDurationAll.checked : false;
      const currentDevice = devices.find(d => d.id === currentSelectedTvId) || devices[0];

      if (applyToAll && currentDevice) {
        currentDevice.items.forEach(it => { it.durationSeconds = newDur; });
      } else if (currentEditingItem) {
        currentEditingItem.durationSeconds = newDur;
      }

      saveDevices();
      closeDurationModal();

      try {
        await apiFetch('/api/v1/devices/update-ad-duration', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            itemId: currentEditingItem.id,
            durationSeconds: newDur,
            applyToAll: applyToAll
          })
        });

        showToast(
          applyToAll ?
            `⚡ Duración de ${newDur}s aplicada a TODOS los anuncios y sincronizada en el televisor.` :
            `⏱️ "${currentEditingItem.title}": duración actualizada a ${newDur}s y enviada a la TV.`,
          '#10B981'
        );
      } catch (err) {
        showToast('⚠️ No se pudo guardar la duración en el servidor.', '#EF4444');
      }
    });
  }

  // Píldoras de ajuste global: solo 5s, 10s, 15s, 20s, 30s, 45s, 60s
  if (globalDurationPills) {
    globalDurationPills.querySelectorAll('.btn-time-pill').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const sec = parseInt(e.currentTarget.getAttribute('data-seconds')) || 15;
        const currentDevice = devices.find(d => d.id === currentSelectedTvId) || devices[0];
        if (!currentDevice) return;

        globalDurationPills.querySelectorAll('.btn-time-pill').forEach(b => b.classList.remove('active'));
        e.currentTarget.classList.add('active');

        currentDevice.items.forEach(it => { it.durationSeconds = sec; });
        saveDevices();

        try {
          await apiFetch('/api/v1/devices/update-ad-duration', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ applyToAll: true, durationSeconds: sec })
          });
          showToast(`⚡ Todos los anuncios ajustados a ${sec}s para "${currentDevice.name}".`, '#38BDF8');
        } catch (err) {
          showToast('⚠️ Error al aplicar duración global.', '#EF4444');
        }
      });
    });
  }

  // Píldoras de duración en modal de subida de nueva publicidad (upload modal)
  const uploadDurationPresets = document.getElementById('upload-duration-presets');
  if (uploadDurationPresets) {
    uploadDurationPresets.querySelectorAll('.btn-time-pill').forEach(btn => {
      btn.addEventListener('click', (e) => {
        uploadDurationPresets.querySelectorAll('.btn-time-pill').forEach(b => b.classList.remove('active'));
        e.currentTarget.classList.add('active');
        const dur = e.currentTarget.getAttribute('data-dur');
        if (inputAdDuration) inputAdDuration.value = dur;
      });
    });
  }

  // ================= GESTIÓN DE SESIÓN Y LOGIN ADMINISTRADOR =================
  const formAdminLogin = document.getElementById('form-admin-login');
  if (formAdminLogin) {
    formAdminLogin.addEventListener('submit', async (e) => {
      e.preventDefault();
      const usernameInput = document.getElementById('login-username');
      const passwordInput = document.getElementById('login-password');
      const errorAlert = document.getElementById('login-error-alert');
      const submitBtn = document.getElementById('btn-submit-login');

      const username = usernameInput ? usernameInput.value.trim() : 'admin';
      const password = passwordInput ? passwordInput.value : '';

      try {
        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<span>⏳</span> Verificando...';
        }
        if (errorAlert) errorAlert.style.display = 'none';

        const res = await apiFetch('/api/v1/admin/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });

        const data = await res.json();
        if (data.success && data.token) {
          localStorage.setItem('0media_auth_token', data.token);
          if (passwordInput) passwordInput.value = '';
          closeLoginModal();
          showToast('✅ ¡Bienvenido! Sesión iniciada con éxito.', '#10B981');
          await loadDevicesFromBackend();
        } else {
          if (errorAlert) {
            errorAlert.innerText = data.error || 'Credenciales incorrectas.';
            errorAlert.style.display = 'block';
          }
        }
      } catch (err) {
        if (errorAlert) {
          errorAlert.innerText = err.message || 'Error de conexión con el servidor.';
          errorAlert.style.display = 'block';
        }
      } finally {
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.innerHTML = '<span>🚀</span> Entrar al Panel de Control';
        }
      }
    });
  }

  const btnAdminLogout = document.getElementById('btn-admin-logout');
  if (btnAdminLogout) {
    btnAdminLogout.addEventListener('click', async () => {
      try {
        await apiFetch('/api/v1/admin/logout', { method: 'POST' });
      } catch (e) {}
      localStorage.removeItem('0media_auth_token');
      showToast('🔒 Sesión cerrada.', '#64748B');
      openLoginModal();
    });
  }

  async function checkAuthOnStartup() {
    try {
      const res = await apiFetch('/api/v1/admin/auth-status');
      const data = await res.json();
      if (data.authEnabled && !data.isAuthenticated) {
        openLoginModal();
      }
    } catch (e) {}
  }

  // ================= 12. INICIALIZACIÓN COMPLETA =================
  renderDevices();
  updateStats();
  renderTvSelector();

  // Verificar autenticación y cargar pantallas
  checkAuthOnStartup();
  loadDevicesFromBackend();

  // Monitoreo periódico en segundo plano cada 3.5 segundos
  setInterval(() => {
    loadDevicesFromBackend();
  }, 3500);

  if (window.location.hash) {
    handleHash();
  } else {
    switchView('playlists');
  }

});
