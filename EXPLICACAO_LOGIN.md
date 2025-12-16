# 🔐 EXPLICAÇÃO COMPLETA E DETALHADA: FLUXO DE LOGIN

## 🎯 Visão Geral do Fluxo

```
Frontend (React) → API (HTTP Controller) → Service → Domain → Repository → Database
                                                              ↓
                    Autenticação em Requests Subsequentes ← Token Validation
```

**Diferente do registo**, o login também estabelece um **sistema de autenticação** que será usado em todos os requests seguintes.

---

## 📍 PARTE 1: FRONTEND - Login.tsx

### Padrão: useReducer (State Machine)

O componente `Login.tsx` usa **`useReducer`** em vez de `useState`. Por quê?

**Conceito: State Machine Pattern**
- Estados bem definidos: `"editing" | "posting" | "succeed" | "failed"`
- Transições explícitas via `dispatch`
- Mais previsível e testável que múltiplos `useState`

---

### Definição dos Estados:

```typescript
type State = {
  username: string;
  password: string;
  error: string | undefined;
  stage: "editing" | "posting" | "succeed" | "failed";
};
```

**Estados possíveis:**
- `"editing"`: Utilizador está a preencher o formulário
- `"posting"`: Request HTTP em curso (loading)
- `"succeed"`: Login bem-sucedido
- `"failed"`: Login falhou (erro)

**Por que State Machine?**
- Evita estados inválidos (ex: `posting = true` e `error != null` simultaneamente)
- Facilita debugging (sempre sabes em que estado estás)
- Melhor UX (botão desabilitado durante `posting`)

---

### Actions (Eventos):

```typescript
type Action =
  | { type: "input-change"; username: string; password: string }
  | { type: "post" }
  | { type: "success" }
  | { type: "error"; message: string };
```

**Conceito: Action Pattern (Redux-like)**
- Cada ação descreve **o que aconteceu**, não **como mudar o estado**
- Reducer decide como o estado muda baseado na ação
- **Vantagem**: Lógica de estado centralizada no reducer

---

### Reducer Function:

```typescript
function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "input-change":
      return {
        ...state,
        username: action.username,
        password: action.password,
      };
    case "post":
      return {
        ...state,
        stage: "posting",
        error: undefined,  // Limpa erro anterior
      };
    case "success":
      return {
        username: "",
        password: "",
        error: undefined,
        stage: "succeed",
      };
    case "error":
      return {
        ...state,
        stage: "failed",
        error: action.message,
      };
    default:
      return state;  // Estado imutável - sempre retorna novo objeto
  }
}
```

**Conceitos Importantes:**

#### 1. **Imutabilidade**
- Sempre retorna **novo objeto**, nunca modifica o existente
- `...state` (spread operator) copia propriedades existentes
- **Por que?** React detecta mudanças comparando referências
- Se modificares `state.username` diretamente, React não re-renderiza!

#### 2. **Pure Function**
- Reducer é uma função pura (sem side effects)
- Dado mesmo `state` e `action`, sempre retorna mesmo resultado
- Facilita testes e debugging

---

### Uso do Reducer:

```typescript
const [state, dispatch] = useReducer(reducer, initialState);
```

**Como funciona:**
- `state`: Estado atual
- `dispatch`: Função para enviar ações
- `initialState`: Estado inicial

**Exemplo de uso:**
```typescript
dispatch({ type: "post" });  // Muda stage para "posting"
```

---

### Handle Submit:

```typescript
const handleSubmit = async (e: React.FormEvent | React.MouseEvent) => {
  e.preventDefault();
  dispatch({ type: "post" });  // Muda para estado "posting"

  try {
    const response = await api.login({
      username: state.username,
      password: state.password,
    });

    // IMPORTANTE: Guarda token e user info
    login(response.data.token, response.data.userId, response.data.username);

    dispatch({ type: "success" });
    navigate("/lobbies");  // Redireciona para lobbies
  } catch (err) {
    if (err instanceof ApiError) {
      dispatch({ type: "error", message: err.message });
    } else {
      dispatch({ type: "error", message: "Ocorreu um erro durante o login" });
    }
  }
};
```

**Conceitos:**

#### 1. **`e.preventDefault()`**
- Previne comportamento padrão (submit do form recarrega página)
- Permite controlo total do fluxo

