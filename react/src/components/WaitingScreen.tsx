import { PlayerInfo } from "../types";

interface WaitingScreenProps {
  currentPlayer: PlayerInfo | undefined;
  playersUntilTurn: number;
}

export function WaitingScreen({
  currentPlayer,
  playersUntilTurn,
}: WaitingScreenProps) {
  return (
    <div className="match-waiting-panel">
      <h3 className="match-waiting-title">Waiting for your turn</h3>
      {currentPlayer && (
        <div className="match-waiting-info">
          <p className="match-waiting-current">
            {`${currentPlayer.name} is rolling`}
          </p>
          {playersUntilTurn > 0 && (
            <p className="match-waiting-count">
              {playersUntilTurn} player{playersUntilTurn !== 1 ? "s" : ""} until
              your turn
            </p>
          )}
        </div>
      )}
    </div>
  );
}
