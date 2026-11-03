package com.badmanners.murglar.lib.navidrome.node

import com.badmanners.murglar.lib.core.model.event.PlayerEvent.TrackEnd
import com.badmanners.murglar.lib.core.model.event.PlayerEvent.TrackStart
import com.badmanners.murglar.lib.core.model.node.Directory as DirectoryNode
import com.badmanners.murglar.lib.core.model.node.NamedPath
import com.badmanners.murglar.lib.core.model.node.Node
import com.badmanners.murglar.lib.core.model.node.Node.Companion.to
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.ENDLESSLY_PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.NON_PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeType.ALBUM
import com.badmanners.murglar.lib.core.model.node.NodeType.ARTIST
import com.badmanners.murglar.lib.core.model.node.NodeType.NODE
import com.badmanners.murglar.lib.core.model.node.NodeType.PLAYLIST
import com.badmanners.murglar.lib.core.model.node.NodeType.TRACK
import com.badmanners.murglar.lib.core.model.node.Path
import com.badmanners.murglar.lib.core.node.BaseNodeResolver
import com.badmanners.murglar.lib.core.node.Directory
import com.badmanners.murglar.lib.core.node.EventConfig
import com.badmanners.murglar.lib.core.node.LikeConfig
import com.badmanners.murglar.lib.core.node.MappedEntity
import com.badmanners.murglar.lib.core.node.Root
import com.badmanners.murglar.lib.core.node.Search
import com.badmanners.murglar.lib.core.node.Track
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.urlDecode
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.urlEncode
import com.badmanners.murglar.lib.navidrome.NavidromeMurglar
import com.badmanners.murglar.lib.navidrome.localization.NavidromeMessages
import com.badmanners.murglar.lib.navidrome.model.NavidromeAlbum
import com.badmanners.murglar.lib.navidrome.model.NavidromeTrack