#### 2. **`login()` do AuthContext**
- Função do contexto de autenticação
- Guarda token no localStorage e atualiza estado global
- **Mais detalhes na Parte 2**

#### 3. **Tratamento de Erros**
- `ApiError`: Erro estruturado da API (status code, mensagem)
- Erro genérico: Fallback para erros inesperados
- Sempre mostra feedback ao utilizador

---

### Input Handling:

```typescript
onChange={(e) =>
  dispatch({
    type: "input-change",
    username: e.target.value,
    password: state.password,  // Mantém password atual
  })
}
```

**Conceito: Controlled Components**
- Valor do input controlado por `state.username`
- Cada mudança dispara `dispatch` → atualiza estado → re-render
- **Vantagem**: Estado sempre sincronizado com UI

**Alternativa (Uncontrolled):**
```typescript
// NÃO usado aqui, mas possível:
<input ref={inputRef} />  // Valor não controlado por React
```

---

## 📍 PARTE 2: AUTH CONTEXT - AuthContext.tsx

### Context API do React:

**Conceito: Context API**
- Permite partilhar estado entre componentes sem prop drilling
- Estado global da aplicação (autenticação)
- Qualquer componente pode aceder via `useAuth()`

---

### Definição do Context:

```typescript
interface AuthContextType {
  isAuthenticated: boolean;
  userId: number | null;
  username: string | null;
  token: string | null;
  login: (token: string, userId: number, username: string) => void;
  logout: () => Promise<void>;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);
```

**Conceitos:**

#### 1. **`createContext`**
- Cria contexto React
- `undefined` como default (será substituído pelo Provider)

#### 2. **Estado Global**
- `isAuthenticated`: Flag booleana (mais fácil que verificar `token != null`)
- `userId`, `username`, `token`: Dados do utilizador autenticado
- `login`, `logout`: Funções para modificar estado

---

### Provider Component:

```typescript
export function AuthProvider({ children }: AuthProviderProps) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userId, setUserId] = useState<number | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
```

**Conceito: Provider Pattern**
- `AuthProvider` envolve a aplicação (ver `index.tsx`)
- Todos os componentes filhos têm acesso ao contexto
- Estado gerido aqui é partilhado globalmente

---

### Persistência com localStorage:

```typescript
useEffect(() => {
  const storedToken = localStorage.getItem("authToken");
  const storedUserId = localStorage.getItem("userId");
  const storedUsername = localStorage.getItem("username");

  if (storedToken && storedUserId && storedUsername) {
    setToken(storedToken);
    setUserId(parseInt(storedUserId));
    setUsername(storedUsername);
    setIsAuthenticated(true);
  }

  setIsLoading(false);
}, []);
```

**Conceitos Importantes:**

#### 1. **`useEffect` com Array Vazio `[]`**
- Executa **apenas uma vez** quando componente monta
- **Use case**: Carregar dados iniciais (ex: verificar se user já está logado)

#### 2. **localStorage**
- Armazenamento persistente no browser
- **Persiste entre sessões** (diferente de `sessionStorage`)
- **Limitações**:
  - Apenas strings (precisa `parseInt`, `JSON.stringify`, etc.)
  - ~5-10MB por domínio
  - Sincrónico (pode bloquear UI se muito grande)

#### 3. **Hydration (Hidratação)**
- Ao carregar página, verifica se há token guardado
- Se sim, restaura estado de autenticação
- **Vantagem**: User não precisa fazer login novamente após refresh

#### 4. **`isLoading`**
- Flag para indicar que ainda está a verificar autenticação
- Evita flash de "não autenticado" antes de verificar localStorage
- Componentes podem mostrar loading enquanto `isLoading = true`

---

### Função `login`:

```typescript
const login = (newToken: string, newUserId: number, newUsername: string) => {
  // 1. Persiste no localStorage
  localStorage.setItem("authToken", newToken);
  localStorage.setItem("userId", newUserId.toString());
  localStorage.setItem("username", newUsername);

  // 2. Atualiza estado React
  setToken(newToken);
  setUserId(newUserId);
  setUsername(newUsername);
  setIsAuthenticated(true);
};
```

**Fluxo:**
1. **Persiste** dados no localStorage (sobrevive a refresh)
2. **Atualiza** estado React (disponível imediatamente)
3. **Marca** como autenticado

