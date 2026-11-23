# -*- coding: utf-8 -*-
import os
import subprocess
import base64

def generate_pdf():
    workspace_dir = r"c:\Users\ASUS\Desktop\0Media"
    logo_path = os.path.join(workspace_dir, "Logo0Media.png")
    output_html = os.path.join(workspace_dir, "owner_manual.html")
    output_pdf = os.path.join(workspace_dir, "0Media_Especificaciones_Tecnicas_Propietario.pdf")
    chrome_path = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

    logo_b64 = ""
    if os.path.exists(logo_path):
        with open(logo_path, "rb") as f:
            logo_b64 = base64.b64encode(f.read()).decode("utf-8")

    html_content = f"""<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>0Media Digital Signage — Especificaciones Técnicas y Manual del Propietario</title>
<style>
    @page {{
        size: A4;
        margin: 20mm 15mm 20mm 15mm;
        @bottom-right {{
            content: counter(page);
        }}
    }}
    
    body {{
        font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif;
        color: #1e293b;
        background-color: #ffffff;
        line-height: 1.55;
        font-size: 11pt;
        margin: 0;
        padding: 0;
    }}

    .cover {{
        page-break-after: always;
        height: 100vh;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        padding: 40px 20px;
        box-sizing: border-box;
    }}

    .cover-top {{
        border-bottom: 3px solid #00E5FF;
        padding-bottom: 25px;
    }}

    .logo-img {{
        max-width: 260px;
        height: auto;
    }}

    .cover-title {{
        margin-top: 50px;
    }}

    .cover-title h1 {{
        font-size: 32pt;
        color: #070B14;
        margin: 0 0 10px 0;
        font-weight: 900;
        letter-spacing: -0.5px;
    }}

    .cover-title h2 {{
        font-size: 16pt;
        color: #0284c7;
        margin: 0 0 20px 0;
        font-weight: 600;
    }}

    .cover-title p {{
        font-size: 12pt;
        color: #64748b;
        max-width: 600px;
        line-height: 1.6;
    }}

    .cover-meta {{
        background: #f8fafc;
        border-left: 4px solid #00E5FF;
        padding: 18px 24px;
        border-radius: 0 8px 8px 0;
    }}

    .cover-meta table {{
        width: 100%;
        border-collapse: collapse;
    }}

    .cover-meta td {{
        padding: 4px 0;
        font-size: 10pt;
    }}

    .cover-meta td.label {{
        font-weight: 700;
        color: #334155;
        width: 160px;
    }}

    .cover-meta td.val {{
        color: #0f172a;
    }}

    /* Section Styling */
    h2.section-title {{
        font-size: 18pt;
        color: #070B14;
        border-bottom: 2px solid #e2e8f0;
        padding-bottom: 8px;
        margin-top: 35px;
        margin-bottom: 16px;
        display: flex;
        align-items: center;
        page-break-after: avoid;
    }}

    h3.subsection-title {{
        font-size: 13pt;
        color: #0369a1;
        margin-top: 22px;
        margin-bottom: 10px;
        page-break-after: avoid;
    }}

    p {{
        margin: 0 0 12px 0;
        text-align: justify;
    }}

    .highlight-box {{
        background: #f0f9ff;
        border: 1px solid #bae6fd;
        border-left: 4px solid #0284c7;
        padding: 14px 18px;
        border-radius: 4px;
        margin: 16px 0;
    }}

    .highlight-box strong {{
        color: #0369a1;
    }}

    .page-break {{
        page-break-before: always;
    }}

    /* Grid & Cards */
    .card-grid {{
        display: table;
        width: 100%;
        margin: 15px 0;
    }}

    .card-row {{
        display: table-row;
    }}

    .card {{
        display: table-cell;
        width: 48%;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 14px;
        vertical-align: top;
        box-sizing: border-box;
        margin-bottom: 12px;
    }}

    .card.spacer {{
        width: 4%;
        border: none;
        background: transparent;
    }}

    .card h4 {{
        margin: 0 0 6px 0;
        color: #0f172a;
        font-size: 11pt;
        font-weight: 700;
    }}

    .card p {{
        margin: 0;
        font-size: 9.5pt;
        color: #475569;
        line-height: 1.45;
    }}

    .badge {{
        display: inline-block;
        padding: 2px 7px;
        border-radius: 4px;
        font-size: 8.5pt;
        font-weight: 700;
        margin-bottom: 6px;
    }}

    .badge-core {{ background: #e0f2fe; color: #0369a1; }}
    .badge-sec {{ background: #fef3c7; color: #92400e; }}
    .badge-media {{ background: #dcfce7; color: #166534; }}
    .badge-db {{ background: #f3e8ff; color: #6b21a8; }}

    /* Tables */
    table.data-table {{
        width: 100%;
        border-collapse: collapse;
        margin: 14px 0 20px 0;
        font-size: 9pt;
        page-break-inside: avoid;
    }}

    table.data-table th {{
        background: #0f172a;
        color: #ffffff;
        text-align: left;
        padding: 9px 12px;
        font-weight: 600;
    }}

    table.data-table td {{
        padding: 8px 12px;
        border-bottom: 1px solid #e2e8f0;
        vertical-align: top;
    }}

    table.data-table tr:nth-child(even) {{
        background: #f8fafc;
    }}

    table.data-table tr:hover {{
        background: #f1f5f9;
    }}

    code {{
        font-family: 'Consolas', 'Courier New', monospace;
        background: #f1f5f9;
        color: #0f172a;
        padding: 1px 4px;
        border-radius: 3px;
        font-size: 8.5pt;
    }}

    .flow-step {{
        margin: 10px 0;
        padding: 10px 14px;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
    }}

    .flow-step-num {{
        display: inline-block;
        width: 22px;
        height: 22px;
        background: #00E5FF;
        color: #070B14;
        font-weight: 900;
        border-radius: 50%;
        text-align: center;
        line-height: 22px;
        font-size: 10pt;
        margin-right: 8px;
    }}

    .footer {{
        margin-top: 30px;
        border-top: 1px solid #e2e8f0;
        padding-top: 10px;
        font-size: 8.5pt;
        color: #94a3b8;
        display: flex;
        justify-content: space-between;
    }}
</style>
</head>
<body>

<!-- PORTADA -->
<div class="cover">
    <div class="cover-top">
        {'<img src="data:image/png;base64,' + logo_b64 + '" class="logo-img" alt="0Media Logo">' if logo_b64 else '<h2>0MEDIA</h2>'}
    </div>
    
    <div class="cover-title">
        <h1>0MEDIA DIGITAL SIGNAGE</h1>
        <h2>Manual de Arquitectura, Componentes y Base de Datos</h2>
        <p>
            Documento técnico y estratégico exclusivo para el <strong>Propietario de la Aplicación</strong>. 
            Detalla la arquitectura de software construida, la función exacta de cada componente, 
            el esquema de base de datos local y los mecanismos de resiliencia implementados para garantizar 
            un producto de cartelería digital autónomo, comercial y escalable.
        </p>
    </div>

    <div class="cover-meta">
        <table>
            <tr>
                <td class="label">Plataforma:</td>
                <td class="val">Android TV, Google TV & TV Box Android (AOSP)</td>
            </tr>
            <tr>
                <td class="label">Lenguaje y Entorno:</td>
                <td class="val">Kotlin 2.0.21 • Android SDK (minSdk 24, targetSdk 34) • Gradle 8.12</td>
            </tr>
            <tr>
                <td class="label">Arquitectura:</td>
                <td class="val">Clean Architecture • MVVM Reactivo • Flujo Unidireccional (UDF)</td>
            </tr>
            <tr>
                <td class="label">Disponibilidad:</td>
                <td class="val">Operación Continua 24/7/365 • Modo 100% Offline Nativo</td>
            </tr>
            <tr>
                <td class="label">Versión del Sistema:</td>
                <td class="val">v1.0.0 (Release para Producción)</td>
            </tr>
        </table>
    </div>
</div>

<!-- SECCIÓN 1: QUÉ ARQUITECTURA SE UTILIZÓ Y POR QUÉ -->
<h2 class="section-title">1. ¿Qué Arquitectura se Utilizó y qué Valor Aporta al Negocio?</h2>

<p>
    Como dueño de la aplicación, su mayor activo es tener un software <strong>mantenible, modular y protegido contra la obsolescencia tecnológica</strong>. 
    Muchas aplicaciones de cartelería digital fracasan porque mezclan la interfaz visual con la descarga de videos y la base de datos en un solo archivo masivo, 
    provocando congelamientos de pantalla cuando falla la red o cuando los televisores llevan semanas encendidos.
</p>

<p>
    En <strong>0Media Digital Signage</strong> se implementó una arquitectura basada en los principios de <strong>Clean Architecture</strong> 
    y el patrón de presentación reactivo <strong>MVVM (Model-View-Intent / Unidirectional Data Flow)</strong>.
</p>

<div class="highlight-box">
    <strong>Principio Rector del Sistema:</strong>
    <em>"La interfaz visual nunca decide qué reproducir ni cuándo descargar. 
    La interfaz es un observador pasivo del estado expuesto por la base de datos local, la cual actúa como la Única Fuente de Verdad (Single Source of Truth)."</em>
</div>

<h3 class="subsection-title">Las 4 Capas del Sistema</h3>

<table class="data-table">
    <thead>
        <tr>
            <th style="width: 20%;">Capa</th>
            <th style="width: 35%;">¿Qué contiene?</th>
            <th style="width: 45%;">¿Por qué es crucial para su negocio?</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><strong>1. Presentación (UI)</strong></td>
            <td>Jetpack Compose for TV, Pantalla de Vinculación (QR/PIN), Pantalla de Reproductor Fullscreen y HUD Técnico.</td>
            <td>Control total de la experiencia visual sin parpadeos, adaptada al control remoto (D-Pad) y optimizada para no gastar memoria innecesaria.</td>
        </tr>
        <tr>
            <td><strong>2. Dominio (Domain)</strong></td>
            <td>Casos de Uso puros en Kotlin (<code>GetActivePlaylistUseCase</code>, <code>InitiatePairingUseCase</code>, etc.).</td>
            <td>Reglas de negocio aisladas. Si mañana decide cambiar el reproductor o la base de datos, las reglas de cómo se programan las campañas nunca se rompen.</td>
        </tr>
        <tr>
            <td><strong>3. Datos (Data)</strong></td>
            <td>Repositorios (<code>DeviceRepository</code>, <code>SyncRepository</code>, <code>TelemetryRepository</code>).</td>
            <td>Orquesta qué datos vienen de Internet y qué datos se leen del disco local. Oculta la complejidad de la red al resto del sistema.</td>
        </tr>
        <tr>
            <td><strong>4. Infraestructura (Core)</strong></td>
            <td>AndroidKeyStore, Room Database, Media3 (ExoPlayer), Coil, OkHttp, Retrofit y Socket.IO.</td>
            <td>Los motores de bajo nivel que interactúan con el hardware físico del televisor (antena WiFi, procesador gráfico, chip criptográfico).</td>
        </tr>
    </tbody>
</table>

<!-- SECCIÓN 2: MAPA DE COMPONENTES Y QUÉ HACE CADA COSA -->
<div class="page-break"></div>
<h2 class="section-title">2. Mapa de Componentes: ¿Qué Hace Cada Pieza de la Aplicación?</h2>

<p>
    A continuación se describe de forma precisa cada componente central desarrollado dentro del proyecto, su función técnica y su importancia operativa:
</p>

<div class="card-grid">
    <div class="card-row">
        <div class="card">
            <span class="badge badge-media">MOTOR MULTIMEDIA</span>
            <h4>SignagePlayerEngine (ExoPlayer / Media3)</h4>
            <p>
                Es el motor que reproduce los videos (MP4 H.264/H.265). A diferencia de apps básicas, este motor <strong>reutiliza una única instancia de decodificación por hardware</strong>. 
                Si un TV Box económico colapsa por un video dañado, el motor atrapa el error y salta al siguiente anuncio en 50 milisegundos sin congelar la pantalla.
            </p>
        </div>
        <div class="card spacer"></div>
        <div class="card">
            <span class="badge badge-media">MOTOR DE IMÁGENES</span>
            <h4>Coil Image Engine</h4>
            <p>
                Gestiona la carga de afiches y carteles estáticos (JPG, PNG, WebP). Realiza un pre-reescalado a la resolución exacta del monitor del TV 
                y aplica una transición suave de 300 ms (Crossfade), evitando el consumo excesivo de memoria RAM que suele provocar reinicios inesperados.
            </p>
        </div>
    </div>
</div>

<div class="card-grid">
    <div class="card-row">
        <div class="card">
            <span class="badge badge-core">DESCARGA Y DISCO</span>
            <h4>SignageDownloadManager</h4>
            <p>
                Descarga los archivos multimedia desde la nube al almacenamiento privado del televisor. 
                Aplica <strong>escritura atómica con validación SHA-256</strong>: el reproductor nunca intenta mostrar un video a medio descargar; 
                solo cuando el archivo está 100% completo y su firma criptográfica coincide, se habilita en la playlist.
            </p>
        </div>
        <div class="card spacer"></div>
        <div class="card">
            <span class="badge badge-core">TIEMPO REAL</span>
            <h4>SignageSocketManager (Socket.IO)</h4>
            <p>
                Mantiene una conexión abierta y silenciosa (WebSocket) con el backend. 
                Permite que cuando usted modifique un anuncio desde su celular en el panel administrativo, 
                el televisor lo sepa al instante sin tener que esperar minutos ni saturar el servidor con peticiones constantes.
            </p>
        </div>
    </div>
</div>

<div class="card-grid">
    <div class="card-row">
        <div class="card">
            <span class="badge badge-sec">SEGURIDAD BANCARIA</span>
            <h4>KeystoreHelper & SecureTokenStorage</h4>
            <p>
                Cifra los tokens de acceso del televisor utilizando el chip seguro de hardware del dispositivo (<strong>AES-256-GCM</strong>). 
                Aunque alguien robe el TV Box físico y extraiga su memoria, <strong>no puede robar las credenciales ni acceder a los servidores de 0Media</strong>.
            </p>
        </div>
        <div class="card spacer"></div>
        <div class="card">
            <span class="badge badge-core">AUTONOMÍA 24/7</span>
            <h4>BootCompletedReceiver & PlaybackService</h4>
            <p>
                Garantiza que si el centro comercial o local sufre un corte de energía eléctrica, en cuanto el televisor se encienda de nuevo, 
                la aplicación se inicie sola automáticamente, recupere sus campañas locales y empiece a reproducir sin intervención humana.
            </p>
        </div>
    </div>
</div>

<div class="card-grid">
    <div class="card-row">
        <div class="card">
            <span class="badge badge-core">SUPERVISIÓN DE RED</span>
            <h4>LiveNetworkMonitor</h4>
            <p>
                Vigila permanentemente el estado del cable de red (Ethernet) o señal Wi-Fi. 
                Si la red cae, activa el modo de supervivencia offline; cuando la red vuelve, sincroniza las métricas acumuladas y reconecta el socket.
            </p>
        </div>
        <div class="card spacer"></div>
        <div class="card">
            <span class="badge badge-sec">HUELLA DIGITAL</span>
            <h4>AndroidHardwareIdProvider</h4>
            <p>
                Calcula una firma criptográfica inmutable combinando el procesador, número de serie y placa base del televisor. 
                Evita la clonación de pantallas y permite saber con total certeza qué televisor físico está operando.
            </p>
        </div>
    </div>
</div>

<!-- SECCIÓN 3: ESTRUCTURA DE LA BASE DE DATOS LOCAL -->
<div class="page-break"></div>
<h2 class="section-title">3. ¿Qué Conforma la Base de Datos Local (Room / SQLite)?</h2>

<p>
    La aplicación contiene su propio motor de base de datos relacional interno (Room SQLite con modo de alto rendimiento <code>WAL</code>). 
    Esta base de datos es la razón por la cual <strong>su reproductor puede seguir funcionando sin Internet durante días o meses</strong>.
</p>

<p>
    A continuación se detallan las <strong>7 entidades (tablas)</strong> que conforman el corazón de datos del televisor:
</p>

<h3 class="subsection-title">1. Tabla: <code>device_identity</code> (Identidad y Estado del Dispositivo)</h3>
<p>Guarda quién es este televisor y si tiene autorización para operar.</p>
<table class="data-table">
    <thead>
        <tr>
            <th>Campo</th>
            <th>Tipo</th>
            <th>Significado para el Negocio</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><code>hardwareFingerprint</code> (PK)</td>
            <td>TEXT</td>
            <td>Identificador inmutable único del procesador físico del televisor.</td>
        </tr>
        <tr>
            <td><code>deviceId</code></td>
            <td>TEXT (Nullable)</td>
            <td>ID único asignado por el backend al autorizar la vinculación.</td>
        </tr>
        <tr>
            <td><code>tenantId</code></td>
            <td>TEXT (Nullable)</td>
            <td>Identificador de la empresa o cliente dueño de esta pantalla.</td>
        </tr>
        <tr>
            <td><code>deviceName</code></td>
            <td>TEXT</td>
            <td>Nombre comercial (ej. "Pantalla Sucursal Norte 01").</td>
        </tr>
        <tr>
            <td><code>pairingStatus</code></td>
            <td>INTEGER (Enum)</td>
            <td>Estado actual: <code>UNPAIRED</code>, <code>PAIRING_IN_PROGRESS</code>, <code>PAIRED</code> o <code>REVOKED</code>.</td>
        </tr>
        <tr>
            <td><code>lastSyncAt</code></td>
            <td>INTEGER (Timestamp)</td>
            <td>Fecha y hora exacta de la última comunicación exitosa con el servidor.</td>
        </tr>
    </tbody>
</table>

<h3 class="subsection-title">2. Tabla: <code>campaigns</code> (Campañas Comerciales Programadas)</h3>
<p>Determina las reglas de negocio de cuándo debe emitirse la publicidad.</p>
<table class="data-table">
    <thead>
        <tr>
            <th>Campo</th>
            <th>Tipo</th>
            <th>Significado para el Negocio</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><code>id</code> (PK)</td>
            <td>TEXT</td>
            <td>Identificador oficial de la campaña (ej. "cmp_verano_2026").</td>
        </tr>
        <tr>
            <td><code>name</code></td>
            <td>TEXT</td>
            <td>Nombre descriptivo visible en el panel administrativo.</td>
        </tr>
        <tr>
            <td><code>priority</code></td>
            <td>INTEGER</td>
            <td>Prioridad de visualización (campañas de mayor valor publicitario se emiten primero).</td>
        </tr>
        <tr>
            <td><code>validFrom</code> / <code>validTo</code></td>
            <td>INTEGER</td>
            <td>Rango de fechas de vigencia. El TV no reproduce anuncios antes ni después de estas fechas.</td>
        </tr>
        <tr>
            <td><code>activeDaysOfWeek</code></td>
            <td>TEXT (JSON List)</td>
            <td>Días activos (ej. <code>[1,2,3,4,5]</code> para lunes a viernes). Permite pautas comerciales por día.</td>
        </tr>
        <tr>
            <td><code>isActive</code></td>
            <td>BOOLEAN</td>
            <td>Interruptor maestro para apagar o encender una campaña remotamente.</td>
        </tr>
    </tbody>
</table>

<h3 class="subsection-title">3. Tablas: <code>playlists</code> y <code>playlist_items</code> (Listas y Anuncios)</h3>
<p>Organiza la secuencia exacta de videos e imágenes dentro de cada campaña.</p>
<table class="data-table">
    <thead>
        <tr>
            <th>Campo</th>
            <th>Tipo</th>
            <th>Significado para el Negocio</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><code>playlistId</code></td>
            <td>TEXT (FK)</td>
            <td>Vincula la lista con la campaña correspondiente.</td>
        </tr>
        <tr>
            <td><code>assetId</code></td>
            <td>TEXT (FK)</td>
            <td>Apunta al video o imagen que debe reproducirse.</td>
        </tr>
        <tr>
            <td><code>orderIndex</code></td>
            <td>INTEGER</td>
            <td>Posición en la secuencia (0, 1, 2, 3...) para respetar el orden establecido por el cliente.</td>
        </tr>
        <tr>
            <td><code>displayDurationSec</code></td>
            <td>INTEGER</td>
            <td>Segundos exactos que debe permanecer una imagen en pantalla (ej. 15 segundos).</td>
        </tr>
        <tr>
            <td><code>fitMode</code></td>
            <td>TEXT</td>
            <td><code>FIT_CENTER</code> (mantiene proporción original) o <code>CROP_CENTER</code> (llena toda la pantalla).</td>
        </tr>
    </tbody>
</table>

<!-- TABLA DE ACTIVOS Y AUDITORÍA -->
<div class="page-break"></div>
<h3 class="subsection-title">4. Tabla: <code>media_assets</code> (Inventario de Archivos en Disco)</h3>
<p>Controla los archivos físicos que están almacenados en el disco del televisor.</p>
<table class="data-table">
    <thead>
        <tr>
            <th>Campo</th>
            <th>Tipo</th>
            <th>Significado para el Negocio</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><code>id</code> (PK)</td>
            <td>TEXT</td>
            <td>Identificador único del archivo publicitario.</td>
        </tr>
        <tr>
            <td><code>remoteUrl</code></td>
            <td>TEXT</td>
            <td>Dirección en la nube de donde se descargó el medio (CDN).</td>
        </tr>
        <tr>
            <td><code>localFilePath</code></td>
            <td>TEXT</td>
            <td>Ruta física en el disco interno del TV (ej. <code>/signage_media/ast_01.mp4</code>).</td>
        </tr>
        <tr>
            <td><code>mediaType</code></td>
            <td>INTEGER (Enum)</td>
            <td>Diferencia si es un <code>IMAGE</code> o un <code>VIDEO</code>.</td>
        </tr>
        <tr>
            <td><code>checksumSha256</code></td>
            <td>TEXT</td>
            <td>Firma digital de 64 caracteres. Garantiza que el archivo no esté dañado ni alterado.</td>
        </tr>
        <tr>
            <td><code>downloadStatus</code></td>
            <td>INTEGER (Enum)</td>
            <td><code>PENDING</code> (0), <code>DOWNLOADING</code> (1), <code>COMPLETED</code> (2), <code>FAILED</code> (3), <code>CORRUPTED</code> (4).</td>
        </tr>
        <tr>
            <td><code>lastAccessed</code></td>
            <td>INTEGER</td>
            <td>Fecha de último uso. Permite purgar archivos viejos cuando el disco se llena (política LRU).</td>
        </tr>
    </tbody>
</table>

<h3 class="subsection-title">5. Tabla: <code>playback_metrics</code> (Auditoría de Emisiones / Proof of Play)</h3>
<p>
    <strong>El componente comercial más valioso:</strong> Guarda un registro exacto de cada segundo de publicidad transmitido. 
    Permite demostrar a los anunciantes con datos fehacientes que sus spots fueron reproducidos.
</p>
<table class="data-table">
    <thead>
        <tr>
            <th>Campo</th>
            <th>Tipo</th>
            <th>Significado para el Negocio</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><code>id</code> (PK)</td>
            <td>INTEGER (Auto)</td>
            <td>Identificador del evento de auditoría.</td>
        </tr>
        <tr>
            <td><code>assetId</code> / <code>playlistItemId</code></td>
            <td>TEXT</td>
            <td>Qué anuncio específico se transmitió.</td>
        </tr>
        <tr>
            <td><code>playedAt</code></td>
            <td>INTEGER (Timestamp)</td>
            <td>Fecha y hora exacta en que comenzó la reproducción.</td>
        </tr>
        <tr>
            <td><code>renderedDurationMs</code></td>
            <td>INTEGER</td>
            <td>Duración real transmitida en milisegundos (ej. 30,000 ms = 30 segundos).</td>
        </tr>
        <tr>
            <td><code>completedNormally</code></td>
            <td>BOOLEAN</td>
            <td><code>true</code> si el spot finalizó completo; <code>false</code> si se interrumpió o falló.</td>
        </tr>
        <tr>
            <td><code>failureReason</code></td>
            <td>TEXT (Nullable)</td>
            <td>Motivo técnico si hubo un error (ej. codec no soportado).</td>
        </tr>
        <tr>
            <td><code>isSynced</code></td>
            <td>BOOLEAN</td>
            <td><code>false</code> si ocurrió offline y está pendiente de envío; <code>true</code> cuando el backend ya lo registró.</td>
        </tr>
    </tbody>
</table>

<h3 class="subsection-title">6. Tabla: <code>content_versions</code> (Control de Versión de Manifiesto)</h3>
<p>
    Almacena la etiqueta de versión actual del contenido (ej. <code>v1.4.12</code>). 
    Permite que cuando el televisor consulte al servidor, el servidor responda inmediatamente con un código <code>304 Not Modified</code> 
    si no hay cambios, ahorrando gigabytes de transferencia de datos en planes de telefonía móvil o internet comercial.
</p>

<!-- SECCIÓN 4: CÓMO FUNCIONA TODO DE EXTREMO A EXTREMO -->
<div class="page-break"></div>
<h2 class="section-title">4. El Ciclo de Vida del Negocio: ¿Cómo Funciona la Aplicación en la Práctica?</h2>

<div class="flow-step">
    <span class="flow-step-num">1</span>
    <strong>Primer Encendido y Vinculación Fácil:</strong>
    El instalador conecta el TV Box nuevo y abre la app. La app detecta que no tiene credenciales en el Keystore y genera un Código QR y un PIN de 6 caracteres (ej. <code>A7X - 9K2</code>). 
    El administrador entra a su panel web desde el celular, escanea el QR o escribe el PIN, y el televisor se vincula solo en 2 segundos.
</div>

<div class="flow-step">
    <span class="flow-step-num">2</span>
    <strong>Descarga Inteligente sin Bloqueos:</strong>
    El televisor consulta la lista de anuncios asignada a esa sucursal. Descarga los videos e imágenes en segundo plano a su disco privado. 
    Verifica que cada archivo pese exactamente lo que debe y que su firma SHA-256 sea válida.
</div>

<div class="flow-step">
    <span class="flow-step-num">3</span>
    <strong>Reproducción Continua a Pantalla Completa:</strong>
    Comienza el bucle publicitario automático (Imagen 15s ➔ Video 30s ➔ Imagen 10s ➔ Video 20s...). 
    La pantalla no muestra botones, ni barras, ni menús. Si un video falla por daño físico, se omite silenciosamente en 50 milisegundos sin pantalla negra.
</div>

<div class="flow-step">
    <span class="flow-step-num">4</span>
    <strong>Actualización en Caliente desde el Panel Web:</strong>
    Si usted sube un nuevo anuncio al panel web a las 3:00 PM, el servidor envía un mensaje por WebSocket (<code>playlist:updated</code>). 
    El televisor descarga el nuevo anuncio sin cortar lo que está transmitiendo. Cuando termina la vuelta del bucle actual, incorpora el nuevo anuncio de forma fluida.
</div>

<div class="flow-step">
    <span class="flow-step-num">5</span>
    <strong>Tolerancia Total a Pérdidas de Internet:</strong>
    Si el proveedor de Internet del local comercial sufre un corte de fibra óptica o señal durante horas o días, 
    <strong>el televisor sigue transmitiendo toda su publicidad con total normalidad desde su base de datos y disco local</strong>. 
    Las métricas de emisión se guardan localmente y se suben al servidor en cuanto regrese la conectividad.
</div>

<div class="flow-step">
    <span class="flow-step-num">6</span>
    <strong>Resiliencia ante Cortes de Energía:</strong>
    Si se corta la luz en el local y el TV Box se apaga abruptamente, la base de datos local está protegida contra corrupción (modo WAL). 
    Al regresar la energía eléctrica, el sistema operativo arranca la app de inmediato y la publicidad reanuda sin pedir que nadie vuelva a vincular la pantalla.
</div>

<!-- SECCIÓN 5: CONCLUSIÓN Y RECOMENDACIONES -->
<div class="page-break"></div>
<h2 class="section-title">5. Recomendaciones de Despliegue para el Propietario</h2>

<div class="highlight-box">
    <strong>Estado del Software:</strong>
    El cliente Android TV se entrega 100% probado, compilado y listo para ser instalado en campo mediante el archivo 
    <code>app/build/outputs/apk/release/app-release.apk</code>.
</div>

<ol>
    <li>
        <strong>Elección de TV Boxes en el Mercado:</strong>
        Para compras masivas de hardware, se recomiendan TV Boxes con al menos <strong>2 GB de memoria RAM</strong> y procesadores 
        Amlogic (S905X3, S905X4) o dispositivos oficiales certificados como <em>Chromecast con Google TV</em> o <em>Xiaomi TV Box S (2nd Gen)</em>.
    </li>
    <li>
        <strong>Configuración de Batería en Dispositivos AOSP:</strong>
        En TV Boxes económicos sin certificación oficial de Google, asegúrese durante la instalación de marcar la aplicación como 
        <em>"Sin restricciones de ahorro de energía"</em> en los ajustes de Android, para garantizar que el sistema operativo no congele el proceso.
    </li>
    <li>
        <strong>Respaldo de Claves de Producción:</strong>
        Cuando decida firmar el APK con su propio certificado digital de empresa para Google Play Store o despliegue privado, 
        mantenga su archivo <code>.jks</code> y contraseñas respaldadas en una bóveda segura fuera del repositorio de código.
    </li>
</ol>

<div class="footer">
    <span>0Media Digital Signage Platform • Documento Confidencial</span>
    <span>Especificaciones Técnicas para Propietario de Producto • Octubre 2026</span>
</div>

</body>
</html>
"""

    with open(output_html, "w", encoding="utf-8") as f:
        f.write(html_content)

    print(f"HTML generado exitosamente en: {output_html}")

    # Ejecutar Chrome headless para imprimir a PDF con renderizado perfecto
    cmd = [
        chrome_path,
        "--headless=new",
        "--disable-gpu",
        "--run-all-compositor-stages-before-draw",
        f"--print-to-pdf={output_pdf}",
        f"file:///{output_html.replace(os.sep, '/')}"
    ]

    print("Generando PDF con Google Chrome...")
    subprocess.run(cmd, check=True)

    if os.path.exists(output_pdf):
        size_bytes = os.path.getsize(output_pdf)
        print(f"PDF GENERADO EXITOSAMENTE: {output_pdf} ({size_bytes} bytes)")
    else:
        print("Error: No se pudo generar el archivo PDF.")

if __name__ == "__main__":
    generate_pdf()
