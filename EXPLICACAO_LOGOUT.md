# 🚪 EXPLICAÇÃO COMPLETA: LOGOUT

## 🎯 Visão Geral do Fluxo

```
Frontend (Layout) → API (HTTP Controller) → Service → Repository → Database
                                                              ↓
                                                    Marca token como revoked
                                                              ↓
                                                    Limpa cookie no browser
```

**Diferente do login**, o logout:
- **Invalida token no servidor** (marca como `revoked = true`)
- **Limpa cookie no browser** (Set-Cookie com Max-Age=0)
- **Limpa estado local** (localStorage + estado React)
- **Sempre limpa estado local** (mesmo se API falhar)

---

## 📍 PARTE 1: FRONTEND - Layout.tsx

### Botão de Logout:

```typescript
export function Layout() {
  const { isAuthenticated, username, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();  // Chama função do AuthContext
    navigate("/login");  // Redireciona para login
  };

  return (
    <nav>
      {isAuthenticated && (
        <>
          <span>Olá, {username}</span>
          <button onClick={handleLogout}>Logout</button>
        </>
      )}
    </nav>
  );
}
```

**Conceitos:**

#### 1. **Layout Component**
- Componente que envolve todas as páginas
- Navbar sempre visível (não muda entre páginas)
- **Use case**: Logout disponível em qualquer página

#### 2. **Conditional Rendering**
```typescript
{isAuthenticated && (
  <>
    <span>Olá, {username}</span>
    <button onClick={handleLogout}>Logout</button>
  </>
)}
```
- Só mostra logout se user estiver autenticado
- **UX**: Não mostra botão se não estiver logado

---

## 📍 PARTE 2: AUTH CONTEXT - AuthContext.tsx

### Função `logout()`:

```typescript
const logout = async () => {
  try {
    await api.logout();  // Chama API para invalidar token
  } catch (error) {
    console.error("Error logging out:", error);
  } finally {
    // SEMPRE limpa estado, mesmo se API falhar
    localStorage.removeItem("authToken");
    localStorage.removeItem("userId");
    localStorage.removeItem("username");

    setToken(null);
    setUserId(null);
    setUsername(null);
    setIsAuthenticated(false);
  }
};
```

**Conceitos Importantes:**

#### 1. **`finally` Block**

```typescript
try {
  await api.logout();
} catch (error) {
  // Trata erro
} finally {
  // SEMPRE executa
  // Limpa estado local
}
```

**Por que `finally`?**
- **Garante**: Estado local é sempre limpo, mesmo se API falhar
- **Segurança**: Se servidor estiver down, user ainda é deslogado localmente
- **UX**: User não fica "preso" autenticado se API falhar

**Cenário sem `finally`:**
```typescript
try {
  await api.logout();
  // Limpa estado aqui
} catch (error) {
  // Se API falhar, estado NÃO é limpo ❌
  // User fica autenticado localmente mas token inválido no servidor
}
```

**Problema**: Estado inconsistente (autenticado localmente, mas token inválido)

---

#### 2. **Limpeza Completa**

```typescript
// 1. Limpa localStorage
localStorage.removeItem("authToken");
localStorage.removeItem("userId");
localStorage.removeItem("username");

// 2. Limpa estado React
setToken(null);
setUserId(null);
setUsername(null);
setIsAuthenticated(false);
```

**Por que ambos?**
- **localStorage**: Persistência (sobrevive a refresh)
- **Estado React**: Reatividade (componentes re-renderizam)

**Se só limpar localStorage:**
- Componentes não re-renderizam imediatamente
- Navbar ainda mostra "Olá, {username}"

**Se só limpar estado React:**
- Após refresh, user volta a estar autenticado (localStorage ainda tem dados)

**Solução**: Limpar ambos para garantir consistência

---

#### 3. **Tratamento de Erros**

```typescript
catch (error) {
  console.error("Error logging out:", error);
}
```

**Por que não mostrar erro ao user?**
- Logout é operação crítica (segurança)
- **Prioridade**: Deslogar localmente (mesmo se servidor falhar)
- **UX**: User não precisa saber se servidor falhou (já está deslogado localmente)

**Alternativa (mostrar erro):**
```typescript
catch (error) {
  alert("Erro ao fazer logout. Tente novamente.");
  // ❌ Problema: User fica autenticado se não limpar estado
}
```

---

## 📍 PARTE 3: CLIENTE API - api.ts

### Função `api.logout()`:

```typescript
logout(): Promise<void> {
  return fetchApi<void>("/users/logout", {
    method: "POST",
  });
}
```

**Conceitos:**

