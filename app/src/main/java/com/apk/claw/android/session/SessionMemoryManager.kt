package com.apk.claw.android.session

import com.apk.claw.android.utils.KVUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class SessionMemory(
    val id: String,
    var name: String,
    val createdAt: Long,
    var updatedAt: Long,
    var condensedSummary: String = "",
    val habitNotes: MutableList<String> = mutableListOf(),
    val errorLessons: MutableList<String> = mutableListOf(),
    val recentTasks: MutableList<String> = mutableListOf(),
    val successfulTaskCounts: MutableMap<String, Int> = mutableMapOf()
)

data class SessionMemoryState(
    var sessionEnabled: Boolean = false,
    var memoryEnabled: Boolean = false,
    var currentSessionId: String = "",
    val sessions: MutableList<SessionMemory> = mutableListOf()
)

object SessionMemoryManager {

    private const val KEY_SESSION_MEMORY_STATE = "KEY_SESSION_MEMORY_STATE"
    private const val DEFAULT_SESSION_NAME = "默认会话"
    private const val MAX_HABITS = 5
    private const val MAX_ERRORS = 5
    private const val MAX_RECENT_TASKS = 6

    private val gson = Gson()
    private val stateType = object : TypeToken<SessionMemoryState>() {}.type

    @Synchronized
    fun isSessionEnabled(): Boolean = loadState().sessionEnabled

    @Synchronized
    fun setSessionEnabled(enabled: Boolean) {
        val state = loadState()
        state.sessionEnabled = enabled
        saveState(state)
    }

    @Synchronized
    fun isMemoryEnabled(): Boolean = loadState().memoryEnabled

    @Synchronized
    fun setMemoryEnabled(enabled: Boolean) {
        val state = loadState()
        state.memoryEnabled = enabled
        saveState(state)
    }

    @Synchronized
    fun listSessions(): List<SessionMemory> = loadState().sessions.sortedByDescending { it.updatedAt }

    @Synchronized
    fun getCurrentSession(): SessionMemory? {
        val state = loadState()
        return state.sessions.firstOrNull { it.id == state.currentSessionId }
    }

    @Synchronized
    fun getCurrentSessionId(): String = loadState().currentSessionId

    @Synchronized
    fun setCurrentSession(sessionId: String): Boolean {
        val state = loadState()
        if (state.sessions.none { it.id == sessionId }) return false
        state.currentSessionId = sessionId
        saveState(state)
        return true
    }

    @Synchronized
    fun createSession(name: String): SessionMemory {
        val state = loadState()
        val now = System.currentTimeMillis()
        val session = SessionMemory(
            id = "session-${UUID.randomUUID().toString().substring(0, 8)}",
            name = name.ifBlank { "会话 ${state.sessions.size + 1}" },
            createdAt = now,
            updatedAt = now
        )
        state.sessions.add(0, session)
        state.currentSessionId = session.id
        saveState(state)
        return session
    }

