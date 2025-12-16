# 🎮 EXPLICAÇÃO COMPLETA: JOIN LOBBY

## 🎯 Visão Geral do Fluxo

```
Frontend → API → Service → Validações → Join Lobby → Verifica se inicia Match
                                                          ↓
                                                    Se maxPlayers atingido:
                                                    Cria Match + Round + Turn
```

**Diferente de criar lobby**, o join lobby:
- **Pode ter 2 resultados**: Apenas join OU join + match iniciado
- **Múltiplas validações**: Lobby existe, não está cheio, não está em curso, user não está já no lobby
- **Auto-start**: Se atingir `maxPlayers`, inicia match automaticamente
- **Transação complexa**: Join + possível criação de Match/Round/Turn

---

## 📍 PARTE 1: FRONTEND - LobbyList.tsx

### Botão "Entrar" no Lobby

```typescript
const handleJoinLobby = async (lobbyId: number) => {
  setJoiningLobbyId(lobbyId);  // Desabilita botão durante request
  
  try {
    const response = await api.joinLobby(lobbyId);
    
    // RESPOSTA PODE SER 2 TIPOS:
    navigate(
      "matchId" in response.data
        ? `/matches/${response.data.matchId}`  // Match iniciado → vai para jogo
        : `/lobbies/${lobbyId}`                 // Apenas join → vai para lobby
    );
  } catch (err) {
    alert(err instanceof ApiError ? err.message : "Error");
  } finally {
    setJoiningLobbyId(null);
    fetchLobbies();  // Atualiza lista (lobby pode ter mudado)
  }
};
```

**Conceitos Importantes:**

#### 1. **Type Guard: `"matchId" in response.data`**

```typescript
"matchId" in response.data
```

**O que faz:**
- Verifica se objeto tem propriedade `matchId`
- **Type narrowing**: TypeScript sabe que se `true`, é `MatchStartedResponse`
- **Se `false`**: É `JoinedResponse`

**Tipos:**
```typescript
type JoinLobbyResponse = JoinedResponse | MatchStartedResponse;

interface JoinedResponse {
  data: { lobbyId: number };
}

interface MatchStartedResponse {
  data: { lobbyId: number; matchId: number };  // ← Tem matchId!
}
```

**Por que não usar `response.data.matchId !== undefined`?**
- `in` operator é mais explícito
- TypeScript faz melhor type narrowing com `in`
- Mais legível

---

#### 2. **Loading State por Lobby**

```typescript
const [joiningLobbyId, setJoiningLobbyId] = useState<number | null>(null);
```

**Por que não apenas `isJoining: boolean`?**
- Múltiplos lobbies na lista
- Queremos desabilitar apenas o botão do lobby sendo processado
- Outros botões continuam funcionando

**Uso:**
```typescript
<button
  onClick={() => handleJoinLobby(lobby.lobbyId)}
  disabled={joiningLobbyId === lobby.lobbyId}  // ← Desabilita apenas este
>
  {joiningLobbyId === lobby.lobbyId ? "..." : "Entrar"}
</button>
```

---

#### 3. **Refresh Após Join**

```typescript
finally {
  setJoiningLobbyId(null);
  fetchLobbies();  // ← Atualiza lista
}
```

**Por que?**
- Lobby pode ter mudado (status, playerCount)
- Se match iniciou, lobby desaparece da lista (status = ONGOING)
- UX: Utilizador vê estado atualizado

---

## 📍 PARTE 2: CLIENTE API - api.ts

### Função `api.joinLobby()`:

