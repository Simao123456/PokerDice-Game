# 🎮 EXPLICAÇÃO COMPLETA E DETALHADA: CRIAÇÃO DE LOBBY

## 🎯 Visão Geral do Fluxo

```
Frontend (React) → API (HTTP Controller) → Service → Domain → Repository → Database
                                                              ↓
                                                    Host auto-join no lobby
```

**Diferente do login/registo**, a criação de lobby:
- **Requer autenticação** (precisa `AuthenticatedUser`)
- **Cria entidade complexa** (Lobby + relação com Users)
- **Auto-join do host** (criador entra automaticamente)
- **Validações de negócio** (limites de jogadores, rondas, timeout)

---

## 📍 PARTE 1: FRONTEND - CreateLobby.tsx

### State Machine Mais Complexa

O componente usa **state machine** com **discriminated unions** (TypeScript).

---

### Definição dos Estados:

```typescript
type State =
  | { tag: "editing"; form: LobbyFormData }
  | { tag: "submitting"; form: LobbyFormData }
  | { tag: "error"; form: LobbyFormData; message: string }
  | { tag: "redirect" };
```

**Conceito: Discriminated Union (Tagged Union)**
- Cada estado tem `tag` que identifica o tipo
- TypeScript usa `tag` para **narrowing** (reduzir tipos possíveis)
- **Vantagem**: Type safety - compilador sabe quais propriedades existem em cada estado

**Exemplo:**
```typescript
if (state.tag === "error") {
  // TypeScript sabe que state.message existe aqui!
  console.log(state.message);
}
```

**Estados:**
- `"editing"`: Utilizador está a preencher formulário
- `"submitting"`: Request HTTP em curso
- `"error"`: Erro ocorreu (mantém form para permitir correção)
- `"redirect"`: Sucesso - redireciona para lista de lobbies

---

### Form Data:

```typescript
interface LobbyFormData {
  name: string;
  description: string;
  maxPlayers: number;
  maxRounds: number;
  timeoutSeconds: number;
}

const initialForm: LobbyFormData = {
  name: "",
  description: "",
  maxPlayers: 2,        // Default: 2 jogadores
  maxRounds: 5,         // Default: 5 rondas
  timeoutSeconds: 30,  // Default: 30 segundos
};
```

**Valores Default:**
- Sensatos para UX (não precisa preencher tudo)
- Podem ser alterados pelo utilizador
- Validação no backend garante limites

---

### Reducer com Pattern Matching:

```typescript
function reducer(state: State, action: Action): State {
  switch (state.tag) {  // Pattern matching no estado atual
    case "editing":
    case "error":  // Mesmo comportamento para ambos
      switch (action.type) {
        case "set_field":
          return {
            tag: "editing",
            form: {
              ...state.form,
              [action.field]: action.value,  // Atualiza campo específico
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
            form: state.form,  // Mantém form para correção
            message: action.message,
          };
        default:
          return state;
      }

    case "redirect":
      return state;  // Estado terminal - não muda mais
  }
}
```

**Conceitos Importantes:**

#### 1. **Pattern Matching no Estado**
- `switch (state.tag)` - decide comportamento baseado no estado atual
- **Vantagem**: Lógica clara - cada estado tem transições bem definidas

#### 2. **Computed Property Names**
```typescript
[action.field]: action.value
```
- Atualiza propriedade dinamicamente
- `action.field` pode ser `"name"`, `"maxPlayers"`, etc.
- **Equivalente a:**
```typescript
if (action.field === "name") {
  form.name = action.value;
} else if (action.field === "maxPlayers") {
  form.maxPlayers = action.value;
}
// ... etc
```

#### 3. **Estado Terminal**
- `"redirect"` é terminal (não muda mais)
- Componente renderiza `null` e `useEffect` faz redirect

---

### Actions:

```typescript
type Action =
  | { type: "set_field"; field: keyof LobbyFormData; value: string | number }
  | { type: "submit" }
  | { type: "success" }
  | { type: "fail"; message: string };
```

