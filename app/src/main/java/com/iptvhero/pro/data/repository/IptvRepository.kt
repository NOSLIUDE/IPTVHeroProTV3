package com.iptvhero.pro.data.repository

import com.iptvhero.pro.data.local.ChannelDao
import com.iptvhero.pro.data.local.ChannelEntity
import com.iptvhero.pro.data.local.ProfileDao
import com.iptvhero.pro.data.local.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class PlaylistItem(
    val title: String,
    val url: String,
    val groupName: String = "Geral",
    val contentType: String = "LIVE",
    val streamIcon: String? = null,
    val remoteId: String? = null
)

data class XtreamCredentials(
    val server: String,
    val username: String,
    val password: String
)

data class HeroIdentity(
    val name: String,
    val universe: String
)

data class BulkProgress(
    val current: Int,
    val total: Int,
    val success: Int,
    val failed: Int,
    val lastLog: String
)

data class SeriesEpisode(
    val id: String,
    val episodeNum: Int,
    val title: String,
    val extension: String,
    val duration: String?
)

object HeroGenerator {
    private val MARVEL_HEROES = listOf(
        "Homem de Ferro", "Capitão América", "Thor", "Hulk", "Viúva Negra",
        "Homem-Aranha", "Doutor Estranho", "Pantera Negra", "Capitã Marvel",
        "Feiticeira Escarlate", "Visão", "Falcão", "Soldado Invernal",
        "Gavião Arqueiro", "Star-Lord", "Gamora", "Rocket", "Groot",
        "Drax", "Mantis", "Shang-Chi", "Ms. Marvel", "Cavaleiro da Lua",
        "Mulher-Hulk", "Loki", "Kate Bishop", "Yelena", "Sam Wilson"
    )

    private val DC_HEROES = listOf(
        "Batman", "Superman", "Mulher-Maravilha", "Flash", "Aquaman",
        "Ciborgue", "Lanterna Verde", "Arqueiro Verde", "Canário Negro",
        "Supergirl", "Batwoman", "Shazam", "Adão Negro", "Besouro Azul",
        "Zatanna", "Constantine", "Asa Noturna", "Estelar", "Mutano",
        "Ravena", "Mulher-Gato", "Arlequina", "Pacificador", "Caçador de Marte"
    )

    fun generateHeroName(): HeroIdentity {
        val isMarvel = Random.nextBoolean()
        val universe = if (isMarvel) "marvel" else "dc"
        val list = if (isMarvel) MARVEL_HEROES else DC_HEROES
        val base = list[Random.nextInt(list.size)]
        val suffix = Random.nextInt(1000, 10000)
        return HeroIdentity(name = "${base}_$suffix", universe = universe)
    }
}

object PlaylistParser {

