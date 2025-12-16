package pt.isel.daw.pokerdice

import kotlin.time.Duration

/**
 * Configuration for the user's domain.
 * @param tokenSizeInBytes The size of the token in bytes.
 * @param tokenTtl The time-to-live of the token.
 * @param tokenRollingTtl The rolling time-to-live of the token.
 */
data class UsersDomainConfig(
    val tokenSizeInBytes: Int,
    val tokenTtl: Duration,
    val tokenRollingTtl: Duration,
    val maxTokensPerUser: Int,
    val minPasswordLength: Int,
    val minUsernameLength: Int,
    val requireDigits: Boolean,
    val requireLowercase: Boolean,
    val requireUppercase: Boolean,
    val requireInvitation: Boolean,
) {
    init {
        require(tokenSizeInBytes > 0) { "Token size must be positive" }
        require(tokenTtl.isPositive()) { "Token TTL must be positive" }
        require(tokenRollingTtl.isPositive()) { "Token rolling TTL must be positive" }
        require(maxTokensPerUser > 0) { "Max tokens per user must be positive" }
        require(minPasswordLength > 0) { "Min password length must be positive" }
        require(minUsernameLength > 0) { "Min username length must be positive" }
    }
}
