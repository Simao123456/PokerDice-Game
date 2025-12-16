package pt.isel.daw.pokerdice.model.output

import pt.isel.daw.pokerdice.readmodels.PlayerInfo
import pt.isel.daw.pokerdice.readmodels.RoundWithDetails
import pt.isel.daw.pokerdice.readmodels.playerToOutput

data class RoundOutputModel(
    val roundId: Int,
    val matchId: Int,
    val number: Int,
    val blind: Double,
    val pot: Double,
    val winnerUserId: Int?,
    val hands: List<HandOutputModel>,
    val currentTurn: TurnOutputModel?,
    val players: List<PlayerInfo>
)

fun roundWithDetailsToOutput(roundWithDetails: RoundWithDetails): RoundOutputModel =
    RoundOutputModel(
        roundId = roundWithDetails.round.roundId,
        matchId = roundWithDetails.round.matchId,
        number = roundWithDetails.round.number,
        blind = roundWithDetails.round.blind,
        pot = roundWithDetails.round.pot,
        winnerUserId = roundWithDetails.round.winnerUserId,
        hands = roundWithDetails.hands.map { handToOutput(it) },
        currentTurn = roundWithDetails.currentTurn?.let { turnToOutput(it) },
        players = roundWithDetails.players.map { playerToOutput(it) }
    )