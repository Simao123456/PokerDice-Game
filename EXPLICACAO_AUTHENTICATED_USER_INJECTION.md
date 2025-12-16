# 🔐 EXPLICAÇÃO COMPLETA: AUTHENTICATED USER INJECTION

## 🎯 O Problema que Resolve

### Sem AuthenticatedUser Injection (Código Repetitivo)

**Imagina que tens 20 endpoints que precisam de autenticação. Sem injection, terias que fazer isto em CADA UM:**

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    request: HttpServletRequest,  // ← Precisas do request
): ResponseEntity<*> {
    // 1. EXTRAIR TOKEN DO HEADER
    val authHeader = request.getHeader("Authorization")
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        return ResponseEntity.status(401).build()
    }
    val token = authHeader.substringAfter("Bearer ").trim()
    
    // 2. VALIDAR TOKEN
    val user = userService.getUserByToken(token)
    if (user == null) {
        return ResponseEntity.status(401).build()
    }
    
    // 3. AGORA FINALMENTE Podes usar o user
    val lobby = lobbyService.createLobby(..., user.userId)
    return ResponseEntity.ok(lobby)
}

@GetMapping("/api/lobbies")
fun getLobbies(request: HttpServletRequest): ResponseEntity<*> {
    // REPETE TODO O CÓDIGO ACIMA! 😱
    val authHeader = request.getHeader("Authorization")
    // ... mesmo código repetido
}

@PostMapping("/api/lobbies/{id}/join")
fun joinLobby(
    @PathVariable id: Int,
    request: HttpServletRequest,
): ResponseEntity<*> {
    // REPETE NOVAMENTE! 😱😱
    val authHeader = request.getHeader("Authorization")
    // ... mesmo código repetido
}

// ... e assim por diante em TODOS os 20 endpoints!
```

**Problemas:**
1. ❌ **Código repetitivo** (DRY violation)
2. ❌ **Fácil esquecer** validação em algum endpoint
3. ❌ **Inconsistente** (diferentes desenvolvedores fazem diferente)
4. ❌ **Difícil testar** (precisa mockar `HttpServletRequest`)
5. ❌ **Difícil mudar** (se mudar lógica de auth, precisa mudar 20 lugares)

---

### Com AuthenticatedUser Injection (Código Limpo)

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,  // ← Mágica! User já validado e injetado
): ResponseEntity<*> {
    // User já está disponível, pronto para usar!
    val lobby = lobbyService.createLobby(..., authUser.user.userId)
    return ResponseEntity.ok(lobby)
}

@GetMapping("/api/lobbies")
fun getLobbies(authUser: AuthenticatedUser): ResponseEntity<*> {
    // Mesmo padrão simples em todos os endpoints
    // ...
}

@PostMapping("/api/lobbies/{id}/join")
fun joinLobby(
    @PathVariable id: Int,
    authUser: AuthenticatedUser,
): ResponseEntity<*> {
    // Mesmo padrão simples
    // ...
}
```

**Vantagens:**
1. ✅ **Código limpo** (sem repetição)
2. ✅ **Type-safe** (compilador garante que user existe)
3. ✅ **Consistente** (mesmo padrão em todos os endpoints)
4. ✅ **Fácil testar** (injeta `AuthenticatedUser` diretamente)
5. ✅ **Fácil mudar** (lógica de auth centralizada)

---

## 🔄 Como Funciona Internamente (Passo a Passo)

### Pipeline Completo do Spring Boot

```
1. Request HTTP chega
   ↓
2. DispatcherServlet (Spring)
   ↓
3. HandlerMapping encontra controller + método
   ↓
4. HandlerInterceptor.preHandle() ← AuthenticationInterceptor
   ↓
5. HandlerMethodArgumentResolver.resolveArgument() ← AuthenticatedUserArgumentResolver
   ↓
6. Controller method executa
   ↓
7. HandlerInterceptor.postHandle()
   ↓
8. Response HTTP
```

