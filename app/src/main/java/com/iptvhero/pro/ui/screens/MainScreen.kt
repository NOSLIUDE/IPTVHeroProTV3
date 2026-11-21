package com.iptvhero.pro.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.iptvhero.pro.data.local.ChannelEntity
import com.iptvhero.pro.data.repository.SeriesEpisode
import com.iptvhero.pro.ui.components.ProfileManagerDialog
import com.iptvhero.pro.ui.components.heroFocusable
import com.iptvhero.pro.ui.theme.DcCyan
import com.iptvhero.pro.ui.theme.GoldFocus
import com.iptvhero.pro.ui.theme.HeroThemeType
import com.iptvhero.pro.ui.theme.MarvelYellow
import com.iptvhero.pro.ui.theme.TextDim
import com.iptvhero.pro.ui.theme.TextPrimary
import com.iptvhero.pro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    currentTheme: HeroThemeType = HeroThemeType.DUEL,
    onCycleTheme: () -> Unit = {},
    onBackToProfiles: () -> Unit = {},
    onNavigateToFullPlayer: () -> Unit = {},
    exitHintText: String? = null,
    onTriggerBack: () -> Boolean = { false },
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val profiles by viewModel.profiles.collectAsState(initial = emptyList())
    val channels by viewModel.channels.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()
    val activeProfile = viewModel.activeProfile

    var selectedTab by remember { mutableStateOf(uiState.lastSelectedTab) }
    var selectedCategory by remember { mutableStateOf(uiState.lastSelectedCategory) }
    val categoryHistory = remember { mutableStateListOf<String>() }
    val focusHistoryByKey = remember { mutableStateMapOf<String, Int>() }
    var searchQuery by remember { mutableStateOf(uiState.lastSearchQuery) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var isPlayerActive by remember { mutableStateOf(!uiState.currentStreamUrl.isNullOrBlank()) }
    var selectedSeasonKey by remember { mutableStateOf("") }
    var hasRestoredInitialHistory by remember { mutableStateOf(false) }
    val lastFocusedItemRequester = remember { FocusRequester() }

    val gridState = rememberLazyGridState()

    val livePlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            livePlayer.release()
        }
    }

    LaunchedEffect(uiState.seasonsMap) {
        if (uiState.seasonsMap.isNotEmpty() && !uiState.seasonsMap.containsKey(selectedSeasonKey)) {
            selectedSeasonKey = uiState.seasonsMap.keys.first()
        }
    }

    LaunchedEffect(channels.size, uiState.lastSelectedTab, uiState.lastSelectedCategory, uiState.lastFocusedIndex) {
        if (!hasRestoredInitialHistory && channels.isNotEmpty()) {
            val restTab = uiState.lastSelectedTab.ifBlank { "CANAIS" }
            val restCat = uiState.lastSelectedCategory
            val restSearch = uiState.lastSearchQuery
            val restIndex = uiState.lastFocusedIndex.coerceAtLeast(0)
            selectedTab = restTab
            selectedCategory = restCat
            searchQuery = restSearch
            focusHistoryByKey["$restTab|$restCat"] = restIndex
            gridState.scrollToItem(restIndex)
            runCatching { lastFocusedItemRequester.requestFocus() }
            hasRestoredInitialHistory = true
        }
    }

    fun currentScopeKey(): String = "$selectedTab|$selectedCategory"

    fun restoreScrollForScope(scopeKey: String) {
        val savedIndex = focusHistoryByKey[scopeKey] ?: 0
        coroutineScope.launch {
            gridState.scrollToItem(savedIndex.coerceAtLeast(0))
        }
    }

    fun selectCategoryTracked(newCategory: String) {
        focusHistoryByKey[currentScopeKey()] = gridState.firstVisibleItemIndex
        if (newCategory != selectedCategory) {
            categoryHistory.add(selectedCategory)
            selectedCategory = newCategory
        }
        restoreScrollForScope("$selectedTab|$newCategory")
    }

    fun stopPlayback() {
        livePlayer.stop()
        livePlayer.clearMediaItems()
        isPlayerActive = false
        viewModel.setStreamUrl(null, "")
    }

    fun startStreamPlayback(url: String, title: String) {
        if (url.isBlank()) return
        viewModel.setStreamUrl(url, title)
        isPlayerActive = true
        livePlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url.trim())))
        livePlayer.prepare()
        livePlayer.playWhenReady = true
    }

    val tabs = listOf("CANAIS", "FILMES", "SERIES")
    val tabChannels = remember(channels, selectedTab) {
        when (selectedTab) {
            "FILMES" -> channels.filter { it.contentType.equals("VOD", ignoreCase = true) }
            "SERIES" -> channels.filter { it.contentType.equals("SERIES", ignoreCase = true) }
            else -> channels.filter { it.contentType.equals("LIVE", ignoreCase = true) }
        }
    }

    fun handleItemClick(channel: ChannelEntity, index: Int) {
        focusHistoryByKey[currentScopeKey()] = index
        viewModel.saveNavigationHistory(
            tab = selectedTab,
            category = selectedCategory,
            search = searchQuery,
            focusedIndex = index,
            openedTitle = channel.title
        )
        val isSeries = selectedTab == "SERIES" || channel.contentType.equals("SERIES", ignoreCase = true)
        if (isSeries) {
            selectedSeasonKey = ""
            viewModel.openSeriesSeasons(channel, tabChannels)
        } else {
            startStreamPlayback(channel.url, channel.title)
        }
    }

    fun playEpisode(episode: SeriesEpisode) {
        val epUrl = viewModel.buildEpisodeStreamUrl(episode) ?: return
        val fullTitle = "${uiState.selectedSeriesTitle} • T${selectedSeasonKey}E${episode.episodeNum} - ${episode.title}"
        viewModel.closeSeriesSeasons()
        startStreamPlayback(epUrl, fullTitle)
    }

    val availableCategories = remember(tabChannels) {
        tabChannels.groupingBy { it.groupName.ifBlank { "Geral" } }.eachCount().toSortedMap()
    }

    val filteredChannels = remember(tabChannels, selectedCategory, searchQuery) {
        val byCat = if (selectedCategory.isBlank()) tabChannels
        else tabChannels.filter { it.groupName.equals(selectedCategory, ignoreCase = true) }
        if (searchQuery.isBlank()) byCat
        else byCat.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val isPosterTab = selectedTab == "FILMES" || selectedTab == "SERIES"
    val gridColumnsCount = if (isPosterTab) 5 else 4
    val totalItems = filteredChannels.size
    val firstVisible = gridState.firstVisibleItemIndex
    val scrollFraction = if (totalItems > gridColumnsCount) {
        (firstVisible.toFloat() / totalItems.toFloat()).coerceIn(0f, 0.75f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.backgroundBrush)
            .padding(12.dp)
    ) {
        // ZONA 1: TOPO (Título + Perfis, Proxy, Tema)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(currentTheme.headerBrush)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "IPTV HERO PRO TV • V6/7",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                Text(
                    text = if (uiState.currentChannelTitle.isNotBlank()) {
                        "Reproduzindo: ${uiState.currentChannelTitle}"
                    } else {
                        activeProfile?.let { "Perfil: ${it.name} • ${channels.size} itens" }
                            ?: "Sem perfil ativo"
                    },
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x99090B14))
                        .heroFocusable(RoundedCornerShape(10.dp)) { onBackToProfiles() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("Perfis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x99090B14))
                        .heroFocusable(RoundedCornerShape(10.dp)) { showProfileDialog = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("Proxy", color = DcCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x99090B14))
                        .heroFocusable(RoundedCornerShape(10.dp)) { onCycleTheme() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("Tema", color = MarvelYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ZONA 2: ABAS (Canais, Filmes, Séries) + Barra de Pesquisa com fonte ajustada sem cortes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE60E1220))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                val labelText = if (tab == "SERIES") "Séries" else tab.lowercase().replaceFirstChar { it.uppercase() }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) GoldFocus.copy(alpha = 0.22f) else Color(0xFF151C2E))
                        .heroFocusable(RoundedCornerShape(8.dp)) {
                            focusHistoryByKey[currentScopeKey()] = gridState.firstVisibleItemIndex
                            selectedTab = tab
                            selectedCategory = ""
                            categoryHistory.clear()
                            searchQuery = ""
                            val targetIdx = focusHistoryByKey["$tab|"] ?: 0
                            restoreScrollForScope("$tab|")
                            viewModel.saveNavigationHistory(
                                tab = tab,
                                category = "",
                                search = "",
                                focusedIndex = targetIdx
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = labelText,
                        color = if (isSelected) GoldFocus else TextDim,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Campo de busca com altura livre e fonte menor (11.5.sp) para nunca cortar letras ao meio
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF070912))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp
                    ),
                    cursorBrush = SolidColor(GoldFocus),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Pesquisar canal, filme ou série...",
                                    color = TextDim,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ZONA 3: CORPO PRINCIPAL (Categorias à Esquerda + Grade de Covers + Barra de Rolagem à Direita)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Barra Lateral de Categorias (D-Pad)
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE60D111E))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CATEGORIAS (D-PAD)",
                    color = DcCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedCategory.isBlank()) GoldFocus.copy(alpha = 0.20f) else Color(0xFF141B2E))
                                .heroFocusable(RoundedCornerShape(8.dp)) {
                                    selectCategoryTracked("")
                                    viewModel.saveNavigationHistory(
                                        tab = selectedTab,
                                        category = "",
                                        search = searchQuery,
                                        focusedIndex = focusHistoryByKey["$selectedTab|"] ?: 0
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Todas",
                                color = if (selectedCategory.isBlank()) GoldFocus else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("${tabChannels.size}", color = GoldFocus, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    items(availableCategories.entries.toList()) { (catName, count) ->
                        val isCatSelected = selectedCategory.equals(catName, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCatSelected) GoldFocus.copy(alpha = 0.20f) else Color(0xFF141B2E))
                                .heroFocusable(RoundedCornerShape(8.dp)) {
                                    selectCategoryTracked(catName)
                                    viewModel.saveNavigationHistory(
                                        tab = selectedTab,
                                        category = catName,
                                        search = searchQuery,
                                        focusedIndex = focusHistoryByKey["$selectedTab|$catName"] ?: 0
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = catName,
                                color = if (isCatSelected) GoldFocus else TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$count", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = exitHintText ?: "Histórico D-Pad ativo • 3x Voltar sai",
                    color = if (exitHintText != null) MarvelYellow else TextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Área Direita: Grade de Conteúdos (Covers) + Barra de Rolagem Lateral
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE60D111E))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPlayerActive && !uiState.currentStreamUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(108.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF131A2C))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = livePlayer
                                        useController = false
                                    }
                                },
                                update = { it.player = livePlayer },
                                modifier = Modifier
                                    .width(176.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "REPRODUZINDO AGORA",
                                    color = DcCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = uiState.currentChannelTitle,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Use STOP no rodapé para interromper ou FULL para tela cheia",
                                    color = TextDim,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.errorMessage
                                    ?: if (uiState.isLoading) (uiState.successMessage ?: "Carregando lista...")
                                    else "Nenhum conteúdo encontrado.",
                                color = if (uiState.errorMessage != null) MarvelYellow else TextDim,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(gridColumnsCount),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(filteredChannels, key = { _, ch -> ch.id }) { index, channel ->
                                val isSelectedItem = uiState.currentChannelTitle == channel.title && isPlayerActive
                                val isSeriesCard = selectedTab == "SERIES" || channel.contentType.equals("SERIES", ignoreCase = true)
                                val isVodOrSeries = isPosterTab || isSeriesCard

                                val isLastAccessed = index == uiState.lastFocusedIndex ||
                                    channel.title.equals(uiState.lastOpenedChannelTitle, ignoreCase = true)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isSelectedItem -> Color(0xFF19233E)
                                                isLastAccessed -> Color(0xFF172038)
                                                else -> Color(0xFF13192B)
                                            }
                                        )
                                        .then(
                                            if (index == uiState.lastFocusedIndex) {
                                                Modifier.focusRequester(lastFocusedItemRequester)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .heroFocusable(RoundedCornerShape(12.dp)) {
                                            handleItemClick(channel, index)
                                        }
                                        .padding(8.dp)
                                ) {
                                    // Proporção ajustada: 2:3 (pôster) para Filmes/Séries e 16:9 para Canais, com ContentScale.Fit para não cortar imagem
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(if (isVodOrSeries) 0.72f else 1.65f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0A0F1D)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!channel.streamIcon.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.title,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(if (isVodOrSeries) 2.dp else 6.dp)
                                            )
                                        } else {
                                            Text(
                                                text = channel.title.take(2).uppercase(),
                                                color = if (isSelectedItem) MarvelYellow else DcCyan,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        if (isSeriesCard) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Color(0xCC0476F2))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SÉRIE",
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = channel.title,
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = if (isSeriesCard) "OK: Temporadas e Episódios" else channel.groupName.ifBlank { selectedTab },
                                        color = if (isSelectedItem || isSeriesCard) DcCyan else TextDim,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Barra de rolagem vertical à direita (sem padding dinâmico de dp que possa estourar restrições de altura)
                BoxWithConstraints(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFF1E263A))
                ) {
                    val thumbHeight = maxHeight * 0.25f
                    val maxTravel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
                    val offsetY = maxTravel * scrollFraction
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY)
                            .fillMaxWidth()
                            .height(thumbHeight)
                            .clip(CircleShape)
                            .background(DcCyan)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ZONA 4: RODAPÉ (HERO LISTA, Atualizar, STOP, Conexões Ativas, FULL)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE60E1220))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE62429))
                    .heroFocusable(RoundedCornerShape(9.dp)) { viewModel.loadHeroListFromGithub() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("HERO LISTA", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF1A2238))
                    .heroFocusable(RoundedCornerShape(9.dp)) {
                        activeProfile?.id?.let { viewModel.selectProfile(it) }
                            ?: viewModel.loadHeroListFromGithub(force = true)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Atualizar", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF3B1219))
                    .heroFocusable(RoundedCornerShape(9.dp)) { stopPlayback() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("STOP", color = Color(0xFFFF6B6E), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF090D16))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                val activeConn = activeProfile?.activeConnections ?: if (isPlayerActive) 1 else 0
                val maxConn = activeProfile?.maxConnections?.toString() ?: "∞"
                Text(
                    text = "Conexões Ativas: $activeConn / $maxConn",
                    color = DcCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF0476F2))
                    .heroFocusable(RoundedCornerShape(9.dp)) {
                        livePlayer.playWhenReady = false
                        onNavigateToFullPlayer()
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text("FULL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
        }
    }

    // MODAL DE SELEÇÃO DE TEMPORADAS E EPISÓDIOS DA SÉRIE (D-PAD)
    if (uiState.selectedSeriesTitle.isNotBlank()) {
        Dialog(
            onDismissRequest = { viewModel.closeSeriesSeasons() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF50B0F1C))
                    .border(1.5.dp, GoldFocus, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SÉRIE SELECIONADA",
                            color = DcCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = uiState.selectedSeriesTitle,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF3B1219))
                            .heroFocusable(RoundedCornerShape(9.dp)) { viewModel.closeSeriesSeasons() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("FECHAR", color = Color(0xFFFF6B6E), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                if (uiState.isLoadingSeasons) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = GoldFocus)
                            Text("Carregando temporadas e episódios...", color = TextDim, fontSize = 13.sp)
                        }
                    }
                } else if (uiState.seasonsMap.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Nenhuma temporada ou episódio encontrado para esta série.",
                            color = MarvelYellow,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    val seasonKeys = uiState.seasonsMap.keys.toList()
                    val activeSeason = if (selectedSeasonKey in seasonKeys) selectedSeasonKey else seasonKeys.first()
                    val episodes = uiState.seasonsMap[activeSeason].orEmpty()

                    // Seletor horizontal de Temporadas
                    Text(
                        text = "TEMPORADAS (${seasonKeys.size})",
                        color = GoldFocus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasonKeys) { sKey ->
                            val isSeasonSelected = sKey == activeSeason
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSeasonSelected) GoldFocus.copy(alpha = 0.25f) else Color(0xFF151C2E))
                                    .heroFocusable(RoundedCornerShape(9.dp)) { selectedSeasonKey = sKey }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "Temporada $sKey",
                                    color = if (isSeasonSelected) GoldFocus else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Lista de Episódios da Temporada selecionada
                    Text(
                        text = "EPISÓDIOS • TEMPORADA $activeSeason (${episodes.size})",
                        color = DcCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(episodes, key = { "${activeSeason}_${it.id}_${it.episodeNum}" }) { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141B2E))
                                    .heroFocusable(RoundedCornerShape(10.dp)) { playEpisode(ep) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E2944))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "EP ${ep.episodeNum.toString().padStart(2, '0')}",
                                            color = GoldFocus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Text(
                                        text = ep.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!ep.duration.isNullOrBlank()) {
                                        Text(
                                            text = ep.duration,
                                            color = TextDim,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = "ASSISTIR ▶",
                                        color = DcCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileManagerDialog(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            onDismiss = { showProfileDialog = false },
            onActivateProfile = { viewModel.selectProfile(it) },
            onDeleteProfile = { viewModel.deleteProfile(it) },
            onImportContent = { txtContent ->
                viewModel.importBatchTxt(txtContent)
                showProfileDialog = false
            },
            onImportGithubHero = { url ->
                viewModel.loadHeroListFromGithub(url)
                showProfileDialog = false
            },
            onImportXtream = { server, user, pass ->
                viewModel.importXtream(server, user, pass)
                showProfileDialog = false
            }
        )
    }

    if (uiState.pendingHeroMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingHeroList() },
            title = { Text("Atualização de Lista") },
            text = { Text(uiState.pendingHeroMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingHeroList() }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingHeroList() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