    @Synchronized
    fun renameSession(sessionId: String, name: String): Boolean {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == sessionId } ?: return false
        session.name = name.ifBlank { session.name }
        session.updatedAt = System.currentTimeMillis()
        saveState(state)
        return true
    }

    @Synchronized
    fun updateSessionContent(
        sessionId: String,
        name: String,
        condensedSummary: String,
        habitNotes: List<String>,
        errorLessons: List<String>,
        recentTasks: List<String>
    ): Boolean {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == sessionId } ?: return false
        session.name = name.ifBlank { session.name }
        session.condensedSummary = condensedSummary.trim()
        session.habitNotes.apply {
            clear()
            addAll(habitNotes.take(MAX_HABITS))
        }
        session.errorLessons.apply {
            clear()
            addAll(errorLessons.take(MAX_ERRORS))
        }
        session.recentTasks.apply {
            clear()
            addAll(recentTasks.take(MAX_RECENT_TASKS))
        }
        session.updatedAt = System.currentTimeMillis()
        saveState(state)
        return true
    }

    @Synchronized
    fun buildTaskPrompt(userTask: String): String {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return userTask

        val sections = mutableListOf<String>()
        if (state.sessionEnabled && session.recentTasks.isNotEmpty()) {
            sections += buildString {
                append("- 当前会话近期记录:\n")
                session.recentTasks.take(2).forEach { append("  - ").append(it).append("\n") }
            }.trimEnd()
        }
        if (state.memoryEnabled && session.condensedSummary.isNotBlank()) {
            sections += "- 凝练摘要: ${session.condensedSummary}"
        }
        if (state.memoryEnabled && session.habitNotes.isNotEmpty()) {
            sections += buildString {
                append("- 用户习惯/偏好:\n")
                session.habitNotes.take(3).forEach { append("  - ").append(it).append("\n") }
            }.trimEnd()
        }
        if (state.memoryEnabled && session.errorLessons.isNotEmpty()) {
            sections += buildString {
                append("- 历史错误经验:\n")
                session.errorLessons.take(3).forEach { append("  - ").append(it).append("\n") }
            }.trimEnd()
        }

        if (sections.isEmpty()) return userTask

        return buildString {
            append("## 当前会话上下文\n")
            append("- 会话名: ").append(session.name).append("\n")
            append(sections.joinToString("\n"))
            append("\n- 以上内容仅作为偏好和经验参考；如果与当前用户指令冲突，以当前指令为准。\n\n")
            append("## 当前用户任务\n")
            append(userTask)
        }
    }

    @Synchronized
    fun recordSuccess(userTask: String, finalSummary: String) {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return
        val normalizedTask = normalizeTask(userTask)
        if (state.sessionEnabled) {
            addUniqueLimited(
                session.recentTasks,
                "成功任务：$normalizedTask；结果：${sanitizeLine(finalSummary.ifBlank { "已完成" })}",
                MAX_RECENT_TASKS
            )
        }
        if (state.memoryEnabled) {
            val count = (session.successfulTaskCounts[normalizedTask] ?: 0) + 1
            session.successfulTaskCounts[normalizedTask] = count
            if (count >= 2) {
                addUniqueLimited(session.habitNotes, "用户经常发起类似任务：$normalizedTask", MAX_HABITS)
            }
            session.condensedSummary = buildCondensedSummary(session)
        }
        session.updatedAt = System.currentTimeMillis()
        saveState(state)
    }

    @Synchronized
    fun recordFailure(userTask: String, errorMessage: String) {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return
        val normalizedTask = normalizeTask(userTask)
        if (state.sessionEnabled) {
            addUniqueLimited(
                session.recentTasks,
                "失败任务：$normalizedTask；错误：${sanitizeLine(errorMessage)}",
                MAX_RECENT_TASKS
            )
        }
        if (state.memoryEnabled) {
            addUniqueLimited(
                session.errorLessons,
                "任务“$normalizedTask”曾失败：${sanitizeLine(errorMessage)}",
                MAX_ERRORS
            )
            session.condensedSummary = buildCondensedSummary(session)
        }
        session.updatedAt = System.currentTimeMillis()
        saveState(state)
    }

    @Synchronized
    fun getStatusSummary(): String {
        val state = loadState()
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId }
        val sessionStatus = if (state.sessionEnabled) "会话开" else "会话关"
        val memoryStatus = if (state.memoryEnabled) "记忆开" else "记忆关"
        return "$sessionStatus · $memoryStatus · ${session?.name ?: DEFAULT_SESSION_NAME}"
    }

    private fun loadState(): SessionMemoryState {
        val raw = KVUtils.getString(KEY_SESSION_MEMORY_STATE, "")
        val state = if (raw.isBlank()) {
            SessionMemoryState()
        } else {
            runCatching { gson.fromJson<SessionMemoryState>(raw, stateType) }.getOrDefault(SessionMemoryState())
        }
        return ensureState(state)
    }

    private fun ensureState(state: SessionMemoryState): SessionMemoryState {
        if (state.sessions.isEmpty()) {
            val now = System.currentTimeMillis()
            state.sessions += SessionMemory(
                id = "session-default",
                name = DEFAULT_SESSION_NAME,
                createdAt = now,
                updatedAt = now
            )
        }
        if (state.currentSessionId.isBlank() || state.sessions.none { it.id == state.currentSessionId }) {
            state.currentSessionId = state.sessions.first().id
        }
        return state
    }

    private fun saveState(state: SessionMemoryState) {
        KVUtils.putString(KEY_SESSION_MEMORY_STATE, gson.toJson(state))
    }

    private fun buildCondensedSummary(session: SessionMemory): String {
        val parts = mutableListOf<String>()
        if (session.habitNotes.isNotEmpty()) {
            parts += "习惯: ${session.habitNotes.take(2).joinToString("；")}"
        }
        if (session.errorLessons.isNotEmpty()) {
            parts += "经验: ${session.errorLessons.take(2).joinToString("；")}"
        }
        if (session.recentTasks.isNotEmpty()) {
            parts += "近期: ${session.recentTasks.take(2).joinToString("；")}"
        }
        return parts.joinToString(" | ")
    }

    private fun addUniqueLimited(target: MutableList<String>, value: String, limit: Int) {
        target.remove(value)
        target.add(0, value)
        while (target.size > limit) {
            target.removeAt(target.lastIndex)
        }
    }

    private fun normalizeTask(task: String): String {
        val oneLine = sanitizeLine(task)
        return if (oneLine.length > 80) oneLine.take(80) + "..." else oneLine
    }

    private fun sanitizeLine(text: String): String {
        return text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    }
}