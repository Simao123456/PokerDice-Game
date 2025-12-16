import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { api, ApiError } from "../api";
import { useAuth } from "../AuthContext";
import "../styles/styles.css";
import { LobbyWithPlayers } from "../types.ts";

type LoadingState = "idle" | "loading" | "success" | "error";

export function LobbyDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { userId } = useAuth();
  const [lobby, setLobby] = useState<LobbyWithPlayers | null>(null);
  const [loadingState, setLoadingState] = useState<LoadingState>("idle");
  const [isLeaving, setIsLeaving] = useState(false);

  useEffect(() => {
    if (id) {
      fetchLobbyDetails();
    }
  }, [id]);

  async function fetchLobbyDetails() {
    setLoadingState("loading");
    try {
      const response = await api.getLobbyById(parseInt(id!));
      setLobby(response.data.value);
      setLoadingState("success");
    } catch (err) {
      setLoadingState("error");
    }
  }

  const handleLeaveLobby = async () => {
    if (!lobby) return;

    const isHost = lobby.hostId === userId;
    const confirmMessage = isHost
      ? "És o host deste lobby. Ao sair, o lobby será eliminado para todos. Tens a certeza?"
      : "Tens a certeza que queres sair deste lobby?";

    if (!confirm(confirmMessage)) return;

    setIsLeaving(true);

    try {
      await api.leaveLobby(lobby.lobbyId);
      navigate("/lobbies");
    } catch (err) {
      if (err instanceof ApiError) {
        alert(`Erro: ${err.message}`);
      }
    } finally {
      setIsLeaving(false);
    }
  };

  if (loadingState === "idle" || loadingState === "loading") {
    return (
      <div className="lobby-details-container">
        <div className="lobby-details-loading">
          <div className="spinner"></div>
          <p>A carregar detalhes do lobby...</p>
        </div>
      </div>
    );
  }

  if (loadingState === "error" || !lobby) {
    return (
      <div className="lobby-details-container">
        <div className="lobby-details-error">
          <h3>Erro ao carregar lobby</h3>
          <button onClick={() => navigate("/lobbies")} className="back-btn">
            Voltar aos Lobbies
          </button>
        </div>
      </div>
    );
  }

  const isHost = lobby.hostId === userId;

  return (
    <div className="lobby-details-container">
      <div className="lobby-details-card">
        <div className="lobby-details-header">
          <div>
            <h2>{lobby.name}</h2>
            {lobby.description && (
              <p className="lobby-details-description">{lobby.description}</p>
            )}
          </div>
          <div className="lobby-details-badges">
            <span className={`lobby-status ${lobby.status.toLowerCase()}`}>
              {lobby.status === "WAITING" ? "À espera" : "Em Jogo"}
            </span>
            {isHost && <span className="host-badge">És o Host</span>}
          </div>
        </div>

        <div className="lobby-details-info">
          <div className="info-grid">
            <div className="info-card">
              <span className="info-label">Jogadores</span>
              <span className="info-value">
                {lobby.players.length} / {lobby.maxPlayers}
              </span>
            </div>
            <div className="info-card">
              <span className="info-label">Mínimo</span>
              <span className="info-value">{lobby.minPlayers}</span>
            </div>
            <div className="info-card">
              <span className="info-label">Rondas</span>
              <span className="info-value">{lobby.maxRounds}</span>
            </div>
            <div className="info-card">
              <span className="info-label">Timeout</span>
              <span className="info-value">{lobby.timeoutSeconds}s</span>
            </div>
          </div>
        </div>

        <div className="players-section">
          <h3>Jogadores no Lobby ({lobby.players.length})</h3>
          <div className="players-list">
            {lobby.players.map((player) => (
              <div key={player.userId} className="player-item">
                <div className="player-info">
                  <span className="player-name">{player.name}</span>
                  {player.userId === lobby.hostId && (
                    <span className="player-host-tag">Host</span>
                  )}
                  {player.userId === userId && (
                    <span className="player-you-tag">Tu</span>
                  )}
                </div>
                <span className="player-balance">{player.balance} fichas</span>
              </div>
            ))}
          </div>
        </div>

        {lobby.status === "WAITING" &&
          lobby.players.length < lobby.minPlayers && (
            <div className="waiting-message">
              <p>
                À espera de mais {lobby.minPlayers - lobby.players.length}{" "}
                jogadores
              </p>
            </div>
          )}

        {lobby.status === "WAITING" &&
          lobby.players.length >= lobby.minPlayers &&
          isHost && (
            <div className="ready-message">
              <p>
                ✓ Jogadores suficientes! O jogo começará quando o lobby encher
              </p>
            </div>
          )}

        <div className="lobby-details-actions">
          <button onClick={() => navigate("/lobbies")} className="back-btn">
            Voltar aos Lobbies
          </button>

          {lobby.status === "WAITING" && (
            <button
              onClick={handleLeaveLobby}
              disabled={isLeaving}
              className="leave-lobby-btn"
            >
              {isLeaving
                ? "A sair..."
                : isHost
                ? "Eliminar Lobby"
                : "Sair do Lobby"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