**Conceito: `keyof` TypeScript**
- `keyof LobbyFormData` = `"name" | "description" | "maxPlayers" | "maxRounds" | "timeoutSeconds"`
- **Type safety**: `action.field` só pode ser propriedade válida de `LobbyFormData`
- Compilador impede erros de typo

---

### Handle Submit:

```typescript
const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();

  if (state.tag === "redirect") return;  // Guard clause

  dispatch({ type: "submit" });  // Muda para "submitting"

  try {
    await api.createLobby({
      name: state.form.name,
      description: state.form.description || undefined,  // Converte "" para undefined
      maxPlayers: Number(state.form.maxPlayers),
      maxRounds: Number(state.form.maxRounds),
      timeoutSeconds: Number(state.form.timeoutSeconds),
    });

    dispatch({ type: "success" });  // Muda para "redirect"
  } catch (err) {
    let msg = "Erro desconhecido ao criar lobby.";
    if (err instanceof ApiError) {
      msg = err.message;
    }
    dispatch({ type: "fail", message: msg });  // Muda para "error"
  }
};
```

**Conceitos:**

#### 1. **Guard Clause**
```typescript
if (state.tag === "redirect") return;
```
- Previne múltiplos submits
- **Pattern**: Verifica condição e retorna cedo (evita nesting)

#### 2. **Conversão de Tipos**
```typescript
description: state.form.description || undefined
```
- Converte string vazia para `undefined`
- **Por que?** Backend espera `String?` (nullable), não string vazia
- `""` e `null`/`undefined` são semanticamente diferentes

#### 3. **Number() Conversion**
- Garante que valores são números (inputs HTML retornam strings)
- **Nota**: `Number("")` = `0`, `Number("abc")` = `NaN` (validação no backend)

---

### useEffect para Redirect:

```typescript
useEffect(() => {
  if (state.tag === "redirect") {
    navigate("/lobbies");
  }
}, [state.tag, navigate]);
```

**Conceito: Side Effect em useEffect**
- Redirect é **side effect** (não é render)
- `useEffect` executa após render
- **Dependencies**: `[state.tag, navigate]` - re-executa se mudarem

**Por que não `navigate()` diretamente no `handleSubmit`?**
- Separação de responsabilidades
- `handleSubmit` muda estado
- `useEffect` reage ao estado e faz side effect
- **Vantagem**: Mais fácil testar (mock `navigate`)

---

### Input Handling:

```typescript
const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
  if (state.tag === "submitting") return;  // Previne mudanças durante submit

  const { name, value, type } = e.target;
  dispatch({
    type: "set_field",
    field: name as keyof LobbyFormData,
    value: type === "number" ? Number(value) : value,
  });
};
```

**Conceitos:**

#### 1. **Type Assertion**
```typescript
field: name as keyof LobbyFormData
```
- TypeScript não sabe que `name` é chave válida
- `as` força tipo (cuidado - pode ser unsafe se `name` não for válido)
- **Alternativa mais segura**: Validação em runtime

#### 2. **Type-based Conversion**
```typescript
value: type === "number" ? Number(value) : value
```
- Converte baseado no tipo do input
- `<input type="number">` → converte para número
- `<input type="text">` → mantém string

---

### Renderização Condicional:

```typescript
if (state.tag === "redirect") {
  return null;  // Não renderiza nada (useEffect faz redirect)
}

const formData = state.tag === "redirect" ? initialForm : state.form;
```

**Conceito: Early Return**
- Se `redirect`, retorna `null` imediatamente
- Evita renderizar formulário desnecessariamente
- **Performance**: Menos trabalho para React

---

## 📍 PARTE 2: CLIENTE API - api.ts

### Função `api.createLobby()`:

```typescript
createLobby(input: LobbyCreateInput): Promise<ApiResponse<LobbyDetails>> {
  return fetchApi<ApiResponse<LobbyDetails>>("/lobby", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}
```

**Nota:**
- Endpoint: `/api/lobby` (singular, não plural)
- **Autenticação**: `fetchApi` adiciona `Authorization: Bearer token` automaticamente
- Token vem de `localStorage.getItem("authToken")` (guardado no login)

