package pt.isel.daw.pokerdice

import kotlinx.datetime.Instant
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import org.slf4j.LoggerFactory
import pt.isel.daw.pokerdice.entities.Session
import pt.isel.daw.pokerdice.entities.User
import pt.isel.daw.pokerdice.utils.Filter
import pt.isel.daw.pokerdice.utils.Sort
import pt.isel.daw.pokerdice.valueobjects.PasswordValidationInfo
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo

class JdbiUserRepository(
    private val handle: Handle,
) : UsersRepository {
    override fun getAllUsers(
        filters: List<Filter>,
        sort: Sort?,
        limit: UInt?,
        skip: UInt?,
    ): List<User> {
        val baseQuery = StringBuilder("select * from users")

        val query = constructSimpleQueryString(baseQuery, "name", filters, sort, limit, skip)

        logger.info("Query: {}", query)

        return handle
            .createQuery(query.toString())
            .mapTo<User>()
            .list()
    }

    override fun storeUser(
        name: String,
        password: PasswordValidationInfo,
        email: String,
    ): Int =
        handle
            .createUpdate(
                """
                INSERT INTO Users (name, password, email) VALUES (:name, :password, :email)
                """,
            ).bind("name", name)
            .bind("password", password.validationInfo)
            .bind("email", email)
            .executeAndReturnGeneratedKeys()
            .mapTo<Int>()
            .one()

    override fun getUserByName(name: String): User? =
        handle
            .createQuery("select * from users where name = :name")
            .bind("name", name)
            .mapTo<User>()
            .singleOrNull()

    override fun getUserById(id: Int): User? =
        handle
            .createQuery("select * from Users where user_id = :user_id")
            .bind("user_id", id)
            .mapTo<User>()
            .singleOrNull()

    override fun isUserStoredByName(name: String): Boolean =
        handle
            .createQuery("select count(*) from Users where name = :name")
            .bind("name", name)
            .mapTo<Int>()
            .single() > 0

    override fun storeSession(
        session: Session,
        maxSessions: Int,
    ) {
        val deletions =
            handle
                .createUpdate(
                    """
                    update sessions set revoked = true
                    where user_id  = :user_id  and revoked = false
                      and session_id in (
                        select session_id from sessions where user_id = :user_id
                            order by last_used_at offset :offset
                      );  
                    """.trimIndent(),
                ).bind("user_id", session.userId)
                .bind("offset", maxSessions - 1)
                .execute()

        logger.info("{} tokens deleted when creating new token", deletions)

        handle
            .createUpdate(
                """
        INSERT INTO Sessions (session_id, user_id, created_at, last_used_at, expires_at, revoked)
        VALUES (:sessionId, :userId, :createdAt, :lastUsedAt, :expiresAt, :revoked)
    """,
            ).bind("sessionId", session.sessionId.validationInfo)
            .bind("userId", session.userId)
            .bind("createdAt", session.createdAt.toEpochMilliseconds())
            .bind("lastUsedAt", session.lastUsedAt.toEpochMilliseconds())
            .bind("expiresAt", session.expiresAt)
            .bind("revoked", session.revoked)
            .execute()
    }

    override fun isTokenExpired(tokenValidationInfo: TokenValidationInfo): Boolean =
        handle
            .createQuery(
                """
            SELECT 
                revoked = true OR 
                expires_at < :currentTime 
            FROM Sessions 
            WHERE session_id = :token
        """,
            ).bind("token", tokenValidationInfo.validationInfo)
            .bind("currentTime", System.currentTimeMillis())
            .mapTo<Boolean>()
            .singleOrNull() ?: true

    override fun updateTokenLastUsed(
        session: Session,
        now: Instant,
    ): Int =
        handle
            .createUpdate(
                """
            UPDATE Sessions 
            SET last_used_at = :now
            WHERE session_id = :sessionId
        """,
            ).bind("now", now.toEpochMilliseconds())
            .bind("sessionId", session.sessionId.validationInfo)
            .execute()

    override fun getSessionByTokenValidationInfo(tokenValidationInfo: TokenValidationInfo): Pair<User, Session>? =
        handle
            .createQuery(
                """
            SELECT 
                u.user_id as user_user_id, 
                u.name, 
                u.password, 
                u.email, 
                u.balance,
                s.session_id, 
                s.user_id as session_user_id, 
                s.created_at, 
                s.last_used_at, 
                s.expires_at, 
                s.revoked
            FROM Users u
            JOIN Sessions s ON u.user_id = s.user_id
            WHERE s.session_id = :token
        """,
            ).bind("token", tokenValidationInfo.validationInfo)
            .mapTo<UserAndSessionModel>()
            .singleOrNull()
            ?.userAndSession

    override fun removeTokenByValidationInfo(
        token: TokenValidationInfo,
        now: Instant,
    ): Int =
        handle
            .createUpdate(
                """
            UPDATE Sessions 
            SET revoked = true, last_used_at = :now
            WHERE session_id = :sessionId
        """,
            ).bind("sessionId", token.validationInfo)
            .bind("now", now.toEpochMilliseconds())
            .execute()

    override fun updateUserBalance(
        userId: Int,
        newBalance: Double,
    ) {
        handle
            .createUpdate("UPDATE Users SET balance = :balance WHERE user_id = :userId")
            .bind("balance", newBalance)
            .bind("userId", userId)
            .execute()
    }

    override fun updateSessionLastUsed(
        sessionId: String,
        now: Long,
    ) {
        TODO("Not yet implemented")
    }

    override fun removeSession(sessionId: String): Boolean {
        TODO("Not yet implemented")
    }

    private data class UserAndSessionModel(
        val user_user_id: Int,
        val name: String,
        val password: String,
        val email: String,
        val balance: Double,
        val session_id: String,
        val session_user_id: Int,
        val created_at: Long,
        val last_used_at: Long,
        val expires_at: Long,
        val revoked: Boolean,
    ) {
        val user: User
            get() =
                User(
                    userId = user_user_id,
                    name = name,
                    password = PasswordValidationInfo(password),
                    email = email,
                    balance = balance,
                )

        val session: Session
            get() =
                Session(
                    sessionId = TokenValidationInfo(session_id),
                    userId = session_user_id,
                    createdAt = Instant.fromEpochMilliseconds(created_at),
                    lastUsedAt = Instant.fromEpochMilliseconds(last_used_at),
                    expiresAt = expires_at,
                    revoked = revoked,
                )

        val userAndSession: Pair<User, Session>
            get() = user to session
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JdbiUserRepository::class.java)
    }
}
