package com.totokode.detektea.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "detection_history")
data class DetectionResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val imagePath: String,
    val detectedObjectCount: Int,
    val primaryDetectionName: String,
    val primaryDetectionConfidence: Float,
    val boundingBoxesJson: String, // UBAH: nama properti lebih deskriptif
    val timestamp: Date = Date()
)