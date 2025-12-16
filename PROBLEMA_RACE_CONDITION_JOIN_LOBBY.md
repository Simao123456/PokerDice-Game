# ⚠️ PROBLEMA: Race Condition no Join Lobby

## 🎯 O Problema Identificado

Tens razão! Há um **problema potencial de race condition** no código atual.

---

## 🔍 Análise do Código Atual

### Código em LobbyService.joinLobby():

```kotlin
transactionManager.run { tx ->
    // 1. Lê player count
    val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
    
    // 2. Verifica se está cheio
    if (currentPlayers >= lobby.maxPlayers) {
        return@run failure(LobbyJoinError.LobbyFull)
    }
    
    // 3. Faz join (INSERT)
    val id = tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
    
    // 4. Calcula novo count
    val newPlayerCount = currentPlayers + 1
    // ...
}
```

### Schema da BD:

```sql
create table if not exists Lobby_Users (
    lobby_id int not null references Lobby(lobby_id) on delete cascade,
    user_id int not null references Users(user_id) on delete cascade,
    joined_at bigint not null,
    primary key (lobby_id, user_id)  -- ← Previne duplicados, mas NÃO previne exceder maxPlayers
);
```

---

## ⚠️ O Problema: Race Condition

### Cenário Problemático:

```
Thread 1 (User A):                    Thread 2 (User B):
─────────────────                     ─────────────────
1. getPlayerCount() → 3               
2. Verifica: 3 < 4? ✓ OK              1. getPlayerCount() → 3 (ainda não vê commit do Thread 1)
3. joinLobby() → INSERT               2. Verifica: 3 < 4? ✓ OK
4. COMMIT → playerCount = 4           3. joinLobby() → INSERT
                                       4. COMMIT → playerCount = 5 ❌ (excede maxPlayers!)
```

**Resultado**: Ambos entram, lobby fica com 5 jogadores quando maxPlayers = 4!

---

## 🔍 Por Que a Transação Não Previne?

### Isolation Level: READ COMMITTED (PostgreSQL Default)

**Com READ COMMITTED:**
- Cada transação vê apenas commits já finalizados
- **Não bloqueia** leituras de outras transações
- **Não previne** que duas transações leiam o mesmo valor e ambas procedam

**O que acontece:**
1. Thread 1 lê `currentPlayers = 3` (antes de qualquer commit)
2. Thread 2 lê `currentPlayers = 3` (ainda não vê commit do Thread 1)
3. Ambas passam na verificação `currentPlayers < maxPlayers`
4. Ambas fazem INSERT
5. Ambas fazem COMMIT
6. **Resultado**: 5 jogadores (excede maxPlayers)

---

## ✅ Soluções Possíveis

### Solução 1: SELECT FOR UPDATE (Row-Level Lock) ⭐ RECOMENDADA

**Bloqueia a linha do Lobby durante a transação:**

```kotlin
// No Repository
fun getLobbyByIdForUpdate(lobbyId: Int): Lobby? =
    handle
        .createQuery("SELECT * FROM Lobby WHERE lobby_id = :id FOR UPDATE")
        .bind("id", lobbyId)
        .mapTo<Lobby>()
        .singleOrNull()

// No Service
transactionManager.run { tx ->
    // 1. BLOQUEIA LOBBY (outros threads esperam)
    val lobby = tx.lobbyRepository.getLobbyByIdForUpdate(lobbyId)
        ?: return@run failure(LobbyJoinError.LobbyNotFound)
    
    // 2. Lê player count (com lock, garante consistência)
    val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
    
    // 3. Verifica
    if (currentPlayers >= lobby.maxPlayers) {
        return@run failure(LobbyJoinError.LobbyFull)
    }
    
    // 4. Faz join
    tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
}
```

**Como funciona:**
- `FOR UPDATE` bloqueia a linha do Lobby
- Thread 2 **espera** até Thread 1 fazer commit
- Thread 2 vê `currentPlayers = 4` (atualizado)
- Thread 2 falha na verificação `currentPlayers >= maxPlayers`

**Vantagens:**
- ✅ Previne race condition
- ✅ Garante consistência
- ✅ Não precisa mudar schema

**Desvantagens:**
- ⚠️ Pode causar contenção (múltiplos users tentando entrar simultaneamente)
- ⚠️ Threads podem esperar (mas é o comportamento correto)

---

### Solução 2: Verificar Novamente Após INSERT

**Verifica playerCount após INSERT e reverte se necessário:**

```kotlin
transactionManager.run { tx ->
    val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
    if (currentPlayers >= lobby.maxPlayers) {
        return@run failure(LobbyJoinError.LobbyFull)
    }
    
    // Faz join
    tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
    
    // VERIFICA NOVAMENTE após INSERT
    val newPlayerCount = tx.lobbyRepository.getPlayerCount(lobbyId)
    if (newPlayerCount > lobby.maxPlayers) {
        // Reverte join (DELETE)
        tx.lobbyRepository.leaveLobby(lobbyId, userId)
        return@run failure(LobbyJoinError.LobbyFull)
    }
}
```

