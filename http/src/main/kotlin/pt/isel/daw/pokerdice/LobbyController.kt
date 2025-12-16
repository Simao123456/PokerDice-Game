package pt.isel.daw.pokerdice

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import pt.isel.daw.pokerdice.lobby.*
import pt.isel.daw.pokerdice.model.Problem
import pt.isel.daw.pokerdice.model.input.LobbyCreateInputModel
import pt.isel.daw.pokerdice.model.output.ApiResponse
import pt.isel.daw.pokerdice.model.output.Meta
import pt.isel.daw.pokerdice.readmodels.LobbyDetails
import pt.isel.daw.pokerdice.readmodels.LobbyWithPlayers
import pt.isel.daw.pokerdice.security.AuthenticatedUser
import pt.isel.daw.pokerdice.utils.Failure
import pt.isel.daw.pokerdice.utils.Success

@RestController
class LobbyController(
    private val lobbyService: LobbyService,
) {
    @PostMapping(Uris.Lobby.CREATE)
    fun createLobby(
        @RequestBody input: LobbyCreateInputModel,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> {
        val res =
            lobbyService.createLobby(
                input.name,
                input.description,
                input.maxPlayers,
                input.maxRounds,
                input.timeoutSeconds,
                authUser.user.userId,
            )
        return when (res) {
            is Success<LobbyDetails> ->
                ResponseEntity
                    .created(Uris.Lobby.byId(res.value.lobbyId))
                    .body(ApiResponse(data = res.value, meta = Meta(message = "Lobby created with success")))

            is Failure<LobbyCreationError> -> {
                when (res.value) {
                    is LobbyCreationError.InvalidName ->
                        Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidLobbyName)

                    is LobbyCreationError.InvalidMaxPlayers ->
                        Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMaxPlayers)

                    is LobbyCreationError.InvalidMaxRounds ->
                        Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMaxRounds)

                    is LobbyCreationError.InvalidTimeoutSeconds ->
                        Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidTimeoutSeconds)
                }
            }
        }
    }

    @GetMapping(Uris.Lobby.GET_BY_ID)
    fun getLobbyById(
        @PathVariable id: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> =
        when (val res = lobbyService.getLobbyById(id)) {
            is Success<LobbyWithPlayers> ->
                ResponseEntity
                    .ok()
                    .body(ApiResponse(data = res, Meta()))

            is Failure<LobbyFetchError> -> {
                when (res.value) {
                    is LobbyFetchError.LobbyNotFound ->
                        Problem.response(HttpStatus.NOT_FOUND, Problem.lobbyNotFound)
                }
            }
        }

    @GetMapping(Uris.Lobby.LIST)
    fun fetchAvailableLobbies(
        @RequestParam params: MultiValueMap<String, String?>,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> {
        val res = lobbyService.getAvailableLobbies(params)
        return ResponseEntity.ok().body(ApiResponse(data = res, Meta(params = params)))
    }

    @PostMapping(Uris.Lobby.JOIN)
    fun joinLobby(
        @PathVariable id: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> =
        when (val res = lobbyService.joinLobby(id, authUser.user.userId)) {
            is Success ->
                when (val out = res.value) {
                    is JoinResult.Joined ->
                        ResponseEntity
                            .created(Uris.Lobby.byId(out.lobbyId))
                            .body(
                                ApiResponse(
                                    data =
                                    out,
                                    meta = Meta("User joined lobby with success"),
                                ),
                            )

                    is JoinResult.MatchStarted ->
                        ResponseEntity
                            .created(Uris.Match.byId(out.matchId))
                            .body(
                                ApiResponse(
                                    data =
                                    out,
                                    meta = Meta("Match is started"),
                                ),
                            )
                }

            is Failure ->
                when (res.value) {
                    LobbyJoinError.LobbyNotFound ->
                        Problem.response(HttpStatus.NOT_FOUND, Problem.lobbyNotFound)

                    LobbyJoinError.LobbyFull ->
                        Problem.response(HttpStatus.CONFLICT, Problem.lobbyFull)

                    LobbyJoinError.UserAlreadyInLobby ->
                        Problem.response(HttpStatus.CONFLICT, Problem.userAlreadyInLobby)

                    LobbyJoinError.LobbyAlreadyStarted ->
                        Problem.response(HttpStatus.CONFLICT, Problem.lobbyAlreadyStarted)

                    LobbyJoinError.LobbyJoinGenericError ->
                        Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.lobbyJoinFailed)

                    LobbyJoinError.MatchStartError ->
                        Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.lobbyJoinFailed)
                }
        }

    @PostMapping(Uris.Lobby.LEAVE)
    fun leaveLobby(
        @PathVariable id: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<*> =
        when (val res = lobbyService.leaveLobby(id, authUser.user.userId)) {
            is Success<Unit> ->
                ResponseEntity
                    .ok()
                    .body(ApiResponse(data = res.value, meta = Meta(message = "User left lobby with success")))

            is Failure<LobbyLeaveError> ->
                when (res.value) {
                    LobbyLeaveError.LobbyNotFound ->
                        Problem.response(HttpStatus.NOT_FOUND, Problem.lobbyNotFound)

                    LobbyLeaveError.UserNotInLobby ->
                        Problem.response(HttpStatus.CONFLICT, Problem.userNotInLobby)

                    LobbyLeaveError.UnknownError ->
                        Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.lobbyLeaveFailed)
                }
        }
}
