package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.DistanceGuardManager
import com.example.data.HapticManager
import com.example.model.AuditLogItem
import com.example.model.FaceDistanceStatus
import com.example.model.GuardianState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val distanceGuardManager = DistanceGuardManager(application)
    val hapticManager = HapticManager(application)

    private val _uiState = MutableStateFlow(GuardianState())
    val uiState: StateFlow<GuardianState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        updateFolderStats()
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        distanceGuardManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            onBound = {
                _uiState.update { it.copy(errorMessage = null) }
            },
            onError = { error ->
                _uiState.update { it.copy(errorMessage = "Camera binding error: ${error.localizedMessage}") }
            }
        )
    }

    fun toggleMonitoring(enabled: Boolean) {
        if (enabled == _uiState.value.isMonitoring) return

        if (enabled) {
            _uiState.update {
                it.copy(
                    isMonitoring = true,
                    countdownSeconds = 10,
                    errorMessage = null
                )
            }
            startTimerLoop()
            // Perform an initial photo check right when protection starts
            performPhotoCheck()
        } else {
            countdownJob?.cancel()
            countdownJob = null
            hapticManager.stopAll()
            _uiState.update {
                it.copy(
                    isMonitoring = false,
                    lastStatus = FaceDistanceStatus.IDLE,
                    showTooClosePopUp = false,
                    countdownSeconds = 10,
                    isAnalyzing = false
                )
            }
        }
    }

    private fun startTimerLoop() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.isMonitoring) {
                for (sec in 10 downTo 1) {
                    if (!_uiState.value.isMonitoring) break
                    _uiState.update { it.copy(countdownSeconds = sec) }
                    delay(1000L)
                }
                if (_uiState.value.isMonitoring) {
                    _uiState.update { it.copy(countdownSeconds = 0) }
                    performPhotoCheck()
                }
            }
        }
    }

    fun performPhotoCheck() {
        if (_uiState.value.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }

            val currentState = _uiState.value
            val result = distanceGuardManager.captureAndAnalyzePhoto(currentState.tooCloseThresholdRatio)

            val folderStats = distanceGuardManager.getFolderStats()

            val newLog = AuditLogItem(
                id = System.currentTimeMillis().toString(),
                timestampMs = System.currentTimeMillis(),
                filename = result.filename,
                status = result.status,
                faceRatioPercent = (result.faceWidthRatio * 100).toInt(),
                deletionConfirmed = (folderStats.first == 0),
                folderSizeBytes = folderStats.second
            )

            val updatedLogs = (listOf(newLog) + currentState.recentLogs).take(25)

            // Show pop-up if face is too close
            val showPopUp = if (result.status == FaceDistanceStatus.TOO_CLOSE) {
                true
            } else if (result.status == FaceDistanceStatus.OPTIMAL || result.status == FaceDistanceStatus.SAFE) {
                false // Automatically dismiss pop-up when user reaches an optimal distance!
            } else {
                currentState.showTooClosePopUp
            }

            // Update haptic vibration
            hapticManager.updateVibrationForDistance(
                status = result.status,
                ratio = result.faceWidthRatio,
                thresholdRatio = currentState.tooCloseThresholdRatio,
                enabled = currentState.hapticsEnabled
            )

            _uiState.update { state ->
                state.copy(
                    lastStatus = result.status,
                    faceWidthRatio = result.faceWidthRatio,
                    showTooClosePopUp = showPopUp,
                    recentLogs = updatedLogs,
                    totalChecksPerformed = state.totalChecksPerformed + 1,
                    tempFolderFileCount = folderStats.first,
                    tempFolderSizeBytes = folderStats.second,
                    isAnalyzing = false,
                    errorMessage = if (result.success) null else result.errorMessage
                )
            }
        }
    }

    fun setThreshold(thresholdRatio: Float) {
        _uiState.update { it.copy(tooCloseThresholdRatio = thresholdRatio) }
    }

    fun toggleHaptics(enabled: Boolean) {
        _uiState.update { it.copy(hapticsEnabled = enabled) }
        if (!enabled) {
            hapticManager.stopAll()
        }
    }

    fun toggleCameraPreview(visible: Boolean) {
        _uiState.update { it.copy(cameraPreviewVisible = visible) }
    }

    fun dismissTooClosePopUp() {
        _uiState.update { it.copy(showTooClosePopUp = false) }
        hapticManager.stopAll()
    }

    fun clearLogs() {
        _uiState.update { it.copy(recentLogs = emptyList()) }
    }

    fun updateFolderStats() {
        val stats = distanceGuardManager.getFolderStats()
        _uiState.update {
            it.copy(
                tempFolderFileCount = stats.first,
                tempFolderSizeBytes = stats.second
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        hapticManager.stopAll()
    }
}
