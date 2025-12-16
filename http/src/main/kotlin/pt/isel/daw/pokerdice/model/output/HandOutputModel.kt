package pt.isel.daw.pokerdice.model.output

import pt.isel.daw.pokerdice.entities.Hand

data class HandOutputModel(
    val handId: Int,
    val roundId: Int,
    val userId: Int,
    val faces: String,
    val rank: Int,
    val tieBreakerKey: Int? = null,
)

fun handToOutput(hand: Hand): HandOutputModel =
    HandOutputModel(
        handId = hand.handId,
        roundId = hand.roundId,
        userId = hand.userId,
        faces = hand.faces,
        rank = hand.rank,
        tieBreakerKey = hand.tieBreakerKey,
    )

fun handListToOutput(hands: List<Hand>): List<HandOutputModel> =
    hands.map { handToOutput(it) }