    /**
     * Identico ao parseM3U(text) do index_2.html
     */
    fun parseM3U(text: String): List<PlaylistItem> {
        val lines = text.split(Regex("\r?\n"))
        val channels = mutableListOf<PlaylistItem>()
        var pendingName: String? = null
        var pendingGroup = "Geral"
        var pendingLogo: String? = null
        var hasPendingInfo = false

        val nameRegex = Regex(",(.*)$")
        val groupRegex = Regex("""group-title="([^"]*)"""")
        val logoRegex = Regex("""tvg-logo="([^"]*)"""")

        for (raw in lines) {
            val l = raw.trim().removePrefix("\uFEFF")
            if (l.isEmpty()) continue

            if (l.startsWith("#EXTINF:")) {
                val nMatch = nameRegex.find(l)
                val gMatch = groupRegex.find(l)
                val logoMatch = logoRegex.find(l)

                pendingName = nMatch?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { "Canal" } ?: "Canal"
                pendingGroup = gMatch?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { "Geral" } ?: "Geral"
                pendingLogo = logoMatch?.groupValues?.getOrNull(1)?.trim()?.ifEmpty { null }
                hasPendingInfo = true
            } else if (!l.startsWith("#") && hasPendingInfo) {
                channels.add(
                    PlaylistItem(
                        title = pendingName ?: "Canal",
                        url = l,
                        groupName = pendingGroup,
                        contentType = "LIVE",
                        streamIcon = pendingLogo
                    )
                )
                hasPendingInfo = false
                pendingName = null
                pendingGroup = "Geral"
                pendingLogo = null
            }
        }
        return channels
    }

    /**
     * Identico ao parseTXT(text) do index_2.html
     */
    fun parseTXT(text: String): List<PlaylistItem> {
        val lines = text.split(Regex("\r?\n"))
        val channels = mutableListOf<PlaylistItem>()
        for (raw in lines) {
            val l = raw.trim().removePrefix("\uFEFF")
            if (l.isEmpty() || l.startsWith("#")) continue
            val inline = parseInlineChannel(l)
            if (inline != null) {
                channels.add(
                    PlaylistItem(
                        title = inline.first,
                        url = inline.second,
                        groupName = "TXT",
                        contentType = "LIVE"
                    )
                )
            }
        }
        return channels
    }

    /**
     * Identico ao parseInlineChannel(line) do index_2.html:
     * Retorna Pair(name, url)
     */
    fun parseInlineChannel(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.contains(",")) {
            val parts = trimmed.split(",")
            val p0 = parts.getOrNull(0)?.trim().orEmpty()
            val p1 = parts.getOrNull(1)?.trim().orEmpty()
            if (p0.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
                return Pair(p1.ifEmpty { "Canal" }, p0)
            }
            if (p1.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
                return Pair(p0.ifEmpty { "Canal" }, p1)
            }
        } else if (trimmed.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
            return Pair("Canal", trimmed)
        }
        return null
    }

    /**
     * Identico ao detectXtreamPure(text) do index_2.html
     */
    fun detectXtreamPure(text: String): XtreamCredentials? {
        val patterns = listOf(
            Regex("""https?://[^\s]+/get\.php\?[^\s]*username=([^&\s]+)[^\s]*password=([^&\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s]+/player_api\.php\?[^\s]*username=([^&\s]+)[^\s]*password=([^&\s]+)""", RegexOption.IGNORE_CASE)
        )
        val hostRegex = Regex("""(https?://[^/\s]+)""", RegexOption.IGNORE_CASE)

        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val h = hostRegex.find(text)
                if (h != null) {
                    val server = h.groupValues[1].trimEnd('/')
                    val user = m.groupValues[1].trim()
                    val pass = m.groupValues[2].trim()
                    if (user.isNotEmpty() && pass.isNotEmpty()) {
                        return XtreamCredentials(server = server, username = user, password = pass)
                    }
                }
            }
        }
        return null
    }

    /**
     * Identico ao parseHeroUrls(text) do index_2.html:
     * Cada linha nao comentada com '#' pode ser "URL | Meta" ou URL pura.
     */
    fun parseHeroUrls(text: String): List<String> {
        return text.split(Regex("\r?\n"))
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { l ->
                val idx = l.indexOf(" | ")
                if (idx > -1) l.substring(0, idx).trim() else l
            }
            .filter { it.startsWith("http", ignoreCase = true) }
    }
}

