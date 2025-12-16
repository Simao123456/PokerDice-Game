package pt.isel.daw.pokerdice.security

import org.springframework.stereotype.Component
import pt.isel.daw.pokerdice.user.UserService

@Component
class RequestTokenProcessor(
    val usersService: UserService,
) {
    fun processAuthorizationHeaderValue(
        authorizationValue: String?,
        headerName: String,
    ): AuthenticatedUser? {
        if (authorizationValue == null) return null

        return if (headerName == NAME_AUTHORIZATION_HEADER) {
            val parts = authorizationValue.trim().split(" ", limit = 2)
            if (parts.size != 2 || parts[0].lowercase() != AUTH_SCHEME) return null
            buildUser(parts[1])
        } else if (headerName == NAME_COOKIE_HEADER) {
            val token =
                authorizationValue
                    .split(";")
                    .map { it.trim() }
                    .firstOrNull {
                        it.startsWith("$AUTH_COOKIE_NAME=", ignoreCase = false)
                    }?.substringAfter("=")
                    ?.takeIf { it.isNotBlank() }

            token?.let { buildUser(it) }
        } else {
            null
        }
    }

    private fun buildUser(token: String): AuthenticatedUser? = usersService.getUserByToken(token)?.let { AuthenticatedUser(it, token) }

    companion object {
        const val AUTH_SCHEME = "bearer"
        const val NAME_AUTHORIZATION_HEADER = "Authorization"
        const val NAME_COOKIE_HEADER = "Cookie"
        const val AUTH_COOKIE_NAME = "AuthCookie"
    }
}