**Problemas:**
- ❌ User pode entrar e ser removido imediatamente (má UX)
- ❌ Não previne race condition (apenas detecta após facto)
- ❌ Pode causar contenção (múltiplos users entram e são removidos)

---

### Solução 3: Constraint CHECK na BD

**Adiciona constraint que limita número de jogadores:**

```sql
-- Adicionar constraint (não existe no schema atual)
ALTER TABLE Lobby_Users 
ADD CONSTRAINT check_max_players 
CHECK (
    (SELECT COUNT(*) FROM Lobby_Users lu2 
     WHERE lu2.lobby_id = Lobby_Users.lobby_id) 
    <= (SELECT max_players FROM Lobby WHERE lobby_id = Lobby_Users.lobby_id)
);
```

**Problemas:**
- ❌ Constraint complexa (subquery) pode ser lenta
- ❌ PostgreSQL pode não suportar bem constraints com subqueries
- ❌ INSERT falha com erro genérico (difícil tratar)

---

### Solução 4: Isolation Level SERIALIZABLE

**Muda isolation level para SERIALIZABLE:**

```kotlin
// No TransactionManager
jdbi.inTransaction<R, Exception>(TransactionIsolationLevel.SERIALIZABLE) { handle ->
    // ...
}
```

**Como funciona:**
- SERIALIZABLE é mais restritivo
- Detecta conflitos e aborta uma das transações
- Retry automático necessário

**Problemas:**
- ❌ Mais lento (mais locks)
- ❌ Pode causar mais rollbacks
- ❌ Precisa retry logic

---

## 🎯 Solução Recomendada: SELECT FOR UPDATE

### Implementação:

#### 1. Adicionar método no Repository:

```kotlin
// LobbyRepository.kt
interface LobbyRepository {
    fun getLobbyByIdForUpdate(lobbyId: Int): Lobby?
    // ... outros métodos
}
```

#### 2. Implementar no JdbiLobbyRepository:

```kotlin
override fun getLobbyByIdForUpdate(lobbyId: Int): Lobby? =
    handle
        .createQuery("SELECT * FROM Lobby WHERE lobby_id = :id FOR UPDATE")
        .bind("id", lobbyId)
        .mapTo<Lobby>()
        .singleOrNull()
```

#### 3. Usar no Service:

```kotlin
fun joinLobby(
    lobbyId: Int,
    userId: Int,
): JoinLobbyResult =
    transactionManager.run { tx ->
        // BLOQUEIA LOBBY (previne race condition)
        val lobby = tx.lobbyRepository.getLobbyByIdForUpdate(lobbyId)
            ?: return@run failure(LobbyJoinError.LobbyNotFound)

        if (lobby.status == LobbyStatus.ONGOING) {
            return@run failure(LobbyJoinError.LobbyAlreadyStarted)
        }

        // Agora lê playerCount com lock (garante consistência)
        val currentPlayers = tx.lobbyRepository.getPlayerCount(lobbyId)
        if (currentPlayers >= lobby.maxPlayers) {
            return@run failure(LobbyJoinError.LobbyFull)
        }

        if (tx.lobbyRepository.isUserInLobby(lobbyId, userId)) {
            return@run failure(LobbyJoinError.UserAlreadyInLobby)
        }

        val id = tx.lobbyRepository.joinLobby(lobbyId, userId, clock.now())
        if (id.isEmpty()) {
            return@run failure(LobbyJoinError.LobbyJoinGenericError)
        }

        val newPlayerCount = currentPlayers + 1
        if (newPlayerCount < lobby.maxPlayers) {
            return@run success(JoinResult.Joined(lobbyId))
        }

        when (val res = matchService.createMatchFromLobby(lobby, tx)) {
            is Success -> success(JoinResult.MatchStarted(lobbyId, res.value))
            is Failure -> failure(LobbyJoinError.MatchStartError)
        }
    }
```

---

## 🔄 Como SELECT FOR UPDATE Resolve

### Cenário com SELECT FOR UPDATE:

```
Thread 1 (User A):                    Thread 2 (User B):
─────────────────                     ─────────────────
1. SELECT ... FOR UPDATE             1. SELECT ... FOR UPDATE
   (obtém lock)                          (BLOQUEADO - espera Thread 1)
2. getPlayerCount() → 3               
3. Verifica: 3 < 4? ✓ OK              
4. joinLobby() → INSERT               
5. COMMIT (libera lock)               2. SELECT ... FOR UPDATE (agora obtém lock)
                                       3. getPlayerCount() → 4 (atualizado!)
                                       4. Verifica: 4 >= 4? ❌ FALHA
                                       5. Retorna LobbyFull
```

**Resultado**: Apenas Thread 1 entra! ✅

---

## 📊 Comparação das Soluções

