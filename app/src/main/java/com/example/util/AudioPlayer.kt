package com.example.util

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var onCompletionListener: (() -> Unit)? = null

    fun play(file: File, onComplete: () -> Unit) {
        stop()
        onCompletionListener = onComplete
        currentFilePath = file.absolutePath
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentFilePath = null
            onCompletionListener?.invoke()
            onCompletionListener = null
        }
    }

    fun isPlaying(filePath: String): Boolean {
        return mediaPlayer?.isPlaying == true && currentFilePath == filePath
    }
}
