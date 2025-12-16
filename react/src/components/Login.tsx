import { useReducer } from "react";
import { useNavigate } from "react-router";
import { api, ApiError } from "../api";
import { useAuth } from "../AuthContext";
import "../styles/styles.css";

type State = {
  username: string;
  password: string;
  error: string | undefined;
  stage: "editing" | "posting" | "succeed" | "failed";
};

type Action =
  | { type: "input-change"; username: string; password: string }
  | { type: "post" }
  | { type: "success" }
  | { type: "error"; message: string };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "input-change":
      return {
        ...state,
        username: action.username,
        password: action.password,
      };
    case "post":
      return {
        ...state,
        stage: "posting",
        error: undefined,
      };
    case "success":
      return {
        username: "",
        password: "",
        error: undefined,
        stage: "succeed",
      };
    case "error":
      return {
        ...state,
        stage: "failed",
        error: action.message,
      };
    default:
      return state;
  }
}

const initialState: State = {
  username: "",
  password: "",
  error: undefined,
  stage: "editing",
};

export function Login() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent | React.MouseEvent) => {
    e.preventDefault();
    dispatch({ type: "post" });

    try {
      const response = await api.login({
        username: state.username,
        password: state.password,
      });

      login(response.data.token, response.data.userId, response.data.username);

      dispatch({ type: "success" });
      navigate("/lobbies");
    } catch (err) {
      if (err instanceof ApiError) {
        dispatch({ type: "error", message: err.message });
      } else {
        dispatch({
          type: "error",
          message: "Ocorreu um erro durante o login",
        });
      }
    }
  };

  return (
    <div>
      <div>
        <h2>Login</h2>
        <div>
          <div>
            <label>
              Username:
              <input
                type="text"
                name="username"
                value={state.username}
                onChange={(e) =>
                  dispatch({
                    type: "input-change",
                    username: e.target.value,
                    password: state.password,
                  })
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleSubmit(e);
                }}
                required
                autoComplete="username"
              />
            </label>
          </div>

          <div>
            <label>
              Password:
              <input
                type="password"
                name="password"
                value={state.password}
                onChange={(e) =>
                  dispatch({
                    type: "input-change",
                    username: state.username,
                    password: e.target.value,
                  })
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleSubmit(e);
                }}
                required
                autoComplete="current-password"
              />
            </label>
          </div>

          {state.error && <div>{state.error}</div>}

          <button onClick={handleSubmit} disabled={state.stage === "posting"}>
            {state.stage === "posting" ? "A entrar..." : "Entrar"}
          </button>
        </div>

        <div>
          <p>So para testar entrar:</p>
          <p>Alice / teste</p>
        </div>
      </div>
    </div>
  );
}
