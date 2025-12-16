package pt.isel.daw.pokerdice.match

import kotlinx.datetime.Clock
import org.springframework.stereotype.Component
import pt.isel.daw.pokerdice.HandEvaluator
import pt.isel.daw.pokerdice.RoundDomainConfig
import pt.isel.daw.pokerdice.Transaction
import pt.isel.daw.pokerdice.TransactionManager
import pt.isel.daw.pokerdice.entities.*
import pt.isel.daw.pokerdice.enums.DiceFace
import pt.isel.daw.pokerdice.enums.LobbyStatus
import pt.isel.daw.pokerdice.enums.MatchStatus
import pt.isel.daw.pokerdice.enums.TurnState
import pt.isel.daw.pokerdice.lobby.LobbyJoinError
import pt.isel.daw.pokerdice.readmodels.EvalInfo
import pt.isel.daw.pokerdice.readmodels.MatchWithPlayers
import pt.isel.daw.pokerdice.readmodels.RoundWithDetails
import pt.isel.daw.pokerdice.readmodels.matchWithPlayersToOutput
import pt.isel.daw.pokerdice.utils.Either
import pt.isel.daw.pokerdice.utils.Failure
import pt.isel.daw.pokerdice.utils.Success
import pt.isel.daw.pokerdice.utils.failure
import pt.isel.daw.pokerdice.utils.success

typealias RollResult = Either<RollError, Roll>
typealias MatchGetResult = Either<MatchGetError, MatchWithPlayers>
typealias RoundResult = Either<RoundError, RoundWithDetails>
typealias RoundsResult = Either<MatchGetError, List<Round>>
typealias TurnResult = Either<TurnError, Turn>
typealias HandsResult = Either<MatchGetError, List<Hand>>
typealias TimelineResult = Either<MatchGetError, List<Turn>>

