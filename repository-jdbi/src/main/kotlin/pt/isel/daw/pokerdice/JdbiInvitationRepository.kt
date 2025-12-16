package pt.isel.daw.pt.isel.daw.pokerdice

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import pt.isel.daw.pokerdice.InvitationRepository

class JdbiInvitationRepository(
    private val handle: Handle,
) : InvitationRepository {
    override fun isValidInvitationCode(code: String): Boolean =
        handle
            .createQuery(
                "SELECT COUNT(*) FROM invitation WHERE code = :code AND is_active = true",
            ).bind("code", code)
            .mapTo<Int>()
            .one() > 0

    override fun consumeInvitation(code: String) {
        handle
            .createUpdate(
                "UPDATE invitation SET is_active = false WHERE code = :code",
            ).bind("code", code)
            .execute()
    }

    override fun createInvitation(code: String): Boolean =
        handle
            .createUpdate(
                "INSERT INTO invitation (code, is_active) VALUES (:code, true)",
            ).bind("code", code)
            .execute() > 0
}
