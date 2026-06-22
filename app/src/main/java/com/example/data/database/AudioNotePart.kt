package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_note_parts",
    foreignKeys = [
        ForeignKey(
            entity = AudioNote::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AudioNotePart(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noteId: Int,
    val partNumber: Int, // 1, 2, ...
    val filePath: String,
    val durationMs: Long,
    val transcript: String? = null,
    val summary: String? = null,
    val status: String // "pending", "processing", "success", "failed"
)