---

## 📍 PARTE 3: HTTP LAYER - LobbyController.kt

### Endpoint: `POST /api/lobby`

```kotlin
@PostMapping(Uris.Lobby.CREATE)
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,  // ← Injetado automaticamente!
): ResponseEntity<*> {
    val res = lobbyService.createLobby(
        input.name,
        input.description,
        input.maxPlayers,
        input.maxRounds,
        input.timeoutSeconds,
        authUser.user.userId,  // ← ID do utilizador autenticado
    )
    return when (res) {
        is Success<LobbyDetails> ->
            ResponseEntity
                .created(Uris.Lobby.byId(res.value.lobbyId))
                .body(ApiResponse(data = res.value, meta = Meta(message = "Lobby created with success")))

        is Failure<LobbyCreationError> -> {
            when (res.value) {
                is LobbyCreationError.InvalidName ->
                    Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidLobbyName)
                // ... outros erros
            }
        }
    }
}
```

**Conceitos Importantes:**

### 1. **AuthenticatedUser Injection**

```kotlin
authUser: AuthenticatedUser
```

**Como funciona?**
1. `AuthenticationInterceptor` valida token
2. `RequestTokenProcessor` extrai user
3. `AuthenticatedUserArgumentResolver` injeta como parâmetro
4. Controller recebe `authUser` automaticamente

**Vantagens:**
- Controller não precisa extrair token manualmente
- Type-safe (compilador garante que user existe)
- Código mais limpo

**Alternativa (sem resolver):**
```kotlin
// Terias que fazer isto em cada método:
val token = request.getHeader("Authorization")?.substringAfter("Bearer ")
val user = userService.getUserByToken(token)
// ... validações, etc.
```

---

### 2. **Location Header**

```kotlin
.created(Uris.Lobby.byId(res.value.lobbyId))
```

- HTTP 201 (Created) com `Location` header
- Indica URL do recurso criado
- Cliente pode usar para redirect (opcional)

**Exemplo:**
```
HTTP/1.1 201 Created
Location: /api/lobbies/42
```

---

### 3. **Meta Information**

```kotlin
meta = Meta(message = "Lobby created with success")
```

- Informação adicional sobre a resposta
- Não é dados do recurso, mas metadata útil
- Pode incluir: timestamp, versão API, mensagens, etc.

---

## 📍 PARTE 4: SERVICE LAYER - LobbyService.kt

### Função `createLobby()`:

```kotlin
fun createLobby(
    name: String,
    description: String?,
    maxPlayers: Int,
    maxRounds: Int,
    timeoutSeconds: Int,
    hostId: Int,  // ← ID do utilizador que cria o lobby
): LobbyCreationResult {
    // 1. LIMPA E VALIDA NOME
    val cleanName = name.trim()
    if (!lobbyDomain.isNameValid(cleanName)) 
        return failure(LobbyCreationError.InvalidName)
    
    // 2. VALIDA PARÂMETROS
    if (!lobbyDomain.arePlayersValid(maxPlayers)) 
        return failure(LobbyCreationError.InvalidMaxPlayers)
    if (!lobbyDomain.areRoundsValid(maxRounds)) 
        return failure(LobbyCreationError.InvalidMaxRounds)
    if (!lobbyDomain.isTimeoutValid(timeoutSeconds)) 
        return failure(LobbyCreationError.InvalidTimeoutSeconds)

    // 3. OBTÉM TIMESTAMP
    val now = clock.now()
    val nowMs = now.toEpochMilliseconds()

    // 4. TRANSACTION - Tudo atómico
    return transactionManager.run { tm ->
        // 5. CRIA LOBBY NA BD
        val lobbyId = tm.lobbyRepository.createLobby(
            name = cleanName,
            description = description,
            hostId = hostId,
            maxPlayers = maxPlayers,
            maxRounds = maxRounds,
            minPlayers = lobbyDomain.getMinPlayers(),  // Configurável
            timeoutSeconds = timeoutSeconds,
            status = LobbyStatus.WAITING,  // Estado inicial
            createdAt = nowMs,
        )

        // 6. HOST ENTRA AUTOMATICAMENTE NO LOBBY
        tm.lobbyRepository.joinLobby(lobbyId, hostId, now)

        // 7. RETORNA READ MODEL
        success(
            LobbyDetails(
                lobbyId = lobbyId,
                name = cleanName,
                description = description,
                hostId = hostId,
                maxPlayers = maxPlayers,
                maxRounds = maxRounds,
                timeoutSeconds = timeoutSeconds,
                createdAt = nowMs,
                status = LobbyStatus.WAITING,
                playerCount = 1,  // Host é o primeiro jogador
                minPlayers = lobbyDomain.getMinPlayers(),
            ),
        )
    }
}
```

