package pt.isel.daw.pt.isel.daw.pokerdice

import org.jdbi.v3.core.Handle
import pt.isel.daw.pokerdice.*

class JdbiTransaction(
    private val handle: Handle,
) : Transaction {
    override val usersRepository: UsersRepository = JdbiUserRepository(handle)
    override val lobbyRepository = JdbiLobbyRepository(handle)
    override val invitationRepository: InvitationRepository = JdbiInvitationRepository(handle)
    override val matchRepository = JdbiMatchRepository(handle)

    override fun rollback() {
        handle.rollback()
    }
}
