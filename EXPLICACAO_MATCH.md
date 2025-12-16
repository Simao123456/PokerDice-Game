# 🎲 EXPLICAÇÃO COMPLETA: MATCH (PARTIDA)

## 🎯 Visão Geral do Fluxo

```
Match → Rounds → Turns → Rolls → Hand Evaluation → Winner → Next Round → Match End
```

**Estrutura Hierárquica:**
- **1 Match** = Múltiplas Rounds
- **1 Round** = Múltiplos Turns (1 por player)
- **1 Turn** = Múltiplos Rolls (máx 3)
- **1 Roll** = 5 dados
- **1 Hand** = Avaliação final de 1 Turn

**Fluxo Principal:**
1. **Carregar Match** → Buscar round atual + turn atual
2. **Se é meu turno** → Mostrar RollScreen (pode lançar dados)
3. **Se não é meu turno** → Mostrar WaitingScreen (aguardar)
4. **Roll Dice** → Lançar dados (máx 3 vezes)
5. **End Turn** → Terminar turno → Avançar para próximo player
6. **Round End** → Avaliar todas as hands → Determinar vencedor → Distribuir pot
7. **Match End** → Se última round → Finalizar match

---

## 📍 PARTE 1: FRONTEND - Match.tsx

### State Machine com `useReducer`:

```typescript
type State =
  | { tag: "loading" }
  | { tag: "error"; message: string }
  | { tag: "playing"; state: MatchState }
  | { tag: "ended" };
```

**Conceitos:**

#### 1. **Discriminated Union para Estados**

```typescript
type State = 
  | { tag: "loading" }           // Carregando dados
  | { tag: "error"; message: string }  // Erro ao carregar
  | { tag: "playing"; state: MatchState }  // Jogando
  | { tag: "ended" };            // Match terminou
```

**Por que discriminated union?**
- **Type Safety**: TypeScript sabe qual estado está ativo
- **Exhaustive Checking**: Switch/case força tratamento de todos os casos
- **Impossível**: Aceder `state.message` quando `tag === "loading"`

**Alternativa (sem discriminated union):**
```typescript
type State = {
  loading?: boolean;
  error?: string;
  playing?: MatchState;
  ended?: boolean;
}
```

**Problema**: Estados podem ser inconsistentes (ex: `loading: true` e `playing: {...}` ao mesmo tempo)

---

#### 2. **MatchState (Estado do Jogo)**

```typescript
const initialMatchState: MatchState = {
  round: null,
  previousRoundWinner: null,
  currentTurn: null,
  selectedDice: Array(5).fill(false),  // Quais dados manter
  lastRoll: null,                       // Último lançamento
  rolling: false,                       // A lançar dados
};
```

**Campos:**
- `round`: Round atual com players, pot, etc.
- `currentTurn`: Turn atual (quem está a jogar)
- `selectedDice`: Array de 5 booleans (quais dados manter)
- `lastRoll`: String com valores dos dados (ex: "A,K,Q,J,10")
- `rolling`: Flag para desabilitar botões durante roll

---

#### 3. **Reducer com Actions**

```typescript
type Action =
  | { type: "fetch_start" }
  | { type: "fetch_success"; round: RoundWithDetails; currentTurn: Turn | null; ... }
  | { type: "roll_start" }
  | { type: "roll_success"; diceValues: string; round: RoundWithDetails; ... }
  | { type: "toggle_dice"; index: number }
  | { type: "end_start" }
  | { type: "end_success"; round: RoundWithDetails; currentTurn: Turn | null }
  | { type: "match_ended" }
  | ...
```

**Por que useReducer?**
- **Complexidade**: Muitos estados relacionados (round, turn, dice, rolling)
- **State Machine**: Estados bem definidos (loading → playing → ended)
- **Actions**: Múltiplas ações que afetam o mesmo estado
- **Predictability**: Reducer é função pura (mesma input = mesma output)

**Se usasse useState:**
```typescript
const [round, setRound] = useState(null);
const [currentTurn, setCurrentTurn] = useState(null);
const [selectedDice, setSelectedDice] = useState(Array(5).fill(false));
const [lastRoll, setLastRoll] = useState(null);
const [rolling, setRolling] = useState(false);
// ... muitas atualizações manuais, fácil esquecer alguma
```

**Problema**: Muitas atualizações manuais, fácil criar estados inconsistentes

---

#### 4. **useEffect para Carregar Dados**

```typescript
useEffect(() => {
  if (!matchId) {
    dispatch({ type: "fetch_error", message: "Invalid match ID" });
    return;
  }

  const loadMatchData = async () => {
    try {
      dispatch({ type: "fetch_start" });
      const matchData = await fetchMatchData(matchId!, userId!);
      
      dispatch({
        type: "fetch_success",
        round: matchData.round,
        currentTurn: matchData.currentTurn,
        lastRoll: matchData.lastRoll,
        previousRoundWinner: matchData.previousRoundWinner,
      });
    } catch (err: any) {
      if (err.message === "MATCH_FINISHED" || err.message === "MATCH_ENDED") {
        dispatch({ type: "match_ended" });
        return;
      }
      dispatch({ type: "fetch_error", message: err.message });
    }
  };

  loadMatchData();
}, [matchId, userId]);
```

**Conceitos:**
- **Dependency Array**: `[matchId, userId]` - re-executa se mudarem
- **Async Function**: `loadMatchData` dentro de useEffect
- **Error Handling**: Trata match terminado vs erro genérico

---

#### 5. **handleRoll - Lançar Dados**

