package pt.isel.daw.pokerdice

import kotlinx.datetime.Instant
import pt.isel.daw.pokerdice.entities.Session
import pt.isel.daw.pokerdice.entities.User
import pt.isel.daw.pokerdice.utils.Filter
import pt.isel.daw.pokerdice.utils.Sort
import pt.isel.daw.pokerdice.valueobjects.PasswordValidationInfo
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo

interface UsersRepository {
    fun getAllUsers(
        filters: List<Filter> = emptyList(),
        sort: Sort? = null,
        limit: UInt? = null,
        skip: UInt? = null,
    ): List<User>

    fun storeUser(
        name: String,
        password: PasswordValidationInfo,
        email: String,
    ): Int

    fun getUserByName(name: String): User?

    fun getUserById(id: Int): User?

    fun isUserStoredByName(name: String): Boolean

    fun storeSession(
        session: Session,
        maxSessions: Int,
    )

    fun updateSessionLastUsed(
        sessionId: String,
        now: Long,
    )

    fun removeSession(sessionId: String): Boolean

    fun isTokenExpired(tokenValidationInfo: TokenValidationInfo): Boolean

    fun getSessionByTokenValidationInfo(tokenValidationInfo: TokenValidationInfo): Pair<User, Session>?

    fun updateTokenLastUsed(
        session: Session,
        now: Instant,
    ): Int

    fun removeTokenByValidationInfo(
        token: TokenValidationInfo,
        now: Instant,
    ): Int

    fun updateUserBalance(
        userId: Int,
        newBalance: Double,
    )
}
