package pt.isel.daw.pokerdice.readmodels

import pt.isel.daw.pokerdice.enums.LobbyStatus

data class LobbyDetails(
    val lobbyId: Int,
    val name: String,
    val description: String?,
    val hostId: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val maxRounds: Int,
    val timeoutSeconds: Int,
    val status: LobbyStatus,
    val createdAt: Long,
    val playerCount: Int,
)

