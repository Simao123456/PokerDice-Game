# 📝 EXPLICAÇÃO COMPLETA: FLUXO DE REGISTO

## 🎯 Visão Geral do Fluxo

```
Frontend (React) → API (HTTP Controller) → Service → Domain → Repository → Database
```

---

## 📍 PARTE 1: FRONTEND - Register.tsx

### O que acontece?

O utilizador preenche um formulário com:
- **Nome de utilizador** (`name`)
- **Password** (`password`)
- **Email** (`email`)
- **Código de convite** (`invitationCode`)

### Código Explicado:

```typescript
// Estado do formulário
const [formData, setFormData] = useState({
  name: "",
  password: "",
  email: "",
  invitationCode: "",
});
```

**Conceito: React Hooks - useState**
- `useState` cria estado local no componente
- `formData` guarda os valores dos campos
- `setFormData` atualiza o estado (re-renderiza o componente)

---

```typescript
const handleSubmit = async (e: FormEvent) => {
  e.preventDefault(); // Previne refresh da página
  setError("");
  
  // Validações no frontend (UX - feedback imediato)
  if (!formData.name.trim()) {
    setError("O nome é obrigatório");
    return;
  }
  // ... mais validações
  
  setIsLoading(true);
  
  try {
    await api.register({ ...formData });
    navigate("/login"); // Redireciona para login
  } catch (err) {
    // Tratamento de erro
  }
};
```

**Conceitos:**
- **`e.preventDefault()`**: Impede o comportamento padrão do formulário (não recarrega a página)
- **`async/await`**: Permite código assíncrono sem callbacks
- **`navigate()`**: React Router - muda de página programaticamente

---

## 📍 PARTE 2: CLIENTE API - api.ts

### Função `api.register()`:

```typescript
register(data: RegisterRequest): Promise<ApiResponse<RegisterResponse>> {
  return fetchApi<ApiResponse<RegisterResponse>>("/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}
```

**O que faz:**
1. Chama `fetchApi` com endpoint `/users` e método POST
2. Converte o objeto JavaScript para JSON (`JSON.stringify`)
3. Envia para o servidor

---

### Função `fetchApi()`:

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
    // Trata erros HTTP
    throw new ApiError(response.status, errorMessage);
  }
  
  return response.json();
}
```

**Conceitos:**
- **`fetch()`**: API nativa do browser para HTTP requests
- **`localStorage`**: Armazenamento local no browser (persiste entre sessões)
- **`Bearer Token`**: Padrão de autenticação (Authorization header)
- **`response.ok`**: Verifica se status HTTP é 2xx
- **`response.json()`**: Converte resposta JSON para objeto JavaScript

**Nota**: No registo, ainda não há token (utilizador não está autenticado)

---

## 📍 PARTE 3: HTTP LAYER - UserController.kt

### Endpoint: `POST /api/users`

```kotlin
@PostMapping(Uris.User.CREATE)
fun createUser(
    @RequestBody input: UserCreateInputModel,
): ResponseEntity<*> =
    when (val res = userService.createUser(...)) {
        is Success<CreatedUserSession> ->
            ResponseEntity
                .created(Uris.User.byId(res.value.userId))
                .body(ApiResponse(data = UserSessionOutputModel(...)))
        
        is Failure<UserCreationError> ->
            when (res.value) {
                is UserCreationError.UsernameInvalid ->
                    Problem.response(HttpStatus.BAD_REQUEST, Problem.invalidUserName)
                // ... outros erros
            }
    }