**Conceitos Importantes:**

### 1. **Validação Antes da Transação**

```kotlin
if (!lobbyDomain.isNameValid(cleanName)) 
    return failure(LobbyCreationError.InvalidName)
```

**Por que antes?**
- Validações simples não precisam de BD
- **Performance**: Falha rápido sem abrir transação
- **Clean code**: Separa validações simples de operações de BD

**Quando validar dentro da transação?**
- Validações que precisam de BD (ex: "nome já existe?")
- Operações que precisam ser atómicas com criação

---

### 2. **Auto-Join do Host**

```kotlin
tm.lobbyRepository.joinLobby(lobbyId, hostId, now)
```

**Por que?**
- Host criou o lobby, deve estar nele automaticamente
- **UX**: Não precisa fazer "criar lobby" + "entrar no lobby"
- **Atomicidade**: Se join falhar, lobby não é criado (rollback)

**Alternativa (sem auto-join):**
- Host teria que fazer 2 requests: `POST /api/lobby` + `POST /api/lobbies/{id}/join`
- Possível race condition (outro user entra antes do host)
- Pior UX

---

### 3. **Read Model vs Entity**

```kotlin
// Retorna LobbyDetails (Read Model), não Lobby (Entity)
success(LobbyDetails(...))
```

**Por que Read Model?**
- `LobbyDetails` inclui `playerCount` (calculado)
- `Lobby` entity não tem `playerCount` (só dados da tabela)
- Read Models são otimizados para **leitura** (queries complexas)
- Entities são otimizados para **escrita** (simples, normalizadas)

**Exemplo:**
```kotlin
// Entity (tabela Lobby)
data class Lobby(
    val lobbyId: Int,
    val name: String,
    // ... sem playerCount
)

// Read Model (query com JOIN)
data class LobbyDetails(
    val lobbyId: Int,
    val name: String,
    val playerCount: Int,  // ← Calculado via COUNT(*)
)
```

---

### 4. **Estado Inicial: WAITING**

```kotlin
status = LobbyStatus.WAITING
```

- Lobby criado mas ainda não começou
- Espera por mais jogadores (até `minPlayers`)
- Quando atinge `minPlayers`, muda para `ONGOING` (quando match inicia)

---

## 📍 PARTE 5: DOMAIN LAYER - LobbyDomain.kt

### Validações:

```kotlin
@Component
class LobbyDomain(
    private val lobbyDomainConfig: LobbyDomainConfig,
) {
    fun isNameValid(name: String): Boolean = 
        name.isNotEmpty() && name.length <= lobbyDomainConfig.nameMaxLength

    fun arePlayersValid(maxPlayers: Int): Boolean = 
        maxPlayers in lobbyDomainConfig.minPlayers..lobbyDomainConfig.maxPlayers

    fun areRoundsValid(maxRounds: Int): Boolean = 
        maxRounds in lobbyDomainConfig.minRounds..lobbyDomainConfig.maxRounds

    fun isTimeoutValid(timeoutSeconds: Int): Boolean =
        timeoutSeconds in lobbyDomainConfig.minTimeoutSeconds..lobbyDomainConfig.maxTimeoutSeconds

    fun getMinPlayers(): Int = lobbyDomainConfig.minPlayers
}
```

