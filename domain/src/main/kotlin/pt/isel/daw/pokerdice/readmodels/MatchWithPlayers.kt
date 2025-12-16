package pt.isel.daw.pokerdice.readmodels

import pt.isel.daw.pokerdice.entities.Match

data class MatchWithPlayers(
    val matchId: Int,
    val lobbyId: Int,
    val startingPlayerUserId: Int,
    val currentRoundId: Int?,
    val status: String,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val players: List<PlayerInfo>,
)

fun matchWithPlayersToOutput(
    match: Match,
    player: List<PlayerInfo>,
): MatchWithPlayers =
    MatchWithPlayers(
        matchId = match.matchId,
        lobbyId = match.lobbyId,
        startingPlayerUserId = match.startingPlayerUserId,
        currentRoundId = match.currentRoundId,
        status = match.status.name,
        createdAt = match.createdAt,
        finishedAt = match.finishedAt,
        players = player.map { playerToOutput(it) },
    )

fun playerToOutput(player: PlayerInfo): PlayerInfo =
    PlayerInfo(
        player.userId,
        player.name,
        player.email,
        player.balance,
    )
