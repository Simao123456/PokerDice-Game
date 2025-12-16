import { useState } from "react";
import { api, ApiError } from "../api";

export function CreateInvitation() {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>("");
  const [generatedCode, setGeneratedCode] = useState<string>("");
  const [copied, setCopied] = useState(false);

  const handleGenerateCode = async () => {
    setError("");
    setGeneratedCode("");
    setCopied(false);
    setIsLoading(true);

    try {
      const response = await api.createInvitation();
      const code = response.data as any;

      setGeneratedCode(code);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(`Erro: ${err.message}`);
      } else {
        setError("Erro ao gerar código");
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopyCode = async () => {
    if (!generatedCode) return;

    try {
      await navigator.clipboard.writeText(generatedCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      alert("Erro ao copiar");
    }
  };

  return (
    <div>
      <h1>Gerar Código de Convite</h1>

      {error && <div>{error}</div>}

      {!generatedCode ? (
        <button onClick={handleGenerateCode} disabled={isLoading}>
          {isLoading ? "A gerar..." : "Gerar Código"}
        </button>
      ) : (
        <div>
          <p>Código gerado com sucesso</p>

          <div>
            <code>{generatedCode}</code>
          </div>

          <button onClick={handleCopyCode}>
            {copied ? "Copiado" : "Copiar"}
          </button>

          <button onClick={handleGenerateCode}>Gerar Outro</button>
        </div>
      )}
    </div>
  );
}