**Por que ambos?**
- `localStorage`: Persistência
- Estado React: Reatividade (componentes re-renderizam)

---

### Função `logout`:

```typescript
const logout = async () => {
  try {
    await api.logout();  // Chama API para invalidar token no servidor
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

**Conceitos:**

#### 1. **`finally` Block**
- Sempre executa, mesmo se `try` ou `catch` lançarem exceção
- **Garante** que estado é limpo mesmo se API falhar
- **Importante**: Evita estado inconsistente (token removido localmente mas não no servidor)

#### 2. **Logout no Servidor**
- `api.logout()` marca token como `revoked = true` na BD
- **Por que?** Se token for roubado, pode ser invalidado
- **Sem isso**: Token continua válido mesmo após logout local

---

### Hook `useAuth`:

```typescript
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
```

**Conceito: Custom Hook**
- Encapsula lógica de aceder ao contexto
- **Validação**: Garante que hook só é usado dentro de `AuthProvider`
- **Type Safety**: TypeScript sabe que `context` não é `undefined` após verificação

**Uso:**
```typescript
const { isAuthenticated, login, logout } = useAuth();
```

---

## 📍 PARTE 3: CLIENTE API - api.ts

### Função `api.login()`:

```typescript
login(input: UserLoginInput): Promise<LoginResponse> {
  return fetchApi<LoginResponse>("/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}
```

**O que faz:**
- Chama `POST /api/login`
- Envia `{ username, password }` como JSON
- Retorna `LoginResponse` com token e dados do user

---

### Função `fetchApi()` (Revisão):

```typescript
export async function fetchApi<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem("authToken");
  
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  
  if (!response.ok) {
    throw new ApiError(response.status, errorMessage);
  }
  
  return response.json();
}
```

**Nota para Login:**
- No login, `token` ainda é `null` (ainda não autenticado)
- Por isso, `Authorization` header não é adicionado
- **Após login**, requests subsequentes incluem o token automaticamente

---

## 📍 PARTE 4: HTTP LAYER - UserController.kt

### Endpoint: `POST /api/login`

```kotlin
@PostMapping("/api/login")
fun login(
    @RequestBody input: UserLoginInputModel,
): ResponseEntity<*> =
    when (val res = userService.login(input.username, input.password)) {
        is Success ->
            ResponseEntity
                .ok()
                .header("Location", Uris.Lobby.LIST)
                .header(
                    "Set-Cookie",
                    "AuthCookie=${res.value.token}; HttpOnly; Secure; SameSite=Strict",
                ).body(
                    ApiResponse(
                        data =
                            UserSessionOutputModel(
                                res.value.userId,
                                res.value.username,
                                res.value.token,
                            ),
                    ),
                )

        is Failure ->
            when (res.value) {
                is UserLoginError.InvalidCredentials ->
                    Problem.response(
                        HttpStatus.BAD_REQUEST,
                        Problem.invalidCredentials,
                    )
            }
    }
```

**Conceitos Importantes:**

### 1. **Set-Cookie Header**

```kotlin
.header(
    "Set-Cookie",
    "AuthCookie=${res.value.token}; HttpOnly; Secure; SameSite=Strict",
)
```

**O que é?**
- Instrui o browser a guardar cookie chamado `AuthCookie`
- Valor: token de autenticação

**Atributos do Cookie:**

#### **`HttpOnly`**
- Cookie **não acessível** via JavaScript (`document.cookie`)
- **Proteção**: Previne XSS (Cross-Site Scripting)
- Se atacante injetar JS malicioso, não consegue ler o cookie

#### **`Secure`**
- Cookie só enviado em conexões **HTTPS**
- **Proteção**: Previne man-in-the-middle (MITM)
- Em desenvolvimento (HTTP), pode não funcionar (depende do browser)

#### **`SameSite=Strict`**
- Cookie **não enviado** em requests cross-site
- **Proteção**: Previne CSRF (Cross-Site Request Forgery)
- Exemplo: Site malicioso não consegue fazer request autenticado

**Por que Cookie E Token no Body?**
- **Cookie**: Enviado automaticamente pelo browser (mais conveniente)
- **Token no Body**: Cliente pode guardar em localStorage e usar em `Authorization` header
- **Flexibilidade**: Suporta ambos os métodos

---

### 2. **Location Header**

```kotlin
.header("Location", Uris.Lobby.LIST)
```

- Indica para onde redirecionar após login
- Cliente pode usar ou ignorar (React faz redirect programático)

---

### 3. **Response Body**

```kotlin
.body(
    ApiResponse(
        data = UserSessionOutputModel(
            res.value.userId,
            res.value.username,
            res.value.token,  // Token original (não hash!)
        ),
    ),
)
```

- Token original incluído na resposta
- Cliente precisa dele para guardar em localStorage
- **Nota**: Token nunca é guardado diretamente na BD (só hash)

---

## 📍 PARTE 5: SERVICE LAYER - UserService.kt

### Função `login()`:

```kotlin
fun login(
    username: String,
    password: String,
): UserLoginResult =
    transactionManager.run { tm ->
        val userRepository = tm.usersRepository
        val user = userRepository.getUserByName(username)

        // 1. VERIFICA SE USER EXISTE
        if (user == null) {
            failure(UserLoginError.InvalidCredentials)
        } 
        // 2. VALIDA PASSWORD
        else if (!usersDomain.validatePassword(password, user.password)) {
            failure(UserLoginError.InvalidCredentials)
        } 
        // 3. CRIA NOVA SESSÃO
        else {
            val token: String = usersDomain.generateTokenValue()
            val tokenValidation = usersDomain.createTokenValidationInformation(token)

            val now = clock.now()
            val expiresAt = now + 7 * 24 * 60 * 60 * 1000.toLong().milliseconds

            val session = Session(
                tokenValidation,
                user.userId,
                now,
                now,
                expiresAt.toEpochMilliseconds(),
                false,
            )
            
            // 4. GUARDA SESSÃO
            userRepository.storeSession(session, usersDomain.maxNumberOfTokensPerUser)
            
            // 5. RETORNA SUCESSO
            success(
                CreatedUserSession(
                    session.userId,
                    user.name,
                    token,
                ),
            )
        }
    }
```

**Conceitos Importantes:**

### 1. **Validação de Password**

```kotlin
usersDomain.validatePassword(password, user.password)
```

**O que faz?**
- Compara password em texto plano com hash guardado
- Usa `BCrypt.matches()` (Spring Security)
- **Seguro**: Hash não pode ser revertido, mas pode ser comparado

**Fluxo:**
```
Password inserida: "teste123"
    ↓ BCrypt.matches()
Hash na BD: "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
    ↓ Comparação
Match? true/false
```

**Por que não comparar strings diretamente?**
- Hash inclui salt (aleatório)
- Mesma password gera hash diferente a cada vez
- BCrypt faz comparação segura internamente

---

### 2. **Geração de Novo Token**

```kotlin
val token: String = usersDomain.generateTokenValue()
```

**Por que novo token a cada login?**
- **Segurança**: Se token antigo foi comprometido, novo login invalida-o
- **Rotação**: Tokens antigos expiram naturalmente
- **Rastreabilidade**: Cada sessão tem token único

---

### 3. **Expiração**

```kotlin
val expiresAt = now + 7 * 24 * 60 * 60 * 1000.toLong().milliseconds
```

- Token expira em **7 dias**
- Após expiração, token é inválido mesmo que não revogado
- **Por que?** Limita janela de ataque se token for roubado

---

### 4. **Mensagem de Erro Genérica**

```kotlin
if (user == null) {
    failure(UserLoginError.InvalidCredentials)
} else if (!usersDomain.validatePassword(...)) {
    failure(UserLoginError.InvalidCredentials)  // MESMO erro!
}
```

**Por que não dizer "user não existe" vs "password errada"?**
- **Segurança**: Previne user enumeration
- Atacante não sabe se username existe ou não
- **Trade-off**: UX pior (user não sabe qual campo está errado)

---

## 📍 PARTE 6: DOMAIN LAYER - UserDomain.kt

### Validação de Password:

```kotlin
fun validatePassword(
    password: String,
    validationInfo: PasswordValidationInfo,
) = passwordEncoder.matches(
    password,
    validationInfo.validationInfo,
)
```

**Conceito: BCrypt Matching**

**Como BCrypt funciona:**
1. Hash guardado inclui: `$2a$10$salt$hash`
2. `matches()` extrai salt do hash
3. Faz hash da password com mesmo salt
4. Compara hashes resultantes

**Exemplo:**
```
Hash guardado: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    ↓ Extrai salt: "N9qo8uLOickgx2ZMRZoMye"
    ↓ Hash password com salt
    ↓ Compara
Match!
```

**Por que BCrypt?**
- **Adaptativo**: Pode aumentar custo computacional (fator 10 = 2^10 iterações)
- **Lento**: Protege contra brute force (torna ataques inviáveis)
- **Salt automático**: Cada hash tem salt único

---

## 📍 PARTE 7: REPOSITORY LAYER

### Buscar User por Nome:

```kotlin
override fun getUserByName(name: String): User? =
    handle
        .createQuery("select * from users where name = :name")
        .bind("name", name)
        .mapTo<User>()
        .singleOrNull()
```

**Conceitos:**
- `singleOrNull()`: Retorna `User?` (null se não encontrar)
- JDBI mapeia automaticamente colunas para propriedades do `User`
- **Type-safe**: Compilador garante que retorna `User?`

---

### Guardar Sessão (Revisão):

```kotlin
override fun storeSession(
    session: Session,
    maxSessions: Int,
) {
    // Remove sessões antigas se exceder limite
    // Insere nova sessão
}
```

**Comportamento:**
- Se user já tem `maxSessions` sessões ativas, remove as mais antigas
- **Por que?** Limita número de dispositivos/sessões simultâneas
- **Vantagem**: Se token for roubado, pode fazer login novamente para invalidar sessões antigas

---

## 📍 PARTE 8: AUTENTICAÇÃO EM REQUESTS SUBSEQUENTES

Após login, **todos os requests** precisam validar o token. Como funciona?

---

### Fluxo de Autenticação:

```
1. Cliente envia request com token (Cookie ou Authorization header)
   ↓
2. AuthenticationInterceptor intercepta request
   ↓
3. RequestTokenProcessor extrai e valida token
   ↓
4. UserService.getUserByToken() busca user na BD
   ↓
5. AuthenticatedUserArgumentResolver injeta user no controller
   ↓
6. Controller recebe AuthenticatedUser como parâmetro
```

---

### AuthenticationInterceptor.kt

```kotlin
@Component
class AuthenticationInterceptor(
    private val authorizationHeaderProcessor: RequestTokenProcessor,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        // 1. VERIFICA SE ENDPOINT PRECISA AUTENTICAÇÃO
        if (handler is HandlerMethod &&
            handler.methodParameters.any {
                it.parameterType == AuthenticatedUser::class.java
            }
        ) {
            // 2. TENTA EXTRAIR TOKEN DO HEADER
            val user =
                authorizationHeaderProcessor.processAuthorizationHeaderValue(
                    request.getHeader(NAME_AUTHORIZATION_HEADER),
                    NAME_AUTHORIZATION_HEADER,
                )

            // 3. TENTA EXTRAIR TOKEN DO COOKIE
            val userCookie =
                authorizationHeaderProcessor
                    .processAuthorizationHeaderValue(
                        request.getHeader(NAME_COOKIE_HEADER), 
                        NAME_COOKIE_HEADER
                    )

            // 4. USA QUALQUER UM QUE ESTEJA DISPONÍVEL
            val authUser = user ?: userCookie

            // 5. SE NENHUM TOKEN VÁLIDO, REJEITA REQUEST
            return if (authUser == null) {
                response.status = 401  // Unauthorized
                response.addHeader(NAME_WWW_AUTHENTICATE_HEADER, RequestTokenProcessor.NAME_COOKIE_HEADER)
                false  // Bloqueia request
            } else {
                // 6. ADICIONA USER AO REQUEST (para ArgumentResolver)
                AuthenticatedUserArgumentResolver.addUserTo(authUser, request)
                true  // Permite request continuar
            }
        }

        return true  // Endpoint não precisa autenticação
    }
}
```

**Conceitos Importantes:**

#### 1. **HandlerInterceptor (Spring)**
- Intercepta **todos os requests** antes de chegar ao controller
- `preHandle()` executa **antes** do controller
- Retorna `true` (continua) ou `false` (bloqueia)

#### 2. **Verificação Dinâmica**
```kotlin
handler.methodParameters.any {
    it.parameterType == AuthenticatedUser::class.java
}
```
- **Só valida** se controller tem parâmetro `AuthenticatedUser`
- Endpoints públicos (ex: `/api/login`) não precisam autenticação
- **Vantagem**: Flexível, não precisa anotações em todos os endpoints

#### 3. **Suporte a Múltiplos Métodos**
- Tenta `Authorization` header primeiro
- Se falhar, tenta `Cookie` header
- **Flexibilidade**: Cliente pode usar qualquer método

#### 4. **401 Unauthorized**
- Status HTTP padrão para "não autenticado"
- `WWW-Authenticate` header indica método esperado (opcional)

---

### RequestTokenProcessor.kt

```kotlin
@Component
class RequestTokenProcessor(
    val usersService: UserService,
) {
    fun processAuthorizationHeaderValue(
        authorizationValue: String?,
        headerName: String,
    ): AuthenticatedUser? {
        if (authorizationValue == null) return null

        return if (headerName == NAME_AUTHORIZATION_HEADER) {
            // BEARER TOKEN: "Bearer abc123..."
            val parts = authorizationValue.trim().split(" ", limit = 2)
            if (parts.size != 2 || parts[0].lowercase() != AUTH_SCHEME) return null
            buildUser(parts[1])  // Extrai token após "Bearer "
        } else if (headerName == NAME_COOKIE_HEADER) {
            // COOKIE: "AuthCookie=abc123...; other=value"
            val token =
                authorizationValue
                    .split(";")
                    .map { it.trim() }
                    .firstOrNull {
                        it.startsWith("$AUTH_COOKIE_NAME=", ignoreCase = false)
                    }?.substringAfter("=")
                    ?.takeIf { it.isNotBlank() }

            token?.let { buildUser(it) }
        } else {
            null
        }
    }

    private fun buildUser(token: String): AuthenticatedUser? = 
        usersService.getUserByToken(token)?.let { 
            AuthenticatedUser(it, token) 
        }
}
```

**Conceitos:**

#### 1. **Bearer Token Parsing**
```
Authorization: Bearer abc123...
    ↓ split(" ", limit = 2)
