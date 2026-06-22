package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioNoteDao {
    @Query("SELECT * FROM audio_notes ORDER BY timestamp DESC")
    fun getAllAudioNotes(): Flow<List<AudioNote>>

    @Query("SELECT * FROM audio_notes WHERE id = :id")
    fun getAudioNoteById(id: Int): Flow<AudioNote?>

    @Query("SELECT * FROM audio_notes WHERE id = :id")
    suspend fun getAudioNoteByIdSuspend(id: Int): AudioNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioNote(audioNote: AudioNote): Long

    @Update
    suspend fun updateAudioNote(audioNote: AudioNote)

    @Delete
    suspend fun deleteAudioNote(audioNote: AudioNote)

    @Query("DELETE FROM audio_notes WHERE id = :id")
    suspend fun deleteAudioNoteById(id: Int)

    // --- AudioNote Parts Queries ---

    @Query("SELECT * FROM audio_note_parts WHERE noteId = :noteId ORDER BY partNumber ASC")
    fun getPartsForNote(noteId: Int): Flow<List<AudioNotePart>>

    @Query("SELECT * FROM audio_note_parts WHERE noteId = :noteId ORDER BY partNumber ASC")
    suspend fun getPartsForNoteSuspend(noteId: Int): List<AudioNotePart>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioNotePart(part: AudioNotePart): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioNoteParts(parts: List<AudioNotePart>)

    @Update
    suspend fun updateAudioNotePart(part: AudioNotePart)

    @Query("DELETE FROM audio_note_parts WHERE noteId = :noteId")
    suspend fun deletePartsForNote(noteId: Int)
}
