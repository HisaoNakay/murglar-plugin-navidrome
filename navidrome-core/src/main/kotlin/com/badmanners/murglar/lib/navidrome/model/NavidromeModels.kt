package com.badmanners.murglar.lib.navidrome.model

import com.badmanners.murglar.lib.core.model.album.AlbumType
import com.badmanners.murglar.lib.core.model.album.BaseAlbum
import com.badmanners.murglar.lib.core.model.artist.BaseArtist
import com.badmanners.murglar.lib.core.model.playlist.BasePlaylist
import com.badmanners.murglar.lib.core.model.track.BaseTrack
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.utils.contract.Model
import org.threeten.bp.LocalDate


@Model
class NavidromeTrack(
    id: String,
    title: String,
    artistIds: List<String>,
    artistNames: List<String>,
    albumId: String?,
    albumName: String?,
    albumReleaseDate: LocalDate?,
    val albumArtist: String?,
    indexInAlbum: Int?,
    volumeNumber: Int?,
    durationMs: Long,
    genre: String?,
    gain: String?,
    peak: Double?,
    sources: List<Source>,
    mediaId: String,
    smallCoverUrl: String?,
    bigCoverUrl: String?
) : BaseTrack(
    id = id,
    title = title,
    subtitle = null,
    artistIds = artistIds,
    artistNames = artistNames,
    albumId = albumId,
    albumName = albumName,
    albumReleaseDate = albumReleaseDate,
    indexInAlbum = indexInAlbum,
    volumeNumber = volumeNumber,
    durationMs = durationMs,
    genre = genre,
    explicit = false,
    gain = gain,
    peak = peak,
    sources = sources,
    mediaId = mediaId,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = null
)

@Model
class NavidromeAlbum(
    id: String,
    title: String,
    type: AlbumType,
    artistIds: List<String>,
    artistNames: List<String>,
    tracksCount: Int,
    releaseDate: LocalDate?,
    genre: String?,
    smallCoverUrl: String?,
    bigCoverUrl: String?
) : BaseAlbum(
    id = id,
    title = title,
    description = null,
    type = type,
    artistIds = artistIds,
    artistNames = artistNames,
    tracksCount = tracksCount,
    releaseDate = releaseDate,
    genre = genre,
    explicit = false,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = null
)

@Model
class NavidromeArtist(
    id: String,
    name: String,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    val albumsCount: Int
) : BaseArtist(
    id = id,
    name = name,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = null
)

@Model
class NavidromePlaylist(
    id: String,
    title: String,
    description: String?,
    tracksCount: Int,
    smallCoverUrl: String?,
    bigCoverUrl: String?
) : BasePlaylist(
    id = id,
    title = title,
    description = description,
    tracksCount = tracksCount,
    explicit = false,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = null
)
