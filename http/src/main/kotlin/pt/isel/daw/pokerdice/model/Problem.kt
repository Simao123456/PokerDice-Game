package pt.isel.daw.pokerdice.model

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.net.URI

class Problem(
    typeUri: URI,
) {
    val type: String = typeUri.toASCIIString()

    companion object {
        private const val MEDIA_TYPE = "application/problem+json"
        private const val BASE =
            "https://github.com/isel-leic-daw/2025-daw-leic51d-2025-leic51d-04"
        private const val BRANCH =
            "/blob/main/docs/problems"
        private const val URL = BASE + BRANCH

        fun response(
            status: HttpStatus,
            problem: Problem,
        ): ResponseEntity<Any> =
            ResponseEntity
                .status(status.value())
                .header("Content-Type", MEDIA_TYPE)
                .body(problem)

        // ---------- GENERIC ----------
        val internalServerError =
            Problem(URI("$URL/internal-server-error"))

        // ---------- USER ERRORS ----------
        val invalidUserName =
            Problem(URI("$URL/invalid-user-name"))

        val usernameAlreadyExists =
            Problem(URI("$URL/username-already-exists"))

        val insecurePassword =
            Problem(URI("$URL/insecure-password"))

        val invalidCredentials =
            Problem(URI("$URL/invalid-credentials"))

        val invitationCodeRequired =
            Problem(URI("$URL/invitation-code-required"))

        val invalidInvitationCode =
            Problem(URI("$URL/invalid-invitation-code"))

        val userNotFound =
            Problem(URI("$URL/user-not-found"))

        val invalidRegistrationCode =
            Problem(URI("$URL/invalid-registration-code"))

        // ---------- LOBBY ERRORS ----------
        val invalidLobbyName =
            Problem(URI("$URL/invalid-lobby-name"))

        val invalidMaxPlayers =
            Problem(URI("$URL/invalid-max-players"))

        val invalidMaxRounds =
            Problem(URI("$URL/invalid-max-rounds"))

        val invalidTimeoutSeconds =
            Problem(URI("$URL/invalid-timeout-seconds"))

        val lobbyNotFound =
            Problem(URI("$URL/lobby-not-found"))

        val userNotInLobby =
            Problem(URI("$URL/user-not-in-lobby"))

        val lobbyLeaveFailed =
            Problem(URI("$URL/lobby-leave-failed"))

        val lobbyFull =
            Problem(URI("$URL/lobby-full"))

        val userAlreadyInLobby =
            Problem(URI("$URL/user-already-in-lobby"))

        val lobbyJoinFailed =
            Problem(URI("$URL/lobby-join-failed"))

        val lobbyAlreadyStarted =
            Problem(URI("$URL/lobby-already-started"))

        // ---------- MATCH / ROUND / TURN ERRORS ----------
        val matchNotFound =
            Problem(URI("$URL/match-not-found"))

        val roundNotFound =
            Problem(URI("$URL/round-not-found"))

        val turnNotFound =
            Problem(URI("$URL/turn-not-found"))

        val invalidMatchState =
            Problem(URI("$URL/invalid-match-state"))

        val maxRollsReached =
            Problem(URI("$URL/max-rolls-reached"))

        val invalidHeldMask =
            Problem(URI("$URL/invalid-held-mask"))

        val missingHeldMask =
            Problem(URI("$URL/missing-held-mask"))

        val matchEnded =
            Problem(URI("$URL/match-ended"))
    }
}
