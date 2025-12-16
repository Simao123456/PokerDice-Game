package pt.isel.daw.pokerdice.readmodels

import pt.isel.daw.pokerdice.entities.Hand
import pt.isel.daw.pokerdice.entities.Round
import pt.isel.daw.pokerdice.entities.Turn

data class RoundWithDetails(
    val round: Round,
    val hands: List<Hand>,
    val currentTurn: Turn?,
    val players: List<PlayerInfo>,
)