---

### PASSO 1: Request HTTP Chega

```
POST /api/lobby HTTP/1.1
Host: localhost:8080
Authorization: Bearer n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk=
Content-Type: application/json

{
  "name": "Mesa dos Campeões",
  "maxPlayers": 4,
  ...
}
```

**O que acontece:**
- Browser/Cliente envia request com token no header `Authorization`
- Spring Boot recebe request e cria `HttpServletRequest` object

---

### PASSO 2: DispatcherServlet Encontra Handler

**Spring Boot usa `DispatcherServlet` para:**
1. Analisar URL (`/api/lobby`)
2. Encontrar controller que trata esse endpoint
3. Encontrar método específico (`createLobby`)

**Resultado:**
- Spring sabe que `LobbyController.createLobby()` deve ser chamado
- Spring analisa **parâmetros** do método:
  ```kotlin
  fun createLobby(
      @RequestBody input: LobbyCreateInputModel,  // ← Spring sabe como resolver
      authUser: AuthenticatedUser,                  // ← Spring precisa resolver isto
  )
  ```

---

### PASSO 3: HandlerInterceptor.preHandle() - AuthenticationInterceptor

**Antes de chamar o controller, Spring executa interceptors:**

```kotlin
@Component
class AuthenticationInterceptor(
    private val authorizationHeaderProcessor: RequestTokenProcessor,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,  // ← Informação sobre o método do controller
    ): Boolean {
        // 1. VERIFICA SE ENDPOINT PRECISA AUTENTICAÇÃO
        if (handler is HandlerMethod &&
            handler.methodParameters.any {
                it.parameterType == AuthenticatedUser::class.java
            }
        ) {
            // 2. EXTRAI E VALIDA TOKEN
            val user = authorizationHeaderProcessor.processAuthorizationHeaderValue(
                request.getHeader("Authorization"),
                "Authorization",
            )
            
            val userCookie = authorizationHeaderProcessor.processAuthorizationHeaderValue(
                request.getHeader("Cookie"),
                "Cookie",
            )
            
            val authUser = user ?: userCookie
            
            // 3. SE TOKEN INVÁLIDO, BLOQUEIA REQUEST
            return if (authUser == null) {
                response.status = 401
                false  // ← Bloqueia request, controller NÃO é chamado
            } else {
                // 4. GUARDA USER NO REQUEST (para ArgumentResolver usar depois)
                AuthenticatedUserArgumentResolver.addUserTo(authUser, request)
                true  // ← Permite request continuar
            }
        }
        
        return true  // Endpoint não precisa auth, continua normalmente
    }
}
```

**Conceitos Importantes:**

#### 1. **Verificação Dinâmica**
```kotlin
handler.methodParameters.any {
    it.parameterType == AuthenticatedUser::class.java
}
```

**O que faz:**
- Inspeciona **parâmetros** do método do controller
- Verifica se algum parâmetro é do tipo `AuthenticatedUser`
- **Se sim**: Endpoint precisa autenticação → valida token
- **Se não**: Endpoint público → ignora

**Exemplo:**
```kotlin
// Endpoint público (não tem AuthenticatedUser)
@PostMapping("/api/login")
fun login(input: UserLoginInputModel): ResponseEntity<*> {
    // Interceptor vê que não tem AuthenticatedUser → não valida token
}

// Endpoint protegido (tem AuthenticatedUser)
@PostMapping("/api/lobby")
fun createLobby(
    input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,  // ← Interceptor vê isto → valida token
): ResponseEntity<*> {
}
```

**Vantagem:**
- **Declarativo**: Declaras necessidade de auth apenas adicionando parâmetro
- **Sem anotações**: Não precisa `@RequiresAuth` ou similar
- **Type-safe**: Compilador garante que tipo está correto

---

#### 2. **Request Attributes (Armazenamento Temporário)**

