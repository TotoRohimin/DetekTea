package com.totokode.detektea.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Insert
    suspend fun insertDetection(detection: DetectionResult)

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<DetectionResult>>
}