package pt.isel.daw.pokerdice

import pt.isel.daw.pokerdice.enums.DiceFace
import pt.isel.daw.pokerdice.enums.HandRank
import pt.isel.daw.pokerdice.readmodels.EvaluatedHand

object HandEvaluator {

    fun evaluate(dice: List<DiceFace>): EvaluatedHand {
        require(dice.size == 5) { "Exactly 5 dice required" }

        val freq = dice.groupingBy { it }.eachCount()
        val facesDesc = freq.keys.sortedByDescending { it.idx }

        val countsDesc = freq.entries
            .sortedWith(compareByDescending<Map.Entry<DiceFace, Int>> { it.value }
                .thenByDescending { it.key.idx })
            .map { it.key to it.value }

        val countValues = countsDesc.map { it.second }

        val uniqueFaces = freq.keys.toList()
        val isStraight = uniqueFaces.size == 5

        return when {
            countValues.firstOrNull() == 5 -> {
                val five = countsDesc.first().first
                EvaluatedHand(HandRank.FIVE_OF_A_KIND, listOf(five), emptyList())
            }
            countValues.firstOrNull() == 4 -> {
                val four = countsDesc.first().first
                val kicker = facesDesc.first { it != four }
                EvaluatedHand(HandRank.FOUR_OF_A_KIND, listOf(four), listOf(kicker))
            }
            countValues == listOf(3, 2) -> {
                val three = countsDesc.first().first
                val pair = countsDesc[1].first
                EvaluatedHand(HandRank.FULL_HOUSE, listOf(three), listOf(pair))
            }
            isStraight -> {
                val high = uniqueFaces.maxBy { it.idx }
                EvaluatedHand(HandRank.STRAIGHT, listOf(high), emptyList())
            }
            countValues.firstOrNull() == 3 -> {
                val three = countsDesc.first().first
                val kickers = facesDesc.filter { it != three }
                EvaluatedHand(HandRank.THREE_OF_A_KIND, listOf(three), kickers)
            }
            countValues == listOf(2, 2, 1) -> {
                val pair1 = countsDesc[0].first
                val pair2 = countsDesc[1].first
                val remaining = countsDesc[2].first
                val pairs = listOf(pair1, pair2).sortedByDescending { it.idx }
                EvaluatedHand(HandRank.TWO_PAIR, pairs, listOf(remaining))
            }
            countValues == listOf(2, 1, 1, 1) -> {
                val pair = countsDesc.first().first
                val kickers = facesDesc.filter { it != pair }
                EvaluatedHand(HandRank.ONE_PAIR, listOf(pair), kickers)
            }
            else -> {
                val high = facesDesc.first()
                val kickers = facesDesc.drop(1)
                EvaluatedHand(HandRank.BUST, listOf(high), kickers)
            }
        }
    }
}