import { RoundWithDetails, Turn } from "../types";

interface RollScreenProps {
  round: RoundWithDetails;
  currentTurn: Turn;
  selectedDice: boolean[];
  lastRoll: string | null;
  rolling: boolean;
  onDiceToggle: (index: number) => void;
  onRoll: () => void;
  onEnd: () => void;
}

export function RollScreen({
  round,
  currentTurn,
  selectedDice,
  lastRoll,
  rolling,
  onDiceToggle,
  onRoll,
  onEnd,
}: RollScreenProps) {
  return (
    <div className="match-turn-panel">
      <div className="match-turn-header">
        <div className="match-pot-info">
          <span className="match-pot-label">Pot:</span>
          <span className="match-pot-value">{round.pot.toFixed(2)}</span>
        </div>
        <div className="match-rolls-info">
          <span className="match-rolls-label">Rolls:</span>
          <span className="match-rolls-value">{currentTurn.rollCount} / 3</span>
        </div>
      </div>

      <div className="match-dice-section">
        <h4 className="match-dice-title">Select dice:</h4>
        <div className="match-dice-container">
          {lastRoll
            ? lastRoll.split(",").map((value, index) => (
                <button
                  key={index}
                  className={`match-die ${
                    selectedDice[index] ? "match-die-selected" : ""
                  } ${currentTurn.rollCount === 0 ? "match-die-disabled" : ""}`}
                  onClick={() => onDiceToggle(index)}
                  disabled={currentTurn.rollCount === 0}
                >
                  {value.trim()}
                </button>
              ))
            : Array.from({ length: 5 }).map((_, index) => (
                <div key={index} className="match-die match-die-unknown">
                  ?
                </div>
              ))}
        </div>
      </div>

      <div className="match-actions">
        <button
          className="match-action-btn match-action-roll"
          onClick={onRoll}
          disabled={rolling || currentTurn.rollCount >= 3}
        >
          {rolling ? "Rolling..." : "Roll"}
        </button>
        <button
          className="match-action-btn match-action-end"
          onClick={onEnd}
          disabled={rolling || currentTurn.rollCount === 0}
        >
          End Turn
        </button>
      </div>
    </div>
  );
}