```kotlin
AuthenticatedUserArgumentResolver.addUserTo(authUser, request)
```

**O que faz:**
```kotlin
fun addUserTo(
    user: AuthenticatedUser,
    request: HttpServletRequest,
) = request.setAttribute(KEY, user)
```

**Conceito: Request Attributes**
- `HttpServletRequest` tem um `Map<String, Object>` interno
- Permite guardar dados **durante o request**
- Dados são **apagados** após request terminar
- **Use case**: Passar dados entre Interceptor → ArgumentResolver → Controller

**Fluxo:**
```
Interceptor:
  request.setAttribute("AuthenticatedUserArgumentResolver", authUser)
  
ArgumentResolver (depois):
  val user = request.getAttribute("AuthenticatedUserArgumentResolver")
  
Controller (depois):
  authUser: AuthenticatedUser  // ← Já populado pelo ArgumentResolver
```

**Por que não passar diretamente?**
- Spring Boot tem pipeline fixo: Interceptor → ArgumentResolver → Controller
- Não podes passar parâmetros diretamente entre fases
- Request attributes são o mecanismo padrão para isso

---

#### 3. **Retorno Boolean**

```kotlin
return if (authUser == null) {
    false  // ← Bloqueia request
} else {
    true   // ← Permite request continuar
}
```

**Comportamento:**
- `false`: Request é **bloqueado**, controller **não é chamado**
- `true`: Request **continua**, próximo passo (ArgumentResolver) executa

**O que acontece se retornar `false`:**
- Response já foi enviada (status 401)
- Pipeline para aqui
- Controller nunca é executado
- **Segurança**: Endpoints protegidos são bloqueados se token inválido

---

### PASSO 4: RequestTokenProcessor - Extração e Validação

**Dentro do Interceptor, `RequestTokenProcessor` faz o trabalho pesado:**

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

        return if (headerName == "Authorization") {
            // BEARER TOKEN: "Bearer abc123..."
            val parts = authorizationValue.trim().split(" ", limit = 2)
            if (parts.size != 2 || parts[0].lowercase() != "bearer") return null
            buildUser(parts[1])  // Extrai token após "Bearer "
        } else if (headerName == "Cookie") {
            // COOKIE: "AuthCookie=abc123...; sessionId=xyz"
            val token = authorizationValue
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("AuthCookie=") }
                ?.substringAfter("=")
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

**Fluxo Detalhado:**

#### 1. **Parsing do Header Authorization**
```
Authorization: Bearer n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk=
    ↓ trim()
"Bearer n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk="
    ↓ split(" ", limit = 2)
["Bearer", "n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk="]
    ↓ parts[0].lowercase() == "bearer"
    ↓ parts[1]
Token: "n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk="
```

#### 2. **Parsing do Cookie**
```
Cookie: AuthCookie=abc123...; sessionId=xyz; other=value
    ↓ split(";")
["AuthCookie=abc123...", "sessionId=xyz", "other=value"]
    ↓ firstOrNull { it.startsWith("AuthCookie=") }
"AuthCookie=abc123..."
    ↓ substringAfter("=")
"abc123..."
```

#### 3. **Validação do Token**
```kotlin
usersService.getUserByToken(token)
```

**O que faz (já explicado no login):**
1. Valida formato do token
2. Faz hash SHA-256 do token
3. Busca sessão na BD pelo hash
4. Verifica se token está expirado/revogado
5. Valida TTLs (token TTL + rolling TTL)
6. Atualiza `last_used_at`
7. Retorna `User` se válido, `null` se inválido

#### 4. **Criação do AuthenticatedUser**
```kotlin
AuthenticatedUser(it, token)
```

**Estrutura:**
```kotlin
data class AuthenticatedUser(
    val user: User,      // Dados do utilizador (userId, name, email, etc.)
    val token: String,   // Token original (para logout, etc.)
)
```

