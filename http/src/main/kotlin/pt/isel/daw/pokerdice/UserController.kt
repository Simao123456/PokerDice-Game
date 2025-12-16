package pt.isel.daw.pokerdice

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import pt.isel.daw.pokerdice.model.Problem
import pt.isel.daw.pokerdice.model.input.UserCreateInputModel
import pt.isel.daw.pokerdice.model.input.UserLoginInputModel
import pt.isel.daw.pokerdice.model.output.*
import pt.isel.daw.pokerdice.readmodels.CreatedUserSession
import pt.isel.daw.pokerdice.security.AuthenticatedUser
import pt.isel.daw.pokerdice.user.UserCreationError
import pt.isel.daw.pokerdice.user.UserGetError
import pt.isel.daw.pokerdice.user.UserLoginError
import pt.isel.daw.pokerdice.user.UserService
import pt.isel.daw.pokerdice.utils.Failure
import pt.isel.daw.pokerdice.utils.Success

@RestController
class UserController(
    private val userService: UserService,
) {
    @GetMapping(Uris.User.FETCH_USERS)
    fun fetchAllUsers(
        @RequestParam params: MultiValueMap<String, String?>,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> {
        val res = userService.getAllUsers(params, authUser.user.userId)
        return ResponseEntity.ok(ApiResponse(data = (userToUserOutput(res)), meta = Meta(params = params)))
    }

    @PostMapping(Uris.User.CREATE)
    fun createUser(
        @RequestBody input: UserCreateInputModel,
    ): ResponseEntity<*> =
        when (val res = userService.createUser(input.name, input.password, input.email, input.invitationCode)) {
            is Success<CreatedUserSession> ->
                ResponseEntity
                    .created(Uris.User.byId(res.value.userId))
                    .body(
                        ApiResponse(
                            data =
                                UserSessionOutputModel(
                                    res.value.userId,
                                    res.value.username,
                                    res.value.token,
                                ),
                            meta = Meta(message = "User successfully created"),
                        ),
                    )

            is Failure<UserCreationError> ->
                when (res.value) {
                    is UserCreationError.UsernameInvalid ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invalidUserName,
                        )

                    is UserCreationError.UserAlreadyExists ->
                        Problem.response(
                            HttpStatus.CONFLICT,
                            Problem.usernameAlreadyExists,
                        )

                    is UserCreationError.InsecurePassword ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.insecurePassword,
                        )

                    is UserCreationError.InvalidInvitationCode ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invalidInvitationCode,
                        )

                    is UserCreationError.InvitationRequired ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invitationCodeRequired,
                        )
                }
        }

    @GetMapping(Uris.User.GET_BY_ID)
    fun fetchUserById(
        @PathVariable id: Int,
    ): ResponseEntity<*> =
        when (val res = userService.getUserById(id)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = UserOutputModel(res.value.userId, res.value.name)))
            is Failure ->
                when (res.value) {
                    is UserGetError.UserNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.userNotFound)
                }
        }

    @PostMapping("/api/login")
    fun login(
        @RequestBody input: UserLoginInputModel,
    ): ResponseEntity<*> =
        when (val res = userService.login(input.username, input.password)) {
            is Success ->
                ResponseEntity
                    .ok()
                    .header("Location", Uris.Lobby.LIST)
                    .header(
                        "Set-Cookie",
                        "AuthCookie=${res.value.token}; HttpOnly; Secure; SameSite=Strict",
                    ).body(
                        ApiResponse(
                            data =
                                UserSessionOutputModel(
                                    res.value.userId,
                                    res.value.username,
                                    res.value.token,
                                ),
                        ),
                    )

            is Failure ->
                when (res.value) {
                    is UserLoginError.InvalidCredentials ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invalidCredentials,
                        )
                }
        }

    @PostMapping(Uris.User.LOGOUT)
    fun logout(authUser: AuthenticatedUser): ResponseEntity<*> =
        try {
            userService.revokeToken(authUser.token)
            ResponseEntity
                .ok()
                .header(
                    "Set-Cookie",
                    "AuthCookie=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=/",
                ).body(ApiResponse(data = "Logged out successfully"))
        } catch (ex: Exception) {
            ResponseEntity
                .internalServerError()
                .header(
                    "Set-Cookie",
                    "AuthCookie=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=/",
                ).body(Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.internalServerError))
        }

    @PostMapping(Uris.User.CREATE_INVITATION)
    fun createInvitation(
        user: AuthenticatedUser
    ): ResponseEntity<*> {

        return when (val res = userService.createInvitation()) {
            is Success -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse(data = res.value))

            is Failure -> Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.userNotFound)
        }
    }
}
