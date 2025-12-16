package pt.isel.daw.pokerdice

import kotlinx.datetime.Clock
import org.jdbi.v3.core.Jdbi
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import pt.isel.daw.pokerdice.security.AuthenticatedUserArgumentResolver
import pt.isel.daw.pokerdice.security.AuthenticationInterceptor
import kotlin.time.Duration.Companion.hours

@SpringBootApplication
class PokerdiceApplication {
    @Bean
    fun jdbi() =
        Jdbi
            .create(
                PGSimpleDataSource().apply {
                    setURL(Environment.getDbUrl())
                },
            ).configureWithAppRequirements()

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun tokenEncoder() = Sha256TokenEncoder()

    @Bean
    fun clock() = Clock.System

    @Bean
    fun usersDomainConfig() =
        UsersDomainConfig(
            tokenSizeInBytes = 256 / 8,
            tokenTtl = 24.hours,
            tokenRollingTtl = 1.hours,
            maxTokensPerUser = 3,
            minPasswordLength = 4,
            minUsernameLength = 3,
            requireDigits = true,
            requireLowercase = true,
            requireUppercase = true,
            requireInvitation = true,
        )

    @Bean
    fun lobbyDomainConfig() =
        LobbyDomainConfig(
            nameMaxLength = 50,
            minPlayers = 2,
            maxPlayers = 10,
            minRounds = 1,
            maxRounds = 20,
            minTimeoutSeconds = 10,
            maxTimeoutSeconds = 300,
        )

    @Bean
    fun roundDomainConfig() =
        RoundDomainConfig(
            defaultBlindAmount = 1.0,
            maxRollsPerTurn = 3,
            numberOfDice = 5,
            diceFaces = listOf("A", "K", "Q", "J", "10", "9"),
        )

    @Configuration
    class PipelineConfigurer(
        val authenticationInterceptor: AuthenticationInterceptor,
        val authenticatedUserArgumentResolver: AuthenticatedUserArgumentResolver,
    ) : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(authenticationInterceptor)
        }

        override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
            resolvers.add(authenticatedUserArgumentResolver)
        }
    }
}

private val logger = LoggerFactory.getLogger("main")

fun main(args: Array<String>) {
    logger.info("Starting app")
    runApplication<PokerdiceApplication>(*args)
}
