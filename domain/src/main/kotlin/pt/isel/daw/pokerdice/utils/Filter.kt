package pt.isel.daw.pokerdice.utils

sealed class Filter {
    data class Contains(
        val value: String,
    ) : Filter()

    data class Access(
        val value: Boolean,
    ) : Filter()

    data class InvitableToChannel(
        val cid: String,
        val inviterId: String,
    ) : Filter()
}

fun getFilters(
    params: Map<String, List<String?>>,
    acceptedFilters: List<String>,
    loggedInUserId: String? = null,
): List<Filter> {
    val filters = mutableListOf<Filter>()
    params
        .filter { (key, _) -> acceptedFilters.contains(key) }
        .forEach { (key, value) ->
            when (key) {
                "contains" ->
                    value.forEach { elem ->
                        elem?.let { if (it.length >= 2) filters.add(Filter.Contains(it)) }
                    }

                "name" ->
                    value.forEach { elem ->
                        elem?.let { if (it.isNotBlank()) filters.add(Filter.Contains(it)) }
                    }

                "access" ->
                    value.firstOrNull()?.let {
                        when (it) {
                            "private" -> filters.add(Filter.Access(true))
                            "public" -> filters.add(Filter.Access(false))
                            else -> {}
                        }
                    }

                "invitableToChannel" ->
                    value.firstOrNull()?.let {
                        filters.add(Filter.InvitableToChannel(it, loggedInUserId!!))
                    }

                else -> {}
            }
        }
    return filters
}

fun Filter.toSQL(columnName: String): String =
    when (this) {
        is Filter.Contains -> "$columnName ilike '%${this.value}%'"
        is Filter.Access -> "isPrivate = '${this.value}'"
        is Filter.InvitableToChannel ->
            """
            uid not in (
                select uid from Members_Channels where cid = '${this.cid}')
                    and uid not in (
                        select uid from Invitations where cid = '${this.cid}' and inviterId = '${this.inviterId}' and isActive = true)
            """.trimIndent()
    }
