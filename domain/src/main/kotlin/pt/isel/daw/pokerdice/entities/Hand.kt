package pt.isel.daw.pokerdice.entities

data class Hand(
    val handId: Int,
    val roundId: Int,
    val userId: Int,
    val faces: String,
    val rank: Int,
    val tieBreakerKey: Int? = null,
) {
    fun isBetterThan(other: Hand): Boolean = rank > other.rank || (rank == other.rank && (tieBreakerKey ?: 0) > (other.tieBreakerKey ?: 0))
}
