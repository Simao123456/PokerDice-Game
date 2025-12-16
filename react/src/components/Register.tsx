import { useState, FormEvent } from "react";
import { useNavigate } from "react-router";
import { api } from "../api";
import "../styles/styles.css";

export function Register() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    name: "",
    password: "",
    email: "",
    invitationCode: "",
  });

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.name.trim()) {
      setError("O nome é obrigatório");
      return;
    }
    if (formData.password.length < 6) {
      setError("A password deve ter pelo menos 6 caracteres");
      return;
    }
    if (!formData.email.trim() || !formData.email.includes("@")) {
      setError("Email inválido");
      return;
    }
    if (!formData.invitationCode.trim()) {
      setError("O código de convite é obrigatório");
      return;
    }

    setIsLoading(true);

    try {
      await api.register({
        name: formData.name.trim(),
        password: formData.password,
        email: formData.email.trim(),
        invitationCode: formData.invitationCode.trim(),
      });

      navigate("/login");
    } catch (err) {
      //TODO()
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange =
    (field: keyof typeof formData) =>
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setFormData((prev) => ({
        ...prev,
        [field]: e.target.value,
      }));
      if (error) setError("");
    };

  return (
    <div>
      <div>
        <h1>Criar Conta</h1>

        <form onSubmit={handleSubmit}>
          {error && (
            <div>
              <p>{error}</p>
            </div>
          )}

          <div>
            <label htmlFor="name">Nome de Utilizador</label>
            <input
              id="name"
              type="text"
              value={formData.name}
              onChange={handleChange("name")}
              disabled={isLoading}
              autoComplete="username"
            />
          </div>

          <div>
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={formData.email}
              onChange={handleChange("email")}
              disabled={isLoading}
              autoComplete="email"
            />
          </div>

          <div>
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={formData.password}
              onChange={handleChange("password")}
              disabled={isLoading}
              autoComplete="new-password"
            />
          </div>

          <div>
            <label htmlFor="invitationCode">Código de Convite</label>
            <input
              id="invitationCode"
              type="text"
              value={formData.invitationCode}
              onChange={handleChange("invitationCode")}
              disabled={isLoading}
              autoComplete="off"
            />
          </div>

          <button type="submit" disabled={isLoading}>
            {isLoading ? "A criar conta..." : "Criar Conta"}
          </button>
        </form>
      </div>
    </div>
  );
}
