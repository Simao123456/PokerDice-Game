package pt.isel.daw.pokerdice

data class LobbyDomainConfig(
    val nameMaxLength: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val minRounds: Int,
    val maxRounds: Int,
    val minTimeoutSeconds: Int,
    val maxTimeoutSeconds: Int,
) {
    init {
        require(nameMaxLength > 0)
        require(minPlayers > 0)
        require(maxPlayers >= minPlayers)
        require(maxRounds > 0)
        require(minRounds > 0)
        require(minTimeoutSeconds > 0)
        require(maxTimeoutSeconds <= 300)
    }
}