@Component
class MatchService(
    private val transactionManager: TransactionManager,
    private val clock: Clock,
    private val roundConfig: RoundDomainConfig,
) {
    fun createMatchFromLobby(
        lobby: Lobby,
        tm: Transaction,
    ): Either<LobbyJoinError.MatchStartError, Int> {
        try {
            val matchId = tm.matchRepository.createMatchFromLobby(lobby)

            tm.lobbyRepository.updateLobbyStatus(lobby.lobbyId, LobbyStatus.ONGOING)

            initializeFirstRound(matchId, lobby.lobbyId, tm)

            return success(matchId)
        } catch (e: Exception) {
            return failure(LobbyJoinError.MatchStartError)
        }
    }

    fun getMatch(matchId: Int): MatchGetResult =
        transactionManager.run { tm ->
            val match =
                tm.matchRepository.getMatchById(matchId)
                    ?: return@run failure(MatchGetError.MatchNotFound)

            val players = tm.matchRepository.getMatchPlayers(matchId)
            val matchWithPlayers = matchWithPlayersToOutput(match, players)
            success(matchWithPlayers)
        }

    fun getCurrentRound(matchId: Int): Either<RoundError, RoundWithDetails> =
        transactionManager.run { tm ->
            val match =
                tm.matchRepository.getMatchById(matchId)
                    ?: return@run failure(RoundError.InvalidMatchState)
            // TODO: Mudar, adicionar Match Status
            if (match.status.name != LobbyStatus.ONGOING.name) {
                return@run failure(RoundError.InvalidMatchState)
            }
            val round =
                tm.matchRepository.currentRound(matchId)
                    ?: return@run failure(RoundError.RoundNotFound)

            val hands = tm.matchRepository.getRoundHands(round.roundId)
            val currentTurn = tm.matchRepository.currentTurn(matchId)
            val players = tm.matchRepository.getRoundPlayers(round.roundId)

            success(RoundWithDetails(round, hands, currentTurn, players))
        }

    fun listRounds(matchId: Int): RoundsResult =
        transactionManager.run { tm ->
            val rounds = tm.matchRepository.listRounds(matchId)
            success(rounds)
        }

    fun getRoundDetails(
        matchId: Int,
        roundId: Int,
    ): RoundResult =
        transactionManager.run { tm ->
            val round =
                tm.matchRepository.roundDetails(matchId, roundId)
                    ?: return@run failure(RoundError.RoundNotFound)

            val players = tm.matchRepository.getRoundPlayers(roundId)
            val hands = tm.matchRepository.getRoundHands(roundId)
            val currentTurn = tm.matchRepository.getCurrentTurnForRound(roundId)

            success(RoundWithDetails(round, hands, currentTurn, players))
        }

    private fun initializeFirstRound(
        matchId: Int,
        lobbyId: Int,
        tm: Transaction,
    ) {
        try {
            val roundId =
                tm.matchRepository.createRound(
                    matchId = matchId,
                    roundNumber = 1,
                    blind = roundConfig.defaultBlindAmount,
                )
            val players = tm.matchRepository.getLobbyPlayers(lobbyId)
            val host = players.first()

            tm.matchRepository.createTurn(roundId = roundId, userId = host.userId, turnNumber = 1)

            players.forEach { player ->
                tm.usersRepository.updateUserBalance(player.userId, player.balance - roundConfig.defaultBlindAmount)
                tm.matchRepository.updateRoundPot(roundId, roundConfig.defaultBlindAmount)
            }
            tm.matchRepository.updateCurrentRound(matchId, roundId)
        } catch (e: Exception) {
            throw e
        }
    }

    fun getCurrentTurn(matchId: Int): TurnResult =
        transactionManager.run { tm ->
            val match =
                tm.matchRepository.getMatchById(matchId)
                    ?: return@run failure(TurnError.InvalidMatchState)
            // TODO: Mudar, adicionar Match Status
            if (match.status.name != LobbyStatus.ONGOING.name) {
                return@run failure(TurnError.InvalidMatchState)
            }

            val turn =
                tm.matchRepository.currentTurn(matchId)
                    ?: return@run failure(TurnError.TurnNotFound)

            success(turn)
        }

    fun getHandHistory(matchId: Int): HandsResult =
        transactionManager.run { tm ->
            val hands = tm.matchRepository.handHistory(matchId)
            success(hands)
        }

    fun getMatchTimeline(matchId: Int): TimelineResult =
        transactionManager.run { tm ->
            val timeline = tm.matchRepository.timeline(matchId)
            success(timeline)
        }

    fun rollDice(
        matchId: Int,
        userId: Int,
        heldMask: String?,
    ): RollResult =
        transactionManager.run { tm ->
            val match = tm.matchRepository.getMatchById(matchId)
            if (match == null || match.status.name != LobbyStatus.ONGOING.name) {
                return@run failure(RollError.InvalidMatchState)
            }

            val currentTurn = tm.matchRepository.currentTurn(matchId)
            if (currentTurn == null) {
                return@run failure(RollError.TurnNotFound)
            }
            if (currentTurn.userId != userId) {
                return@run failure(RollError.InvalidMatchState)
            }
            if (currentTurn.rollCount > roundConfig.maxRollsPerTurn) {
                return@run failure(RollError.MaxRollsReached)
            }

            if (currentTurn.rollCount > 0 && heldMask != null && heldMask.isEmpty() || currentTurn.rollCount == 3) {
                when (val adv = advanceTurn(tm, matchId, userId)) {
                    is Failure -> {
                        return@run when (adv.value) {
                            is TurnError.TurnNotFound -> failure(RollError.TurnNotFound)
                            is TurnError.InvalidMatchState -> failure(RollError.InvalidMatchState)
                            is TurnError.MatchEnded -> failure(RollError.InvalidMatchState)
                        }
                    }

                    is Success -> {
                        val last =
                            tm.matchRepository.getLastRollForTurn(currentTurn.turnId)
                                ?: return@run failure(RollError.InvalidMatchState)
                        return@run success(last)
                    }
                }
            }

            if (currentTurn.rollCount > 0 && heldMask == null) {
                return@run failure(RollError.MissingHeldMask)
            }
            if (currentTurn.rollCount > 0 && !isValidHeldMask(heldMask!!)) {
                return@run failure(RollError.InvalidHeldMask)
            }

            val diceValues =
                generateDiceValues(currentTurn.rollCount, heldMask, tm, currentTurn.turnId)
                    ?: return@run failure(RollError.InvalidMatchState)

            val roll =
                Roll(
                    rollId = 0,
                    turnId = currentTurn.turnId,
                    createdAt = clock.now().toEpochMilliseconds(),
                    heldMask = if (currentTurn.rollCount == 0) "00000" else heldMask!!,
                    diceValues = diceValues,
                )

            val savedRoll = tm.matchRepository.createRoll(roll)
            tm.matchRepository.incrementTurnRollCount(currentTurn.turnId)

            success(savedRoll)
        }

    fun getLastRoll(
        matchId: Int,
        userId: Int,
    ): RollResult{
        return transactionManager.run { tm ->
            val match = tm.matchRepository.getMatchById(matchId)
            if (match == null || match.status.name != LobbyStatus.ONGOING.name) {
                return@run failure(RollError.InvalidMatchState)
            }

            val currentTurn = tm.matchRepository.currentTurn(matchId)
            if (currentTurn == null) {
                return@run failure(RollError.TurnNotFound)
            }
            if (currentTurn.userId != userId) {
                return@run failure(RollError.InvalidMatchState)
            }

            val lastRoll = tm.matchRepository.getLastRollForTurn(currentTurn.turnId)
            if (lastRoll == null) {
                return@run failure(RollError.TurnNotFound)
            }

            success(lastRoll)
        }
    }

    private fun generateDiceValues(
        rollCount: Int,
        heldMask: String?,
        tm: Transaction,
        turnId: Int,
    ): String? {
        if (rollCount == 0) {
            return (1..roundConfig.numberOfDice).joinToString(",") {
                randomDiceValue()
            }
        }
        val previousRoll = tm.matchRepository.getLastRollForTurn(turnId) ?: return null
        val previousValues = previousRoll.diceValues.split(",")

        return (0..4).joinToString(",") { idx ->
            if (heldMask!![idx] == '1') previousValues[idx] else randomDiceValue()
        }
    }

    private fun randomDiceValue(): String = roundConfig.diceFaces.random()

    private fun isValidHeldMask(mask: String): Boolean =
        mask.length == roundConfig.numberOfDice &&
            mask.all { it in '0'..'1' } &&
            mask.count { it == '1' } < roundConfig.maxRollsPerTurn + 1

    private fun advanceTurn(
        tm: Transaction,
        matchId: Int,
        userId: Int,
    ): Either<TurnError, Turn> {
        // TODO: Mudar erros
        val match =
            tm.matchRepository.getMatchById(matchId)
                ?: return failure(TurnError.InvalidMatchState)

        if (match.status.name != LobbyStatus.ONGOING.name) {
            return failure(TurnError.InvalidMatchState)
        }

        val currTurn =
            tm.matchRepository.currentTurn(matchId)
                ?: return failure(TurnError.TurnNotFound)

        if (currTurn.userId != userId) {
            return failure(TurnError.InvalidMatchState)
        }

        val round =
            tm.matchRepository.currentRound(matchId)
                ?: return failure(TurnError.InvalidMatchState)

        val roundPlayers = tm.matchRepository.getRoundPlayers(round.roundId)
        if (roundPlayers.isEmpty()) {
            return failure(TurnError.InvalidMatchState)
        }

        val nextTurnNumber = currTurn.number + 1
        val roundPlayerCount = roundPlayers.size
        val roundEnded = nextTurnNumber > roundPlayerCount

        if (!roundEnded) {
            val idx = roundPlayers.indexOfFirst { it.userId == currTurn.userId }
            val nextIdx = if (idx >= 0) (idx + 1) % roundPlayerCount else 0
            val nextPlayer = roundPlayers[nextIdx]

            tm.matchRepository.createTurn(round.roundId, nextPlayer.userId, nextTurnNumber)
            tm.matchRepository.updateTurnState(currTurn.turnId, TurnState.ENDED)
            val nextTurn =
                tm.matchRepository.getCurrentTurnForRound(round.roundId)
                    ?: return failure(TurnError.InvalidMatchState)

            return success(nextTurn)
        } else {
            val turnsForRound =
                tm.matchRepository
                    .timeline(matchId)
                    .filter { it.roundId == round.roundId }
                    .sortedBy { it.number }

            if (turnsForRound.isEmpty()) return failure(TurnError.InvalidMatchState)

            val evaluations = mutableListOf<EvalInfo>()

            turnsForRound.forEach { t ->
                val lastRoll = tm.matchRepository.getLastRollForTurn(t.turnId)
                val (rankInt, tieKey) =
                    if (lastRoll == null || lastRoll.diceValues.isBlank()) {
                        0 to 0
                    } else {
                        val diceFaces =
                            lastRoll.diceValues.split(",").mapNotNull { s ->
                                DiceFace.entries.firstOrNull { it.label == s }
                            }
                        if (diceFaces.size != roundConfig.numberOfDice) {
                            0 to 0
                        } else {
                            val evaluated = HandEvaluator.evaluate(diceFaces)
                            val rankValue = evaluated.rank.strength
                            val (_, majorList, minorList) = evaluated
                            val keyDigits: List<Int> = (majorList + minorList).map { df -> df.idx }.sortedDescending()
                            val computedKey: Int = keyDigits.fold(0) { acc: Int, d: Int -> acc * 10 + d }
                            rankValue to computedKey
                        }
                    }
                evaluations += EvalInfo(t.userId, rankInt, tieKey)
                tm.matchRepository.createHand(round.roundId, t.userId, lastRoll?.diceValues ?: "", rankInt, tieKey)
            }

            val bestRank = evaluations.maxOf { it.rank }
            val byBestRank = evaluations.filter { it.rank == bestRank }
            val bestTie = byBestRank.maxOf { it.tieKey }
            val winners = byBestRank.filter { it.tieKey == bestTie }.map { it.userId }

            val pot = round.pot
            val winnersCount = winners.size.coerceAtLeast(1)
            val share = pot / winnersCount

            winners.forEach { uid ->
                val user = tm.usersRepository.getUserById(uid)
                if (user != null) {
                    tm.usersRepository.updateUserBalance(uid, user.balance + share)
                }
            }

            tm.matchRepository.setRoundWinner(round.roundId, winners.first())

            val match =
                tm.matchRepository.getMatchById(matchId)
                    ?: return failure(TurnError.InvalidMatchState)
            val lobby =
                tm.lobbyRepository.getLobbyById(match.lobbyId)
                    ?: return failure(TurnError.InvalidMatchState)

            if (round.number >= lobby.maxRounds) {
                endMatch(matchId, tm)
                return success(turnsForRound.last())
            }

            val currentMatch =
                tm.matchRepository.getMatchById(matchId)
                    ?: return failure(TurnError.InvalidMatchState)

            val nextRoundNumber = round.number + 1
            val nextRoundId = tm.matchRepository.createRound(matchId, nextRoundNumber, round.blind)
            tm.matchRepository.updateTurnState(currTurn.turnId, TurnState.ENDED)

            val lobbyPlayers = tm.matchRepository.getLobbyPlayers(currentMatch.lobbyId)
            val previousUser = tm.matchRepository.getPreviousStartingPlayer(matchId, round.number + 1)
            // TODO send next player
            val startingUser =
                tm.usersRepository.getUserById(
                    lobbyPlayers
                        .firstOrNull { it.userId > previousUser }
                        ?.userId
                        ?: lobbyPlayers.first().userId,
                )

            if (startingUser == null) {
                tm.matchRepository.createTurn(nextRoundId, 1, 1)
            } else {
                tm.matchRepository.createTurn(nextRoundId, startingUser.userId, 1)
            }

            lobbyPlayers.forEach { p ->
                tm.usersRepository.updateUserBalance(p.userId, p.balance - round.blind)
                tm.matchRepository.updateRoundPot(nextRoundId, round.blind)
            }

            tm.matchRepository.updateCurrentRound(matchId, nextRoundId)
            val newTurn =
                tm.matchRepository.getCurrentTurnForRound(nextRoundId)
                    ?: return failure(TurnError.InvalidMatchState)

            return success(newTurn)
        }
    }

    private fun endMatch(
        matchId: Int,
        tm: Transaction,
    ) {
        tm.matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED)
        tm.matchRepository.setMatchFinishedAt(matchId, clock.now().toEpochMilliseconds())

        val match = tm.matchRepository.getMatchById(matchId)!!
        // TODO Change to lobbyService
        tm.lobbyRepository.updateLobbyStatus(match.lobbyId, LobbyStatus.ONGOING)
    }
}