```

**Conceitos:**

#### 1. **`@PostMapping`** (Spring Boot)
- Anotação que mapeia método para endpoint HTTP POST
- Spring Boot faz routing automático baseado na anotação

#### 2. **`@RequestBody`**
- Spring Boot converte automaticamente JSON do request body para objeto Kotlin
- `UserCreateInputModel` é um **DTO (Data Transfer Object)** - modelo só para transferência

#### 3. **Padrão Either**
```kotlin
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()
}
```

**O que é?**
- Tipo funcional que representa **sucesso OU erro**
- `Left` = Erro
- `Right` = Sucesso
- **Vantagem**: Força tratamento explícito de erros (type-safe)

**Exemplo:**
```kotlin
when (val res = userService.createUser(...)) {
    is Success -> // Trata sucesso
    is Failure -> // Trata erro
}
```

#### 4. **`ResponseEntity`**
- Spring Boot - representa resposta HTTP completa
- Permite controlar status code, headers, body
- `.created()` = HTTP 201 (Created)
- `.body()` = corpo da resposta

#### 5. **`Problem` (RFC 7807)**
- Padrão para respostas de erro estruturadas
- Inclui `type`, `title`, `status`, `detail`
- Facilita debugging e tratamento de erros no cliente

---

## 📍 PARTE 4: SERVICE LAYER - UserService.kt

### Função `createUser()`:

```kotlin
fun createUser(
    name: String,
    password: String,
    email: String,
    invitationCode: String? = null,
): UserCreationResult {
    // 1. VALIDAÇÃO DE PASSWORD
    if (!usersDomain.isSafePassword(password)) 
        return failure(UserCreationError.InsecurePassword)
    
    // 2. VALIDAÇÃO DE USERNAME
    if (!usersDomain.isValidUsername(name)) 
        return failure(UserCreationError.UsernameInvalid)
    
    // 3. VERIFICA SE PRECISA DE CONVITE
    if (usersDomain.isInvitationRequired() && invitationCode == null) 
        return failure(UserCreationError.InvitationRequired)
    
    // 4. CRIA HASH DA PASSWORD
    val passwordValidationInfo = usersDomain.createPasswordValidationInformation(password)
    
    // 5. TRANSACTION - Tudo dentro de uma transação
    return transactionManager.run { transaction ->
        val usersRepository = transaction.usersRepository
        val invitationRepository = transaction.invitationRepository
        
        // 6. VALIDA CÓDIGO DE CONVITE
        if (!invitationRepository.isValidInvitationCode(invitationCode!!)) {
            return@run failure(UserCreationError.InvalidInvitationCode)
        }
        
        // 7. VERIFICA SE USERNAME JÁ EXISTE
        if (usersRepository.isUserStoredByName(name)) {
            return@run failure(UserCreationError.UserAlreadyExists)
        }
        
        // 8. GUARDA UTILIZADOR NA BD
        val userId = usersRepository.storeUser(name, passwordValidationInfo, email)
        
        // 9. CONSUME O CÓDIGO DE CONVITE (marca como usado)
        if (usersDomain.isInvitationRequired()) {
            invitationRepository.consumeInvitation(invitationCode)
        }
        
        // 10. GERA TOKEN DE AUTENTICAÇÃO
        val token = usersDomain.generateTokenValue()
        val tokenValidation = usersDomain.createTokenValidationInformation(token)
        
        // 11. CRIA SESSÃO
        val now = clock.now()
        val expiresAt = now + 7 * 24 * 60 * 60 * 1000.toLong().milliseconds
        
        val session = Session(
            tokenValidation,
            userId = userId,
            createdAt = now,
            lastUsedAt = now,
            expiresAt = expiresAt.toEpochMilliseconds(),
            revoked = false,
        )
        
        // 12. GUARDA SESSÃO (e remove sessões antigas se necessário)
        usersRepository.storeSession(session, usersDomain.maxNumberOfTokensPerUser)
        
        // 13. RETORNA SUCESSO
        success(CreatedUserSession(userId, name, token))
    }
}
```

**Conceitos Importantes:**

### 1. **Transaction Manager**
```kotlin
transactionManager.run { transaction ->
    // Todo o código aqui é atómico
    // Se algo falhar, tudo é revertido (rollback)
}
```

**O que é?**
- Garante **atomicidade** (ACID)
- Se qualquer operação falhar, todas são revertidas
- Exemplo: Se guardar user mas falhar ao consumir convite, o user é removido

**Por que é importante?**
- Evita estados inconsistentes
- Exemplo sem transação:
  - User criado ✅
  - Consumir convite falha ❌
  - Resultado: User criado mas convite ainda ativo (inconsistência!)

### 2. **Dependency Injection**
- `usersDomain`, `transactionManager`, `clock` são injetados
- Spring Boot cria e fornece automaticamente
- Facilita testes (podes injetar mocks)

### 3. **Domain Layer**
- `usersDomain` contém **regras de negócio**
- Validações, geração de tokens, etc.
- Separação de responsabilidades

---

## 📍 PARTE 5: DOMAIN LAYER - UserDomain.kt

### Validação de Password:

```kotlin
fun isSafePassword(password: String): Boolean {
    if (password.length < config.minPasswordLength) return false
    if (config.requireDigits && !password.any { it.isDigit() }) return false
    if (config.requireLowercase && !password.any { it.isLowerCase() }) return false
    if (config.requireUppercase && !password.any { it.isUpperCase() }) return false
    return true
}
```

**Conceito: Domain Rules**
- Regras de negócio centralizadas
- Configuráveis via `UsersDomainConfig`
- Fácil de alterar sem tocar em outras camadas

---

### Criação de Hash da Password:

```kotlin
fun createPasswordValidationInformation(password: String) =
    PasswordValidationInfo(
        validationInfo = passwordEncoder.encode(password),
    )
