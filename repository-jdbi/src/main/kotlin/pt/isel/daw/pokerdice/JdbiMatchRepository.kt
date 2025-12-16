package pt.isel.daw.pokerdice

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import pt.isel.daw.pokerdice.entities.*
import pt.isel.daw.pokerdice.enums.MatchStatus
import pt.isel.daw.pokerdice.enums.TurnState
import pt.isel.daw.pokerdice.readmodels.PlayerInfo

class JdbiMatchRepository(
    private val handle: Handle,
) : MatchRepository {
    override fun getMatchById(matchId: Int): Match? =
        handle
            .createQuery("SELECT * FROM Match WHERE match_id = :id")
            .bind("id", matchId)
            .mapTo<Match>()
            .singleOrNull()

    override fun listRounds(matchId: Int): List<Round> =
        handle
            .createQuery("SELECT * FROM Round WHERE match_id = :matchId ORDER BY number")
            .bind("matchId", matchId)
            .mapTo<Round>()
            .list()

    override fun currentRound(matchId: Int): Round? = listRounds(matchId).lastOrNull()

    override fun roundDetails(
        matchId: Int,
        roundId: Int,
    ): Round? =
        handle
            .createQuery("SELECT * FROM Round WHERE match_id = :matchId AND round_id = :roundId")
            .bind("matchId", matchId)
            .bind("roundId", roundId)
            .mapTo<Round>()
            .singleOrNull()

    override fun currentTurn(matchId: Int): Turn? =
        handle
            .createQuery(
                """
                SELECT * FROM Turn t 
                WHERE t.round_id = (
                    SELECT round_id FROM Round 
                    WHERE match_id = :matchId 
                    ORDER BY round_id DESC 
                    LIMIT 1
                )
                AND t.state = 'active'
                LIMIT 1
            """,
            ).bind("matchId", matchId)
            .mapTo<Turn>()
            .singleOrNull()

    override fun getMatchPlayers(matchId: Int): List<PlayerInfo> =
        handle
            .createQuery(
                """
                SELECT u.user_id, u.name, u.email, u.balance 
                FROM Users u
                JOIN Lobby_Users lu ON u.user_id = lu.user_id
                JOIN Lobby l ON lu.lobby_id = l.lobby_id
                JOIN Match m ON l.lobby_id = m.lobby_id
                WHERE m.match_id = :matchId
            """,
            ).bind("matchId", matchId)
            .mapTo<PlayerInfo>()
            .list()

    override fun handHistory(matchId: Int): List<Hand> =
        handle
            .createQuery(
                """
                SELECT h.* FROM Hand h
                JOIN Round r ON h.round_id = r.round_id
                WHERE r.match_id = :matchId
                ORDER BY r.round_id, h.hand_id
            """,
            ).bind("matchId", matchId)
            .mapTo<Hand>()
            .list()

    override fun getRoundPlayers(roundId: Int): List<PlayerInfo> =
        handle
            .createQuery(
                """
            SELECT u.user_id, u.name, u.email, u.balance 
            FROM Users u
            JOIN Lobby_Users lu ON u.user_id = lu.user_id
            JOIN Lobby l ON lu.lobby_id = l.lobby_id
            JOIN Match m ON l.lobby_id = m.lobby_id
            JOIN Round r ON m.match_id = r.match_id
            WHERE r.round_id = :roundId
            ORDER BY lu.joined_at
            """,
            ).bind("roundId", roundId)
            .mapTo<PlayerInfo>()
            .list()

    override fun getRoundHands(roundId: Int): List<Hand> =
        handle
            .createQuery("SELECT * FROM Hand WHERE round_id = :roundId ORDER BY hand_id")
            .bind("roundId", roundId)
            .mapTo<Hand>()
            .list()

    override fun getCurrentTurnForRound(roundId: Int): Turn? =
        handle
            .createQuery(
                """
            SELECT * FROM Turn 
            WHERE round_id = :roundId AND state = 'active'
            LIMIT 1
        """,
            ).bind("roundId", roundId)
            .mapTo<Turn>()
            .singleOrNull()

    override fun timeline(matchId: Int): List<Turn> =
        handle
            .createQuery(
                """
                SELECT t.* FROM Turn t
                JOIN Round r ON t.round_id = r.round_id
                WHERE r.match_id = :matchId
                ORDER BY r.round_id, t.number
            """,
            ).bind("matchId", matchId)
            .mapTo<Turn>()
            .list()

    override fun createRoll(roll: Roll): Roll =
        handle
            .createUpdate(
                """
            INSERT INTO Roll (turn_id, created_at, held_mask, dice_values)
            VALUES (:turnId, :createdAt, :heldMask, :diceValues)
            RETURNING *
        """,
            ).bind("turnId", roll.turnId)
            .bind("createdAt", roll.createdAt)
            .bind("heldMask", roll.heldMask)
            .bind("diceValues", roll.diceValues)
            .executeAndReturnGeneratedKeys()
            .mapTo<Roll>()
            .one()

    override fun getLastRollForTurn(turnId: Int): Roll? =
        handle
            .createQuery(
                """
            SELECT * FROM Roll
            WHERE turn_id = :turnId
            ORDER BY roll_id DESC
            LIMIT 1
        """,
            ).bind("turnId", turnId)
            .mapTo<Roll>()
            .singleOrNull()

    override fun incrementTurnRollCount(turnId: Int) {
        handle
            .createUpdate(
                """
            UPDATE Turn 
            SET roll_count = roll_count + 1
            WHERE turn_id = :turnId
        """,
            ).bind("turnId", turnId)
            .execute()
    }

    override fun createMatchFromLobby(lobby: Lobby): Int =
        handle
            .createUpdate(
                """
            INSERT INTO Match (lobby_id, starting_player_user_id, created_at)
            VALUES (:lobbyId, :startingPlayerId, :createdAt)
            RETURNING match_id
            """,
            ).bind("lobbyId", lobby.lobbyId)
            .bind("startingPlayerId", lobby.hostId)
            .bind("createdAt", System.currentTimeMillis())
            .executeAndReturnGeneratedKeys("match_id")
            .mapTo<Int>()
            .one()

    override fun createRound(
        matchId: Int,
        roundNumber: Int,
        blind: Double,
    ): Int =
        handle
            .createUpdate(
                """
            INSERT INTO Round (match_id, number, blind, pot)
            VALUES (:matchId, :roundNumber, :blind, 0)
            RETURNING round_id
            """,
            ).bind("matchId", matchId)
            .bind("roundNumber", roundNumber)
            .bind("blind", blind)
            .executeAndReturnGeneratedKeys("round_id")
            .mapTo<Int>()
            .one()

    override fun createTurn(
        roundId: Int,
        userId: Int,
        turnNumber: Int,
    ): Int =
        handle
            .createUpdate(
                """
            INSERT INTO Turn (round_id, user_id, number, state, roll_count)
            VALUES (:roundId, :userId, :turnNumber, 'active', 0)
            RETURNING turn_id
            """,
            ).bind("roundId", roundId)
            .bind("userId", userId)
            .bind("turnNumber", turnNumber)
            .executeAndReturnGeneratedKeys("turn_id")
            .mapTo<Int>()
            .one()

    override fun updateCurrentRound(
        matchId: Int,
        roundId: Int,
    ) {
        handle
            .createUpdate("UPDATE Match SET current_round_id = :roundId WHERE match_id = :matchId")
            .bind("roundId", roundId)
            .bind("matchId", matchId)
            .execute()
    }

    override fun getLobbyPlayers(lobbyId: Int): List<PlayerInfo> =
        handle
            .createQuery(
                """
            SELECT u.user_id, u.name, u.email, u.balance 
            FROM Users u
            JOIN Lobby_Users lu ON u.user_id = lu.user_id
            WHERE lu.lobby_id = :lobbyId
            ORDER BY lu.joined_at
            """,
            ).bind("lobbyId", lobbyId)
            .mapTo<PlayerInfo>()
            .list()

    override fun getMatchByLobbyId(lobbyId: Int): Match? =
        handle
            .createQuery("SELECT * FROM Match WHERE lobby_id = :lobbyId")
            .bind("lobbyId", lobbyId)
            .mapTo<Match>()
            .singleOrNull()

    override fun updateRoundPot(
        roundId: Int,
        amount: Double,
    ) {
        handle
            .createUpdate("UPDATE Round SET pot = pot + :amount WHERE round_id = :roundId")
            .bind("amount", amount)
            .bind("roundId", roundId)
            .execute()
    }

    override fun createHand(
        roundId: Int,
        userId: Int,
        faces: String,
        rank: Int,
        tieBreakerKey: Int,
    ): Int =
        handle
            .createUpdate(
                """
            INSERT INTO Hand (round_id, user_id, faces, rank, tie_breaker_key)
            VALUES (:roundId, :userId, :faces, :rank, :tieBreakerKey)
            RETURNING hand_id
            """,
            ).bind("roundId", roundId)
            .bind("userId", userId)
            .bind("faces", faces)
            .bind("rank", rank)
            .bind("tieBreakerKey", tieBreakerKey)
            .executeAndReturnGeneratedKeys("hand_id")
            .mapTo<Int>()
            .one()

    override fun updateTurnState(
        turnId: Int,
        state: TurnState,
    ) {
        handle
            .createUpdate("UPDATE Turn SET state = :state WHERE turn_id = :turnId")
            .bind("state", state.name)
            .bind("turnId", turnId)
            .execute()
    }

    override fun setRoundWinner(
        roundId: Int,
        userId: Int,
    ) {
        handle
            .createUpdate(
                """
            UPDATE Round 
            SET winner_user_id = :userId
            WHERE round_id = :roundId
            """,
            ).bind("roundId", roundId)
            .bind("userId", userId)
            .execute()
    }

    override fun getPreviousStartingPlayer(
        matchId: Int,
        roundNumber: Int,
    ): Int =
        handle
            .createQuery(
                """
            SELECT user_id FROM Turn
            WHERE round_id = (
                SELECT round_id FROM Round
                WHERE match_id = :matchId AND number = :previousRoundNumber
            )
            """,
            ).bind("matchId", matchId)
            .bind("previousRoundNumber", roundNumber - 1)
            .mapTo<Int>()
            .first()

    override fun updateMatchStatus(
        matchId: Int,
        status: MatchStatus,
    ) {
        handle
            .createUpdate("UPDATE Match SET status = :status WHERE match_id = :matchId")
            .bind("matchId", matchId)
            .bind("status", status.name)
            .execute()
    }

    override fun setMatchFinishedAt(
        matchId: Int,
        finishedAt: Long,
    ) {
        handle
            .createUpdate("UPDATE Match SET finished_at = :finishedAt WHERE match_id = :matchId")
            .bind("matchId", matchId)
            .bind("finishedAt", finishedAt)
            .execute()
    }
}
