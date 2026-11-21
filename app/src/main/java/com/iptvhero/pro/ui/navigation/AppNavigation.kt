package com.iptvhero.pro.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iptvhero.pro.ui.screens.MainScreen
import com.iptvhero.pro.ui.screens.PlayerScreen
import com.iptvhero.pro.ui.screens.ProfilesScreen
import com.iptvhero.pro.ui.screens.SplashScreen
import com.iptvhero.pro.ui.theme.HeroThemeType
import com.iptvhero.pro.ui.viewmodel.MainViewModel

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    onExitApp: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("splash") }
    var currentTheme by remember { mutableStateOf(HeroThemeType.DUEL) }
    val navigationHistory = remember { mutableStateListOf<String>() }

    var backPressCount by remember { mutableStateOf(0) }
    var lastBackTimestamp by remember { mutableLongStateOf(0L) }
    var exitHintText by remember { mutableStateOf<String?>(null) }

    val profiles by viewModel.profiles.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    fun navigateTo(targetScreen: String) {
        if (targetScreen != currentScreen) {
            navigationHistory.add(currentScreen)
            currentScreen = targetScreen
            backPressCount = 0
            exitHintText = null
        }
    }

    fun handleBackAction(): Boolean {
        if (currentScreen == "player") {
            currentScreen = "main"
            backPressCount = 0
            exitHintText = null
            return true
        }

        if (navigationHistory.isNotEmpty()) {
            val previous = navigationHistory.removeAt(navigationHistory.lastIndex)
            if (previous != "splash") {
                currentScreen = previous
                backPressCount = 0
                exitHintText = null
                return true
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastBackTimestamp > 2500L) {
            backPressCount = 0
        }
        lastBackTimestamp = now
        backPressCount += 1

        if (backPressCount >= 3) {
            onExitApp()
            return true
        }

        val remaining = 3 - backPressCount
        exitHintText = "Pressione Voltar mais $remaining vez(es) para sair do aplicativo"
        return true
    }

    BackHandler(enabled = currentScreen != "splash") {
        handleBackAction()
    }

    fun cycleTheme() {
        val values = HeroThemeType.entries.toTypedArray()
        currentTheme = values[(currentTheme.ordinal + 1) % values.size]
    }

    val statusText = uiState.errorMessage ?: uiState.successMessage

    when (currentScreen) {
        "splash" -> {
            SplashScreen(onFinished = { currentScreen = "profiles" })
        }
        "profiles" -> {
            ProfilesScreen(
                profiles = profiles,
                activeProfileId = viewModel.activeProfile?.id,
                statusMessage = statusText,
                pendingHeroMessage = uiState.pendingHeroMessage,
                currentTheme = currentTheme,
                onCycleTheme = ::cycleTheme,
                onOpenImportTxt = { navigateTo("main") },
                onLoadHeroGithub = {
                    viewModel.loadHeroListFromGithub()
                },
                onConfirmPendingHero = {
                    viewModel.confirmPendingHeroList()
                },
                onDismissPendingHero = {
                    viewModel.dismissPendingHeroList()
                },
                onSelectProfile = { profileId ->
                    viewModel.selectProfile(profileId)
                },
                onDeleteProfile = { profileId ->
                    viewModel.deleteProfile(profileId)
                },
                onClearAllProfiles = { viewModel.clearAllProfiles() },
                onEnterApp = { navigateTo("main") }
            )
        }
        "main" -> {
            MainScreen(
                viewModel = viewModel,
                currentTheme = currentTheme,
                onCycleTheme = ::cycleTheme,
                onBackToProfiles = { navigateTo("profiles") },
                onNavigateToFullPlayer = { navigateTo("player") },
                exitHintText = exitHintText,
                onTriggerBack = { handleBackAction() },
                onExitApp = onExitApp
            )
        }
        "player" -> {
            PlayerScreen(
                streamUrl = uiState.currentStreamUrl,
                channelTitle = uiState.currentChannelTitle,
                onBack = {
                    viewModel.setStreamUrl(null, "")
                    currentScreen = "main"
                }
            )
        }
    }
}