```

**Conceito: Password Hashing**
- **NUNCA** guardar passwords em texto plano
- `passwordEncoder` usa **BCrypt** (algoritmo de hash)
- BCrypt:
  - One-way (não dá para reverter)
  - Inclui salt (proteção contra rainbow tables)
  - Computacionalmente caro (proteção contra brute force)

**Exemplo:**
```
Password: "teste123"
Hash: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

---

### Geração de Token:

```kotlin
fun generateTokenValue(): String =
    ByteArray(config.tokenSizeInBytes).let { byteArray ->
        SecureRandom.getInstanceStrong().nextBytes(byteArray)
        Base64.getUrlEncoder().encodeToString(byteArray)
    }
```

**Conceitos:**

#### 1. **SecureRandom**
- Gerador de números aleatórios criptograficamente seguro
- Não previsível (importante para segurança)

#### 2. **Base64 Encoding**
- Converte bytes para string (URL-safe)
- Permite enviar token em HTTP headers/cookies
- Exemplo: `n3V3do3WCLY3YTmNt5ZotOUzjMlUzh78UHFyOF6D8Rk=`

#### 3. **Token Size**
- Configurável (ex: 32 bytes = 256 bits)
- Maior = mais seguro, mas mais longo

---

### Criação de Token Validation Info:

```kotlin
fun createTokenValidationInformation(token: String): TokenValidationInfo = 
    tokenEncoder.createValidationInformation(token)
```

**O que faz?**
- `Sha256TokenEncoder` faz hash SHA-256 do token
- **Por que?**
  - Token original nunca é guardado na BD
  - Só o hash é guardado
  - Se BD for comprometida, tokens não podem ser usados
  - Similar a passwords (one-way hash)

**Fluxo:**
```
Token original: "abc123..."
    ↓ SHA-256
Hash guardado: "xyz789..."
```

Na validação, faz hash do token recebido e compara com o guardado.

---

## 📍 PARTE 6: REPOSITORY LAYER

### Interface: UsersRepository.kt

```kotlin
interface UsersRepository {
    fun storeUser(
        name: String,
        password: PasswordValidationInfo,
        email: String,
    ): Int  // Retorna userId gerado
    
    fun isUserStoredByName(name: String): Boolean
    
    fun storeSession(
        session: Session,
        maxSessions: Int,
    )
}
```

