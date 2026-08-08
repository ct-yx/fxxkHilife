package com.freebuds.controller.data

import com.freebuds.controller.core.session.EarbudSession

/**
 * Owns the single active control session slot for one repository.
 *
 * The transport can report a late EOF while a replacement session is being installed. All
 * attach/detach checks are identity-based, so an old callback cannot detach the replacement.
 */
class EarbudSessionRegistry {
    data class ActiveSession(
        val attemptId: String,
        val address: String,
        val session: EarbudSession,
    )

    private val lock = Any()
    private var active: ActiveSession? = null

    fun install(attemptId: String, address: String, session: EarbudSession): Boolean = synchronized(lock) {
        if (active != null) return@synchronized false
        active = ActiveSession(attemptId, address, session)
        true
    }

    fun current(): ActiveSession? = synchronized(lock) { active }

    fun currentSession(): EarbudSession? = synchronized(lock) { active?.session }

    fun isCurrent(session: EarbudSession?): Boolean = synchronized(lock) {
        session != null && active?.session === session
    }

    fun detach(): ActiveSession? = synchronized(lock) {
        active.also { active = null }
    }

    fun detachIfCurrent(session: EarbudSession?): ActiveSession? = synchronized(lock) {
        if (session == null || active?.session !== session) return@synchronized null
        active.also { active = null }
    }
}
