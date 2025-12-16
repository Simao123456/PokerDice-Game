package pt.isel.daw.pokerdice

import kotlinx.datetime.Instant
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import pt.isel.daw.pokerdice.entities.Lobby
import pt.isel.daw.pokerdice.enums.LobbyStatus
import pt.isel.daw.pokerdice.readmodels.LobbyDetails
import pt.isel.daw.pokerdice.readmodels.PlayerInfo
import pt.isel.daw.pokerdice.utils.Filter
import pt.isel.daw.pokerdice.utils.Sort

class JdbiLobbyRepository(
    private val handle: Handle,
) : LobbyRepository {
    override fun createLobby(
        name: String,
        description: String?,
        hostId: Int,
        maxPlayers: Int,
        minPlayers: Int,
        maxRounds: Int,
        timeoutSeconds: Int,
        status: LobbyStatus,
        createdAt: Long,
    ): Int =
        handle
            .createUpdate(
                """
                INSERT INTO Lobby (
                    name,
                    description,
                    status,
                    host_id,
                    min_players,
                    max_players,
                    max_rounds,
                    timeout_seconds,
                    created_at
                )
                VALUES (
                    :name,
                    :description,
                    :status,          
                    :hostId,
                    :minPlayers,                   
                    :maxPlayers,
                    :maxRounds,
                    :timeoutSeconds,
                    :createdAt
                )
                RETURNING lobby_id
                """.trimIndent(),
            ).bind("name", name)
            .bind("description", description)
            .bind("status", status.name)
            .bind("hostId", hostId)
            .bind("maxPlayers", maxPlayers)
            .bind("minPlayers", minPlayers)
            .bind("maxRounds", maxRounds)
            .bind("timeoutSeconds", timeoutSeconds)
            .bind("createdAt", createdAt)
            .executeAndReturnGeneratedKeys("lobby_id")
            .mapTo<Int>()
            .one()

    override fun getLobbyPlayers(lobbyId: Int): List<PlayerInfo> =
        handle
            .createQuery(
                """
                SELECT
                  u.user_id   AS userId,
                  u.name      AS name,
                  u.email     AS email,
                  u.balance   AS balance
                FROM lobby_users lu
                JOIN users u ON u.user_id = lu.user_id
                WHERE lu.lobby_id = :id
                ORDER BY lu.joined_at
                """.trimIndent(),
            ).bind("id", lobbyId)
            .mapTo(PlayerInfo::class.java)
            .list()

    override fun getLobbyById(lobbyId: Int): Lobby? {
        val lobby = handle.createQuery("SELECT * FROM Lobby WHERE lobby_id = :id")
        return lobby.bind("id", lobbyId).mapTo<Lobby>().singleOrNull()
    }

    override fun listAvailableLobbies(
        filters: List<Filter>,
        sort: Sort?,
        limit: UInt?,
        skip: UInt?,
    ): List<LobbyDetails> {
        val base =
            StringBuilder(
                """
                SELECT
                  l.lobby_id,
                  l.name,
                  l.description,
                  l.host_id,
                  l.min_players,
                  l.max_players,
                  l.max_rounds,
                  l.timeout_seconds,
                  l.status,
                  l.created_at,
                  (SELECT COUNT(*) FROM Lobby_Users lu WHERE lu.lobby_id = l.lobby_id) AS player_count
                FROM Lobby l
                WHERE l.status = 'WAITING'
                """.trimIndent(),
            )

        val query =
            constructSimpleQueryString(
                baseQuery = base,
                columnName = "l.name",
                filters = filters,
                sort = sort,
                limit = limit,
                skip = skip,
                baseContainsWhere = true,
            )

        return handle
            .createQuery(query.toString())
            .mapTo<LobbyDetails>()
            .list()
    }

    override fun joinLobby(
        lobbyId: Int,
        userId: Int,
        time: Instant,
    ): String =
        handle
            .createUpdate(
                """
                INSERT INTO Lobby_Users (lobby_id, user_id, joined_at) VALUES (:lobbyId, :userId, :time)
                """.trimIndent(),
            ).bind("lobbyId", lobbyId)
            .bind("userId", userId)
            .bind("time", time.toEpochMilliseconds())
            .executeAndReturnGeneratedKeys("lobby_id")
            .mapTo<String>()
            .one()

    override fun getPlayerCount(lobbyId: Int): Int =
        handle
            .createQuery("SELECT COUNT(*) FROM Lobby_Users WHERE lobby_id = :lobbyId")
            .bind("lobbyId", lobbyId)
            .mapTo<Int>()
            .one()

    override fun isUserInLobby(
        lobbyId: Int,
        userId: Int,
    ): Boolean =
        handle
            .createQuery(
                """
            SELECT EXISTS(
                SELECT 1 FROM Lobby_Users 
                WHERE lobby_id = :lobbyId AND user_id = :userId
            )
        """,
            ).bind("lobbyId", lobbyId)
            .bind("userId", userId)
            .mapTo<Boolean>()
            .first()

    override fun leaveLobby(
        lobbyId: Int,
        userId: Int,
    ): Boolean =
        handle
            .createUpdate("DELETE FROM Lobby_Users WHERE lobby_id = :lobbyId AND user_id = :userId")
            .bind("lobbyId", lobbyId)
            .bind("userId", userId)
            .execute() > 0

    override fun deleteAllUsersFromLobby(lobbyId: Int): Int =
        handle
            .createUpdate(
                """DELETE FROM lobby_users WHERE lobby_id = :lobbyId""",
            ).bind("lobbyId", lobbyId)
            .execute()

    override fun deleteLobby(lobbyId: Int): Boolean =
        handle
            .createUpdate(
                """DELETE FROM lobby WHERE lobby_id = :lobbyId""",
            ).bind("lobbyId", lobbyId)
            .execute() > 0

    override fun updateLobbyStatus(
        lobbyId: Int,
        status: LobbyStatus,
    ) {
        handle
            .createUpdate("UPDATE Lobby SET status = :status WHERE lobby_id = :lobbyId")
            .bind("lobbyId", lobbyId)
            .bind("status", status.name)
            .execute()
    }
}
