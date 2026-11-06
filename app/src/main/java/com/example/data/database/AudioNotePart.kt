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
    val mimeType: String, // The MIME type of this part's file, needed for the API request
    val durationMs: Long,
    // When true the audio is sent by reference (Files API) instead of inline Base64,
    // which allows processing files larger than the ~20MB inline limit (e.g. 1 hour of audio)
    val useFileApi: Boolean = false,
    val transcript: String? = null,
    val summary: String? = null,
    val status: String // "pending", "processing", "success", "failed"
)
