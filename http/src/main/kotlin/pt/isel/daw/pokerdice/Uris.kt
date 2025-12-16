package pt.isel.daw.pokerdice

import org.springframework.web.util.UriTemplate
import java.net.URI

object Uris {
    const val PREFIX = "/api"
    const val HOME = PREFIX
    const val VERSION = "v1"

    fun home(): URI = URI(HOME)

    object User {
        const val CREATE = "$PREFIX/users"
        const val GET_BY_ID = "$PREFIX/users/{id}"
        const val LOGIN = "$PREFIX/login"
        const val LOGOUT = "$PREFIX/users/logout"
        const val FETCH_USERS = "$PREFIX/users"
        const val CREATE_INVITATION = "$PREFIX/users/invitations"

        fun byId(id: Int): URI = UriTemplate(GET_BY_ID).expand(id)
    }

    object Lobby {
        const val CREATE = "$PREFIX/lobby"
        const val GET_BY_ID = "$PREFIX/lobbies/{id}"
        const val LIST = "$PREFIX/lobbies"
        const val JOIN = "$PREFIX/lobbies/{id}/join"
        const val LEAVE = "$PREFIX/lobbies/{id}/leave"

        fun byId(id: Int): URI = UriTemplate(GET_BY_ID).expand(id)
    }

    object Match {
        const val GET_BY_ID = "$PREFIX/matches/{mid}"
        const val LIST_ROUNDS = "$PREFIX/matches/{mid}/rounds"
        const val CURR_ROUND = "$PREFIX/matches/{mid}/rounds/current"
        const val ROUND_DET = "$PREFIX/matches/{mid}/rounds/{rid}"
        const val CURR_TURN = "$PREFIX/matches/{mid}/turns/current"
        const val ROLL = "$PREFIX/matches/{mid}/turns/current/roll"
        const val HAND_HIST = "$PREFIX/matches/{mid}/hands"
        const val TIMELINE = "$PREFIX/matches/{mid}/timeline"
        const val GET_LAST_ROLL = "$PREFIX/matches/{mid}/last-roll"

        fun byId(id: Int): URI = UriTemplate(Lobby.GET_BY_ID).expand(id)
    }
}