**Por que guardar token original?**
- Pode ser necessário para operações (ex: logout precisa do token)
- Não precisa fazer hash novamente

---

### PASSO 5: HandlerMethodArgumentResolver - AuthenticatedUserArgumentResolver

**Após Interceptor validar, Spring precisa "resolver" o parâmetro `authUser`:**

```kotlin
@Component
class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {
    
    // 1. SPRING PERGUNTA: "Consegues resolver este parâmetro?"
    override fun supportsParameter(parameter: MethodParameter): Boolean = 
        parameter.parameterType == AuthenticatedUser::class.java
    
    // 2. SPRING PEDE: "Resolve este parâmetro então"
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw IllegalStateException("TODO")
        
        // 3. RECUPERA USER DO REQUEST ATTRIBUTE (colocado pelo Interceptor)
        return getUserFrom(request) ?: throw IllegalStateException("TODO")
    }

    companion object {
        private const val KEY = "AuthenticatedUserArgumentResolver"

        fun getUserFrom(request: HttpServletRequest): AuthenticatedUser? =
            request.getAttribute(KEY)?.let {
                it as? AuthenticatedUser
            }
    }
}
```

**Conceitos Importantes:**

#### 1. **HandlerMethodArgumentResolver Interface**

**Spring Boot tem sistema de "resolvers" para parâmetros:**
- `@RequestBody` → `RequestResponseBodyMethodProcessor` (resolver padrão)
- `@PathVariable` → `PathVariableMethodArgumentResolver` (resolver padrão)
- `@RequestParam` → `RequestParamMethodArgumentResolver` (resolver padrão)
- `AuthenticatedUser` → `AuthenticatedUserArgumentResolver` (resolver customizado)

**Como Spring decide qual resolver usar:**
1. Para cada parâmetro do método, Spring pergunta a **todos os resolvers**:
   - "Consegues resolver este parâmetro?" (`supportsParameter()`)
2. Primeiro resolver que retornar `true` é usado
3. Spring chama `resolveArgument()` para obter valor

**Exemplo:**
```kotlin
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,  // ← RequestResponseBodyMethodProcessor
    @PathVariable id: Int,                       // ← PathVariableMethodArgumentResolver
    authUser: AuthenticatedUser,                  // ← AuthenticatedUserArgumentResolver
): ResponseEntity<*> {
}
```

---

#### 2. **supportsParameter() - Filtro**

```kotlin
override fun supportsParameter(parameter: MethodParameter): Boolean = 
    parameter.parameterType == AuthenticatedUser::class.java
```

**O que faz:**
- Spring chama isto para **cada parâmetro** de **cada método** de **cada controller**
- Se retornar `true`, Spring usa este resolver para esse parâmetro
- Se retornar `false`, Spring tenta próximo resolver

**Performance:**
- Verificação é rápida (apenas comparação de tipos)
- Spring cacheia resultados

---

#### 3. **resolveArgument() - Resolução Real**

```kotlin
override fun resolveArgument(...): Any? {
    val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
    return getUserFrom(request) ?: throw IllegalStateException("TODO")
}
```

**O que faz:**
1. Obtém `HttpServletRequest` do `NativeWebRequest`
2. Recupera `AuthenticatedUser` do request attribute (colocado pelo Interceptor)
3. Retorna para Spring injetar no parâmetro do controller

**Por que `throw IllegalStateException` se `null`?**
- Se chegou aqui, Interceptor **deveria** ter validado e colocado user
- Se `null`, é bug (Interceptor não funcionou corretamente)
- **Segurança**: Melhor falhar explicitamente que retornar `null` silenciosamente

---

### PASSO 6: Controller Method Executa