#### 1. **Token Enviado Automaticamente**
- `fetchApi` adiciona `Authorization: Bearer token` automaticamente
- Token vem de `localStorage.getItem("authToken")`
- **Importante**: Token é necessário para logout (servidor precisa saber qual token invalidar)

#### 2. **Response Void**
- `Promise<void>` - não retorna dados
- HTTP 204 (No Content) ou 200 com body vazio
- **Por que?** Logout não precisa retornar dados

---

## 📍 PARTE 4: HTTP LAYER - UserController.kt

### Endpoint: `POST /api/users/logout`

```kotlin
@PostMapping(Uris.User.LOGOUT)
fun logout(authUser: AuthenticatedUser): ResponseEntity<*> =
    try {
        userService.revokeToken(authUser.token)
        ResponseEntity
            .ok()
            .header(
                "Set-Cookie",
                "AuthCookie=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=/",
            ).body(ApiResponse(data = "Logged out successfully"))
    } catch (ex: Exception) {
        ResponseEntity
            .internalServerError()
            .header(
                "Set-Cookie",
                "AuthCookie=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=/",
            ).body(Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.internalServerError))
    }
```

**Conceitos Importantes:**

### 1. **Try-Catch com Finally Implícito**

```kotlin
try {
    userService.revokeToken(authUser.token)
    // Retorna sucesso
} catch (ex: Exception) {
    // Retorna erro
}
```

**Por que try-catch?**
- `revokeToken` pode falhar (ex: BD indisponível)
- **Mas**: Cookie é sempre limpo (mesmo se revokeToken falhar)

**Por que limpar cookie mesmo em erro?**
- **Segurança**: Cookie deve ser removido mesmo se servidor falhar
- **UX**: Browser não envia cookie em requests futuros
- **Consistência**: Estado local já foi limpo (frontend)

---

### 2. **Set-Cookie para Limpar Cookie**

```kotlin
.header(
    "Set-Cookie",
    "AuthCookie=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=/",
)
```

**Como funciona:**
- `AuthCookie=` - valor vazio
- `Max-Age=0` - expira imediatamente
- Browser remove cookie automaticamente

**Atributos mantidos:**
- `HttpOnly` - mantém segurança
- `Secure` - mantém segurança
- `SameSite=Strict` - mantém segurança
- `Path=/` - garante que cookie é removido em todo o domínio

**Por que manter atributos?**
- Garante que cookie é removido corretamente
- Mantém consistência com cookie original

---

### 3. **Cookie Limpo em Ambos os Casos**

```kotlin
try {
    // ...
    .header("Set-Cookie", "AuthCookie=; ... Max-Age=0; ...")
} catch (ex: Exception) {
    // ...
    .header("Set-Cookie", "AuthCookie=; ... Max-Age=0; ...")  // ← MESMO cookie limpo
}
```

**Por que?**
- Cookie deve ser removido **sempre** (sucesso ou erro)
- **Segurança**: Token não deve permanecer no browser após logout
- **Consistência**: Estado local já foi limpo

---

## 📍 PARTE 5: SERVICE LAYER - UserService.kt

### Função `revokeToken()`:

```kotlin
fun revokeToken(token: String): Boolean {
    val tokenValidationInfo: TokenValidationInfo = 
        usersDomain.createTokenValidationInformation(token)
    
    return transactionManager.run {
        it.usersRepository.removeTokenByValidationInfo(
            tokenValidationInfo, 
            clock.now()
        )
        true
    }
}
```

**Conceitos:**

#### 1. **Token Validation Info**

```kotlin
usersDomain.createTokenValidationInformation(token)
```

**O que faz:**
- Faz hash SHA-256 do token
- Cria `TokenValidationInfo` com hash
- **Por que?** Token original nunca é guardado na BD (só hash)

**Fluxo:**
```
Token original: "abc123..."
    ↓ SHA-256
Hash: "xyz789..."
    ↓ Busca na BD
Session com session_id = "xyz789..."
    ↓ UPDATE
SET revoked = true
```

---

#### 2. **Retorno Boolean**

```kotlin
return transactionManager.run {
    it.usersRepository.removeTokenByValidationInfo(...)
    true  // ← Sempre retorna true
}
```

**Por que sempre `true`?**
- Se token não existir, UPDATE não afeta linhas (mas não é erro)
- **Comportamento**: Logout sempre "succeeds" (idempotente)
- **Segurança**: Não revela se token existia ou não

**Alternativa (retornar número de linhas afetadas):**
```kotlin
val rowsAffected = it.usersRepository.removeTokenByValidationInfo(...)
return rowsAffected > 0  // ← Revela se token existia
```

