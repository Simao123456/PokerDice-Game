package pt.isel.daw.pokerdice.model.input

data class UserCreateInputModel(
    val name: String,
    val password: String,
    val email: String,
    val invitationCode: String? = null,
)
