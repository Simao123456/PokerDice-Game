package pt.isel.daw.pokerdice

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import pt.isel.daw.pokerdice.match.*
import pt.isel.daw.pokerdice.model.Problem
import pt.isel.daw.pokerdice.model.input.RollRequestInputModel
import pt.isel.daw.pokerdice.model.output.*
import pt.isel.daw.pokerdice.security.AuthenticatedUser
import pt.isel.daw.pokerdice.utils.Failure
import pt.isel.daw.pokerdice.utils.Success

@RestController
class MatchController(
    private val matchService: MatchService,
) {
    @GetMapping(Uris.Match.GET_BY_ID)
    fun getMatch(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getMatch(mid)) {
            is Success ->
                ResponseEntity.ok(
                    ApiResponse(data = result.value),
                )

            is Failure ->
                when (result.value) {
                    is MatchGetError.MatchNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.matchNotFound)
                }
        }

    @GetMapping(Uris.Match.CURR_ROUND)
    fun getCurrentRound(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getCurrentRound(mid)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = roundWithDetailsToOutput(result.value)))
            is Failure ->
                when (result.value) {
                    is RoundError.RoundNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.roundNotFound)
                    is RoundError.InvalidMatchState ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invalidMatchState,
                        )
                }
        }

    @GetMapping(Uris.Match.LIST_ROUNDS)
    fun listRounds(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.listRounds(mid)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = result.value))
            is Failure ->
                when (result.value) {
                    is MatchGetError.MatchNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.matchNotFound)
                }
        }

    @GetMapping(Uris.Match.ROUND_DET)
    fun getRoundDetails(
        @PathVariable mid: Int,
        @PathVariable rid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getRoundDetails(mid, rid)) {
            is Success ->
                ResponseEntity.ok(
                    ApiResponse(data = roundWithDetailsToOutput(result.value)),
                )

            is Failure ->
                when (result.value) {
                    is RoundError.RoundNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.roundNotFound)
                    is RoundError.InvalidMatchState ->
                        Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMatchState)
                }
        }

    @GetMapping(Uris.Match.CURR_TURN)
    fun getCurrentTurn(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getCurrentTurn(mid)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = turnToOutput(result.value)))
            is Failure ->
                when (result.value) {
                    is TurnError.TurnNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.turnNotFound)
                    is TurnError.InvalidMatchState ->
                        Problem.response(
                            HttpStatus.BAD_REQUEST,
                            Problem.invalidMatchState,
                        )

                    is TurnError.MatchEnded -> Problem.response(HttpStatus.BAD_REQUEST, Problem.matchEnded)
                }
        }

    @GetMapping(Uris.Match.HAND_HIST)
    fun getHandHistory(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getHandHistory(mid)) {
            is Success ->
                ResponseEntity.ok(
                    ApiResponse(data = handListToOutput(result.value)),
                )

            is Failure ->
                when (result.value) {
                    is MatchGetError.MatchNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.matchNotFound)
                }
        }

    @GetMapping(Uris.Match.TIMELINE)
    fun getMatchTimeline(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getMatchTimeline(mid)) {
            is Success ->
                ResponseEntity.ok(
                    ApiResponse(data = turnListToOutput(result.value)),
                )

            is Failure ->
                when (result.value) {
                    is MatchGetError.MatchNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.matchNotFound)
                }
        }

    @PostMapping(Uris.Match.ROLL)
    fun rollDice(
        @PathVariable mid: Int,
        @RequestBody(required = false) input: RollRequestInputModel?,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.rollDice(mid, authUser.user.userId, input?.heldMask)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = result.value))
            is Failure ->
                when (result.value) {
                    RollError.TurnNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.turnNotFound)
                    RollError.InvalidMatchState -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMatchState)
                    RollError.MaxRollsReached -> Problem.response(HttpStatus.BAD_REQUEST, Problem.maxRollsReached)
                    RollError.InvalidHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidHeldMask)
                    RollError.MissingHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.missingHeldMask)
                }
        }

    @GetMapping(Uris.Match.GET_LAST_ROLL)
    fun getLastRoll(
        @PathVariable mid: Int,
        authUser: AuthenticatedUser,
    ): ResponseEntity<Any> =
        when (val result = matchService.getLastRoll(mid, authUser.user.userId)) {
            is Success -> ResponseEntity.ok(ApiResponse(data = result.value))
            is Failure ->
                when (result.value) {
                    RollError.TurnNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.turnNotFound)
                    RollError.InvalidMatchState -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMatchState)
                    RollError.MaxRollsReached -> Problem.response(HttpStatus.BAD_REQUEST, Problem.maxRollsReached)
                    RollError.InvalidHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidHeldMask)
                    RollError.MissingHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.missingHeldMask)
                }
        }
}
