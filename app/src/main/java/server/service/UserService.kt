package server.service
import java.util.regex.Pattern
import database.AppDatabase
import database.entity.User
import org.mindrot.jbcrypt.BCrypt

data class LoginResult(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null
)


class UserService(private val database: AppDatabase,private val sessionManager: SessionManager) {

    fun checkUsername(username: String?): Pair<Boolean, String?> {
        if (username.isNullOrEmpty()) {
            return Pair(false, "Username cannot be empty.")
        }

        if (containsHtmlTags(username)) {
            return Pair(false, "HTML tags are not permitted in the username.")
        }

        if (!Pattern.compile("^[a-zA-Z]+\$").matcher(username).matches()) {
            return Pair(false, "Username should only contain alphabets.")
        }

        if (username.length > 10) {
            return Pair(false, "Username should not exceed 10 characters.")
        }

        if (username.length < 4) {
            return Pair(false, "Username should be at least 4 characters long.")
        }

        return Pair(true, null)
    }

    fun checkPassword(password: String?): Pair<Boolean, String?> {
        if (password.isNullOrEmpty()) {
            return Pair(false, "Password cannot be empty.")
        }

        if (containsHtmlTags(password)) {
            return Pair(false, "HTML tags are not permitted in the password.")
        }

        if (password.length > 10) {
            return Pair(false, "Password should not exceed 10 characters.")
        }

        if (password.length < 6) {
            return Pair(false, "Password should be at least 6 characters long.")
        }

        return Pair(true, null)
    }

    private fun containsHtmlTags(input: String): Boolean {
        return Pattern.compile("<[^>]*>?").matcher(input).find()
    }

    fun getUserById(userId: Long): User? {
        return database.userDao().getUserById(userId)
    }

    fun loginUser(username: String, password: String): LoginResult{
        val user = database.userDao().getEnabledUserByUsername(username)
            ?: return LoginResult(success = false, message = "User not found or disabled.")

        val hashedPassword = BCrypt.hashpw(password, user.salt)

        return if (hashedPassword == user.passwordHash) {
            val sessionToken=sessionManager.createSession(user.id)
            LoginResult(success = true, token = sessionToken)
        } else {
            LoginResult(success = false, message = "Incorrect password.")
        }
    }

}