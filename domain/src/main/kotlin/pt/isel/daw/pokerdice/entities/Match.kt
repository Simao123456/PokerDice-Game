package pt.isel.daw.pokerdice.entities

import pt.isel.daw.pokerdice.enums.MatchStatus

data class Match(
    val matchId: Int,
    val lobbyId: Int,
    val startingPlayerUserId: Int,
    val currentRoundId: Int?,
    val status: MatchStatus = MatchStatus.ONGOING,
    val createdAt: Long,
    val finishedAt: Long? = null,
)
