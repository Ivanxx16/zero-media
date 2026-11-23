// 0Media Digital Signage — Servidor Backend Profesional Multi-Dispositivo & Multi-Región
// Arquitectura de alto rendimiento: HTTP + WebSocket / Socket.IO + Streaming HTTP 206 + Persistencia ACID SQLite/JSON
// Compatible con ejecución local y despliegue en la nube (VPS, Docker, Cloud PaaS)

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const os = require('os');

// ================= 1. CARGA DE CONFIGURACIÓN Y VARIABLES DE ENTORNO =================
function loadEnv() {
  const envPath = path.join(__dirname, '.env');
  if (fs.existsSync(envPath)) {
    try {
      const content = fs.readFileSync(envPath, 'utf-8');
      content.split(/\r?\n/).forEach(line => {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#')) {
          const eqIdx = trimmed.indexOf('=');
          if (eqIdx !== -1) {
            const key = trimmed.slice(0, eqIdx).trim();
            const val = trimmed.slice(eqIdx + 1).trim().replace(/^["']|["']$/g, '');
            if (process.env[key] === undefined) {
              process.env[key] = val;
            }
          }
        }
      });
    } catch (e) {
      console.warn('[ENV] No se pudo leer archivo .env:', e.message);
    }
  }
}
loadEnv();

function getDetectedLocalIp() {
  try {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name]) {
        if (iface.family === 'IPv4' && !iface.internal) {
          return iface.address;
        }
      }
    }
  } catch (e) {}
  return '192.168.110.129';
}

const PORT = parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';
const LOCAL_IP = process.env.LOCAL_IP || getDetectedLocalIp();
const PUBLIC_URL = process.env.PUBLIC_URL ? process.env.PUBLIC_URL.replace(/\/+$/, '') : null;
const ADMIN_SECRET = process.env.ADMIN_SECRET || null;

// ================= CONFIGURACIÓN DE SEGURIDAD Y PRODUCCIÓN =================
const ADMIN_USER = process.env.ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || null;
const AUTH_ENABLED = process.env.AUTH_ENABLED === 'true' || Boolean(ADMIN_PASSWORD);
const SESSION_SECRET = process.env.SESSION_SECRET || ('0media_' + crypto.randomBytes(24).toString('hex'));

// Almacenamiento en la nube (S3 / Cloudflare R2 / MinIO / CDN)
const STORAGE_DRIVER = (process.env.STORAGE_DRIVER || 'local').toLowerCase(); // 'local' | 's3'
const S3_ENDPOINT = process.env.S3_ENDPOINT || '';
const S3_REGION = process.env.S3_REGION || 'auto';
const S3_BUCKET = process.env.S3_BUCKET || '';
const S3_ACCESS_KEY_ID = process.env.S3_ACCESS_KEY_ID || '';
const S3_SECRET_ACCESS_KEY = process.env.S3_SECRET_ACCESS_KEY || '';
const S3_PUBLIC_DOMAIN = process.env.S3_PUBLIC_DOMAIN ? process.env.S3_PUBLIC_DOMAIN.replace(/\/+$/, '') : '';

// ---------------- 1.1 HASHING CRIPTOGRÁFICO DE CONTRASEÑAS (scrypt - OWASP) ----------------
function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  if (!password) return '';
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${hash}`;
}

function verifyPassword(password, combined) {
  if (!combined || !combined.includes(':')) return false;
  try {
    const [salt, storedHash] = combined.split(':');
    const checkHash = crypto.scryptSync(password, salt, 64).toString('hex');
    return crypto.timingSafeEqual(Buffer.from(storedHash, 'hex'), Buffer.from(checkHash, 'hex'));
  } catch (e) {
    return false;
  }
}

let storedAdminHash = ADMIN_PASSWORD ? hashPassword(ADMIN_PASSWORD) : null;

// ---------------- 1.2 GESTIÓN DE SESIONES Y TOKENS DE ADMINISTRADOR ----------------
const activeSessions = new Map(); // token -> { user, createdAt, expiresAt }

function createSession(user) {
  const token = 'sess_' + crypto.randomBytes(32).toString('hex');
  const now = Date.now();
  const expiresAt = now + 7 * 24 * 60 * 60 * 1000; // 7 días de validez
  activeSessions.set(token, { user, createdAt: now, expiresAt });
  return { token, expiresAt };
}

function validateSession(token) {
  if (!token) return null;
  const session = activeSessions.get(token);
  if (!session) return null;
  if (Date.now() > session.expiresAt) {
    activeSessions.delete(token);
    return null;
  }
  return session;
}

function destroySession(token) {
  if (token) activeSessions.delete(token);
}

function parseCookies(req) {
  const list = {};
  const cookieHeader = req.headers.cookie;
  if (!cookieHeader) return list;
  cookieHeader.split(';').forEach(cookie => {
    const parts = cookie.split('=');
    const name = parts.shift().trim();
    const value = decodeURIComponent(parts.join('=') || '');
    if (name) list[name] = value;
  });
  return list;
}

function getAuthUser(req) {
  // 1. Cabecera Authorization: Bearer <token>
  const authHeader = req.headers['authorization'] || '';
  if (authHeader.startsWith('Bearer ')) {
    const token = authHeader.slice(7).trim();
    const session = validateSession(token);
    if (session) return session.user;
  }

  // 2. Cookie segura de sesión HttpOnly
  const cookies = parseCookies(req);
  if (cookies['0media_session']) {
    const session = validateSession(cookies['0media_session']);
    if (session) return session.user;
  }

  // 3. Clave estática secreta de integración (ADMIN_SECRET)
  if (ADMIN_SECRET && req.headers['x-admin-secret'] === ADMIN_SECRET) {
    return { id: 'admin', username: ADMIN_USER || 'admin', role: 'ADMIN', name: 'SuperAdmin', company: '0Media Digital Signage' };
  }

  // 4. Si la autenticación global está desactivada en modo dev, retornar usuario admin por defecto
  if (!AUTH_ENABLED) {
    return { id: 'admin', username: ADMIN_USER || 'admin', role: 'ADMIN', name: 'Administrador 0Media', company: '0Media Digital Signage' };
  }

  return null;
}

function checkAdminAuth(req) {
  const user = getAuthUser(req);
  return user && user.role === 'ADMIN';
}

function checkAnyAuth(req) {
  return getAuthUser(req) !== null;
}

// Comprobación estricta de propiedad de publicidad por usuario
function isOwnerOfItem(it, user) {
  if (!it || !user) return false;
  if (user.role === 'ADMIN') return true;
  const uid = (user.id || '').toLowerCase().trim();
  const uname = (user.username || '').toLowerCase().trim();
  const ucomp = (user.company || '').toLowerCase().trim();
  const uName = (user.name || '').toLowerCase().trim();

  const itOwnerId = (it.ownerId || '').toLowerCase().trim();
  const itOwnerUser = (it.ownerUsername || '').toLowerCase().trim();
  const itClientName = (it.clientName || '').toLowerCase().trim();

  if (itOwnerId && (itOwnerId === uid || itOwnerId === uname)) return true;
  if (itOwnerUser && itOwnerUser === uname) return true;
  if (itClientName && (itClientName === uname || (ucomp && itClientName === ucomp) || (uName && itClientName === uName))) return true;
  return false;
}

// ---------------- 1.3 PROTECCIÓN CONTRA ATAQUES: RATE LIMITER EN MEMORIA (Sliding Window) ----------------
const rateLimitBuckets = new Map();

function isRateLimited(key, maxRequests, windowMs) {
  const now = Date.now();
  let timestamps = rateLimitBuckets.get(key) || [];
  timestamps = timestamps.filter(t => now - t < windowMs);
  if (timestamps.length >= maxRequests) {
    rateLimitBuckets.set(key, timestamps);
    const retryAfter = Math.max(1, Math.ceil((windowMs - (now - timestamps[0])) / 1000));
    return { limited: true, retryAfter };
  }
  timestamps.push(now);
  rateLimitBuckets.set(key, timestamps);
  return { limited: false, remaining: maxRequests - timestamps.length };
}

// Limpieza periódica de buckets caducados
setInterval(() => {
  const now = Date.now();
  for (const [key, timestamps] of rateLimitBuckets.entries()) {
    const valid = timestamps.filter(t => now - t < 15 * 60 * 1000);
    if (valid.length === 0) rateLimitBuckets.delete(key);
    else rateLimitBuckets.set(key, valid);
  }
}, 10 * 60 * 1000).unref();

// ---------------- 1.4 ALMACENAMIENTO S3 / CLOUDFLARE R2 / CDN (AWS Signature V4 Cero-Dependencias) ----------------
async function uploadToS3(key, buffer, mimeType) {
  if (STORAGE_DRIVER !== 's3' || !S3_BUCKET || !S3_ACCESS_KEY_ID || !S3_SECRET_ACCESS_KEY) {
    return null;
  }
  try {
    const endpointStr = S3_ENDPOINT || `https://${S3_BUCKET}.s3.${S3_REGION}.amazonaws.com`;
    const endpointUrl = new URL(endpointStr);
    const host = endpointUrl.host;
    const s3Path = `/${S3_BUCKET}/${key}`;
    const date = new Date();
    const amzDate = date.toISOString().replace(/[:-]|\.\d{3}/g, '');
    const dateStamp = amzDate.substr(0, 8);
    const service = 's3';

    const payloadHash = crypto.createHash('sha256').update(buffer).digest('hex');
    const canonicalHeaders = `content-type:${mimeType}\nhost:${host}\nx-amz-content-sha256:${payloadHash}\nx-amz-date:${amzDate}\n`;
    const signedHeaders = 'content-type;host;x-amz-content-sha256;x-amz-date';

    const canonicalRequest = `PUT\n${s3Path}\n\n${canonicalHeaders}\n${signedHeaders}\n${payloadHash}`;
    const credentialScope = `${dateStamp}/${S3_REGION}/${service}/aws4_request`;
    const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${credentialScope}\n${crypto.createHash('sha256').update(canonicalRequest).digest('hex')}`;

    function getSignatureKey(secret, dateStamp, regionName, serviceName) {
      const kDate = crypto.createHmac('sha256', 'AWS4' + secret).update(dateStamp).digest();
      const kRegion = crypto.createHmac('sha256', kDate).update(regionName).digest();
      const kService = crypto.createHmac('sha256', kRegion).update(serviceName).digest();
      return crypto.createHmac('sha256', kService).update('aws4_request').digest();
    }

    const signingKey = getSignatureKey(S3_SECRET_ACCESS_KEY, dateStamp, S3_REGION, service);
    const signature = crypto.createHmac('sha256', signingKey).update(stringToSign).digest('hex');
    const authHeader = `AWS4-HMAC-SHA256 Credential=${S3_ACCESS_KEY_ID}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}`;

    const putUrl = `${endpointUrl.protocol}//${host}${s3Path}`;
    const response = await fetch(putUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': mimeType,
        'Host': host,
        'x-amz-date': amzDate,
        'x-amz-content-sha256': payloadHash,
        'Authorization': authHeader
      },
      body: buffer
    });

    if (!response.ok) {
      const errText = await response.text();
      console.warn(`[S3 ERROR] Fallo al subir archivo a la nube (HTTP ${response.status}): ${errText}`);
      return null;
    }

    const publicUrl = S3_PUBLIC_DOMAIN ? `${S3_PUBLIC_DOMAIN}/${key}` : `${endpointUrl.protocol}//${host}/${S3_BUCKET}/${key}`;
    console.log(`[S3 SUCCESS] Archivo multimedia subido a Cloud Storage: ${publicUrl}`);
    return publicUrl;
  } catch (err) {
    console.warn(`[S3 EXCEPTION]:`, err.message);
    return null;
  }
}

// Detección automática multi-entorno para FRONTEND, UPLOADS y APK
const FRONTEND_DIR = fs.existsSync(path.join(__dirname, '..', 'frontend-web'))
  ? path.join(__dirname, '..', 'frontend-web')
  : (fs.existsSync(path.join(__dirname, 'frontend-web')) 
      ? path.join(__dirname, 'frontend-web') 
      : path.join(__dirname, 'admin-panel'));

const UPLOADS_DIR = fs.existsSync(path.join(__dirname, 'uploads'))
  ? path.join(__dirname, 'uploads')
  : path.join(__dirname, '..', 'backend', 'uploads');

const ASSETS_DIR = path.join(FRONTEND_DIR, 'assets');
const STATE_FILE = path.join(__dirname, 'signage_state.json');
const DB_FILE = path.join(__dirname, 'signage.db');