```typescript
joinLobby(lobbyId: number): Promise<JoinLobbyResponse> {
  return fetchApi<JoinLobbyResponse>(`/lobbies/${lobbyId}/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
  });
}
```

**Nota:**
- Endpoint: `POST /api/lobbies/{id}/join`
- **Autenticação**: Token enviado automaticamente via `fetchApi`
- **Sem body**: Apenas `lobbyId` no path

---

## 📍 PARTE 3: HTTP LAYER - LobbyController.kt

### Endpoint: `POST /api/lobbies/{id}/join`

```kotlin
@PostMapping(Uris.Lobby.JOIN)
fun joinLobby(
    @PathVariable id: Int,
    authUser: AuthenticatedUser,
): ResponseEntity<*> =
    when (val res = lobbyService.joinLobby(id, authUser.user.userId)) {
        is Success ->
            when (val out = res.value) {
                is JoinResult.Joined ->
                    ResponseEntity
                        .created(Uris.Lobby.byId(out.lobbyId))
                        .body(
                            ApiResponse(
                                data = out,  // { lobbyId: 42 }
                                meta = Meta("User joined lobby with success"),
                            ),
                        )

                is JoinResult.MatchStarted ->
                    ResponseEntity
                        .created(Uris.Match.byId(out.matchId))
                        .body(
                            ApiResponse(
                                data = out,  // { lobbyId: 42, matchId: 15 }
                                meta = Meta("Match is started"),
                            ),
                        )
            }

        is Failure ->
            when (res.value) {
                LobbyJoinError.LobbyNotFound ->
                    Problem.response(HttpStatus.NOT_FOUND, Problem.lobbyNotFound)
                LobbyJoinError.LobbyFull ->
                    Problem.response(HttpStatus.CONFLICT, Problem.lobbyFull)
                LobbyJoinError.UserAlreadyInLobby ->
                    Problem.response(HttpStatus.CONFLICT, Problem.userAlreadyInLobby)
                LobbyJoinError.LobbyAlreadyStarted ->
                    Problem.response(HttpStatus.CONFLICT, Problem.lobbyAlreadyStarted)
                LobbyJoinError.LobbyJoinGenericError ->
                    Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.lobbyJoinFailed)
                LobbyJoinError.MatchStartError ->
                    Problem.response(HttpStatus.INTERNAL_SERVER_ERROR, Problem.lobbyJoinFailed)
            }
    }
```

**Conceitos Importantes:**

### 1. **Sealed Class: JoinResult**

```kotlin
sealed class JoinResult {
    data class Joined(val lobbyId: Int) : JoinResult()
    data class MatchStarted(val lobbyId: Int, val matchId: Int) : JoinResult()
}
```

**Conceito: Sealed Class (Discriminated Union)**
- Tipo que pode ser um de vários subtipos
- **Type-safe**: Compilador força tratamento de todos os casos
- **Pattern matching**: `when` expression trata cada caso

**Vantagens:**
- **Type safety**: Não podes esquecer caso (compilador reclama)
- **Exhaustive**: `when` deve tratar todos os casos
- **Legível**: Código claro sobre possíveis resultados

**Alternativa (sem sealed class):**
```kotlin
// ❌ Menos type-safe
data class JoinResult(
    val lobbyId: Int,
    val matchId: Int? = null,  // null = apenas join, não null = match iniciado
)
```

**Problemas:**
- Não é explícito (precisa verificar `matchId != null`)
- Fácil esquecer verificação
- Menos legível

---

### 2. **Location Header Dinâmico**

```kotlin
when (val out = res.value) {
    is JoinResult.Joined ->
        ResponseEntity.created(Uris.Lobby.byId(out.lobbyId))
        // Location: /api/lobbies/42
    
    is JoinResult.MatchStarted ->
        ResponseEntity.created(Uris.Match.byId(out.matchId))
        // Location: /api/matches/15
}
```

**Por que Location diferente?**
- **Joined**: Recurso criado é a relação User-Lobby (redireciona para lobby)
- **MatchStarted**: Recurso criado é o Match (redireciona para match)
- **RESTful**: Location aponta para recurso criado

---

## 📍 PARTE 4: SERVICE LAYER - LobbyService.kt

### Função `joinLobby()`:

```kotlin
fun joinLobby(
    lobbyId: Int,
    userId: Int,
): JoinLobbyResult =
    transactionManager.run { tx ->
        // 1. BUSCA LOBBY
        val lobby = tx.lobbyRepository.getLobbyById(lobbyId)
            ?: return@run failure(LobbyJoinError.LobbyNotFound)

        // 2. VERIFICA SE LOBBY JÁ ESTÁ EM CURSO
        if (lobby.status == LobbyStatus.ONGOING) {
            return@run failure(LobbyJoinError.LobbyAlreadyStarted)
        }

        // 3. VERIFICA SE LOBBY ESTÁ CHEIO
        val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
        if (currentPlayers >= lobby.maxPlayers) {
            return@run failure(LobbyJoinError.LobbyFull)
        }

        // 4. VERIFICA SE USER JÁ ESTÁ NO LOBBY
        if (tx.lobbyRepository.isUserInLobby(lobbyId, userId)) {
            return@run failure(LobbyJoinError.UserAlreadyInLobby)
        }

        // 5. ADICIONA USER AO LOBBY
        val id = tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
        if (id.isEmpty()) {
            return@run failure(LobbyJoinError.LobbyJoinGenericError)
        }

        // 6. CALCULA NOVO PLAYER COUNT
        val newPlayerCount = currentPlayers + 1

        // 7. VERIFICA SE DEVE INICIAR MATCH
        if (newPlayerCount < lobby.maxPlayers) {
            // Ainda há espaço → apenas join
            return@run success(JoinResult.Joined(lobbyId))
        }

        // 8. LOBBY CHEIO → INICIA MATCH
        when (val res = matchService.createMatchFromLobby(lobby, tx)) {
            is Success -> 
                success(JoinResult.MatchStarted(lobbyId, res.value))
            is Failure -> 
                failure(LobbyJoinError.MatchStartError)
        }
    }
