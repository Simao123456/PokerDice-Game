package pt.isel.daw.pokerdice

import pt.isel.daw.pokerdice.entities.*
import pt.isel.daw.pokerdice.enums.MatchStatus
import pt.isel.daw.pokerdice.enums.TurnState
import pt.isel.daw.pokerdice.readmodels.PlayerInfo

interface MatchRepository {
    fun getMatchById(matchId: Int): Match?

    fun listRounds(matchId: Int): List<Round>

    fun currentRound(matchId: Int): Round?

    fun roundDetails(
        matchId: Int,
        roundId: Int,
    ): Round?

    fun currentTurn(matchId: Int): Turn?

    fun getMatchPlayers(matchId: Int): List<PlayerInfo>

    fun handHistory(matchId: Int): List<Hand>

    fun getRoundPlayers(roundId: Int): List<PlayerInfo>

    fun getRoundHands(roundId: Int): List<Hand>

    fun getCurrentTurnForRound(roundId: Int): Turn?

    fun timeline(matchId: Int): List<Turn>

    fun createRoll(roll: Roll): Roll

    fun getLastRollForTurn(turnId: Int): Roll?

    fun incrementTurnRollCount(turnId: Int)

    fun createMatchFromLobby(lobby: Lobby): Int

    fun createRound(
        matchId: Int,
        roundNumber: Int,
        blind: Double,
    ): Int

    fun createTurn(
        roundId: Int,
        userId: Int,
        turnNumber: Int,
    ): Int

    fun updateCurrentRound(
        matchId: Int,
        roundId: Int,
    )

    fun updateTurnState(
        turnId: Int,
        state: TurnState,
    )

    fun getLobbyPlayers(lobbyId: Int): List<PlayerInfo>

    fun getMatchByLobbyId(lobbyId: Int): Match?

    fun updateRoundPot(
        roundId: Int,
        amount: Double,
    )

    fun createHand(
        roundId: Int,
        userId: Int,
        faces: String,
        rank: Int,
        tieBreakerKey: Int,
    ): Int

    fun setRoundWinner(
        roundId: Int,
        userId: Int,
    )

    fun getPreviousStartingPlayer(
        matchId: Int,
        roundNumber: Int,
    ): Int

    fun updateMatchStatus(
        matchId: Int,
        status: MatchStatus,
    )

    fun setMatchFinishedAt(
        matchId: Int,
        finishedAt: Long,
    )
}
