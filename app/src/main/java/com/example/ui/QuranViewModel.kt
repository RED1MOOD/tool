package com.example.ui

import android.app.Application
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Surah
import com.example.data.SurahData
import com.example.data.api.QuranApiService
import com.example.data.database.AppDatabase
import com.example.data.database.FavoriteAyah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

sealed class SurahDetailState {
    object Idle : SurahDetailState()
    object Loading : SurahDetailState()
    data class Success(val verses: List<Verse>) : SurahDetailState()
    data class Error(val message: String) : SurahDetailState()
}

data class Verse(
    val indexInSurah: Int, // 1-based
    val absoluteNumber: Int,
    val arabicText: String,
    val englishText: String,
    var isBookmarked: Boolean = false
)

enum class AppTab {
    READ, AUDIO, DESIGNER, BOOKMARKS
}

enum class Reciter(val id: String, val displayName: String, val description: String) {
    ALAFASY("Alafasy_128kbps", "مشاري راشد العفاسي", "من أعذب الأصوات الهادئة"),
    HUSARY("Husary_128kbps", "محمود خليل الحصري", "شيخ مقارئ مصر ورائد الترتيل"),
    ABDUL_BASIT("Abdul_Basit_Murattal_192kbps", "عبد الباسط عبد الصمد", "صوت الجنة، أسطورة التلاوة المصرية"),
    MINSHAWI("Minshawi_Murattal_128kbps", "محمد صديق المنشاوي", "الصوت الباكي الخاشع المؤثر"),
    SUDAIS("Sudais_64kbps", "عبد الرحمن السديس", "تلاوة الحرم المكي الشريف"),
    MAHER("MaherAlMuaiqly128kbps", "ماهر المعيقلي", "صوت المسجد الحرام الخاشع الندي"),
    SHURAIM("Saood_ash-Shuraym_128kbps", "سعود الشريم", "صوت الحرم المكي القدير السريع الشجي"),
    DOSARI("Yasser_Ad-Dussary_128kbps", "ياسر الدوسري", "قارئ الكعبة المشرفة بنبرته المتميزة المونقة"),
    MUSTAFA_ISMAIL("Mostafa_Ismaeel_128kbps", "مصطفى إسماعيل", "ملك المقامات القرآني بجمهورية مصر العربية")
}

enum class VideoQuality(
    val idName: String,
    val nameAr: String,
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val fps: Int,
    val descAr: String
) {
    SD_480P("SD_480P", "جودة اقتصادية (SD - 480p)", 480, 854, 1200000, 12, "تصدير فائق السرعة، خفيف، ملائم للهواتف القديمة"),
    HD_720P("HD_720P", "جودة عالية (HD - 720p)", 720, 1280, 2500000, 15, "متوازنة واحترافية للنشر في منصات التواصل (افتراضي)"),
    UHD_1080P("UHD_1080P", "جودة خارقة (FHD - 1080p) 👑", 1080, 1920, 5000000, 24, "دقة فائقة الوضوح مع تلاوة سلسة وتفاصيل بالغة الجمال")
}

data class CardStyle(
    val backgroundIndex: Int = 0, // 0-4 gradients, 5-8 scenic presets, 9 custom image, 10 custom video
    val arFontSize: Float = 36f,
    val enFontSize: Float = 18f,
    val showArabic: Boolean = true,
    val showEnglish: Boolean = true,
    val textOpacity: Float = 1.0f,
    val roundedCard: Boolean = true,
    val drawBorder: Boolean = true,
    val customBgImageUri: String? = null,
    val customBgVideoUri: String? = null,
    val fontIndex: Int = 2 // default to 2 (Lifta-Black OTF)
)