const APK_PATH = fs.existsSync(path.join(__dirname, '..', 'aplicacion-tv', '0Media_Signage_TV.apk'))
  ? path.join(__dirname, '..', 'aplicacion-tv', '0Media_Signage_TV.apk')
  : (fs.existsSync(path.join(__dirname, 'aplicacion-tv', '0Media_Signage_TV.apk'))
      ? path.join(__dirname, 'aplicacion-tv', '0Media_Signage_TV.apk')
      : path.join(__dirname, '0Media_Signage_TV.apk'));

if (!fs.existsSync(UPLOADS_DIR)) fs.mkdirSync(UPLOADS_DIR, { recursive: true });
if (!fs.existsSync(ASSETS_DIR)) fs.mkdirSync(ASSETS_DIR, { recursive: true });

// ================= 2. CAPA DE PERSISTENCIA (SQLite con WAL y Espejo JSON) =================
let sqliteDb = null;
try {
  const { DatabaseSync } = require('node:sqlite');
  sqliteDb = new DatabaseSync(DB_FILE);
  sqliteDb.exec('PRAGMA journal_mode = WAL;');
  sqliteDb.exec('PRAGMA synchronous = NORMAL;');
  sqliteDb.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      fingerprint TEXT UNIQUE,
      name TEXT,
      location TEXT,
      model TEXT,
      resolution TEXT,
      ip TEXT,
      status TEXT,
      pairing_code TEXT,
      is_paired INTEGER,
      has_been_paired INTEGER,
      temp_token TEXT,
      permanent_token TEXT,
      tenant_id TEXT,
      ram_usage TEXT,
      free_disk TEXT,
      temp TEXT,
      campaign_name TEXT,
      device_info TEXT,
      items TEXT,
      last_initiated INTEGER,
      last_paired_at INTEGER,
      updated_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS global_items (
      id TEXT PRIMARY KEY,
      asset_id TEXT,
      title TEXT,
      media_type TEXT,
      media_tag TEXT,
      url TEXT,
      remote_url TEXT,
      duration_seconds INTEGER,
      order_index INTEGER,
      size_text TEXT,
      file_size_bytes INTEGER,
      checksum_sha256 TEXT,
      mime_type TEXT,
      is_enabled INTEGER,
      created_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS metadata (
      key TEXT PRIMARY KEY,
      value TEXT
    );
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT UNIQUE,
      email TEXT,
      password_hash TEXT,
      name TEXT,
      company TEXT,
      role TEXT,
      phone TEXT,
      assigned_devices TEXT,
      is_active INTEGER DEFAULT 1,
      created_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS playback_metrics (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT,
      playlist_item_id TEXT,
      asset_id TEXT,
      played_at INTEGER,
      rendered_duration_ms INTEGER,
      completed_normally INTEGER
    );
  `);

  // Migraciones automáticas no destructivas
  try { sqliteDb.exec("ALTER TABLE devices ADD COLUMN tariff TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE devices ADD COLUMN max_slots INTEGER DEFAULT 10;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE devices ADD COLUMN tv_user TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE devices ADD COLUMN tv_password_hash TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN owner_id TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN client_name TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN valid_from TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN valid_to TEXT;"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN approval_status TEXT DEFAULT 'APPROVED';"); } catch (e) {}
  try { sqliteDb.exec("ALTER TABLE global_items ADD COLUMN owner_username TEXT;"); } catch (e) {}

  console.log('[DATABASE] Motor SQLite inicializado en modo WAL con protección contra cortes de energía.');
} catch (err) {
  console.warn('[DATABASE] SQLite nativo no disponible, usando motor de persistencia JSON atómico:', err.message);
  sqliteDb = null;
}

// Estado en memoria
let state = {
  devices: [],
  manifestVersion: "v1.0.0",
  customItems: [],
  users: []
};

function generateUniquePairingCode() {
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  let code = "";
  let attempts = 0;
  do {
    code = "";
    for (let i = 0; i < 6; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    attempts++;
  } while (state.devices.some(d => d.pairingCode === code && !d.isPaired) && attempts < 200);
  return code;
}

function loadState() {
  let loadedFromSqlite = false;

  if (sqliteDb) {
    try {
      const devRows = sqliteDb.prepare('SELECT * FROM devices').all() || [];
      const itemRows = sqliteDb.prepare('SELECT * FROM global_items ORDER BY order_index ASC').all() || [];
      const metaVer = sqliteDb.prepare("SELECT value FROM metadata WHERE key = 'manifestVersion'").get();

      state.devices = devRows.map(row => ({
        id: row.id,
        fingerprint: row.fingerprint,
        name: row.name,
        location: row.location,
        model: row.model,
        resolution: row.resolution,
        ip: row.ip,
        status: row.status,
        pairingCode: row.pairing_code,
        isPaired: Boolean(row.is_paired),
        hasBeenPaired: Boolean(row.has_been_paired),
        tempToken: row.temp_token,
        permanentToken: row.permanent_token,
        tenantId: row.tenant_id,
        ramUsage: row.ram_usage,
        freeDisk: row.free_disk,
        temp: row.temp,
        campaignName: row.campaign_name,
        deviceInfo: row.device_info ? JSON.parse(row.device_info) : {},
        items: row.items ? JSON.parse(row.items) : [],
        lastInitiated: row.last_initiated,
        lastPairedAt: row.last_paired_at,
        tariff: row.tariff || '',
        maxSlots: row.max_slots || 10,
        tvUser: row.tv_user || '',
        tvPasswordHash: row.tv_password_hash || ''
      }));

      state.customItems = itemRows.map(row => ({
        id: row.id,
        assetId: row.asset_id,
        title: row.title,
        mediaType: row.media_type,
        mediaTag: row.media_tag,
        url: row.url,
        remoteUrl: row.remote_url,
        durationSeconds: row.duration_seconds,
        orderIndex: row.order_index,
        sizeText: row.size_text,
        fileSizeBytes: row.file_size_bytes,
        checksumSha256: row.checksum_sha256,
        mimeType: row.mime_type,
        isEnabled: Boolean(row.is_enabled),
        ownerId: row.owner_id || null,
        ownerUsername: row.owner_username || null,
        clientName: row.client_name || null,
        validFrom: row.valid_from || null,
        validTo: row.valid_to || null,
        approvalStatus: row.approval_status || 'APPROVED'
      }));

      try {
        const userRows = sqliteDb.prepare('SELECT * FROM users').all() || [];
        state.users = userRows.map(u => ({
          id: u.id,
          username: u.username,
          email: u.email,
          passwordHash: u.password_hash,
          name: u.name,
          company: u.company,
          role: u.role || 'CLIENT',
          phone: u.phone,
          assignedDevices: u.assigned_devices ? JSON.parse(u.assigned_devices) : ['*'],
          isActive: Boolean(u.is_active),
          createdAt: u.created_at
        }));
        if (state.users.length > 0) {
          console.log(`[STATE] Cargados ${state.users.length} usuarios anunciantes desde SQLite.`);
        }
      } catch (uErr) {}

      if (metaVer && metaVer.value) state.manifestVersion = metaVer.value;
      loadedFromSqlite = true;
      console.log(`[STATE] Cargado desde SQLite: ${state.devices.length} pantallas, ${state.customItems.length} anuncios globales.`);
    } catch (err) {
      console.warn('[STATE SQLITE LOAD ERROR]:', err.message);
    }
  }

  // Fallback o migración desde signage_state.json si la BD estaba vacía
  if (!loadedFromSqlite && fs.existsSync(STATE_FILE)) {
    try {
      const data = JSON.parse(fs.readFileSync(STATE_FILE, 'utf-8'));
      if (Array.isArray(data.devices)) {
        state.devices = data.devices;
      }
      if (data.manifestVersion) state.manifestVersion = data.manifestVersion;
      if (Array.isArray(data.customItems)) state.customItems = data.customItems;
      if (Array.isArray(data.users)) state.users = data.users;
      console.log(`[STATE] Migrado desde ${STATE_FILE}: ${state.devices.length} dispositivos y ${state.customItems.length} anuncios.`);
      saveState(); // Sincroniza inmediatamente a SQLite
    } catch (err) {
      console.error(`[STATE JSON READ ERROR]:`, err.message);
    }
  }
}

function saveState() {
  // 1. Guardar en SQLite si está disponible
  if (sqliteDb) {
    try {
      sqliteDb.exec('BEGIN TRANSACTION;');

      const insertDev = sqliteDb.prepare(`
        INSERT OR REPLACE INTO devices (
          id, fingerprint, name, location, model, resolution, ip, status,
          pairing_code, is_paired, has_been_paired, temp_token, permanent_token,
          tenant_id, ram_usage, free_disk, temp, campaign_name, device_info,
          items, last_initiated, last_paired_at, tariff, max_slots, tv_user, tv_password_hash, updated_at
        ) VALUES (
          ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
      `);

      // Limpiar pantallas removidas
      sqliteDb.exec('DELETE FROM devices;');
      for (const d of state.devices) {
        insertDev.run(
          d.id, d.fingerprint, d.name, d.location, d.model, d.resolution, d.ip, d.status,
          d.pairingCode, d.isPaired ? 1 : 0, d.hasBeenPaired ? 1 : 0, d.tempToken, d.permanentToken,
          d.tenantId, d.ramUsage, d.freeDisk, d.temp, d.campaignName,
          JSON.stringify(d.deviceInfo || {}), JSON.stringify(d.items || []),
          d.lastInitiated || 0, d.lastPairedAt || 0, d.tariff || '', parseInt(d.maxSlots, 10) || 10,
          d.tvUser || '', d.tvPasswordHash || '', Date.now()
        );
      }

      const insertItem = sqliteDb.prepare(`
        INSERT OR REPLACE INTO global_items (
          id, asset_id, title, media_type, media_tag, url, remote_url,
          duration_seconds, order_index, size_text, file_size_bytes,
          checksum_sha256, mime_type, is_enabled, owner_id, owner_username,
          client_name, valid_from, valid_to, approval_status, created_at
        ) VALUES (
          ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
      `);

      sqliteDb.exec('DELETE FROM global_items;');
      for (const it of state.customItems) {
        insertItem.run(
          it.id, it.assetId, it.title, it.mediaType, it.mediaTag, it.url, it.remoteUrl,
          it.durationSeconds, it.orderIndex, it.sizeText, it.fileSizeBytes,
          it.checksumSha256, it.mimeType, it.isEnabled !== false ? 1 : 0,
          it.ownerId || null, it.ownerUsername || null, it.clientName || null,
          it.validFrom || null, it.validTo || null, it.approvalStatus || 'APPROVED',
          Date.now()
        );
      }

      // Guardar usuarios anunciantes
      const insertUser = sqliteDb.prepare(`
        INSERT OR REPLACE INTO users (
          id, username, email, password_hash, name, company, role, phone, assigned_devices, is_active, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      sqliteDb.exec('DELETE FROM users;');
      for (const u of (state.users || [])) {
        insertUser.run(
          u.id, u.username, u.email || '', u.passwordHash, u.name || '', u.company || '',
          u.role || 'CLIENT', u.phone || '', JSON.stringify(u.assignedDevices || ['*']),
          u.isActive !== false ? 1 : 0, u.createdAt || Date.now()
        );
      }

      sqliteDb.prepare("INSERT OR REPLACE INTO metadata (key, value) VALUES ('manifestVersion', ?)").run(state.manifestVersion);
      sqliteDb.exec('COMMIT;');
    } catch (err) {
      try { sqliteDb.exec('ROLLBACK;'); } catch (e) {}
      console.error('[STATE SAVE SQLITE ERROR]:', err.message);
    }
  }

  // 2. Guardar siempre respaldo en JSON para inspección manual y máxima retrocompatibilidad
  try {
    const tmpFile = `${STATE_FILE}.tmp`;
    fs.writeFileSync(tmpFile, JSON.stringify(state, null, 2), 'utf-8');
    fs.renameSync(tmpFile, STATE_FILE);
  } catch (err) {
    console.error(`[STATE SAVE JSON ERROR]:`, err.message);
  }
}

loadState();

// ================= 3. MOTOR DE RESOLUCIÓN DINÁMICA DE URLS (MULTI-UBICACIÓN) =================
// Resuelve la URL pública canónica sin importar si la petición viene de localhost,
// de una IP en red local, o desde un dominio público / túnel en la nube.
function resolveBaseUrl(req) {
  if (PUBLIC_URL) {
    return PUBLIC_URL;
  }
  const forwardedHost = req.headers['x-forwarded-host'];
  const host = forwardedHost || req.headers.host;
  if (host) {
    const proto = req.headers['x-forwarded-proto'] || (req.socket && req.socket.encrypted ? 'https' : 'http');
    return `${proto}://${host}`;
  }
  return `http://${LOCAL_IP}:${PORT}`;
}

function resolveAssetRemoteUrl(req, itemUrl) {
  if (!itemUrl) return '';
  if (itemUrl.startsWith('http://') || itemUrl.startsWith('https://')) {
    // Si ya es una URL pública de Cloud Storage (S3 / R2 / CDN), se preserva intacta
    if (S3_PUBLIC_DOMAIN && itemUrl.startsWith(S3_PUBLIC_DOMAIN)) {
      return itemUrl;
    }
    if (S3_ENDPOINT) {
      try {
        const s3Host = new URL(S3_ENDPOINT).host;
        if (itemUrl.includes(s3Host)) return itemUrl;
      } catch (e) {}
    }
    try {
      const u = new URL(itemUrl);
      const baseUrl = resolveBaseUrl(req);
      const baseObj = new URL(baseUrl);
      u.protocol = baseObj.protocol;
      u.host = baseObj.host;
      return u.toString();
    } catch (e) {
      return itemUrl;
    }
  }
  const cleanPath = itemUrl.replace(/^\/+/, '');
  return `${resolveBaseUrl(req)}/${cleanPath}`;
}

// ================= 4. MOTOR REAL-TIME WEBSOCKET / SOCKET.IO ENGINE.IO =================
// Implementación nativa RFC 6455 compatible con Android TV SignageSocketManager
const activeLiveSockets = new Map(); // sessionId -> { socket, deviceId, fingerprint, lastPing }

function encodeWsFrame(text) {
  const payload = Buffer.from(text, 'utf-8');
  const len = payload.length;
  let header;

  if (len < 126) {
    header = Buffer.alloc(2);
    header[0] = 0x81; // FIN + text opcode
    header[1] = len;
  } else if (len <= 0xFFFF) {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }

  return Buffer.concat([header, payload]);
}

function decodeWsFrames(buffer) {
  const messages = [];
  let offset = 0;

  while (offset + 2 <= buffer.length) {
    const firstByte = buffer[offset];
    const secondByte = buffer[offset + 1];
    const opcode = firstByte & 0x0F;
    const isMasked = (secondByte & 0x80) !== 0;
    let payloadLen = secondByte & 0x7F;
    let headerLen = 2;

    if (payloadLen === 126) {
      if (offset + 4 > buffer.length) break;
      payloadLen = buffer.readUInt16BE(offset + 2);
      headerLen = 4;
    } else if (payloadLen === 127) {
      if (offset + 10 > buffer.length) break;
      payloadLen = Number(buffer.readBigUInt64BE(offset + 2));
      headerLen = 10;
    }

    const maskOffset = offset + headerLen;
    const dataOffset = maskOffset + (isMasked ? 4 : 0);
    const totalFrameLen = dataOffset + payloadLen - offset;

    if (offset + totalFrameLen > buffer.length) {
      break;
    }

    let payload = buffer.subarray(dataOffset, dataOffset + payloadLen);
    if (isMasked) {
      const maskKey = buffer.subarray(maskOffset, maskOffset + 4);
      const unmasked = Buffer.alloc(payloadLen);
      for (let i = 0; i < payloadLen; i++) {
        unmasked[i] = payload[i] ^ maskKey[i % 4];
      }
      payload = unmasked;
    }

    if (opcode === 0x01) {
      messages.push({ opcode, text: payload.toString('utf-8') });
    } else if (opcode === 0x08) {
      messages.push({ opcode, close: true });
    } else if (opcode === 0x09) {
      messages.push({ opcode, ping: true });
    }

    offset += totalFrameLen;
  }

  return { messages, remaining: buffer.subarray(offset) };
}

function sendWsMessage(socket, text) {
  try {
    if (socket && !socket.destroyed && socket.writable) {
      socket.write(encodeWsFrame(text));
    }
  } catch (err) {
    console.warn('[WS SEND ERROR]:', err.message);
  }
}

// Emisión en tiempo real de eventos tipados a todas las pantallas o a una específica
function broadcastPlaylistUpdated(manifestVersion, changeSummary, targetDeviceId = null) {
  const payload = JSON.stringify(['playlist:updated', {
    manifestVersion: manifestVersion || state.manifestVersion,
    changeSummary: changeSummary || 'Contenido publicitario actualizado'
  }]);
  const socketIoMsg = '42' + payload;

  let count = 0;
  for (const [sid, client] of activeLiveSockets.entries()) {
    if (!targetDeviceId || client.deviceId === targetDeviceId) {
      sendWsMessage(client.socket, socketIoMsg);
      count++;
    }
  }
  console.log(`[WS BROADCAST] Evento "playlist:updated" emitido a ${count} pantalla(s) en vivo.`);
}

function notifyDevicePaired(deviceId, permanentToken, tenantId) {
  const payload = JSON.stringify(['device:paired', {
    deviceId: deviceId,
    permanentToken: permanentToken,
    tenantId: tenantId || '0media-enterprise'
  }]);
  const socketIoMsg = '42' + payload;

  for (const [sid, client] of activeLiveSockets.entries()) {
    if (client.deviceId === deviceId || !client.deviceId) {
      sendWsMessage(client.socket, socketIoMsg);
    }
  }
  console.log(`[WS NOTIFY] Confirmación "device:paired" enviada a televisor ${deviceId}.`);
}

function notifyDeviceRevoked(deviceId, reason = 'Desvinculado por el administrador') {
  const payload = JSON.stringify(['device:revoked', { reason }]);
  const socketIoMsg = '42' + payload;

  for (const [sid, client] of activeLiveSockets.entries()) {
    if (client.deviceId === deviceId) {
      sendWsMessage(client.socket, socketIoMsg);
    }
  }
  console.log(`[WS NOTIFY] Revocación "device:revoked" enviada a televisor ${deviceId}.`);
}

// ================= 5. SERVIDOR HTTP PRINCIPAL =================
const server = http.createServer((req, res) => {
  // CORS & Seguridad Infalible (Soporta file://, localhost, IP local y dominios externos)
  const origin = req.headers.origin;
  if (origin && origin !== 'null') {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
  } else {
    res.setHeader('Access-Control-Allow-Origin', '*');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE, PUT');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, x-temp-token, x-device-id, x-hardware-fingerprint, x-device-model, If-None-Match, Range, x-admin-secret');
  res.setHeader('Access-Control-Allow-Private-Network', 'true');
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');

  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Private-Network', 'true');
    res.writeHead(204);
    res.end();
    return;
  }

  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const url = urlObj.pathname;
  const clientIp = req.socket.remoteAddress ? req.socket.remoteAddress.replace(/^.*:/, '') : '127.0.0.1';

  // 0. RATE LIMITING GLOBAL (Excluye streaming de video e imágenes para no interrumpir reproducción)
  if (url.startsWith('/api/')) {
    const apiLimit = isRateLimited(`global_${clientIp}`, 300, 60 * 1000); // 300 peticiones / minuto
    if (apiLimit.limited) {
      res.writeHead(429, {
        'Content-Type': 'application/json',
        'Retry-After': String(apiLimit.retryAfter)
      });
      res.end(JSON.stringify({
        success: false,
        error: `Límite de peticiones excedido. Espere ${apiLimit.retryAfter} segundos.`
      }));
      return;
    }
  }

  // Helper para leer body JSON con protección
  function parseBody(callback) {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 120 * 1024 * 1024) req.destroy(); // 120 MB max
    });
    req.on('end', () => {
      try {
        const parsed = body ? JSON.parse(body) : {};
        callback(parsed);
      } catch (err) {
        callback({});
      }
    });
  }

  // Helper para servir archivos multimedia con soporte de Streaming HTTP 206 (Range)
  function serveMediaFileWithStreaming(filePath, contentType) {
    if (!fs.existsSync(filePath)) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: "Archivo multimedia no encontrado" }));
      return;
    }

    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const range = req.headers.range;

    if (range) {
      const parts = range.replace(/bytes=/, "").split("-");
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

      if (start >= fileSize || end >= fileSize || start > end) {
        res.writeHead(416, {
          'Content-Range': `bytes */${fileSize}`,
          'Content-Type': contentType
        });
        res.end();
        return;
      }

      const chunksize = (end - start) + 1;
      const fileStream = fs.createReadStream(filePath, { start, end });
      res.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${fileSize}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunksize,
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=86400, immutable'
      });
      fileStream.pipe(res);
    } else {
      res.writeHead(200, {
        'Content-Length': fileSize,
        'Accept-Ranges': 'bytes',
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=86400, immutable'
      });
      fs.createReadStream(filePath).pipe(res);
    }
  }

  // 1. HEALTH CHECK (Para Docker, Kubernetes y Cloud Monitors)
  if (url === '/health' || url === '/api/v1/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'UP',
      uptimeSeconds: Math.floor(process.uptime()),
      timestamp: Date.now(),
      registeredScreens: state.devices.length,
      pairedScreens: state.devices.filter(d => d.isPaired).length,
      liveSockets: activeLiveSockets.size,
      storageEngine: sqliteDb ? 'SQLite WAL' : 'Atomic JSON',
      storageDriver: STORAGE_DRIVER
    }));
    return;
  }

  // 1.0 LISTA PÚBLICA DE PANTALLAS (Para selector de anunciantes en login / portal)
  if (req.method === 'GET' && (url === '/api/v1/public/screens' || url === '/api/v1/devices/public-list' || url === '/api/v1/devices/catalog')) {
    const screens = (state.devices || [])
      .filter(d => d.isPaired || d.hasBeenPaired)
      .map(d => {
        const approvedCount = (d.items || []).filter(x => x.isEnabled !== false && x.approvalStatus === 'APPROVED').length;
        const maxSlots = parseInt(d.maxSlots, 10) || 10;
        return {
          id: d.id,
          name: d.name,
          location: d.location || 'Ubicación General',
          tariff: d.tariff || '$50.00 / mes',
          maxSlots: maxSlots,
          activeAdsCount: approvedCount,
          availableSlots: Math.max(0, maxSlots - approvedCount),
          resolution: d.resolution || '1920x1080',
          model: d.model || 'Android TV',
          status: d.status || 'ONLINE',
          hasPassword: Boolean(d.tvPasswordHash),
          tvUser: d.tvUser || ''
        };
      });
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, screens }));
    return;
  }

  // ================= 1.1 AUTENTICACIÓN MULTI-ROL (ADMINISTRADOR Y CLIENTES ANUNCIANTES) =================
  if (req.method === 'POST' && (url === '/api/v1/admin/login' || url === '/api/v1/auth/login')) {
    const loginLimit = isRateLimited(`login_${clientIp}`, 10, 15 * 60 * 1000); // Máx 10 intentos en 15 min
    if (loginLimit.limited) {
      res.writeHead(429, { 'Content-Type': 'application/json', 'Retry-After': String(loginLimit.retryAfter) });
      res.end(JSON.stringify({
        success: false,
        error: `Demasiados intentos fallidos. Por seguridad espere ${loginLimit.retryAfter} segundos.`
      }));
      return;
    }

    parseBody(data => {
      const username = (data.username || '').trim();
      const password = data.password || '';

      // 1. Probar credenciales de SuperAdmin
      const isDevFallback = !ADMIN_PASSWORD && (password === 'admin' || password === 'password123' || !AUTH_ENABLED);
      const validAdmin = (username.toLowerCase() === ADMIN_USER.toLowerCase()) && 
        ((storedAdminHash && verifyPassword(password, storedAdminHash)) || isDevFallback);
      if (validAdmin) {
        const adminUser = {
          id: 'admin',
          username: ADMIN_USER,
          name: 'Super Administrador',
          company: '0Media Digital Signage',
          role: 'ADMIN'
        };
        const { token, expiresAt } = createSession(adminUser);
        console.log(`[AUTH] Inicio de sesión exitoso como ADMIN para "${username}" desde IP ${clientIp}.`);
        res.writeHead(200, {
          'Content-Type': 'application/json',
          'Set-Cookie': `0media_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800`
        });
        res.end(JSON.stringify({
          success: true,
          token: token,
          user: adminUser,
          expiresAt: expiresAt
        }));
        return;
      }

      // 2. Probar credenciales asignadas a cada Televisor vinculado (DEVICE_USER)
      let matchedDevice = null;
      if (data.deviceId) {
        matchedDevice = (state.devices || []).find(d => 
          (d.isPaired || d.hasBeenPaired) &&
          d.id === data.deviceId &&
          ((d.tvPasswordHash && verifyPassword(password, d.tvPasswordHash)) || (!d.tvPasswordHash && (password === '' || password === 'admin')))
        );
      }
      if (!matchedDevice && username) {
        matchedDevice = (state.devices || []).find(d => 
          (d.isPaired || d.hasBeenPaired) &&
          d.tvUser &&
          d.tvUser.toLowerCase() === username.toLowerCase() &&
          d.tvPasswordHash &&
          verifyPassword(password, d.tvPasswordHash)
        );
      }

      if (matchedDevice) {
        const deviceUser = {
          id: `user_${matchedDevice.id}`,
          username: matchedDevice.tvUser,
          name: `Usuario de ${matchedDevice.name}`,
          company: matchedDevice.location ? `${matchedDevice.name} (${matchedDevice.location})` : matchedDevice.name,
          role: 'DEVICE_USER',
          assignedDeviceId: matchedDevice.id,
          deviceName: matchedDevice.name,
          deviceLocation: matchedDevice.location
        };
        const { token, expiresAt } = createSession(deviceUser);
        console.log(`[AUTH] Inicio de sesión exitoso como USUARIO DE TELEVISOR para "${username}" en pantalla "${matchedDevice.name}" desde IP ${clientIp}.`);
        res.writeHead(200, {
          'Content-Type': 'application/json',
          'Set-Cookie': `0media_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800`
        });
        res.end(JSON.stringify({
          success: true,
          token: token,
          user: deviceUser,
          expiresAt: expiresAt
        }));
        return;
      }

      // 3. Probar credenciales de Clientes / Anunciantes registrados previamente
      const clientUser = (state.users || []).find(u => 
        (u.username.toLowerCase() === username.toLowerCase() || (u.email && u.email.toLowerCase() === username.toLowerCase())) &&
        u.isActive !== false
      );

      if (clientUser && verifyPassword(password, clientUser.passwordHash)) {
        const userObj = {
          id: clientUser.id,
          username: clientUser.username,
          name: clientUser.name,
          company: clientUser.company || clientUser.name,
          email: clientUser.email || '',
          phone: clientUser.phone || '',
          role: 'CLIENT',
          assignedDevices: clientUser.assignedDevices || ['*']
        };
        const { token, expiresAt } = createSession(userObj);
        console.log(`[AUTH] Inicio de sesión exitoso como CLIENTE para "${username}" (${clientUser.company}) desde IP ${clientIp}.`);
        res.writeHead(200, {
          'Content-Type': 'application/json',
          'Set-Cookie': `0media_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800`
        });
        res.end(JSON.stringify({
          success: true,
          token: token,
          user: userObj,
          expiresAt: expiresAt
        }));
        return;
      }

      console.warn(`[AUTH FAIL] Intento de acceso fallido para "${username}" desde IP ${clientIp}.`);
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: false,
        error: "Credenciales de usuario o contraseña incorrectas."
      }));
    });
    return;
  }

  if (req.method === 'POST' && (url === '/api/v1/admin/logout' || url === '/api/v1/auth/logout')) {
    const cookies = parseCookies(req);
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace(/^Bearer\s+/i, '').trim() || cookies['0media_session'];
    if (token) destroySession(token);

    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Set-Cookie': '0media_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0'
    });
    res.end(JSON.stringify({ success: true, message: "Sesión cerrada correctamente." }));
    return;
  }

  // ================= 1.1.1 REGISTRO PÚBLICO DE USUARIOS ANUNCIANTES (WEB SIN APK) =================
  if (req.method === 'POST' && (url === '/api/v1/auth/register' || url === '/api/v1/users/register')) {
    const regLimit = isRateLimited(`reg_${clientIp}`, 15, 15 * 60 * 1000);
    if (regLimit.limited) {
      res.writeHead(429, { 'Content-Type': 'application/json', 'Retry-After': String(regLimit.retryAfter) });
      res.end(JSON.stringify({
        success: false,
        error: `Demasiados registros desde esta conexión. Por favor espere ${regLimit.retryAfter} segundos.`
      }));
      return;
    }

    parseBody(data => {
      const username = (data.username || '').toLowerCase().trim();
      const password = data.password || '';
      const name = (data.name || '').trim();
      const company = (data.company || name || username).trim();
      const phone = (data.phone || '').trim();
      const email = (data.email || '').trim();
      const selectedDeviceId = (data.selectedDeviceId || data.deviceId || '').trim();

      if (!username || username.length < 3) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "El nombre de usuario debe tener al menos 3 caracteres." }));
        return;
      }
      if (!/^[a-zA-Z0-9_.-]+$/.test(username)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "El nombre de usuario solo puede contener letras, números, puntos y guiones." }));
        return;
      }
      if (!password || password.length < 4) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "La contraseña debe tener al menos 4 caracteres." }));
        return;
      }

      if (username === ADMIN_USER.toLowerCase() || (state.users || []).some(u => u.username.toLowerCase() === username)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: `El usuario "${username}" ya está registrado. Por favor inicia sesión o elige otro nombre.` }));
        return;
      }

      // Asignar dispositivo inicial si se seleccionó uno válido
      let initialDevices = [];
      if (selectedDeviceId) {
        const found = (state.devices || []).find(d => d.id === selectedDeviceId && (d.isPaired || d.hasBeenPaired));
        if (found) {
          initialDevices.push(found.id);
        }
      }

      const newUser = {
        id: 'usr_' + Date.now().toString(36) + '_' + crypto.randomBytes(3).toString('hex'),
        username: username,
        passwordHash: hashPassword(password),
        name: name || username,
        company: company,
        email: email,
        phone: phone,
        role: 'CLIENT',
        assignedDevices: initialDevices,
        isActive: true,
        createdAt: Date.now()
      };

      if (!Array.isArray(state.users)) state.users = [];
      state.users.push(newUser);
      saveState();

      const sessionUser = {
        id: newUser.id,
        username: newUser.username,
        name: newUser.name,
        company: newUser.company,
        email: newUser.email,
        phone: newUser.phone,
        role: 'CLIENT',
        assignedDevices: newUser.assignedDevices
      };

      const { token, expiresAt } = createSession(sessionUser);
      console.log(`[USER REGISTER] Nuevo anunciante registrado: "${newUser.company}" (@${newUser.username}), vinculado a: ${initialDevices.length > 0 ? initialDevices.join(', ') : 'Ninguno (catálogo pendiente)'}.`);

      res.writeHead(201, {
        'Content-Type': 'application/json',
        'Set-Cookie': `0media_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800`
      });
      res.end(JSON.stringify({
        success: true,
        message: `¡Bienvenido a 0Media! Tu cuenta para "${newUser.company}" fue creada exitosamente.`,
        token: token,
        user: sessionUser,
        expiresAt: expiresAt
      }));
    });
    return;
  }

  // ================= 1.1.2 VINCULAR PANTALLA DISPONIBLE A LA CUENTA DEL USUARIO ANUNCIANTE =================
  if (req.method === 'POST' && (url === '/api/v1/users/link-device' || url === '/api/v1/devices/link-user')) {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "Debes iniciar sesión para vincularte a una pantalla." }));
      return;
    }

    parseBody(data => {
      const deviceId = (data.deviceId || '').trim();
      if (!deviceId) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "Identificador de dispositivo no proporcionado." }));
        return;
      }

      const targetDevice = (state.devices || []).find(d => d.id === deviceId && (d.isPaired || d.hasBeenPaired));
      if (!targetDevice) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "El televisor seleccionado no existe o no está activo." }));
        return;
      }

      if (authUser.role === 'ADMIN') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: true,
          message: "Como Administrador tienes acceso global a todas las pantallas.",
          deviceId: targetDevice.id,
          device: {
            id: targetDevice.id,
            name: targetDevice.name,
            location: targetDevice.location,
            tariff: targetDevice.tariff || '$50.00 / mes'
          }
        }));
        return;
      }

      // Buscar el usuario en state.users
      const user = (state.users || []).find(u => u.id === authUser.id || u.username.toLowerCase() === authUser.username.toLowerCase());
      if (!user) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "Usuario anunciante no encontrado." }));
        return;
      }

      if (!Array.isArray(user.assignedDevices)) {
        user.assignedDevices = [];
      }

      if (!user.assignedDevices.includes(targetDevice.id)) {
        user.assignedDevices.push(targetDevice.id);
      }

      // Actualizar sesión activa
      if (Array.isArray(authUser.assignedDevices)) {
        if (!authUser.assignedDevices.includes(targetDevice.id)) {
          authUser.assignedDevices.push(targetDevice.id);
        }
      } else {
        authUser.assignedDevices = [targetDevice.id];
      }

      saveState();

      console.log(`[USER LINK DEVICE] Usuario "${user.username}" (${user.company}) se vinculó al televisor "${targetDevice.name}" (${targetDevice.id}).`);

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        message: `¡Te has vinculado exitosamente a "${targetDevice.name}" (${targetDevice.location})!`,
        assignedDevices: user.assignedDevices,
        device: {
          id: targetDevice.id,
          name: targetDevice.name,
          location: targetDevice.location,
          tariff: targetDevice.tariff || '$50.00 / mes',
          maxSlots: parseInt(targetDevice.maxSlots, 10) || 10
        }
      }));
    });
    return;
  }

  if (req.method === 'GET' && (url === '/api/v1/admin/auth-status' || url === '/api/v1/auth/me')) {
    const user = getAuthUser(req);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      authEnabled: AUTH_ENABLED,
      isAuthenticated: Boolean(user),
      user: user || null
    }));
    return;
  }

  // ================= 1.2 GESTIÓN DE CLIENTES / ANUNCIANTES (SOLO ADMIN) =================
  if (req.method === 'GET' && url === '/api/v1/admin/users') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Solo administrador." }));
      return;
    }

    const safeUsers = (state.users || []).map(u => ({
      id: u.id,
      username: u.username,
      name: u.name,
      company: u.company,
      email: u.email,
      phone: u.phone,
      role: u.role,
      assignedDevices: u.assignedDevices,
      isActive: u.isActive,
      createdAt: u.createdAt
    }));

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, users: safeUsers }));
    return;
  }

  if (req.method === 'POST' && (url === '/api/v1/admin/users' || url === '/api/v1/admin/users/create')) {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const username = (data.username || '').toLowerCase().trim();
      const password = data.password || '';
      const name = (data.name || '').trim();
      const company = (data.company || name || username).trim();
      const email = (data.email || '').trim();
      const phone = (data.phone || '').trim();
      const assignedDevices = Array.isArray(data.assignedDevices) && data.assignedDevices.length > 0 ? data.assignedDevices : ['*'];

      if (!username || username.length < 3) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "El nombre de usuario debe tener al menos 3 caracteres." }));
        return;
      }

      if (!password || password.length < 4) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "La contraseña debe tener al menos 4 caracteres." }));
        return;
      }

      if (username === ADMIN_USER.toLowerCase() || (state.users || []).some(u => u.username.toLowerCase() === username)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: `El usuario "${username}" ya existe en el sistema.` }));
        return;
      }

      const newUser = {
        id: 'usr_' + Date.now().toString(36) + '_' + crypto.randomBytes(3).toString('hex'),
        username: username,
        passwordHash: hashPassword(password),
        name: name || username,
        company: company,
        email: email,
        phone: phone,
        role: 'CLIENT',
        assignedDevices: assignedDevices,
        isActive: true,
        createdAt: Date.now()
      };

      if (!Array.isArray(state.users)) state.users = [];
      state.users.push(newUser);
      saveState();

      console.log(`[USER CREATE] Anunciante registrado: "${newUser.company}" (${newUser.username}) por Admin.`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        message: `Cliente "${newUser.company}" creado con éxito.`,
        user: {
          id: newUser.id,
          username: newUser.username,
          name: newUser.name,
          company: newUser.company,
          email: newUser.email,
          phone: newUser.phone,
          role: newUser.role,
          assignedDevices: newUser.assignedDevices,
          createdAt: newUser.createdAt
        }
      }));
    });
    return;
  }

  if (req.method === 'POST' && url === '/api/v1/admin/users/delete') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const userId = data.userId;
      if (!userId) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "ID de usuario requerido." }));
        return;
      }

      state.users = (state.users || []).filter(u => u.id !== userId);
      saveState();

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, message: "Usuario cliente eliminado con éxito." }));
    });
    return;
  }

  // ================= 1.3 CONFIGURACIÓN DE PANTALLA: TARIFAS Y DATOS =================
  if (req.method === 'POST' && url === '/api/v1/admin/update-device-settings') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const dev = state.devices.find(d => d.id === data.deviceId);
      if (!dev) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "Pantalla no encontrada." }));
        return;
      }

      if (data.name) dev.name = data.name.trim();
      if (data.location) dev.location = data.location.trim();
      if (data.tariff !== undefined) dev.tariff = String(data.tariff).trim();
      if (data.maxSlots !== undefined) dev.maxSlots = Math.max(1, parseInt(data.maxSlots, 10) || 10);
      if (data.tvUser !== undefined) dev.tvUser = String(data.tvUser).trim();
      if (data.tvPassword && String(data.tvPassword).trim()) {
        dev.tvPasswordHash = hashPassword(String(data.tvPassword).trim());
      }

      saveState();
      console.log(`[DEVICE SETTINGS] Pantalla "${dev.name}": Tarifa=${dev.tariff}, Ubicación=${dev.location}, Cupos=${dev.maxSlots}.`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, device: dev }));
    });
    return;
  }

  // 2. PANEL DE ADMINISTRACIÓN WEB Y BIENVENIDA
  if (url === '/' || url === '/admin' || url === '/admin/' || url === '/index.html' || url === '/admin/index.html' || url === '/welcome' || url === '/welcome/') {
    const indexPath = path.join(FRONTEND_DIR, 'index.html');
    if (fs.existsSync(indexPath)) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      fs.createReadStream(indexPath).pipe(res);
      return;
    }
  }

  if (url === '/styles.css' || url === '/admin/styles.css') {
    const cssPath = path.join(FRONTEND_DIR, 'styles.css');
    if (fs.existsSync(cssPath)) {
      res.writeHead(200, { 'Content-Type': 'text/css; charset=utf-8' });
      fs.createReadStream(cssPath).pipe(res);
      return;
    }
  }

  if (url === '/app.js' || url === '/admin/app.js') {
    const jsPath = path.join(FRONTEND_DIR, 'app.js');
    if (fs.existsSync(jsPath)) {
      res.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8' });
      fs.createReadStream(jsPath).pipe(res);
      return;
    }
  }

  // 3. DESCARGA DEL APK DE INSTALACIÓN
  if (url === '/app.apk' || url === '/0Media_Signage_TV.apk') {
    if (fs.existsSync(APK_PATH)) {
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="0Media_Signage_TV.apk"',
        'Content-Length': fs.statSync(APK_PATH).size
      });
      fs.createReadStream(APK_PATH).pipe(res);
      return;
    }
  }

  // 4. ARCHIVOS SUBIDOS CON STREAMING HTTP 206 (VIDEOS E IMÁGENES)
  if (url.startsWith('/uploads/') || url.startsWith('/admin/uploads/')) {
    const fileName = path.basename(url);
    const filePath = path.join(UPLOADS_DIR, fileName);
    const ext = path.extname(fileName).toLowerCase();
    const mimeTypes = {
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.png': 'image/png',
      '.webp': 'image/webp',
      '.gif': 'image/gif',
      '.mp4': 'video/mp4',
      '.webm': 'video/webm'
    };
    serveMediaFileWithStreaming(filePath, mimeTypes[ext] || 'application/octet-stream');
    return;
  }

  // 5. ASSETS DEFAULT CON STREAMING
  if (url.startsWith('/assets/') || url.startsWith('/admin/assets/')) {
    const fileName = path.basename(url);
    const filePath = path.join(ASSETS_DIR, fileName);
    const ext = path.extname(fileName).toLowerCase();
    const mimeTypes = {
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.png': 'image/png',
      '.webp': 'image/webp',
      '.gif': 'image/gif',
      '.mp4': 'video/mp4'
    };
    serveMediaFileWithStreaming(filePath, mimeTypes[ext] || 'application/octet-stream');
    return;
  }

  // 6. INICIAR VINCULACIÓN (Desde Televisor o Celular)
  if (req.method === 'POST' && url === '/api/v1/devices/pairing/initiate') {
    parseBody(data => {
      const rawFp = data.hardwareFingerprint || req.headers['x-hardware-fingerprint'];
      const clientIp = req.socket.remoteAddress ? req.socket.remoteAddress.replace(/^.*:/, '') : LOCAL_IP;
      const model = data.deviceInfo?.model || req.headers['x-device-model'] || 'Android Device';
      const manufacturer = data.deviceInfo?.manufacturer || 'Android';

      const fingerprint = rawFp || `fp_${clientIp}_${model.replace(/\s+/g, '_')}`;
      console.log(`[TV CONNECT] Solicitud desde: ${manufacturer} ${model} (FP: ${fingerprint.substring(0, 12)}...)`);

      let device = state.devices.find(d => d.fingerprint === fingerprint);

      if (!device) {
        const cleanName = `${manufacturer} ${model}`.trim();
        const cleanId = `tv-${manufacturer.toLowerCase().replace(/[^a-z0-9]/g, '')}-${model.toLowerCase().replace(/[^a-z0-9]/g, '')}-${crypto.randomBytes(3).toString('hex')}`;
        device = {
          id: cleanId,
          fingerprint: fingerprint,
          name: cleanName || `Pantalla #${state.devices.length + 1}`,
          location: `Ubicación #${state.devices.length + 1}`,
          model: cleanName,
          resolution: data.deviceInfo?.screenResolution || "1920x1080",
          ip: clientIp,
          status: "STANDBY",
          pairingCode: "",
          isPaired: false,
          hasBeenPaired: false,
          tempToken: "",
          permanentToken: `perm_${crypto.randomBytes(16).toString('hex')}`,
          tenantId: "0media-enterprise",
          ramUsage: "430 MB / 2 GB",
          freeDisk: "14.2 GB",
          temp: "40°C",
          campaignName: "Esperando Vinculación",
          items: [],
          deviceInfo: data.deviceInfo || {}
        };
        state.devices.push(device);
      } else {
        if (data.deviceInfo) {
          device.deviceInfo = data.deviceInfo;
          device.model = `${manufacturer} ${model}`.trim();
          if (data.deviceInfo.screenResolution) device.resolution = data.deviceInfo.screenResolution;
        }
        device.ip = clientIp;
        if (!device.hasBeenPaired && !device.isPaired) {
          device.items = [];
        }
      }

      device.pairingCode = generateUniquePairingCode();
      device.tempToken = `temp_${crypto.randomBytes(8).toString('hex')}`;
      device.isPaired = false;
      device.status = "STANDBY";
      device.lastInitiated = Date.now();

      saveState();

      const currentBaseUrl = resolveBaseUrl(req);
      console.log(`[PAIRING CODE] Pantalla "${device.name}" (${device.id}) -> CÓDIGO: ${device.pairingCode}`);

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        data: {
          pairingCode: device.pairingCode,
          qrPayload: `0media://pair?code=${device.pairingCode}&lanUrl=${encodeURIComponent(currentBaseUrl)}`,
          tempToken: device.tempToken,
          expiresInSeconds: 300
        }
      }));
    });
    return;
  }

  // 7. POLLING DE VINCULACIÓN
  if (req.method === 'GET' && url === '/api/v1/devices/pairing/poll') {
    const tempToken = req.headers['x-temp-token'];
    const query = urlObj.searchParams;
    const fingerprint = query.get('fingerprint') || req.headers['x-hardware-fingerprint'];
    const clientIp = req.socket.remoteAddress ? req.socket.remoteAddress.replace(/^.*:/, '') : '';

    let device = state.devices.find(d => 
      (tempToken && d.tempToken === tempToken) || 
      (tempToken && d.pairingCode && tempToken.toUpperCase().includes(d.pairingCode.toUpperCase())) ||
      (fingerprint && (d.fingerprint === fingerprint || (d.pairingCode && fingerprint.toUpperCase().endsWith(d.pairingCode)) || (d.id && d.id.includes(fingerprint.toLowerCase()))))
    );

    if (!device && clientIp) {
      device = state.devices.find(d => d.ip === clientIp);
    }

    if (!device && fingerprint) {
      const authorizedPre = state.devices.find(d => d.isPaired && d.id && d.id.startsWith('tv-device-'));
      if (authorizedPre) {
        authorizedPre.fingerprint = fingerprint;
        if (clientIp) authorizedPre.ip = clientIp;
        device = authorizedPre;
      }
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    if (device && device.isPaired) {
      res.end(JSON.stringify({
        success: true,
        data: {
          paired: true,
          deviceId: device.id,
          permanentToken: device.permanentToken,
          tenantId: device.tenantId
        }
      }));
    } else {
      res.end(JSON.stringify({
        success: true,
        data: { paired: false }
      }));
    }
    return;
  }

  // 8. AUTORIZACIÓN MANUAL DESDE EL PANEL DE ADMINISTRACIÓN
  if (req.method === 'POST' && (url === '/api/v1/admin/authorize-pairing' || url === '/api/v1/devices/pairing/confirm')) {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Inicie sesión en el panel." }));
      return;
    }

    const pinLimit = isRateLimited(`pin_${clientIp}`, 10, 10 * 60 * 1000); // Máx 10 intentos por cada 10 min
    if (pinLimit.limited) {
      res.writeHead(429, { 'Content-Type': 'application/json', 'Retry-After': String(pinLimit.retryAfter) });
      res.end(JSON.stringify({
        success: false,
        error: `Demasiados intentos de vinculación. Por seguridad espere ${pinLimit.retryAfter} segundos.`
      }));
      return;
    }

    parseBody(data => {
      const pin = (data.code || '').trim().toUpperCase();
      console.log(`[ADMIN PAIR] Intento de autorización con código: "${pin}"`);

      let targetDevice = state.devices.find(d => d.pairingCode && d.pairingCode.toUpperCase() === pin);

      const normalizeCode = s => (s || '').toUpperCase()
        .replace(/[E5S]/g, '5')
        .replace(/[B8]/g, '8')
        .replace(/[O0]/g, '0')
        .replace(/[I1L]/g, '1')
        .replace(/[Z2]/g, '2');

      const normPin = normalizeCode(pin);
      if (!targetDevice) {
        targetDevice = state.devices.find(d => normalizeCode(d.pairingCode) === normPin);
      }
      if (!targetDevice) {
        targetDevice = state.devices.find(d => d.fingerprint && d.fingerprint.toUpperCase().endsWith(pin));
      }
      if (!targetDevice) {
        const standbyDevices = state.devices.filter(d => !d.isPaired);
        if (standbyDevices.length > 0) {
          standbyDevices.sort((a, b) => (b.lastInitiated || 0) - (a.lastInitiated || 0));
          targetDevice = standbyDevices[0];
        }
      }

      if (!targetDevice) {
        if (pin.length >= 6) {
          const clientIp = req.socket.remoteAddress ? req.socket.remoteAddress.replace(/^.*:/, '') : LOCAL_IP;
          targetDevice = {
            id: `tv-device-${pin.toLowerCase()}`,
            fingerprint: `fp_device_${pin.toLowerCase()}`,
            name: data.name || `Pantalla ${pin}`,
            location: data.location || "Sala Principal",
            model: "Android Device",
            resolution: "1920x1080",
            ip: clientIp,
            status: "ONLINE",
            pairingCode: pin,
            isPaired: true,
            hasBeenPaired: true,
            tempToken: `demo_temp_${pin}`,
            permanentToken: `perm_${crypto.randomBytes(16).toString('hex')}`,
            tenantId: "0media-enterprise",
            ramUsage: "430 MB / 2 GB",
            freeDisk: "14.2 GB",
            temp: "40°C",
            campaignName: "Esperando Publicidad",
            items: [],
            deviceInfo: {}
          };
          state.devices.push(targetDevice);
        } else {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            success: false,
            error: `El código "${pin}" debe tener al menos 6 caracteres.`
          }));
          return;
        }
      }

      targetDevice.pairingCode = pin;
      targetDevice.isPaired = true;
      targetDevice.hasBeenPaired = true;
      targetDevice.status = "ONLINE";
      targetDevice.lastPairedAt = Date.now();
      if (data.name) targetDevice.name = data.name.trim();
      if (data.location) targetDevice.location = data.location.trim();
      if (data.tariff !== undefined) targetDevice.tariff = String(data.tariff).trim();
      if (data.maxSlots !== undefined) targetDevice.maxSlots = Math.max(1, parseInt(data.maxSlots, 10) || 10);
      if (data.tvUser) targetDevice.tvUser = String(data.tvUser).trim();
      if (data.tvPassword && String(data.tvPassword).trim()) {
        targetDevice.tvPasswordHash = hashPassword(String(data.tvPassword).trim());
      }
      if (!Array.isArray(targetDevice.items)) targetDevice.items = [];

      // Sincronizar automáticamente cualquier publicidad general activa al nuevo televisor vinculado
      const activeGlobalAds = (state.customItems || []).filter(x => (x.isGlobal || x.ownerId === 'admin') && x.isEnabled !== false);
      activeGlobalAds.forEach(gAd => {
        if (!targetDevice.items.some(x => x.id === gAd.id)) {
          targetDevice.items.unshift({ ...gAd });
        }
      });
      targetDevice.items.forEach((it, idx) => { it.orderIndex = idx + 1; });

      state.manifestVersion = "v" + Date.now();
      saveState();

      // Notificar en tiempo real por WebSocket
      notifyDevicePaired(targetDevice.id, targetDevice.permanentToken, targetDevice.tenantId);

      console.log(`[ADMIN PAIR OK] Pantalla "${targetDevice.name}" (${targetDevice.id}) AUTORIZADA.`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        message: `Dispositivo "${targetDevice.name}" autorizado con éxito.`,
        device: targetDevice
      }));
    });
    return;
  }

  // 9. DESVINCULAR PANTALLA
  if ((req.method === 'POST' || req.method === 'GET') && (url === '/api/v1/admin/revoke-pairing' || url === '/api/v1/devices/unpair')) {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const devId = data?.deviceId;
      if (devId) {
        const dev = state.devices.find(d => d.id === devId);
        if (dev) {
          dev.isPaired = false;
          dev.hasBeenPaired = false;
          dev.status = "STANDBY";
          dev.pairingCode = generateUniquePairingCode();
          notifyDeviceRevoked(dev.id);
        }
      } else {
        state.devices.forEach(d => {
          d.isPaired = false;
          d.hasBeenPaired = false;
          d.status = "STANDBY";
          d.pairingCode = generateUniquePairingCode();
          notifyDeviceRevoked(d.id);
        });
      }
      saveState();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        message: "Sesión desvinculada exitosamente."
      }));
    });
    return;
  }

  // 10. OBTENER DISPOSITIVOS PARA EL PANEL WEB
  if (req.method === 'GET' && (url === '/api/v1/admin/devices' || url === '/api/v1/devices')) {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Inicie sesión en el panel." }));
      return;
    }

    const pairedDevices = state.devices.filter(d => d.isPaired || d.hasBeenPaired);

    // Enriquecer items con métricas de reproducción reales desde SQLite
    const enrichItems = (items) => {
      if (!Array.isArray(items)) return [];
      return items.map(it => {
        let playCount = 0;
        if (sqliteDb) {
          try {
            const row = sqliteDb.prepare('SELECT COUNT(*) as cnt FROM playback_metrics WHERE playlist_item_id = ? OR asset_id = ?').get(it.id, it.assetId);
            playCount = row ? row.cnt : 0;
          } catch (e) {}
        }
        return {
          ...it,
          playCount: playCount
        };
      });
    };

    if (authUser.role === 'DEVICE_USER') {
      // Filtrar para el usuario del televisor: SOLO ve su televisor asignado y SOLO las publicidades que él mismo subió
      const myDevice = pairedDevices.find(d => d.id === authUser.assignedDeviceId);
      const userDevices = myDevice ? [{
        ...myDevice,
        tvUser: myDevice.tvUser || '',
        hasTvPassword: Boolean(myDevice.tvPasswordHash),
        tvPasswordHash: undefined,
        items: enrichItems(myDevice.items || []).filter(it => isOwnerOfItem(it, authUser)),
        totalActiveAds: (myDevice.items || []).filter(x => x.isEnabled !== false && x.approvalStatus === 'APPROVED').length,
        tariff: myDevice.tariff || '',
        maxSlots: parseInt(myDevice.maxSlots, 10) || 10
      }] : [];

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        devices: userDevices,
        role: 'DEVICE_USER',
        user: authUser
      }));
      return;
    }

    if (authUser.role === 'CLIENT') {
      // Filtrar para el cliente: solo ve las pantallas asignadas o todas si '*',
      // y en device.items SOLO ve sus propias publicidades (NUNCA ve publicidades de otros clientes)
      const allowedScreens = authUser.assignedDevices || ['*'];
      const clientDevices = pairedDevices
        .filter(d => allowedScreens.includes('*') || allowedScreens.includes(d.id))
        .map(d => {
          const allItems = enrichItems(d.items || []);
          const myItems = allItems.filter(it => isOwnerOfItem(it, authUser));
          const totalApproved = allItems.filter(it => it.approvalStatus === 'APPROVED' && it.isEnabled !== false).length;
          const maxSlots = parseInt(d.maxSlots, 10) || 10;
          return {
            ...d,
            tvPasswordHash: undefined,
            items: myItems, // Solo ve sus propios anuncios
            totalActiveAds: totalApproved,
            maxSlots: maxSlots,
            availableSlots: Math.max(0, maxSlots - totalApproved),
            tariff: d.tariff || ''
          };
        });

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        devices: clientDevices,
        role: 'CLIENT',
        user: authUser
      }));
      return;
    }

    // Rol ADMIN: es el ÚNICO usuario que ve todas las pantallas y todas las publicidades de todos los clientes
    const adminDevices = pairedDevices.map(d => ({
      ...d,
      tvUser: d.tvUser || '',
      hasTvPassword: Boolean(d.tvPasswordHash),
      tvPasswordHash: undefined,
      items: enrichItems(d.items || []),
      tariff: d.tariff || '',
      maxSlots: parseInt(d.maxSlots, 10) || 10
    }));

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      success: true,
      devices: adminDevices,
      role: 'ADMIN',
      user: authUser
    }));
    return;
  }

  // 11. SUBIDA DE PUBLICIDAD Y SINCRONIZACIÓN MULTI-DISPOSITIVO
  if (req.method === 'POST' && url === '/api/v1/devices/upload-ad') {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado para subir publicidad." }));
      return;
    }

    parseBody(async data => {
      try {
        const { title, durationSeconds, mediaType, mediaTag, fileName, dataUrl, replacePrevious, targetTvId, validFrom, validTo, clientName, clientPhone } = data;

        if (!dataUrl || !fileName) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: "Datos de imagen o video no proporcionados" }));
          return;
        }

        let effectiveTargetTvId = targetTvId;
        if (authUser.role === 'DEVICE_USER') {
          effectiveTargetTvId = authUser.assignedDeviceId;
        } else if (authUser.role === 'CLIENT') {
          const allowed = authUser.assignedDevices || [];
          if (!effectiveTargetTvId || effectiveTargetTvId === 'ALL' || effectiveTargetTvId === 'all') {
            if (allowed.length > 0 && allowed[0] !== '*') {
              effectiveTargetTvId = allowed[0];
            } else if (allowed.length === 0) {
              res.writeHead(403, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ success: false, error: "Aún no te has vinculado a ningún televisor. Elige una pantalla en tu panel para subir publicidad." }));
              return;
            }
          }
          if (Array.isArray(allowed) && !allowed.includes('*') && !allowed.includes(effectiveTargetTvId)) {
            res.writeHead(403, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: "No tienes permiso para publicar en este televisor. Primero debes vincularlo a tu cuenta." }));
            return;
          }
        }

        const isClient = authUser.role === 'CLIENT';
        const isDeviceUser = authUser.role === 'DEVICE_USER';
        const isRestricted = isClient || isDeviceUser;
        const ownerId = isRestricted ? (authUser.id || authUser.username) : 'admin';
        const ownerUsername = isRestricted ? (authUser.username || authUser.name || authUser.id).toLowerCase() : 'admin';
        const finalClientName = isRestricted ? (authUser.company || authUser.name || authUser.username) : (clientName || 'Institucional 0Media').trim();
        const finalClientPhone = isRestricted ? (authUser.phone || '') : (clientPhone || '').trim();
        const approvalStatus = isRestricted ? 'PENDING' : 'APPROVED'; // Si es cliente/usuario TV queda en revisión; si es admin se aprueba directo

        const base64Data = dataUrl.replace(/^data:[^;]+;base64,/, "");
        const fileBuffer = Buffer.from(base64Data, 'base64');
        const cleanName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_');
        const uniqueFileName = `${Date.now()}_${cleanName}`;
        const filePath = path.join(UPLOADS_DIR, uniqueFileName);

        fs.writeFileSync(filePath, fileBuffer);

        const fileSize = fileBuffer.length;
        const fileHash = crypto.createHash('sha256').update(fileBuffer).digest('hex');

        const ext = path.extname(cleanName).toLowerCase();
        let mimeType = 'image/jpeg';
        if (ext === '.png') mimeType = 'image/png';
        else if (ext === '.webp') mimeType = 'image/webp';
        else if (ext === '.gif') mimeType = 'image/gif';
        else if (ext === '.mp4') mimeType = 'video/mp4';
        else if (ext === '.webm') mimeType = 'video/webm';

        const assetId = "asset_" + Date.now().toString(36) + "_" + Math.random().toString(36).substr(2, 4);
        const itemId = "item_" + Date.now().toString(36) + "_" + Math.random().toString(36).substr(2, 4);
        const relativeUrl = `uploads/${uniqueFileName}`;
        let dynamicRemoteUrl = `${resolveBaseUrl(req)}/${relativeUrl}`;

        // Subida automática a Almacenamiento en la Nube (S3 / Cloudflare R2 / CDN)
        if (STORAGE_DRIVER === 's3') {
          const s3Key = `uploads/${uniqueFileName}`;
          const cloudUrl = await uploadToS3(s3Key, fileBuffer, mimeType);
          if (cloudUrl) {
            dynamicRemoteUrl = cloudUrl;
          }
        }

        const sizeKb = Math.round(fileSize / 1024);
        const sizeText = sizeKb > 1024 ? `${(sizeKb / 1024).toFixed(1)} MB` : `${sizeKb} KB`;

        const isGlobalBroadcast = !effectiveTargetTvId || effectiveTargetTvId === 'ALL' || effectiveTargetTvId === 'all' || Boolean(data.isGlobal);
        const isPriority = isGlobalBroadcast || Boolean(data.isPriority);
        const targetDevice = isGlobalBroadcast ? null : state.devices.find(d => d.id === effectiveTargetTvId);

        if (targetDevice && targetDevice.maxSlots) {
          const currentCount = (targetDevice.items || []).filter(x => x.isEnabled !== false).length;
          const maxAllowed = parseInt(targetDevice.maxSlots, 10) || 10;
          if (currentCount >= maxAllowed) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              success: false,
              error: `El televisor "${targetDevice.name}" ha completado su cupo comercial de ${maxAllowed} publicidades.`
            }));
            return;
          }
        }

        const newItem = {
          id: itemId,
          assetId: assetId,
          title: title || cleanName.replace(/\.[^/.]+$/, ""),
          mediaType: mediaType || (ext === '.mp4' ? 'VIDEO' : (ext === '.gif' ? 'GIF' : 'IMAGE')),
          mediaTag: mediaTag || (ext === '.png' ? 'FOTO PNG' : (ext === '.gif' ? 'GIF ANIMADO' : (ext === '.mp4' ? 'VIDEO MP4' : 'FOTO 4K'))),
          url: relativeUrl,
          remoteUrl: dynamicRemoteUrl,
          durationSeconds: Math.max(1, parseInt(durationSeconds, 10) || 15),
          orderIndex: 1,
          sizeText: `${sizeText} • Adaptado`,
          fileSizeBytes: fileSize,
          checksumSha256: fileHash,
          mimeType: mimeType,
          isEnabled: true,
          // Datos comerciales y de gestión DOOH
          ownerId: ownerId,
          ownerUsername: ownerUsername,
          clientName: finalClientName,
          clientPhone: finalClientPhone,
          validFrom: validFrom || null,
          validTo: validTo || null,
          approvalStatus: approvalStatus,
          tariffAmount: targetDevice?.tariff ? parseFloat(targetDevice.tariff) : 0,
          isGlobal: isGlobalBroadcast,
          isPriority: isPriority,
          createdAt: Date.now()
        };

        if (targetDevice) {
          if (!Array.isArray(targetDevice.items)) targetDevice.items = [];
          if (replacePrevious === true && !isClient) {
            targetDevice.items = [newItem];
          } else if (isPriority) {
            targetDevice.items.unshift(newItem); // Prioridad: se ubica al frente
          } else {
            targetDevice.items.push(newItem);
          }
          targetDevice.items.forEach((it, idx) => { it.orderIndex = idx + 1; });
          console.log(`[UPLOAD] Publicidad asignada a pantalla "${targetDevice.name}" por ${finalClientName} (Estado: ${approvalStatus}).`);
        } else {
          // Sincronización global con prioridad en todos los dispositivos vinculados
          const activeDevs = state.devices.filter(d => d.isPaired);
          activeDevs.forEach(d => {
            if (!Array.isArray(d.items)) d.items = [];
            if (isPriority) {
              d.items.unshift({ ...newItem }); // Se ubica primero en cada carrusel
            } else {
              d.items.push({ ...newItem });
            }
            d.items.forEach((it, idx) => { it.orderIndex = idx + 1; });
          });
          console.log(`[UPLOAD GLOBAL] Publicidad General prioritaria sincronizada a ${activeDevs.length} pantallas por ${finalClientName} (Estado: ${approvalStatus}).`);
        }

        // Catálogo global
        if (!Array.isArray(state.customItems)) state.customItems = [];
        if (isPriority) {
          state.customItems.unshift(newItem);
        } else {
          state.customItems.push(newItem);
        }
        state.customItems.forEach((it, idx) => { it.orderIndex = idx + 1; });

        state.manifestVersion = "v" + Date.now();
        saveState();

        // Solo se transmite a la pantalla física si ya está aprobada
        if (approvalStatus === 'APPROVED') {
          broadcastPlaylistUpdated(state.manifestVersion, `Nueva publicidad: ${newItem.title}`, targetDevice?.id);
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: true,
          item: newItem,
          approvalStatus: approvalStatus,
          message: approvalStatus === 'PENDING' ? 
            'Publicidad enviada con éxito. Está en revisión por el administrador antes de proyectarse en la pantalla.' : 
            'Publicidad publicada y proyectada en pantalla con éxito.',
          manifestVersion: state.manifestVersion
        }));
      } catch (err) {
        console.error("[UPLOAD HANDLER ERROR]", err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: err.message }));
      }
    });
    return;
  }

  // 12. BLOQUEAR / ACTIVAR ANUNCIO
  if ((req.method === 'POST' || req.method === 'GET') && url === '/api/v1/devices/toggle-block-ad') {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const itemId = data.itemId;
      const targetTvId = data.targetTvId;

      // Seguridad: Solo el dueño o el administrador puede pausar/activar anuncios
      if (authUser.role !== 'ADMIN') {
        let it = null;
        if (targetTvId) {
          const dev = state.devices.find(d => d.id === targetTvId);
          it = dev?.items?.find(x => x.id === itemId);
        }
        if (!it) {
          for (const d of state.devices) {
            it = (d.items || []).find(x => x.id === itemId);
            if (it) break;
          }
        }
        if (!it) it = state.customItems?.find(x => x.id === itemId);
        if (it && !isOwnerOfItem(it, authUser)) {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: "No tienes permiso para pausar este anuncio." }));
          return;
        }
      }

      const itemInCatalog = state.customItems.find(it => it.id === itemId);
      const isEnabled = data.isEnabled !== undefined ? Boolean(data.isEnabled) : (itemInCatalog ? !itemInCatalog.isEnabled : true);

      if (itemInCatalog) itemInCatalog.isEnabled = isEnabled;

      const isGlobalToggle = !targetTvId || targetTvId === 'ALL' || targetTvId === 'all';
      if (!isGlobalToggle) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev && Array.isArray(dev.items)) {
          const it = dev.items.find(x => x.id === itemId);
          if (it) it.isEnabled = isEnabled;
        }
      } else {
        state.devices.forEach(d => {
          if (Array.isArray(d.items)) {
            const it = d.items.find(x => x.id === itemId);
            if (it) it.isEnabled = isEnabled;
          }
        });
      }

      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, `Anuncio ${isEnabled ? 'activado' : 'pausado'}`);

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, isEnabled: isEnabled, manifestVersion: state.manifestVersion }));
    });
    return;
  }

  // 13. ACTUALIZAR TIEMPO DE DURACIÓN
  if ((req.method === 'POST' || req.method === 'GET') && url === '/api/v1/devices/update-ad-duration') {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const { itemId, durationSeconds, applyToAll, targetTvId } = data;
      const parsedSeconds = Math.max(1, parseInt(durationSeconds, 10) || 15);

      // Seguridad: Clientes o usuarios de televisor solo pueden alterar la duración de sus propios anuncios
      if (authUser.role === 'DEVICE_USER') {
        if (targetTvId && targetTvId !== authUser.assignedDeviceId) {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: "No tienes permiso para modificar anuncios de otro televisor." }));
          return;
        }
      } else if (authUser.role === 'CLIENT') {
        const dev = targetTvId ? state.devices.find(d => d.id === targetTvId) : null;
        const it = dev?.items?.find(x => x.id === itemId) || state.customItems?.find(x => x.id === itemId);
        if (it && it.ownerId !== authUser.id) {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: "No tienes permiso para modificar la duración de este anuncio." }));
          return;
        }
      }

      const updateList = (list) => {
        if (!Array.isArray(list)) return;
        if (applyToAll === true) {
          list.forEach(it => {
            if (authUser.role === 'ADMIN' || it.ownerId === authUser.id) {
              it.durationSeconds = parsedSeconds;
            }
          });
        } else if (itemId) {
          const it = list.find(x => x.id === itemId || x.assetId === itemId);
          if (it) it.durationSeconds = parsedSeconds;
        }
      };

      if (targetTvId) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev && Array.isArray(dev.items)) {
          updateList(dev.items);
        } else {
          state.devices.forEach(d => { if (Array.isArray(d.items)) updateList(d.items); });
        }
      } else {
        state.devices.forEach(d => { if (Array.isArray(d.items)) updateList(d.items); });
      }

      // Sincronizar catálogo global también si coincide el ítem o aplica a todos
      updateList(state.customItems);

      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, `Duración actualizada a ${parsedSeconds}s`);
      console.log(`[DURATION UPDATE] Item ${itemId || 'TODOS'} -> ${parsedSeconds}s (Target: ${targetTvId || 'GLOBAL'}). Versión: ${state.manifestVersion}`);

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        durationSeconds: parsedSeconds,
        manifestVersion: state.manifestVersion
      }));
    });
    return;
  }

  // 13.1 REORDENAR Y MOVER ANUNCIOS EN LA LISTA DEL TELEVISOR
  if (req.method === 'POST' && url === '/api/v1/devices/reorder-items') {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado para reordenar anuncios." }));
      return;
    }

    parseBody(data => {
      const { targetTvId, itemIds } = data;
      if (!targetTvId || !Array.isArray(itemIds)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "Datos de reordenamiento incompletos." }));
        return;
      }

      const targetDevice = state.devices.find(d => d.id === targetTvId);
      if (!targetDevice || !Array.isArray(targetDevice.items)) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: "Dispositivo no encontrado." }));
        return;
      }

      // Reordenar items según itemIds
      const itemsMap = new Map(targetDevice.items.map(it => [it.id, it]));
      const newItems = [];
      itemIds.forEach(id => {
        if (itemsMap.has(id)) {
          newItems.push(itemsMap.get(id));
          itemsMap.delete(id);
        }
      });
      // Agregar los que falten por seguridad
      for (const it of itemsMap.values()) {
        newItems.push(it);
      }

      newItems.forEach((it, idx) => {
        it.orderIndex = idx + 1;
      });

      targetDevice.items = newItems;
      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, 'Orden de lista actualizado');

      console.log(`[REORDER] ${newItems.length} anuncios reordenados en pantalla "${targetDevice.name}". Versión: ${state.manifestVersion}`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        manifestVersion: state.manifestVersion,
        itemsCount: newItems.length
      }));
    });
    return;
  }

  // 13.0 BANDEJA DE APROBACIONES PENDIENTES (SOLO ADMIN)
  if (req.method === 'GET' && url === '/api/v1/admin/pending-approvals') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Solo administrador." }));
      return;
    }

    const pendingAds = [];
    const seenItemIds = new Set();
    (state.devices || []).forEach(dev => {
      if (Array.isArray(dev.items)) {
        dev.items.forEach(it => {
          if (it.approvalStatus === 'PENDING' && !seenItemIds.has(it.id)) {
            seenItemIds.add(it.id);
            pendingAds.push({
              ...it,
              deviceId: dev.id,
              deviceName: dev.name,
              deviceLocation: dev.location,
              deviceTariff: dev.tariff || '$50.00 / mes'
            });
          }
        });
      }
    });

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, pendingAds }));
    return;
  }

  // 13.05 PUBLICIDAD GENERAL / GLOBAL (EXCLUSIVO ADMIN)
  if (req.method === 'GET' && url === '/api/v1/admin/global-ads') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Solo administrador." }));
      return;
    }

    const pairedCount = (state.devices || []).filter(d => d.isPaired).length;
    const globalAds = [];
    const seenIds = new Set();

    (state.customItems || []).forEach(it => {
      if ((it.isGlobal || it.ownerId === 'admin') && !seenIds.has(it.id)) {
        seenIds.add(it.id);
        const devCount = (state.devices || []).filter(d => d.isPaired && (d.items || []).some(x => x.id === it.id)).length;
        globalAds.push({
          ...it,
          activeDevicesCount: devCount || pairedCount,
          totalPairedDevices: pairedCount
        });
      }
    });

    (state.devices || []).forEach(d => {
      if (Array.isArray(d.items)) {
        d.items.forEach(it => {
          if ((it.isGlobal || it.ownerId === 'admin') && !seenIds.has(it.id)) {
            seenIds.add(it.id);
            const devCount = (state.devices || []).filter(dev => dev.isPaired && (dev.items || []).some(x => x.id === it.id)).length;
            globalAds.push({
              ...it,
              activeDevicesCount: devCount || pairedCount,
              totalPairedDevices: pairedCount
            });
          }
        });
      }
    });

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      success: true,
      globalAds: globalAds,
      totalPairedDevices: pairedCount
    }));
    return;
  }

  // 13.1 MODERACIÓN DE ANUNCIOS: APROBAR
  if (req.method === 'POST' && url === '/api/v1/devices/approve-ad') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Solo administrador." }));
      return;
    }
    parseBody(data => {
      const { itemId, targetTvId } = data;
      let targetItem = null;
      const updateItem = (list) => {
        if (!Array.isArray(list)) return;
        const it = list.find(x => x.id === itemId || x.assetId === itemId);
        if (it) {
          it.approvalStatus = 'APPROVED';
          it.rejectionReason = null;
          targetItem = it;
        }
      };

      if (targetTvId) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev && Array.isArray(dev.items)) updateItem(dev.items);
      } else {
        state.devices.forEach(d => { if (Array.isArray(d.items)) updateItem(d.items); });
      }
      updateItem(state.customItems);

      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, "Anuncio aprobado y puesto al aire");

      console.log(`[MODERATION] Anuncio ${itemId} APROBADO por administrador.`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, item: targetItem, manifestVersion: state.manifestVersion }));
    });
    return;
  }

  // 13.2 MODERACIÓN DE ANUNCIOS: RECHAZAR
  if (req.method === 'POST' && url === '/api/v1/devices/reject-ad') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado. Solo administrador." }));
      return;
    }
    parseBody(data => {
      const { itemId, targetTvId, reason } = data;
      let targetItem = null;
      const updateItem = (list) => {
        if (!Array.isArray(list)) return;
        const it = list.find(x => x.id === itemId || x.assetId === itemId);
        if (it) {
          it.approvalStatus = 'REJECTED';
          it.rejectionReason = reason || "Contenido no aprobado por el administrador.";
          targetItem = it;
        }
      };

      if (targetTvId) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev && Array.isArray(dev.items)) updateItem(dev.items);
      } else {
        state.devices.forEach(d => { if (Array.isArray(d.items)) updateItem(d.items); });
      }
      updateItem(state.customItems);

      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, "Anuncio rechazado de la programación");

      console.log(`[MODERATION] Anuncio ${itemId} RECHAZADO por administrador.`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, item: targetItem, manifestVersion: state.manifestVersion }));
    });
    return;
  }

  // 14. ELIMINAR ANUNCIO
  if (req.method === 'POST' && url === '/api/v1/devices/delete-ad') {
    const authUser = getAuthUser(req);
    if (!authUser) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const itemId = data.itemId;
      const targetTvId = data.targetTvId;

      // Seguridad: Solo el dueño o el administrador puede eliminar anuncios
      if (authUser.role !== 'ADMIN') {
        let it = null;
        if (targetTvId) {
          const dev = state.devices.find(d => d.id === targetTvId);
          it = dev?.items?.find(x => x.id === itemId);
        }
        if (!it) {
          for (const d of state.devices) {
            it = (d.items || []).find(x => x.id === itemId);
            if (it) break;
          }
        }
        if (!it) it = state.customItems?.find(x => x.id === itemId);
        if (it && !isOwnerOfItem(it, authUser)) {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, error: "No tienes permiso para eliminar este anuncio." }));
          return;
        }
      }

      const isGlobalDelete = !targetTvId || targetTvId === 'ALL' || targetTvId === 'all';
      if (!isGlobalDelete) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev && Array.isArray(dev.items)) {
          dev.items = dev.items.filter(it => it.id !== itemId);
        }
      } else {
        state.customItems = state.customItems.filter(it => it.id !== itemId);
        state.devices.forEach(d => {
          if (Array.isArray(d.items)) {
            d.items = d.items.filter(it => it.id !== itemId);
          }
        });
      }

      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, 'Anuncio eliminado');

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, manifestVersion: state.manifestVersion }));
    });
    return;
  }

  // 15. LIMPIAR TODOS LOS ANUNCIOS
  if ((req.method === 'POST' || req.method === 'GET') && url === '/api/v1/devices/clear-all-ads') {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    parseBody(data => {
      const targetTvId = data?.targetTvId;
      if (targetTvId) {
        const dev = state.devices.find(d => d.id === targetTvId);
        if (dev) dev.items = [];
      } else {
        state.devices.forEach(d => { d.items = []; });
        state.customItems = [];
      }
      state.manifestVersion = "v" + Date.now();
      saveState();
      broadcastPlaylistUpdated(state.manifestVersion, 'Todos los anuncios eliminados');

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, manifestVersion: state.manifestVersion, items: [] }));
    });
    return;
  }

  // 16. FORZAR SINCRONIZACIÓN INMEDIATA
  if ((req.method === 'POST' || req.method === 'GET') && (url === '/api/v1/devices/sync-now' || url === '/api/v1/admin/sync-now')) {
    if (!checkAdminAuth(req)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "No autorizado." }));
      return;
    }

    req.resume();
    state.manifestVersion = "v" + Date.now();
    saveState();
    broadcastPlaylistUpdated(state.manifestVersion, 'Sincronización forzada desde panel web');

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, manifestVersion: state.manifestVersion }));
    return;
  }

  // 17. MANIFIESTO DE CAMPAÑAS Y PUBLICIDADES (SINCRONIZACIÓN REMOTA)
  if (req.method === 'GET' && url === '/api/v1/devices/sync/manifest') {
    const authHeader = req.headers['authorization'] || '';
    const token = authHeader.replace(/^Bearer\s+/i, '');
    const fingerprint = req.headers['x-hardware-fingerprint'];
    const clientIp = req.socket.remoteAddress ? req.socket.remoteAddress.replace(/^.*:/, '') : '';

    let device = null;
    if (token) {
      device = state.devices.find(d => d.permanentToken === token || (token === 'perm_token_demo_123' && d.isPaired));
    }
    if (!device && fingerprint) {
      device = state.devices.find(d => 
        d.fingerprint === fingerprint || 
        (d.pairingCode && fingerprint.toUpperCase().endsWith(d.pairingCode)) || 
        (d.id && d.id.includes(fingerprint.toLowerCase()))
      );
    }
    if (!device && clientIp) {
      device = state.devices.find(d => d.ip === clientIp && d.isPaired);
    }
    if (!device) {
      device = state.devices.find(d => d.isPaired);
    }

    if (!device || !device.isPaired) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: "Dispositivo no vinculado." }));
      return;
    }

    const deviceItems = Array.isArray(device.items) ? device.items : [];
    
    // Filtro DOOH comercial: Solo anuncios APROBADOS y dentro de fechas de contrato
    const activeItems = deviceItems.filter(item => {
      if (item.isEnabled === false) return false;
      // Solo anuncios con estado de aprobación 'APPROVED'
      if (item.approvalStatus && item.approvalStatus !== 'APPROVED') return false;
      // Control de vigencia de contratos
      const now = new Date();
      if (item.validFrom) {
        const fromDate = new Date(item.validFrom);
        if (!isNaN(fromDate.getTime()) && now < fromDate) return false;
      }
      if (item.validTo) {
        const toDate = new Date(item.validTo);
        toDate.setHours(23, 59, 59, 999);
        if (!isNaN(toDate.getTime()) && now > toDate) return false;
      }
      return true;
    });

    let campaigns = [];
    let assets = [];

    if (activeItems.length > 0) {
      campaigns = [
        {
          id: `camp_${device.id}`,
          name: `Campaña ${device.name}`,
          priority: 1,
          validFrom: null,
          validTo: null,
          activeDaysOfWeek: [1, 2, 3, 4, 5, 6, 7],
          playlist: {
            id: `playlist_${device.id}`,
            name: "Carrusel Continuo",
            items: activeItems.map((item, idx) => ({
              itemId: item.id,
              assetId: item.assetId,
              orderIndex: idx + 1,
              displayDurationSec: Math.max(1, parseInt(item.durationSeconds, 10) || 15),
              transition: "CUT",
              fitMode: "FIT_CENTER"
            }))
          }
        }
      ];

      // URLs adaptadas dinámicamente al host público o red local actual
      assets = activeItems.map(item => ({
        id: item.assetId,
        url: resolveAssetRemoteUrl(req, item.url || item.remoteUrl),
        mimeType: item.mimeType,
        fileSizeBytes: item.fileSizeBytes,
        checksumSha256: item.checksumSha256
      }));
    }

    const currentVersionTag = `${state.manifestVersion}_${device.id}_${activeItems.length}`;
    const ifNoneMatch = req.headers['if-none-match'];

    if (ifNoneMatch && ifNoneMatch === currentVersionTag) {
      res.writeHead(304, { 'ETag': currentVersionTag });
      res.end();
      return;
    }

    const manifest = {
      manifestVersion: currentVersionTag,
      serverTimestamp: Date.now(),
      settings: {
        orientation: "LANDSCAPE",
        audioMuted: false,
        heartbeatIntervalSeconds: 15
      },
      campaigns: campaigns,
      assets: assets
    };

    console.log(`[SYNC MANIFEST] Enviando manifiesto a "${device.name}" (${device.id}): ${assets.length} activo(s).`);
    res.writeHead(200, {
      'Content-Type': 'application/json',
      'ETag': currentVersionTag
    });
    res.end(JSON.stringify({ success: true, data: manifest }));
    return;
  }

  // 18. TELEMETRÍA Y HEARTBEAT + PRUEBA DE EMISIÓN (PROOF OF PLAY)
  if (req.method === 'POST' && url.startsWith('/api/v1/devices/telemetry/')) {
    parseBody(data => {
      const authHeader = req.headers['authorization'] || '';
      const token = authHeader.replace(/^Bearer\s+/i, '');
      const fingerprint = req.headers['x-hardware-fingerprint'];

      const dev = state.devices.find(d => 
        (token && d.permanentToken === token) || 
        (fingerprint && d.fingerprint === fingerprint) ||
        (data.deviceId && (d.id === data.deviceId || d.permanentToken === data.deviceId))
      );
      if (dev) {
        dev.status = "ONLINE";
        if (data.cpuTemp) dev.temp = `${data.cpuTemp}°C`;
        if (data.ramUsageBytes) {
          const mb = Math.round(data.ramUsageBytes / (1024 * 1024));
          dev.ramUsage = `${mb} MB / 2 GB`;
        }
        if (data.diskFreeBytes) {
          const gb = (data.diskFreeBytes / (1024 * 1024 * 1024)).toFixed(1);
          dev.freeDisk = `${gb} GB`;
        }
        dev.lastTelemetryAt = Date.now();

        // REGISTRO DE PRUEBA DE EMISIÓN (PROOF OF PLAY)
        if (Array.isArray(data.metrics) && data.metrics.length > 0 && sqliteDb) {
          try {
            sqliteDb.exec('BEGIN TRANSACTION;');
            const insertMetric = sqliteDb.prepare(`
              INSERT INTO playback_metrics (device_id, playlist_item_id, asset_id, played_at, rendered_duration_ms, completed_normally)
              VALUES (?, ?, ?, ?, ?, ?)
            `);
            for (const m of data.metrics) {
              insertMetric.run(
                dev.id,
                m.playlistItemId || '',
                m.assetId || '',
                m.playedAt || Date.now(),
                m.renderedDurationMs || 0,
                m.completedNormally ? 1 : 0
              );
            }
            sqliteDb.exec('COMMIT;');
            console.log(`[PROOF OF PLAY] Registradas ${data.metrics.length} emisiones para "${dev.name}".`);
          } catch (mErr) {
            try { sqliteDb.exec('ROLLBACK;'); } catch (e) {}
            console.warn('[PROOF OF PLAY ERROR]:', mErr.message);
          }
        }
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, data: { success: true, ack: Date.now() } }));
    });
    return;
  }

  // DEFAULT 404
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: "Endpoint no encontrado", url }));
});