**Agora Spring tem todos os parâmetros resolvidos:**

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,  // ← Resolvido por RequestResponseBodyMethodProcessor
    authUser: AuthenticatedUser,                  // ← Resolvido por AuthenticatedUserArgumentResolver
): ResponseEntity<*> {
    // authUser já está populado e pronto para usar!
    val lobby = lobbyService.createLobby(
        input.name,
        input.description,
        input.maxPlayers,
        input.maxRounds,
        input.timeoutSeconds,
        authUser.user.userId,  // ← User já validado e disponível
    )
    return ResponseEntity.ok(lobby)
}
```

**O que acontece:**
- Spring chama método com parâmetros já resolvidos
- `input` vem do JSON do request body
- `authUser` vem do token validado pelo Interceptor
- Controller não precisa fazer nenhuma validação de auth!

---

## 🔧 Configuração no Spring Boot

### Registro dos Componentes

```kotlin
@Configuration
class PipelineConfigurer(
    val authenticationInterceptor: AuthenticationInterceptor,
    val authenticatedUserArgumentResolver: AuthenticatedUserArgumentResolver,
) : WebMvcConfigurer {
    
    // 1. REGISTRA INTERCEPTOR
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
    }
    
    // 2. REGISTRA ARGUMENT RESOLVER
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserArgumentResolver)
    }
}
```

**Conceitos:**

#### 1. **WebMvcConfigurer**
- Interface do Spring Boot para customizar configuração MVC
- Permite adicionar interceptors, resolvers, converters, etc.
- `@Configuration` garante que Spring Boot executa isto na inicialização

#### 2. **InterceptorRegistry**
- Spring Boot mantém lista de interceptors
- Ordem importa (interceptors executam na ordem registrada)
- Neste caso, só há um interceptor (autenticação)

#### 3. **Argument Resolvers List**
- Spring Boot mantém lista de argument resolvers
- Ordem importa (primeiro resolver que suporta é usado)
- Resolvers customizados são adicionados **antes** dos padrão

---

## 🎯 Por Que É Usado em TODOS os Endpoints Autenticados?

### 1. **Consistência**

**Todos os endpoints seguem mesmo padrão:**
```kotlin
// LobbyController
fun createLobby(..., authUser: AuthenticatedUser)
fun getLobbies(authUser: AuthenticatedUser)
fun joinLobby(..., authUser: AuthenticatedUser)

// MatchController
fun getMatch(..., authUser: AuthenticatedUser)
fun rollDice(..., authUser: AuthenticatedUser)

// UserController
fun logout(authUser: AuthenticatedUser)
fun createInvitation(user: AuthenticatedUser)
```

**Vantagem:**
- Qualquer desenvolvedor vê padrão imediatamente
- Fácil entender código
- Fácil adicionar novos endpoints (só adicionar parâmetro)

---

### 2. **Type Safety**

**Compilador garante que:**
- Se método tem `AuthenticatedUser`, user **existe** (não é `null`)
- Não podes esquecer validação (compilador força parâmetro)
- Não podes usar tipo errado (compilador impede)

**Exemplo de Erro Compile-Time:**
```kotlin
fun createLobby(
    input: LobbyCreateInputModel,
    // Esqueceu authUser - compilador não reclama, mas...
): ResponseEntity<*> {
    val userId = ???  // ← Como obter userId? Erro!
}
```

**Com AuthenticatedUser:**
```kotlin
fun createLobby(
    input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,  // ← Compilador força ter isto
): ResponseEntity<*> {
    val userId = authUser.user.userId  // ← Type-safe, sempre existe
}
```

---

### 3. **Testabilidade**

**Testes são mais fáceis:**

```kotlin
// SEM injection (difícil testar)
@Test
fun testCreateLobby() {
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getHeader("Authorization")).thenReturn("Bearer token")
    // ... muito setup complexo
}