| Solução | Previne Race Condition? | Performance | Complexidade | Recomendado? |
|---------|------------------------|-------------|--------------|--------------|
| **SELECT FOR UPDATE** | ✅ Sim | ⚠️ Pode causar contenção | ✅ Simples | ✅ **SIM** |
| Verificar após INSERT | ❌ Não (apenas detecta) | ✅ Boa | ✅ Simples | ❌ Não |
| Constraint CHECK | ✅ Sim | ❌ Lenta (subquery) | ⚠️ Complexa | ❌ Não |
| SERIALIZABLE | ✅ Sim | ❌ Mais lenta | ⚠️ Média | ⚠️ Talvez |

---

## 🎓 Conceitos Importantes

### 1. **SELECT FOR UPDATE**

**O que faz:**
- Bloqueia linha(s) selecionadas até fim da transação
- Outras transações que tentam ler **esperam** (não bloqueiam, apenas esperam)
- Garante leitura consistente

**Sintaxe:**
```sql
SELECT * FROM Lobby WHERE lobby_id = :id FOR UPDATE;
```

**Comportamento:**
- Thread 1: Obtém lock, lê, modifica, commit (libera lock)
- Thread 2: Espera lock ser libertado, lê valor atualizado

---

### 2. **Isolation Levels**

| Level | Dirty Reads | Non-Repeatable Reads | Phantom Reads | Race Conditions |
|-------|-------------|---------------------|---------------|-----------------|
| **READ UNCOMMITTED** | ❌ Permite | ❌ Permite | ❌ Permite | ❌ Não previne |
| **READ COMMITTED** (default) | ✅ Previne | ❌ Permite | ❌ Permite | ❌ Não previne |
| **REPEATABLE READ** | ✅ Previne | ✅ Previne | ❌ Permite | ⚠️ Parcial |
| **SERIALIZABLE** | ✅ Previne | ✅ Previne | ✅ Previne | ✅ Previne |

**PostgreSQL Default**: READ COMMITTED
- Previne dirty reads
- **Mas não previne** race conditions como esta

---

### 3. **Row-Level Locking**

**Tipos de locks:**
- **FOR UPDATE**: Lock exclusivo (outros não podem ler nem escrever)
- **FOR SHARE**: Lock compartilhado (outros podem ler mas não escrever)
- **NOWAIT**: Não espera (retorna erro se lock não disponível)

**No nosso caso:**
- `FOR UPDATE` é correto (queremos exclusividade durante verificação + insert)

---

## 💡 Por Que o Código Atual Funciona na Prática?

**Pode funcionar porque:**
1. **Baixa concorrência**: Poucos users tentam entrar simultaneamente
2. **Timing**: Janela de race condition é pequena (milissegundos)
3. **PRIMARY KEY**: Previne duplicados (user não pode entrar 2x)

**Mas ainda há problema:**
- ❌ Pode exceder `maxPlayers` se 2 users entrarem simultaneamente
- ❌ Não é garantido (depende de timing)

---

## ✅ Recomendação Final

**Implementar SELECT FOR UPDATE:**

1. ✅ **Previne race condition** garantidamente
2. ✅ **Simples de implementar** (1 método novo)
3. ✅ **Não muda schema** (sem alterações na BD)
4. ✅ **Comportamento correto** (users esperam se necessário)

**Trade-off:**
- ⚠️ Pode causar contenção (múltiplos users esperam)
- ⚠️ Mas é o comportamento correto (melhor que exceder maxPlayers)

---

## 🔧 Implementação Sugerida

### Passo 1: Adicionar ao Repository Interface

```kotlin
// LobbyRepository.kt
interface LobbyRepository {
    fun getLobbyById(lobbyId: Int): Lobby?
    fun getLobbyByIdForUpdate(lobbyId: Int): Lobby?  // ← NOVO
    // ...
}
```

### Passo 2: Implementar no JdbiLobbyRepository

```kotlin
override fun getLobbyByIdForUpdate(lobbyId: Int): Lobby? =
    handle
        .createQuery("SELECT * FROM Lobby WHERE lobby_id = :id FOR UPDATE")
        .bind("id", lobbyId)
        .mapTo<Lobby>()
        .singleOrNull()
```

### Passo 3: Usar no Service

```kotlin
fun joinLobby(...): JoinLobbyResult =
    transactionManager.run { tx ->
        // MUDAR: getLobbyById → getLobbyByIdForUpdate
        val lobby = tx.lobbyRepository.getLobbyByIdForUpdate(lobbyId)
            ?: return@run failure(LobbyJoinError.LobbyNotFound)
        
        // Resto do código igual
        // ...
    }
```

---

## 🎓 Resumo

**Problema identificado:**
- ✅ Race condition existe (2 users podem entrar simultaneamente e exceder maxPlayers)
- ✅ Transação com READ COMMITTED não previne
- ✅ PRIMARY KEY previne duplicados mas não previne exceder maxPlayers

**Solução:**
- ✅ **SELECT FOR UPDATE** previne race condition
- ✅ Bloqueia lobby durante verificação + insert
- ✅ Garante que apenas um user entra por vez

**Conclusão:**
- ⚠️ **Há um problema** no código atual
- ✅ **SELECT FOR UPDATE resolve** o problema
- ✅ **Implementação simples** (1 método novo)

---

Fim da análise! 🎉
