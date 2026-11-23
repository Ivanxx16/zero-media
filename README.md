# 0Media Digital Signage — Ecosistema Completo

Plataforma integral de **Digital Signage (Cartelería Digital)** profesional diseñada para transmisión continua 24/7/365 en pantallas, televisores Android TV / Google TV y gestión remota multi-dispositivo en tiempo real.

---

## 📁 ESTRUCTURA DEL PROYECTO

El proyecto está organizado de manera modular e independiente:

```text
0Media/
├── backend/             # Servidor Node.js (API REST, WebSockets nativos RFC 6455, SQLite WAL, HTTP 206 Streaming)
│   ├── servidor-local.js # Motor principal del backend
│   ├── package.json     # Configuración de ejecución
│   ├── Dockerfile       # Contenedor aislado de backend
│   ├── Caddyfile        # Proxy inverso con HTTPS automático (Let's Encrypt)
│   ├── nginx.conf       # Configuración para Nginx tradicional
│   └── uploads/         # Directorio de almacenamiento de imágenes y videos
│
├── frontend-web/        # Panel de Control Web Administrativo (HTML5, CSS3 Midnight, JS)
│   ├── index.html       # Panel maestro de control
│   ├── app.js           # Lógica reactiva y conexión en vivo
│   ├── styles.css       # Sistema de diseño Midnight
│   └── assets/          # Logotipos y recursos gráficos
│
├── aplicacion-tv/       # Cliente nativo Android TV (Kotlin + Jetpack Compose for TV + Media3 ExoPlayer)
│   ├── app/             # Código fuente de la app Android
│   ├── 0Media_Signage_TV.apk # Instalador APK precompilado listo para televisores
│   ├── gradlew.bat      # Compilador Gradle automatizado
│   └── build.gradle.kts # Configuración de dependencias
│
├── docs/                # Documentación técnica, manual de usuario y diagramas
├── Dockerfile           # Contenedor Docker multi-módulo para despliegue en la nube
├── docker-compose.prod.yml # Pila de producción con Caddy SSL automático
├── iniciar-servidor.bat # Script de inicio rápido del backend
└── abrir-panel.bat      # Script para abrir el panel de control en navegador
```

---

## 1. DESCRIPCIÓN GENERAL

La aplicación funciona como el reproductor multimedia final del ecosistema **0Media**, interactuando con un backend central independiente (Node.js + Express + TypeScript + PostgreSQL + Prisma + Socket.IO) y administrable desde cualquier teléfono móvil o computadora.

### Características Principales:
- **Vinculación Remota Zero-Touch:** Conexión inmediata a la cuenta administrativa mediante escaneo de **Código QR** de alto contraste o digitación de **Código Manual de 6 caracteres**.
- **Motor Multimedia Híbrido:** Reproducción fluida y continua de videos (**MP4 H.264/H.265**) con aceleración por hardware (**AndroidX Media3 / ExoPlayer**) e imágenes de alta resolución (**JPG, PNG, WebP**) con fundido cruzado (**Coil**).
- **Operación 100% Offline:** Persistencia relacional local (**Room Database**) y almacenamiento privado verificado. La reproducción continúa de forma indefinida tras la pérdida de conexión a Internet.
- **Sincronización en Caliente:** Actualizaciones de campañas y listas de reproducción vía **Socket.IO** en tiempo real, descargando nuevos activos en segundo plano sin interrumpir la reproducción actual.
- **Seguridad Criptográfica por Hardware:** Almacenamiento de tokens y credenciales permanentes cifrados con **AES-256-GCM** mediante **Android Keystore**.
- **Tolerancia a Cortes Eléctricos:** Detección de arranque del televisor (**Boot Completed**) y reanudación automática de la transmisión sin requerir una nueva vinculación.
- **Recuperación Automática de Errores:** Omisión automática de videos corruptos en menos de 50 ms para evitar pantallas negras.

---

## 2. ARQUITECTURA TÉCNICA

