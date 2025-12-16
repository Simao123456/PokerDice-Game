package pt.isel.daw.pokerdice.model.output

import pt.isel.daw.pokerdice.entities.Turn

data class TurnOutputModel(
    val turnId: Int,
    val roundId: Int,
    val userId: Int,
    val number: Int,
    val state: String = "active",
    val rollCount: Int = 0,
)

fun turnToOutput(turn: Turn): TurnOutputModel =
    TurnOutputModel(
        turnId = turn.turnId,
        roundId = turn.roundId,
        userId = turn.userId,
        state = turn.state,
        number = turn.number,
        rollCount = turn.rollCount,
    )

fun turnListToOutput(turns: List<Turn>): List<TurnOutputModel> = turns.map { turnToOutput(it) }
