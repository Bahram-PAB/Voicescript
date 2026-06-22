package com.example.data.repository

import com.example.data.database.AudioNote
import com.example.data.database.AudioNoteDao
import kotlinx.coroutines.flow.Flow

class AudioNoteRepository(private val audioNoteDao: AudioNoteDao) {
    val allAudioNotes: Flow<List<AudioNote>> = audioNoteDao.getAllAudioNotes()

    fun getAudioNoteById(id: Int): Flow<AudioNote?> = audioNoteDao.getAudioNoteById(id)

    suspend fun getAudioNoteByIdSuspend(id: Int): AudioNote? = audioNoteDao.getAudioNoteByIdSuspend(id)

    suspend fun insertAudioNote(audioNote: AudioNote): Long = audioNoteDao.insertAudioNote(audioNote)

    suspend fun updateAudioNote(audioNote: AudioNote) = audioNoteDao.updateAudioNote(audioNote)

    suspend fun deleteAudioNote(audioNote: AudioNote) = audioNoteDao.deleteAudioNote(audioNote)

    suspend fun deleteAudioNoteById(id: Int) = audioNoteDao.deleteAudioNoteById(id)
}