class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = QuranApiService.create()
    private val database = AppDatabase.getDatabase(application)
    private val favoriteDao = database.favoriteAyahDao()

    // UI state
    var currentTab by mutableStateOf(AppTab.READ)
    var searchQuery by mutableStateOf("")
    var selectedSurah by mutableStateOf<Surah?>(null)
    var surahDetailState by mutableStateOf<SurahDetailState>(SurahDetailState.Idle)

    // Selection in designer
    var designerSurah by mutableStateOf<Surah>(SurahData.surahsList[0])
    var designerAyahNumber by mutableStateOf(1)
    var designerArabicText by mutableStateOf("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")
    var designerEnglishText by mutableStateOf("In the name of Allah, the Entirely Beautiful, the Especially Merciful.")
    var activeCardStyle by mutableStateOf(CardStyle())

    // Range select state for compiling sequential verses
    var exportRangeMode by mutableStateOf(false)
    var designerStartAyah by mutableStateOf(1)
    var designerEndAyah by mutableStateOf(1)
    var designerSurahVerses by mutableStateOf<List<Verse>>(emptyList())

    // Audio Player State
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying by mutableStateOf(false)
    var isAudioLoading by mutableStateOf(false)
    var currentPlayingSurah by mutableStateOf<Surah?>(null)
    var currentPlayingAyahIndex by mutableStateOf(1) // 1-based
    var audioProgress by mutableStateOf(0f) // 0 to 1
    var audioReciter by mutableStateOf(Reciter.ALAFASY)

    // Video Generator State
    var isVideoExporting by mutableStateOf(false)
    var videoExportProgress by mutableStateOf(0f)
    var videoExportStatus by mutableStateOf("")
    var exportedVideoUri by mutableStateOf<Uri?>(null)
    var selectedVideoQuality by mutableStateOf(VideoQuality.HD_720P)

    // Favorites Flow from Room
    val favoriteAyahs: StateFlow<List<FavoriteAyah>> = favoriteDao.getAllFavoritesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of surahs filtered
    val filteredSurahs: List<Surah>
        get() = if (searchQuery.isBlank()) {
            SurahData.surahsList
        } else {
            SurahData.surahsList.filter {
                it.englishName.contains(searchQuery, ignoreCase = true) ||
                        it.name.contains(searchQuery) ||
                        it.englishNameTranslation.contains(searchQuery, ignoreCase = true)
            }
        }

    init {
        // Initialize player progress monitoring
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(800)
                mediaPlayer?.let { player ->
                    if (isPlaying && player.duration > 0) {
                        audioProgress = player.currentPosition.toFloat() / player.duration.toFloat()
                    }
                }
            }
        }
        // Prefetch first Surah's designer cache
        loadSurahVersesForDesigner(designerSurah.number)
    }

    fun selectSurah(surah: Surah) {
        selectedSurah = surah
        currentTab = AppTab.READ
        loadSurahVerses(surah.number)
    }

    fun selectSurahForDesigner(surah: Surah) {
        designerSurah = surah
        designerAyahNumber = 1
        designerStartAyah = 1
        designerEndAyah = 1.coerceAtMost(surah.numberOfAyahs)
        currentTab = AppTab.DESIGNER
        loadSurahVersesForDesigner(surah.number)
    }

    private fun loadSurahVerses(surahId: Int) {
        surahDetailState = SurahDetailState.Loading
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getSurahDetails(surahId)
                }
                if (response.code == 200 && response.data.size >= 2) {
                    val simpleEdition = response.data[0]
                    val enEdition = response.data[1]

                    val versesList = simpleEdition.ayahs.mapIndexed { idx, arAyah ->
                        val enText = enEdition.ayahs.getOrNull(idx)?.text ?: ""
                        val isBookmarked = favoriteDao.isFavorite(surahId, arAyah.numberInSurah)
                        Verse(
                            indexInSurah = arAyah.numberInSurah,
                            absoluteNumber = arAyah.number,
                            arabicText = arAyah.text,
                            englishText = enText,
                            isBookmarked = isBookmarked
                        )
                    }
                    surahDetailState = SurahDetailState.Success(versesList)
                } else {
                    surahDetailState = SurahDetailState.Error("فشل تحميل البيانات من الخادم")
                }
            } catch (e: Exception) {
                surahDetailState = SurahDetailState.Error(e.localizedMessage ?: "حدث خطأ غير متوقع")
            }
        }
    }

    fun loadSurahVersesForDesigner(surahId: Int) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getSurahDetails(surahId)
                }
                if (response.code == 200 && response.data.size >= 2) {
                    val simpleEdition = response.data[0]
                    val enEdition = response.data[1]

                    val versesList = simpleEdition.ayahs.mapIndexed { idx, arAyah ->
                        val enText = enEdition.ayahs.getOrNull(idx)?.text ?: ""
                        Verse(
                            indexInSurah = arAyah.numberInSurah,
                            absoluteNumber = arAyah.number,
                            arabicText = arAyah.text,
                            englishText = enText,
                            isBookmarked = false
                        )
                    }
                    designerSurahVerses = versesList
                    
                    // Sync up designers text fields
                    val cur = versesList.firstOrNull { it.indexInSurah == designerAyahNumber }
                    if (cur != null) {
                        designerArabicText = cur.arabicText
                        designerEnglishText = cur.englishText
                    }
                }
            } catch (e: Exception) {
                // fall back gracefully
            }
        }
    }

    fun loadSingleVerseForDesigner(surahId: Int, ayahNum: Int) {
        // If designerSurahVerses is already cached, utilize it directly
        val cached = designerSurahVerses.firstOrNull { it.indexInSurah == ayahNum }
        if (cached != null) {
            designerArabicText = cached.arabicText
            designerEnglishText = cached.englishText
        } else {
            viewModelScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        apiService.getSurahDetails(surahId)
                    }
                    if (response.code == 200 && response.data.size >= 2) {
                        val simpleEdition = response.data[0]
                        val enEdition = response.data[1]

                        val arAyah = simpleEdition.ayahs.firstOrNull { it.numberInSurah == ayahNum }
                        val enAyah = enEdition.ayahs.firstOrNull { it.numberInSurah == ayahNum }

                        if (arAyah != null && enAyah != null) {
                            designerArabicText = arAyah.text
                            designerEnglishText = enAyah.text
                        }
                    }
                } catch (e: Exception) {
                    // fall back
                }
            }
        }
    }

    // Toggle Bookmarks in database
    fun toggleBookmark(surah: Surah, verse: Verse) {
        viewModelScope.launch {
            val isFav = favoriteDao.isFavorite(surah.number, verse.indexInSurah)
            if (isFav) {
                favoriteDao.deleteFavorite(
                    FavoriteAyah(
                        surahNumber = surah.number,
                        ayahNumber = verse.indexInSurah,
                        surahName = surah.name,
                        surahEnglishName = surah.englishName,
                        arabicText = verse.arabicText,
                        englishTranslation = verse.englishText
                    )
                )
                verse.isBookmarked = false
            } else {
                favoriteDao.insertFavorite(
                    FavoriteAyah(
                        surahNumber = surah.number,
                        ayahNumber = verse.indexInSurah,
                        surahName = surah.name,
                        surahEnglishName = surah.englishName,
                        arabicText = verse.arabicText,
                        englishTranslation = verse.englishText
                    )
                )
                verse.isBookmarked = true
            }

            // Sync detail UI state if loaded
            val state = surahDetailState
            if (state is SurahDetailState.Success) {
                val updated = state.verses.map {
                    if (it.indexInSurah == verse.indexInSurah) {
                        it.copy(isBookmarked = !isFav)
                    } else it
                }
                surahDetailState = SurahDetailState.Success(updated)
            }
        }
    }

    fun toggleBookmarkFromFav(fav: FavoriteAyah) {
        viewModelScope.launch {
            favoriteDao.deleteFavorite(fav)

            // If selectedSurah is currently viewing, reload or update Bookmarked field
            selectedSurah?.let {
                if (it.number == fav.surahNumber) {
                    val state = surahDetailState
                    if (state is SurahDetailState.Success) {
                        val updated = state.verses.map { verse ->
                            if (verse.indexInSurah == fav.ayahNumber) {
                                verse.copy(isBookmarked = false)
                            } else verse
                        }
                        surahDetailState = SurahDetailState.Success(updated)
                    }
                }
            }
        }
    }

    // Audio Playback Commands
    fun playAudio(surah: Surah, ayahIndex: Int) {
        stopAudio()
        currentPlayingSurah = surah
        currentPlayingAyahIndex = ayahIndex
        isAudioLoading = true
        isPlaying = false

        val surahStr = String.format(Locale.US, "%03d", surah.number)
        val ayahStr = String.format(Locale.US, "%03d", ayahIndex)
        val url = "https://everyayah.com/data/${audioReciter.id}/$surahStr$ayahStr.mp3"

        viewModelScope.launch {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener {
                        this@QuranViewModel.isAudioLoading = false
                        this@QuranViewModel.isPlaying = true
                        start()
                    }
                    setOnCompletionListener {
                        // Play next Ayah automatically!
                        val totalAyahs = surah.numberOfAyahs
                        if (currentPlayingAyahIndex < totalAyahs) {
                            playNextAyah()
                        } else {
                            this@QuranViewModel.isPlaying = false
                            this@QuranViewModel.currentPlayingSurah = null
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        this@QuranViewModel.isAudioLoading = false
                        this@QuranViewModel.isPlaying = false
                        false
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                this@QuranViewModel.isAudioLoading = false
                this@QuranViewModel.isPlaying = false
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        } else {
            // Start from beginning or selected
            val surah = selectedSurah ?: SurahData.surahsList[0]
            playAudio(surah, 1)
        }
    }

    fun playNextAyah() {
        val surah = currentPlayingSurah ?: selectedSurah ?: return
        val nextIndex = currentPlayingAyahIndex + 1
        if (nextIndex <= surah.numberOfAyahs) {
            playAudio(surah, nextIndex)
        }
    }

    fun playPrevAyah() {
        val surah = currentPlayingSurah ?: selectedSurah ?: return
        val prevIndex = currentPlayingAyahIndex - 1
        if (prevIndex >= 1) {
            playAudio(surah, prevIndex)
        }
    }

    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        isPlaying = false
        isAudioLoading = false
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }

    fun generateVerseVideo(
        context: android.content.Context,
        onFinished: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isVideoExporting) return
        isVideoExporting = true
        videoExportProgress = 0.05f
        videoExportStatus = "جاري تجميع وتحضير آيات الذكر الحكيم..."

        viewModelScope.launch {
            try {
                // Compile the list of verses to render based on selection mode
                val versesToRender = ArrayList<Verse>()
                val start = if (exportRangeMode) designerStartAyah else designerAyahNumber
                val end = if (exportRangeMode) designerEndAyah else designerAyahNumber

                if (exportRangeMode && designerSurahVerses.isNotEmpty()) {
                    val filtered = designerSurahVerses.filter { it.indexInSurah in start..end }
                    versesToRender.addAll(filtered)
                } else {
                    versesToRender.add(Verse(
                        indexInSurah = designerAyahNumber,
                        absoluteNumber = 0,
                        arabicText = designerArabicText,
                        englishText = designerEnglishText
                    ))
                }

                if (versesToRender.isEmpty()) {
                    throw IllegalStateException("لم نجد آيات صالحة للتصدير في هذا المدى.")
                }

                val audioUrls = ArrayList<String>()
                val verseTexts = ArrayList<Pair<String, String>>()

                for (v in versesToRender) {
                    val surahStr = String.format(Locale.US, "%03d", designerSurah.number)
                    val ayahStr = String.format(Locale.US, "%03d", v.indexInSurah)
                    val url = "https://everyayah.com/data/${audioReciter.id}/$surahStr$ayahStr.mp3"
                    audioUrls.add(url)
                    verseTexts.add(Pair(v.arabicText, v.englishText))
                }

                val videoDir = File(context.cacheDir, "exported_videos")
                if (!videoDir.exists()) videoDir.mkdirs()
                val outputFile = File(videoDir, "quran_video_${System.currentTimeMillis()}.mp4")

                videoExportStatus = "تحميل الأصوات وتوليف الخلفيات الاحترافية..."

                val success = com.example.utils.VideoCreator.createQuranVideo(
                    context = context,
                    audioUrls = audioUrls,
                    verseTexts = verseTexts,
                    surahName = designerSurah.name,
                    startAyahNum = start,
                    style = activeCardStyle,
                    outputFile = outputFile,
                    width = selectedVideoQuality.width,
                    height = selectedVideoQuality.height,
                    bitRate = selectedVideoQuality.bitRate,
                    frameRate = selectedVideoQuality.fps,
                    onProgress = { progress ->
                        videoExportProgress = progress
                        if (progress <= 0.15f) {
                            val pct = (progress / 15f * 10000).toInt().coerceIn(0, 100)
                            videoExportStatus = "جاري تحميل تلاوة القارئ: $pct%"
                        } else if (progress < 0.85f) {
                            val percent = ((progress - 0.15f) / 0.70f * 100).toInt().coerceIn(0, 100)
                            videoExportStatus = "جاري مزامنة التلاوة مع الخلفية المتحركة والرندرة: $percent%"
                        } else if (progress < 1.0f) {
                            videoExportStatus = "حفظ ودمج ملف المقطع النهائي..."
                        } else {
                            videoExportStatus = "اكتمل التصدير بنجاح!"
                        }
                    }
                )

                if (success && outputFile.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                    exportedVideoUri = uri
                    isVideoExporting = false
                    onFinished(uri)
                } else {
                    isVideoExporting = false
                    videoExportStatus = "حدث خطأ أثناء حفظ ملف رندرة الفيديو."
                    onError("فشل إنشاء الفيديو.")
                }
            } catch (e: Exception) {
                isVideoExporting = false
                videoExportStatus = "فشل تصدير الفيديو: ${e.localizedMessage}"
                onError(e.localizedMessage ?: "حدث خطأ غير متوقع.")
            }
        }
    }

    // High performance share card generator
    fun generateAndShareVerseCard(
        arabicText: String,
        englishText: String,
        surahName: String,
        ayahNum: Int,
        style: CardStyle,
        onShareReady: (Uri) -> Unit
    ) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                createVerseCardBitmapStatic(getApplication(), arabicText, englishText, surahName, ayahNum, style)
            }
            val uri = withContext(Dispatchers.IO) {
                saveBitmapToCache(bitmap)
            }
            if (uri != null) {
                onShareReady(uri)
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        val context = getApplication<Application>()
        val parentDir = File(context.cacheDir, "shared_verses")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val file = File(parentDir, "verse_card_${System.currentTimeMillis()}.jpg")
        return try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}

