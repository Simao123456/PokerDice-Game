import { useNavigate } from "react-router";

export function MatchEndScreen() {
  const navigate = useNavigate();

  return (
    <div className="match-container">
      <div className="match-ended">
        <h2 className="match-ended-title">Match Ended</h2>
        <p className="match-ended-message">
          The match has ended. Thank you for playing!
        </p>
        <button
          className="match-ended-btn"
          onClick={() => navigate("/lobbies")}
        >
          Back to Lobbies
        </button>
      </div>
    </div>
  );
}