```

**Conceitos Importantes:**

### 1. **Validações Sequenciais (Early Returns)**

```kotlin
val lobby = tx.lobbyRepository.getLobbyById(lobbyId)
    ?: return@run failure(LobbyJoinError.LobbyNotFound)

if (lobby.status == LobbyStatus.ONGOING) {
    return@run failure(LobbyJoinError.LobbyAlreadyStarted)
}
// ... mais validações
```

**Conceito: Guard Clauses**
- Verifica condições e retorna cedo se falhar
- **Vantagem**: Evita nesting profundo
- **Legibilidade**: Código mais linear, fácil de ler

**Alternativa (sem guard clauses):**
```kotlin
// ❌ Nesting profundo
val lobby = tx.lobbyRepository.getLobbyById(lobbyId)
if (lobby != null) {
    if (lobby.status != LobbyStatus.ONGOING) {
        val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
        if (currentPlayers < lobby.maxPlayers) {
            // ... código aninhado
        }
    }
}
```

---

### 2. **Race Condition Protection**

```kotlin
val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
if (currentPlayers >= lobby.maxPlayers) {
    return@run failure(LobbyJoinError.LobbyFull)
}

// ... join user

val newPlayerCount = currentPlayers + 1
if (newPlayerCount < lobby.maxPlayers) {
    return@run success(JoinResult.Joined(lobbyId))
}
```

**Problema Potencial:**
- Entre verificar `currentPlayers` e fazer join, outro user pode entrar
- **Solução**: Transação garante isolamento
- **Isolation Level**: PostgreSQL default (READ COMMITTED) previne dirty reads

**Cenário de Race Condition:**
```
Thread 1: currentPlayers = 3, maxPlayers = 4 → OK, pode entrar
Thread 2: currentPlayers = 3, maxPlayers = 4 → OK, pode entrar
Thread 1: joinLobby() → playerCount = 4
Thread 2: joinLobby() → playerCount = 5 (excede maxPlayers!)
```

**Como transação previne:**
- Thread 2 vê `currentPlayers = 4` após Thread 1 commit
- Thread 2 falha na verificação `currentPlayers >= maxPlayers`
- **Resultado**: Apenas Thread 1 entra

---

### 3. **Auto-Start Match**

```kotlin
if (newPlayerCount < lobby.maxPlayers) {
    // Ainda há espaço
    return@run success(JoinResult.Joined(lobbyId))
}

