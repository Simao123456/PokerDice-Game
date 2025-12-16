package pt.isel.daw.pokerdice.entities

data class Round(
    val roundId: Int,
    val matchId: Int,
    val number: Int,
    val blind: Double = 1.0,
    val pot: Double = 0.0,
    val winnerUserId: Int? = null,
)
