package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBuyerLogs {
    private const val MAX_LOGS = 300
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun addLog(message: String) {
        val time = timeFormat.format(Date())
        val formatted = "[$time] $message"
        val current = _logs.value.toMutableList()
        current.add(0, formatted)
        if (current.size > MAX_LOGS) {
            _logs.value = current.subList(0, MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun addLogsBatch(messages: List<String>) {
        if (messages.isEmpty()) return
        val time = timeFormat.format(Date())
        val formatted = messages.map { "[$time] $it" }
        val current = _logs.value.toMutableList()
        current.addAll(0, formatted)
        if (current.size > MAX_LOGS) {
            _logs.value = current.subList(0, MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