**Conceitos:**

#### 1. **Range Operator (`in`)**
```kotlin
maxPlayers in minPlayers..maxPlayers
```
- Verifica se valor está no range inclusivo
- **Equivalente a:** `minPlayers <= maxPlayers && maxPlayers <= maxPlayers`
- Mais legível

#### 2. **Configuração Centralizada**
- `LobbyDomainConfig` define limites
- Fácil alterar sem tocar em código
- **Exemplo de config:**
```kotlin
LobbyDomainConfig(
    nameMaxLength = 50,
    minPlayers = 2,
    maxPlayers = 10,
    minRounds = 1,
    maxRounds = 20,
    minTimeoutSeconds = 10,
    maxTimeoutSeconds = 300,
)
```

#### 3. **Domain Rules**
- Regras de negócio centralizadas
- Não dependem de BD ou HTTP
- Fácil testar (unit tests)

---

## 📍 PARTE 6: REPOSITORY LAYER

### Interface: LobbyRepository.kt

```kotlin
interface LobbyRepository {
    fun createLobby(
        name: String,
        description: String?,
        hostId: Int,
        maxPlayers: Int,
        minPlayers: Int,
        maxRounds: Int,
        timeoutSeconds: Int,
        status: LobbyStatus,
        createdAt: Long,
    ): Int  // Retorna lobbyId gerado

    fun joinLobby(
        lobbyId: Int,
        userId: Int,
        time: Instant,
    ): String  // Retorna algum ID (não usado no código atual)
}
```

**Conceito: Repository Pattern (Revisão)**
- Abstração sobre acesso a dados
- Domain não conhece SQL
- Facilita testes (mock repository)

---

### Implementação: JdbiLobbyRepository.kt

### Criar Lobby:

```kotlin
override fun createLobby(
    name: String,
    description: String?,
    hostId: Int,
    maxPlayers: Int,
    minPlayers: Int,
    maxRounds: Int,
    timeoutSeconds: Int,
    status: LobbyStatus,
    createdAt: Long,
): Int =
    handle
        .createUpdate(
            """
            INSERT INTO Lobby (
                name,
                description,
                status,
                host_id,
                min_players,
                max_players,
                max_rounds,
                timeout_seconds,
                created_at
            )
            VALUES (
                :name,
                :description,
                :status,          
                :hostId,
                :minPlayers,                   
                :maxPlayers,
                :maxRounds,
                :timeoutSeconds,
                :createdAt
            )
            RETURNING lobby_id
            """.trimIndent(),
        )
        .bind("name", name)
        .bind("description", description)
        .bind("status", status.name)  // Enum → String
        .bind("hostId", hostId)
        .bind("maxPlayers", maxPlayers)
        .bind("minPlayers", minPlayers)
        .bind("maxRounds", maxRounds)
        .bind("timeoutSeconds", timeoutSeconds)
        .bind("createdAt", createdAt)
        .executeAndReturnGeneratedKeys("lobby_id")
        .mapTo<Int>()
        .one()
```

**Conceitos:**

#### 1. **RETURNING Clause (PostgreSQL)**
```sql
RETURNING lobby_id
```
- Retorna valor da coluna após INSERT
- **Alternativa sem RETURNING:**
```sql
INSERT INTO Lobby (...) VALUES (...);
SELECT lastval();  -- Precisa de 2 queries
```
- **Vantagem**: Atómico (1 query)

#### 2. **Enum to String**
```kotlin
.bind("status", status.name)
```
- `LobbyStatus.WAITING.name` = `"WAITING"`
- PostgreSQL guarda como string (ou pode usar ENUM type)
- JDBI converte automaticamente na leitura

#### 3. **Nullable Description**
```kotlin
.bind("description", description)
```
- Se `description = null`, JDBI binda como SQL `NULL`
- **Importante**: Não bindar string vazia `""` como `null` (são diferentes semanticamente)

---