```typescript
const handleRoll = async () => {
  if (state.tag !== "playing" || !matchId) return;
  const { currentTurn, selectedDice, rolling } = state.state;
  
  // Validações
  if (!currentTurn || currentTurn.userId !== userId || rolling) return;

  // Criar heldMask (quais dados manter)
  const heldMask = currentTurn.rollCount > 0 
    ? createHeldMask(selectedDice) 
    : undefined;
  
  if (heldMask === "00000") {
    alert("You must select at least one die to keep");
    return;
  }

  try {
    dispatch({ type: "roll_start" });  // Desabilita botões
    
    // 1. Lançar dados
    const { data: { diceValues } } = await api.rollDice(matchId, heldMask);
    
    // 2. Buscar round atualizado (pode ter mudado turno se foi último roll)
    const { data: round } = await api.getCurrentRound(matchId);
    
    dispatch({
      type: "roll_success",
      diceValues,
      round,
      currentTurn: round.currentTurn,
    });
  } catch (err: any) {
    dispatch({ type: "roll_error", message: err.message });
    alert(`Error: ${err.message}`);
  }
};
```

**Conceitos Importantes:**

##### a) **HeldMask**

```typescript
const heldMask = createHeldMask(selectedDice);
// selectedDice = [true, false, true, false, false]
// heldMask = "10100"
```

**O que é?**
- String de 5 caracteres ('0' ou '1')
- `'1'` = manter dado
- `'0'` = relançar dado

**Exemplo:**
- Dados: `[A, K, Q, J, 10]`
- User seleciona: `[A, Q]` (índices 0 e 2)
- HeldMask: `"10100"`
- Próximo roll: Mantém `A` e `Q`, relança `K`, `J`, `10`

---

##### b) **Validação de HeldMask**

```typescript
if (heldMask === "00000") {
  alert("You must select at least one die to keep");
  return;
}
```

**Por que?**
- **Regra do jogo**: Após primeiro roll, deve manter pelo menos 1 dado
- **Primeiro roll** (`rollCount === 0`): Não precisa heldMask (relança todos)

---

##### c) **Dois Requests Sequenciais**

```typescript
// 1. Lançar dados
const { data: { diceValues } } = await api.rollDice(matchId, heldMask);

// 2. Buscar round atualizado
const { data: round } = await api.getCurrentRound(matchId);
```

**Por que dois requests?**
- `rollDice` retorna apenas `Roll` (diceValues)
- Mas se foi o 3º roll, turno pode ter avançado automaticamente
- `getCurrentRound` retorna estado completo (round + currentTurn atualizado)

**Alternativa (retornar round no rollDice):**
- Mais eficiente (1 request)
- Mas viola separação de responsabilidades (roll não deveria retornar round)

---

#### 6. **handleEnd - Terminar Turno**

```typescript
const handleEnd = async () => {
  if (state.tag !== "playing" || !matchId) return;
  const { currentTurn, rolling, round } = state.state;
  
  // Validações
  if (!currentTurn || currentTurn.userId !== userId || rolling) return;
  if (currentTurn.rollCount === 0) {
    alert("You must roll at least once before ending your turn");
    return;
  }

  try {
    dispatch({ type: "end_start" });
    
    // Chamar rollDice com heldMask vazio força avanço de turno
    await api.rollDice(matchId, "");
    
    // Verificar se match terminou
    if (await checkMatchEnded()) {
      dispatch({ type: "end_success", round: round!, currentTurn: null });
      return;
    }

    // Buscar round atualizado
    const { data } = await api.getCurrentRound(matchId);
    dispatch({
      type: "end_success",
      round: data,
      currentTurn: data.currentTurn,
    });
  } catch (err: any) {
    if (await checkMatchEnded()) {
      dispatch({ type: "end_success", round: round!, currentTurn: null });
      return;
    }
    dispatch({ type: "end_error", message: err.message });
    alert(`Error: ${err.message}`);
  }
};
```

**Conceitos:**

##### a) **RollDice com HeldMask Vazio**

```typescript
await api.rollDice(matchId, "");
```

**O que faz?**
- `heldMask = ""` (string vazia)
- Backend detecta: `heldMask.isEmpty()` → Avança turno automaticamente
- **Não lança dados**: Apenas força avanço de turno

**Por que não endpoint separado?**
- Reutiliza lógica existente
- Menos código duplicado
- Mas pode ser confuso (rollDice sem realmente lançar)

---

##### b) **Verificação de Match End**

```typescript
if (await checkMatchEnded()) {
  dispatch({ type: "end_success", round: round!, currentTurn: null });
  return;
}
```

**Por que verificar?**
- Após avançar turno, round pode ter terminado
- Se última round terminou, match também terminou
- **Importante**: Verificar mesmo em catch (match pode ter terminado)

---

#### 7. **Conditional Rendering**

```typescript
{userTurn && currentTurn ? (
  <RollScreen
    round={round}
    currentTurn={currentTurn}
    selectedDice={selectedDice}
    lastRoll={lastRoll}
    rolling={rolling}
    onDiceToggle={handleDiceToggle}
    onRoll={handleRoll}
    onEnd={handleEnd}
  />
) : (
  <WaitingScreen
    currentPlayer={currentPlayer}
    playersUntilTurn={playersUntilTurn}
  />
)}
```

**Conceitos:**
- **userTurn**: Função helper que verifica se é turno do user
- **RollScreen**: Mostra dados, botões Roll/End
- **WaitingScreen**: Mostra quem está a jogar, quantos players até meu turno

---

## 📍 PARTE 2: FRONTEND - RollScreen.tsx

### Componente de Jogo:

