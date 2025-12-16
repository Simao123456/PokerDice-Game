package pt.isel.daw.pokerdice

import org.springframework.stereotype.Component

@Component
class LobbyDomain(
    private val lobbyDomainConfig: LobbyDomainConfig,
) {
    fun isNameValid(name: String): Boolean = name.isNotEmpty() && name.length <= lobbyDomainConfig.nameMaxLength

    fun arePlayersValid(maxPlayers: Int): Boolean = maxPlayers in lobbyDomainConfig.minPlayers..lobbyDomainConfig.maxPlayers

    fun areRoundsValid(maxRounds: Int): Boolean = maxRounds in lobbyDomainConfig.minRounds..lobbyDomainConfig.maxRounds

    fun isTimeoutValid(timeoutSeconds: Int): Boolean =
        timeoutSeconds in lobbyDomainConfig.minTimeoutSeconds..lobbyDomainConfig.maxTimeoutSeconds

    fun getMinPlayers(): Int = lobbyDomainConfig.minPlayers
}
