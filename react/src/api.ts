import {
  LobbiesResponse,
  LoginResponse,
  UserLoginInput,
  LobbyCreateInput,
  ApiResponse,
  LobbyDetails,
  JoinLobbyResponse,
  LeaveLobbyResponse,
  LobbyDetailsResponse,
  MatchWithPlayers,
  RegisterRequest,
  RegisterResponse,
  CreateInvitationResponse,
  RoundWithDetails,
  Round,
  Turn,
  RollResponse,
} from "./types";

const API_BASE_URL = "/api";
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export async function fetchApi<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem("authToken");

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    let errorMessage = response.statusText;

    try {
      const errorBody = await response.json();
      errorMessage = errorBody?.detail || errorBody?.title || errorMessage;
    } catch {}

    throw new ApiError(response.status, errorMessage);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const api = {
  register(data: RegisterRequest): Promise<ApiResponse<RegisterResponse>> {
    return fetchApi<ApiResponse<RegisterResponse>>("/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
  },

  createInvitation(): Promise<ApiResponse<CreateInvitationResponse>> {
    return fetchApi<ApiResponse<CreateInvitationResponse>>(
      "/users/invitations",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }
    );
  },

  login(input: UserLoginInput): Promise<LoginResponse> {
    return fetchApi<LoginResponse>("/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },

  logout(): Promise<void> {
    return fetchApi<void>("/users/logout", {
      method: "POST",
    });
  },

  getAllLobbies(): Promise<LobbiesResponse> {
    return fetchApi<LobbiesResponse>("/lobbies");
  },

  joinLobby(lobbyId: number): Promise<JoinLobbyResponse> {
    return fetchApi<JoinLobbyResponse>(`/lobbies/${lobbyId}/join`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    });
  },

  getLobbyById(lobbyId: number): Promise<LobbyDetailsResponse> {
    return fetchApi<LobbyDetailsResponse>(`/lobbies/${lobbyId}`);
  },

  leaveLobby(lobbyId: number): Promise<LeaveLobbyResponse> {
    return fetchApi<LeaveLobbyResponse>(`/lobbies/${lobbyId}/leave`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    });
  },

  createLobby(input: LobbyCreateInput): Promise<ApiResponse<LobbyDetails>> {
    return fetchApi<ApiResponse<LobbyDetails>>("/lobby", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  getMatch(matchId: number): Promise<ApiResponse<MatchWithPlayers>> {
    return fetchApi<ApiResponse<MatchWithPlayers>>(`/matches/${matchId}`);
  },

  getCurrentRound(matchId: number): Promise<ApiResponse<RoundWithDetails>> {
    return fetchApi<ApiResponse<RoundWithDetails>>(
      `/matches/${matchId}/rounds/current`
    );
  },

  listRounds(matchId: number): Promise<ApiResponse<Round[]>> {
    return fetchApi<ApiResponse<Round[]>>(`/matches/${matchId}/rounds`);
  },

  getRoundDetails(
    matchId: number,
    roundId: number
  ): Promise<ApiResponse<RoundWithDetails>> {
    return fetchApi<ApiResponse<RoundWithDetails>>(
      `/matches/${matchId}/rounds/${roundId}`
    );
  },

  getCurrentTurn(matchId: number): Promise<ApiResponse<Turn>> {
    return fetchApi<ApiResponse<Turn>>(`/matches/${matchId}/turns/current`);
  },

  rollDice(
    matchId: number,
    heldMask?: string
  ): Promise<ApiResponse<RollResponse>> {
    const body = heldMask !== undefined ? { heldMask } : undefined;
    return fetchApi<ApiResponse<RollResponse>>(
      `/matches/${matchId}/turns/current/roll`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
      }
    );
  },

  getLastRoll(matchId: number): Promise<ApiResponse<RollResponse>> {
    return fetchApi<ApiResponse<RollResponse>>(`/matches/${matchId}/last-roll`);
  },
};