class NavidromeNodeResolver(
    murglar: NavidromeMurglar,
    messages: NavidromeMessages
) : BaseNodeResolver<NavidromeMurglar, NavidromeMessages>(murglar, messages) {

    companion object {
        private const val SCROBBLE_MAX_MS = 4 * 60 * 1000
    }

    override val configurations = listOf(
        Root(
            pattern = "starredTracks",
            name = messages::starredTracks,
            paging = NON_PAGEABLE,
            hasSubdirectories = false,
            isOwn = true,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getStarredTracks
        ),
        Root(
            pattern = "starredAlbums",
            name = messages::starredAlbums,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::getStarredAlbums
        ),
        Root(
            pattern = "starredArtists",
            name = messages::starredArtists,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = ARTIST,
            nodeContentSupplier = ::getStarredArtists
        ),
        Root(
            pattern = "playlists",
            name = messages::myPlaylists,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = PLAYLIST,
            nodeContentSupplier = ::getPlaylists
        ),
        Root(
            pattern = "newestAlbums",
            name = messages::newestAlbums,
            paging = PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ALBUM,
            nodeContentSupplier = { parentPath, page, _ -> getAlbumList("newest", parentPath, page) }
        ),
        Root(
            pattern = "recentAlbums",
            name = messages::recentAlbums,
            paging = PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ALBUM,
            nodeContentSupplier = { parentPath, page, _ -> getAlbumList("recent", parentPath, page) }
        ),
        Root(
            pattern = "frequentAlbums",
            name = messages::frequentAlbums,
            paging = PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ALBUM,
            nodeContentSupplier = { parentPath, page, _ -> getAlbumList("frequent", parentPath, page) }
        ),
        Root(
            pattern = "randomAlbums",
            name = messages::randomAlbums,
            paging = ENDLESSLY_PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ALBUM,
            nodeContentSupplier = { parentPath, page, _ -> getAlbumList("random", parentPath, page) }
        ),
        Root(
            pattern = "allAlbums",
            name = messages::alphabeticalAlbums,
            paging = PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ALBUM,
            nodeContentSupplier = { parentPath, page, _ -> getAlbumList("alphabeticalByName", parentPath, page) }
        ),
        Root(
            pattern = "allArtists",
            name = messages::allArtists,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = ARTIST,
            nodeContentSupplier = ::getAllArtists
        ),
        Root(
            pattern = "randomTracks",
            name = messages::randomTracks,
            paging = ENDLESSLY_PAGEABLE,
            hasSubdirectories = false,
            isOwn = false,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getRandomTracks
        ),
        Root(
            pattern = "genres",
            name = messages::genres,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = NODE,
            nodeContentSupplier = ::getGenres
        ),

        Search(
            pattern = "searchTracks",
            name = messages::tracksSearch,
            hasSubdirectories = false,
            contentNodeType = TRACK,
            nodeContentSupplier = ::searchTracks
        ),
        Search(
            pattern = "searchAlbums",
            name = messages::albumsSearch,
            hasSubdirectories = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::searchAlbums
        ),
        Search(
            pattern = "searchArtists",
            name = messages::artistsSearch,
            hasSubdirectories = true,
            contentNodeType = ARTIST,
            nodeContentSupplier = ::searchArtists
        ),

        Directory(
            pattern = "*/genre-<genre>",
            paging = PAGEABLE,
            hasSubdirectories = false,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getGenreTracks
        ),

        Track(
            pattern = "*/track-<trackId>",
            relatedPaths = ::getTrackRelatedPaths,
            like = LikeConfig(rootNodePath("starredTracks"), ::likeTrack),
            events = listOf(
                EventConfig(TrackStart::class) { _, _ -> murglar.scrobble(to(), submission = false) },
                EventConfig(TrackEnd::class) { event, _ -> to<NavidromeTrack>().handleEndEvent(event.endTimeMs) }
            ),
            nodeSupplier = ::getTrack
        ),

        MappedEntity(
            pattern = "*/album-<albumId>",
            paging = NON_PAGEABLE,
            hasSubdirectories = false,
            type = ALBUM,
            contentNodeType = TRACK,
            relatedPaths = ::getAlbumRelatedPaths,
            like = LikeConfig(rootNodePath("starredAlbums"), ::likeAlbum),
            nodeSupplier = ::getAlbum,
            nodeContentSupplier = ::getAlbumTracks
        ),

        MappedEntity(
            pattern = "*/artist-<artistId>",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            type = ARTIST,
            contentNodeType = ALBUM,
            like = LikeConfig(rootNodePath("starredArtists"), ::likeArtist),
            nodeSupplier = ::getArtist,
            nodeContentSupplier = ::getArtistAlbums
        ),

        MappedEntity(
            pattern = "*/playlist-<playlistId>",
            paging = NON_PAGEABLE,
            hasSubdirectories = false,
            type = PLAYLIST,
            contentNodeType = TRACK,
            nodeSupplier = ::getPlaylist,
            nodeContentSupplier = ::getPlaylistTracks
        ),
    )


    private suspend fun getStarredTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getStarredTracks().convertTracks(parentPath)

    private suspend fun getStarredAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getStarredAlbums().convertAlbums(parentPath)

    private suspend fun getStarredArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getStarredArtists().convertArtists(parentPath)

    private suspend fun getPlaylists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getPlaylists().convertPlaylists(parentPath)

    private suspend fun getAlbumList(type: String, parentPath: Path, page: Int?) =
        murglar.getAlbumList(type, page!!).convertAlbums(parentPath)

    private suspend fun getAllArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getAllArtists().convertArtists(parentPath)

    private suspend fun getRandomTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getRandomTracks().convertTracks(parentPath)

    private suspend fun getGenres(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getGenres().map { (genre, songCount) ->
            val nodeId = "genre-${genre.urlEncode()}"
            node(DirectoryNode(nodeId, genre, "$songCount ♫"), parentPath.child(nodeId))
        }

    private suspend fun getGenreTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getGenreTracks(params["genre"]!!.urlDecode(), page!!).convertTracks(parentPath)

    private suspend fun searchTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchTracks(params.getQuery(), page!!).convertTracks(parentPath)

    private suspend fun searchAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchAlbums(params.getQuery(), page!!).convertAlbums(parentPath)

    private suspend fun searchArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchArtists(params.getQuery(), page!!).convertArtists(parentPath)

    private suspend fun getAlbumTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getAlbumTracks(params["albumId"]!!).convertTracks(parentPath)

    private suspend fun getArtistAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistAlbums(params["artistId"]!!).convertAlbums(parentPath)

    private suspend fun getPlaylistTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getPlaylistTracks(params["playlistId"]!!).convertTracks(parentPath)

    private suspend fun getTrack(parentPath: Path, params: Map<String, String>) =
        murglar.getTrack(params["trackId"]!!).convertTrack(parentPath)

    private suspend fun getAlbum(parentPath: Path, params: Map<String, String>) =
        murglar.getAlbum(params["albumId"]!!).convertAlbum(parentPath)

    private suspend fun getArtist(parentPath: Path, params: Map<String, String>) =
        murglar.getArtist(params["artistId"]!!).convertArtist(parentPath)

    private suspend fun getPlaylist(parentPath: Path, params: Map<String, String>) =
        murglar.getPlaylist(params["playlistId"]!!).convertPlaylist(parentPath)


    private fun getTrackRelatedPaths(node: Node): List<NamedPath> {
        val track = node.to<NavidromeTrack>()
        val paths = mutableListOf<NamedPath>()

        if (track.hasAlbum)
            paths += NamedPath(track.albumName!!, ALBUM, unmappedPath().child("album-${track.albumId}"))

        track.artistIds.zip(track.artistNames)
            .filter { it.first.isNotEmpty() }
            .forEach { (id, name) -> paths += NamedPath(name, ARTIST, unmappedPath().child("artist-$id")) }

        return paths
    }

    private fun getAlbumRelatedPaths(node: Node): List<NamedPath> {
        val album = node.to<NavidromeAlbum>()
        return album.artistIds.zip(album.artistNames)
            .filter { it.first.isNotEmpty() }
            .map { (id, name) -> NamedPath(name, ARTIST, unmappedPath().child("artist-$id")) }
    }


    private suspend fun likeTrack(node: Node, like: Boolean) = murglar.star("id", node.nodeId, like)

    private suspend fun likeAlbum(node: Node, like: Boolean) = murglar.star("albumId", node.nodeId, like)

    private suspend fun likeArtist(node: Node, like: Boolean) = murglar.star("artistId", node.nodeId, like)


    private suspend fun NavidromeTrack.handleEndEvent(endTimeMs: Int) {
        val threshold = minOf(durationMs / 2, SCROBBLE_MAX_MS.toLong())
        if (endTimeMs >= threshold)
            murglar.scrobble(this, submission = true)
    }
}
