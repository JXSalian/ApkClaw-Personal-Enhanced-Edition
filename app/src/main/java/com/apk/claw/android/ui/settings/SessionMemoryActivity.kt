package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.session.SessionMemory
import com.apk.claw.android.session.SessionMemoryManager
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton

class SessionMemoryActivity : BaseActivity() {

    private lateinit var switchEnableSession: SwitchCompat
    private lateinit var switchEnableMemory: SwitchCompat
    private lateinit var spinnerSessions: Spinner
    private lateinit var tvSummary: TextView
    private lateinit var etSelectedSessionName: EditText
    private lateinit var etCondensedSummary: EditText
    private lateinit var etHabitNotes: EditText
    private lateinit var etErrorLessons: EditText
    private lateinit var etRecentTasks: EditText
    private lateinit var etNewSessionName: EditText
    private lateinit var btnCreateSession: KButton
    private lateinit var btnSave: KButton

    private var sessions: List<SessionMemory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_memory)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.session_memory_title))
            showBackButton(true) { finish() }
        }

        switchEnableSession = findViewById(R.id.switchEnableSession)
        switchEnableMemory = findViewById(R.id.switchEnableMemory)
        spinnerSessions = findViewById(R.id.spinnerSessions)
        tvSummary = findViewById(R.id.tvSessionSummary)
        etSelectedSessionName = findViewById(R.id.etSelectedSessionName)
        etCondensedSummary = findViewById(R.id.etCondensedSummary)
        etHabitNotes = findViewById(R.id.etHabitNotes)
        etErrorLessons = findViewById(R.id.etErrorLessons)
        etRecentTasks = findViewById(R.id.etRecentTasks)
        etNewSessionName = findViewById(R.id.etNewSessionName)
        btnCreateSession = findViewById(R.id.btnCreateSession)
        btnSave = findViewById(R.id.btnSaveSessionMemory)

        switchEnableSession.isChecked = SessionMemoryManager.isSessionEnabled()
        switchEnableMemory.isChecked = SessionMemoryManager.isMemoryEnabled()
        switchEnableSession.setOnCheckedChangeListener { _, _ ->
            updateSummaryVisibility()
            updateSummaryText(spinnerSessions.selectedItemPosition)
        }
        switchEnableMemory.setOnCheckedChangeListener { _, _ ->
            updateSummaryVisibility()
            updateSummaryText(spinnerSessions.selectedItemPosition)
        }

        spinnerSessions.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSummaryText(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        btnCreateSession.setOnClickListener {
            val name = etNewSessionName.text.toString().trim()
            val created = SessionMemoryManager.createSession(name)
            loadSessions(created.id)
            etNewSessionName.setText("")
            Toast.makeText(this, getString(R.string.session_memory_created, created.name), Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            val selected = sessions.getOrNull(spinnerSessions.selectedItemPosition)
            if (selected == null) {
                Toast.makeText(this, R.string.session_memory_choose_session, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SessionMemoryManager.setSessionEnabled(switchEnableSession.isChecked)
            SessionMemoryManager.setMemoryEnabled(switchEnableMemory.isChecked)
            SessionMemoryManager.updateSessionContent(
                sessionId = selected.id,
                name = etSelectedSessionName.text.toString().trim(),
                condensedSummary = etCondensedSummary.text.toString().trim(),
                habitNotes = parseLines(etHabitNotes),
                errorLessons = parseLines(etErrorLessons),
                recentTasks = parseLines(etRecentTasks)
            )
            SessionMemoryManager.setCurrentSession(selected.id)
            Toast.makeText(this, R.string.session_memory_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        loadSessions(SessionMemoryManager.getCurrentSessionId())
        updateSummaryVisibility()
    }

    private fun loadSessions(selectedSessionId: String) {
        sessions = SessionMemoryManager.listSessions()
        val titles = sessions.map { "${it.name} (${it.id})" }
        spinnerSessions.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, titles)
        val selectedIndex = sessions.indexOfFirst { it.id == selectedSessionId }.coerceAtLeast(0)
        if (sessions.isNotEmpty()) {
            spinnerSessions.setSelection(selectedIndex)
            updateSummaryText(selectedIndex)
        }
    }

    private fun updateSummaryVisibility() {
        tvSummary.alpha = if (switchEnableSession.isChecked || switchEnableMemory.isChecked) 1f else 0.6f
        etRecentTasks.alpha = if (switchEnableSession.isChecked) 1f else 0.6f
        etCondensedSummary.alpha = if (switchEnableMemory.isChecked) 1f else 0.6f
        etHabitNotes.alpha = if (switchEnableMemory.isChecked) 1f else 0.6f
        etErrorLessons.alpha = if (switchEnableMemory.isChecked) 1f else 0.6f
    }

    private fun updateSummaryText(position: Int) {
        val session = sessions.getOrNull(position)
        etSelectedSessionName.setText(session?.name.orEmpty())
        etRecentTasks.setText(session?.recentTasks?.joinToString("\n").orEmpty())
        etCondensedSummary.setText(session?.condensedSummary.orEmpty())
        etHabitNotes.setText(session?.habitNotes?.joinToString("\n").orEmpty())
        etErrorLessons.setText(session?.errorLessons?.joinToString("\n").orEmpty())
        tvSummary.text = if (session == null) {
            getString(R.string.session_memory_empty_summary)
        } else {
            buildString {
                append(getString(R.string.session_memory_summary_name, session.name)).append("\n")
                append(getString(
                    R.string.session_memory_summary_status,
                    getString(
                        R.string.session_memory_summary_flags,
                        if (switchEnableSession.isChecked) getString(R.string.session_memory_enabled) else getString(R.string.session_memory_disabled),
                        if (switchEnableMemory.isChecked) getString(R.string.session_memory_enabled) else getString(R.string.session_memory_disabled)
                    )
                )).append("\n\n")
                if (switchEnableSession.isChecked && session.recentTasks.isNotEmpty()) {
                    append(getString(R.string.session_memory_recent_tasks)).append("\n")
                    session.recentTasks.take(3).forEach { append("- ").append(it).append("\n") }
                    append("\n")
                }
                if (switchEnableMemory.isChecked) {
                    append(session.condensedSummary.ifBlank { getString(R.string.session_memory_empty_summary) })
                } else if (!switchEnableSession.isChecked) {
                    append(getString(R.string.session_memory_both_disabled))
                }
            }
        }
    }

    private fun parseLines(editText: EditText): List<String> {
        return editText.text.toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}