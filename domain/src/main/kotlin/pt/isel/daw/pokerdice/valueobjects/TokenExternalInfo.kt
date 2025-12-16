package pt.isel.daw.pokerdice.valueobjects

import java.time.Instant

data class TokenExternalInfo(
    val tokenValue: String,
    val tokenExpiration: Instant,
)
