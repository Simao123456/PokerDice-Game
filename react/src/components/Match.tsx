import { useEffect, useReducer } from "react";
import { useParams } from "react-router";
import { api, ApiError } from "../api";
import { useAuth } from "../AuthContext";
import { RoundWithDetails, Turn, MatchState } from "../types";
import {
  isUserTurn,
  getCurrentPlayer,
  calculatePlayersUntilTurn,
  createHeldMask,
  fetchMatchData,
} from "../utils/matchUtils";
import { RollScreen } from "./RollScreen";
import { WaitingScreen } from "./WaitingScreen";
import { MatchEndScreen } from "./MatchEndScreen";
import "../styles/styles.css";

type State =
  | { tag: "loading" }
  | { tag: "error"; message: string }
  | { tag: "playing"; state: MatchState }
  | { tag: "ended" };

type Action =
  | { type: "fetch_start" }
  | {
      type: "fetch_success";
      round: RoundWithDetails;
      currentTurn: Turn | null;
      lastRoll: string | null;
      previousRoundWinner: string | null;
    }
  | { type: "fetch_error"; message: string }
  | { type: "match_ended" }
  | { type: "toggle_dice"; index: number }
  | { type: "roll_start" }
  | {
      type: "roll_success";
      diceValues: string;
      round: RoundWithDetails;
      currentTurn: Turn | null;
    }
  | { type: "roll_error"; message: string }
  | { type: "end_start" }
  | { type: "end_success"; round: RoundWithDetails; currentTurn: Turn | null }
  | { type: "end_error"; message: string };

const initialMatchState: MatchState = {
  round: null,
  previousRoundWinner: null,
  currentTurn: null,
  selectedDice: Array(5).fill(false),
  lastRoll: null,
  rolling: false,
};

const initialState: State = { tag: "loading" };

function reducer(state: State, action: Action): State {
  if (action.type === "match_ended") return { tag: "ended" };

  switch (state.tag) {
    case "loading":
    case "error":
      if (action.type === "fetch_start") return { tag: "loading" };
      if (action.type === "fetch_success") {
        return {
          tag: "playing",
          state: {
            ...initialMatchState,
            round: action.round,
            currentTurn: action.currentTurn,
            lastRoll: action.lastRoll,
            previousRoundWinner: action.previousRoundWinner,
          },
        };
      }
      if (action.type === "fetch_error")
        return { tag: "error", message: action.message };
      return state;

    case "playing":
      if (action.type === "fetch_success") {
        const sameTurn =
          action.currentTurn?.turnId === state.state.currentTurn?.turnId;
        return {
          tag: "playing",
          state: {
            ...state.state,
            round: action.round,
            currentTurn: action.currentTurn,
            previousRoundWinner:
              action.previousRoundWinner ?? state.state.previousRoundWinner,
            lastRoll:
              action.lastRoll ?? (sameTurn ? state.state.lastRoll : null),
          },
        };
      }
      if (action.type === "toggle_dice") {
        if (!state.state.currentTurn || state.state.currentTurn.rollCount === 0)
          return state;
        const newSelection = [...state.state.selectedDice];
        newSelection[action.index] = !newSelection[action.index];
        return {
          tag: "playing",
          state: { ...state.state, selectedDice: newSelection },
        };
      }
      if (action.type === "roll_start" || action.type === "end_start") {
        return { tag: "playing", state: { ...state.state, rolling: true } };
      }
      if (action.type === "roll_success") {
        return {
          tag: "playing",
          state: {
            ...state.state,
            round: action.round,
            currentTurn: action.currentTurn,
            lastRoll: action.diceValues,
            selectedDice: Array(5).fill(false),
            rolling: false,
          },
        };
      }
      if (action.type === "end_success") {
        return {
          tag: "playing",
          state: {
            ...state.state,
            round: action.round,
            currentTurn: action.currentTurn,
            lastRoll: null,
            selectedDice: Array(5).fill(false),
            rolling: false,
          },
        };
      }
      if (action.type === "roll_error" || action.type === "end_error") {
        return { tag: "playing", state: { ...state.state, rolling: false } };
      }
      return state;

    case "ended":
      return state;
  }
}