class IptvRepository(
    private val profileDao: ProfileDao,
    private val channelDao: ChannelDao,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        const val HERO_LIST_URL =
            "https://raw.githubusercontent.com/NOSLIUDE/Satsil/refs/heads/main/all_in_one.txt"
    }

    val allProfiles: Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    fun getChannelsByProfile(profileId: Long): Flow<List<ChannelEntity>> =
        channelDao.getChannelsByProfile(profileId)

    suspend fun getActiveProfile(): ProfileEntity? = profileDao.getActiveProfile()

    suspend fun setActiveProfile(profileId: Long) {
        profileDao.setActiveProfile(profileId)
    }

    suspend fun deleteProfile(profileId: Long) {
        profileDao.deleteProfileById(profileId)
    }

    suspend fun clearAllProfiles() {
        profileDao.deleteAllProfiles()
    }

    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Baixa o arquivo remoto com cache-bust (?t=timestamp) igual ao heroUrlWithBust() do index_2.html
     */
    suspend fun fetchHeroListRaw(url: String = HERO_LIST_URL): Pair<String, List<String>> =
        withContext(Dispatchers.IO) {
            val clean = url.trim()
            val sep = if (clean.contains("?")) "&" else "?"
            val bustedUrl = "$clean${sep}t=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(bustedUrl)
                .header("Cache-Control", "no-cache, no-store")
                .build()

            val text = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            if (text.isBlank()) throw IllegalStateException("Lista vazia no servidor.")
            val urls = PlaylistParser.parseHeroUrls(text)
            if (urls.isEmpty()) throw IllegalStateException("Nenhuma lista valida encontrada.")
            Pair(text, urls)
        }

    /**
     * Processa um lote de linhas (igual ao processBulk / processOneBulkLine do index_2.html),
     * criando 1 perfil por linha valida com nome de heroi Marvel/DC, validade, conexoes e contagens.
     */
    suspend fun processBulkLines(
        rawLines: List<String>,
        isHeroOrigin: Boolean = true,
        onProgress: ((BulkProgress) -> Unit)? = null
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val lines = rawLines
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { l ->
                val idx = l.indexOf(" | ")
                if (idx > -1 && l.substring(0, idx).trim().startsWith("http", ignoreCase = true)) {
                    l.substring(0, idx).trim()
                } else {
                    l
                }
            }
            .take(100_000)

        val total = lines.size
        if (total == 0) return@withContext Pair(0, 0)

        val successCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val semaphore = Semaphore(15)

        coroutineScope {
            val jobs = lines.mapIndexed { idx, line ->
                async {
                    semaphore.withPermit {
                        val resultLog = try {
                            val info = processSingleBulkLine(line, isHeroOrigin)
                            successCount.incrementAndGet()
                            "OK [${idx + 1}] $info"
                        } catch (e: Exception) {
                            failedCount.incrementAndGet()
                            "ERRO [${idx + 1}] ${e.message ?: "Falha"}"
                        }
                        val done = processedCount.incrementAndGet()
                        onProgress?.invoke(
                            BulkProgress(
                                current = done,
                                total = total,
                                success = successCount.get(),
                                failed = failedCount.get(),
                                lastLog = resultLog
                            )
                        )
                    }
                }
            }
            jobs.awaitAll()
        }

        Pair(successCount.get(), failedCount.get())
    }

    /**
     * Identico ao processOneBulkLine(line) do index_2.html
     */
    private suspend fun processSingleBulkLine(line: String, bulkOrigin: Boolean): String {
        val creds = PlaylistParser.detectXtreamPure(line)
        if (creds != null) {
            return bulkLoadXtreamProfileOnly(creds.server, creds.username, creds.password, bulkOrigin)
        }

        if (line.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
            val isM3uHint = line.contains("m3u", ignoreCase = true) ||
                line.contains("get.php", ignoreCase = true) ||
                line.contains("type=m3u", ignoreCase = true)
            val hero = HeroGenerator.generateHeroName()
            val profileId = profileDao.insertProfile(
                ProfileEntity(
                    name = hero.name,
                    universe = hero.universe,
                    sourceType = if (isM3uHint) "m3u" else "txt",
                    sourceLabel = line.take(300),
                    liveCount = 0,
                    vodCount = 0,
                    seriesCount = 0,
                    bulkOrigin = bulkOrigin,
                    isActive = false
                )
            )
            profileDao.setActiveProfile(profileId)
            return "${hero.name} (${if (isM3uHint) "M3U" else "TXT"}) - pendente"
        }

        val inline = PlaylistParser.parseInlineChannel(line)
        if (inline != null) {
            val hero = HeroGenerator.generateHeroName()
            val profileId = profileDao.insertProfile(
                ProfileEntity(
                    name = hero.name,
                    universe = hero.universe,
                    sourceType = "txt",
                    sourceLabel = inline.first,
                    liveCount = 1,
                    bulkOrigin = bulkOrigin,
                    isActive = false
                )
            )
            channelDao.insertChannels(
                listOf(
                    ChannelEntity(
                        profileId = profileId,
                        title = inline.first,
                        url = inline.second,
                        groupName = "TXT",
                        contentType = "LIVE"
                    )
                )
            )
            profileDao.setActiveProfile(profileId)
            return "${hero.name} (inline) - 1"
        }

        throw IllegalStateException("Nao reconhecido")
    }

    /**
     * Identico ao bulkLoadXtream(server, user, pass) do index_2.html:
     * Valida credenciais, extrai exp_date, max_connections e contagens de live/vod/series
     * sem sobrecarregar o banco antes do usuario clicar no card do perfil.
     */
    private suspend fun bulkLoadXtreamProfileOnly(
        server: String,
        user: String,
        pass: String,
        bulkOrigin: Boolean
    ): String {
        val cleanServer = server.trim().trimEnd('/')
        val encUser = URLEncoder.encode(user.trim(), "UTF-8")
        val encPass = URLEncoder.encode(pass.trim(), "UTF-8")
        val authUrl = "$cleanServer/player_api.php?username=$encUser&password=$encPass"

        val authJson = fetchJsonObject(authUrl)
        val userInfo = authJson?.optJSONObject("user_info")
        if (userInfo == null || userInfo.optInt("auth", 0) == 0) {
            throw IllegalStateException("Credenciais invalidas")
        }

        val expSeconds = userInfo.optString("exp_date").toLongOrNull() ?: 0L
        val validityIso = if (expSeconds > 0L) Instant.ofEpochSecond(expSeconds).toString() else null
        val maxConnections = userInfo.optString("max_connections").toIntOrNull()
        val activeConnections = userInfo.optString("active_cons").toIntOrNull() ?: 0

        val hero = HeroGenerator.generateHeroName()
        val profileId = profileDao.insertProfile(
            ProfileEntity(
                name = hero.name,
                universe = hero.universe,
                sourceType = "xtream",
                sourceLabel = cleanServer,
                server = cleanServer,
                user = user.trim(),
                pass = pass.trim(),
                validityIso = validityIso,
                maxConnections = maxConnections,
                activeConnections = activeConnections,
                liveCount = 0,
                vodCount = 0,
                seriesCount = 0,
                bulkOrigin = bulkOrigin,
                isActive = false
            )
        )
        profileDao.setActiveProfile(profileId)
        return "${hero.name} (XTREAM) - perfil salvo"
    }

    /**
     * Identico ao activateProfile(id) do index_2.html:
     * Ativa o perfil e reconecta automaticamente se for Xtream ou M3U remoto.
     */
    suspend fun activateAndSyncProfile(profileId: Long): Int = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext 0
        profileDao.setActiveProfile(profileId)

        if (profile.sourceType.equals("xtream", ignoreCase = true) &&
            !profile.server.isNullOrBlank() &&
            !profile.user.isNullOrBlank() &&
            !profile.pass.isNullOrBlank()
        ) {
            return@withContext loadXtreamData(
                server = profile.server,
                username = profile.user,
                password = profile.pass,
                existingProfileId = profile.id
            )
        }

        if (profile.sourceType.equals("m3u", ignoreCase = true) &&
            profile.sourceLabel.startsWith("http", ignoreCase = true)
        ) {
            return@withContext loadM3UUrlForProfile(profile.id, profile.sourceLabel)
        }

        if (profile.sourceType.equals("txt", ignoreCase = true) &&
            profile.sourceLabel.startsWith("http", ignoreCase = true)
        ) {
            return@withContext loadM3UUrlForProfile(profile.id, profile.sourceLabel)
        }

        channelDao.countChannelsByProfile(profileId)
    }

    suspend fun loadM3UUrlForProfile(profileId: Long, url: String): Int =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url.trim()).build()
            val text = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val items = if (text.contains("#EXTM3U") || text.contains("#EXTINF")) {
                PlaylistParser.parseM3U(text)
            } else {
                PlaylistParser.parseTXT(text)
            }

            channelDao.deleteChannelsByProfile(profileId)
            if (items.isNotEmpty()) {
                val entities = items.map {
                    ChannelEntity(
                        profileId = profileId,
                        title = it.title,
                        url = it.url,
                        groupName = it.groupName,
                        contentType = it.contentType,
                        streamIcon = it.streamIcon,
                        remoteId = it.remoteId
                    )
                }
                channelDao.insertChannels(entities)
            }

            profileDao.getProfileById(profileId)?.let { p ->
                profileDao.updateProfile(
                    p.copy(
                        liveCount = items.size,
                        vodCount = 0,
                        seriesCount = 0,
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }
            items.size
        }

    /**
     * Identico ao loadXtreamData(server, user, pass, existingProfileId) do index_2.html
     */
    suspend fun loadXtreamData(
        server: String,
        username: String,
        password: String,
        existingProfileId: Long? = null
    ): Int = withContext(Dispatchers.IO) {
        val cleanServer = server.trim().trimEnd('/')
        val encUser = URLEncoder.encode(username.trim(), "UTF-8")
        val encPass = URLEncoder.encode(password.trim(), "UTF-8")
        val base = "$cleanServer/player_api.php?username=$encUser&password=$encPass"

        val authJson = fetchJsonObject(base)
        val userInfo = authJson?.optJSONObject("user_info")
        if (userInfo == null || userInfo.optInt("auth", 0) == 0) {
            throw IllegalArgumentException("Credenciais invalidas")
        }

        val expSeconds = userInfo.optString("exp_date").toLongOrNull() ?: 0L
        val validityIso = if (expSeconds > 0L) Instant.ofEpochSecond(expSeconds).toString() else null
        val maxConnections = userInfo.optString("max_connections").toIntOrNull()
        val activeConnections = userInfo.optString("active_cons").toIntOrNull() ?: 0

        val liveItems = mutableListOf<PlaylistItem>()
        val vodItems = mutableListOf<PlaylistItem>()
        val seriesItems = mutableListOf<PlaylistItem>()

        // 1. LIVE
        runCatching {
            val cats = fetchJsonArray("$base&action=get_live_categories").toCategoryMap()
            val streams = fetchJsonArray("$base&action=get_live_streams")
            if (streams != null) {
                for (i in 0 until streams.length()) {
                    val x = streams.optJSONObject(i) ?: continue
                    val streamId = x.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                    val title = x.optString("name").ifBlank { "Canal" }
                    val catId = x.optString("category_id")
                    val group = cats[catId] ?: "Geral"
                    val icon = x.optString("stream_icon").ifBlank { x.optString("stream_icon_url") }.ifBlank { null }
                    liveItems.add(
                        PlaylistItem(
                            title = title,
                            url = "$cleanServer/live/$encUser/$encPass/$streamId.m3u8",
                            groupName = group,
                            contentType = "LIVE",
                            streamIcon = icon,
                            remoteId = streamId
                        )
                    )
                }
            }
        }

        // 2. VOD
        runCatching {
            val cats = fetchJsonArray("$base&action=get_vod_categories").toCategoryMap()
            val streams = fetchJsonArray("$base&action=get_vod_streams")
            if (streams != null) {
                for (i in 0 until streams.length()) {
                    val x = streams.optJSONObject(i) ?: continue
                    val streamId = x.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                    val title = x.optString("name").ifBlank { "Filme" }
                    val ext = x.optString("container_extension").ifBlank { "mp4" }
                    val catId = x.optString("category_id")
                    val group = cats[catId] ?: "Geral"
                    val icon = x.optString("stream_icon").ifBlank { x.optString("movie_image") }.ifBlank { null }
                    vodItems.add(
                        PlaylistItem(
                            title = title,
                            url = "$cleanServer/movie/$encUser/$encPass/$streamId.$ext",
                            groupName = group,
                            contentType = "VOD",
                            streamIcon = icon,
                            remoteId = streamId
                        )
                    )
                }
            }
        }

        // 3. SERIES
        runCatching {
            val cats = fetchJsonArray("$base&action=get_series_categories").toCategoryMap()
            val streams = fetchJsonArray("$base&action=get_series")
            if (streams != null) {
                for (i in 0 until streams.length()) {
                    val x = streams.optJSONObject(i) ?: continue
                    val seriesId = x.optString("series_id").takeIf { it.isNotBlank() } ?: continue
                    val title = x.optString("name").ifBlank { "Série" }
                    val catId = x.optString("category_id")
                    val group = cats[catId] ?: "Geral"
                    val cover = x.optString("cover").ifBlank { x.optString("series_icon") }.ifBlank { null }
                    seriesItems.add(
                        PlaylistItem(
                            title = title,
                            url = "$cleanServer/series/$encUser/$encPass/$seriesId",
                            groupName = group,
                            contentType = "SERIES",
                            streamIcon = cover,
                            remoteId = seriesId
                        )
                    )
                }
            }
        }

        val targetProfileId = if (existingProfileId != null) {
            val existing = profileDao.getProfileById(existingProfileId)
            if (existing != null) {
                profileDao.updateProfile(
                    existing.copy(
                        validityIso = validityIso,
                        maxConnections = maxConnections,
                        activeConnections = activeConnections,
                        liveCount = liveItems.size,
                        vodCount = vodItems.size,
                        seriesCount = seriesItems.size,
                        lastUsed = System.currentTimeMillis()
                    )
                )
                existing.id
            } else {
                createNewXtreamProfile(cleanServer, username, password, validityIso, maxConnections, activeConnections, liveItems.size, vodItems.size, seriesItems.size)
            }
        } else {
            createNewXtreamProfile(cleanServer, username, password, validityIso, maxConnections, activeConnections, liveItems.size, vodItems.size, seriesItems.size)
        }

        val combined = liveItems + vodItems + seriesItems
        channelDao.deleteChannelsByProfile(targetProfileId)
        if (combined.isNotEmpty()) {
            val entities = combined.map {
                ChannelEntity(
                    profileId = targetProfileId,
                    title = it.title,
                    url = it.url,
                    groupName = it.groupName,
                    contentType = it.contentType,
                    streamIcon = it.streamIcon,
                    remoteId = it.remoteId
                )
            }
            channelDao.insertChannels(entities)
        }

        profileDao.setActiveProfile(targetProfileId)
        combined.size
    }

    private suspend fun createNewXtreamProfile(
        cleanServer: String,
        username: String,
        password: String,
        validityIso: String?,
        maxConnections: Int?,
        activeConnections: Int,
        liveCount: Int,
        vodCount: Int,
        seriesCount: Int
    ): Long {
        val hero = HeroGenerator.generateHeroName()
        return profileDao.insertProfile(
            ProfileEntity(
                name = hero.name,
                universe = hero.universe,
                sourceType = "xtream",
                sourceLabel = cleanServer,
                server = cleanServer,
                user = username.trim(),
                pass = password.trim(),
                validityIso = validityIso,
                maxConnections = maxConnections,
                activeConnections = activeConnections,
                liveCount = liveCount,
                vodCount = vodCount,
                seriesCount = seriesCount,
                bulkOrigin = false,
                isActive = true
            )
        )
    }

    /**
     * Carrega temporadas e episodios via get_series_info (identico ao loadSeriesInfo do index_2.html)
     */
    suspend fun fetchSeriesSeasons(
        profile: ProfileEntity,
        seriesId: String
    ): Map<String, List<SeriesEpisode>> = withContext(Dispatchers.IO) {
        val cleanServer = profile.server?.trimEnd('/') ?: throw IllegalStateException("Servidor Xtream ausente")
        val encUser = URLEncoder.encode(profile.user.orEmpty(), "UTF-8")
        val encPass = URLEncoder.encode(profile.pass.orEmpty(), "UTF-8")
        val url = "$cleanServer/player_api.php?username=$encUser&password=$encPass&action=get_series_info&series_id=$seriesId"

        val root = fetchJsonObject(url) ?: throw IllegalStateException("Falha ao buscar episodios da serie.")
        val episodesObj = root.optJSONObject("episodes") ?: return@withContext emptyMap()

        val result = linkedMapOf<String, List<SeriesEpisode>>()
        val sortedKeys = episodesObj.keys().asSequence().toList()
            .sortedBy { it.toDoubleOrNull() ?: Double.MAX_VALUE }

        for (seasonNum in sortedKeys) {
            val arr = episodesObj.optJSONArray(seasonNum) ?: continue
            val list = mutableListOf<SeriesEpisode>()
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                val id = ep.optString("id")
                    .ifBlank { ep.optString("episode_id") }
                    .ifBlank { ep.optString("stream_id") }
                    .ifBlank { ep.optString("vod_id") }
                if (id.isBlank()) continue

                val epNum = ep.optInt("episode_num", ep.optInt("episode", i + 1))
                val title = ep.optString("title")
                    .ifBlank { ep.optString("name") }
                    .ifBlank { "Episódio $epNum" }
                val ext = ep.optString("container_extension")
                    .ifBlank { ep.optString("ext") }
                    .ifBlank { "mp4" }
                val dur = ep.optString("duration").ifBlank {
                    ep.optJSONObject("info")?.optString("duration").orEmpty()
                }.ifBlank { null }

                list.add(
                    SeriesEpisode(
                        id = id,
                        episodeNum = epNum,
                        title = title,
                        extension = ext,
                        duration = dur
                    )
                )
            }
            result[seasonNum] = list.sortedBy { it.episodeNum }
        }
        result
    }

    /**
     * Exporta todos os perfis no formato exato do exportProfilesTXT() do index_2.html:
     * sourceType|sourceLabel|server|user|pass|validity|maxConnections|hero|live|vod|series
     */
    fun formatProfilesExportTxt(profiles: List<ProfileEntity>): String {
        val lines = mutableListOf("# IPTV Hero Pro Profiles")
        for (p in profiles) {
            lines.add(
                listOf(
                    p.sourceType,
                    p.sourceLabel,
                    p.server.orEmpty(),
                    p.user.orEmpty(),
                    p.pass.orEmpty(),
                    p.validityIso.orEmpty(),
                    p.maxConnections?.toString().orEmpty(),
                    p.name,
                    p.liveCount.toString(),
                    p.vodCount.toString(),
                    p.seriesCount.toString()
                ).joinToString("|")
            )
        }
        return lines.joinToString("\n")
    }

    private fun fetchString(url: String): String? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun fetchJsonObject(url: String): JSONObject? {
        val raw = fetchString(url) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun fetchJsonArray(url: String): JSONArray? {
        val raw = fetchString(url) ?: return null
        return runCatching { JSONArray(raw) }.getOrNull()
    }

    private fun JSONArray?.toCategoryMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val id = obj.optString("category_id")
            val name = obj.optString("category_name")
            if (id.isNotBlank() && name.isNotBlank()) {
                map[id] = name
            }
        }
        return map
    }
}
