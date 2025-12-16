# 🤔 useState vs useReducer: Quando Usar Cada Um?

## 📊 Análise dos Componentes de Lobby

### Estado Atual:

**LobbyList.tsx** - usa `useState`:
```typescript
const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
const [searchQuery, setSearchQuery] = useState("");
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [error, setError] = useState("");
const [joiningLobbyId, setJoiningLobbyId] = useState<number | null>(null);
```

**LobbyDetails.tsx** - usa `useState`:
```typescript
const [lobby, setLobby] = useState<LobbyWithPlayers | null>(null);
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [isLeaving, setIsLeaving] = useState(false);
```

**CreateLobby.tsx** - usa `useReducer`:
```typescript
type State = 
  | { tag: "editing"; form: LobbyFormData }
  | { tag: "submitting"; form: LobbyFormData }
  | { tag: "error"; form: LobbyFormData; message: string }
  | { tag: "redirect" };
```

---

## 🎯 Quando Usar useState?

### ✅ Use `useState` quando:

1. **Estado simples e independente**
   - Cada estado não depende de outros
   - Atualizações são diretas e isoladas

2. **Poucas atualizações de estado**
   - Componente não tem muitas mudanças de estado
   - Lógica de atualização é simples

3. **Estados não relacionados**
   - Estados não precisam ser atualizados juntos
   - Não há dependências entre estados

**Exemplo (LobbyList.tsx atual):**
```typescript
// ✅ Estados independentes - useState faz sentido
const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
const [searchQuery, setSearchQuery] = useState("");  // Independente
const [loadingState, setLoadingState] = useState<LoadingState>("idle");  // Independente
```

**Por que funciona bem:**
- `lobbies` e `searchQuery` são independentes
- `loadingState` é independente
- Atualizações são simples (`setLobbies`, `setSearchQuery`)

---

## 🎯 Quando Usar useReducer?

### ✅ Use `useReducer` quando:

1. **Estado complexo com múltiplas propriedades relacionadas**
   - Estado tem várias propriedades que mudam juntas
   - Atualizações precisam ser coordenadas

2. **Lógica de atualização complexa**
   - Múltiplas condições para atualizar estado
   - Transições de estado bem definidas (state machine)

3. **Estados mutuamente exclusivos**
   - Estados que não podem coexistir (ex: `loading` e `error`)
   - Precisa garantir consistência

4. **Múltiplas atualizações de estado em uma ação**
   - Uma ação atualiza vários estados
   - Reduz risco de estados inconsistentes

**Exemplo (CreateLobby.tsx):**
```typescript
// ✅ State machine - useReducer faz sentido
type State = 
  | { tag: "editing"; form: LobbyFormData }
  | { tag: "submitting"; form: LobbyFormData }  // Não pode ter error simultaneamente
  | { tag: "error"; form: LobbyFormData; message: string }
  | { tag: "redirect" };
```

**Por que funciona bem:**
- Estados são mutuamente exclusivos (não pode estar `submitting` e `error` ao mesmo tempo)
- Transições bem definidas (`editing` → `submitting` → `success` ou `error`)
- Type-safe (TypeScript garante estados válidos)

---

## 🔍 Análise: LobbyList.tsx

### Estado Atual (useState):

```typescript
const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
const [searchQuery, setSearchQuery] = useState("");
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [error, setError] = useState("");
const [joiningLobbyId, setJoiningLobbyId] = useState<number | null>(null);
```

### ❓ Faria sentido usar useReducer?

**Análise:**

#### ✅ **Mantém useState porque:**
1. **Estados são independentes**
   - `lobbies` não depende de `searchQuery`
   - `loadingState` não depende de `error`
   - `joiningLobbyId` é isolado

2. **Atualizações são simples**
   - `setLobbies(response.data)` - direto
   - `setSearchQuery(e.target.value)` - direto
   - Não há lógica complexa

3. **Não há state machine**
   - Estados podem coexistir (ex: `loadingState = "success"` e `error = ""`)
   - Não há transições bem definidas

