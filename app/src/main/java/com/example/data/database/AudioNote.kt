package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_notes")
data class AudioNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String?,
    val mimeType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val transcript: String? = null,
    val summary: String? = null,
    val status: String // "pending", "processing", "success", "failed"
)
