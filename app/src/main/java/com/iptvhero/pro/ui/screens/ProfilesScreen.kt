package com.iptvhero.pro.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptvhero.pro.data.local.ProfileEntity
import com.iptvhero.pro.ui.components.heroFocusable
import com.iptvhero.pro.ui.theme.DcBlue
import com.iptvhero.pro.ui.theme.DcCyan
import com.iptvhero.pro.ui.theme.GoldFocus
import com.iptvhero.pro.ui.theme.HeroThemeType
import com.iptvhero.pro.ui.theme.MarvelRed
import com.iptvhero.pro.ui.theme.MarvelYellow
import com.iptvhero.pro.ui.theme.TextDim
import com.iptvhero.pro.ui.theme.TextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ProfilesScreen(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    statusMessage: String?,
    pendingHeroMessage: String? = null,
    currentTheme: HeroThemeType,
    onCycleTheme: () -> Unit,
    onOpenImportTxt: () -> Unit,
    onLoadHeroGithub: () -> Unit,
    onConfirmPendingHero: () -> Unit = {},
    onDismissPendingHero: () -> Unit = {},
    onSelectProfile: (Long) -> Unit,
    onDeleteProfile: (Long) -> Unit = {},
    onClearAllProfiles: () -> Unit,
    onEnterApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.backgroundBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x8C07070F))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "IPTV HERO PRO",
                color = MarvelYellow,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            HeroActionButton("TEMA", isPrimary = false, onClick = onCycleTheme)
            HeroActionButton("HERO LISTA", isPrimary = false, onClick = onLoadHeroGithub)
            HeroActionButton("XTREAM / TXT", isPrimary = false, onClick = onOpenImportTxt)
            HeroActionButton("ENTRAR", isPrimary = true, onClick = onEnterApp)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusMessage ?: "Nenhum perfil ainda.\nClique em HERO LISTA ou XTREAM / TXT para adicionar listas.",
                    color = TextDim,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.weight(1f).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == activeProfileId
                    val isMarvel = profile.universe.equals("marvel", ignoreCase = true)
                    val validityInfo = formatValidity(profile.validityIso)

                    Box(
                        modifier = Modifier
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    if (isMarvel) listOf(Color(0xE0260A12), Color(0xF00E0A12))
                                    else listOf(Color(0xE00A1628), Color(0xF00A0E18))
                                )
                            )
                            .heroFocusable(RoundedCornerShape(16.dp)) {
                                onSelectProfile(profile.id)
                                onEnterApp()
                            }
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(DcBlue, DcCyan))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (isMarvel) "M" else "DC", color = Color.White, fontWeight = FontWeight.Black)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isActive) {
                                        Text(
                                            text = "ATIVO",
                                            color = GoldFocus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MarvelRed.copy(alpha = 0.85f))
                                            .heroFocusable(CircleShape) { onDeleteProfile(profile.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("X", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = profile.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${profile.sourceType.uppercase()}${if (profile.bulkOrigin) " • LOTE" else ""}",
                                    color = TextDim,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TV ${profile.liveCount}", color = MarvelYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("VOD ${profile.vodCount}", color = DcCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("SER ${profile.seriesCount}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "CON ${profile.activeConnections}/${profile.maxConnections ?: "∞"}",
                                        color = TextDim,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = validityInfo.first,
                                    color = validityInfo.second,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xA607070F))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroActionButton("CARREGAR HERO LISTA", isPrimary = true, onClick = onLoadHeroGithub)
            HeroActionButton("IMPORTAR LOTE TXT", isPrimary = false, onClick = onOpenImportTxt)
            HeroActionButton("LIMPAR TUDO", isPrimary = false, onClick = onClearAllProfiles)
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    color = GoldFocus,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (!pendingHeroMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onDismissPendingHero,
            title = { Text("HERO LISTA") },
            text = { Text(pendingHeroMessage) },
            confirmButton = {
                Button(onClick = onConfirmPendingHero) {
                    Text("Reprocessar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPendingHero) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun formatValidity(validityIso: String?): Pair<String, Color> {
    if (validityIso.isNullOrBlank()) return Pair("Sem validade", TextDim)
    return try {
        val exp = Instant.parse(validityIso)
        val now = Instant.now()
        val days = ChronoUnit.DAYS.between(now, exp)
        val formatted = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
            .format(exp)
        when {
            days < 0 -> Pair("Expirado ($formatted)", Color(0xFFFF6B6E))
            days <= 7 -> Pair("${days}d restantes ($formatted)", Color(0xFFFFB347))
            else -> Pair("Válido até $formatted", Color(0xFF4ADE80))
        }
    } catch (_: Exception) {
        Pair("Sem validade", TextDim)
    }
}

@Composable
private fun HeroActionButton(text: String, isPrimary: Boolean, onClick: () -> Unit) {
    val bgBrush = if (isPrimary) {
        Brush.horizontalGradient(listOf(MarvelRed, DcBlue))
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgBrush)
            .heroFocusable(RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