```typescript
export function RollScreen({
  round,
  currentTurn,
  selectedDice,
  lastRoll,
  rolling,
  onDiceToggle,
  onRoll,
  onEnd,
}: RollScreenProps) {
  return (
    <div className="match-turn-panel">
      {/* Pot e Rolls */}
      <div className="match-turn-header">
        <div>Pot: {round.pot.toFixed(2)}</div>
        <div>Rolls: {currentTurn.rollCount} / 3</div>
      </div>

      {/* Dados */}
      <div className="match-dice-section">
        <h4>Select dice:</h4>
        <div className="match-dice-container">
          {lastRoll
            ? lastRoll.split(",").map((value, index) => (
                <button
                  key={index}
                  className={`match-die ${
                    selectedDice[index] ? "match-die-selected" : ""
                  } ${currentTurn.rollCount === 0 ? "match-die-disabled" : ""}`}
                  onClick={() => onDiceToggle(index)}
                  disabled={currentTurn.rollCount === 0}
                >
                  {value.trim()}
                </button>
              ))
            : Array.from({ length: 5 }).map((_, index) => (
                <div key={index} className="match-die match-die-unknown">
                  ?
                </div>
              ))}
        </div>
      </div>

      {/* Botões */}
      <div className="match-actions">
        <button
          onClick={onRoll}
          disabled={rolling || currentTurn.rollCount >= 3}
        >
          {rolling ? "Rolling..." : "Roll"}
        </button>
        <button
          onClick={onEnd}
          disabled={rolling || currentTurn.rollCount === 0}
        >
          End Turn
        </button>
      </div>
    </div>
  );
}
```

**Conceitos:**

#### 1. **Renderização Condicional de Dados**

```typescript
{lastRoll
  ? lastRoll.split(",").map((value, index) => (
      <button>{value.trim()}</button>
    ))
  : Array.from({ length: 5 }).map((_, index) => (
      <div>?</div>
    ))}
```

**O que faz:**
- **Se `lastRoll` existe**: Mostra valores dos dados (ex: "A,K,Q,J,10")
- **Se `lastRoll` é null**: Mostra "?" (ainda não lançou)

**Por que?**
- Primeiro roll: `lastRoll = null` (ainda não lançou)
- Após primeiro roll: `lastRoll = "A,K,Q,J,10"` (mostra valores)

---

#### 2. **Desabilitar Seleção no Primeiro Roll**

```typescript
disabled={currentTurn.rollCount === 0}
```

**Por que?**
- **Primeiro roll** (`rollCount === 0`): Não pode selecionar dados (ainda não lançou)
- **Após primeiro roll** (`rollCount > 0`): Pode selecionar dados para manter

---

#### 3. **Desabilitar Botão Roll**

```typescript
disabled={rolling || currentTurn.rollCount >= 3}
```

**Condições:**
- `rolling`: A lançar dados (evita duplo click)
- `rollCount >= 3`: Já lançou 3 vezes (máximo permitido)

---

#### 4. **Desabilitar Botão End**

```typescript
disabled={rolling || currentTurn.rollCount === 0}
```

**Condições:**
- `rolling`: A lançar dados
- `rollCount === 0`: Ainda não lançou (deve lançar pelo menos 1 vez)

---

## 📍 PARTE 3: FRONTEND - WaitingScreen.tsx

### Componente de Espera:

```typescript
export function WaitingScreen({
  currentPlayer,
  playersUntilTurn,
}: WaitingScreenProps) {
  return (
    <div className="match-waiting-panel">
      <h3>Waiting for your turn</h3>
      {currentPlayer && (
        <div>
          <p>{`${currentPlayer.name} is rolling`}</p>
          {playersUntilTurn > 0 && (
            <p>
              {playersUntilTurn} player{playersUntilTurn !== 1 ? "s" : ""} until your turn
            </p>
          )}
        </div>
      )}
    </div>
  );
}
```

**Conceitos:**
- **currentPlayer**: Player que está a jogar agora
- **playersUntilTurn**: Quantos players até meu turno
- **UX**: Informa user sobre progresso do jogo

---

## 📍 PARTE 4: CLIENTE API - api.ts

### Funções de Match:

```typescript
getMatch(matchId: number): Promise<ApiResponse<MatchWithPlayers>>
getCurrentRound(matchId: number): Promise<ApiResponse<RoundWithDetails>>
listRounds(matchId: number): Promise<ApiResponse<Round[]>>
rollDice(matchId: number, heldMask?: string): Promise<ApiResponse<RollResponse>>
getLastRoll(matchId: number): Promise<ApiResponse<RollResponse>>
```

**Conceitos:**

#### 1. **rollDice com HeldMask Opcional**

```typescript
rollDice(matchId: number, heldMask?: string): Promise<ApiResponse<RollResponse>> {
  const body = heldMask !== undefined ? { heldMask } : undefined;
  return fetchApi<ApiResponse<RollResponse>>(
    `/matches/${matchId}/turns/current/roll`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    }
  );
}
```

**O que faz:**
- **Primeiro roll** (`heldMask === undefined`): Não envia body (relança todos)
- **Rolls seguintes** (`heldMask !== undefined`): Envia `{ heldMask: "10100" }`
- **End turn** (`heldMask === ""`): Envia `{ heldMask: "" }` (força avanço)

---

## 📍 PARTE 5: HTTP LAYER - MatchController.kt

### Endpoint: `POST /api/matches/{mid}/turns/current/roll`

```kotlin
@PostMapping(Uris.Match.ROLL)
fun rollDice(
    @PathVariable mid: Int,
    @RequestBody(required = false) input: RollRequestInputModel?,
    authUser: AuthenticatedUser,
): ResponseEntity<Any> =
    when (val result = matchService.rollDice(mid, authUser.user.userId, input?.heldMask)) {
        is Success -> ResponseEntity.ok(ApiResponse(data = result.value))
        is Failure ->
            when (result.value) {
                RollError.TurnNotFound -> Problem.response(HttpStatus.NOT_FOUND, Problem.turnNotFound)
                RollError.InvalidMatchState -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidMatchState)
                RollError.MaxRollsReached -> Problem.response(HttpStatus.BAD_REQUEST, Problem.maxRollsReached)
                RollError.InvalidHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidHeldMask)
                RollError.MissingHeldMask -> Problem.response(HttpStatus.BAD_REQUEST, Problem.missingHeldMask)
            }
    }
```

**Conceitos:**

#### 1. **@RequestBody(required = false)**

```kotlin
@RequestBody(required = false) input: RollRequestInputModel?
```

