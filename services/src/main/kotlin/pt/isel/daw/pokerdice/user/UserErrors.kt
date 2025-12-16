package pt.isel.daw.pokerdice.user

sealed class UserCreationError {
    data object UsernameInvalid : UserCreationError()

    data object UserAlreadyExists : UserCreationError()

    data object InsecurePassword : UserCreationError()

    data object InvalidInvitationCode : UserCreationError()

    data object InvitationRequired : UserCreationError()
}

sealed class ServiceError {
    data object UnknownError : ServiceError()
}

sealed class UserErrors : ServiceError()

sealed class UserGetError {
    data object UserNotFound : UserGetError()
}

sealed class UserLoginError {
    data object InvalidCredentials : UserLoginError()
}
