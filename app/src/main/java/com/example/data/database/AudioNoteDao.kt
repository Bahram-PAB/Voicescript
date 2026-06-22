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
}