### Join Lobby (Host):

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
        .bind("time", time.toEpochMilliseconds())  // Instant → Long (epoch ms)
        .executeAndReturnGeneratedKeys("lobby_id")
        .mapTo<String>()
        .one()
```

**Conceitos:**

#### 1. **Tabela de Relação (Many-to-Many)**
- `Lobby_Users` é tabela de junção
- Relaciona `Lobby` com `User` (many-to-many)
- **Estrutura:**
```
Lobby_Users:
  - lobby_id (FK → Lobby)
  - user_id (FK → Users)
  - joined_at (timestamp)
```

#### 2. **Timestamp Conversion**
```kotlin
time.toEpochMilliseconds()
```
- `Instant` (Kotlin) → `Long` (epoch milliseconds)
- PostgreSQL guarda como `BIGINT` ou `TIMESTAMP`
- **Epoch ms**: Número de milissegundos desde 1 Jan 1970 UTC

#### 3. **Return Value**
- Retorna `String` (não usado no código atual)
- Provavelmente retorna `lobby_id` como string
- **Nota**: Tipo de retorno pode ser melhorado

---

## 📍 PARTE 7: ENTIDADES E READ MODELS

### Entity: Lobby.kt

```kotlin
data class Lobby(
    val lobbyId: Int,
    val name: String,
    val description: String?,
    val hostId: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val maxRounds: Int,
    val timeoutSeconds: Int,
    val status: LobbyStatus = LobbyStatus.ONGOING,
    val createdAt: Long,
)
```

**Conceito: Entity**
- Representa linha da tabela `Lobby`
- **1:1** com estrutura da BD
- Usado para **escrita** (INSERT, UPDATE)

---

### Read Model: LobbyDetails.kt

```kotlin
data class LobbyDetails(
    val lobbyId: Int,
    val name: String,
    val description: String?,
    val hostId: Int,
    val minPlayers: Int,
    val maxPlayers: Int,
    val maxRounds: Int,
    val timeoutSeconds: Int,
    val status: LobbyStatus,
    val createdAt: Long,
    val playerCount: Int,  // ← Calculado, não está na tabela Lobby
)
```

**Conceito: Read Model**
- Otimizado para **leitura**
- Pode incluir dados calculados (`playerCount`)
- Pode combinar dados de múltiplas tabelas
- **Vantagem**: Queries mais eficientes (evita N+1 queries)

**Exemplo de Query:**
```sql
SELECT 
  l.*,
  (SELECT COUNT(*) FROM Lobby_Users lu WHERE lu.lobby_id = l.lobby_id) AS player_count
FROM Lobby l
```

---

### Read Model: LobbyWithPlayers.kt

```kotlin
data class LobbyWithPlayers(
    val lobbyId: Int,
    val name: String,
    // ... outros campos do Lobby
    val players: List<PlayerInfo>,  // ← Lista de jogadores
)

fun lobbyWithPlayersToOutput(
    lobby: Lobby,
    players: List<PlayerInfo>,
): LobbyWithPlayers = LobbyWithPlayers(...)
```

**Conceito: Aggregation**
- Combina `Lobby` + `List<PlayerInfo>`
- **Por que função separada?**
  - Separação de responsabilidades
  - Fácil testar
  - Reutilizável

---

## 📍 PARTE 8: ENUMS

### LobbyStatus.kt

```kotlin
enum class LobbyStatus {
    WAITING,   // Esperando jogadores
    ONGOING,   // Partida em curso
}
```

**Conceito: Enum**
- Tipo seguro (não pode ter valor inválido)
- **Alternativa (String):**
```kotlin
val status: String  // Pode ser "WAITING", "ONGOING", "INVALID", etc.
```
- **Problema**: Valores inválidos não são detectados em compile-time

**Uso:**
```kotlin
if (lobby.status == LobbyStatus.WAITING) {
    // Lobby ainda não começou
}
```

---

## 📍 PARTE 9: TRANSACTION MANAGER

### Atomicidade: Criar Lobby + Join Host

```kotlin
transactionManager.run { tm ->
    // 1. Cria lobby
    val lobbyId = tm.lobbyRepository.createLobby(...)
    
    // 2. Host entra
    tm.lobbyRepository.joinLobby(lobbyId, hostId, now)
    
    // Se qualquer operação falhar, ambas são revertidas
}
```

**Por que transação?**
- **Atomicidade**: Tudo ou nada
- Se `createLobby` suceder mas `joinLobby` falhar:
  - Sem transação: Lobby criado mas host não está nele (inconsistência!)
  - Com transação: Rollback - lobby não é criado

**Exemplo de Falha:**
```kotlin
// Se joinLobby falhar (ex: constraint violation)
tm.lobbyRepository.createLobby(...)  // ✅ Sucesso
tm.lobbyRepository.joinLobby(...)   // ❌ Falha (ex: user não existe)
// → Rollback: Lobby é removido
```

---

## 🔄 FLUXO COMPLETO RESUMIDO

```
1. User preenche formulário (CreateLobby.tsx)
   ↓
