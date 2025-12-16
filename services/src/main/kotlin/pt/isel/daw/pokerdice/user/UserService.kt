package pt.isel.daw.pokerdice.user

import kotlinx.datetime.Clock
import org.springframework.stereotype.Component
import pt.isel.daw.pokerdice.TransactionManager
import pt.isel.daw.pokerdice.UsersDomain
import pt.isel.daw.pokerdice.entities.Session
import pt.isel.daw.pokerdice.entities.User
import pt.isel.daw.pokerdice.readmodels.CreatedUserSession
import pt.isel.daw.pokerdice.utils.*
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.times

typealias UserCreationResult = Either<UserCreationError, CreatedUserSession>
typealias UserGetResult = Either<UserGetError, User>
typealias UserLoginResult = Either<UserLoginError, CreatedUserSession>

@Component
class UserService(
    private val transactionManager: TransactionManager,
    private val usersDomain: UsersDomain,
    private val clock: Clock,
) {
    fun getAllUsers(
        params: Map<String, List<String?>>,
        loggedInUserId: Int,
    ): List<User> =
        transactionManager.run { tm ->
            val acceptedSorts = listOf("asc", "desc")
            val sort: Sort? = getSort(params, acceptedSorts)

            val acceptedFilters = listOf("contains", "invitableToChannel", "name")
            val filters: List<Filter> = getFilters(params, acceptedFilters, loggedInUserId.toString())

            val limit = params["limit"]?.get(0)?.toUIntOrNull()
            val skip = params["skip"]?.get(0)?.toUIntOrNull()

            val usersRepository = tm.usersRepository
            usersRepository.getAllUsers(filters, sort, limit, skip)
        }

    fun createUser(
        name: String,
        password: String,
        email: String,
        invitationCode: String? = null,
    ): UserCreationResult {
        if (!usersDomain.isSafePassword(password)) return failure(UserCreationError.InsecurePassword)

        if (!usersDomain.isValidUsername(name)) return failure(UserCreationError.UsernameInvalid)

        if (usersDomain.isInvitationRequired() && invitationCode == null) return failure(UserCreationError.InvitationRequired)

        val passwordValidationInfo = usersDomain.createPasswordValidationInformation(password)

        return transactionManager.run { transaction ->
            val usersRepository = transaction.usersRepository
            val invitationRepository = transaction.invitationRepository

            if (!invitationRepository.isValidInvitationCode(invitationCode!!)) {
                return@run failure(UserCreationError.InvalidInvitationCode)
            }

            if (usersRepository.isUserStoredByName(name)) {
                return@run failure(UserCreationError.UserAlreadyExists)
            }

            val userId = usersRepository.storeUser(name, passwordValidationInfo, email)
            if (usersDomain.isInvitationRequired()) {
                invitationRepository.consumeInvitation(invitationCode)
            }

            val token = usersDomain.generateTokenValue()
            val tokenValidation = usersDomain.createTokenValidationInformation(token)
            val now = clock.now()
            val expiresAt = now + 7 * 24 * 60 * 60 * 1000.toLong().milliseconds

            val session =
                Session(
                    tokenValidation,
                    userId = userId,
                    createdAt = now,
                    lastUsedAt = now,
                    expiresAt = expiresAt.toEpochMilliseconds(),
                    revoked = false,
                )

            usersRepository.storeSession(session, usersDomain.maxNumberOfTokensPerUser)
            success(CreatedUserSession(userId, name, token))
        }
    }

    fun getUserById(uid: Int): UserGetResult =
        transactionManager.run { tm ->
            val usersRepository = tm.usersRepository
            val user: User? = usersRepository.getUserById(uid)
            if (user == null) {
                failure(UserGetError.UserNotFound)
            } else {
                success(user)
            }
        }

    fun getUserByToken(token: String): User? {
        if (!usersDomain.canBeToken(token)) {
            return null
        }
        return transactionManager.run { tm ->
            val usersRepository = tm.usersRepository
            val tokenValidationInfo = usersDomain.createTokenValidationInformation(token)
            if (usersRepository.isTokenExpired(tokenValidationInfo)) {
                return@run null
            }
            val userAndSession: Pair<User, Session>? =
                usersRepository.getSessionByTokenValidationInfo(tokenValidationInfo)
            userAndSession?.let {
                if (usersDomain.isTokenTimeValid(clock, it.second)) {
                    usersRepository.updateTokenLastUsed(it.second, clock.now())
                    return@run it.first
                } else {
                    usersRepository.removeTokenByValidationInfo(tokenValidationInfo, clock.now())
                    return@run null
                }
            }
        }
    }

    fun login(
        username: String,
        password: String,
    ): UserLoginResult =
        transactionManager.run { tm ->
            val userRepository = tm.usersRepository
            val user = userRepository.getUserByName(username)

            if (user == null) {
                failure(UserLoginError.InvalidCredentials)
            } else if (!usersDomain.validatePassword(password, user.password)) {
                failure(UserLoginError.InvalidCredentials)
            } else {
                val token: String = usersDomain.generateTokenValue()
                val tokenValidation = usersDomain.createTokenValidationInformation(token)

                val now = clock.now()
                val expiresAt = now + 7 * 24 * 60 * 60 * 1000.toLong().milliseconds

                val session =
                    Session(
                        tokenValidation,
                        user.userId,
                        now,
                        now,
                        expiresAt.toEpochMilliseconds(),
                        false,
                    )
                userRepository.storeSession(session, usersDomain.maxNumberOfTokensPerUser)
                success(
                    CreatedUserSession(
                        session.userId,
                        user.name,
                        token,
                    ),
                )
            }
        }

    fun revokeToken(token: String): Boolean {
        val tokenValidationInfo: TokenValidationInfo = usersDomain.createTokenValidationInformation(token)
        return transactionManager.run {
            it.usersRepository.removeTokenByValidationInfo(tokenValidationInfo, clock.now())
            true
        }
    }

    fun createInvitation(): Either<ServiceError, String> =
        transactionManager.run { tm ->
            val code = UUID.randomUUID().toString()
            tm.invitationRepository.createInvitation(code)
            success(code)
        }
}
