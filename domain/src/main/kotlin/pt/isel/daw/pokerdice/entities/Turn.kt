package pt.isel.daw.pokerdice.entities

data class Turn(
    val turnId: Int,
    val roundId: Int,
    val userId: Int,
    val number: Int,
    val state: String = "active",
    val rollCount: Int = 0,
)
