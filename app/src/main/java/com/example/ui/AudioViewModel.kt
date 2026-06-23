package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.AudioNote
import com.example.data.database.AudioNotePart
import com.example.data.repository.AudioNoteRepository
import com.example.util.AudioSplitter
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.InlineData
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.util.AudioPlayer
import com.example.util.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AudioNoteRepository
    val allNotes: StateFlow<List<AudioNote>>

    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer(application)

    private val prefs = application.getSharedPreferences("audio_summarizer_prefs", Context.MODE_PRIVATE)
    private val _userApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    fun updateApiKey(newKey: String) {
        prefs.edit().putString("gemini_api_key", newKey).apply()
        _userApiKey.value = newKey
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AudioNoteRepository(database.audioNoteDao())
        allNotes = repository.allAudioNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingFile = MutableStateFlow<File?>(null)
    val recordingFile: StateFlow<File?> = _recordingFile.asStateFlow()

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private val _processingStatus = MutableStateFlow<String?>(null)
    val processingStatus: StateFlow<String?> = _processingStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun startRecording() {
        viewModelScope.launch {
            _errorMessage.value = null
            val file = audioRecorder.startRecording()
            if (file != null) {
                _recordingFile.value = file
                _isRecording.value = true
            } else {
                _errorMessage.value = "خطا در شروع ضبط صدا. لطفاً دسترسی میکروفون را بررسی کنید."
            }
        }
    }

    fun stopAndSaveRecording(title: String) {
        viewModelScope.launch {
            _isRecording.value = false
            audioRecorder.stopRecording()
            val file = _recordingFile.value
            if (file != null && file.exists()) {
                val finalTitle = title.ifBlank { "ضبط شده جدید" }
                val note = AudioNote(
                    title = finalTitle,
                    filePath = file.absolutePath,
                    mimeType = "audio/mp4",
                    durationMs = getAudioDuration(file),
                    status = "pending"
                )
                val id = repository.insertAudioNote(note)
                transcribeAndSummarizeNote(id.toInt())
            }
            _recordingFile.value = null
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        audioRecorder.stopRecording()
        _recordingFile.value?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        _recordingFile.value = null
    }

    fun processPickedAudio(context: Context, uri: Uri, title: String) {
        viewModelScope.launch {
            _processingStatus.value = "در حال بارگذاری فایل صوتی..."
            val file = copyUriToCache(context, uri)
            if (file == null || !file.exists()) {
                _errorMessage.value = "بارگذاری فایل صوتی با خطا مواجه شد."
                _processingStatus.value = null
                return@launch
            }

            val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val finalTitle = title.ifBlank { "فایل صوتی وارد شده" }
            val note = AudioNote(
                title = finalTitle,
                filePath = file.absolutePath,
                mimeType = mimeType,
                durationMs = getAudioDuration(file),
                status = "pending"
            )
            val id = repository.insertAudioNote(note)
            transcribeAndSummarizeNote(id.toInt())
            _processingStatus.value = null
        }
    }

    fun resumeProcessing(noteId: Int) {
        transcribeAndSummarizeNote(noteId)
    }

    fun getPartsForNoteFlow(noteId: Int): Flow<List<AudioNotePart>> {
        return repository.getPartsForNote(noteId)
    }

    fun getPartsForNote(noteId: Int): StateFlow<List<AudioNotePart>> {
        return repository.getPartsForNote(noteId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private fun transcribeAndSummarizeNote(noteId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = repository.getAudioNoteByIdSuspend(noteId) ?: return@launch
            try {
                repository.updateAudioNote(note.copy(status = "processing"))

                val file = note.filePath?.let { File(it) }
                if (file == null || !file.exists()) {
                    repository.updateAudioNote(note.copy(status = "failed", transcript = "فایل پیدا نشد"))
                    return@launch
                }

                // Check for API key
                var apiKey = userApiKey.value
                if (apiKey.isBlank()) {
                    apiKey = BuildConfig.GEMINI_API_KEY
                }

                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    repository.updateAudioNote(
                        note.copy(
                            status = "failed",
                            transcript = "کلید API تعریف نشده است. لطفاً کلید Gemini را در دکمه تنظیمات بالای صفحه وارد کنید."
                        )
                    )
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = "کلید API تعریف نشده است. لطفاً آن را تنظیم کنید."
                    }
                    return@launch
                }

                // Get or create parts
                var parts = repository.getPartsForNoteSuspend(noteId)
                if (parts.isEmpty()) {
                    // Split the file using AudioSplitter
                    val splitDir = File(getApplication<Application>().cacheDir, "splits").apply { mkdirs() }
                    // 15 minutes is 15 * 60 * 1000L ms
                    val chunkDurationMs = 15 * 60 * 1000L
                    val splitFiles = AudioSplitter.splitAudio(file, splitDir, chunkDurationMs)
                    
                    if (splitFiles.isEmpty()) {
                        repository.updateAudioNote(note.copy(status = "failed", transcript = "خطا در بخش‌بندی فایل صوتی."))
                        return@launch
                    }

                    val partsToInsert = splitFiles.mapIndexed { index, splitFile ->
                        AudioNotePart(
                            noteId = noteId,
                            partNumber = index + 1,
                            filePath = splitFile.absolutePath,
                            durationMs = getAudioDuration(splitFile),
                            status = "pending"
                        )
                    }
                    repository.insertAudioNoteParts(partsToInsert)
                    parts = repository.getPartsForNoteSuspend(noteId)
                }

                // Update total parts count on note
                repository.updateAudioNote(
                    repository.getAudioNoteByIdSuspend(noteId)!!.copy(
                        status = "processing",
                        totalParts = parts.size
                    )
                )

                // Now loop through pending/failed parts and process them
                for (part in parts) {
                    if (part.status == "success") continue

                    // Mark part as processing
                    repository.updateAudioNotePart(part.copy(status = "processing"))

                    // Calculate current progress before we send API request
                    val currentParts = repository.getPartsForNoteSuspend(noteId)
                    val successCount = currentParts.count { it.status == "success" }
                    val progressPercent = ((successCount.toFloat() / currentParts.size.toFloat()) * 100).toInt()
                    repository.updateAudioNote(
                        repository.getAudioNoteByIdSuspend(noteId)!!.copy(
                            status = "processing",
                            progressPercent = progressPercent
                        )
                    )

                    try {
                        val partFile = File(part.filePath)
                        if (!partFile.exists()) {
                            repository.updateAudioNotePart(part.copy(status = "failed", transcript = "فایل بخش مربوطه پیدا نشد."))
                            continue
                        }

                        val bytes = partFile.readBytes()
                        val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        val promptText = """
                            You are an expert audio dialogue transcriber and summarizer. Your task is to transcribe the provided audio conversation and summarize it.
                            Provide your response EXACTLY in the following format. Do not add any conversational filler before or after.

                            === TRANSCRIPT ===
                            [Transcribe this part of the audio conversation precisely here in its spoken language, maintaining paragraphs and formatting]

                            === SUMMARY ===
                            [Write a highly detailed, professional summary of this part of the conversation in Persian (Farsi), including: key topics, conclusions, and action items discussed in this part]
                        """.trimIndent()

                        val request = GenerateContentRequest(
                            contents = listOf(
                                Content(
                                    parts = listOf(
                                        Part(text = promptText),
                                        Part(inlineData = InlineData(mimeType = note.mimeType, data = base64Audio))
                                    )
                                )
                            )
                        )

                        val response = RetrofitClient.service.generateContent(apiKey, request)
                        val fullResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                        if (fullResponseText != null) {
                            val splitResult = fullResponseText.split("=== SUMMARY ===")
                            var transcriptVal = splitResult.getOrNull(0)?.replace("=== TRANSCRIPT ===", "")?.trim()
                            var summaryVal = splitResult.getOrNull(1)?.trim()

                            if (transcriptVal.isNullOrBlank() || summaryVal.isNullOrBlank()) {
                                transcriptVal = fullResponseText
                                summaryVal = "خلاصه گفتگو در متن گفتگو گنجانده شده است."
                            }

                            repository.updateAudioNotePart(
                                part.copy(
                                    transcript = transcriptVal,
                                    summary = summaryVal,
                                    status = "success"
                                )
                            )
                        } else {
                            repository.updateAudioNotePart(
                                part.copy(
                                    status = "failed",
                                    transcript = "پاسخی از هوش مصنوعی برای این بخش دریافت نشد."
                                )
                            )
                            // Stop processing further parts since we encountered an issue (e.g. key/rate limit)
                            repository.updateAudioNote(
                                repository.getAudioNoteByIdSuspend(noteId)!!.copy(status = "failed")
                            )
                            return@launch
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        repository.updateAudioNotePart(
                            part.copy(
                                status = "failed",
                                transcript = "خطا در پردازش هوش مصنوعی: ${e.localizedMessage}"
                            )
                        )
                        repository.updateAudioNote(
                            repository.getAudioNoteByIdSuspend(noteId)!!.copy(status = "failed")
                        )
                        return@launch
                    }
                }

                // Check final status of all parts
                val finalParts = repository.getPartsForNoteSuspend(noteId)
                val allSuccessful = finalParts.all { it.status == "success" }
                val finishedCount = finalParts.count { it.status == "success" }
                val finalProgressPercent = ((finishedCount.toFloat() / finalParts.size.toFloat()) * 100).toInt()

                if (allSuccessful) {
                    val combinedTranscript = finalParts.joinToString("\n\n") { "--- بخش ${it.partNumber} ---\n${it.transcript}" }
                    val combinedSummary = finalParts.joinToString("\n\n") { "--- خلاصه بخش ${it.partNumber} ---\n${it.summary}" }

                    repository.updateAudioNote(
                        repository.getAudioNoteByIdSuspend(noteId)!!.copy(
                            status = "success",
                            progressPercent = 100,
                            transcript = combinedTranscript,
                            summary = combinedSummary
                        )
                    )
                } else {
                    repository.updateAudioNote(
                        repository.getAudioNoteByIdSuspend(noteId)!!.copy(
                            status = "failed",
                            progressPercent = finalProgressPercent
                        )
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                repository.updateAudioNote(
                    note.copy(
                        status = "failed",
                        transcript = "خطای غیرمنتظره: ${e.localizedMessage}"
                    )
                )
            }
        }
    }

    fun deleteNote(note: AudioNote) {
        viewModelScope.launch {
            note.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            repository.deleteAudioNote(note)
        }
    }

    fun togglePlayAudio(filePath: String) {
        if (audioPlayer.isPlaying(filePath)) {
            _currentlyPlayingPath.value = null
            audioPlayer.stop()
        } else {
            val file = File(filePath)
            if (file.exists()) {
                _currentlyPlayingPath.value = filePath
                audioPlayer.play(file) {
                    _currentlyPlayingPath.value = null
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stopRecording()
    }

    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "audio/mpeg"
            val extension = when {
                mimeType.contains("mp3") -> "mp3"
                mimeType.contains("wav") -> "wav"
                mimeType.contains("m4a") -> "m4a"
                mimeType.contains("mp4") -> "m4a"
                mimeType.contains("ogg") -> "ogg"
                mimeType.contains("3gpp") -> "3gp"
                else -> "m4a"
            }
            val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.$extension")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class AudioViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AudioViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
