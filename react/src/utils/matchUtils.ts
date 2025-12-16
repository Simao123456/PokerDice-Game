import { RoundWithDetails, Turn, PlayerInfo, Round, MatchDataResult } from "../types";
import { api } from "../api";

export function isUserTurn(currentTurn: Turn | null, userId: number | null): boolean {
  return !!(currentTurn && userId && 
    currentTurn.userId === userId && 
    currentTurn.state?.toLowerCase() === "active");
}

export function getCurrentPlayer(
  round: RoundWithDetails,
  currentTurn: Turn | null
): PlayerInfo | undefined {
  return currentTurn ? round.players.find((p) => p.userId === currentTurn.userId) : undefined;
}

export function calculatePlayersUntilTurn(
  round: RoundWithDetails,
  currentTurn: Turn | null,
  userId: number | null
): number {
  if (!currentTurn || !userId) return 0;

  const currentIndex = round.players.findIndex((p) => p.userId === currentTurn.userId);
  const userIndex = round.players.findIndex((p) => p.userId === userId);

  if (currentIndex === -1 || userIndex === -1) return 0;
  if (currentIndex === userIndex) return 0;

  return (userIndex - currentIndex + round.players.length) % round.players.length;
}

export function createHeldMask(selectedDice: boolean[]): string {
  return selectedDice.map((held) => (held ? "1" : "0")).join("");
}

export async function checkMatchFinished(matchId: number): Promise<boolean> {
  try {
    const matchResponse = await api.getMatch(matchId);
    return matchResponse.data.status === "FINISHED";
  } catch (err: any) {
    console.warn("Could not fetch match status:", err.message || err);
    return false;
  }
}

export function isMatchEnded(round: RoundWithDetails, rounds: Round[] | null): boolean {
  if (round.winnerUserId === null || round.currentTurn !== null || !rounds) {
    return false;
  }
  const maxRoundNumber = Math.max(...rounds.map(r => r.number));
  return round.number === maxRoundNumber;
}

export async function fetchLastRollForUser(
  matchId: number,
  currentTurn: Turn | null,
  userId: number
): Promise<string | null> {
  if (!currentTurn || currentTurn.userId !== userId || currentTurn.rollCount === 0) {
    return null;
  }
  
  try {
    const lastRollResponse = await api.getLastRoll(matchId);
    return lastRollResponse.data.diceValues;
  } catch (err: any) {
    console.warn("Could not fetch last roll:", err.message || err);
    return null;
  }
}

export function getPreviousRoundWinner(
  round: RoundWithDetails,
  rounds: Round[] | null
): string | null {
  if (round.number <= 1 || !rounds) {
    return null;
  }
  
  const previousRound = rounds.find((r) => r.number === round.number - 1);
  if (!previousRound || !previousRound.winnerUserId) {
    return null;
  }
  
  const winner = round.players.find((p) => p.userId === previousRound.winnerUserId);
  return winner?.name || null;
}

export async function fetchMatchData(
  matchId: number,
  userId: number
): Promise<MatchDataResult> {
  const isFinished = await checkMatchFinished(matchId);
  if (isFinished) {
    throw new Error("MATCH_FINISHED");
  }
  
  const roundResponse = await api.getCurrentRound(matchId);
  const round = roundResponse.data;
  const currentTurn = round.currentTurn;
  
  let rounds: Round[] | null = null;
  try {
    const roundsResponse = await api.listRounds(matchId);
    rounds = roundsResponse.data;
  } catch (err: any) {
    console.warn("Could not fetch rounds list:", err.message || err);
  }
  
  const matchEnded = isMatchEnded(round, rounds);
  if (matchEnded) {
    throw new Error("MATCH_ENDED");
  }
  
  const lastRoll = await fetchLastRollForUser(matchId, currentTurn, userId);

  const previousRoundWinner = getPreviousRoundWinner(round, rounds);
  
  return {
    round,
    currentTurn,
    lastRoll,
    previousRoundWinner,
    isMatchEnded: false,
  };
}
