package pt.isel.daw.pokerdice.model.output

data class UserSessionOutputModel(
    val userId: Int,
    val username: String,
    val token: String
)