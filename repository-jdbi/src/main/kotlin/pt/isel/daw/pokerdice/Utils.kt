package pt.isel.daw.pokerdice

import kotlinx.datetime.Instant
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import pt.isel.daw.pokerdice.mappers.InstantMapper
import pt.isel.daw.pokerdice.mappers.PasswordValidationInfoMapper
import pt.isel.daw.pokerdice.mappers.TokenValidationInfoMapper
import pt.isel.daw.pokerdice.utils.Filter
import pt.isel.daw.pokerdice.utils.Sort
import pt.isel.daw.pokerdice.utils.toSQL
import pt.isel.daw.pokerdice.valueobjects.PasswordValidationInfo
import pt.isel.daw.pokerdice.valueobjects.TokenValidationInfo

fun Jdbi.configureWithAppRequirements(): Jdbi {
    installPlugin(KotlinPlugin())
    installPlugin(PostgresPlugin())

    registerColumnMapper(PasswordValidationInfo::class.java, PasswordValidationInfoMapper())
    registerColumnMapper(TokenValidationInfo::class.java, TokenValidationInfoMapper())
    registerColumnMapper(Instant::class.java, InstantMapper())

    return this
}

fun constructSimpleQueryString(
    baseQuery: StringBuilder,
    columnName: String,
    filters: List<Filter>,
    sort: Sort?,
    limit: UInt?,
    skip: UInt?,
    baseContainsWhere: Boolean = false,
): StringBuilder {
    val query = StringBuilder(baseQuery)
    if (filters.isNotEmpty()) {
        if (baseContainsWhere) {
            query.append(" AND ")
        } else {
            query.append(" WHERE ")
        }
        query.append(filters.joinToString(" AND ") { it.toSQL(columnName) })
    }
    sort?.let {
        query.append(" ORDER BY ")
        query.append(it.toSQL(columnName))
    }
    limit?.let {
        query.append(" LIMIT $it")
    }
    skip?.let {
        query.append(" OFFSET $it")
    }
    return query
}