["Bearer", "abc123..."]
    ↓ parts[0].lowercase() == "bearer"
    ↓ parts[1]
Token: "abc123..."
```

#### 2. **Cookie Parsing**
```
Cookie: AuthCookie=abc123...; sessionId=xyz; other=value
    ↓ split(";")
["AuthCookie=abc123...", "sessionId=xyz", "other=value"]
    ↓ firstOrNull { it.startsWith("AuthCookie=") }
"AuthCookie=abc123..."
    ↓ substringAfter("=")
"abc123..."
```

**Por que parsing manual?**
- Cookies podem ter múltiplos valores separados por `;`
- Precisa encontrar o cookie específico (`AuthCookie`)
- Spring tem helpers, mas parsing manual dá mais controlo

#### 3. **`buildUser()`**
- Chama `usersService.getUserByToken(token)`
- Se token válido, retorna `AuthenticatedUser`
- Se inválido, retorna `null`

---

### UserService.getUserByToken()

```kotlin
fun getUserByToken(token: String): User? {
    // 1. VALIDA FORMATO DO TOKEN
    if (!usersDomain.canBeToken(token)) {
        return null
    }
    
    return transactionManager.run { tm ->
        val usersRepository = tm.usersRepository
        val tokenValidationInfo = usersDomain.createTokenValidationInformation(token)
        
        // 2. VERIFICA SE TOKEN ESTÁ EXPIRADO/REVOGADO
        if (usersRepository.isTokenExpired(tokenValidationInfo)) {
            return@run null
        }
        
        // 3. BUSCA USER E SESSÃO
        val userAndSession: Pair<User, Session>? =
            usersRepository.getSessionByTokenValidationInfo(tokenValidationInfo)
        
        userAndSession?.let {
            // 4. VALIDA TEMPO DA SESSÃO
            if (usersDomain.isTokenTimeValid(clock, it.second)) {
                // 5. ATUALIZA lastUsedAt (rolling TTL)
                usersRepository.updateTokenLastUsed(it.second, clock.now())
                return@run it.first
            } else {
                // 6. TOKEN EXPIRADO - REMOVE DA BD
                usersRepository.removeTokenByValidationInfo(tokenValidationInfo, clock.now())
                return@run null
            }
        }
    }
}
```

**Conceitos Importantes:**

#### 1. **Validação de Formato**
```kotlin
usersDomain.canBeToken(token)
```
- Verifica se token tem formato válido (Base64, tamanho correto)
- **Early return**: Falha rápido se formato inválido (evita queries desnecessárias)

#### 2. **Token Validation Info**
```kotlin
usersDomain.createTokenValidationInformation(token)
```
- Faz **hash SHA-256** do token
- Hash é usado para buscar na BD (token original nunca é guardado)

#### 3. **isTokenExpired()**
```kotlin
usersRepository.isTokenExpired(tokenValidationInfo)
```
- Verifica se token está `revoked = true` ou `expires_at < now`
- **Query SQL:**
```sql
SELECT revoked = true OR expires_at < :currentTime 
FROM Sessions 
WHERE session_id = :token
```

#### 4. **Token Time Validation**
```kotlin
usersDomain.isTokenTimeValid(clock, session)
```
- Valida **dois** TTLs:
  - **Token TTL**: Tempo desde criação (ex: 7 dias)
  - **Rolling TTL**: Tempo desde último uso (ex: 1 dia)
- **Rolling TTL**: Se não usar token por 1 dia, expira (mesmo que criado há 2 dias)

**Por que Rolling TTL?**
- Tokens inativos são mais prováveis de serem comprometidos
- Força re-autenticação periódica
- **Segurança**: Limita janela de ataque

#### 5. **Update Last Used**
```kotlin
usersRepository.updateTokenLastUsed(session, clock.now())
```
- Atualiza `last_used_at` a cada request válido
- **Rolling TTL** recomeça a contar
- **Exemplo**: Token criado há 6 dias, usado hoje → válido por mais 1 dia

---

### AuthenticatedUserArgumentResolver.kt

```kotlin
@Component
class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter) = 
        parameter.parameterType == AuthenticatedUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw IllegalStateException("TODO")
        return getUserFrom(request) ?: throw IllegalStateException("TODO")
    }

    companion object {
        private const val KEY = "AuthenticatedUserArgumentResolver"

        fun addUserTo(
            user: AuthenticatedUser,
            request: HttpServletRequest,
        ) = request.setAttribute(KEY, user)

        fun getUserFrom(request: HttpServletRequest): AuthenticatedUser? =
            request.getAttribute(KEY)?.let {
                it as? AuthenticatedUser
            }
    }
}
```

**Conceitos:**

#### 1. **HandlerMethodArgumentResolver (Spring)**
- Resolve parâmetros de métodos de controller automaticamente
- `supportsParameter()`: Indica se este resolver suporta o parâmetro
- `resolveArgument()`: Retorna valor do parâmetro

#### 2. **Request Attributes**
- `request.setAttribute(KEY, user)`: Guarda user no request
- `request.getAttribute(KEY)`: Recupera user do request
- **Por que?** Request é passado entre Interceptor → ArgumentResolver → Controller

#### 3. **Injeção Automática**
```kotlin
@GetMapping("/api/lobbies")
fun getLobbies(authUser: AuthenticatedUser) {  // ← Injetado automaticamente!
    // authUser.user contém User autenticado
    // authUser.token contém token original
}
```

**Vantagens:**
- Controller não precisa extrair token manualmente
- Type-safe (compilador garante que user existe)
- Código mais limpo

---

## 📍 PARTE 9: TRANSACTION MANAGER (Revisão)

### JdbiTransactionManager:

```kotlin
@Component
class JdbiTransactionManager(
    private val jdbi: Jdbi,
) : TransactionManager {
    override fun <R> run(block: (Transaction) -> R): R =
        jdbi.inTransaction<R, Exception> { handle ->
            val transaction = JdbiTransaction(handle)
            block(transaction)
        }
}
```

**Conceito: `inTransaction`**
- JDBI gerencia transação automaticamente
- Se `block` lançar exceção → rollback
- Se `block` retornar normalmente → commit
- **Garante**: Atomicidade automática

---

## 🔄 FLUXO COMPLETO DO LOGIN

```
1. User preenche formulário (Login.tsx)
   ↓