**Problema**: Revela informação (token válido vs inválido)

---

#### 3. **Transação**

```kotlin
transactionManager.run { ... }
```

**Por que transação?**
- Operação simples (apenas UPDATE)
- Mas garante atomicidade
- Se falhar, rollback automático

**Neste caso**: Transação é simples (apenas 1 operação), mas mantém padrão consistente

---

## 📍 PARTE 6: REPOSITORY LAYER

### Função `removeTokenByValidationInfo()`:

```kotlin
override fun removeTokenByValidationInfo(
    token: TokenValidationInfo,
    now: Instant,
): Int =
    handle
        .createUpdate(
            """
            UPDATE Sessions 
            SET revoked = true, last_used_at = :now
            WHERE session_id = :sessionId
            """.trimIndent(),
        )
        .bind("sessionId", token.validationInfo)  // Hash do token
        .bind("now", now.toEpochMilliseconds())
        .execute()
```

**Conceitos Importantes:**

#### 1. **UPDATE em vez de DELETE**

```sql
UPDATE Sessions SET revoked = true
```

**Por que não DELETE?**
- **Auditoria**: Mantém histórico de sessões
- **Debugging**: Pode investigar quando token foi revogado
- **Análise**: Pode analisar padrões de uso

**Se fizesse DELETE:**
- Perde histórico
- Não sabe quando token foi usado pela última vez
- Difícil debuggar problemas

**Trade-off:**
- ✅ Mantém histórico
- ⚠️ Tabela cresce (mas pode limpar sessões antigas periodicamente)

---

#### 2. **Atualização de `last_used_at`**

```sql
SET revoked = true, last_used_at = :now
```

**Por que atualizar `last_used_at`?**
- **Consistência**: Última ação no token foi logout
- **Auditoria**: Sabe exatamente quando token foi revogado
- **Análise**: Pode calcular tempo de sessão (created_at → last_used_at)

---

#### 3. **WHERE Clause**

```sql
WHERE session_id = :sessionId
```

**O que faz:**
- Busca sessão pelo hash do token
- Atualiza apenas essa sessão
- **Segurança**: Não afeta outras sessões do mesmo user

**Se não tivesse WHERE:**
- Atualizaria todas as sessões (não queremos isso!)
- User perderia todas as sessões (desktop + mobile)

---

## 📍 PARTE 7: VALIDAÇÃO DO TOKEN REVOGADO

### Como Token Revogado é Detectado?

**No `getUserByToken()` (já explicado no login):**

```kotlin
fun getUserByToken(token: String): User? {
    // ...
    return transactionManager.run { tm ->
        val tokenValidationInfo = usersDomain.createTokenValidationInformation(token)
        
        // VERIFICA SE TOKEN ESTÁ REVOGADO
        if (tm.usersRepository.isTokenExpired(tokenValidationInfo)) {
            return@run null
        }
        // ...
    }
}
```

**Função `isTokenExpired()`:**

```kotlin
override fun isTokenExpired(tokenValidationInfo: TokenValidationInfo): Boolean =
    handle
        .createQuery(
            """
            SELECT 
                revoked = true OR 
                expires_at < :currentTime 
            FROM Sessions 
            WHERE session_id = :token
            """,
        )
        .bind("token", tokenValidationInfo.validationInfo)
        .bind("currentTime", System.currentTimeMillis())
        .mapTo<Boolean>()
        .singleOrNull() ?: true
```

**Conceitos:**

#### 1. **Verificação de Revoked**

```sql
revoked = true OR expires_at < :currentTime
```

**Duas condições:**
- `revoked = true`: Token explicitamente revogado (logout)
- `expires_at < :currentTime`: Token expirado por tempo

**Ambos invalidam token:**
- Se qualquer um for `true`, token é inválido
- **OR**: Token inválido se revogado OU expirado

---

#### 2. **Retorno `?: true`**

```kotlin
.mapTo<Boolean>()
.singleOrNull() ?: true
```

**O que faz:**
- Se sessão não existe (`singleOrNull()` retorna `null`)
- Retorna `true` (token é considerado expirado/inválido)
- **Segurança**: Se token não existe, trata como inválido

**Por que `true`?**
- **Fail-secure**: Se não encontra sessão, assume inválido
- **Segurança**: Melhor rejeitar token válido que aceitar inválido

---

## 🔄 FLUXO COMPLETO RESUMIDO