// Lobby cheio → inicia match
when (val res = matchService.createMatchFromLobby(lobby, tx)) {
    is Success -> success(JoinResult.MatchStarted(lobbyId, res.value))
    is Failure -> failure(LobbyJoinError.MatchStartError)
}
```

**Lógica:**
- Se `newPlayerCount < maxPlayers`: Apenas join (lobby ainda não cheio)
- Se `newPlayerCount >= maxPlayers`: Inicia match automaticamente

**Por que auto-start?**
- **UX**: Não precisa ação adicional do host
- **Automático**: Quando lobby enche, jogo começa
- **Consistente**: Sempre acontece quando atinge `maxPlayers`

---

## 📍 PARTE 5: MATCH SERVICE - MatchService.kt

### Função `createMatchFromLobby()`:

```kotlin
fun createMatchFromLobby(
    lobby: Lobby,
    tm: Transaction,
): Either<LobbyJoinError.MatchStartError, Int> {
    try {
        // 1. CRIA MATCH
        val matchId = tm.matchRepository.createMatchFromLobby(lobby)

        // 2. ATUALIZA STATUS DO LOBBY
        tm.lobbyRepository.updateLobbyStatus(lobby.lobbyId, LobbyStatus.ONGOING)

        // 3. INICIALIZA PRIMEIRA RONDA
        initializeFirstRound(matchId, lobby.lobbyId, tm)

        return success(matchId)
    } catch (e: Exception) {
        return failure(LobbyJoinError.MatchStartError)
    }
}
```

**Conceitos:**

#### 1. **Try-Catch para Erros**

```kotlin
try {
    // Operações que podem falhar
} catch (e: Exception) {
    return failure(LobbyJoinError.MatchStartError)
}
```

**Por que try-catch?**
- Operações de BD podem lançar exceções
- **Conversão**: Exceções → Either (padrão funcional)
- **Consistência**: Todos os serviços retornam Either

**Alternativa (sem try-catch):**
- Exceções propagariam e quebrariam transação
- Menos controlo sobre tipo de erro

---

#### 2. **Atualização de Status do Lobby**

```kotlin
tm.lobbyRepository.updateLobbyStatus(lobby.lobbyId, LobbyStatus.ONGOING)
```

**Por que mudar status?**
- Lobby não está mais "WAITING" (esperando jogadores)
- Está "ONGOING" (partida em curso)
- **Importante**: Outros users não podem mais entrar

---

### Função `initializeFirstRound()`:

```kotlin
private fun initializeFirstRound(
    matchId: Int,
    lobbyId: Int,
    tm: Transaction,
) {
    // 1. CRIA PRIMEIRA RONDA
    val roundId = tm.matchRepository.createRound(
        matchId = matchId,
        roundNumber = 1,
        blind = roundConfig.defaultBlindAmount,  // Ex: 1.0
    )
    
    // 2. BUSCA JOGADORES DO LOBBY
    val players = tm.matchRepository.getLobbyPlayers(lobbyId)
    val host = players.first()  // Primeiro a entrar (host)

    // 3. CRIA PRIMEIRO TURN (host começa)
    tm.matchRepository.createTurn(
        roundId = roundId,
        userId = host.userId,
        turnNumber = 1,
    )

    // 4. COBRA BLIND DE TODOS OS JOGADORES
    players.forEach { player ->
        // Deduz blind do balance
        tm.usersRepository.updateUserBalance(
            player.userId,
            player.balance - roundConfig.defaultBlindAmount
        )
        // Adiciona ao pot da ronda
        tm.matchRepository.updateRoundPot(roundId, roundConfig.defaultBlindAmount)
    }
    
    // 5. MARCA RONDA COMO CURRENT
    tm.matchRepository.updateCurrentRound(matchId, roundId)
}
```

**Conceitos Importantes:**

#### 1. **Blind (Aposta Cega)**

**O que é?**
- Aposta obrigatória que todos os jogadores fazem no início da ronda
- **Exemplo**: Blind = 1.0 → cada jogador aposta 1.0 fichas
- **Pot**: Soma de todas as blinds (ex: 4 jogadores × 1.0 = 4.0 no pot)

**Por que blind?**
- Garante que há algo em jogo (pot não é zero)
- Similar a poker (small blind, big blind)

**Fluxo:**
```
Jogador 1: balance = 10.0 → aposta 1.0 → balance = 9.0
Jogador 2: balance = 10.0 → aposta 1.0 → balance = 9.0
Jogador 3: balance = 10.0 → aposta 1.0 → balance = 9.0
Jogador 4: balance = 10.0 → aposta 1.0 → balance = 9.0
Pot da ronda: 4.0
```

---

#### 2. **Host Começa (Primeiro Turn)**

```kotlin
val host = players.first()  // Primeiro a entrar (host)
tm.matchRepository.createTurn(
    roundId = roundId,
    userId = host.userId,
    turnNumber = 1,
)
```

**Por que host começa?**
- Convenção: Criador do lobby joga primeiro
- **Alternativa**: Poderia ser aleatório ou por ordem de entrada
- **Consistência**: Sempre o mesmo (host sempre primeiro)

---

#### 3. **Atomicidade: Tudo ou Nada**

```kotlin
transactionManager.run { tm ->
    // Se qualquer operação falhar, tudo é revertido:
    // - Match não é criado
    // - Lobby status não muda
    // - Round não é criado
    // - Blinds não são cobrados
    // - Turn não é criado
}
```

**Por que importante?**
- Se criar match mas falhar ao criar round → estado inconsistente
- Se cobrar blinds mas falhar ao criar turn → jogadores perderam fichas sem jogo
- **Transação garante**: Tudo ou nada

---

## 📍 PARTE 6: REPOSITORY LAYER

### Join Lobby:

```kotlin
override fun joinLobby(
    lobbyId: Int,
    userId: Int,
    time: Instant,
): String =
    handle
        .createUpdate(
            """
            INSERT INTO Lobby_Users (lobby_id, user_id, joined_at) 
            VALUES (:lobbyId, :userId, :time)
            """.trimIndent(),
        )
        .bind("lobbyId", lobbyId)
        .bind("userId", userId)
        .bind("time", time.toEpochMilliseconds())
        .executeAndReturnGeneratedKeys("lobby_id")
        .mapTo<String>()
        .one()