La solución aplica principios de **Clean Architecture** y **MVVM Reactivo / Unidirectional Data Flow (UDF)** con Coroutines y `StateFlow`.

```
com.digitalsignage.tv/
│
├── core/
│   ├── common/             # Dispatchers, AppResult, Logger cíclico, NetworkMonitor, Hash SHA-256
│   ├── database/           # Persistencia Room (DeviceEntity, Campaigns, Playlists, MediaAssets, Metrics)
│   ├── datastore/          # SharedPreferences y DataStore (Preferencias de pantalla y servidor)
│   ├── security/           # KeystoreHelper (Cifrado AES-256-GCM) y SecureTokenStorage
│   ├── storage/            # EnvironmentConfig (Conmutación dinámica DEV/LAN/PROD)
│   ├── network/            # Retrofit, OkHttp con interceptores, APIs de Dispositivo, Sync y Telemetría
│   ├── network/socket/     # SignageSocketManager con reconexión exponencial y eventos tipados
│   ├── downloader/         # SignageDownloadManager (Descarga atómica con verificación SHA-256)
│   ├── media/              # SignagePlayerEngine (Media3 ExoPlayer con reciclaje de decodificador)
│   └── designsystem/       # Paleta Midnight 0Media, componentes TV y badges de estado
│
├── domain/
│   ├── model/              # Modelos de negocio puros (DeviceIdentity, PairingSession, Playlist, Asset)
│   └── usecase/            # Casos de uso de vinculación, sesión, sincronización y reproducción
│
├── data/
│   └── repository/         # Implementaciones de repositorios (Única Fuente de Verdad)
│
├── feature/
│   ├── pairing/            # UI en Compose for TV para escaneo QR, PIN de 6 chars y temporizador
│   └── player/             # UI de pantalla completa inmersiva, alerta de emergencias y HUD técnico
│
└── app/
    ├── di/                 # AppContainer (Inyección de dependencias limpia)
    ├── receiver/           # BootCompletedReceiver (Arranque tras encendido)
    ├── service/            # SignagePlaybackService (Foreground Service de supervivencia)
    ├── SignageApplication  # Ciclo de vida global y atrapador de excepciones para modo kiosco
    └── MainActivity        # Actividad principal con gestión de foco para control remoto
```

---

## 3. TECNOLOGÍAS Y LIBRERÍAS

| Tecnología | Versión | Uso |
| :--- | :--- | :--- |
| **Kotlin** | `2.0.21` | Lenguaje de programación oficial. |
| **Android SDK** | `minSdk = 24`, `targetSdk = 34` | Cobertura desde Android 7.0 hasta Android 14. |
| **Android Gradle Plugin** | `8.5.2` | Compilación de recursos y generación de binarios. |
| **Gradle** | `8.12` | Sistema de compilación automatizado. |
| **Compose for TV** | `1.0.0-alpha10` | Interfaz reactiva optimizada para control remoto D-Pad. |
| **AndroidX Media3** | `1.3.1` | Motor de reproducción de video (ExoPlayer). |
| **Coil** | `2.6.0` | Carga asíncrona y pre-caché de imágenes en memoria. |
| **Retrofit / OkHttp** | `2.11.0` / `4.12.0` | Cliente REST HTTP con interceptores y timeouts controlados. |
| **Socket.IO Client** | `2.1.1` | Comunicación bidireccional en tiempo real con el backend. |
| **Room Database** | `2.6.1` (vía KSP) | Base de datos relacional SQLite local con modo WAL. |
| **Android Keystore** | `API 24+` | Generación y custodia de clave simétrica AES-256. |
| **ZXing Core** | `3.5.3` | Generador ligero de mapas de bits QR. |

---

## 4. REQUISITOS DEL SISTEMA

