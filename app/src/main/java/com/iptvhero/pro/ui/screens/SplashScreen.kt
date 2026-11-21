package com.iptvhero.pro.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptvhero.pro.ui.theme.DcBlue
import com.iptvhero.pro.ui.theme.GoldFocus
import com.iptvhero.pro.ui.theme.MarvelRed
import com.iptvhero.pro.ui.theme.MarvelYellow
import com.iptvhero.pro.ui.theme.TextDim
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1025), Color(0xFF05050B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Canvas(modifier = Modifier.size(88.dp).scale(pulse)) {
                val w = size.width
                val h = size.height
                val shieldPath = Path().apply {
                    moveTo(w * 0.5f, h * 0.04f)
                    lineTo(w * 0.92f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.74f)
                    lineTo(w * 0.5f, h * 0.96f)
                    lineTo(w * 0.16f, h * 0.74f)
                    lineTo(w * 0.08f, h * 0.24f)
                    close()
                }
                drawPath(shieldPath, color = Color(0xD90A0A16))
                drawPath(
                    shieldPath,
                    brush = Brush.linearGradient(listOf(MarvelRed, MarvelYellow, DcBlue)),
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            Text(
                text = "IPTV HERO PRO",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    brush = Brush.horizontalGradient(listOf(MarvelYellow, MarvelRed, DcBlue))
                )
            )

            Text(
                text = "MULTIVERSE • LIVE • VOD",
                color = TextDim,
                fontSize = 12.sp,
                letterSpacing = 5.sp
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .width(260.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = GoldFocus,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}
