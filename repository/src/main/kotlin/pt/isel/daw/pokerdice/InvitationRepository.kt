package pt.isel.daw.pokerdice

interface InvitationRepository {
    fun isValidInvitationCode(code: String): Boolean

    fun consumeInvitation(code: String)

    fun createInvitation(code: String): Boolean
}
