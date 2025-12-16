import { Link, Outlet, useNavigate } from "react-router";
import { useAuth } from "../AuthContext";
import "../styles/styles.css";

export function Layout() {
  const { isAuthenticated, username, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div className="layout-container">
      <nav className="layout-nav">
        <div className="layout-nav-left">
          <Link to="/" className="layout-nav-link">
            <h1 className="layout-nav-title">Pokerdice App</h1>
          </Link>
          <div className="layout-nav-links">
            {isAuthenticated && (
              <>
                <Link to="/lobbies" className="layout-nav-link">
                  Lobbies
                </Link>
                <Link to="/invitations/create" className="layout-nav-link">
                  Criar Convite
                </Link>
              </>
            )}
            <Link to="/rules" className="layout-nav-link">
              Regras
            </Link>
          </div>
        </div>

        <div className="layout-nav-right">
          {isAuthenticated ? (
            <>
              <span className="layout-username">Olá, {username}</span>
              <button onClick={handleLogout} className="layout-logout-btn">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="layout-nav-link">
                Login{" "}
              </Link>
              <Link to="/register" className="layout-nav-link">
                Registar
              </Link>
            </>
          )}
        </div>
      </nav>
      <div className="layout-main">
        <Outlet />
      </div>
    </div>
  );
}
