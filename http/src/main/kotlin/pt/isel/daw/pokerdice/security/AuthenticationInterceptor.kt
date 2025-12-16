package pt.isel.daw.pokerdice.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val authorizationHeaderProcessor: RequestTokenProcessor,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod &&
            handler.methodParameters.any {
                it.parameterType == AuthenticatedUser::class.java
            }
        ) {
            // enforce authentication
            val user =
                authorizationHeaderProcessor.processAuthorizationHeaderValue(
                    request.getHeader(NAME_AUTHORIZATION_HEADER),
                    NAME_AUTHORIZATION_HEADER,
                )

            val userCookie =
                authorizationHeaderProcessor
                    .processAuthorizationHeaderValue(request.getHeader(NAME_COOKIE_HEADER), NAME_COOKIE_HEADER)

            val authUser = user ?: userCookie

            return if (authUser == null) {
                response.status = 401
                response.addHeader(NAME_WWW_AUTHENTICATE_HEADER, RequestTokenProcessor.NAME_COOKIE_HEADER)
                false
            } else {
                AuthenticatedUserArgumentResolver.addUserTo(authUser, request)
                true
            }
        }

        return true
    }

    companion object {
        const val NAME_AUTHORIZATION_HEADER = "Authorization"
        private const val NAME_WWW_AUTHENTICATE_HEADER = "WWW-Authenticate"
        const val NAME_COOKIE_HEADER = "Cookie"
    }
}
