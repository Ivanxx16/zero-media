package com.digitalsignage.tv.feature.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.core.designsystem.theme.BrightBlue
import com.digitalsignage.tv.core.designsystem.theme.DeepNavyBackground
import com.digitalsignage.tv.core.designsystem.theme.ElectricCyan
import com.digitalsignage.tv.core.designsystem.theme.StatusOnlineGreen
import com.digitalsignage.tv.core.designsystem.theme.StatusWarningAmber
import com.digitalsignage.tv.core.designsystem.theme.SurfaceBorder
import com.digitalsignage.tv.core.designsystem.theme.SurfaceCard
import com.digitalsignage.tv.core.designsystem.theme.SurfaceCardElevated
import com.digitalsignage.tv.core.designsystem.theme.TextCode
import com.digitalsignage.tv.core.designsystem.theme.TextMuted
import com.digitalsignage.tv.core.designsystem.theme.TextPrimary
import com.digitalsignage.tv.core.designsystem.theme.TextSecondary
import com.digitalsignage.tv.feature.player.viewmodel.PlayerUiState
import com.digitalsignage.tv.feature.player.viewmodel.PlayerViewModel
import java.io.File

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.Menu -> {
                            viewModel.toggleDiagnostics()
                            true
                        }
                        Key.Back -> {
                            onOpenDiagnostics()
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.Spacebar -> {
                            viewModel.triggerBackgroundSyncAndDownload()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                PlayerLoadingScreen()
            }
            is PlayerUiState.Standby -> {
                PlayerStandbyScreen(
                    message = state.message,
                    onOpenDiagnostics = onOpenDiagnostics,
                    onSyncNow = { viewModel.triggerBackgroundSyncAndDownload() }
                )
            }
            is PlayerUiState.Playing -> {
                ActiveMediaRenderer(
                    viewModel = viewModel,
                    state = state
                )

                // Emergency Overlay Alert Banner (if commanded by admin)
                AnimatedVisibility(
                    visible = state.overlayAlert != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    state.overlayAlert?.let { alertText ->
                        EmergencyAlertBanner(alertText = alertText)
                    }
                }

                // Hidden Technician Diagnostic HUD (toggled via D-Pad UP or Menu)
                AnimatedVisibility(
                    visible = state.isDiagnosticsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    TechnicianHudOverlay(
                        state = state,
                        onOpenDiagnostics = onOpenDiagnostics
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveMediaRenderer(
    viewModel: PlayerViewModel,
    state: PlayerUiState.Playing
) {
    val item = state.item
    val asset = item.asset ?: return
    val localFile = File(asset.localFilePath ?: "")

    val contentScale = if (item.fitMode == "CROP_CENTER") ContentScale.Crop else ContentScale.Fit
    val resizeMode = if (item.fitMode == "CROP_CENTER") {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (asset.mediaType) {
            MediaType.VIDEO -> {
                // Media3 ExoPlayer SurfaceView Container
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            setResizeMode(resizeMode)
                            player = viewModel.playerEngine.exoPlayer
                        }
                    },
                    update = { view ->
                        view.setResizeMode(resizeMode)
                        if (view.player != viewModel.playerEngine.exoPlayer) {
                            view.player = viewModel.playerEngine.exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            MediaType.IMAGE -> {
                // Coil Image Rendering with Memory Optimizations
                val context = LocalContext.current
                val imageRequest = remember(localFile.absolutePath) {
                    ImageRequest.Builder(context)
                        .data(localFile)
                        .crossfade(300)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Publicidad Digital Signage",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun PlayerStandbyScreen(
    message: String,
    onOpenDiagnostics: () -> Unit,
    onSyncNow: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(SurfaceCard, DeepNavyBackground),
                    radius = 1600f
                )
            )
            .clickable { onSyncNow() }
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(ElectricCyan, BrightBlue))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "0M",
                    color = DeepNavyBackground,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp
                )
            }

            Text(
                text = "0MEDIA DIGITAL SIGNAGE",
                color = ElectricCyan,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "Pantalla Activa • Modo Standby",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = message,
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(500.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onSyncNow,
                colors = ButtonDefaults.buttonColors(containerColor = BrightBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "🔄 Sincronizar Anuncios Ahora",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Presione [BACK] para menú técnico o haga clic para sincronizar",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PlayerLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = ElectricCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Iniciando motor de reproducción multimedia...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun EmergencyAlertBanner(alertText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFDC2626).copy(alpha = 0.95f))
            .border(2.dp, Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.White, CircleShape)
            )
            Text(
                text = "AVISO DE EMERGENCIA: $alertText",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun TechnicianHudOverlay(
    state: PlayerUiState.Playing,
    onOpenDiagnostics: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard.copy(alpha = 0.92f))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StatusOnlineGreen, CircleShape)
                )
                Text(
                    text = "HUD DE SERVICIO TÉCNICO",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Campaña: ${state.campaignName}",
                color = TextPrimary,
                fontSize = 13.sp
            )
            Text(
                text = "Item: [${state.playlistIndex + 1}/${state.totalItems}] • ID: ${state.item.assetId}",
                color = TextCode,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Formato: ${state.item.asset?.mimeType} • Modo: ${state.item.fitMode}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "Presione [BACK] para abrir menú de diagnóstico completo",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