// COM injection (fácil testar)
@Test
fun testCreateLobby() {
    val authUser = AuthenticatedUser(
        user = User(userId = 1, name = "Alice", ...),
        token = "token123"
    )
    val result = controller.createLobby(input, authUser)
    // ... simples e direto!
}
```

---

### 4. **Manutenibilidade**

**Se precisares mudar lógica de autenticação:**
- **Sem injection**: Mudas em 20 lugares diferentes
- **Com injection**: Mudas apenas em `AuthenticationInterceptor` e `RequestTokenProcessor`

**Exemplo: Adicionar suporte a API Keys**
```kotlin
// SEM injection: Mudas 20 métodos
// COM injection: Mudas apenas RequestTokenProcessor
fun processAuthorizationHeaderValue(...): AuthenticatedUser? {
    // Adiciona lógica para API keys
    if (header.startsWith("ApiKey ")) {
        return validateApiKey(...)
    }
    // ... resto do código
}
```

---

### 5. **Separação de Responsabilidades**

**Cada componente tem responsabilidade única:**

- **AuthenticationInterceptor**: Valida token e bloqueia requests inválidos
- **RequestTokenProcessor**: Extrai e processa token
- **AuthenticatedUserArgumentResolver**: Injeta user no controller
- **Controller**: Usa user (não se preocupa com validação)

**Vantagem:**
- Código mais organizado
- Fácil testar cada parte isoladamente
- Fácil modificar sem afetar outras partes

---

## 📊 Comparação: Com vs Sem Injection

### Exemplo Real: 3 Endpoints

#### SEM Injection (Código Repetitivo)

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    request: HttpServletRequest,
): ResponseEntity<*> {
    val token = extractToken(request) ?: return ResponseEntity.status(401).build()
    val user = userService.getUserByToken(token) 
        ?: return ResponseEntity.status(401).build()
    return lobbyService.createLobby(..., user.userId)
}

@GetMapping("/api/lobbies")
fun getLobbies(request: HttpServletRequest): ResponseEntity<*> {
    val token = extractToken(request) ?: return ResponseEntity.status(401).build()
    val user = userService.getUserByToken(token) 
        ?: return ResponseEntity.status(401).build()
    return lobbyService.getLobbies()
}

@PostMapping("/api/lobbies/{id}/join")
fun joinLobby(
    @PathVariable id: Int,
    request: HttpServletRequest,
): ResponseEntity<*> {
    val token = extractToken(request) ?: return ResponseEntity.status(401).build()
    val user = userService.getUserByToken(token) 
        ?: return ResponseEntity.status(401).build()
    return lobbyService.joinLobby(id, user.userId)
}

// Função helper (mas ainda é código extra)
private fun extractToken(request: HttpServletRequest): String? {
    val header = request.getHeader("Authorization")
    return header?.substringAfter("Bearer ")?.trim()
}
```

**Problemas:**
- ❌ 15 linhas de código repetido por endpoint
- ❌ Fácil esquecer validação
- ❌ Difícil testar (precisa mockar `HttpServletRequest`)
- ❌ Se mudar lógica de auth, muda em 3 lugares

---

#### COM Injection (Código Limpo)

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,
): ResponseEntity<*> {
    return lobbyService.createLobby(..., authUser.user.userId)
}

@GetMapping("/api/lobbies")
fun getLobbies(authUser: AuthenticatedUser): ResponseEntity<*> {
    return lobbyService.getLobbies()
}

