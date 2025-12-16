# 🎯 Guia de Exploração do Projeto PokerDice

## 📋 Visão Geral

Este é um projeto de **jogo multiplayer de Poker Dice** com arquitetura distribuída:
- **Backend**: Kotlin + Spring Boot (múltiplas instâncias com load balancing)
- **Frontend**: React + TypeScript
- **Base de Dados**: PostgreSQL
- **Infraestrutura**: Docker Compose com Nginx como load balancer

---

## 🗺️ Caminho de Exploração Recomendado

### **FASE 1: Arquitetura e Infraestrutura** (30 min)
*Entender como tudo se conecta*

1. **Docker & Infraestrutura**
   - `docker-compose.yml` - Topologia dos serviços
   - `docker/Dockerfile-*` - Como cada serviço é construído
   - `docker/nginx.conf` - Configuração do load balancer

2. **Estrutura do Projeto**
   - Módulos Gradle: `domain`, `repository`, `repository-jdbi`, `services`, `http`, `host`
   - Separação de responsabilidades (Clean Architecture)

---

### **FASE 2: Domínio e Entidades** (45 min)
*O coração do negócio - as regras do jogo*

1. **Entidades Principais** (`domain/src/.../entities/`)
   - `User.kt` - Utilizadores do sistema
   - `Lobby.kt` - Salas de espera para jogos
   - `Match.kt` - Partidas em curso
   - `Round.kt` - Rondas dentro de uma partida
   - `Turn.kt` - Turnos de cada jogador
   - `Hand.kt` - Mãos de dados
   - `Roll.kt` - Lançamentos de dados
   - `Session.kt` - Sessões de autenticação
   - `Invitation.kt` - Códigos de convite

2. **Enums** (`domain/src/.../enums/`)
   - `DiceFace.kt` - Faces dos dados (A, K, Q, J, 10, 9)
   - `HandRank.kt` - Rankings das mãos (poker hands)
   - `LobbyStatus.kt` - Estados do lobby
   - `MatchStatus.kt` - Estados da partida
   - `TurnState.kt` - Estados do turno

3. **Lógica de Negócio**
   - `HandEvaluator.kt` - Avaliação de mãos de poker
   - `UserDomain.kt` - Regras de utilizadores
   - `LobbyDomain.kt` - Regras de lobbies
   - `RoundDomainConfig.kt` - Configuração de rondas

4. **Read Models** (`domain/src/.../readmodels/`)
   - Modelos de leitura otimizados para queries
   - `LobbyDetails.kt`, `MatchWithPlayers.kt`, etc.

---

### **FASE 3: Camada de Dados** (30 min)
*Como os dados são persistidos*

1. **Interfaces de Repositório** (`repository/`)
   - `UsersRepository.kt`
   - `LobbyRepository.kt`
   - `MatchRepository.kt`
   - `InvitationRepository.kt`
   - `TransactionManager.kt` - Gestão de transações

2. **Implementação JDBI** (`repository-jdbi/`)
   - `JdbiUserRepository.kt`
   - `JdbiLobbyRepository.kt`
   - `JdbiMatchRepository.kt`
   - Mappers: `InstantMapper.kt`, etc.

3. **Schema da Base de Dados**
   - `docker/db/dev/create-schema.sql`
   - `docker/db/dev/insert-mock-data.sql`

---

### **FASE 4: Camada de Serviços** (45 min)
*Orquestração e coordenação*

1. **UserService** (`services/.../user/`)
   - `UserService.kt` - Criação, login, gestão de tokens
   - `UserErrors.kt` - Tipos de erro

2. **LobbyService** (`services/.../lobby/`)
   - `LobbyService.kt` - Criação, join, leave de lobbies
   - `LobbyErrors.kt`

3. **MatchService** (`services/.../match/`)
   - `MatchService.kt` - Gestão de partidas, rondas, turnos
   - `MatchErrors.kt`

**Padrão Either**: Todos os serviços usam `Either<Error, Success>` para tratamento de erros

---

### **FASE 5: Camada HTTP (API REST)** (45 min)
*Interface externa - endpoints*

1. **Controllers**
   - `UserController.kt` - `/api/users`, `/api/login`, `/api/logout`
   - `LobbyController.kt` - `/api/lobby`, `/api/lobbies`
   - `MatchController.kt` - `/api/matches`