```

**Conceitos:**

#### 1. **Tabela de Relação (Many-to-Many)**

```
Lobby_Users:
  - lobby_id (FK → Lobby)
  - user_id (FK → Users)
  - joined_at (timestamp)
```

**Por que tabela separada?**
- Relação many-to-many: 1 lobby tem N users, 1 user pode estar em N lobbies
- **Alternativa (sem tabela):**
  - Array de user_ids na tabela Lobby (não normalizado)
  - Difícil queryar (precisa parsing)
  - Limita número de users (tamanho fixo)

**Vantagens da tabela:**
- Normalizado (3NF)
- Fácil queryar (JOIN simples)
- Escalável (sem limite de users)

---

#### 2. **Timestamp de Join**

```kotlin
joined_at: Instant
```

**Por que guardar?**
- Ordem de entrada (quem entrou primeiro)
- **Uso**: Host é `players.first()` (primeiro a entrar)
- **Futuro**: Pode mostrar "User X entrou há Y minutos"

---

### Get Player Count:

```kotlin
override fun getPlayerCount(lobbyId: Int): Int =
    handle
        .createQuery("SELECT COUNT(*) FROM Lobby_Users WHERE lobby_id = :lobbyId")
        .bind("lobbyId", lobbyId)
        .mapTo<Int>()
        .one()
```

**Conceito: COUNT(*)**
- Conta número de linhas que correspondem ao filtro
- **Eficiente**: BD otimiza COUNT (não precisa carregar todas as linhas)
- **Atualizado**: Sempre reflete estado atual (não cache)

---

### Is User In Lobby:

```kotlin
override fun isUserInLobby(
    lobbyId: Int,
    userId: Int,
): Boolean =
    handle
        .createQuery(
            """
            SELECT EXISTS(
                SELECT 1 FROM Lobby_Users 
                WHERE lobby_id = :lobbyId AND user_id = :userId
            )
            """.trimIndent(),
        )
        .bind("lobbyId", lobbyId)
        .bind("userId", userId)
        .mapTo<Boolean>()
        .first()
```

**Conceito: EXISTS()**
- Retorna `true` se subquery retorna pelo menos 1 linha
- **Eficiente**: Para assim que encontra primeira linha (não precisa contar todas)
- **Melhor que COUNT(*)**: Mais rápido (não precisa contar, só verificar existência)

**Alternativa (menos eficiente):**
```sql
SELECT COUNT(*) > 0 FROM Lobby_Users WHERE ...
```
- Conta todas as linhas (mais lento)
- EXISTS para na primeira linha

---

## 📍 PARTE 7: MATCH REPOSITORY

### Create Match From Lobby:

```kotlin
fun createMatchFromLobby(lobby: Lobby): Int
```

**O que faz:**
- Cria entrada na tabela `Match`
- Liga match ao lobby (`lobby_id`)
- Define status inicial (`ONGOING`)
- Retorna `matchId` gerado

**Estrutura Match:**
```kotlin
data class Match(
    val matchId: Int,
    val lobbyId: Int,
    val startingPlayerUserId: Int,
    val currentRoundId: Int?,  // null inicialmente, preenchido após criar round
    val status: MatchStatus = MatchStatus.ONGOING,
    val createdAt: Long,
    val finishedAt: Long? = null,
)
```

---

## 🔄 FLUXO COMPLETO RESUMIDO

### Cenário 1: Apenas Join (Lobby não cheio)

```
1. User clica "Entrar" (LobbyList.tsx)
   ↓
2. api.joinLobby(lobbyId) → POST /api/lobbies/{id}/join
   ↓
3. LobbyController.joinLobby() recebe request
   ↓
4. LobbyService.joinLobby(lobbyId, userId)
   ↓
5. TransactionManager.run { ... }
   ↓
6. Validações:
   - Lobby existe? ✓
   - Lobby não está ONGOING? ✓
   - Lobby não está cheio? ✓ (ex: 2/4 jogadores)
   - User não está já no lobby? ✓
   ↓
7. LobbyRepository.joinLobby() → INSERT INTO Lobby_Users
   ↓