export function Match() {
  const { matchId: matchIdParam } = useParams<{ matchId: string }>();
  const { userId } = useAuth();
  const [state, dispatch] = useReducer(reducer, initialState);

  const matchId = matchIdParam ? parseInt(matchIdParam) : null;

  useEffect(() => {
    if (!matchId) {
      dispatch({ type: "fetch_error", message: "Invalid match ID" });
      return;
    }

    const loadMatchData = async () => {
      try {
        dispatch({ type: "fetch_start" });

        const matchData = await fetchMatchData(matchId!, userId!);

        dispatch({
          type: "fetch_success",
          round: matchData.round,
          currentTurn: matchData.currentTurn,
          lastRoll: matchData.lastRoll,
          previousRoundWinner: matchData.previousRoundWinner,
        });
      } catch (err: any) {
        if (err.message === "MATCH_FINISHED" || err.message === "MATCH_ENDED") {
          dispatch({ type: "match_ended" });
          return;
        }

        dispatch({
          type: "fetch_error",
          message:
            err instanceof ApiError ? err.message : "Error loading match data",
        });
      }
    };

    loadMatchData();
  }, [matchId, userId]);

  const handleDiceToggle = (index: number) => {
    if (state.tag !== "playing") return;
    dispatch({ type: "toggle_dice", index });
  };

  const handleRoll = async () => {
    if (state.tag !== "playing" || !matchId) return;
    const { currentTurn, selectedDice, rolling } = state.state;
    if (!currentTurn || currentTurn.userId !== userId || rolling) return;

    const heldMask =
      currentTurn.rollCount > 0 ? createHeldMask(selectedDice) : undefined;
    if (heldMask === "00000") {
      alert("You must select at least one die to keep");
      return;
    }

    try {
      dispatch({ type: "roll_start" });
      const {
        data: { diceValues },
      } = await api.rollDice(matchId, heldMask);
      const { data: round } = await api.getCurrentRound(matchId);
      dispatch({
        type: "roll_success",
        diceValues,
        round,
        currentTurn: round.currentTurn,
      });
    } catch (err: any) {
      const message =
        err instanceof ApiError ? err.message : "Error rolling dice";
      dispatch({ type: "roll_error", message });
      alert(`Error: ${message}`);
    }
  };

  const checkMatchEnded = async (): Promise<boolean> => {
    if (!matchId) return false;
    try {
      const { data } = await api.getMatch(matchId);
      if (data.status === "FINISHED") {
        dispatch({ type: "match_ended" });
        return true;
      }
    } catch {
      console.warn("Could not check match status");
    }
    return false;
  };

  const handleEnd = async () => {
    if (state.tag !== "playing" || !matchId) return;
    const { currentTurn, rolling, round } = state.state;
    if (!currentTurn || currentTurn.userId !== userId || rolling) return;
    if (currentTurn.rollCount === 0) {
      alert("You must roll at least once before ending your turn");
      return;
    }

    try {
      dispatch({ type: "end_start" });
      await api.rollDice(matchId, "");
      if (await checkMatchEnded()) {
        dispatch({ type: "end_success", round: round!, currentTurn: null });
        return;
      }

      const { data } = await api.getCurrentRound(matchId);
      dispatch({
        type: "end_success",
        round: data,
        currentTurn: data.currentTurn,
      });
    } catch (err: any) {
      if (await checkMatchEnded()) {
        dispatch({ type: "end_success", round: round!, currentTurn: null });
        return;
      }
      const message =
        err instanceof ApiError ? err.message : "Error ending turn";
      dispatch({ type: "end_error", message });
      alert(`Error: ${message}`);
    }
  };

  if (state.tag === "loading") {
    return (
      <div className="match-container">
        <div className="match-loading">
          <div className="spinner"></div>
          <p>Loading match...</p>
        </div>
      </div>
    );
  }

  if (state.tag === "error") {
    return (
      <div className="match-container">
        <div className="match-error">
          <h3>Error</h3>
          <p>{state.message}</p>
        </div>
      </div>
    );
  }

  if (state.tag === "ended") return <MatchEndScreen />;

  if (state.tag !== "playing" || !state.state.round || !matchId) {
    return (
      <div className="match-container">
        <div className="match-error">
          <h3>Match not found</h3>
        </div>
      </div>
    );
  }

  const { round, currentTurn, selectedDice, lastRoll, rolling } = state.state;
  const userTurn = isUserTurn(currentTurn, userId);
  const currentPlayer = getCurrentPlayer(round, currentTurn);
  const playersUntilTurn = calculatePlayersUntilTurn(
    round,
    currentTurn,
    userId
  );

  const { previousRoundWinner } = state.state;

  return (
    <div className="match-container">
      <div className="match-header">
        <div className="match-header-info">
          <div className="match-round-counter">
            <span className="match-round-label">Round:</span>
            <span className="match-round-value">{round.number}</span>
          </div>
          {previousRoundWinner && (
            <div className="match-last-winner">
              <span className="match-winner-label">Last Winner:</span>
              <span className="match-winner-name">{previousRoundWinner}</span>
            </div>
          )}
        </div>
      </div>

      <div className="match-content">
        <div className="match-players-panel">
          <h3>Players</h3>
          <div className="match-players-list">
            {round.players.map((player) => (
              <div
                key={player.userId}
                className={`match-player-item ${
                  player.userId === userId ? "match-player-you" : ""
                } ${
                  currentTurn?.userId === player.userId
                    ? "match-player-current"
                    : ""
                }`}
              >
                <span className="match-player-name">{player.name}</span>
                {player.userId === userId && (
                  <span className="match-player-tag">You</span>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="match-game-panel">
          {userTurn && currentTurn ? (
            <RollScreen
              round={round}
              currentTurn={currentTurn}
              selectedDice={selectedDice}
              lastRoll={lastRoll}
              rolling={rolling}
              onDiceToggle={handleDiceToggle}
              onRoll={handleRoll}
              onEnd={handleEnd}
            />
          ) : (
            <WaitingScreen
              currentPlayer={currentPlayer}
              playersUntilTurn={playersUntilTurn}
            />
          )}
        </div>
      </div>
    </div>
  );
}
