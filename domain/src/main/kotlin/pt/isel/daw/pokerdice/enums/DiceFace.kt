package pt.isel.daw.pokerdice.enums

enum class DiceFace(val label: String, val idx: Int) {
    NINE("9", 0),
    TEN("10", 1),
    J("J", 2),
    Q("Q", 3),
    K("K", 4),
    A("A", 5);


    companion object {
        fun fromString(s: String): DiceFace =
            DiceFace.entries.firstOrNull { it.label == s } ?: throw IllegalArgumentException("Unknown face: $s")

    }
}