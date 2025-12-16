package pt.isel.daw.pokerdice.utils

sealed class Sort {
    data object ASC : Sort()

    data object DESC : Sort()
}

fun getSort(
    params: Map<String, List<String?>>,
    acceptedSorts: List<String>,
): Sort? {
    params["sort"]?.let { prms ->
        prms
            .filter { acceptedSorts.contains(it) }
            .forEach { prm ->
                when (prm) {
                    "asc" -> return Sort.ASC
                    "desc" -> return Sort.DESC
                }
            }
    }
    return null
}

fun Sort.toSQL(columnName: String): String =
    when (this) {
        is Sort.ASC -> "$columnName asc"
        is Sort.DESC -> "$columnName desc"
    }
