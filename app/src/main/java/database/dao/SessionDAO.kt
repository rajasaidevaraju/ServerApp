package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import database.entity.Session

@Dao
interface SessionDAO {
    @Insert
    fun insert(session: Session)

    @Query("SELECT * FROM sessions WHERE token = :token LIMIT 1")
    fun getByToken(token: String): Session?

    @Query("DELETE FROM sessions WHERE token = :token")
    fun deleteByToken(token: String)

    @Query("DELETE FROM sessions WHERE user_id = :userId")
    fun deleteByUserId(userId: Long)

    @Query("DELETE FROM sessions WHERE expires_at <= :now")
    fun deleteExpired(now: Long)

    @Query("DELETE FROM sessions")
    fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE expires_at > :now")
    fun countActive(now: Long): Int
}