2. dispatch({ type: "submit" }) → state.tag = "submitting"
   ↓
3. api.createLobby({ name, description, maxPlayers, ... })
   ↓
4. fetchApi("/lobby", { method: "POST", Authorization: Bearer token })
   ↓
5. AuthenticationInterceptor valida token
   ↓
6. LobbyController.createLobby() recebe LobbyCreateInputModel + AuthenticatedUser
   ↓
7. LobbyService.createLobby(name, ..., hostId)
   ↓
8. LobbyDomain valida: nome, maxPlayers, maxRounds, timeoutSeconds
   ↓
9. TransactionManager.run { ... }
   ↓
10. LobbyRepository.createLobby() → INSERT INTO Lobby
   ↓
11. LobbyRepository.joinLobby() → INSERT INTO Lobby_Users
   ↓
12. Transaction commit (tudo guardado)
   ↓
13. LobbyService retorna Success(LobbyDetails)
   ↓
14. LobbyController retorna HTTP 201 + Location header + JSON
   ↓
15. Frontend recebe resposta
   ↓
16. dispatch({ type: "success" }) → state.tag = "redirect"
   ↓
17. useEffect detecta "redirect" → navigate("/lobbies")
```

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **Discriminated Union**: State machine com type safety
2. **Pattern Matching**: `switch` no estado para decidir comportamento
3. **AuthenticatedUser Injection**: Injeção automática via ArgumentResolver
4. **Auto-Join**: Host entra automaticamente no lobby criado
5. **Read Models**: Modelos otimizados para leitura (com dados calculados)
6. **Entities**: Modelos 1:1 com BD (para escrita)
7. **Domain Rules**: Validações centralizadas e configuráveis
8. **Transaction Atomicity**: Criar lobby + join host são atómicos
9. **Enum Types**: Type-safe status values
10. **Many-to-Many**: Tabela de junção (Lobby_Users)

---

## 🔐 VALIDAÇÕES E SEGURANÇA

- ✅ **Autenticação obrigatória**: Só users autenticados podem criar lobby
- ✅ **Validação de limites**: maxPlayers, maxRounds, timeoutSeconds dentro de limites
- ✅ **Validação de nome**: Não vazio, tamanho máximo
- ✅ **Atomicidade**: Lobby + join host são atómicos
- ✅ **Host ownership**: `hostId` é o criador (não pode ser alterado)

---

## 💡 DIFERENÇAS: CRIAR LOBBY vs REGISTO/LOGIN

| Aspecto | Registo/Login | Criar Lobby |
|---------|---------------|-------------|
| **Autenticação** | Não precisa | Precisa (AuthenticatedUser) |
| **Entidades** | User, Session | Lobby, Lobby_Users |
| **Relações** | User → Session (1:N) | Lobby ↔ User (N:M) |
| **Auto-join** | N/A | Host entra automaticamente |
| **Estado** | N/A | WAITING → ONGOING |
| **Validações** | Password, username | Nome, limites numéricos |

---

Fim da explicação da criação de lobby! 🎉
