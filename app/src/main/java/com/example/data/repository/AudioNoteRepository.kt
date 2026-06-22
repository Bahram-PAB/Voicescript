package com.example.data.repository

import com.example.data.database.AudioNote
import com.example.data.database.AudioNoteDao
import com.example.data.database.AudioNotePart
import kotlinx.coroutines.flow.Flow

class AudioNoteRepository(private val audioNoteDao: AudioNoteDao) {
    val allAudioNotes: Flow<List<AudioNote>> = audioNoteDao.getAllAudioNotes()

    fun getAudioNoteById(id: Int): Flow<AudioNote?> = audioNoteDao.getAudioNoteById(id)

    suspend fun getAudioNoteByIdSuspend(id: Int): AudioNote? = audioNoteDao.getAudioNoteByIdSuspend(id)

    suspend fun insertAudioNote(audioNote: AudioNote): Long = audioNoteDao.insertAudioNote(audioNote)

    suspend fun updateAudioNote(audioNote: AudioNote) = audioNoteDao.updateAudioNote(audioNote)

    suspend fun deleteAudioNote(audioNote: AudioNote) = audioNoteDao.deleteAudioNote(audioNote)

    suspend fun deleteAudioNoteById(id: Int) = audioNoteDao.deleteAudioNoteById(id)

    // --- Audio Note Parts ---

    fun getPartsForNote(noteId: Int): Flow<List<AudioNotePart>> = audioNoteDao.getPartsForNote(noteId)

    suspend fun getPartsForNoteSuspend(noteId: Int): List<AudioNotePart> = audioNoteDao.getPartsForNoteSuspend(noteId)

    suspend fun insertAudioNotePart(part: AudioNotePart): Long = audioNoteDao.insertAudioNotePart(part)

    suspend fun insertAudioNoteParts(parts: List<AudioNotePart>) = audioNoteDao.insertAudioNoteParts(parts)

    suspend fun updateAudioNotePart(part: AudioNotePart) = audioNoteDao.updateAudioNotePart(part)

    suspend fun deletePartsForNote(noteId: Int) = audioNoteDao.deletePartsForNote(noteId)
}