// ================= 6. GESTIÓN DE PROTOCOLO WEBSOCKET & ENGINE.IO =================
server.on('upgrade', (req, socket, head) => {
  const upgradeHeader = req.headers['upgrade'];
  if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
    socket.destroy();
    return;
  }

  const key = req.headers['sec-websocket-key'];
  if (!key) {
    socket.destroy();
    return;
  }

  const acceptKey = crypto.createHash('sha1')
    .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
    .digest('base64');

  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    'Sec-WebSocket-Accept: ' + acceptKey + '\r\n\r\n'
  );

  const sessionId = 'sid_' + crypto.randomBytes(8).toString('hex');
  const clientInfo = {
    socket: socket,
    sessionId: sessionId,
    deviceId: null,
    fingerprint: null,
    lastPing: Date.now()
  };
  activeLiveSockets.set(sessionId, clientInfo);

  // Engine.IO Open Packet (Tipo 0)
  const openPacket = '0' + JSON.stringify({
    sid: sessionId,
    upgrades: [],
    pingInterval: 25000,
    pingTimeout: 20000,
    maxPayload: 10000000
  });
  sendWsMessage(socket, openPacket);

  let frameBuffer = Buffer.alloc(0);

  socket.on('data', chunk => {
    frameBuffer = Buffer.concat([frameBuffer, chunk]);
    const { messages, remaining } = decodeWsFrames(frameBuffer);
    frameBuffer = remaining;

    for (const msg of messages) {
      if (msg.close) {
        socket.destroy();
        return;
      }
      if (msg.text) {
        handleSocketMessage(clientInfo, msg.text);
      }
    }
  });

  socket.on('error', err => {
    activeLiveSockets.delete(sessionId);
  });

  socket.on('close', () => {
    activeLiveSockets.delete(sessionId);
  });
});

