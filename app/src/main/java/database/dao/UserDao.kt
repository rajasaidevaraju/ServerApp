package database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import database.entity.User

@Dao
interface UserDao {
    @Insert
    fun insertUser(user: User): Long // Returns the inserted row ID

    @Query("SELECT * FROM users WHERE user_name = :userName LIMIT 1")
    fun getUserByUsername(userName: String): User?


    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserById(userId: Long): User?

    @Query("UPDATE users SET password_hash = :passwordHash WHERE id = :userId")
    fun updatePassword(userId: Long, passwordHash: String)

    @Query("UPDATE users SET password_hash = :passwordHash, salt = :salt WHERE id = :userId")
    fun updatePasswordWithSalt(userId: Long, passwordHash: String, salt: String?)

    @Query("UPDATE users SET disabled = :disabled WHERE id = :userId")
    fun setDisabled(userId: Long, disabled: Boolean)

    @Query("DELETE FROM users WHERE id = :userId")
    fun deleteUser(userId: Long):Int

    @Query("SELECT * FROM users WHERE user_name = :userName AND disabled = 0 LIMIT 1")
    fun getEnabledUserByUsername(userName: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsers():List<User>
}