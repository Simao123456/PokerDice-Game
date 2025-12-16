package pt.isel.daw.pokerdice

import kotlinx.datetime.Clock
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import pt.isel.daw.pokerdice.entities.Session
import pt.isel.daw.pokerdice.valueobjects.PasswordValidationInfo
import pt.isel.daw.pokerdice.valueobjects.TokenEncoder
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo
import java.security.SecureRandom
import java.util.*

@Component
class UsersDomain(
    private val passwordEncoder: PasswordEncoder,
    private val tokenEncoder: TokenEncoder,
    private val config: UsersDomainConfig,
) {
    fun generateTokenValue(): String =
        ByteArray(config.tokenSizeInBytes).let { byteArray ->
            SecureRandom.getInstanceStrong().nextBytes(byteArray)
            Base64.getUrlEncoder().encodeToString(byteArray)
        }

    fun createPasswordValidationInformation(password: String) =
        PasswordValidationInfo(
            validationInfo = passwordEncoder.encode(password),
        )

    fun validatePassword(
        password: String,
        validationInfo: PasswordValidationInfo,
    ) = passwordEncoder.matches(
        password,
        validationInfo.validationInfo,
    )

    fun createTokenValidationInformation(token: String): TokenValidationInfo = tokenEncoder.createValidationInformation(token)

    fun isSafePassword(password: String): Boolean {
        if (password.length < config.minPasswordLength) return false
        if (config.requireDigits && !password.any { it.isDigit() }) return false
        if (config.requireLowercase && !password.any { it.isLowerCase() }) return false
        if (config.requireUppercase && !password.any { it.isUpperCase() }) return false
        return true
    }

    fun canBeToken(token: String): Boolean =
        try {
            Base64
                .getUrlDecoder()
                .decode(token)
                .size == config.tokenSizeInBytes
        } catch (ex: IllegalArgumentException) {
            false
        }

    fun isTokenTimeValid(
        clock: Clock,
        session: Session,
    ): Boolean {
        val now = clock.now()
        return session.createdAt <= now &&
            (now - session.createdAt) <= config.tokenTtl &&
            (now - session.lastUsedAt) <= config.tokenRollingTtl
    }

    fun isValidUsername(username: String): Boolean = username.length >= config.minUsernameLength

    fun isInvitationRequired(): Boolean = config.requireInvitation

    val maxNumberOfTokensPerUser = config.maxTokensPerUser
}