// Global drawing package helper to ensure maximum design aesthetics with customizable Lifta font
fun createVerseCardBitmapStatic(
    context: android.content.Context,
    arabicText: String,
    englishText: String,
    surahName: String,
    ayahNum: Int,
    style: CardStyle,
    width: Int = 1080,
    height: Int = 1920,
    transparentBg: Boolean = false
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val scaleX = width.toFloat() / 1080f
    val scaleY = height.toFloat() / 1920f
    val scaleFactor = Math.min(scaleX, scaleY).coerceAtLeast(0.45f)

    // 1. Draw Background style (bypassed if transparency is enabled in video overlays)
    val bgPaint = Paint().apply { isAntiAlias = true }
    if (!transparentBg) {
        when (style.backgroundIndex) {
            0 -> { // Deep Emerald Spiritual Gradient
                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#062C1E"), Color.parseColor("#0B4E35"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            1 -> { // Midnight Dark Blue Space
                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#091026"), Color.parseColor("#1B2956"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            2 -> { // Majestic Luxury Gold Theme
                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#141414"), Color.parseColor("#262217"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            3 -> { // Velvet Maroon Calm Sunset
                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#2B0411"), Color.parseColor("#5A0E2A"),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            4 -> { // Charcoal Black Absolute Minimalist
                bgPaint.color = Color.parseColor("#121212")
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
            else -> {
                // Presets/Custom will overlay an image or video frame on top, draw deep dim background fallback
                bgPaint.color = Color.parseColor("#000000")
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }
        }
    }

    // 2. Draw Ornamental Islamic Border Pattern
    if (style.drawBorder) {
        val borderPaint = Paint().apply {
            color = Color.parseColor("#ECCB7D") // Golden border
            strokeWidth = 6f * scaleFactor
            setStyle(Paint.Style.STROKE)
            isAntiAlias = true
        }
        val margin = 40f * scaleFactor
        canvas.drawRect(margin, margin, width - margin, height - margin, borderPaint)

        borderPaint.strokeWidth = 2f * scaleFactor
        canvas.drawRect(margin + 12f * scaleFactor, margin + 12f * scaleFactor, width - margin - 12f * scaleFactor, height - margin - 12f * scaleFactor, borderPaint)

        val fillPaint = Paint().apply {
            color = Color.parseColor("#ECCB7D")
            isAntiAlias = true
        }
        val cornerSize = 25f * scaleFactor
        canvas.drawRect(margin, margin, margin + cornerSize, margin + cornerSize, fillPaint)
        canvas.drawRect(width - margin - cornerSize, margin, width - margin, margin + cornerSize, fillPaint)
        canvas.drawRect(margin, height - margin - cornerSize, margin + cornerSize, height - margin, fillPaint)
        canvas.drawRect(width - margin - cornerSize, height - margin - cornerSize, width - margin, height - margin, fillPaint)
    }

    // 3. Draw Semi-Transparent Card Core Backdrop
    val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = (style.textOpacity * 175).toInt() // frostable deep opacity
        isAntiAlias = true
    }
    val cardMarginLeft = 100f * scaleX
    val cardMarginTop = 220f * scaleY
    val cardWidth = width - (cardMarginLeft * 2)
    val cardRect = RectF(cardMarginLeft, cardMarginTop, width - cardMarginLeft, height - cardMarginTop)

    if (style.roundedCard) {
        canvas.drawRoundRect(cardRect, 40f * scaleFactor, 40f * scaleFactor, overlayPaint)
    } else {
        canvas.drawRect(cardRect, overlayPaint)
    }

    // Golden accent header decoration for card
    val linePaint = Paint().apply {
        color = Color.parseColor("#ECCB7D")
        strokeWidth = 3f * scaleFactor
        isAntiAlias = true
    }
    canvas.drawLine(width / 2f - 150f * scaleFactor, cardMarginTop + 100f * scaleFactor, width / 2f + 150f * scaleFactor, cardMarginTop + 100f * scaleFactor, linePaint)

    val circlePaint = Paint().apply {
        color = Color.parseColor("#DCAE3D")
        setStyle(Paint.Style.FILL)
        isAntiAlias = true
    }
    canvas.drawCircle(width / 2f, cardMarginTop + 100f * scaleFactor, 15f * scaleFactor, circlePaint)

    // 4. Print Sacred Texts
    val maxTextWidth = (cardWidth - 120f * scaleFactor).toInt()
    var currentY = cardMarginTop + 200f * scaleLocationY(height)

    // 4a. Draw ARABIC text with custom font family loaded
    if (style.showArabic && arabicText.isNotBlank()) {
        val fontTypeface = try {
            when (style.fontIndex) {
                0 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                1 -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                2 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.resources.getFont(com.example.R.font.lifta_black)
                    } else {
                        androidx.core.content.res.ResourcesCompat.getFont(context, com.example.R.font.lifta_black)
                            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    }
                }
                else -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
        } catch (e: Exception) {
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val arTextPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = style.arFontSize * 1.55f * scaleFactor // Scale up for beautiful print crispyness
            isAntiAlias = true
            typeface = fontTypeface
            textAlign = Paint.Align.CENTER
        }

        val builder = StaticLayout.Builder.obtain(arabicText, 0, arabicText.length, arTextPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.35f)
            .setIncludePad(true)

        val layout = builder.build()
        canvas.save()
        canvas.translate(width / 2f, currentY)
        layout.draw(canvas)
        canvas.restore()

        currentY += layout.height + 120f * scaleFactor
    }

    // 4b. Draw ENGLISH text
    if (style.showEnglish && englishText.isNotBlank()) {
        val enTextPaint = TextPaint().apply {
            color = Color.parseColor("#E1E6EE")
            textSize = style.enFontSize * 1.6f * scaleFactor
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val builder = StaticLayout.Builder.obtain(englishText, 0, englishText.length, enTextPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(true)

        val layout = builder.build()
        canvas.save()
        canvas.translate(width / 2f, currentY)
        layout.draw(canvas)
        canvas.restore()
    }

    // 5. Draw Signature metadata
    val sigPaint = Paint().apply {
        color = Color.parseColor("#ECCB7D")
        textSize = 34f * scaleFactor
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val sigText = "$surahName • آية $ayahNum"
    canvas.drawText(sigText, width / 2f, height - cardMarginTop - 100f * scaleFactor, sigPaint)

    // Minor watermark stamp
    val wmPaint = Paint().apply {
        color = Color.WHITE
        alpha = 80
        textSize = 24f * scaleFactor
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("مشارك من تطبيق صانع روائع وفيديوهات القرآن", width / 2f, height - 120f * scaleFactor, wmPaint)

    return bitmap
}

private fun scaleLocationY(height: Int): Float {
    return if (height > 1500) 1.0f else if (height > 1000) 0.85f else 0.70f
}
