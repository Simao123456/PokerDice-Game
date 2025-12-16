package pt.isel.daw.pokerdice.entities

import pt.isel.daw.pokerdice.enums.LobbyStatus

data class Lobby(
    val lobbyId: Int,
    val name: String,
    val description: String?,
    val hostId: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val maxRounds: Int,
    val timeoutSeconds: Int,
    val status: LobbyStatus = LobbyStatus.ONGOING,
    val createdAt: Long,
)