**Conceito: Repository Pattern**
- Abstração sobre acesso a dados
- Domain não conhece detalhes de implementação (SQL, JDBI, etc.)
- Facilita testes (podes criar mock repository)
- Permite trocar implementação (ex: de PostgreSQL para MongoDB)

---

### Implementação: JdbiUserRepository.kt

```kotlin
override fun storeUser(
    name: String,
    password: PasswordValidationInfo,
    email: String,
): Int =
    handle
        .createUpdate(
            """
            INSERT INTO Users (name, password, email) 
            VALUES (:name, :password, :email)
            """,
        )
        .bind("name", name)
        .bind("password", password.validationInfo)
        .bind("email", email)
        .executeAndReturnGeneratedKeys()
        .mapTo<Int>()
        .one()
```

**Conceitos:**

#### 1. **JDBI**
- Biblioteca Java para acesso a BD
- Mais simples que JDBC puro
- Suporta named parameters (`:name`, `:password`)

#### 2. **SQL Injection Protection**
- `bind()` previne SQL injection
- JDBI escapa automaticamente os valores
- **NUNCA** fazer: `"SELECT * FROM users WHERE name = '$name'"` (vulnerável!)

#### 3. **Generated Keys**
- `executeAndReturnGeneratedKeys()` retorna ID gerado pela BD
- PostgreSQL gera `user_id` automaticamente (SERIAL)

---

### Guardar Sessão:

```kotlin
override fun storeSession(
    session: Session,
    maxSessions: Int,
) {
    // 1. Remove sessões antigas se exceder limite
    val deletions = handle
        .createUpdate(
            """
            UPDATE sessions SET revoked = true
            WHERE user_id = :user_id AND revoked = false
              AND session_id IN (
                SELECT session_id FROM sessions 
                WHERE user_id = :user_id
                ORDER BY last_used_at 
                OFFSET :offset
              )
            """
        )
        .bind("user_id", session.userId)
        .bind("offset", maxSessions - 1)
        .execute()
    
    // 2. Insere nova sessão
    handle
        .createUpdate(
            """
            INSERT INTO Sessions 
            (session_id, user_id, created_at, last_used_at, expires_at, revoked)
            VALUES (:sessionId, :userId, :createdAt, :lastUsedAt, :expiresAt, :revoked)
            """
        )
        .bind("sessionId", session.sessionId.validationInfo)
        // ... outros binds
        .execute()
}
```

**Conceitos:**

#### 1. **Limite de Sessões por User**
- Sistema permite múltiplas sessões (ex: desktop + mobile)
- Mas limita número máximo (ex: 3)
- Remove as mais antigas (menos usadas)

#### 2. **Revoked vs Expired**
- `revoked = true`: Token explicitamente revogado (logout)
- `expires_at < now`: Token expirado por tempo
- Ambos invalidam o token

---

### InvitationRepository:

```kotlin
override fun isValidInvitationCode(code: String): Boolean =
    handle
        .createQuery(
            "SELECT COUNT(*) FROM invitation WHERE code = :code AND is_active = true"
        )
        .bind("code", code)
        .mapTo<Int>()
        .one() > 0

override fun consumeInvitation(code: String) {
    handle
        .createUpdate(
            "UPDATE invitation SET is_active = false WHERE code = :code"
        )
        .bind("code", code)
        .execute()
}
```

**Conceitos:**

#### 1. **Sistema de Convites**
- Controla quem pode registar-se
- Cada código só pode ser usado uma vez
- `is_active = false` marca como usado

#### 2. **Atomicidade**
- Validação e consumo dentro da mesma transação
- Evita race condition (2 users usarem mesmo código)

---

## 📍 PARTE 7: TRANSACTION MANAGER

### Interface:

```kotlin
interface TransactionManager {
    fun <R> run(block: (Transaction) -> R): R
}
```

**O que faz?**
- Garante que todas as operações são atómicas
- Se algo falhar, faz rollback
- Implementação usa JDBI transactions