#### ❌ **Não faz sentido useReducer porque:**
- Adicionaria complexidade desnecessária
- Não há benefício (estados não são coordenados)
- Código ficaria mais verboso sem ganho

---

### 💡 Mas poderia melhorar com useReducer se...

**Se quisesses garantir que `loadingState` e `error` não podem coexistir:**

```typescript
// COM useReducer (mais type-safe)
type State = 
  | { tag: "idle"; lobbies: LobbyDetails[]; searchQuery: string }
  | { tag: "loading"; lobbies: LobbyDetails[]; searchQuery: string }
  | { tag: "success"; lobbies: LobbyDetails[]; searchQuery: string }
  | { tag: "error"; lobbies: LobbyDetails[]; searchQuery: string; message: string };

// Vantagem: TypeScript garante que não podes ter loadingState="loading" e error="..." simultaneamente
```

**Mas neste caso:**
- ❌ Overhead desnecessário
- ❌ Código mais complexo
- ✅ `useState` atual é suficiente

---

## 🔍 Análise: LobbyDetails.tsx

### Estado Atual (useState):

```typescript
const [lobby, setLobby] = useState<LobbyWithPlayers | null>(null);
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [isLeaving, setIsLeaving] = useState(false);
```

### ❓ Faria sentido usar useReducer?

**Análise:**

#### ✅ **Mantém useState porque:**
1. **Estados simples**
   - `lobby`: dados do lobby (ou null)
   - `loadingState`: estado de carregamento
   - `isLeaving`: flag booleana simples

2. **Não há dependências complexas**
   - `isLeaving` é independente
   - `loadingState` e `lobby` são independentes

3. **Lógica simples**
   - `setLobby(response.data.value)` - direto
   - `setLoadingState("success")` - direto

#### ❌ **Não faz sentido useReducer porque:**
- Apenas 3 estados simples
- Não há state machine
- Não há transições complexas

---

### 💡 Mas poderia melhorar com useReducer se...

**Se quisesses garantir que `lobby` e `loadingState` são consistentes:**

```typescript
// COM useReducer (mais type-safe)
type State = 
  | { tag: "idle" }
  | { tag: "loading" }
  | { tag: "success"; lobby: LobbyWithPlayers; isLeaving: boolean }
  | { tag: "error" };

// Vantagem: Se tag="success", lobby SEMPRE existe (não pode ser null)
```

**Mas neste caso:**
- ❌ Overhead desnecessário
- ✅ `useState` atual funciona bem
- ✅ Verificação `if (!lobby)` já garante type safety

---

## 📊 Comparação: CreateLobby vs LobbyList

### CreateLobby.tsx (useReducer) ✅

**Por que useReducer faz sentido:**
```typescript
// Estados mutuamente exclusivos
type State = 
  | { tag: "editing"; form: LobbyFormData }
  | { tag: "submitting"; form: LobbyFormData }  // ← Não pode ter error aqui
  | { tag: "error"; form: LobbyFormData; message: string }
  | { tag: "redirect" };

// Transições bem definidas
editing → submitting → success (redirect) OU error
```

**Vantagens:**
- ✅ Type-safe: não pode estar `submitting` e `error` simultaneamente
- ✅ Transições explícitas
- ✅ Estado sempre consistente

---

### LobbyList.tsx (useState) ✅

**Por que useState faz sentido:**
```typescript
// Estados independentes
const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
const [searchQuery, setSearchQuery] = useState("");
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [error, setError] = useState("");
```

**Vantagens:**
- ✅ Simples e direto
- ✅ Estados independentes (não precisam coordenação)
- ✅ Fácil de entender

**Se mudasse para useReducer:**
```typescript
// ❌ Mais complexo sem benefício
type State = {
  lobbies: LobbyDetails[];
  searchQuery: string;
  loadingState: LoadingState;
  error: string;
  joiningLobbyId: number | null;
};

// Reducer seria apenas:
function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "set_lobbies":
      return { ...state, lobbies: action.lobbies };
    case "set_search":
      return { ...state, searchQuery: action.value };
    // ... apenas wrappers de setState
  }
}
```

