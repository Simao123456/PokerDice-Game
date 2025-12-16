import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import { AuthProvider } from "./AuthContext";
import { Layout } from "./components/Layout";
import { Login } from "./components/Login";
import { LobbyList } from "./components/LobbyList";
import { Rules } from "./components/Rules";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { HomePage } from "./components/Home";
import { LobbyDetails } from "./components/LobbyDetails";
import { Match } from "./components/Match";
import { CreateLobby } from "./components/CreateLobby";
import { Register } from "./components/Register.tsx";
import { CreateInvitation } from "./components/CreateInvitation.tsx";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: "login",
        element: <Login />,
      },
      {
        path: "register",
        element: <Register />,
      },
      {
        path: "invitations/create",
        element: (
          <ProtectedRoute>
            <CreateInvitation />
          </ProtectedRoute>
        ),
      },
      {
        path: "lobbies",
        element: (
          <ProtectedRoute>
            <LobbyList />
          </ProtectedRoute>
        ),
      },
      {
        path: "rules",
        element: <Rules />,
      },
      {
        path: "lobbies/:id",
        element: (
          <ProtectedRoute>
            <LobbyDetails />
          </ProtectedRoute>
        ),
      },
      {
        path: "lobbies/create",
        element: (
          <ProtectedRoute>
            <CreateLobby />
          </ProtectedRoute>
        ),
      },
      {
        path: "matches/:matchId",
        element: (
          <ProtectedRoute>
            <Match />
          </ProtectedRoute>
        ),
      },
    ],
  },
]);

createRoot(document.getElementById("container")!).render(
  <AuthProvider>
    <RouterProvider router={router} />
  </AuthProvider>
);