2. dispatch({ type: "post" }) → stage = "posting"
   ↓
3. api.login({ username, password })
   ↓
4. fetchApi("/login", { method: "POST", body: JSON })
   ↓
5. UserController.login() recebe UserLoginInputModel
   ↓
6. UserService.login(username, password)
   ↓
7. TransactionManager.run { ... }
   ↓
8. UsersRepository.getUserByName(username)
   ↓
9. UserDomain.validatePassword(password, user.password)
   ↓
10. BCrypt.matches() compara password com hash
   ↓
11. UserDomain.generateTokenValue() → novo token
   ↓
12. UserDomain.createTokenValidationInformation(token) → hash SHA-256
   ↓
13. UsersRepository.storeSession() → INSERT na BD
   ↓
14. Transaction commit
   ↓
15. UserService retorna Success(CreatedUserSession)
   ↓
16. UserController retorna HTTP 200 + Set-Cookie + JSON body
   ↓
17. Frontend recebe resposta
   ↓
18. AuthContext.login() → localStorage + estado React
   ↓
19. navigate("/lobbies")
```

---

## 🔄 FLUXO DE AUTENTICAÇÃO EM REQUEST SUBSEQUENTE

```
1. Cliente faz request (ex: GET /api/lobbies)
   ↓
2. fetchApi() adiciona Authorization: Bearer token (ou Cookie enviado automaticamente)
   ↓