**Por que `required = false`?**
- **Primeiro roll**: Não precisa body (heldMask é opcional)
- **Rolls seguintes**: Precisa body com heldMask
- **End turn**: Body vazio (`heldMask = ""`)

---

#### 2. **AuthenticatedUser Injection**

```kotlin
authUser: AuthenticatedUser
```

**O que faz:**
- Injeta user autenticado automaticamente
- Usa `authUser.user.userId` para validar se é turno do user
- **Segurança**: User não pode lançar dados no turno de outro

---

## 📍 PARTE 6: SERVICE LAYER - MatchService.kt

### Função `rollDice()`:

```kotlin
fun rollDice(
    matchId: Int,
    userId: Int,
    heldMask: String?,
): RollResult =
    transactionManager.run { tm ->
        // 1. Validar match
        val match = tm.matchRepository.getMatchById(matchId)
        if (match == null || match.status.name != LobbyStatus.ONGOING.name) {
            return@run failure(RollError.InvalidMatchState)
        }

        // 2. Validar turn atual
        val currentTurn = tm.matchRepository.currentTurn(matchId)
        if (currentTurn == null) {
            return@run failure(RollError.TurnNotFound)
        }
        if (currentTurn.userId != userId) {
            return@run failure(RollError.InvalidMatchState)
        }
        if (currentTurn.rollCount > roundConfig.maxRollsPerTurn) {
            return@run failure(RollError.MaxRollsReached)
        }

        // 3. Se heldMask vazio OU rollCount == 3 → Avançar turno
        if (currentTurn.rollCount > 0 && heldMask != null && heldMask.isEmpty() 
            || currentTurn.rollCount == 3) {
            when (val adv = advanceTurn(tm, matchId, userId)) {
                is Failure -> return@run failure(...)
                is Success -> {
                    val last = tm.matchRepository.getLastRollForTurn(currentTurn.turnId)
                        ?: return@run failure(RollError.InvalidMatchState)
                    return@run success(last)
                }
            }
        }

        // 4. Validar heldMask
        if (currentTurn.rollCount > 0 && heldMask == null) {
            return@run failure(RollError.MissingHeldMask)
        }
        if (currentTurn.rollCount > 0 && !isValidHeldMask(heldMask!!)) {
            return@run failure(RollError.InvalidHeldMask)
        }

        // 5. Gerar valores dos dados
        val diceValues = generateDiceValues(
            currentTurn.rollCount, 
            heldMask, 
            tm, 
            currentTurn.turnId
        ) ?: return@run failure(RollError.InvalidMatchState)

        // 6. Criar Roll
        val roll = Roll(
            rollId = 0,
            turnId = currentTurn.turnId,
            createdAt = clock.now().toEpochMilliseconds(),
            heldMask = if (currentTurn.rollCount == 0) "00000" else heldMask!!,
            diceValues = diceValues,
        )

        // 7. Guardar Roll e incrementar rollCount
        val savedRoll = tm.matchRepository.createRoll(roll)
        tm.matchRepository.incrementTurnRollCount(currentTurn.turnId)

        success(savedRoll)
    }
```

**Conceitos Importantes:**

#### 1. **Validações Sequenciais**

```kotlin
// 1. Match existe e está ONGOING
// 2. Turn existe
// 3. É turno do user
// 4. Não excedeu maxRolls
// 5. HeldMask válido (se necessário)
```

**Por que tantas validações?**
- **Segurança**: Previne ações inválidas
- **Consistência**: Garante estado válido antes de modificar
- **Fail-fast**: Retorna erro imediatamente se inválido

---

#### 2. **Avanço Automático de Turno**

```kotlin
if (currentTurn.rollCount > 0 && heldMask != null && heldMask.isEmpty() 
    || currentTurn.rollCount == 3) {
    // Avançar turno
    advanceTurn(...)
}
```

**Condições para avançar:**
- `heldMask.isEmpty()`: User terminou turno manualmente (End Turn)
- `rollCount == 3`: User já lançou 3 vezes (máximo)

**O que faz:**
- Chama `advanceTurn()` que:
  - Avalia todas as hands do round (se round terminou)
  - Determina vencedor
  - Distribui pot
  - Cria próximo round (se necessário)
  - Cria próximo turn

---

#### 3. **generateDiceValues()**

```kotlin
private fun generateDiceValues(
    rollCount: Int,
    heldMask: String?,
    tm: Transaction,
    turnId: Int,
): String? {
    if (rollCount == 0) {
        // Primeiro roll: gera todos os dados aleatoriamente
        return (1..roundConfig.numberOfDice).joinToString(",") {
            randomDiceValue()
        }
    }
    
    // Rolls seguintes: mantém dados selecionados, relança outros
    val previousRoll = tm.matchRepository.getLastRollForTurn(turnId) ?: return null
    val previousValues = previousRoll.diceValues.split(",")

    return (0..4).joinToString(",") { idx ->
        if (heldMask!![idx] == '1') 
            previousValues[idx]  // Manter
        else 
            randomDiceValue()     // Relançar
    }
}
```

**Conceitos:**

##### a) **Primeiro Roll**

```kotlin
if (rollCount == 0) {
    return (1..5).joinToString(",") { randomDiceValue() }
}
```

- Gera 5 dados aleatórios
- Exemplo: `"A,K,Q,J,10"`

---

##### b) **Rolls Seguintes**

```kotlin
val previousValues = previousRoll.diceValues.split(",")
return (0..4).joinToString(",") { idx ->
    if (heldMask[idx] == '1') 
        previousValues[idx]  // Manter
    else 
        randomDiceValue()    // Relançar
}
```