- **Dispositivo Destino:** Android TV, Google TV o TV Box con Android 7.0 (API 24) o superior.
- **Memoria RAM Mínima:** 1 GB de RAM (Recomendado: 2 GB o superior).
- **Almacenamiento Libre:** Mínimo 1 GB de espacio interno libre para caché de video.
- **Entorno de Compilación:**
  - JDK 17 (Ej. Microsoft OpenJDK 17 o Temurin 17).
  - Android SDK Platform 34 y Build-Tools 34.0.0+.
  - Conexión a Internet para descargar dependencias en el primer build.

---

## 5. CONFIGURACIÓN DE ENTORNOS Y VARIABLES

Las direcciones del backend se configuran por entorno y pueden ser alternadas dinámicamente desde el menú de la aplicación sin recompilar:

| Entorno | Base URL REST | Base URL Socket.IO | Propósito |
| :--- | :--- | :--- | :--- |
| **`LOCAL_DEV`** | `http://10.0.2.2:3000/` | `http://10.0.2.2:3000` | Pruebas en Emulador de Android TV. |
| **`LAN_TEST`** | `http://192.168.1.100:3000/` | `http://192.168.1.100:3000` | Pruebas en red local (IP configurable). |
| **`PRODUCTION`** | `https://api.digitalsignage.io/` | `https://socket.digitalsignage.io` | Servidores oficiales de producción en la nube. |
| **`CUSTOM`** | Definido en runtime | Definido en runtime | Servidor específico configurado por el técnico. |

---

## 6. COMPILACIÓN Y GENERACIÓN DEL APK