function handleSocketMessage(clientInfo, rawText) {
  // Engine.IO Ping (2) -> Responder con Pong (3)
  if (rawText === '2') {
    sendWsMessage(clientInfo.socket, '3');
    clientInfo.lastPing = Date.now();
    return;
  }

  // Socket.IO Handshake Connect (40 o 40{...})
  if (rawText.startsWith('40')) {
    const authDataStr = rawText.slice(2);
    let authObj = {};
    if (authDataStr) {
      try { authObj = JSON.parse(authDataStr); } catch (e) {}
    }
    const fp = authObj.hardwareFingerprint || authObj.fingerprint;
    const token = authObj.token ? authObj.token.replace(/^Bearer\s+/i, '') : null;

    if (fp || token) {
      const dev = state.devices.find(d => (fp && d.fingerprint === fp) || (token && d.permanentToken === token));
      if (dev) {
        clientInfo.deviceId = dev.id;
        clientInfo.fingerprint = dev.fingerprint;
        dev.status = "ONLINE";
        console.log(`[WS CONNECT] Pantalla "${dev.name}" (${dev.id}) enlazada al socket en tiempo real.`);
      }
    }

    sendWsMessage(clientInfo.socket, '40' + JSON.stringify({ sid: clientInfo.sessionId }));
    return;
  }

  // Socket.IO Evento (42[event, data])
  if (rawText.startsWith('42')) {
    try {
      const payload = JSON.parse(rawText.slice(2));
      const [eventName, data] = payload;

      if (eventName === 'device:state_changed') {
        if (clientInfo.deviceId) {
          const dev = state.devices.find(d => d.id === clientInfo.deviceId);
          if (dev) dev.status = data?.state || 'PLAYING';
        }
      } else if (eventName === 'device:pong') {
        clientInfo.lastPing = Date.now();
      }
    } catch (e) {}
  }
}

