package pt.isel.daw.pokerdice.entities

data class Roll(
    val rollId: Int,
    val turnId: Int,
    val createdAt: Long,
    val heldMask: String,
    val diceValues: String,
)
