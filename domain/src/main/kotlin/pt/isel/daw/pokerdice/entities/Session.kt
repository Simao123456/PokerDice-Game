package pt.isel.daw.pokerdice.entities

import kotlinx.datetime.Instant
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo

data class Session(
    val sessionId: TokenValidationInfo,
    val userId: Int,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Long,
    val revoked: Boolean = false,
)
