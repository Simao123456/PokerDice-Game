package pt.isel.daw.pokerdice.readmodels

import pt.isel.daw.pokerdice.entities.Lobby
import pt.isel.daw.pokerdice.enums.LobbyStatus

data class LobbyWithPlayers(
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
    val players: List<PlayerInfo>,
)

fun lobbyWithPlayersToOutput(
    lobby: Lobby,
    players: List<PlayerInfo>,
): LobbyWithPlayers =
    LobbyWithPlayers(
        lobbyId = lobby.lobbyId,
        name = lobby.name,
        description = lobby.description,
        hostId = lobby.hostId,
        minPlayers = lobby.minPlayers,
        maxPlayers = lobby.maxPlayers,
        maxRounds = lobby.maxRounds,
        timeoutSeconds = lobby.timeoutSeconds,
        status = lobby.status,
        createdAt = lobby.createdAt,
        players = players,
    )
