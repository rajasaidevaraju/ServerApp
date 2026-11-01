package server.service

import database.AppDatabase
import database.entity.Session
import java.util.UUID

class SessionManager(private val database: AppDatabase) {
    private val sessionDuration = 120 * 60 * 1000L // 120 minutes, never extended

    fun createSession(userId: Long): String {
        val now = System.currentTimeMillis()
        database.sessionDao().deleteExpired(now)
        val token = UUID.randomUUID().toString() // Simple unique token
        database.sessionDao().insert(Session(token, userId, now, now + sessionDuration))
        return token
    }

    fun validateSession(token: String): Long? {
        val session = database.sessionDao().getByToken(token) ?: return null
        if (session.expiresAt <= System.currentTimeMillis()) {
            database.sessionDao().deleteByToken(token) // Remove expired session
            return null
        }
        return session.userId
    }

    fun invalidateSession(token: String) {
        database.sessionDao().deleteByToken(token)
    }

    fun invalidateUserSessions(userId: Long) {
        database.sessionDao().deleteByUserId(userId)
    }

    fun clearAllSessions(): Int {
        return database.sessionDao().deleteAll()
    }

    fun countActiveSessions(): Int {
        return database.sessionDao().countActive(System.currentTimeMillis())
    }
}
