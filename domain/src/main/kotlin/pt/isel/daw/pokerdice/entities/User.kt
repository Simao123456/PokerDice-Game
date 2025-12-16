package pt.isel.daw.pokerdice.entities

import pt.isel.daw.pokerdice.valueobjects.PasswordValidationInfo

data class User(
    val userId: Int,
    val name: String,
    val password: PasswordValidationInfo,
    val email: String,
    val balance: Double = 0.0,
)