**Exemplo de uso:**
```kotlin
transactionManager.run { transaction ->
    val repo = transaction.usersRepository
    repo.storeUser(...)  // Se isto falhar
    repo.consumeInvitation(...)  // Isto nunca executa
}  // Tudo ou nada!
```

---

## 📍 PARTE 8: VALUE OBJECTS

### PasswordValidationInfo:

```kotlin
data class PasswordValidationInfo(
    val validationInfo: String,  // Hash da password
)
```

**Conceito: Value Object**
- Não é entidade (não tem ID)
- Representa um valor com significado
- Imutável (data class em Kotlin)
- Encapsula lógica relacionada

**Por que não String direto?**
- Type safety: não confundes password com hash
- Compilador impede erros
- Código mais claro

---

### TokenValidationInfo:

```kotlin
data class TokenValidationInfo(
    val validationInfo: String,  // Hash do token
)
```

Mesmo conceito - hash do token, não o token original.

---

## 📍 PARTE 9: READ MODELS

### CreatedUserSession:

```kotlin
data class CreatedUserSession(
    val userId: Int,
    val username: String,
    val token: String,  // Token original (não hash!)
)
```

**Conceito: Read Model**
- Modelo otimizado para leitura
- Diferente das entidades de domínio
- Contém apenas dados necessários para resposta
- Token original incluído (cliente precisa dele)

---

## 📍 PARTE 10: OUTPUT MODELS

### UserSessionOutputModel:

```kotlin
data class UserSessionOutputModel(
    val userId: Int,
    val username: String,
    val token: String
)
```

**Conceito: DTO (Data Transfer Object)**
- Modelo só para transferência HTTP
- Pode ser diferente do Read Model
- Serializa para JSON automaticamente (Spring Boot)

---

## 🔄 FLUXO COMPLETO RESUMIDO

```
1. User preenche formulário (Register.tsx)
   ↓
2. Frontend valida (UX - feedback rápido)
   ↓
3. api.register() → POST /api/users
   ↓
4. UserController.createUser() recebe JSON
   ↓
5. UserService.createUser() orquestra
   ↓
6. UserDomain valida password/username
   ↓
7. TransactionManager.run { ... }
   ↓
8. InvitationRepository valida código
   ↓
9. UsersRepository verifica se username existe
   ↓
10. UsersRepository.storeUser() → INSERT na BD
   ↓
11. UserDomain gera token
   ↓
12. UserDomain cria hash do token
   ↓
13. UsersRepository.storeSession() → INSERT sessão
   ↓
14. InvitationRepository.consumeInvitation() → UPDATE
   ↓
15. Transaction commit (tudo guardado)
   ↓
16. UserService retorna Success(CreatedUserSession)
   ↓
17. UserController retorna HTTP 201 + JSON
   ↓
18. Frontend recebe resposta
   ↓
19. Navigate para /login
```

---

## 🎓 CONCEITOS-CHAVE RESUMIDOS

1. **Clean Architecture**: Separação em camadas (HTTP → Service → Domain → Repository)
2. **Either Pattern**: Type-safe error handling
3. **Repository Pattern**: Abstração de acesso a dados
4. **Transaction Manager**: Atomicidade (ACID)
5. **Value Objects**: Type safety (PasswordValidationInfo, TokenValidationInfo)
6. **DTOs**: Modelos para transferência (Input/Output)
7. **Read Models**: Modelos otimizados para leitura
8. **Password Hashing**: BCrypt (one-way, com salt)
9. **Token Hashing**: SHA-256 (segurança)
10. **Dependency Injection**: Spring Boot fornece dependências

---

## 🔐 SEGURANÇA

- ✅ Passwords nunca em texto plano (BCrypt)
- ✅ Tokens nunca guardados diretamente (SHA-256 hash)
- ✅ SQL Injection protegido (JDBI bind)
- ✅ Transações garantem consistência
- ✅ Validações em múltiplas camadas

---

Fim da explicação do registo! 🎉