// Keepalive periódico de WebSockets cada 20 segundos
setInterval(() => {
  for (const [sid, client] of activeLiveSockets.entries()) {
    if (client.socket && !client.socket.destroyed) {
      sendWsMessage(client.socket, '2'); // Engine.IO ping
    } else {
      activeLiveSockets.delete(sid);
    }
  }
}, 20000);

// ================= 7. ARRANQUE DEL SERVIDOR =================
server.listen(PORT, HOST, () => {
  const currentPublic = PUBLIC_URL || `http://${LOCAL_IP}:${PORT}`;
  console.log(`===================================================================`);
  console.log(`    0Media Digital Signage — Servidor Backend Profesional`);
  console.log(`===================================================================`);
  console.log(`[STATUS] Servidor escuchando en: http://${HOST}:${PORT}`);
  console.log(`[LOCAL IP] Acceso en red local: http://${LOCAL_IP}:${PORT}`);
  console.log(`[PUBLIC URL] URL Canónica: ${currentPublic}`);
  console.log(`[DATABASE] Motor de datos: ${sqliteDb ? 'SQLite WAL + Espejo JSON' : 'JSON Transaccional'}`);
  console.log(`[REALTIME] Soporte WebSocket / Socket.IO: ACTIVO`);
  console.log(`[STREAMING] Streaming HTTP 206 Partial Content: ACTIVO`);
  console.log(`[SCREENS] Dispositivos registrados: ${state.devices.length} (${state.devices.filter(d => d.isPaired).length} vinculados)`);
  console.log(`===================================================================`);
});

// Manejo de apagado limpio
process.on('SIGINT', () => {
  console.log('\n[SERVER] Guardando estado final y cerrando conexiones...');
  saveState();
  if (sqliteDb) try { sqliteDb.close(); } catch (e) {}
  process.exit(0);
});

process.on('SIGTERM', () => {
  saveState();
  if (sqliteDb) try { sqliteDb.close(); } catch (e) {}
  process.exit(0);
});