3. AuthenticationInterceptor.preHandle() intercepta
   ↓
4. Verifica se endpoint precisa autenticação (parâmetro AuthenticatedUser?)
   ↓
5. RequestTokenProcessor.processAuthorizationHeaderValue()
   ↓
6. Extrai token de Authorization header OU Cookie
   ↓
7. UserService.getUserByToken(token)
   ↓
8. UserDomain.canBeToken(token) → valida formato
   ↓
9. UserDomain.createTokenValidationInformation(token) → hash SHA-256
   ↓
10. UsersRepository.isTokenExpired() → verifica revoked/expires_at
   ↓
11. UsersRepository.getSessionByTokenValidationInfo() → busca user
   ↓
12. UserDomain.isTokenTimeValid() → valida TTLs
   ↓
13. UsersRepository.updateTokenLastUsed() → atualiza last_used_at
   ↓
14. AuthenticatedUserArgumentResolver.resolveArgument()
   ↓
15. Retorna AuthenticatedUser do request attribute
   ↓
16. Controller recebe AuthenticatedUser como parâmetro
   ↓
17. Processa request normalmente
```

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **useReducer**: State machine pattern para gestão de estado complexo
2. **Context API**: Estado global partilhado entre componentes
3. **localStorage**: Persistência de dados no browser
4. **HttpOnly Cookies**: Proteção contra XSS
5. **Secure Cookies**: Apenas HTTPS
6. **SameSite Cookies**: Proteção contra CSRF
7. **HandlerInterceptor**: Intercepta requests antes do controller
8. **HandlerMethodArgumentResolver**: Injeção automática de parâmetros
9. **BCrypt Matching**: Comparação segura de passwords
10. **Token Hashing**: SHA-256 para validação (token nunca guardado)
11. **Rolling TTL**: Token expira se inativo por X tempo
12. **Transaction Manager**: Atomicidade de operações

---

## 🔐 SEGURANÇA

- ✅ Passwords comparadas com BCrypt (nunca em texto plano)
- ✅ Tokens validados via hash SHA-256 (token original nunca na BD)
- ✅ HttpOnly cookies (proteção XSS)
- ✅ Secure cookies (apenas HTTPS)
- ✅ SameSite cookies (proteção CSRF)
- ✅ Rolling TTL (tokens inativos expiram)
- ✅ Token revocation (logout invalida token)
- ✅ Mensagens de erro genéricas (previne user enumeration)

---

## 💡 DIFERENÇAS: LOGIN vs REGISTO

| Aspecto | Registo | Login |
|---------|---------|-------|
| **Validação** | Password + Username + Email + Invitation | Username + Password |
| **Criação** | Cria novo user | Usa user existente |
| **Sessão** | Cria sessão | Cria nova sessão |
| **Autenticação** | Não precisa (público) | Não precisa (público) |
| **Resultado** | User criado + sessão | Sessão criada |

**Ambos** criam sessão e retornam token, mas login **valida** credenciais existentes.

---

Fim da explicação do login! 🎉
