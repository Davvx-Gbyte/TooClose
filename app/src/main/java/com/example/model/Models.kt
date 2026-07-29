package com.example.model

enum class FaceDistanceStatus {
    IDLE,
    NO_FACE,
    SAFE,
    OPTIMAL,
    TOO_CLOSE
}

data class AuditLogItem(
    val id: String,
    val timestampMs: Long,
    val filename: String,
    val status: FaceDistanceStatus,
    val faceRatioPercent: Int,
    val deletionConfirmed: Boolean,
    val folderSizeBytes: Long
)

data class GuardianState(
    val isMonitoring: Boolean = false,
    val countdownSeconds: Int = 10,
    val lastStatus: FaceDistanceStatus = FaceDistanceStatus.IDLE,
    val faceWidthRatio: Float = 0f,
    val tooCloseThresholdRatio: Float = 0.42f, // 42% frame width threshold
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val cameraPreviewVisible: Boolean = false,
    val showTooClosePopUp: Boolean = false,
    val recentLogs: List<AuditLogItem> = emptyList(),
    val totalChecksPerformed: Int = 0,
    val tempFolderFileCount: Int = 0,
    val tempFolderSizeBytes: Long = 0L,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null
)
