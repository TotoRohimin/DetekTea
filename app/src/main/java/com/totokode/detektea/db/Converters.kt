package com.totokode.detektea.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import com.totokode.detektea.DetectionBox

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromDetectionBoxList(value: List<DetectionBox>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toDetectionBoxList(value: String): List<DetectionBox> {
        val listType = object : TypeToken<List<DetectionBox>>() {}.type
        return Gson().fromJson(value, listType)
    }
}