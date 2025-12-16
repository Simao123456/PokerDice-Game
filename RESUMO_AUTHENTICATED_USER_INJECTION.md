# 📝 RESUMO: AUTHENTICATED USER INJECTION

## 🎯 O Que É?

**Mecanismo do Spring Boot que injeta automaticamente o utilizador autenticado como parâmetro nos métodos dos controllers.**

```kotlin
@PostMapping("/api/lobby")
fun createLobby(
    @RequestBody input: LobbyCreateInputModel,
    authUser: AuthenticatedUser,  // ← Injetado automaticamente!
): ResponseEntity<*> {
    // authUser.user.userId já está disponível
}
```

---

## ❌ Problema que Resolve

**Sem injection:**
- Código repetitivo em cada endpoint (extrair token, validar, buscar user)
- Fácil esquecer validação
- Difícil testar
- Difícil manter (mudanças em 20+ lugares)

**Com injection:**
- ✅ Código limpo (apenas adicionar parâmetro)
- ✅ Type-safe (compilador garante que user existe)
- ✅ Consistente (mesmo padrão em todos os endpoints)
- ✅ Fácil testar (injeta `AuthenticatedUser` diretamente)
- ✅ Fácil manter (lógica centralizada)

---

## 🔄 Como Funciona (3 Componentes)

### 1. **AuthenticationInterceptor**
- **Quando**: Antes do controller executar (`preHandle`)
- **O que faz**:
  - Verifica se método tem parâmetro `AuthenticatedUser`
  - Se sim: extrai e valida token
  - Se token válido: guarda user em `request attribute`
  - Se token inválido: retorna 401, bloqueia request

### 2. **RequestTokenProcessor**
- **O que faz**:
  - Extrai token de `Authorization: Bearer ...` ou `Cookie: AuthCookie=...`
  - Valida token (busca na BD, verifica expiração, TTLs)
  - Retorna `AuthenticatedUser` se válido

### 3. **AuthenticatedUserArgumentResolver**
- **Quando**: Spring precisa resolver parâmetros do método
- **O que faz**:
  - `supportsParameter()`: Diz ao Spring que resolve `AuthenticatedUser`
  - `resolveArgument()`: Recupera user do `request attribute` e retorna

---

## 🔄 Fluxo Completo

```
1. Request HTTP chega com token
   ↓
2. DispatcherServlet encontra controller + método
   ↓
3. AuthenticationInterceptor.preHandle()
   - Verifica se método precisa auth (tem AuthenticatedUser?)
   - Extrai token do header
   - Valida token (RequestTokenProcessor)
   - Se válido: guarda user em request attribute
   - Se inválido: retorna 401, bloqueia
   ↓
4. AuthenticatedUserArgumentResolver.resolveArgument()
   - Recupera user do request attribute
   - Retorna para Spring injetar
   ↓
5. Controller method executa
   - authUser já está populado e pronto para usar
```

---

## 💡 Por Que É Usado em TODOS os Endpoints Autenticados?

1. **Consistência**: Mesmo padrão em todos os endpoints
2. **Type Safety**: Compilador garante que user existe (não é null)
3. **DRY**: Don't Repeat Yourself (sem código repetido)
4. **Manutenibilidade**: Mudanças de auth em 1 lugar apenas
5. **Testabilidade**: Fácil testar (injeta `AuthenticatedUser` diretamente)

---

## 🎓 Conceitos-Chave

- **HandlerInterceptor**: Intercepta requests antes do controller
- **HandlerMethodArgumentResolver**: Resolve parâmetros do método
- **Request Attributes**: Armazenamento temporário durante request (comunicação entre componentes)
- **Declarative**: Declaras necessidade de auth (parâmetro), não como fazer
- **Separation of Concerns**: Cada componente tem responsabilidade única

---

## 📊 Exemplo Prático

### SEM Injection (60 linhas)
```kotlin
@PostMapping("/api/lobby")
fun createLobby(input: LobbyCreateInputModel, request: HttpServletRequest) {
    val token = extractToken(request) ?: return ResponseEntity.status(401).build()
    val user = userService.getUserByToken(token) ?: return ResponseEntity.status(401).build()
    // ... código repetido em cada endpoint
}
```

### COM Injection (5 linhas)
```kotlin
@PostMapping("/api/lobby")
fun createLobby(input: LobbyCreateInputModel, authUser: AuthenticatedUser) {
    // authUser já está disponível!
}
```

**Economia: 75% menos código!**

---

## 🔧 Configuração

```kotlin
@Configuration
class PipelineConfigurer : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
    }
    
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserArgumentResolver)
    }
}
```

---

## ✅ Vantagens vs Alternativas

| Alternativa | Problema |
|-------------|----------|
| Anotações (`@RequiresAuth`) | Ainda precisa extrair user manualmente |
| Base Controller | Ainda precisa chamar método em cada endpoint |
| AspectJ (AOP) | Mais complexo, difícil de entender |

**AuthenticatedUser Injection é melhor porque:**
- Type-safe (compile-time)
- Declarativo (apenas parâmetro)
- Sem código repetido
- Fácil testar

---

## 🎯 Pontos para a Discussão

1. **"Por que não usar anotações?"**
   - Anotações são runtime, não type-safe
   - Ainda precisas extrair user manualmente
   - Fácil esquecer de usar anotação

2. **"Como garante que user existe?"**
   - Interceptor valida ANTES do controller executar
   - Se token inválido, request é bloqueado (401)
   - Se chega ao controller, user já foi validado

3. **"E se esquecer de adicionar parâmetro?"**
   - Endpoint fica público (sem autenticação)
   - Mas isso é intencional (alguns endpoints são públicos)
   - Para proteger: adicionar parâmetro `AuthenticatedUser`

4. **"Como testa?"**
   - Injeta `AuthenticatedUser` diretamente no teste
   - Não precisa mockar `HttpServletRequest`
   - Muito mais simples!

5. **"E se precisar mudar lógica de auth?"**
   - Mudas apenas em `AuthenticationInterceptor` e `RequestTokenProcessor`
   - Todos os endpoints automaticamente usam nova lógica
   - Sem mudar 20+ métodos!

---

## 📝 Estrutura do AuthenticatedUser

```kotlin
data class AuthenticatedUser(
    val user: User,      // Dados do utilizador (userId, name, email, etc.)
    val token: String,   // Token original (para logout, etc.)
)
```

**Por que guardar token?**
- Pode ser necessário para operações (ex: logout precisa do token)

---

## 🔐 Segurança

- ✅ Token validado ANTES do controller executar
- ✅ Se token inválido, request bloqueado (401)
- ✅ User só injetado se token válido
- ✅ Type-safe (não pode ser null no controller)

---

## 💬 Frases-Chave para a Discussão

- "Elimina código repetitivo em todos os endpoints autenticados"
- "Type-safe: compilador garante que user existe"
- "Lógica de autenticação centralizada em 2 componentes"
- "Fácil testar: injeta `AuthenticatedUser` diretamente"
- "Declarativo: apenas adicionar parâmetro, Spring faz o resto"
- "Separação de responsabilidades: Interceptor valida, Resolver injeta, Controller usa"

---

Fim do resumo! 🎉