@PostMapping("/api/lobbies/{id}/join")
fun joinLobby(
    @PathVariable id: Int,
    authUser: AuthenticatedUser,
): ResponseEntity<*> {
    return lobbyService.joinLobby(id, authUser.user.userId)
}
```

**Vantagens:**
- ✅ 1 linha por endpoint (apenas parâmetro)
- ✅ Impossível esquecer (compilador força)
- ✅ Fácil testar (injeta `AuthenticatedUser` diretamente)
- ✅ Se mudar lógica de auth, muda apenas em 1 lugar (Interceptor)

**Redução de código:**
- **Sem**: ~60 linhas (3 endpoints × 20 linhas cada)
- **Com**: ~15 linhas (3 endpoints × 5 linhas cada)
- **Economia**: 75% menos código!

---

## 🔍 Fluxo Visual Completo

```
┌─────────────────────────────────────────────────────────────┐
│ 1. REQUEST HTTP CHEGA                                        │
│    POST /api/lobby                                           │
│    Authorization: Bearer token123...                         │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. DISPATCHER SERVLET                                        │
│    Encontra: LobbyController.createLobby()                  │
│    Parâmetros: [LobbyCreateInputModel, AuthenticatedUser]    │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. AUTHENTICATION INTERCEPTOR (preHandle)                   │
│    ✓ Verifica: método tem AuthenticatedUser?                │
│    ✓ Extrai token do header                                 │
│    ✓ Valida token (RequestTokenProcessor)                    │
│    ✓ Se válido: guarda user em request attribute            │
│    ✓ Se inválido: retorna 401, bloqueia request             │
└──────────────────────┬──────────────────────────────────────┘
                       ↓ (se válido)
┌─────────────────────────────────────────────────────────────┐
│ 4. AUTHENTICATED USER ARGUMENT RESOLVER                     │
│    ✓ Spring pergunta: "Consegues resolver AuthenticatedUser?"│
│    ✓ Resolver: "Sim!" (supportsParameter)                   │
│    ✓ Spring: "Resolve então" (resolveArgument)               │
│    ✓ Resolver: recupera user do request attribute           │
│    ✓ Retorna AuthenticatedUser para Spring                   │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. CONTROLLER METHOD EXECUTA                                │
│    createLobby(input, authUser)                             │
│    ✓ input: resolvido do JSON                               │
│    ✓ authUser: resolvido pelo ArgumentResolver              │
│    ✓ Método executa normalmente                             │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. RESPONSE HTTP                                             │
│    HTTP/1.1 201 Created                                     │
│    { "data": { "lobbyId": 42, ... } }                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎓 Resumo dos Conceitos

1. **HandlerInterceptor**: Intercepta requests antes do controller
2. **HandlerMethodArgumentResolver**: Resolve parâmetros do método
3. **Request Attributes**: Armazenamento temporário durante request
4. **Type Safety**: Compilador garante que user existe
5. **DRY Principle**: Don't Repeat Yourself (sem código repetido)
6. **Separation of Concerns**: Cada componente tem responsabilidade única
7. **Declarative Programming**: Declaras necessidade de auth (parâmetro), não como fazer

---

## 💡 Por Que É Melhor Que Alternativas?

### Alternativa 1: Anotações (`@RequiresAuth`)

```kotlin
@RequiresAuth
@PostMapping("/api/lobby")
fun createLobby(...) { }
```

**Problemas:**
- ❌ Ainda precisa extrair user manualmente no método
- ❌ Anotações são runtime (não type-safe)
- ❌ Fácil esquecer de usar anotação

---

### Alternativa 2: Base Controller

```kotlin
abstract class AuthenticatedController {
    protected fun getAuthenticatedUser(request: HttpServletRequest): User {
        // ... código de extração
    }
}
```

**Problemas:**
- ❌ Ainda precisa chamar método em cada endpoint
- ❌ Herança (menos flexível)
- ❌ Fácil esquecer de chamar método

---

### Alternativa 3: AspectJ (AOP)

```kotlin
@Around("@annotation(RequiresAuth)")
fun around(joinPoint: ProceedingJoinPoint) {
    // ... validação
}
```

**Problemas:**
- ❌ Mais complexo (AOP é difícil de entender)
- ❌ Ainda precisa anotações
- ❌ Debugging mais difícil

---

**AuthenticatedUser Injection é melhor porque:**
- ✅ Type-safe (compile-time)
- ✅ Declarativo (apenas parâmetro)
- ✅ Sem código repetido
- ✅ Fácil testar
- ✅ Fácil entender

---

Fim da explicação detalhada! 🎉
