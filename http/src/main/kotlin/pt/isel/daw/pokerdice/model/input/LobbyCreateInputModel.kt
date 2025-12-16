package pt.isel.daw.pokerdice.model.input

data class LobbyCreateInputModel(
    val name: String,
    val description: String? = null,
    val maxPlayers: Int,
    val maxRounds: Int,
    val timeoutSeconds: Int,
)