**Exemplo:**
- Roll anterior: `["A", "K", "Q", "J", "10"]`
- HeldMask: `"10100"` (manter A e Q)
- Resultado: `["A", "K'", "Q", "J'", "10'"]` (K', J', 10' são novos valores)

---

#### 4. **isValidHeldMask()**

```kotlin
private fun isValidHeldMask(mask: String): Boolean =
    mask.length == roundConfig.numberOfDice &&
        mask.all { it in '0'..'1' } &&
        mask.count { it == '1' } < roundConfig.maxRollsPerTurn + 1
```

**Validações:**
- `length == 5`: Exatamente 5 caracteres
- `all { it in '0'..'1' }`: Apenas '0' ou '1'
- `count { it == '1' } < 4`: Máximo 3 dados mantidos (maxRollsPerTurn = 3)

**Por que máximo 3?**
- **Regra do jogo**: Não pode manter todos os dados (deve relançar pelo menos 1)

---

### Função `advanceTurn()`:

```kotlin
private fun advanceTurn(
    tm: Transaction,
    matchId: Int,
    userId: Int,
): Either<TurnError, Turn> {
    // 1. Validar match e turn
    val match = tm.matchRepository.getMatchById(matchId) ?: return failure(...)
    val currTurn = tm.matchRepository.currentTurn(matchId) ?: return failure(...)
    val round = tm.matchRepository.currentRound(matchId) ?: return failure(...)
    val roundPlayers = tm.matchRepository.getRoundPlayers(round.roundId)

    // 2. Calcular próximo turn
    val nextTurnNumber = currTurn.number + 1
    val roundPlayerCount = roundPlayers.size
    val roundEnded = nextTurnNumber > roundPlayerCount

    if (!roundEnded) {
        // 3a. Round não terminou → Avançar para próximo player
        val idx = roundPlayers.indexOfFirst { it.userId == currTurn.userId }
        val nextIdx = (idx + 1) % roundPlayerCount
        val nextPlayer = roundPlayers[nextIdx]

        tm.matchRepository.createTurn(round.roundId, nextPlayer.userId, nextTurnNumber)
        tm.matchRepository.updateTurnState(currTurn.turnId, TurnState.ENDED)
        val nextTurn = tm.matchRepository.getCurrentTurnForRound(round.roundId)!!
        return success(nextTurn)
    } else {
        // 3b. Round terminou → Avaliar hands e determinar vencedor
        // ... (ver abaixo)
    }
}
```

**Conceitos:**

#### 1. **Cálculo de Próximo Player**

```kotlin
val idx = roundPlayers.indexOfFirst { it.userId == currTurn.userId }
val nextIdx = (idx + 1) % roundPlayerCount
val nextPlayer = roundPlayers[nextIdx]
```

**O que faz:**
- Encontra índice do player atual
- Calcula próximo índice (circular: último → primeiro)
- Exemplo: `[A, B, C]`, current = B (idx=1) → next = C (idx=2)
- Exemplo: `[A, B, C]`, current = C (idx=2) → next = A (idx=0)

---

#### 2. **Round End Detection**

```kotlin
val roundEnded = nextTurnNumber > roundPlayerCount
```

**O que faz:**
- Se `nextTurnNumber > roundPlayerCount`, round terminou
- Exemplo: 3 players, turnNumber atual = 3 → nextTurnNumber = 4 > 3 → round terminou

---

### Função `advanceTurn()` - Parte 2 (Round End):

```kotlin
else {
    // Round terminou
    
    // 1. Buscar todos os turns do round
    val turnsForRound = tm.matchRepository
        .timeline(matchId)
        .filter { it.roundId == round.roundId }
        .sortedBy { it.number }

    // 2. Avaliar cada turn (criar Hand)
    val evaluations = mutableListOf<EvalInfo>()
    turnsForRound.forEach { t ->
        val lastRoll = tm.matchRepository.getLastRollForTurn(t.turnId)
        val (rankInt, tieKey) = if (lastRoll == null || lastRoll.diceValues.isBlank()) {
            0 to 0  // Sem roll = rank 0
        } else {
            // Converter string para DiceFace
            val diceFaces = lastRoll.diceValues.split(",").mapNotNull { s ->
                DiceFace.entries.firstOrNull { it.label == s }
            }
            
            // Avaliar hand
            val evaluated = HandEvaluator.evaluate(diceFaces)
            val rankValue = evaluated.rank.strength
            val (_, majorList, minorList) = evaluated
            val keyDigits = (majorList + minorList).map { df -> df.idx }.sortedDescending()
            val computedKey = keyDigits.fold(0) { acc, d -> acc * 10 + d }
            rankValue to computedKey
        }
        evaluations += EvalInfo(t.userId, rankInt, tieKey)
        tm.matchRepository.createHand(round.roundId, t.userId, lastRoll?.diceValues ?: "", rankInt, tieKey)
    }

    // 3. Determinar vencedor(es)
    val bestRank = evaluations.maxOf { it.rank }
    val byBestRank = evaluations.filter { it.rank == bestRank }
    val bestTie = byBestRank.maxOf { it.tieKey }
    val winners = byBestRank.filter { it.tieKey == bestTie }.map { it.userId }

    // 4. Distribuir pot
    val pot = round.pot
    val winnersCount = winners.size.coerceAtLeast(1)
    val share = pot / winnersCount
    winners.forEach { uid ->
        val user = tm.usersRepository.getUserById(uid)
        if (user != null) {
            tm.usersRepository.updateUserBalance(uid, user.balance + share)
        }
    }

    // 5. Marcar vencedor do round
    tm.matchRepository.setRoundWinner(round.roundId, winners.first())

    // 6. Verificar se match terminou
    val lobby = tm.lobbyRepository.getLobbyById(match.lobbyId)!!
    if (round.number >= lobby.maxRounds) {
        endMatch(matchId, tm)
        return success(turnsForRound.last())
    }

    // 7. Criar próximo round
    val nextRoundNumber = round.number + 1
    val nextRoundId = tm.matchRepository.createRound(matchId, nextRoundNumber, round.blind)
    tm.matchRepository.updateTurnState(currTurn.turnId, TurnState.ENDED)

    // 8. Determinar starting player do próximo round
    val lobbyPlayers = tm.matchRepository.getLobbyPlayers(currentMatch.lobbyId)
    val previousUser = tm.matchRepository.getPreviousStartingPlayer(matchId, round.number + 1)
    val startingUser = tm.usersRepository.getUserById(
        lobbyPlayers.firstOrNull { it.userId > previousUser }?.userId
            ?: lobbyPlayers.first().userId
    )

    // 9. Criar primeiro turn do próximo round
    if (startingUser == null) {
        tm.matchRepository.createTurn(nextRoundId, 1, 1)
    } else {
        tm.matchRepository.createTurn(nextRoundId, startingUser.userId, 1)
    }

    // 10. Cobrar blinds do próximo round
    lobbyPlayers.forEach { p ->
        tm.usersRepository.updateUserBalance(p.userId, p.balance - round.blind)
        tm.matchRepository.updateRoundPot(nextRoundId, round.blind)
    }

    // 11. Atualizar round atual
    tm.matchRepository.updateCurrentRound(matchId, nextRoundId)
    val newTurn = tm.matchRepository.getCurrentTurnForRound(nextRoundId)!!
    return success(newTurn)
}
```

**Conceitos Importantes:**

#### 1. **Avaliação de Hands**

```kotlin
val evaluated = HandEvaluator.evaluate(diceFaces)
val rankValue = evaluated.rank.strength
val (_, majorList, minorList) = evaluated
val keyDigits = (majorList + minorList).map { df -> df.idx }.sortedDescending()
val computedKey = keyDigits.fold(0) { acc, d -> acc * 10 + d }
```

**O que faz:**
- `HandEvaluator.evaluate()`: Avalia 5 dados e retorna `EvaluatedHand`
- `rank.strength`: Força do rank (ex: FIVE_OF_A_KIND = 7, FOUR_OF_A_KIND = 6, ...)
- `majorList`: Faces principais (ex: [A, A, A] para THREE_OF_A_KIND)
- `minorList`: Faces secundárias (ex: [K, Q] para kickers)
- `computedKey`: Chave numérica para desempate (ex: 11100 para A,A,A,K,Q)

**Exemplo:**
- Dados: `[A, A, A, K, Q]`
- Rank: `THREE_OF_A_KIND` (strength = 4)
- Major: `[A]` (índice = 0)
- Minor: `[K, Q]` (índices = 1, 2)
- Key: `01200` (sorted desc: [2, 1, 0, 0, 0])

---

#### 2. **Determinação de Vencedor**

```kotlin
val bestRank = evaluations.maxOf { it.rank }
val byBestRank = evaluations.filter { it.rank == bestRank }
val bestTie = byBestRank.maxOf { it.tieKey }
val winners = byBestRank.filter { it.tieKey == bestTie }.map { it.userId }
```

**Algoritmo:**
1. **Melhor rank**: Maior `rank` (ex: 7 = FIVE_OF_A_KIND)
2. **Filtrar por melhor rank**: Apenas players com esse rank
3. **Melhor tieKey**: Maior `tieKey` entre esses players
4. **Filtrar por melhor tieKey**: Vencedores finais

**Exemplo:**
- Player A: rank=6, tieKey=5000 (FOUR_OF_A_KIND com A)
- Player B: rank=6, tieKey=4000 (FOUR_OF_A_KIND com K)
- Player C: rank=5, tieKey=3000 (FULL_HOUSE)
- **Vencedor**: Player A (melhor rank=6, melhor tieKey=5000)

**Empate:**
- Se múltiplos players têm mesmo rank e tieKey → Dividem pot

---

#### 3. **Distribuição de Pot**

```kotlin
val pot = round.pot
val winnersCount = winners.size.coerceAtLeast(1)
val share = pot / winnersCount
winners.forEach { uid ->
    val user = tm.usersRepository.getUserById(uid)
    if (user != null) {
        tm.usersRepository.updateUserBalance(uid, user.balance + share)
    }
}
```

**Conceitos:**
- **Pot**: Total de blinds pagos no round
- **Share**: Pot dividido pelo número de vencedores
- **Empate**: Múltiplos vencedores dividem pot igualmente

**Exemplo:**
- Pot = 10.0
- 1 vencedor → Share = 10.0
- 2 vencedores → Share = 5.0 cada

---

#### 4. **Criação de Próximo Round**

```kotlin
val nextRoundNumber = round.number + 1
val nextRoundId = tm.matchRepository.createRound(matchId, nextRoundNumber, round.blind)
```

**O que faz:**
- Cria novo round com número incrementado
- Mantém mesmo blind (pode ser configurável)

---

#### 5. **Determinação de Starting Player**

```kotlin
val previousUser = tm.matchRepository.getPreviousStartingPlayer(matchId, round.number + 1)
val startingUser = tm.usersRepository.getUserById(
    lobbyPlayers.firstOrNull { it.userId > previousUser }?.userId
        ?: lobbyPlayers.first().userId
)
```

**Algoritmo:**
- **Primeiro round**: Host começa
- **Rounds seguintes**: Próximo player (circular)
- Exemplo: Round 1 = A, Round 2 = B, Round 3 = C, Round 4 = A

---

#### 6. **Cobrança de Blinds**

```kotlin
lobbyPlayers.forEach { p ->
    tm.usersRepository.updateUserBalance(p.userId, p.balance - round.blind)
    tm.matchRepository.updateRoundPot(nextRoundId, round.blind)
}
```

**O que faz:**
- Deduz blind de cada player
- Adiciona blind ao pot do round

---

### Função `endMatch()`:

```kotlin
private fun endMatch(matchId: Int, tm: Transaction) {
    tm.matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED)
    tm.matchRepository.setMatchFinishedAt(matchId, clock.now().toEpochMilliseconds())
    
    val match = tm.matchRepository.getMatchById(matchId)!!
    tm.lobbyRepository.updateLobbyStatus(match.lobbyId, LobbyStatus.ONGOING)
}
```

**Conceitos:**
- **Match Status**: `FINISHED`
- **Finished At**: Timestamp de fim
- **Lobby Status**: Volta para `ONGOING` (pode criar novo match)

---

## 📍 PARTE 7: DOMAIN LAYER - HandEvaluator.kt

### Função `evaluate()`:

```kotlin
fun evaluate(dice: List<DiceFace>): EvaluatedHand {
    require(dice.size == 5) { "Exactly 5 dice required" }

    // 1. Contar frequências
    val freq = dice.groupingBy { it }.eachCount()
    val facesDesc = freq.keys.sortedByDescending { it.idx }
    val countsDesc = freq.entries
        .sortedWith(compareByDescending<Map.Entry<DiceFace, Int>> { it.value }
            .thenByDescending { it.key.idx })
        .map { it.key to it.value }
    val countValues = countsDesc.map { it.second }

    // 2. Verificar straight
    val uniqueFaces = freq.keys.toList()
    val isStraight = uniqueFaces.size == 5

    // 3. Determinar rank
    return when {
        countValues.firstOrNull() == 5 -> {
            // FIVE_OF_A_KIND
            val five = countsDesc.first().first
            EvaluatedHand(HandRank.FIVE_OF_A_KIND, listOf(five), emptyList())
        }
        countValues.firstOrNull() == 4 -> {
            // FOUR_OF_A_KIND
            val four = countsDesc.first().first
            val kicker = facesDesc.first { it != four }
            EvaluatedHand(HandRank.FOUR_OF_A_KIND, listOf(four), listOf(kicker))
        }
        countValues == listOf(3, 2) -> {
            // FULL_HOUSE
            val three = countsDesc.first().first
            val pair = countsDesc[1].first
            EvaluatedHand(HandRank.FULL_HOUSE, listOf(three), listOf(pair))
        }
        isStraight -> {
            // STRAIGHT
            val high = uniqueFaces.maxBy { it.idx }
            EvaluatedHand(HandRank.STRAIGHT, listOf(high), emptyList())
        }
        countValues.firstOrNull() == 3 -> {
            // THREE_OF_A_KIND
            val three = countsDesc.first().first
            val kickers = facesDesc.filter { it != three }
            EvaluatedHand(HandRank.THREE_OF_A_KIND, listOf(three), kickers)
        }
        countValues == listOf(2, 2, 1) -> {
            // TWO_PAIR
            val pair1 = countsDesc[0].first
            val pair2 = countsDesc[1].first
            val remaining = countsDesc[2].first
            val pairs = listOf(pair1, pair2).sortedByDescending { it.idx }
            EvaluatedHand(HandRank.TWO_PAIR, pairs, listOf(remaining))
        }
        countValues == listOf(2, 1, 1, 1) -> {
            // ONE_PAIR
            val pair = countsDesc.first().first
            val kickers = facesDesc.filter { it != pair }
            EvaluatedHand(HandRank.ONE_PAIR, listOf(pair), kickers)
        }
        else -> {
            // BUST (high card)
            val high = facesDesc.first()
            val kickers = facesDesc.drop(1)
            EvaluatedHand(HandRank.BUST, listOf(high), kickers)
        }
    }
}
```

**Conceitos:**

#### 1. **Contagem de Frequências**

```kotlin
val freq = dice.groupingBy { it }.eachCount()
// Exemplo: [A, A, A, K, Q] → {A: 3, K: 1, Q: 1}
```

**O que faz:**
- Agrupa dados por face
- Conta quantas vezes cada face aparece
- **Base para determinar rank**

---

#### 2. **Ordenação por Frequência e Índice**

```kotlin
val countsDesc = freq.entries
    .sortedWith(compareByDescending<Map.Entry<DiceFace, Int>> { it.value }
        .thenByDescending { it.key.idx })
```

**O que faz:**
- Ordena por frequência (descendente)
- Em caso de empate, ordena por índice (descendente)
- **Exemplo**: `{A: 3, K: 1, Q: 1}` → `[(A, 3), (K, 1), (Q, 1)]`

---

#### 3. **Hierarquia de Ranks**

```
FIVE_OF_A_KIND (5 iguais)     → strength = 7
FOUR_OF_A_KIND (4 iguais)     → strength = 6
FULL_HOUSE (3+2)               → strength = 5
STRAIGHT (5 diferentes)        → strength = 4
THREE_OF_A_KIND (3 iguais)     → strength = 3
TWO_PAIR (2+2)                 → strength = 2
ONE_PAIR (2 iguais)            → strength = 1
BUST (high card)               → strength = 0
```

**Ordem de verificação:**
- Verifica ranks mais fortes primeiro
- **Importante**: Ordem importa (ex: FULL_HOUSE também tem 3 iguais, mas é melhor que THREE_OF_A_KIND)

---

#### 4. **Major e Minor Lists**

```kotlin
EvaluatedHand(HandRank.THREE_OF_A_KIND, listOf(three), kickers)
```

**O que são:**
- **Major**: Faces principais (ex: `[A]` para THREE_OF_A_KIND com A)
- **Minor**: Faces secundárias (ex: `[K, Q]` para kickers)

**Uso:**
- **Rank**: Determina tipo de hand
- **Major**: Desempate primário (ex: THREE_OF_A_KIND com A > THREE_OF_A_KIND com K)
- **Minor**: Desempate secundário (ex: mesmo THREE_OF_A_KIND, melhor kicker ganha)

---

## 🔄 FLUXO COMPLETO RESUMIDO

### 1. Carregar Match

```
Frontend: useEffect → fetchMatchData()
  ↓
API: GET /api/matches/{id}/rounds/current
  ↓
Service: getCurrentRound() → RoundWithDetails
  ↓
Frontend: dispatch({ type: "fetch_success", ... })
  ↓
Render: RollScreen ou WaitingScreen
```

---

### 2. Roll Dice

```
Frontend: handleRoll() → api.rollDice(matchId, heldMask)
  ↓
API: POST /api/matches/{id}/turns/current/roll
  ↓
Service: rollDice()
  ↓
  ├─ Validar match, turn, user
  ├─ Se heldMask vazio OU rollCount == 3 → advanceTurn()
  ├─ Gerar diceValues (manter selecionados, relançar outros)
  ├─ Criar Roll
  ├─ Incrementar rollCount
  └─ Retornar Roll
  ↓
Frontend: api.getCurrentRound() (atualizar estado)
  ↓
Frontend: dispatch({ type: "roll_success", ... })
  ↓
Render: Atualizar dados exibidos
```

---

### 3. End Turn

```
Frontend: handleEnd() → api.rollDice(matchId, "")
  ↓
Service: rollDice() → heldMask.isEmpty() → advanceTurn()
  ↓
  ├─ Se round não terminou:
  │   ├─ Criar próximo turn
  │   └─ Retornar próximo turn
  │
  └─ Se round terminou:
      ├─ Avaliar todas as hands
      ├─ Determinar vencedor(es)
      ├─ Distribuir pot
      ├─ Se última round → endMatch()
      ├─ Se não → Criar próximo round
      │   ├─ Determinar starting player
      │   ├─ Criar primeiro turn
      │   └─ Cobrar blinds
      └─ Retornar novo turn
  ↓
Frontend: checkMatchEnded() → Se terminou → MatchEndScreen
Frontend: api.getCurrentRound() → Atualizar estado
```

---

### 4. Round End (Detalhado)

```
advanceTurn() detecta: nextTurnNumber > roundPlayerCount
  ↓
Buscar todos os turns do round
  ↓
Para cada turn:
  ├─ Buscar último roll
  ├─ Converter string → DiceFace[]
  ├─ HandEvaluator.evaluate() → EvaluatedHand
  ├─ Calcular rank e tieKey
  └─ Criar Hand na BD
  ↓
Determinar vencedor(es):
  ├─ bestRank = max(ranks)
  ├─ byBestRank = filter(rank == bestRank)
  ├─ bestTie = max(tieKeys)
  └─ winners = filter(tieKey == bestTie)
  ↓
Distribuir pot:
  ├─ share = pot / winners.size
  └─ winners.forEach { updateBalance(userId, balance + share) }
  ↓
setRoundWinner(roundId, winners.first())
  ↓
Se round.number >= maxRounds:
  └─ endMatch()
Senão:
  ├─ createRound(nextNumber, blind)
  ├─ createTurn(startingPlayer, 1)
  ├─ Cobrar blinds
  └─ updateCurrentRound(matchId, nextRoundId)
```

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **State Machine**: useReducer para estados complexos (loading → playing → ended)
2. **HeldMask**: String binária indicando quais dados manter
3. **Roll Count**: Máximo 3 rolls por turn
4. **Round End**: Quando todos os players jogaram (nextTurnNumber > playerCount)
5. **Hand Evaluation**: HandEvaluator avalia 5 dados e retorna rank + tieKey
6. **Winner Determination**: Melhor rank → melhor tieKey → vencedor(es)
7. **Pot Distribution**: Pot dividido igualmente entre vencedores
8. **Match End**: Quando última round termina
9. **Transaction**: Toda operação atómica (roll, advanceTurn, endMatch)
10. **Starting Player**: Rotação circular entre rounds

---

## 🔐 VALIDAÇÕES E SEGURANÇA

- ✅ User só pode lançar dados no seu turno
- ✅ Máximo 3 rolls por turn
- ✅ HeldMask válido (5 caracteres, apenas '0'/'1', máximo 3 '1's)
- ✅ Match deve estar ONGOING
- ✅ Turn deve existir e estar ACTIVE
- ✅ Transações garantem atomicidade
- ✅ Validações sequenciais (fail-fast)

---

## 💡 DIFERENÇAS: Roll vs End Turn

| Aspecto | Roll | End Turn |
|---------|------|----------|
| **HeldMask** | Especifica dados a manter | Vazio (`""`) |
| **Gera Dados** | Sim (mantém selecionados, relança outros) | Não |
| **Avança Turno** | Só se `rollCount == 3` | Sempre |
| **Cria Roll** | Sim | Não |

---

## 🎯 Pontos para a Discussão

1. **"Por que usar useReducer em vez de useState?"**
   - Complexidade: Muitos estados relacionados
   - State Machine: Estados bem definidos
   - Predictability: Reducer é função pura

2. **"Como funciona o HeldMask?"**
   - String binária de 5 caracteres
   - '1' = manter, '0' = relançar
   - Primeiro roll não precisa (relança todos)

3. **"Como é determinado o vencedor?"**
   - Melhor rank (maior strength)
   - Em caso de empate, melhor tieKey
   - Múltiplos vencedores dividem pot

4. **"O que acontece quando round termina?"**
   - Avalia todas as hands
   - Determina vencedor(es)
   - Distribui pot
   - Cria próximo round (se não for última)

5. **"Como funciona a rotação de starting player?"**
   - Primeiro round: Host
   - Rounds seguintes: Próximo player (circular)

---

## 💬 Frases-Chave para a Discussão

- "Match usa state machine com useReducer para gerir estados complexos (loading, playing, ended)"
- "HeldMask é string binária indicando quais dados manter ('1') ou relançar ('0')"
- "HandEvaluator avalia 5 dados e retorna rank (tipo de hand) + tieKey (para desempate)"
- "Vencedor é determinado por melhor rank, depois melhor tieKey em caso de empate"
- "Round termina quando todos os players jogaram (nextTurnNumber > playerCount)"
- "Pot é dividido igualmente entre vencedores em caso de empate"
- "Match termina quando última round termina (round.number >= maxRounds)"

---

Fim da explicação do Match! 🎉