```
1. User clica "Logout" (Layout.tsx)
   ↓
2. handleLogout() → logout() do AuthContext
   ↓
3. api.logout() → POST /api/users/logout
   ↓
4. fetchApi adiciona Authorization: Bearer token
   ↓
5. AuthenticationInterceptor valida token
   ↓
6. UserController.logout() recebe AuthenticatedUser
   ↓
7. UserService.revokeToken(authUser.token)
   ↓
8. UserDomain.createTokenValidationInformation() → hash SHA-256
   ↓
9. TransactionManager.run { ... }
   ↓
10. UsersRepository.removeTokenByValidationInfo()
   ↓
11. UPDATE Sessions SET revoked = true WHERE session_id = hash
   ↓
12. Transaction commit
   ↓
13. UserService retorna true
   ↓
14. UserController retorna HTTP 200 + Set-Cookie (limpa cookie)
   ↓
15. Frontend recebe resposta
   ↓
16. finally block executa (sempre!)
   ↓
17. localStorage.removeItem() (limpa todos os dados)
   ↓
18. setToken(null), setUserId(null), etc. (limpa estado React)
   ↓
19. setIsAuthenticated(false)
   ↓
20. navigate("/login")
```

---

## 🔄 FLUXO COM ERRO (API Falha)

```
1. User clica "Logout"
   ↓
2. api.logout() → POST /api/users/logout
   ↓
3. API falha (ex: servidor down, timeout)
   ↓
4. catch block executa
   ↓
5. console.error("Error logging out:", error)
   ↓
6. finally block executa (SEMPRE!)
   ↓
7. localStorage.removeItem() (limpa todos os dados)
   ↓
8. setToken(null), etc. (limpa estado React)
   ↓
9. setIsAuthenticated(false)
   ↓
10. navigate("/login")
```

**Resultado**: User é deslogado localmente, mesmo se servidor falhar!

**Problema potencial:**
- Token ainda válido no servidor (não foi revogado)
- **Mas**: User não consegue usar (localStorage limpo, não tem token)
- **Segurança**: Token expira naturalmente (TTL)

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **finally Block**: Garante execução mesmo se houver erro
2. **Idempotência**: Logout pode ser chamado múltiplas vezes sem efeito
3. **Fail-Secure**: Se não encontra token, trata como inválido
4. **UPDATE vs DELETE**: Mantém histórico (auditoria)
5. **Cookie Expiration**: Max-Age=0 remove cookie imediatamente
6. **State Cleanup**: Limpa localStorage E estado React
7. **Error Handling**: Limpa estado mesmo se API falhar

---

## 🔐 SEGURANÇA

- ✅ Token revogado no servidor (`revoked = true`)
- ✅ Cookie removido no browser (`Max-Age=0`)
- ✅ Estado local limpo (localStorage + React)
- ✅ Estado sempre limpo (mesmo se API falhar)
- ✅ Token não pode ser reutilizado após logout
- ✅ Cookie não é enviado em requests futuros

---

## 💡 DIFERENÇAS: LOGOUT vs LOGIN

| Aspecto | Login | Logout |
|---------|-------|--------|
| **Criação** | Cria nova sessão | Revoga sessão existente |
| **Token** | Gera novo token | Invalida token atual |
| **Cookie** | Cria cookie | Remove cookie |
| **Estado** | Define estado | Limpa estado |
| **Erro** | Falha se credenciais inválidas | Sempre limpa estado local (mesmo se API falhar) |

---

## 🎯 Pontos para a Discussão

1. **"Por que não fazer DELETE em vez de UPDATE?"**
   - Mantém histórico para auditoria
   - Facilita debugging
   - Pode analisar padrões de uso

2. **"Por que limpar estado mesmo se API falhar?"**
   - Segurança: User não fica "preso" autenticado
   - UX: User pode fazer login novamente
   - Token expira naturalmente (TTL)

3. **"E se token não existir na BD?"**
   - UPDATE não afeta linhas (0 rows)
   - Retorna `true` mesmo assim (idempotente)
   - Não revela se token existia (segurança)

4. **"Por que atualizar `last_used_at` no logout?"**
   - Consistência: Última ação foi logout
   - Auditoria: Sabe quando token foi revogado
   - Análise: Pode calcular duração da sessão

---

## 💬 Frases-Chave para a Discussão

- "Logout é idempotente: pode ser chamado múltiplas vezes sem efeito"
- "finally block garante que estado local é sempre limpo, mesmo se API falhar"
- "Token é revogado no servidor (UPDATE revoked=true) e cookie é removido no browser"
- "Fail-secure: se token não existe, trata como inválido"
- "UPDATE em vez de DELETE mantém histórico para auditoria"

---

Fim da explicação do logout! 🎉
