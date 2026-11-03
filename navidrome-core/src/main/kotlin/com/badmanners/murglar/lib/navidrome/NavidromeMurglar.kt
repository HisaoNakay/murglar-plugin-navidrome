package com.badmanners.murglar.lib.navidrome

import com.badmanners.murglar.lib.core.localization.DefaultMessages.Companion.DEFAULT
import com.badmanners.murglar.lib.core.localization.MessageException
import com.badmanners.murglar.lib.core.localization.RussianMessages.Companion.RUSSIAN
import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.model.album.AlbumType
import com.badmanners.murglar.lib.core.model.node.Node
import com.badmanners.murglar.lib.core.model.tag.Lyrics
import com.badmanners.murglar.lib.core.model.tag.Lyrics.SyncedLyrics
import com.badmanners.murglar.lib.core.model.tag.Tags
import com.badmanners.murglar.lib.core.model.track.source.Bitrate
import com.badmanners.murglar.lib.core.model.track.source.Container
import com.badmanners.murglar.lib.core.model.track.source.Extension
import com.badmanners.murglar.lib.core.model.track.source.QualityTier
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.model.track.source.SourceFormat
import com.badmanners.murglar.lib.core.model.track.source.SourceFormat.SourceFormatAvailability.AVAILABLE
import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.notification.NotificationMiddleware
import com.badmanners.murglar.lib.core.preference.ActionPreference
import com.badmanners.murglar.lib.core.preference.EditPreference
import com.badmanners.murglar.lib.core.preference.Preference
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.preference.SwitchPreference
import com.badmanners.murglar.lib.core.service.BaseMurglar
import com.badmanners.murglar.lib.core.utils.MediaId
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.normalize
import com.badmanners.murglar.lib.core.utils.getBooleanOpt
import com.badmanners.murglar.lib.core.utils.getDoubleOpt
import com.badmanners.murglar.lib.core.utils.getIntOpt
import com.badmanners.murglar.lib.core.utils.getJsonArrayOpt
import com.badmanners.murglar.lib.core.utils.getJsonObject
import com.badmanners.murglar.lib.core.utils.getJsonObjectOpt
import com.badmanners.murglar.lib.core.utils.getLongOpt
import com.badmanners.murglar.lib.core.utils.getString
import com.badmanners.murglar.lib.core.utils.getStringOpt
import com.badmanners.murglar.lib.core.utils.jsonObjectOpt
import com.badmanners.murglar.lib.core.utils.stringOpt
import com.badmanners.murglar.lib.navidrome.api.SubsonicClient
import com.badmanners.murglar.lib.navidrome.localization.NavidromeDefaultMessages
import com.badmanners.murglar.lib.navidrome.localization.NavidromeMessages
import com.badmanners.murglar.lib.navidrome.localization.NavidromeRussianMessages
import com.badmanners.murglar.lib.navidrome.login.NavidromeLoginResolver
import com.badmanners.murglar.lib.navidrome.model.NavidromeAlbum
import com.badmanners.murglar.lib.navidrome.model.NavidromeArtist
import com.badmanners.murglar.lib.navidrome.model.NavidromePlaylist
import com.badmanners.murglar.lib.navidrome.model.NavidromeTrack
import com.badmanners.murglar.lib.navidrome.node.NavidromeNodeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.threeten.bp.LocalDate
import java.util.Locale


