import { useEffect, useReducer } from "react";
import { useNavigate } from "react-router";
import { api, ApiError } from "../api";
import "../styles/styles.css";

interface LobbyFormData {
  name: string;
  description: string;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
}

type State =
  | { tag: "editing"; form: LobbyFormData }
  | { tag: "submitting"; form: LobbyFormData }
  | { tag: "error"; form: LobbyFormData; message: string }
  | { tag: "redirect" };

type Action =
  | { type: "set_field"; field: keyof LobbyFormData; value: string | number }
  | { type: "submit" }
  | { type: "success" }
  | { type: "fail"; message: string };

const initialForm: LobbyFormData = {
  name: "",
  description: "",
  maxPlayers: 2,
  maxRounds: 5,
  timeoutSeconds: 30,
};

const initialState: State = { tag: "editing", form: initialForm };

function reducer(state: State, action: Action): State {
  switch (state.tag) {
    case "editing":
    case "error":
      switch (action.type) {
        case "set_field":
          return {
            tag: "editing",
            form: {
              ...state.form,
              [action.field]: action.value,
            },
          };
        case "submit":
          return { tag: "submitting", form: state.form };
        default:
          return state;
      }

    case "submitting":
      switch (action.type) {
        case "success":
          return { tag: "redirect" };
        case "fail":
          return {
            tag: "error",
            form: state.form,
            message: action.message,
          };
        default:
          return state;
      }

    case "redirect":
      return state;
  }
}

export function CreateLobby() {
  const navigate = useNavigate();
  const [state, dispatch] = useReducer(reducer, initialState);

  useEffect(() => {
    if (state.tag === "redirect") {
      navigate("/lobbies");
    }
  }, [state.tag, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (state.tag === "redirect") return;

    dispatch({ type: "submit" });

    try {
      await api.createLobby({
        name: state.form.name,
        description: state.form.description || undefined,
        maxPlayers: Number(state.form.maxPlayers),
        maxRounds: Number(state.form.maxRounds),
        timeoutSeconds: Number(state.form.timeoutSeconds),
      });

      dispatch({ type: "success" });
    } catch (err) {
      let msg = "Erro desconhecido ao criar lobby.";
      if (err instanceof ApiError) {
        msg = err.message;
      }
      dispatch({ type: "fail", message: msg });
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (state.tag === "submitting") return;

    const { name, value, type } = e.target;
    dispatch({
      type: "set_field",
      field: name as keyof LobbyFormData,
      value: type === "number" ? Number(value) : value,
    });
  };

  const isSubmitting = state.tag === "submitting";

  const formData = state.tag === "redirect" ? initialForm : state.form;

  if (state.tag === "redirect") {
    return null;
  }

  return (
    <div className="login-container">
      <div className="login-card" style={{ maxWidth: "600px" }}>
        <div className="lobby-list-header">
          <h2>Criar Novo Lobby</h2>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="login-form-group">
            <label className="login-form-label">Nome do Lobby *</label>
            <input
              type="text"
              name="name"
              className="login-form-input"
              value={formData.name}
              onChange={handleInputChange}
              placeholder="Ex: Mesa dos Campeões"
              disabled={isSubmitting}
            />
          </div>

          <div className="login-form-group">
            <label className="login-form-label">Descrição</label>
            <input
              type="text"
              name="description"
              className="login-form-input"
              value={formData.description}
              onChange={handleInputChange}
              placeholder="Opcional: Descrição curta"
              disabled={isSubmitting}
            />
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr 1fr",
              gap: "1rem",
            }}
          >
            <div className="login-form-group">
              <label className="login-form-label">Jogadores (2-6)</label>
              <input
                type="number"
                name="maxPlayers"
                min="2"
                max="6"
                required
                className="login-form-input"
                value={formData.maxPlayers}
                onChange={handleInputChange}
                disabled={isSubmitting}
              />
            </div>

            <div className="login-form-group">
              <label className="login-form-label">Rondas (1-20)</label>
              <input
                type="number"
                name="maxRounds"
                min="1"
                max="20"
                required
                className="login-form-input"
                value={formData.maxRounds}
                onChange={handleInputChange}
                disabled={isSubmitting}
              />
            </div>

            <div className="login-form-group">
              <label className="login-form-label">Timeout (s)</label>
              <input
                type="number"
                name="timeoutSeconds"
                min="5"
                max="120"
                required
                className="login-form-input"
                value={formData.timeoutSeconds}
                onChange={handleInputChange}
                disabled={isSubmitting}
              />
            </div>
          </div>

          {}
          {state.tag === "error" && (
            <div
              className="lobby-list-error"
              style={{ padding: "1rem", marginTop: "1rem" }}
            >
              <p style={{ margin: 0 }}>{state.message}</p>
            </div>
          )}

          <div style={{ marginTop: "2rem", display: "flex", gap: "1rem" }}>
            <button
              type="button"
              onClick={() => navigate("/lobbies")}
              className="retry-btn"
              disabled={isSubmitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="create-lobby-btn"
              style={{ flex: 1 }}
              disabled={isSubmitting}
            >
              {isSubmitting ? "A criar..." : "Criar Lobby"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
