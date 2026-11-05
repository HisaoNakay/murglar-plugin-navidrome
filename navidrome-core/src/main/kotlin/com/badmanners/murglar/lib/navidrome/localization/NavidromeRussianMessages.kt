package com.badmanners.murglar.lib.navidrome.localization

import com.badmanners.murglar.lib.core.localization.RussianMessages


object NavidromeRussianMessages : RussianMessages(), NavidromeMessages {
    override val serviceName = "Navidrome"
    override val serverAddress = "Адрес сервера"
    override val serverAddressHint = "Например 192.168.1.10:4533 или http://host:4533"
    override val findServer = "Найти сервер в сети"
    override val findServerSummary = "Просканировать локальные сети устройства в поисках Navidrome"
    override val searchingServer = "Ищу сервер Navidrome…"
    override val serverFound = "Navidrome найден"
    override val serverNotFound = "Сервер Navidrome в локальных сетях не найден"
    override val autoDiscovery = "Автопоиск"
    override val autoDiscoverySummary = "Искать сервер в локальных сетях, если его адрес сменился"
    override val original = "Оригинал"
    override val starredTracks = "Избранные треки"
    override val starredAlbums = "Избранные альбомы"
    override val starredArtists = "Избранные исполнители"
    override val allArtists = "Все исполнители"
    override val newestAlbums = "Недавно добавленные"
    override val recentAlbums = "Недавно прослушанные"
    override val frequentAlbums = "Часто слушаемые альбомы"
    override val frequentTracks = "Часто слушаемые треки"
    override val randomAlbums = "Случайные альбомы"
    override val alphabeticalAlbums = "Все альбомы"
    override val allTracks = "Все треки"
    override val randomTracks = "Случайные треки"
    override val genres = "Жанры"
}
