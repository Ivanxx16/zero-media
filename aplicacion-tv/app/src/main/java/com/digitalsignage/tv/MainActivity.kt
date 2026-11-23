package com.digitalsignage.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.digitalsignage.tv.app.service.SignagePlaybackService
import com.digitalsignage.tv.core.common.logging.LogEntry
import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.designsystem.components.SocketStatusBadge
import com.digitalsignage.tv.core.designsystem.components.StatusBadge
import com.digitalsignage.tv.core.designsystem.theme.BrightBlue
import com.digitalsignage.tv.core.designsystem.theme.DeepNavyBackground
import com.digitalsignage.tv.core.designsystem.theme.DigitalSignageTvTheme
import com.digitalsignage.tv.core.designsystem.theme.ElectricCyan
import com.digitalsignage.tv.core.designsystem.theme.SkyGlow
import com.digitalsignage.tv.core.designsystem.theme.StatusOnlineGreen
import com.digitalsignage.tv.core.designsystem.theme.StatusWarningAmber
import com.digitalsignage.tv.core.designsystem.theme.SurfaceBorder
import com.digitalsignage.tv.core.designsystem.theme.SurfaceCard
import com.digitalsignage.tv.core.designsystem.theme.SurfaceCardElevated
import com.digitalsignage.tv.core.designsystem.theme.TextCode
import com.digitalsignage.tv.core.designsystem.theme.TextMuted
import com.digitalsignage.tv.core.designsystem.theme.TextPrimary
import com.digitalsignage.tv.core.designsystem.theme.TextSecondary
import com.digitalsignage.tv.core.storage.AppEnvironment
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on continuously for digital signage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set sticky immersive mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Start foreground service
        SignagePlaybackService.start(this)

        val container = (application as SignageApplication).container

        setContent {
            DigitalSignageTvTheme {
                val coroutineScope = rememberCoroutineScope()
                val isOnline by container.networkMonitor.isOnline.collectAsState(initial = container.networkMonitor.isCurrentlyConnected)
                val socketState by container.socketManager.connectionState.collectAsState()
                val appSettings by container.preferencesDataStore.settingsFlow.collectAsState(
                    initial = com.digitalsignage.tv.core.storage.AppSettings(
                        environment = AppEnvironment.LAN_TEST,
                        customRestUrl = "",
                        customSocketUrl = "",
                        screenOrientation = "LANDSCAPE",
                        isAudioMuted = false,
                        volumeLevel = 100,
                        diagnosticsPin = "0000",
                        lastSyncTimestamp = 0L
                    )
                )

                val deviceIdentity by container.deviceRepository.observeDeviceIdentity().collectAsState(
                    initial = com.digitalsignage.tv.domain.model.DeviceIdentity(
                        hardwareFingerprint = container.hardwareIdProvider.getHardwareFingerprint(),
                        deviceId = null,
                        tenantId = null,
                        deviceName = null,
                        pairingStatus = com.digitalsignage.tv.core.database.entity.PairingStatus.UNPAIRED,
                        hasCredentials = false
                    )
                )

                val pairingViewModel = remember {
                    com.digitalsignage.tv.feature.pairing.viewmodel.PairingViewModel(
                        getDeviceIdentityUseCase = container.getDeviceIdentityUseCase,
                        initiatePairingUseCase = container.initiatePairingUseCase,
                        checkPairingStatusUseCase = container.checkPairingStatusUseCase,
                        validateDeviceSessionUseCase = container.validateDeviceSessionUseCase,
                        unpairDeviceUseCase = container.unpairDeviceUseCase,
                        socketManager = container.socketManager,
                        preferencesDataStore = container.preferencesDataStore,
                        saveCredentialsAction = { deviceId, token, tenantId ->
                            container.deviceRepository.saveDeviceCredentials(deviceId, token, tenantId)
                        }
                    )
                }

                val playerViewModel = remember {
                    com.digitalsignage.tv.feature.player.viewmodel.PlayerViewModel(
                        playerEngine = container.playerEngine,
                        getActivePlaylistUseCase = container.getActivePlaylistUseCase,
                        recordProofOfPlayUseCase = container.recordProofOfPlayUseCase,
                        syncRepository = container.syncRepository,
                        downloadManager = container.downloadManager,
                        telemetryRepository = container.telemetryRepository,
                        socketManager = container.socketManager,
                        preferencesDataStore = container.preferencesDataStore
                    )
                }

                var showDiagnosticsMenu by remember { mutableStateOf(false) }
                var isLocallyPaired by remember { mutableStateOf(false) }
                val pairingUiState by pairingViewModel.uiState.collectAsState()

                // Listen for backend revocation events in real time
                LaunchedEffect(Unit) {
                    container.socketManager.incomingEvents.collect { event ->
                        if (event is com.digitalsignage.tv.core.network.socket.SocketIncomingEvent.DeviceRevoked) {
                            SignageLogger.w("MainActivity", "DeviceRevoked event received: ${event.reason}")
                            isLocallyPaired = false
                            container.unpairDeviceUseCase(event.reason ?: "Revocado remotamente")
                            pairingViewModel.checkInitialState()
                            showDiagnosticsMenu = false
                        }
                    }
                }

                val isPaired = isLocallyPaired ||
                    pairingUiState is com.digitalsignage.tv.feature.pairing.viewmodel.PairingUiState.Paired ||
                    (deviceIdentity.deviceId != null && deviceIdentity.pairingStatus == com.digitalsignage.tv.core.database.entity.PairingStatus.PAIRED)

                // Monitoreo periódico de sesión activa: Si el servidor o panel revoca la vinculación, regresar al QR
                LaunchedEffect(isPaired) {
                    if (isPaired) {
                        while (true) {
                            kotlinx.coroutines.delay(4000L)
                            val validation = container.validateDeviceSessionUseCase()
                            if (validation is com.digitalsignage.tv.core.common.result.AppResult.Error &&
                                validation.exception is com.digitalsignage.tv.core.common.result.AppException.UnauthorizedError) {
                                SignageLogger.w("MainActivity", "Sesión desvinculada por el servidor/panel. Mostrando pantalla de vinculación...")
                                isLocallyPaired = false
                                pairingViewModel.checkInitialState()
                                showDiagnosticsMenu = false
                                break
                            }
                        }
                    }
                }

                if (!isPaired) {
                    com.digitalsignage.tv.feature.pairing.ui.PairingScreen(
                        viewModel = pairingViewModel,
                        isOnline = isOnline,
                        connectionType = container.networkMonitor.getActiveConnectionType(),
                        socketState = socketState,
                        currentEnvironmentName = appSettings.environment.name,
                        onPairingSuccess = { deviceId ->
                            SignageLogger.i("MainActivity", "Successfully paired as deviceId=$deviceId")
                            isLocallyPaired = true
                            playerViewModel.initializePlayback()
                        }
                    )
                } else {
                    if (!showDiagnosticsMenu) {
                        com.digitalsignage.tv.feature.player.ui.PlayerScreen(
                            viewModel = playerViewModel,
                            onOpenDiagnostics = { showDiagnosticsMenu = true }
                        )
                    } else {
                        InfrastructureDashboardScreen(
                            container = container,
                            onUnpairClicked = {
                                coroutineScope.launch {
                                    isLocallyPaired = false
                                    container.unpairDeviceUseCase("Desvinculado por usuario")
                                    pairingViewModel.checkInitialState()
                                    showDiagnosticsMenu = false
                                }
                            },
                            onCloseDiagnostics = {
                                showDiagnosticsMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InfrastructureDashboardScreen(
    container: com.digitalsignage.tv.app.di.AppContainer,
    onUnpairClicked: () -> Unit,
    onCloseDiagnostics: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val isOnline by container.networkMonitor.isOnline.collectAsState(initial = container.networkMonitor.isCurrentlyConnected)
    val socketState by container.socketManager.connectionState.collectAsState()
    val appSettings by container.preferencesDataStore.settingsFlow.collectAsState(
        initial = com.digitalsignage.tv.core.storage.AppSettings(
            environment = AppEnvironment.LAN_TEST,
            customRestUrl = "",
            customSocketUrl = "",
            screenOrientation = "LANDSCAPE",
            isAudioMuted = false,
            volumeLevel = 100,
            diagnosticsPin = "0000",
            lastSyncTimestamp = 0L
        )
    )

    val deviceIdentity by container.deviceRepository.observeDeviceIdentity().collectAsState(
        initial = com.digitalsignage.tv.domain.model.DeviceIdentity(
            hardwareFingerprint = container.hardwareIdProvider.getHardwareFingerprint(),
            deviceId = null,
            tenantId = null,
            deviceName = null,
            pairingStatus = com.digitalsignage.tv.core.database.entity.PairingStatus.UNPAIRED,
            hasCredentials = false
        )
    )

    var connectionTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var logsVisible by remember { mutableStateOf(false) }
    var recentLogs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    // Connect socket when online and credentials exist
    LaunchedEffect(isOnline, appSettings.environment) {
        if (isOnline) {
            val env = container.preferencesDataStore.getResolvedEnvironment()
            container.socketManager.connect(env)
        } else {
            container.socketManager.disconnect()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SurfaceCard, DeepNavyBackground),
                    radius = 1600f
                )
            )
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "0MEDIA",
                            color = ElectricCyan,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Digital Signage OS",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Infraestructura Android TV & Conectividad Backend",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                // Status Badges Row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val connType = container.networkMonitor.getActiveConnectionType()
                    StatusBadge(
                        label = if (isOnline) "Red: $connType" else "Red: Desconectado",
                        isActive = isOnline
                    )
                    SocketStatusBadge(state = socketState)
                    StatusBadge(
                        label = "Env: ${appSettings.environment.name}",
                        isActive = true,
                        activeColor = SkyGlow
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Info Content: 2 Columns
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Column 1: Hardware & Device Identity Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard.copy(alpha = 0.9f))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "ESTADO DEL DISPOSITIVO",
                            color = ElectricCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        InfoRow(label = "Hardware Fingerprint", value = deviceIdentity.hardwareFingerprint.take(24) + "...")
                        InfoRow(label = "Modelo", value = container.hardwareIdProvider.getDeviceInfo().model)
                        InfoRow(label = "SO", value = container.hardwareIdProvider.getDeviceInfo().osVersion)
                        InfoRow(label = "Estado de Vinculación", value = deviceIdentity.pairingStatus.name)
                        InfoRow(label = "Device ID", value = deviceIdentity.deviceId ?: "No asignado")
                        InfoRow(label = "Organización / Tenant", value = deviceIdentity.tenantId ?: "No asignado")

                        val env = appSettings.environment
                        val baseUrl = if (env == AppEnvironment.CUSTOM) appSettings.customRestUrl else env.defaultRestUrl
                        InfoRow(label = "URL Backend REST", value = baseUrl)

                        connectionTestResult?.let { result ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceCardElevated)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = result,
                                    color = if (result.contains("Error") || result.contains("Fallo")) StatusWarningAmber else StatusOnlineGreen,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Column 2: Interactive Remote Control Actions or Logs View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard.copy(alpha = 0.9f))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    if (logsVisible) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOGS EN MEMORIA",
                                    color = ElectricCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Card(
                                    onClick = { logsVisible = false },
                                    colors = CardDefaults.colors(containerColor = SurfaceCardElevated)
                                ) {
                                    Text("Cerrar", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(recentLogs) { log ->
                                    Text(
                                        text = log.formatted(),
                                        color = if (log.level.name == "ERROR") StatusWarningAmber else TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "ACCIONES DE INFRAESTRUCTURA (CONTROL REMOTO)",
                                color = ElectricCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            // Action 1: Probar Conexión Backend
                            TvActionCard(
                                title = if (isTestingConnection) "Probando conexión..." else "1. Probar Conexión con Backend REST",
                                description = "Realiza petición HTTP con interceptores al endpoint /api/v1/devices",
                                onClick = {
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        val res = container.testBackendConnectionUseCase()
                                        connectionTestResult = when (res) {
                                            is AppResult.Success -> "Conexión Exitosa. Latencia: ${res.data} ms"
                                            is AppResult.Error -> "Resultado: ${res.exception.message}"
                                        }
                                        isTestingConnection = false
                                    }
                                }
                            )

                            // Action 2: Conectar / Reconectar Socket.IO
                            TvActionCard(
                                title = "2. Reconectar Socket.IO Manualmente",
                                description = "Fuerza reconexión limpia y reintento de handshake en tiempo real",
                                onClick = {
                                    coroutineScope.launch {
                                        val resolved = container.preferencesDataStore.getResolvedEnvironment()
                                        container.socketManager.connect(resolved)
                                    }
                                }
                            )

                            // Action 3: Alternar Entorno (Dev <-> LAN <-> Prod)
                            TvActionCard(
                                title = "3. Alternar Entorno Backend",
                                description = "Ciclar entre LOCAL_DEV (10.0.2.2), LAN_TEST y PRODUCTION",
                                onClick = {
                                    coroutineScope.launch {
                                        val next = when (appSettings.environment) {
                                            AppEnvironment.LOCAL_DEV -> AppEnvironment.LAN_TEST
                                            AppEnvironment.LAN_TEST -> AppEnvironment.PRODUCTION
                                            AppEnvironment.PRODUCTION -> AppEnvironment.LOCAL_DEV
                                            AppEnvironment.CUSTOM -> AppEnvironment.LOCAL_DEV
                                        }
                                        container.preferencesDataStore.setEnvironment(next)
                                        connectionTestResult = "Entorno cambiado a ${next.name}"
                                    }
                                }
                            )

                            // Action 0: Volver a Pantalla Completa
                            TvActionCard(
                                title = "0. Reanudar Reproducción en Pantalla Completa",
                                description = "Regresa a la transmisión continua de publicidad digital sin detener el motor",
                                onClick = onCloseDiagnostics
                            )

                            // Action 4: Inspeccionar Logs
                            TvActionCard(
                                title = "4. Inspeccionar Logs del Sistema",
                                description = "Muestra el buffer en memoria de los últimos eventos del reproductor",
                                onClick = {
                                    recentLogs = SignageLogger.getRecentLogs()
                                    logsVisible = true
                                }
                            )

                            // Action 5: Desvincular Dispositivo (Reset to Pairing)
                            TvActionCard(
                                title = "5. Desvincular Pantalla (Volver a Vincular)",
                                description = "Elimina credenciales de forma segura y regresa a la pantalla de vinculación QR",
                                onClick = onUnpairClicked
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "D-Pad Navigation Activa | Android TV SDK 34 | Media3 Ready | Room DB WAL Mode",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "0Media Signage v1.0.0",
                    color = TextCode,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvActionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = SurfaceCardElevated,
            focusedContainerColor = BrightBlue.copy(alpha = 0.25f)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, ElectricCyan),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = TextCode,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
