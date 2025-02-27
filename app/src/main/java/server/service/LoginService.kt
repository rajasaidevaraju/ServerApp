package server.service
import java.util.regex.Pattern
import database.AppDatabase
import org.mindrot.jbcrypt.BCrypt

class LoginService(private val database: AppDatabase) {

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

    fun loginUser(username: String, password: String): Pair<Boolean, String?> {
        val user = database.userDao().getEnabledUserByUsername(username)
            ?: return Pair(false, "User not found or disabled.")

        val hashedPassword = BCrypt.hashpw(password, user.salt)

        return if (hashedPassword == user.passwordHash) {
            Pair(true, null)
        } else {
            Pair(false, "Incorrect password.")
        }
    }

}