package com.badmanners.murglar.lib.navidrome.localization

import com.badmanners.murglar.lib.core.localization.Messages


interface NavidromeMessages : Messages {
    val serverAddress: String
    val serverAddressHint: String
    val findServer: String
    val findServerSummary: String
    val searchingServer: String
    val serverFound: String
    val serverNotFound: String
    val autoDiscovery: String
    val autoDiscoverySummary: String
    val original: String
    val starredTracks: String
    val starredAlbums: String
    val starredArtists: String
    val allArtists: String
    val newestAlbums: String
    val recentAlbums: String
    val frequentAlbums: String
    val frequentTracks: String
    val randomAlbums: String
    val alphabeticalAlbums: String
    val allTracks: String
    val randomTracks: String
    val genres: String
}
