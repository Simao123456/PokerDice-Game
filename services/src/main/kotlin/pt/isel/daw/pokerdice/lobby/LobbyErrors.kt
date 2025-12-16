package pt.isel.daw.pokerdice.lobby

sealed class LobbyCreationError {
    data object InvalidName : LobbyCreationError()

    data object InvalidMaxPlayers : LobbyCreationError()

    data object InvalidMaxRounds : LobbyCreationError()

    data object InvalidTimeoutSeconds : LobbyCreationError()
}

sealed class LobbyFetchError {
    data object LobbyNotFound : LobbyFetchError()
}

sealed class LobbyJoinError {
    data object LobbyAlreadyStarted : LobbyJoinError()

    data object LobbyFull : LobbyJoinError()

    data object UserAlreadyInLobby : LobbyJoinError()

    data object LobbyJoinGenericError : LobbyJoinError()

    data object LobbyNotFound : LobbyJoinError()

    data object MatchStartError : LobbyJoinError()
}

sealed class LobbyLeaveError {
    data object UserNotInLobby : LobbyLeaveError()

    data object LobbyNotFound : LobbyLeaveError()

    data object UnknownError : LobbyLeaveError()
}
