package com.digitalsignage.tv.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalsignage.tv.core.designsystem.theme.StatusErrorRed
import com.digitalsignage.tv.core.designsystem.theme.StatusOnlineGreen
import com.digitalsignage.tv.core.designsystem.theme.StatusWarningAmber
import com.digitalsignage.tv.core.designsystem.theme.SurfaceBorder
import com.digitalsignage.tv.core.designsystem.theme.SurfaceCard
import com.digitalsignage.tv.core.designsystem.theme.TextPrimary
import com.digitalsignage.tv.core.network.socket.SocketConnectionState

@Composable
fun StatusBadge(
    label: String,
    isActive: Boolean,
    activeColor: Color = StatusOnlineGreen,
    inactiveColor: Color = StatusErrorRed,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = if (isActive) activeColor else inactiveColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.85f))
            .border(1.dp, SurfaceBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(if (isActive) alphaAnim else 1f)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SocketStatusBadge(
    state: SocketConnectionState,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (state) {
        SocketConnectionState.CONNECTED, SocketConnectionState.AUTHENTICATED -> "WS: Conectado" to StatusOnlineGreen
        SocketConnectionState.CONNECTING -> "WS: Conectando..." to StatusWarningAmber
        SocketConnectionState.DISCONNECTED -> "WS: Desconectado" to StatusErrorRed
        SocketConnectionState.ERROR -> "WS: Error" to StatusErrorRed
    }

    StatusBadge(
        label = label,
        isActive = state == SocketConnectionState.CONNECTED || state == SocketConnectionState.AUTHENTICATED,
        activeColor = color,
        inactiveColor = color,
        modifier = modifier
    )
}
