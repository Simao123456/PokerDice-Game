package pt.isel.daw.pokerdice

data class RoundDomainConfig(
    val defaultBlindAmount: Double,
    val maxRollsPerTurn: Int,
    val numberOfDice: Int,
    val diceFaces: List<String>,
) {
    init {
        require(defaultBlindAmount > 0)
        require(maxRollsPerTurn in 1..5)
        require(numberOfDice == 5)
        require(diceFaces.size == 6)
    }
}
