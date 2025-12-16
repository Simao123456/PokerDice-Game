package pt.isel.daw.pokerdice.lobby

import kotlinx.datetime.Clock
import org.springframework.stereotype.Component
import pt.isel.daw.pokerdice.LobbyDomain
import pt.isel.daw.pokerdice.TransactionManager
import pt.isel.daw.pokerdice.enums.LobbyStatus
import pt.isel.daw.pokerdice.match.MatchService
import pt.isel.daw.pokerdice.readmodels.LobbyDetails
import pt.isel.daw.pokerdice.readmodels.LobbyWithPlayers
import pt.isel.daw.pokerdice.readmodels.lobbyWithPlayersToOutput
import pt.isel.daw.pokerdice.utils.*

typealias LobbyCreationResult = Either<LobbyCreationError, LobbyDetails>
typealias LobbyFetchResult = Either<LobbyFetchError, LobbyWithPlayers>

sealed class JoinResult {
    data class Joined(
        val lobbyId: Int,
    ) : JoinResult()

    data class MatchStarted(
        val lobbyId: Int,
        val matchId: Int,
    ) : JoinResult()
}
typealias JoinLobbyResult = Either<LobbyJoinError, JoinResult>

@Component
class LobbyService(
    private val transactionManager: TransactionManager,
    private val clock: Clock,
    private val lobbyDomain: LobbyDomain,
    private val matchService: MatchService,
) {
    fun createLobby(
        name: String,
        description: String?,
        maxPlayers: Int,
        maxRounds: Int,
        timeoutSeconds: Int,
        hostId: Int,
    ): LobbyCreationResult {
        val cleanName = name.trim()
        if (!lobbyDomain.isNameValid(cleanName)) return failure(LobbyCreationError.InvalidName)
        if (!lobbyDomain.arePlayersValid(maxPlayers)) return failure(LobbyCreationError.InvalidMaxPlayers)
        if (!lobbyDomain.areRoundsValid(maxRounds)) return failure(LobbyCreationError.InvalidMaxRounds)
        if (!lobbyDomain.isTimeoutValid(timeoutSeconds)) return failure(LobbyCreationError.InvalidTimeoutSeconds)

        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()

        return transactionManager.run { tm ->
            val lobbyId =
                tm.lobbyRepository.createLobby(
                    name = cleanName,
                    description = description,
                    hostId = hostId,
                    maxPlayers = maxPlayers,
                    maxRounds = maxRounds,
                    minPlayers = lobbyDomain.getMinPlayers(),
                    timeoutSeconds = timeoutSeconds,
                    status = LobbyStatus.WAITING,
                    createdAt = nowMs,
                )

            tm.lobbyRepository.joinLobby(lobbyId, hostId, now)

            success(
                LobbyDetails(
                    lobbyId = lobbyId,
                    name = cleanName,
                    description = description,
                    hostId = hostId,
                    maxPlayers = maxPlayers,
                    maxRounds = maxRounds,
                    timeoutSeconds = timeoutSeconds,
                    createdAt = nowMs,
                    status = LobbyStatus.WAITING,
                    playerCount = 1,
                    minPlayers = lobbyDomain.getMinPlayers(),
                ),
            )
        }
    }

    fun getLobbyById(lid: Int): LobbyFetchResult =
        transactionManager.run { tm ->
            val lobby =
                tm.lobbyRepository.getLobbyById(lid)
                    ?: return@run failure(LobbyFetchError.LobbyNotFound)

            val players = tm.lobbyRepository.getLobbyPlayers(lid)
            val dto = lobbyWithPlayersToOutput(lobby, players)
            success(dto)
        }

    fun getAvailableLobbies(params: Map<String, List<String?>>): List<LobbyDetails> =
        transactionManager.run { tm ->
            val acceptedSorts = listOf("asc", "desc")
            val sort: Sort? = getSort(params, acceptedSorts)

            val acceptedFilters = listOf("contains")
            val filters: List<Filter> = getFilters(params, acceptedFilters)

            val limit = params["limit"]?.get(0)?.toUIntOrNull()
            val skip = params["skip"]?.get(0)?.toUIntOrNull()
            tm.lobbyRepository.listAvailableLobbies(filters, sort, limit, skip)
        }

    fun joinLobby(
        lobbyId: Int,
        userId: Int,
    ): JoinLobbyResult =
        transactionManager.run { tx ->
            val lobby =
                tx.lobbyRepository.getLobbyById(lobbyId)
                    ?: return@run failure(LobbyJoinError.LobbyNotFound)

            if (lobby.status == LobbyStatus.ONGOING) {
                return@run failure(LobbyJoinError.LobbyAlreadyStarted)
            }

            val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
            if (currentPlayers >= lobby.maxPlayers) {
                return@run failure(LobbyJoinError.LobbyFull)
            }

            if (tx.lobbyRepository.isUserInLobby(lobbyId, userId)) {
                return@run failure(LobbyJoinError.UserAlreadyInLobby)
            }

            val id = tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
            if (id.isEmpty()) {
                return@run failure(LobbyJoinError.LobbyJoinGenericError)
            }

            val newPlayerCount = currentPlayers + 1
            if (newPlayerCount < lobby.maxPlayers) {
                return@run success(JoinResult.Joined(lobbyId))
            }

            when (val res = matchService.createMatchFromLobby(lobby, tx)) {
                is Success -> success(JoinResult.MatchStarted(lobbyId, res.value))
                is Failure -> failure(LobbyJoinError.MatchStartError)
            }
        }

    fun leaveLobby(
        lobbyId: Int,
        userId: Int,
    ): Either<LobbyLeaveError, Unit> =
        transactionManager.run { tm ->
            val lobby =
                tm.lobbyRepository.getLobbyById(lobbyId)
                    ?: return@run failure(LobbyLeaveError.LobbyNotFound)

            if (lobby.hostId == userId) {
                return@run if (tm.lobbyRepository.deleteLobby(lobbyId)) {
                    success(Unit)
                } else {
                    failure(LobbyLeaveError.LobbyNotFound)
                }
            }

            return@run if (tm.lobbyRepository.leaveLobby(lobbyId, userId)) {
                success(Unit)
            } else {
                failure(LobbyLeaveError.UserNotInLobby)
            }
        }
}
