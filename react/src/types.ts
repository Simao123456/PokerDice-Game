export interface Hand {
  handId: number;
  roundId: number;
  userId: number;
  faces: string;
  rank: number;
  tieBreaker: number | null;
}

export interface Invitation {
  code: string;
  isActive: boolean;
}

export interface Lobby {
  lobbyId: number;
  name: string;
  description: string | null;
  hostId: number;
  minPlayers: number;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
  status: "WAITING" | "ONGOING";
  createdAt: number;
}

export interface Match {
  matchId: number;
  lobbyId: number;
  startingPlayerUserId: number;
  currentRoundId: number | null;
  status: "ONGOING" | "FINISHED";
  createdAt: number;
  finishedAt: number | null;
}

export interface Roll {
  rollId: number;
  turnId: number;
  createdAt: number;
  heldMask: string;
  diceValues: string;
}

export interface Round {
  roundId: number;
  matchId: number;
  number: number;
  blind: number;
  pot: number;
  winnerUserId: number | null;
}

export interface Session {
  sessionId: string;
  userId: number;
  createdAt: string;
  lastUsedAt: string;
  expireAt: number;
  revoked: boolean;
}

export interface Turn {
  turnId: number;
  roundId: number;
  userId: number;
  number: number;
  state: string;
  rollCount: number;
}

export interface User {
  userId: number;
  name: string;
  password: string;
  email: string;
  balance: number;
}

export interface RegisterRequest {
  name: string;
  password: string;
  email: string;
  invitationCode: string;
}

export interface RegisterResponse {
  userId: number;
  name: string;
  token: string;
}

export interface Invitation {
  code: string;
  isActive: boolean;
}

export interface CreateInvitationResponse {
  code: string;
  isActive: boolean;
}

export interface LoginResponse {
  data: {
    userId: number;
    username: string;
    token: string;
  };
}

export interface UserLoginInput {
  username: string;
  password: string;
}

export interface LobbyDetails {
  lobbyId: number;
  name: string;
  description: string | null;
  hostId: number;
  minPlayers: number;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
  status: "WAITING" | "ONGOING";
  createdAt: number;
  playerCount: number;
  currentUserInLobby?: boolean;
}

export interface LobbiesResponse {
  data: LobbyDetails[];
}

export interface LobbyCreateInput {
  name: string;
  description?: string;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
}

export interface ApiResponse<T> {
  data: T;
  meta?: {
    total?: number;
    limit?: number;
    skip?: number;
  };
}
export interface JoinedResponse {
  data: {
    lobbyId: number;
  };
  meta: {
    message: string;
  };
}

export interface MatchStartedResponse {
  data: {
    lobbyId: number;
    matchId: number;
  };
  meta: {
    message: string;
  };
}

export interface LeaveLobbyResponse {
  data: null;
  meta: {
    message: string;
  };
}

export type JoinLobbyResponse = JoinedResponse | MatchStartedResponse;

export interface PlayerInfo {
  userId: number;
  name: string;
  email: string;
  balance: number;
}

export interface LobbyWithPlayers {
  lobbyId: number;
  name: string;
  description: string | null;
  hostId: number;
  minPlayers: number;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
  status: "WAITING" | "ONGOING";
  createdAt: number;
  players: PlayerInfo[];
}

export interface LobbyDetailsResponse {
  data: {
    value: LobbyWithPlayers;
  };
  meta: Record<string, never>;
}

export interface LeaveLobbyResponse {
  data: null;
  meta: {
    message: string;
  };
}

export interface RoundWithDetails {
  roundId: number;
  number: number;
  blind: number;
  pot: number;
  winnerUserId: number | null;
  currentTurn: Turn | null;
  players: PlayerInfo[];
  hands: Hand[];
}

export interface MatchState {
  round: RoundWithDetails | null;
  previousRoundWinner: string | null;
  currentTurn: Turn | null;
  selectedDice: boolean[];
  lastRoll: string | null;
  rolling: boolean;
}

export interface MatchWithPlayers {
  matchId: number;
  lobbyId: number;
  status: "ONGOING" | "FINISHED";
  players: PlayerInfo[];
}

export interface RollRequestInput {
  heldMask?: string;
}

export interface RollResponse {
  rollId: number;
  turnId: number;
  createdAt: number;
  heldMask: string;
  diceValues: string;
}

export interface MatchStateResponse {
  matchStatus: "ONGOING" | "FINISHED";
  round: RoundWithDetails;
  currentTurn: Turn | null;
  lastRoll: string | null;
  previousRoundWinner: string | null;
  playersUntilUserTurn: number;
  isMatchEnded: boolean;
}

export interface MatchDataResult {
  round: RoundWithDetails;
  currentTurn: Turn | null;
  lastRoll: string | null;
  previousRoundWinner: string | null;
  isMatchEnded: boolean;
}
