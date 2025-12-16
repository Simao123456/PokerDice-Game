package pt.isel.daw.pokerdice.security

import pt.isel.daw.pokerdice.entities.User

data class AuthenticatedUser(
    val user: User,
    val token: String,
)
