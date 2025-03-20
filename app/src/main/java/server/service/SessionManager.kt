package server.service

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val userId: Long,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(120 * 60 * 1000L)
)

class SessionManager {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val sessionDuration = 120 * 60 * 1000L // 120 minutes
    private val cleanupInterval = 60 * 1000L

    init {
        Thread {
            while (true) {
                cleanupExpiredSessions()
                Thread.sleep(cleanupInterval)
            }
        }.start()
    }

    fun createSession(userId: Long): String {
        val token = UUID.randomUUID().toString() // Simple unique token
        val session = Session(userId)
        sessions[token] = session
        return token
    }

    fun validateSession(token: String): Long? {
        val session = sessions[token] ?: return null
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token) // Remove expired session
            return null
        }
        return session.userId
    }

    fun invalidateSession(token: String) {
        sessions.remove(token)
    }

    fun extendSession(token: String): Boolean {
        val session = sessions[token] ?: return false
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token)
            return false
        }
        sessions[token] = session.copy(expiresAt = Instant.now().plusSeconds(sessionDuration / 1000))
        return true
    }

    private fun cleanupExpiredSessions() {
        val now = Instant.now()
        sessions.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }
}