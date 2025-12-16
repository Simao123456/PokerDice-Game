package pt.isel.daw.pokerdice

interface Transaction {
    val usersRepository: UsersRepository
    val lobbyRepository: LobbyRepository
    val invitationRepository: InvitationRepository

    val matchRepository: MatchRepository

    fun rollback()
}