### A. Ejecutar Pruebas Unitarias
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest --no-daemon
```

### B. Generar APK de Desarrollo (Debug)
```powershell
.\gradlew.bat assembleDebug --no-daemon
```
- **Ruta del archivo generado:**
  `app/build/outputs/apk/debug/app-debug.apk`

### C. Generar APK de Producción (Release)
```powershell
.\gradlew.bat assembleRelease --no-daemon
```
- **Ruta del archivo generado:**
  `app/build/outputs/apk/release/app-release.apk`

---

## 7. INSTALACIÓN EN TELEVISORES Y TV BOXES

### Método 1: Instalación mediante Memoria USB (Pendrive)
1. Copie el archivo `app-release.apk` (o `app-debug.apk`) a una memoria USB formateada en FAT32 o NTFS.
2. Conecte la memoria USB al puerto del televisor o TV Box.
3. En el televisor, abra una aplicación de explorador de archivos (Ej. *X-plore File Manager* o *FX File Explorer*).
4. Seleccione el archivo `.apk` y presione **Instalar**.
   *(Si el sistema solicita habilitar "Instalar aplicaciones de fuentes desconocidas", conceda el permiso al explorador de archivos).*
5. Abra la aplicación **0Media Digital Signage** desde el lanzador de aplicaciones.

### Método 2: Instalación Remota mediante ADB (Red Local)
1. En el televisor, vaya a **Ajustes > Preferencias del dispositivo > Información** y presione 7 veces seguidas sobre **Número de compilación** para activar el menú de desarrollador.
2. Vaya a **Ajustes > Opciones de desarrollador** y active la **Depuración por red / Depuración USB**.
3. Conecte el televisor desde la computadora mediante PowerShell:
   ```powershell
   adb connect <IP_DEL_TELEVISOR>:5555
   adb install -r "app\build\outputs\apk\release\app-release.apk"
   ```
4. Para iniciar la aplicación de inmediato por comando:
   ```powershell
   adb shell am start -n com.digitalsignage.tv/.MainActivity
   ```

---

## 8. PROCEDIMIENTO DE VINCULACIÓN PASO A PASO

1. **Primer Inicio:** Al abrir la aplicación, el televisor detectará que no posee credenciales y mostrará la pantalla de vinculación con el banner oficial, el **Código QR** y el **Código Manual de 6 caracteres**.
2. **Escaneo por QR:**
   - Desde un teléfono inteligente conectado a la cuenta de administrador, escanee el código QR que se muestra en pantalla.
   - El panel web abrirá la confirmación de vinculación solicitando asignarle un nombre y ubicación al televisor.
3. **Ingreso por Código Manual:**
   - Si prefiere digitarlo, abra el panel web de 0Media, diríjase a **Pantallas > Vincular Dispositivo** e introduzca el código de 6 caracteres (ej. `A7X9K2`).
4. **Autorización Instantánea:**
   - El televisor recibirá la confirmación automáticamente por WebSocket (o sondeo de respaldo), resguardará las credenciales en **Android Keystore**, descargará la lista de reproducción asignada y comenzará la transmisión ininterrumpida a pantalla completa.

---

## 9. FUNCIONAMIENTO OFFLINE Y SINCRONIZACIÓN

- **¿Qué ocurre si se corta la conexión a Internet?**
  La aplicación no se detiene ni muestra errores. El reproductor continúa en bucle infinito reproduciendo las imágenes y videos guardados en `/signage_media`.
- **¿Qué ocurre cuando regresa Internet?**
  La aplicación reconecta el WebSocket de forma automática, reporta su estado de salud (*Heartbeat*), valida la sesión y consulta si hay cambios en el manifiesto. Si se añadieron nuevos anuncios, los descarga en segundo plano y los incorpora al finalizar la vuelta del bucle actual.
- **¿Qué ocurre si el televisor se apaga o reinicia?**
  Al arrancar el TV Box, el receptor de inicio automático despierta la aplicación. Esta recupera las credenciales del Keystore y reanuda la reproducción desde el almacenamiento local sin requerir un nuevo código QR.

---

## 10. MENÚ DE SERVICIO TÉCNICO Y TECLAS DE CONTROL REMOTO

| Tecla del Control Remoto | Acción en la Aplicación |
| :--- | :--- |
| **`FLECHA ARRIBA` / `MENU`** | Despliega u oculta el **HUD de servicio técnico** (muestra la campaña activa, formato del video, tiempo de reproducción y resolución). |
| **`BACK` (Atrás)** | Abre el panel de diagnóstico completo para inspeccionar logs en memoria, forzar pruebas de red o desvincular el equipo. |
| **`OK / CENTER`** | Interactúa con los botones enfocado en las pantallas de diagnóstico o vinculación. |

---

## 11. CONTRATO DE INTEGRACIÓN DEL BACKEND CENTRAL

El backend Node.js / Express debe proveer los siguientes endpoints y eventos para interactuar con este cliente:

### Endpoints REST:
- `POST /api/v1/devices/pairing/initiate`: Recibe `{ hardwareFingerprint, deviceInfo }` y responde `{ pairingCode, qrPayload, tempToken, expiresInSeconds }`.
- `GET /api/v1/devices/pairing/poll`: Sondeo con cabecera `x-temp-token` para verificar confirmación de vinculación.
- `GET /api/v1/devices/sync/manifest`: Consulta de lista activa con cabecera `Authorization: Bearer <deviceToken>`. Admite `If-None-Match` para devolver `304 Not Modified`.
- `POST /api/v1/devices/telemetry/heartbeat`: Envío de signos vitales (temperatura, RAM, almacenamiento libre).
- `POST /api/v1/devices/telemetry/metrics`: Envío en lote de métricas de reproducción (*Proof of Play* acumuladas offline).

### Eventos Socket.IO:
- **`device:paired` (Servidor -> TV):** Emite `{ deviceId, permanentToken, tenantId }`.
- **`playlist:updated` (Servidor -> TV):** Notifica actualización de anuncios con `{ manifestVersion }`.
- **`device:overlay_alert` (Servidor -> TV):** Muestra cintillo de emergencia con `{ alertId, text, durationSec }`.
- **`device:revoked` (Servidor -> TV):** Fuerza desvinculación inmediata con `{ reason }`.
- **`device:state_changed` (TV -> Servidor):** Notifica `{ state: "PLAYING", assetId }`.
- **`device:pong` (TV -> Servidor):** Responde a pings periódicos de latencia.