8. newPlayerCount = 3 < maxPlayers (4) → Apenas join
   ↓
9. Retorna Success(JoinResult.Joined(lobbyId))
   ↓
10. Controller retorna HTTP 201 + { lobbyId: 42 }
   ↓
11. Frontend navega para /lobbies/42
```

---

### Cenário 2: Join + Match Iniciado (Lobby cheio)

```
1. User clica "Entrar" (LobbyList.tsx)
   ↓
2. api.joinLobby(lobbyId) → POST /api/lobbies/{id}/join
   ↓
3. LobbyController.joinLobby() recebe request
   ↓
4. LobbyService.joinLobby(lobbyId, userId)
   ↓
5. TransactionManager.run { ... }
   ↓
6. Validações (mesmas)
   ↓
7. LobbyRepository.joinLobby() → INSERT INTO Lobby_Users
   ↓
8. newPlayerCount = 4 >= maxPlayers (4) → Deve iniciar match!
   ↓
9. MatchService.createMatchFromLobby(lobby, tx)
   ↓
10. MatchRepository.createMatchFromLobby() → INSERT INTO Match
   ↓
11. LobbyRepository.updateLobbyStatus() → UPDATE Lobby SET status = 'ONGOING'
   ↓
12. MatchService.initializeFirstRound()
   ↓
13. MatchRepository.createRound() → INSERT INTO Round
   ↓
14. MatchRepository.createTurn() → INSERT INTO Turn (host começa)
   ↓
15. Cobra blind de todos os jogadores:
    - UsersRepository.updateUserBalance() (deduz blind)
    - MatchRepository.updateRoundPot() (adiciona ao pot)
   ↓
16. MatchRepository.updateCurrentRound() → UPDATE Match SET current_round_id = ...
   ↓
17. Transaction commit (tudo guardado)
   ↓
18. Retorna Success(JoinResult.MatchStarted(lobbyId, matchId))
   ↓
19. Controller retorna HTTP 201 + { lobbyId: 42, matchId: 15 }
   ↓
20. Frontend navega para /matches/15
```

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **Sealed Class (Discriminated Union)**: Type-safe resultados múltiplos
2. **Guard Clauses**: Validações com early returns
3. **Race Condition Protection**: Transações garantem isolamento
4. **Auto-Start Match**: Quando lobby cheio, inicia automaticamente
5. **Blind (Aposta Cega)**: Aposta obrigatória no início da ronda
6. **Many-to-Many**: Tabela de relação (Lobby_Users)
7. **EXISTS() vs COUNT()**: EXISTS mais eficiente para verificar existência
8. **Atomicidade**: Join + Match creation são atómicos

---

## 🔐 VALIDAÇÕES E SEGURANÇA

- ✅ Lobby existe (não pode entrar em lobby inexistente)
- ✅ Lobby não está em curso (não pode entrar em match já iniciado)
- ✅ Lobby não está cheio (não pode exceder maxPlayers)
- ✅ User não está já no lobby (previne duplicados)
- ✅ Transação garante atomicidade (join + match creation)
- ✅ Race conditions prevenidas (isolation level)

---

## 💡 DIFERENÇAS: JOIN vs CREATE LOBBY

| Aspecto | Criar Lobby | Join Lobby |
|---------|-------------|------------|
| **Resultado** | Sempre cria lobby | Join OU Join + Match |
| **Auto-join** | Host entra automaticamente | User que chama entra |
| **Validações** | Nome, limites | Lobby existe, não cheio, não em curso |
| **Complexidade** | Simples (cria lobby + join) | Complexa (validações + possível match) |
| **Transação** | Criar lobby + join host | Join + possível criar match/round/turn |

---

## 🎯 Pontos para a Discussão

1. **"Por que não deixar host iniciar match manualmente?"**
   - Auto-start é mais conveniente (não precisa ação adicional)
   - Evita host esquecer de iniciar
   - Consistente (sempre acontece quando atinge maxPlayers)

2. **"E se dois users tentarem entrar simultaneamente?"**
   - Transação garante isolamento
   - Segundo user vê playerCount atualizado
   - Apenas um entra (race condition prevenida)

3. **"Por que cobrar blind no início?"**
   - Garante que há pot em jogo
   - Similar a poker (small/big blind)
   - Incentiva participação (algo em risco)

4. **"Por que host começa primeiro?"**
   - Convenção simples e consistente
   - Alternativa: Poderia ser aleatório ou por ordem
   - Fácil de implementar e entender

---

Fim da explicação do join lobby! 🎉
