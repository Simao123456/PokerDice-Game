package pt.isel.daw.pokerdice.pipeline

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant

@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("HttpLogger")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val start = Instant.now()
        val method = request.method
        val uri = request.requestURI

        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = Duration.between(start, Instant.now()).toMillis()
            val status = response.status
            log.info("[{} {}] -> {} ({}ms)", method, uri, status, duration)
        }
    }
}