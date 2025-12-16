import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { useNavigate } from "react-router";
import "../styles/styles.css";
import { LobbyDetails } from "../types.ts";

type LoadingState = "idle" | "loading" | "success" | "error";

export function LobbyList() {
  const navigate = useNavigate();
  const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [loadingState, setLoadingState] = useState<LoadingState>("idle");
  const [error, setError] = useState("");
  const [joiningLobbyId, setJoiningLobbyId] = useState<number | null>(null);

  useEffect(() => {
    fetchLobbies();
  }, []);

  async function fetchLobbies() {
    setLoadingState("loading");
    try {
      const response = await api.getAllLobbies();
      setLobbies(response.data);
      setLoadingState("success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Error loading lobbies");
      setLoadingState("error");
    }
  }

  const handleJoinLobby = async (lobbyId: number) => {
    setJoiningLobbyId(lobbyId);
    try {
      const response = await api.joinLobby(lobbyId);
      navigate(
        "matchId" in response.data
          ? `/matches/${response.data.matchId}`
          : `/lobbies/${lobbyId}`
      );
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Error");
    } finally {
      setJoiningLobbyId(null);
      fetchLobbies();
    }
  };

  const filteredLobbies = lobbies.filter((lobby) =>
    lobby.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  if (loadingState === "loading") {
    return (
      <div className="lobby-list-container">
        <div className="lobby-list-loading">
          <div className="spinner"></div>
        </div>
      </div>
    );
  }

  if (loadingState === "error") {
    return (
      <div className="lobby-list-container">
        <div className="lobby-list-error">
          <h3>Error</h3>
          <p>{error}</p>
          <button onClick={fetchLobbies} className="retry-btn">
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="lobby-list-container">
      <div className="lobby-list-header">
        <h2>Lobbies</h2>
        <button
          onClick={() => navigate("/lobbies/create")}
          className="create-lobby-btn"
        >
          Criar Lobby
        </button>
      </div>

      <input
        type="text"
        placeholder="Pesquisar..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        className="search-input"
      />

      {filteredLobbies.length === 0 ? (
        <div className="no-lobbies">
          <p>
            {searchQuery
              ? `Nenhum lobby encontrado`
              : "Sem lobbies disponíveis"}
          </p>
        </div>
      ) : (
        <div className="lobbies-grid">
          {filteredLobbies.map((lobby) => (
            <div key={lobby.lobbyId} className="lobby-card">
              <div className="lobby-card-header">
                <h3>{lobby.name}</h3>
                <span className={`lobby-status ${lobby.status.toLowerCase()}`}>
                  {lobby.status === "WAITING" ? "À espera" : "Em Jogo"}
                </span>
              </div>

              {lobby.description && (
                <p className="lobby-description">{lobby.description}</p>
              )}

              <div className="lobby-info">
                <div className="lobby-info-item">
                  <span className="info-label">Jogadores:</span>
                  <span className="info-value">
                    {lobby.playerCount} / {lobby.maxPlayers}
                  </span>
                </div>
                <div className="lobby-info-item">
                  <span className="info-label">Rondas:</span>
                  <span className="info-value">{lobby.maxRounds}</span>
                </div>
                <div className="lobby-info-item">
                  <span className="info-label">Timeout:</span>
                  <span className="info-value">{lobby.timeoutSeconds}s</span>
                </div>
              </div>

              {lobby.status === "WAITING" && (
                <div className="lobby-actions">
                  <button
                    onClick={() => handleJoinLobby(lobby.lobbyId)}
                    disabled={joiningLobbyId === lobby.lobbyId}
                    className="join-lobby-btn"
                  >
                    {joiningLobbyId === lobby.lobbyId ? "..." : "Entrar"}
                  </button>
                  <button
                    onClick={() => navigate(`/lobbies/${lobby.lobbyId}`)}
                    className="view-details-btn"
                  >
                    Detalhes
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
