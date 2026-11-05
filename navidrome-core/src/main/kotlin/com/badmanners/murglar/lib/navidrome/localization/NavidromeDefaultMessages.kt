package com.badmanners.murglar.lib.navidrome.localization

import com.badmanners.murglar.lib.core.localization.DefaultMessages


object NavidromeDefaultMessages : DefaultMessages(), NavidromeMessages {
    override val serviceName = "Navidrome"
    override val serverAddress = "Server address"
    override val serverAddressHint = "For example 192.168.1.10:4533 or http://host:4533"
    override val findServer = "Find server in network"
    override val findServerSummary = "Scan local networks of this device for Navidrome"
    override val searchingServer = "Searching for Navidrome server…"
    override val serverFound = "Navidrome found"
    override val serverNotFound = "Navidrome server not found in local networks"
    override val autoDiscovery = "Auto-discovery"
    override val autoDiscoverySummary = "Search the server in local networks when its address changes"
    override val original = "Original"
    override val starredTracks = "Starred tracks"
    override val starredAlbums = "Starred albums"
    override val starredArtists = "Starred artists"
    override val allArtists = "All artists"
    override val newestAlbums = "Recently added"
    override val recentAlbums = "Recently played"
    override val frequentAlbums = "Most played albums"
    override val frequentTracks = "Most played tracks"
    override val randomAlbums = "Random albums"
    override val alphabeticalAlbums = "All albums"
    override val allTracks = "All tracks"
    override val randomTracks = "Random tracks"
    override val genres = "Genres"
}
