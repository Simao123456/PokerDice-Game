package pt.isel.daw.pokerdice.model.output

import pt.isel.daw.pokerdice.entities.User

data class UserOutputModel(
    val userId: Int,
    val name: String,
)

fun userToUserOutput(users: List<User>): List<UserOutputModel> =
    users.map { user ->
        UserOutputModel(user.userId, user.name)
    }
