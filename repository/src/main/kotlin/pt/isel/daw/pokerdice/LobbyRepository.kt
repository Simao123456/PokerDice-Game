package pt.isel.daw.pokerdice

import kotlinx.datetime.Instant
import pt.isel.daw.pokerdice.entities.Lobby
import pt.isel.daw.pokerdice.enums.LobbyStatus
import pt.isel.daw.pokerdice.readmodels.LobbyDetails
import pt.isel.daw.pokerdice.readmodels.PlayerInfo
import pt.isel.daw.pokerdice.utils.Filter
import pt.isel.daw.pokerdice.utils.Sort

interface LobbyRepository {
    fun createLobby(
        name: String,
        description: String?,
        hostId: Int,
        maxPlayers: Int,
        minPlayers: Int,
        maxRounds: Int,
        timeoutSeconds: Int,
        status: LobbyStatus,
        createdAt: Long,
    ): Int

    fun getLobbyById(lobbyId: Int): Lobby?

    fun getLobbyPlayers(lobbyId: Int): List<PlayerInfo>

    fun listAvailableLobbies(
        filters: List<Filter> = emptyList(),
        sort: Sort? = null,
        limit: UInt? = null,
        skip: UInt? = null,
    ): List<LobbyDetails>

    fun getPlayerCount(lobbyId: Int): Int

    fun isUserInLobby(
        lobbyId: Int,
        userId: Int,
    ): Boolean

    fun joinLobby(
        lobbyId: Int,
        userId: Int,
        time: Instant,
    ): String

    fun leaveLobby(
        lobbyId: Int,
        userId: Int,
    ): Boolean

    fun deleteAllUsersFromLobby(lobbyId: Int): Int

    fun deleteLobby(lobbyId: Int): Boolean

    fun updateLobbyStatus(
        lobbyId: Int,
        status: LobbyStatus,
    )


}
