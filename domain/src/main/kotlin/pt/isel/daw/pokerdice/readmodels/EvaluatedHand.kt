package pt.isel.daw.pokerdice.readmodels

import pt.isel.daw.pokerdice.enums.DiceFace
import pt.isel.daw.pokerdice.enums.HandRank

data class EvaluatedHand(
    val rank: HandRank,
    val primary: List<DiceFace>,
    val kickers: List<DiceFace>
) : Comparable<EvaluatedHand> {

    private fun compareFaceLists(a: List<DiceFace>, b: List<DiceFace>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrNull(i)?.idx ?: -1
            val bi = b.getOrNull(i)?.idx ?: -1
            if (ai != bi) return ai.compareTo(bi)
        }
        return 0
    }



    override fun compareTo(other: EvaluatedHand): Int {
        val rankCmp = rank.strength.compareTo(other.rank.strength)
        if (rankCmp != 0) return rankCmp
        return compareFaceLists(kickers, other.kickers)
    }

    fun winner(other: EvaluatedHand): EvaluatedHand =
        if (this >= other) this else other
}