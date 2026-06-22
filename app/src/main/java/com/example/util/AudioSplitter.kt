package com.example.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object AudioSplitter {
    private const val TAG = "AudioSplitter"

    /**
     * Splits an audio file into chunks of [chunkDurationMs] milliseconds.
     * Returns a list of temporary files for each chunk.
     * If splitting fails, returns the single copied file as a fallback.
     */
    fun splitAudio(sourceFile: File, outputDir: File, chunkDurationMs: Long): List<File> {
        val durationUs = chunkDurationMs * 1000
        val resultFiles = mutableListOf<File>()

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting data source, using fallback copy", e)
            return fallbackSingleFile(sourceFile, outputDir)
        }

        val trackCount = extractor.trackCount
        if (trackCount == 0) {
            extractor.release()
            return fallbackSingleFile(sourceFile, outputDir)
        }

        // Find audio track
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            return fallbackSingleFile(sourceFile, outputDir)
        }

        val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        if (totalDurationUs <= durationUs || totalDurationUs == 0L) {
            extractor.release()
            return fallbackSingleFile(sourceFile, outputDir)
        }

        extractor.selectTrack(audioTrackIndex)

        val bufferSize = 1024 * 1024 // 1MB buffer
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        var chunkIndex = 1
        var startTimeUs = 0L
        val baseFileName = "split_${System.currentTimeMillis()}"

        // We will loop through the file and write to multiple muxers
        try {
            while (startTimeUs < totalDurationUs) {
                val endTimeUs = startTimeUs + durationUs
                val chunkFile = File(outputDir, "${baseFileName}_part_${chunkIndex}.${sourceFile.extension}")
                
                var muxer: MediaMuxer? = null
                var writtenSamples = 0
                try {
                    muxer = MediaMuxer(chunkFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val muxerTrackIndex = muxer.addTrack(format)
                    muxer.start()

                    // Seek to start time
                    extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) {
                            break // EOF
                        }

                        val presentationTimeUs = extractor.sampleTime
                        // If we have advanced past this chunk's end time, stop writing.
                        // (We still allow writing at least a few samples to make sure it's valid).
                        if (presentationTimeUs >= endTimeUs && writtenSamples > 5) {
                            break
                        }

                        bufferInfo.presentationTimeUs = presentationTimeUs - startTimeUs
                        if (bufferInfo.presentationTimeUs < 0) {
                            bufferInfo.presentationTimeUs = 0
                        }
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                        writtenSamples++
                        extractor.advance()
                    }

                    if (writtenSamples > 0) {
                        resultFiles.add(chunkFile)
                    } else {
                        // Clean empty files
                        if (chunkFile.exists()) {
                            chunkFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing chunk $chunkIndex with MediaMuxer", e)
                    // If MediaMuxer fails for MP3/etc, try custom fallback chunking.
                } finally {
                    try {
                        muxer?.stop()
                        muxer?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping muxer for chunk $chunkIndex", e)
                    }
                }

                startTimeUs = endTimeUs
                chunkIndex++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Overall splitting error, using fallback", e)
        } finally {
            extractor.release()
        }

        return if (resultFiles.isEmpty()) {
            fallbackSingleFile(sourceFile, outputDir)
        } else {
            resultFiles
        }
    }

    private fun fallbackSingleFile(sourceFile: File, outputDir: File): List<File> {
        val singleChunkFile = File(outputDir, "part_1_${System.currentTimeMillis()}.${sourceFile.extension}")
        return try {
            sourceFile.copyTo(singleChunkFile, overwrite = true)
            listOf(singleChunkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed fallback file copying", e)
            emptyList()
        }
    }
}
