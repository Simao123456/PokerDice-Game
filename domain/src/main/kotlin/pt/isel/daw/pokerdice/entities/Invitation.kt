package pt.isel.daw.pokerdice.entities

data class Invitation(
    val code: String,
    val isActive: Boolean = true,
)
