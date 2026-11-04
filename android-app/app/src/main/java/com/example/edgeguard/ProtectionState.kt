package com.example.edgeguard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ProtectionState { Off, On, Alerting }

object EdgeGuardState {
    private val _state = MutableStateFlow(ProtectionState.Off)
    val state: StateFlow<ProtectionState> = _state
    fun set(s: ProtectionState) { _state.value = s }
}