2. **Segurança**
   - `AuthenticationInterceptor.kt` - Validação de tokens
   - `AuthenticatedUserArgumentResolver.kt` - Injeção do user autenticado
   - `RequestTokenProcessor.kt` - Processamento de tokens (cookie/header)

3. **Modelos**
   - `model/input/` - DTOs de entrada
   - `model/output/` - DTOs de saída
   - `Problem.kt` - Respostas de erro (RFC 7807)

4. **API Specification**
   - `docs/api.yaml` - OpenAPI/Swagger completo

---

### **FASE 6: Frontend React** (60 min)
*Interface do utilizador*

1. **Estrutura**
   - `react/src/index.tsx` - Ponto de entrada e routing
   - `react/src/AuthContext.tsx` - Contexto de autenticação
   - `react/src/api.ts` - Cliente HTTP para API

2. **Componentes Principais**
   - `Login.tsx` / `Register.tsx` - Autenticação
   - `Home.tsx` - Página inicial
   - `LobbyList.tsx` - Lista de lobbies
   - `LobbyDetails.tsx` - Detalhes do lobby
   - `CreateLobby.tsx` - Criação de lobby
   - `Match.tsx` - Interface de jogo
   - `RollScreen.tsx` - Tela de lançamento
   - `WaitingScreen.tsx` - Espera por outros jogadores

3. **Hooks e Utils**
   - `useFetch.tsx` - Hook para chamadas API
   - `matchUtils.ts` - Utilitários do jogo

4. **Tipos**
   - `types.ts` - TypeScript types/interfaces

---

### **FASE 7: Fluxo Completo de um Jogo** (30 min)
*Seguir uma partida do início ao fim*

1. **Registo/Login** → `UserService.createUser()` / `UserService.login()`
2. **Criar Lobby** → `LobbyService.createLobby()`
3. **Juntar-se ao Lobby** → `LobbyService.joinLobby()`
4. **Iniciar Match** → Quando lobby atinge minPlayers
5. **Jogar Rondas** → `MatchService` gerencia rounds/turns
6. **Lançar Dados** → `MatchService.rollDice()`
7. **Avaliar Mãos** → `HandEvaluator` calcula rankings
8. **Finalizar Match** → Quando todas as rondas terminam

---

## 🔍 Pontos-Chave para a Discussão

### Arquitetura
- ✅ **Clean Architecture**: Separação clara de camadas
- ✅ **Dependency Inversion**: Interfaces em `repository`, implementação em `repository-jdbi`
- ✅ **Either Pattern**: Tratamento funcional de erros
- ✅ **Read Models**: Otimização de queries complexas

### Segurança
- ✅ Tokens SHA-256 com validação
- ✅ Cookies HttpOnly + Secure
- ✅ BCrypt para passwords
- ✅ Sistema de convites

### Escalabilidade
- ✅ Load balancing com Nginx
- ✅ Múltiplas instâncias backend
- ✅ Base de dados partilhada
- ✅ Stateless authentication

### Regras de Negócio
- ✅ Validação de passwords
- ✅ Limites de jogadores/rondas
- ✅ Timeouts por turno
- ✅ Avaliação de mãos de poker

---

## 📚 Ordem de Leitura Sugerida

1. `README.md` - Visão geral
2. `docs/api.yaml` - Contratos da API
3. `domain/entities/*.kt` - Modelo de dados
4. `domain/HandEvaluator.kt` - Lógica do jogo
5. `services/user/UserService.kt` - Exemplo de serviço
6. `http/UserController.kt` - Exemplo de controller
7. `react/src/components/Login.tsx` - Exemplo de componente
8. `docker-compose.yml` - Infraestrutura

---

## 🎓 Conceitos Importantes

- **Either Pattern**: `Either<Error, Success>` para tratamento de erros
- **Transaction Manager**: Garantia de atomicidade
- **Read Models**: Modelos otimizados para leitura vs. entidades de domínio
- **Value Objects**: `PasswordValidationInfo`, `TokenValidationInfo`
- **Domain Config**: Configuração centralizada de regras

---

## 💡 Dicas para a Discussão

1. **Começa pelo fluxo de um utilizador**: Registo → Login → Criar Lobby → Jogar
2. **Explica a arquitetura em camadas**: Por que separar domain, services, http?
3. **Foca nos padrões**: Either, Repository, Transaction Manager
4. **Menciona escalabilidade**: Load balancing, stateless, shared DB
5. **Destaca segurança**: Tokens, cookies, password hashing

---

Boa sorte com a discussão! 🚀
