package pt.isel.daw.pokerdice.match

sealed class MatchGetError {
    data object MatchNotFound : MatchGetError()
}

sealed class RoundError {
    data object RoundNotFound : RoundError()

    data object InvalidMatchState : RoundError()
}

sealed class TurnError {
    data object TurnNotFound : TurnError()

    data object InvalidMatchState : TurnError()

    data object MatchEnded : TurnError()
}

sealed class RollError {
    data object TurnNotFound : RollError()

    data object InvalidMatchState : RollError()

    data object MaxRollsReached : RollError()

    data object InvalidHeldMask : RollError()

    data object MissingHeldMask : RollError()
}
