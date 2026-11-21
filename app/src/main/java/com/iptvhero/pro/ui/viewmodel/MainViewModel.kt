package com.iptvhero.pro.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iptvhero.pro.data.local.ChannelEntity
import com.iptvhero.pro.data.local.ProfileEntity
import com.iptvhero.pro.data.repository.BulkProgress
import com.iptvhero.pro.data.repository.IptvRepository
import com.iptvhero.pro.data.repository.SeriesEpisode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class MainUiState(
    val currentStreamUrl: String? = null,
    val currentChannelTitle: String = "",
    val isLoading: Boolean = false,
    val bulkProgress: BulkProgress? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val pendingHeroUrls: List<String>? = null,
    val pendingHeroMessage: String? = null,
    val pendingHeroHash: String? = null,
    val seasonsMap: Map<String, List<SeriesEpisode>> = emptyMap(),
    val isLoadingSeasons: Boolean = false,
    val selectedSeriesTitle: String = ""
)

class MainViewModel(
    private val repository: IptvRepository,
    context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("iptv_hero_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val profiles: Flow<List<ProfileEntity>> = repository.allProfiles

    var activeProfile by mutableStateOf<ProfileEntity?>(null)
        private set

    private val activeProfileIdFlow = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<List<ChannelEntity>> = activeProfileIdFlow.flatMapLatest { profileId ->
        if (profileId != null) repository.getChannelsByProfile(profileId) else flowOf(emptyList())
    }

    init {
        loadActiveProfile()
    }

    fun loadActiveProfile() {
        viewModelScope.launch {
            val active = repository.getActiveProfile()
            activeProfile = active
            activeProfileIdFlow.value = active?.id
        }
    }

    /**
     * Identico ao activateProfile(id) do index_2.html:
     * Ativa o perfil e busca os canais/filmes/series da fonte (Xtream ou M3U) se necessario.
     */
    fun selectProfile(profileId: Long, onReady: (() -> Unit)? = null) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = "Conectando perfil..."
            )
        }
        viewModelScope.launch {
            try {
                val count = repository.activateAndSyncProfile(profileId)
                val active = repository.getActiveProfile()
                activeProfile = active
                activeProfileIdFlow.value = active?.id
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Perfil ${active?.name ?: ""} ativado ($count itens)."
                    )
                }
                onReady?.invoke()
            } catch (e: Exception) {
                loadActiveProfile()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = null,
                        errorMessage = "Erro ao carregar perfil: ${e.message ?: "Falha"}"
                    )
                }
                onReady?.invoke()
            }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            loadActiveProfile()
        }
    }

    fun clearAllProfiles() {
        viewModelScope.launch {
            repository.clearAllProfiles()
            loadActiveProfile()
            _uiState.update { it.copy(successMessage = "Todos os perfis removidos.", errorMessage = null) }
        }
    }

    /**
     * Identico ao loadHeroList() do index_2.html:
     * Baixa o arquivo all_in_one.txt com cache-bust, calcula SHA-256 e compara com a ultima versao.
     */
    fun loadHeroListFromGithub(url: String = IptvRepository.HERO_LIST_URL, force: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = "Verificando atualizacoes da Hero Lista..."
            )
        }
        viewModelScope.launch {
            try {
                val (rawText, urls) = repository.fetchHeroListRaw(url)
                val hash = repository.sha256Hex(rawText)
                val prevHash = prefs.getString("hero_list_hash", null)

                if (!force && prevHash != null && prevHash == hash) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingHeroUrls = urls,
                            pendingHeroHash = hash,
                            pendingHeroMessage = "Hero Lista sem alteracoes (${urls.size} listas). Deseja reprocessar mesmo assim?"
                        )
                    }
                    return@launch
                }

                executeHeroBulk(urls, hash)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = null,
                        errorMessage = "Falha Hero Lista: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun confirmPendingHeroList() {
        val urls = _uiState.value.pendingHeroUrls ?: return
        val hash = _uiState.value.pendingHeroHash.orEmpty()
        _uiState.update { it.copy(pendingHeroUrls = null, pendingHeroMessage = null) }
        viewModelScope.launch {
            executeHeroBulk(urls, hash)
        }
    }

    fun dismissPendingHeroList() {
        _uiState.update { it.copy(pendingHeroUrls = null, pendingHeroMessage = null, isLoading = false) }
    }

    private suspend fun executeHeroBulk(urls: List<String>, hash: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = "Processando ${urls.size} listas da Hero Lista..."
            )
        }
        val (ok, fail) = repository.processBulkLines(urls, isHeroOrigin = true) { prog ->
            _uiState.update {
                it.copy(
                    bulkProgress = prog,
                    successMessage = "Processando ${prog.current}/${prog.total} (OK: ${prog.success} | Falhas: ${prog.failed})"
                )
            }
        }

        prefs.edit()
            .putString("hero_list_hash", hash)
            .putLong("hero_list_checked_at", System.currentTimeMillis())
            .apply()

        val active = repository.getActiveProfile()
        activeProfile = active
        activeProfileIdFlow.value = active?.id


        _uiState.update {
            it.copy(
                isLoading = false,
                bulkProgress = null,
                successMessage = "Hero Lista concluida! Criados: $ok | Falhas: $fail"
            )
        }
    }

    /**
     * Identico ao loadBulkTXT / processBulk do index_2.html:
     * Cada linha vira um perfil de heroi individual.
     */
    fun importBatchTxt(fileContent: String) {
        val lines = fileContent.split(Regex("\r?\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (lines.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nenhuma linha valida encontrada.") }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = "Processando ${lines.size} entradas..."
            )
        }
        viewModelScope.launch {
            try {
                val (ok, fail) = repository.processBulkLines(lines, isHeroOrigin = true) { prog ->
                    _uiState.update {
                        it.copy(
                            bulkProgress = prog,
                            successMessage = "Lote: ${prog.current}/${prog.total} (OK: ${prog.success})"
                        )
                    }
                }
                val active = repository.getActiveProfile()
                activeProfile = active
                activeProfileIdFlow.value = active?.id
                if (active != null) {
                    runCatching { repository.activateAndSyncProfile(active.id) }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bulkProgress = null,
                        successMessage = "Lote concluido! $ok perfis criados ($fail falhas)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bulkProgress = null,
                        errorMessage = "Erro ao importar: ${e.message ?: "Falha"}"
                    )
                }
            }
        }
    }

    fun importXtream(server: String, user: String, pass: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = "Autenticando Xtream..."
            )
        }
        viewModelScope.launch {
            try {
                val total = repository.loadXtreamData(server, user, pass)
                val active = repository.getActiveProfile()
                activeProfile = active
                activeProfileIdFlow.value = active?.id
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Xtream conectado (${active?.name})! $total itens."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = null,
                        errorMessage = "Erro Xtream: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun openSeriesSeasons(seriesChannel: ChannelEntity) {
        val profile = activeProfile ?: return
        val seriesId = seriesChannel.remoteId ?: return
        _uiState.update {
            it.copy(
                isLoadingSeasons = true,
                selectedSeriesTitle = seriesChannel.title,
                seasonsMap = emptyMap()
            )
        }
        viewModelScope.launch {
            try {
                val seasons = repository.fetchSeriesSeasons(profile, seriesId)
                _uiState.update {
                    it.copy(
                        isLoadingSeasons = false,
                        seasonsMap = seasons
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingSeasons = false,
                        errorMessage = "Erro ao carregar episodios: ${e.message}"
                    )
                }
            }
        }
    }

    fun closeSeriesSeasons() {
        _uiState.update { it.copy(seasonsMap = emptyMap(), selectedSeriesTitle = "", isLoadingSeasons = false) }
    }

    fun buildEpisodeStreamUrl(episode: SeriesEpisode): String? {
        val profile = activeProfile ?: return null
        val cleanServer = profile.server?.trimEnd('/') ?: return null
        val encUser = URLEncoder.encode(profile.user.orEmpty(), "UTF-8")
        val encPass = URLEncoder.encode(profile.pass.orEmpty(), "UTF-8")
        return "$cleanServer/series/$encUser/$encPass/${episode.id}.${episode.extension}"
    }

    fun exportProfilesText(currentProfiles: List<ProfileEntity>): String {
        return repository.formatProfilesExportTxt(currentProfiles)
    }

    fun setStreamUrl(url: String?, title: String = "") {
        _uiState.update { it.copy(currentStreamUrl = url, currentChannelTitle = title) }
    }
}

class MainViewModelFactory(
    private val repository: IptvRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
    }
}