class NavidromeMurglar(
    id: String,
    preferences: PreferenceMiddleware,
    network: NetworkMiddleware,
    notifications: NotificationMiddleware,
    logger: LoggerMiddleware
) : BaseMurglar<NavidromeTrack, NavidromeMessages>(
    id, MESSAGES, preferences, network, notifications, logger
) {

    companion object {
        /**
         * Must be used only for [MediaId.build], don't pass it to the [BaseMurglar] constructor directly!
         */
        const val SERVICE_ID = "navidrome"

        private val MESSAGES = mapOf(
            DEFAULT to NavidromeDefaultMessages,
            RUSSIAN to NavidromeRussianMessages
        )

        const val PAGE_SIZE = 50

        private const val SMALL_COVER_SIZE = 300
        private const val BIG_COVER_SIZE = 1200

        private const val ORIGINAL_SOURCE = "original"
        private const val MP3_320_SOURCE = "mp3_320"
        private const val MP3_128_SOURCE = "mp3_128"

        private val LOSSLESS_SUFFIXES = setOf("flac", "wav", "alac", "ape", "wv", "aiff", "aif", "dsf", "dff")

        private val LOSSY_EXTENSIONS = listOf(
            Extension.MP3, Extension.AAC, Extension.OPUS, Extension.OGG, Extension.M4A, Extension.MP4,
            Extension.WMA, Extension.WEBM, Extension.MKV, Extension.UNKNOWN
        )
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client = SubsonicClient(preferences, network, logger, messages)

    override val loginResolver = NavidromeLoginResolver(notifications, client, messages)

    override val nodeResolver = NavidromeNodeResolver(this, messages)

    override val murglarPreferences: List<Preference>
        get() = listOf(
            EditPreference(
                id = "server_url",
                title = messages.serverAddress,
                message = messages.serverAddressHint,
                getter = { client.serverUrl },
                setter = { client.changeServerUrl(it) },
                refreshAllAfterChange = true
            ),
            ActionPreference(
                id = "find_server",
                title = messages.findServer,
                summary = messages.findServerSummary,
                action = ::findServerInBackground,
                needConfirmation = false,
                refreshAllAfterInvoke = true
            ),
            SwitchPreference(
                id = "auto_discovery",
                title = messages.autoDiscovery,
                summary = messages.autoDiscoverySummary,
                getter = { client.autoDiscovery },
                setter = { client.autoDiscovery = it }
            )
        )

    override val possibleFormats: List<SourceFormat>
        get() = listOf(Extension.FLAC, Extension.WAV, Extension.M4A, Extension.UNKNOWN).map {
            SourceFormat(it, Bitrate.B_LOSSLESS, QualityTier.LOSSLESS, AVAILABLE)
        } + LOSSY_EXTENSIONS.map {
            SourceFormat(it, Bitrate.B_UNKNOWN, QualityTier.HIGH_QUALITY, AVAILABLE)
        } + listOf(
            SourceFormat(Extension.MP3, Bitrate.B_320, QualityTier.HIGH_QUALITY, AVAILABLE),
            SourceFormat(Extension.MP3, Bitrate.B_128, QualityTier.NORMAL_QUALITY, AVAILABLE)
        )

    override suspend fun onCreate() {
        if (!loginResolver.isLogged)
            return
        try {
            client.ensureReachable()
        } catch (e: Exception) {
            logger.w("Navidrome", "Server check on start failed", e)
        }
    }

    override suspend fun resolveSourceForUrl(track: NavidromeTrack, source: Source): Source {
        loginResolver.checkLogged()
        client.ensureReachable()
        val url = when (source.id) {
            ORIGINAL_SOURCE -> client.buildUrl("stream", "id" to track.id, "format" to "raw")
            MP3_320_SOURCE -> client.buildUrl("stream", "id" to track.id, "format" to "mp3", "maxBitRate" to 320)
            MP3_128_SOURCE -> client.buildUrl("stream", "id" to track.id, "format" to "mp3", "maxBitRate" to 128)
            else -> error("${messages.sourceUrlUnavailable} Unknown source '${source.id}'!")
        }
        return source.copyWithNewUrl(url)
    }

    override fun hasLyrics(track: NavidromeTrack) = true

    override suspend fun getLyrics(track: NavidromeTrack): Lyrics {
        val response = client.call("getLyricsBySongId", "id" to track.id)
        val all = response.getJsonObjectOpt("lyricsList")?.getJsonArrayOpt("structuredLyrics")
            ?.mapNotNull { it.jsonObjectOpt }
            .orEmpty()
        val lyrics = all.firstOrNull { it.getBooleanOpt("synced") == true } ?: all.firstOrNull()
        val lines = lyrics?.getJsonArrayOpt("line")?.mapNotNull { it.jsonObjectOpt }.orEmpty()
        if (lines.isEmpty())
            throw MessageException(messages.trackHasNoLyrics)

        val plain = lines.joinToString("\n") { it.getStringOpt("value").orEmpty() }
        if (lyrics?.getBooleanOpt("synced") != true)
            return Lyrics(plain)

        val offset = lyrics.getLongOpt("offset") ?: 0L
        val syncedLines = lines.map {
            SyncedLyrics.Line((it.getLongOpt("start") ?: 0L) + offset, null, it.getStringOpt("value"))
        }
        val synced = SyncedLyrics(syncedLines, track.artistNames.joinToString(", "), track.title, track.albumName)
        return Lyrics(plain, synced)
    }

    override suspend fun getTags(track: NavidromeTrack, parent: Node?) = Tags.Builder().apply {
        title = track.title
        artists = track.artistNames
        album = track.albumName
        albumArtist = track.albumArtist
        albumType = (parent as? NavidromeAlbum)?.type
        trackNumber = track.indexInAlbum
        diskNumber = track.volumeNumber
        totalTracks = (parent as? NavidromeAlbum)?.tracksCount
        releaseDate = track.albumReleaseDate
        genre = track.genre
        gain = track.gain
        peak = track.peak
        mediaId = track.mediaId
    }.createTags()

    override suspend fun getTracksByMediaIds(mediaIds: List<String>): List<NavidromeTrack> = mediaIds
        .map { MediaId.getIds(it).joinToString("_") }
        .mapNotNull { id ->
            try {
                getTrack(id)
            } catch (e: Exception) {
                logger.w("Navidrome", "Can't load track $id", e)
                null
            }
        }


    //region query methods
    suspend fun getStarredTracks(): List<NavidromeTrack> = getStarred().getJsonArrayOpt("song").toTracks()

    suspend fun getStarredAlbums(): List<NavidromeAlbum> = getStarred().getJsonArrayOpt("album").toAlbums()

    suspend fun getStarredArtists(): List<NavidromeArtist> = getStarred().getJsonArrayOpt("artist").toArtists()

    private suspend fun getStarred(): JsonObject {
        loginResolver.checkLogged()
        return client.call("getStarred2").getJsonObjectOpt("starred2") ?: JsonObject(emptyMap())
    }

    suspend fun getAllArtists(): List<NavidromeArtist> {
        loginResolver.checkLogged()
        val indexes = client.call("getArtists").getJsonObjectOpt("artists")?.getJsonArrayOpt("index")
        return indexes.orEmpty()
            .mapNotNull { it.jsonObjectOpt?.getJsonArrayOpt("artist") }
            .flatMap { it.toArtists() }
    }

    /**
     * @param type one of `newest`, `recent`, `frequent`, `random`, `alphabeticalByName`
     */
    suspend fun getAlbumList(type: String, page: Int): List<NavidromeAlbum> {
        loginResolver.checkLogged()
        val response = client.call(
            "getAlbumList2", "type" to type, "size" to PAGE_SIZE, "offset" to page * PAGE_SIZE
        )
        return response.getJsonObjectOpt("albumList2")?.getJsonArrayOpt("album").toAlbums()
    }

    suspend fun getRandomTracks(): List<NavidromeTrack> {
        loginResolver.checkLogged()
        val response = client.call("getRandomSongs", "size" to PAGE_SIZE)
        return response.getJsonObjectOpt("randomSongs")?.getJsonArrayOpt("song").toTracks()
    }

    suspend fun getGenres(): List<Pair<String, Int>> {
        loginResolver.checkLogged()
        val genres = client.call("getGenres").getJsonObjectOpt("genres")?.getJsonArrayOpt("genre")
        return genres.orEmpty()
            .mapNotNull { it.jsonObjectOpt }
            .mapNotNull { genre -> genre.getStringOpt("value")?.let { it to (genre.getIntOpt("songCount") ?: 0) } }
            .filter { it.first.isNotBlank() }
            .sortedBy { it.first.lowercase() }
    }

    suspend fun getGenreTracks(genre: String, page: Int): List<NavidromeTrack> {
        loginResolver.checkLogged()
        val response = client.call(
            "getSongsByGenre", "genre" to genre, "count" to PAGE_SIZE, "offset" to page * PAGE_SIZE
        )
        return response.getJsonObjectOpt("songsByGenre")?.getJsonArrayOpt("song").toTracks()
    }

    suspend fun getPlaylists(): List<NavidromePlaylist> {
        loginResolver.checkLogged()
        val playlists = client.call("getPlaylists").getJsonObjectOpt("playlists")?.getJsonArrayOpt("playlist")
        return playlists.orEmpty().mapNotNull { it.jsonObjectOpt?.toPlaylist() }
    }

    suspend fun getPlaylist(playlistId: String): NavidromePlaylist = getPlaylistObject(playlistId).toPlaylist()

    suspend fun getPlaylistTracks(playlistId: String): List<NavidromeTrack> =
        getPlaylistObject(playlistId).getJsonArrayOpt("entry").toTracks()

    private suspend fun getPlaylistObject(playlistId: String): JsonObject {
        loginResolver.checkLogged()
        return client.call("getPlaylist", "id" to playlistId).getJsonObject("playlist")
    }

    suspend fun searchTracks(query: String, page: Int): List<NavidromeTrack> =
        search(query, "song", page).getJsonArrayOpt("song").toTracks()

    suspend fun searchAlbums(query: String, page: Int): List<NavidromeAlbum> =
        search(query, "album", page).getJsonArrayOpt("album").toAlbums()

    suspend fun searchArtists(query: String, page: Int): List<NavidromeArtist> =
        search(query, "artist", page).getJsonArrayOpt("artist").toArtists()

    private suspend fun search(query: String, what: String, page: Int): JsonObject {
        loginResolver.checkLogged()
        val counts = listOf("song", "album", "artist").flatMap {
            val count = if (it == what) PAGE_SIZE else 0
            listOf("${it}Count" to count, "${it}Offset" to page * count)
        }
        val response = client.call("search3", "query" to query, *counts.toTypedArray())
        return response.getJsonObjectOpt("searchResult3") ?: JsonObject(emptyMap())
    }

    suspend fun getArtist(artistId: String): NavidromeArtist = getArtistObject(artistId).toArtist()

    suspend fun getArtistAlbums(artistId: String): List<NavidromeAlbum> =
        getArtistObject(artistId).getJsonArrayOpt("album").toAlbums()

    private suspend fun getArtistObject(artistId: String): JsonObject {
        loginResolver.checkLogged()
        return client.call("getArtist", "id" to artistId).getJsonObject("artist")
    }

    suspend fun getAlbum(albumId: String): NavidromeAlbum = getAlbumObject(albumId).toAlbum()

    suspend fun getAlbumTracks(albumId: String): List<NavidromeTrack> =
        getAlbumObject(albumId).getJsonArrayOpt("song").toTracks()

    private suspend fun getAlbumObject(albumId: String): JsonObject {
        loginResolver.checkLogged()
        return client.call("getAlbum", "id" to albumId).getJsonObject("album")
    }

    suspend fun getTrack(trackId: String): NavidromeTrack {
        loginResolver.checkLogged()
        return client.call("getSong", "id" to trackId).getJsonObject("song").toTrack()
    }

    /**
     * @param idParam `id` for tracks, `albumId` for albums, `artistId` for artists
     */
    suspend fun star(idParam: String, id: String, star: Boolean) {
        loginResolver.checkLogged()
        client.call(if (star) "star" else "unstar", idParam to id)
    }

    suspend fun scrobble(track: NavidromeTrack, submission: Boolean) {
        loginResolver.checkLogged()
        client.call(
            "scrobble", "id" to track.id, "submission" to submission, "time" to System.currentTimeMillis()
        )
    }
    //endregion query methods


    private fun findServerInBackground() {
        notifications.shortNotify(messages.searchingServer)
        backgroundScope.launch {
            val url = try {
                client.discover()
            } catch (e: Exception) {
                logger.e("Navidrome", "Discovery failed", e)
                null
            }
            when (url) {
                null -> notifications.longNotify(messages.serverNotFound)
                else -> notifications.longNotify("${messages.serverFound}: $url")
            }
        }
    }

    private fun coverUrl(coverArtId: String?, size: Int) =
        coverArtId?.takeIf { it.isNotEmpty() }?.let { client.buildUrl("getCoverArt", "id" to it, "size" to size) }

    private fun JsonObject.idNamePairs(arrayKey: String, idKey: String, nameKey: String): List<Pair<String, String>> {
        val fromArray = getJsonArrayOpt(arrayKey).orEmpty()
            .mapNotNull { it.jsonObjectOpt }
            .mapNotNull { artist -> artist.getStringOpt("name")?.let { (artist.getStringOpt("id") ?: "") to it } }
        if (fromArray.isNotEmpty())
            return fromArray
        val name = getStringOpt(nameKey) ?: return emptyList()
        return listOf((getStringOpt(idKey) ?: "") to name)
    }

    private fun JsonArray?.toTracks(): List<NavidromeTrack> = orEmpty().mapNotNull { it.jsonObjectOpt?.toTrack() }

    private fun JsonArray?.toAlbums(): List<NavidromeAlbum> = orEmpty().mapNotNull { it.jsonObjectOpt?.toAlbum() }

    private fun JsonArray?.toArtists(): List<NavidromeArtist> = orEmpty().mapNotNull { it.jsonObjectOpt?.toArtist() }

    private fun JsonObject.toTrack(): NavidromeTrack {
        val id = getString("id")
        val artists = idNamePairs("artists", "artistId", "artist")
        val year = getIntOpt("year")?.takeIf { it > 0 }
        val replayGain = getJsonObjectOpt("replayGain")
        val gain = replayGain?.getDoubleOpt("trackGain")?.takeIf { it != 0.0 }?.let {
            "%+.2f dB".format(Locale.ROOT, it)
        }
        val peak = replayGain?.getDoubleOpt("trackPeak")?.takeIf { it > 0.0 }
        val albumArtist = getStringOpt("displayAlbumArtist")
            ?: getJsonArrayOpt("albumArtists")?.mapNotNull { it.jsonObjectOpt?.getStringOpt("name") }
                ?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val coverArt = getStringOpt("coverArt")

        return NavidromeTrack(
            id = id,
            title = (getStringOpt("title") ?: id).normalize(),
            artistIds = artists.map { it.first },
            artistNames = artists.map { it.second.normalize() },
            albumId = getStringOpt("albumId"),
            albumName = getStringOpt("album")?.normalize(),
            albumReleaseDate = year?.let { LocalDate.of(it, 1, 1) },
            albumArtist = albumArtist,
            indexInAlbum = getIntOpt("track")?.takeIf { it > 0 },
            volumeNumber = getIntOpt("discNumber")?.takeIf { it > 0 },
            durationMs = (getLongOpt("duration") ?: 0L) * 1000,
            genre = getStringOpt("genre"),
            gain = gain,
            peak = peak,
            sources = buildSources(getStringOpt("suffix"), getIntOpt("bitRate"), getLongOpt("size")),
            mediaId = MediaId.build(SERVICE_ID, id),
            smallCoverUrl = coverUrl(coverArt, SMALL_COVER_SIZE),
            bigCoverUrl = coverUrl(coverArt, BIG_COVER_SIZE)
        )
    }

    private fun buildSources(suffix: String?, bitRate: Int?, size: Long?): List<Source> {
        val lowerSuffix = suffix?.lowercase()
        val extension = lowerSuffix.toExtension()
        val lossless = lowerSuffix in LOSSLESS_SUFFIXES || (extension == Extension.M4A && (bitRate ?: 0) >= 600)
        val originalTag = buildString {
            append(messages.original).append(": ").append(suffix?.uppercase() ?: "?")
            bitRate?.takeIf { it > 0 }?.let { append(' ').append(it).append(" kbps") }
        }
        val sources = mutableListOf(
            Source(
                ORIGINAL_SOURCE, null, originalTag, extension, Container.PROGRESSIVE,
                if (lossless) Bitrate.B_LOSSLESS else Bitrate.B_UNKNOWN,
                if (lossless) QualityTier.LOSSLESS else QualityTier.HIGH_QUALITY,
                size ?: Source.UNKNOWN_SIZE
            )
        )
        val isMp3 = extension == Extension.MP3
        if (!isMp3 || (bitRate ?: Int.MAX_VALUE) > 320)
            sources += Source(
                MP3_320_SOURCE, null, "MP3 ${Bitrate.B_320.text}", Extension.MP3, Container.PROGRESSIVE,
                Bitrate.B_320, QualityTier.HIGH_QUALITY
            )
        if (!isMp3 || (bitRate ?: Int.MAX_VALUE) > 128)
            sources += Source(
                MP3_128_SOURCE, null, "MP3 ${Bitrate.B_128.text}", Extension.MP3, Container.PROGRESSIVE,
                Bitrate.B_128, QualityTier.NORMAL_QUALITY
            )
        return sources
    }

    private fun String?.toExtension() = when (this) {
        "mp3" -> Extension.MP3
        "flac" -> Extension.FLAC
        "wav" -> Extension.WAV
        "aac" -> Extension.AAC
        "opus" -> Extension.OPUS
        "ogg", "oga" -> Extension.OGG
        "m4a", "alac" -> Extension.M4A
        "mp4" -> Extension.MP4
        "wma" -> Extension.WMA
        "webm" -> Extension.WEBM
        "mka", "mkv" -> Extension.MKV
        else -> Extension.UNKNOWN
    }

    private fun JsonObject.toAlbum(): NavidromeAlbum {
        val id = getString("id")
        val artists = idNamePairs("artists", "artistId", "artist")
        val releaseTypes = getJsonArrayOpt("releaseTypes")?.mapNotNull { it.stringOpt?.lowercase() }.orEmpty()
        val type = when {
            getBooleanOpt("isCompilation") == true || "compilation" in releaseTypes -> AlbumType.COMPILATION
            "single" in releaseTypes -> AlbumType.SINGLE
            "ep" in releaseTypes -> AlbumType.EP
            else -> AlbumType.ALBUM
        }
        val coverArt = getStringOpt("coverArt")
        return NavidromeAlbum(
            id = id,
            title = (getStringOpt("name") ?: getStringOpt("title") ?: id).normalize(),
            type = type,
            artistIds = artists.map { it.first },
            artistNames = artists.map { it.second.normalize() },
            tracksCount = getIntOpt("songCount") ?: 0,
            releaseDate = getIntOpt("year")?.takeIf { it > 0 }?.let { LocalDate.of(it, 1, 1) },
            genre = getStringOpt("genre"),
            smallCoverUrl = coverUrl(coverArt, SMALL_COVER_SIZE),
            bigCoverUrl = coverUrl(coverArt, BIG_COVER_SIZE)
        )
    }

    private fun JsonObject.toArtist(): NavidromeArtist {
        val id = getString("id")
        val coverArt = getStringOpt("coverArt")
        val externalImage = getStringOpt("artistImageUrl")?.takeIf { it.isNotEmpty() }
        return NavidromeArtist(
            id = id,
            name = (getStringOpt("name") ?: id).normalize(),
            smallCoverUrl = coverUrl(coverArt, SMALL_COVER_SIZE) ?: externalImage,
            bigCoverUrl = coverUrl(coverArt, BIG_COVER_SIZE) ?: externalImage,
            albumsCount = getIntOpt("albumCount") ?: 0
        )
    }

    private fun JsonObject.toPlaylist(): NavidromePlaylist {
        val id = getString("id")
        val coverArt = getStringOpt("coverArt")
        return NavidromePlaylist(
            id = id,
            title = (getStringOpt("name") ?: id).normalize(),
            description = getStringOpt("comment")?.takeIf { it.isNotBlank() }?.take(500),
            tracksCount = getIntOpt("songCount") ?: 0,
            smallCoverUrl = coverUrl(coverArt, SMALL_COVER_SIZE),
            bigCoverUrl = coverUrl(coverArt, BIG_COVER_SIZE)
        )
    }
}
