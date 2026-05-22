package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object VideoCreator {
    private const val TAG = "VideoCreator"

    // Generates video pairing background style with single or sequential verses lyric audio at selected resolution/quality
    suspend fun createQuranVideo(
        context: Context,
        audioUrls: List<String>,
        verseTexts: List<Pair<String, String>>, // list of (Arabic, English) verses
        surahName: String,
        startAyahNum: Int,
        style: com.example.ui.CardStyle,
        outputFile: File,
        width: Int,
        height: Int,
        bitRate: Int,
        frameRate: Int,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempAudioFile = File(context.cacheDir, "temp_quran_combined.mp3")
        
        var encoder: MediaCodec? = null
        var eglDisplay: EGLDisplay? = null
        var eglSurface: EGLSurface? = null
        var eglContext: EGLContext? = null
        var customVideoRetriever: MediaMetadataRetriever? = null
        var muxer: MediaMuxer? = null
        var audioExtractor: MediaExtractor? = null
        var program = 0
        var muxerStarted = false

        try {
            // STEP 1: Process and combine the audio tracks
            onProgress(0.04f)
            val fosSummary = FileOutputStream(tempAudioFile)
            val verseDurationsMs = ArrayList<Long>()
            var totalDurationMs: Long = 0

            for (idx in audioUrls.indices) {
                val url = audioUrls[idx]
                // Persistent cached filename based on recitation URL pattern to prevent redundant file transfers
                val cleanFileName = url
                    .replace("https://everyayah.com/data/", "")
                    .replace("/", "_")
                val cacheFile = File(context.cacheDir, "quran_audio_cache_$cleanFileName")

                if (!cacheFile.exists()) {
                    val downloaded = downloadAudio(url, cacheFile)
                    if (!downloaded) {
                        Log.e(TAG, "Failed to download audio for verse unique file $cleanFileName")
                        try { fosSummary.close() } catch (ex: Exception) {}
                        return@withContext false
                    }
                }

                // Retrieve single verse length
                val retrieverSingle = MediaMetadataRetriever()
                var singleDuration: Long = 8000 // default fallback
                try {
                    retrieverSingle.setDataSource(cacheFile.absolutePath)
                    val durStr = retrieverSingle.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durStr != null) {
                        singleDuration = durStr.toLong()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting verse duration for $idx", e)
                } finally {
                    retrieverSingle.release()
                }

                verseDurationsMs.add(singleDuration)
                totalDurationMs += singleDuration

                // Append bytes to the shared stream
                cacheFile.inputStream().use { input ->
                    input.copyTo(fosSummary)
                }

                val prog = 0.04f + 0.11f * ((idx + 1).toFloat() / audioUrls.size)
                onProgress(prog)
            }
            fosSummary.close()

            // Calculate precise cumulative timelines
            val verseTimelines = ArrayList<Pair<Long, Long>>()
            var currentAccumMs: Long = 0
            for (duration in verseDurationsMs) {
                verseTimelines.add(Pair(currentAccumMs, currentAccumMs + duration))
                currentAccumMs += duration
            }

            fun getVerseIndexAt(timeMs: Long): Int {
                for (idx in verseTimelines.indices) {
                    val timeline = verseTimelines[idx]
                    if (timeMs >= timeline.first && timeMs < timeline.second) {
                        return idx
                    }
                }
                return (verseTimelines.size - 1).coerceAtLeast(0)
            }

            Log.d(TAG, "Spliced video contains ${audioUrls.size} verses, overall duration: $totalDurationMs ms")

            // STEP 2: Pre-render beautiful frosted card overlays for each verse at selected video resolution
            val cardBitmapsCache = ArrayList<Bitmap>()
            for (idx in verseTexts.indices) {
                val versePair = verseTexts[idx]
                val card = com.example.ui.createVerseCardBitmapStatic(
                    context = context,
                    arabicText = versePair.first,
                    englishText = versePair.second,
                    surahName = surahName,
                    ayahNum = startAyahNum + idx,
                    style = style,
                    width = width,
                    height = height,
                    transparentBg = (style.backgroundIndex >= 5) // transparent card background overlays on top of scenic or custom files
                )
                cardBitmapsCache.add(card)
            }

            // STEP 3: Setup video configuration parameters & OpenGL rendering
            val durationSec = totalDurationMs / 1000f
            val totalFrames = (durationSec * frameRate).toInt().coerceAtLeast(12)

            // Prepare external background if needed (scenic presets or custom images)
            var staticBgBitmap: Bitmap? = null
            if (style.backgroundIndex in 5..9) {
                val bgUrl = when (style.backgroundIndex) {
                    5 -> "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=80" // Rawdah
                    6 -> "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80" // Galaxy
                    7 -> "https://images.unsplash.com/photo-1524260855347-148a0455a0b7?w=800&q=80" // Serene fog
                    8 -> "https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&q=80" // Golden abstract
                    else -> null
                }
                if (bgUrl != null) {
                    val bgFile = File(context.cacheDir, "preset_bg_${style.backgroundIndex}.jpg")
                    if (!bgFile.exists()) {
                        downloadAudio(bgUrl, bgFile)
                    }
                    if (bgFile.exists()) {
                        staticBgBitmap = BitmapFactory.decodeFile(bgFile.absolutePath)
                    }
                } else if (style.backgroundIndex == 9 && !style.customBgImageUri.isNullOrBlank()) {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(style.customBgImageUri)).use { stream ->
                            staticBgBitmap = BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading custom img", e)
                    }
                }
            }

            // Setup Custom Background Video Frame Retriever
            if (style.backgroundIndex == 10 && !style.customBgVideoUri.isNullOrBlank()) {
                try {
                    customVideoRetriever = MediaMetadataRetriever().apply {
                        setDataSource(context, Uri.parse(style.customBgVideoUri))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed configuring bg video retriever", e)
                }
            }

            // Setup MediaFormat for video encoder
            val videoMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val format = MediaFormat.createVideoFormat(videoMime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1f)
            }

            encoder = MediaCodec.createEncoderByType(videoMime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // Initialize OpenGL context
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("No display")
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val config = configs[0]

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)

            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            program = compileGLProgram()
            val uTextureLoc = GLES20.glGetUniformLocation(program, "s_texture")
            val aPositionLoc = GLES20.glGetAttribLocation(program, "vPosition")
            val aTexCoordLoc = GLES20.glGetAttribLocation(program, "a_texCoord")

            val vertexBuffer = getFloatBuffer(floatArrayOf(
                -1f,  1f, 0f,
                -1f, -1f, 0f,
                 1f,  1f, 0f,
                 1f, -1f, 0f
            ))

            val texBuffer = getFloatBuffer(floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 0f,
                1f, 1f
            ))

            val tempVideoFile = File(context.cacheDir, "temp_quran_video.mp4")
            muxer = MediaMuxer(tempVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            var currentFrame = 0

            // LOOP: Render each GLES frame and feed to the encoder
            while (currentFrame < totalFrames) {
                val frameTimeMs = (currentFrame * 1000L) / frameRate
                val activeVerseIdx = getVerseIndexAt(frameTimeMs)

                // 1. Get/Merge backdrop for current frame
                val cardOverlay = cardBitmapsCache[activeVerseIdx]
                val mergedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(mergedBitmap)

                // Draw Background
                if (customVideoRetriever != null) {
                    val videoTimeUs = frameTimeMs * 1000L
                    val rawFrame = try {
                        customVideoRetriever.getFrameAtTime(videoTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        null
                    }
                    if (rawFrame != null) {
                        val cropped = scaleCenterCrop(rawFrame, width, height)
                        canvas.drawBitmap(cropped, 0f, 0f, null)
                        cropped.recycle()
                    } else {
                        canvas.drawBitmap(cardOverlay, 0f, 0f, null)
                    }
                } else if (staticBgBitmap != null) {
                    val cropped = scaleCenterCrop(staticBgBitmap!!, width, height)
                    canvas.drawBitmap(cropped, 0f, 0f, null)
                    cropped.recycle()
                } else {
                    canvas.drawBitmap(cardOverlay, 0f, 0f, null)
                }

                // If backdrop image/video was drawn, overlay the transparente text card on top!
                if (customVideoRetriever != null || staticBgBitmap != null) {
                    canvas.drawBitmap(cardOverlay, 0f, 0f, null)
                }

                // Load compiled Frame texture to OpenGL
                val textureId = loadTexture(mergedBitmap)
                mergedBitmap.recycle()

                // OpenGL Drawing instructions
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                GLES20.glUseProgram(program)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glUniform1i(uTextureLoc, 0)

                GLES20.glEnableVertexAttribArray(aPositionLoc)
                GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

                GLES20.glEnableVertexAttribArray(aTexCoordLoc)
                GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Swap presentation timestamp & buffer
                val ptsNs = currentFrame * (1000000000L / frameRate)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                // Clean-up dynamic frame texture fast
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)

                // Drain Encoder output
                var encoderDone = false
                while (!encoderDone) {
                    val status = encoder.dequeueOutputBuffer(bufferInfo, 2500)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderDone = true
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        if (videoTrackIndex >= 0 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (status >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(status)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(status, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }

                currentFrame++
                val progressPercent = 0.15f + (0.70f * (currentFrame.toFloat() / totalFrames))
                onProgress(progressPercent)
            }

            // Flush remaining stream frames
            encoder.signalEndOfInputStream()
            var fullyDrained = false
            while (!fullyDrained) {
                val status = encoder.dequeueOutputBuffer(bufferInfo, 5000)
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    fullyDrained = true
                } else if (status >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(status)!!
                    if (bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        fullyDrained = true
                    }
                } else {
                    fullyDrained = true
                }
            }

            // Cleanup OpenGL and Encoder resources safely
            GLES20.glDeleteProgram(program)
            program = 0
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = null
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = null
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = null

            encoder.stop()
            encoder.release()
            encoder = null
            customVideoRetriever?.release()
            customVideoRetriever = null

            // STEP 4: Merge the Spliced full-length continuous MP3 Audio Track
            onProgress(0.90f)
            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(tempAudioFile.absolutePath)
            var audioTrackIndex = -1
            var audioSourceTrack = -1
            for (i in 0 until audioExtractor.trackCount) {
                val trackFormat = audioExtractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioSourceTrack = i
                    audioTrackIndex = muxer.addTrack(trackFormat)
                    break
                }
            }

            if (audioSourceTrack >= 0 && audioTrackIndex >= 0) {
                audioExtractor.selectTrack(audioSourceTrack)
                val maxBufferSize = 64 * 1024
                val audioBuffer = ByteBuffer.allocate(maxBufferSize)
                val audioBufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
                    if (audioBufferInfo.size < 0) {
                        break
                    }
                    audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    audioBufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo)
                    audioExtractor.advance()
                }
            } else {
                Log.w(TAG, "No suitable audio found inside generated track MP3!")
            }

            audioExtractor.release()
            audioExtractor = null

            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            muxer = null

            // STEP 5: Save exported clip and flush cache files
            if (tempVideoFile.exists()) {
                tempVideoFile.copyTo(outputFile, overwrite = true)
                tempVideoFile.delete()
                tempAudioFile.delete()
                onProgress(1.0f)
                return@withContext true
            }

            tempAudioFile.delete()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failure exporting merged video file", e)
            tempAudioFile.delete()
            return@withContext false
        } finally {
            // SECURE RESOURCE DISPOSAL FINALLY BLOCK TO PREVENT INTERMITTENT DEVICE/CODEC OUT-OF-MEMORY CRASHES
            try {
                if (program != 0) {
                    GLES20.glDeleteProgram(program)
                }
            } catch (e: Exception) {}

            try {
                if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    }
                    if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (e: Exception) {}

            try {
                encoder?.let {
                    it.stop()
                    it.release()
                }
            } catch (e: Exception) {}

            try {
                customVideoRetriever?.release()
            } catch (e: Exception) {}

            try {
                audioExtractor?.release()
            } catch (e: Exception) {}

            try {
                muxer?.let {
                    if (muxerStarted) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (e: Exception) {}
        }
    }

    private fun downloadAudio(urlStr: String, destinationFile: File): Boolean {
        var attempts = 3
        while (attempts > 0) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection().apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                connection.connect()
                val input = BufferedInputStream(connection.getInputStream())
                val output = FileOutputStream(destinationFile)
                val data = ByteArray(4096)
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    output.write(data, 0, count)
                }
                output.flush()
                output.close()
                input.close()
                return true
            } catch (e: Exception) {
                attempts--
                Log.e(TAG, "Audio transfer failed for url: $urlStr (Attempts left: $attempts)", e)
                if (attempts > 0) {
                    try { Thread.sleep(1200) } catch (te: Exception) {}
                }
            }
        }
        return false
    }

    private fun scaleCenterCrop(source: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val sourceWidth = source.width
        val sourceHeight = source.height

        val xScale = newWidth.toFloat() / sourceWidth
        val yScale = newHeight.toFloat() / sourceHeight
        val scale = Math.max(xScale, yScale)

        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight

        val left = (newWidth - scaledWidth) / 2f
        val top = (newHeight - scaledHeight) / 2f

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(source, null, RectF(left, top, left + scaledWidth, top + scaledHeight), paint)
        return dest
    }

    private fun compileGLProgram(): Int {
        val vertexShaderSource = """
            attribute vec4 vPosition;
            attribute vec2 a_texCoord;
            varying vec2 v_texCoord;
            void main() {
                gl_Position = vPosition;
                v_texCoord = vec2(a_texCoord.x, 1.0 - a_texCoord.y);
            }
        """.trimIndent()

        val fragmentShaderSource = """
            precision mediump float;
            varying vec2 v_texCoord;
            uniform sampler2D s_texture;
            void main() {
                gl_FragColor = texture2D(s_texture, v_texCoord);
            }
        """.trimIndent()

        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, vertexShaderSource)
            GLES20.glCompileShader(it)
        }
        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, fragmentShaderSource)
            GLES20.glCompileShader(it)
        }

        return GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textureId
    }

    private fun getFloatBuffer(array: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(array.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(array)
                position(0)
            }
        }
    }
}