**Problemas:**
- ❌ Mais código sem benefício
- ❌ Não há lógica complexa para justificar
- ❌ Estados continuam independentes (não há coordenação)

---

## 🎓 Regra de Ouro

### Use `useState` quando:
- ✅ Estados são **independentes**
- ✅ Atualizações são **simples** (1 linha)
- ✅ Não há **state machine**
- ✅ Não há **dependências** entre estados

### Use `useReducer` quando:
- ✅ Estados são **relacionados** (mudam juntos)
- ✅ Há **state machine** (estados mutuamente exclusivos)
- ✅ Lógica de atualização é **complexa**
- ✅ Precisa **garantir consistência** entre estados

---

## 💡 Exemplo: Quando Refatorar para useReducer

### Cenário: LobbyList com Polling

**Se adicionasses polling (atualizar lista a cada X segundos):**

```typescript
// ❌ COM useState (pode ter estados inconsistentes)
const [lobbies, setLobbies] = useState<LobbyDetails[]>([]);
const [loadingState, setLoadingState] = useState<LoadingState>("idle");
const [isPolling, setIsPolling] = useState(false);

// Problema: Podes ter loadingState="loading" e isPolling=true simultaneamente
// (inconsistente - não deveria estar a carregar se já está a fazer polling)
```

**Solução com useReducer:**
```typescript
// ✅ COM useReducer (garante consistência)
type State = 
  | { tag: "idle"; lobbies: LobbyDetails[] }
  | { tag: "loading"; lobbies: LobbyDetails[] }
  | { tag: "polling"; lobbies: LobbyDetails[] }  // ← Não pode estar loading simultaneamente
  | { tag: "error"; lobbies: LobbyDetails[]; message: string };

// TypeScript garante que não podes ter loading e polling ao mesmo tempo
```

---

## 📝 Resposta Direta

### Para LobbyList.tsx e LobbyDetails.tsx:

**❌ NÃO faz sentido usar useReducer porque:**

1. **Estados são independentes**
   - Não há dependências entre `lobbies`, `searchQuery`, `loadingState`
   - Cada um pode ser atualizado isoladamente

2. **Lógica simples**
   - Atualizações são diretas (`setLobbies`, `setSearchQuery`)
   - Não há transições complexas

3. **Não há state machine**
   - Estados podem coexistir (ex: `loadingState="success"` e `error=""`)
   - Não há estados mutuamente exclusivos

4. **Overhead desnecessário**
   - useReducer adicionaria complexidade sem benefício
   - Código ficaria mais verboso

**✅ Mantém useState porque:**
- É a ferramenta certa para o trabalho
- Código mais simples e legível
- Fácil de manter

---

## 🎯 Comparação Final

| Componente | Estado Atual | Deveria usar useReducer? | Por quê? |
|------------|--------------|---------------------------|----------|
| **CreateLobby** | useReducer | ✅ Sim (já usa) | State machine, transições complexas |
| **Login** | useReducer | ✅ Sim (já usa) | State machine, transições complexas |
| **LobbyList** | useState | ❌ Não | Estados independentes, lógica simples |
| **LobbyDetails** | useState | ❌ Não | Estados simples, sem dependências |

---

## 💬 Frases para a Discussão

- "useState é suficiente quando estados são independentes e atualizações são simples"
- "useReducer faz sentido quando há state machine ou estados que precisam ser coordenados"
- "CreateLobby usa useReducer porque tem transições bem definidas (editing → submitting → success/error)"
- "LobbyList usa useState porque estados são independentes e não há lógica complexa"
- "Não adicionar complexidade desnecessária - usar a ferramenta certa para cada caso"

---

## 🎓 Conclusão

**Para LobbyList e LobbyDetails:**
- ✅ **Mantém useState** - é a escolha correta
- ❌ **Não refatores para useReducer** - adicionaria complexidade sem benefício
- ✅ **Código atual está bem** - simples, legível, fácil de manter

**A regra é:**
- **useState** para estados simples e independentes
- **useReducer** para state machines e lógica complexa

---

Fim da análise! 🎉
