package pt.isel.daw.pokerdice.readmodels

data class CreatedUserSession(
    val userId: Int,
    val username: String,
    val token: String,
)
