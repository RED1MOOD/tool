package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.Surah
import com.example.data.SurahData
import com.example.ui.QuranViewModel
import com.example.ui.Reciter
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppScreen()
                }
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: QuranViewModel = viewModel()

    // Base background styles matching our refined luxury design
    val bgGradientColors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF070F0C), Color(0xFF020504))
    } else {
        listOf(Color(0xFFF0F6F3), Color(0xFFDFEAE4))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(bgGradientColors))
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header of application
                AppHeader(viewModel = viewModel)

                // The unified Quran Video Maker Studio
                Box(modifier = Modifier.weight(1f)) {
                    QuranDesignerScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppHeader(viewModel: QuranViewModel) {
    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕋", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text(
                        text = "صانع روائع القرآن الفخم 👑",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Serif
                        ),
                        modifier = Modifier.testTag("app_title")
                    )
                    Text(
                        text = "تصميم احترافي للآيات الشريفة ومزامنتها بالفيديو والمقامات",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            // Simple decorative islamic star badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun QuranDesignerScreen(viewModel: QuranViewModel) {
    val context = LocalContext.current
    var isSurahDropdownExpanded by remember { mutableStateOf(false) }
    var isReciterDropdownExpanded by remember { mutableStateOf(false) }
    var isSavingInProgress by remember { mutableStateOf(false) }

    // Launchers for user's custom backdrops files from device
    val launcherImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.activeCardStyle = viewModel.activeCardStyle.copy(
                backgroundIndex = 9,
                customBgImageUri = it.toString()
            )
            Toast.makeText(context, "تم تحديد صورة الخلفية الخاصة بك بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    val launcherVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.activeCardStyle = viewModel.activeCardStyle.copy(
                backgroundIndex = 10,
                customBgVideoUri = it.toString()
            )
            Toast.makeText(context, "تم تحديد مقطع فيديو الخلفية الخاص بك بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // 1. Selector Panel (Surah, Ayah, Sound, and consecutive verses toggle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "🕋 إعداد الآيات والمستمع العذب:",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                // Tab Switcher for Single Verse vs range mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!viewModel.exportRangeMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.exportRangeMode = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "آية واحدة فقط",
                            color = if (!viewModel.exportRangeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (viewModel.exportRangeMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.exportRangeMode = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "مجموعة آيات متتالية (فيديو متزامن)",
                            color = if (viewModel.exportRangeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Row 1: Surah Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .clickable { isSurahDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("السورة الكريمة: ${viewModel.designerSurah.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose Surah")
                        }

                        DropdownMenu(
                            expanded = isSurahDropdownExpanded,
                            onDismissRequest = { isSurahDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            SurahData.surahsList.forEach { sur ->
                                DropdownMenuItem(
                                    text = { Text("سورة ${sur.name} (${sur.englishName})") },
                                    onClick = {
                                        viewModel.selectSurahForDesigner(sur)
                                        isSurahDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Conditional Layout if Single is selected
                if (!viewModel.exportRangeMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("رقم الآية المحددة:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                TextField(
                                    value = viewModel.designerAyahNumber.toString(),
                                    onValueChange = { newVal ->
                                        val checkedValue = newVal.toIntOrNull() ?: 1
                                        val total = viewModel.designerSurah.numberOfAyahs
                                        if (checkedValue in 1..total) {
                                            viewModel.designerAyahNumber = checkedValue
                                            viewModel.loadSingleVerseForDesigner(viewModel.designerSurah.number, checkedValue)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.width(60.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Range Select inputs (Start Ayah & End Ayah)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Start Ayah
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("آية البداية:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                TextField(
                                    value = viewModel.designerStartAyah.toString(),
                                    onValueChange = { newVal ->
                                        val num = newVal.toIntOrNull() ?: 1
                                        val total = viewModel.designerSurah.numberOfAyahs
                                        if (num in 1..total) {
                                            viewModel.designerStartAyah = num
                                            if (viewModel.designerEndAyah < num) viewModel.designerEndAyah = num
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        // End Ayah
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("آية النهاية:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                TextField(
                                    value = viewModel.designerEndAyah.toString(),
                                    onValueChange = { newVal ->
                                        val num = newVal.toIntOrNull() ?: 1
                                        val total = viewModel.designerSurah.numberOfAyahs
                                        if (num in viewModel.designerStartAyah..total) {
                                            viewModel.designerEndAyah = num
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }

                // Row 2: Selected Reciter selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reciter Dropdown Selector
                    Box(
                        modifier = Modifier
                            .weight(1.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .clickable { isReciterDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("القارئ: ${viewModel.audioReciter.displayName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(viewModel.audioReciter.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose Reciter")
                        }

                        DropdownMenu(
                            expanded = isReciterDropdownExpanded,
                            onDismissRequest = { isReciterDropdownExpanded = false }
                        ) {
                            Reciter.values().forEach { rec ->
                                DropdownMenuItem(
                                    text = { Text(rec.displayName + " (${rec.description})") },
                                    onClick = {
                                        viewModel.audioReciter = rec
                                        isReciterDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Direct audio listener player preview
                    IconButton(
                        onClick = {
                            viewModel.playAudio(viewModel.designerSurah, viewModel.designerAyahNumber)
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (viewModel.isPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                    ) {
                        if (viewModel.isAudioLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                if (viewModel.isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "Play preview",
                                tint = if (viewModel.isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2. WYSIWYG live simulator card
        Text(
            "✨ معاينة مظهر وتصميم البطاقات (النسبة الرأسية 9:16):",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(24.dp))
                .border(2.5.dp, Color(0xFFECCB7D).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .background(
                    when (viewModel.activeCardStyle.backgroundIndex) {
                        0 -> Brush.verticalGradient(listOf(Color(0xFF062C1E), Color(0xFF0B4E35))) // Emerald Spiritual
                        1 -> Brush.verticalGradient(listOf(Color(0xFF091026), Color(0xFF1B2956))) // Midnight Blue Space
                        2 -> Brush.verticalGradient(listOf(Color(0xFF141414), Color(0xFF262217))) // Gold Luxury Accent
                        3 -> Brush.verticalGradient(listOf(Color(0xFF2B0411), Color(0xFF5A0E2A))) // Velvet Maroon
                        4 -> SolidColor(Color(0xFF121212)) // Charcoal Dark Minimalist
                        else -> SolidColor(Color(0xFF1F1F1F)) // Dynamic background preview representation
                    }
                )
        ) {
            
            // Render preset images behind preview
            if (viewModel.activeCardStyle.backgroundIndex in 5..8) {
                val imageUrl = when (viewModel.activeCardStyle.backgroundIndex) {
                    5 -> "https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=80"
                    6 -> "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80"
                    7 -> "https://images.unsplash.com/photo-1524260855347-148a0455a0b7?w=800&q=80"
                    8 -> "https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&q=80"
                    else -> ""
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Scenic preset",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.55f
                )
            } else if (viewModel.activeCardStyle.backgroundIndex == 9 && !viewModel.activeCardStyle.customBgImageUri.isNullOrBlank()) {
                AsyncImage(
                    model = viewModel.activeCardStyle.customBgImageUri,
                    contentDescription = "Custom upload background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.65f
                )
            }

            // If video backdrop, show visual indicator icon
            if (viewModel.activeCardStyle.backgroundIndex == 10) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", fontSize = 48.sp)
                        Text(
                            "مقطع فيديو مخصص كخلفية متزامنة",
                            color = Color(0xFFECCB7D),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Ornamental Islamic Border
            if (viewModel.activeCardStyle.drawBorder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .border(2.5.dp, Color(0xFFECCB7D), RoundedCornerShape(4.dp))
                ) {
                    // Small decorative corners
                    Spacer(modifier = Modifier.size(12.dp).background(Color(0xFFECCB7D)).align(Alignment.TopStart))
                    Spacer(modifier = Modifier.size(12.dp).background(Color(0xFFECCB7D)).align(Alignment.TopEnd))
                    Spacer(modifier = Modifier.size(12.dp).background(Color(0xFFECCB7D)).align(Alignment.BottomStart))
                    Spacer(modifier = Modifier.size(12.dp).background(Color(0xFFECCB7D)).align(Alignment.BottomEnd))
                }
            }

            val cardModifier = if (viewModel.activeCardStyle.roundedCard) {
                Modifier.clip(RoundedCornerShape(20.dp))
            } else {
                Modifier
            }

            // Centered dark background overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 80.dp, horizontal = 36.dp)
                    .then(cardModifier)
                    .background(Color.Black.copy(alpha = viewModel.activeCardStyle.textOpacity * 0.65f))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Top Moon Cres icon
                    Text("🌙", fontSize = 24.sp, color = Color(0xFFECCB7D), modifier = Modifier.padding(bottom = 8.dp))
                    Box(modifier = Modifier.width(50.dp).height(1.5.dp).background(Color(0xFFECCB7D)))

                    Spacer(modifier = Modifier.height(20.dp))

                    // Arabic Scripture
                    if (viewModel.activeCardStyle.showArabic) {
                        Text(
                            text = if (viewModel.exportRangeMode) {
                                "«تلاوة متتابعة من الآية ${viewModel.designerStartAyah} إلى ${viewModel.designerEndAyah}»"
                            } else {
                                viewModel.designerArabicText
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White,
                                textDirection = TextDirection.Rtl,
                                fontSize = (viewModel.activeCardStyle.arFontSize - 4f).sp,
                                lineHeight = (viewModel.activeCardStyle.arFontSize * 1.25f - 4f).sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (viewModel.activeCardStyle.showArabic && viewModel.activeCardStyle.showEnglish && !viewModel.exportRangeMode) {
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // English Translation
                    if (viewModel.activeCardStyle.showEnglish && !viewModel.exportRangeMode) {
                        Text(
                            text = viewModel.designerEnglishText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFFDFE4EC),
                                fontSize = (viewModel.activeCardStyle.enFontSize - 2f).sp,
                                lineHeight = (viewModel.activeCardStyle.enFontSize * 1.25f - 2f).sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Foot metadata signature block
                    Text(
                        text = if (viewModel.exportRangeMode) {
                            "سلسلة آيات سورة ${viewModel.designerSurah.name}"
                        } else {
                            "سورة ${viewModel.designerSurah.name} • آية ${viewModel.designerAyahNumber}"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFECCB7D),
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. Customize Controllers Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🎨 لوحة التخصيص والألوان الإبداعية:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                // FONT CUSTOMIZER SELECTOR (traditional vs modern vs Lifta-Black)
                Text("👑 نوع خط الكتابة القرآني الشريف:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "معاصر أنيق" to 0,
                        "نسخ متناسق" to 1,
                        "خط ليفتة الفاخر 👑" to 2
                    ).forEach { pair ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (viewModel.activeCardStyle.fontIndex == pair.second) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = if (viewModel.activeCardStyle.fontIndex == pair.second) 2.dp else 0.dp,
                                    color = Color(0xFFECCB7D),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.activeCardStyle = viewModel.activeCardStyle.copy(fontIndex = pair.second)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pair.first,
                                color = if (viewModel.activeCardStyle.fontIndex == pair.second) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Background selection choices row
                Text("🌸 محطة الخلفيات الروحانية والمتحركة:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                
                // Row of preset colors & spiritual templates
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "الزمرد" to 0,
                        "الفضاء" to 1,
                        "الذهبي" to 2,
                        "المخمل" to 3,
                        "القاتم" to 4,
                        "الروضة" to 5,
                        "النجوم" to 6,
                        "الضباب" to 7,
                        "البريق" to 8
                    ).forEach { pair ->
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when (pair.second) {
                                        0 -> Color(0xFF0B4E35)
                                        1 -> Color(0xFF1B2956)
                                        2 -> Color(0xFF262217)
                                        3 -> Color(0xFF5A0E2A)
                                        4 -> Color(0xFF1E1E1E)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                )
                                .border(
                                    width = if (viewModel.activeCardStyle.backgroundIndex == pair.second) 2.5.dp else 0.dp,
                                    color = Color(0xFFECCB7D),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.activeCardStyle = viewModel.activeCardStyle.copy(backgroundIndex = pair.second)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                pair.first,
                                color = if (pair.second < 5) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Custom media upload pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { launcherImagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload img")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("صورة خلفية مخصصة 🖼️", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { launcherVideoPicker.launch("video/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = "Upload vid")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فيديو خلفية مخصص 🎬", fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار الكلمات القرآنية بالعربية", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.activeCardStyle.showArabic,
                        onCheckedChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(showArabic = it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار الترجمة الإنجليزية", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.activeCardStyle.showEnglish,
                        onCheckedChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(showEnglish = it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("رسم إطار إسلامي ذهبي فخم", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.activeCardStyle.drawBorder,
                        onCheckedChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(drawBorder = it) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Font sliders
                Text("حجم الخط العربي (${viewModel.activeCardStyle.arFontSize.toInt()}):", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = viewModel.activeCardStyle.arFontSize,
                    onValueChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(arFontSize = it) },
                    valueRange = 24f..48f
                )

                Text("حجم خط الترجمة (${viewModel.activeCardStyle.enFontSize.toInt()}):", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = viewModel.activeCardStyle.enFontSize,
                    onValueChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(enFontSize = it) },
                    valueRange = 12f..24f
                )

                Text("شفافية بطاقة المتن الخلفية (${(viewModel.activeCardStyle.textOpacity * 100).toInt()}%):", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = viewModel.activeCardStyle.textOpacity,
                    onValueChange = { viewModel.activeCardStyle = viewModel.activeCardStyle.copy(textOpacity = it) },
                    valueRange = 0.2f..1.0f
                )
            }
        }

        // 4. Video Production State / Execution area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "🎬 إنتاج ورندرة الفيديو النهائي:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )

                Text(
                    text = if (viewModel.exportRangeMode) {
                        "سيتم تجميع تلاوة السورة الكريمة من آية ${viewModel.designerStartAyah} إلى ${viewModel.designerEndAyah} متتابعة وعرض كلمات كل آية بالتزامن الكامل على خلفيتك الرائعة المنتقاة!"
                    } else {
                        "سيتم دمج تصميم البطاقة الرائع الذي قمت بإعداده في الأعلى مع تلاوة القارئ لتخريج مقطع فيديو MP4 رأسي مذهل جاهز للنشر كـ Reels أو Stories."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    "⚙️ اختر دقة وجودة رندرة الفيديو (Quality):",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.ui.VideoQuality.values().forEach { quality ->
                        val isSelected = viewModel.selectedVideoQuality == quality
                        Card(
                            onClick = { viewModel.selectedVideoQuality = quality },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = when(quality) {
                                        com.example.ui.VideoQuality.SD_480P -> "480p"
                                        com.example.ui.VideoQuality.HD_720P -> "720p (HD)"
                                        com.example.ui.VideoQuality.UHD_1080P -> "1080p (FHD)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = when(quality) {
                                        com.example.ui.VideoQuality.SD_480P -> "اقتصادية"
                                        com.example.ui.VideoQuality.HD_720P -> "متوازنة"
                                        com.example.ui.VideoQuality.UHD_1080P -> "فائقة"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // Show detailed helper text for the selected quality
                Text(
                    text = viewModel.selectedVideoQuality.descAr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )

                // Render Progress block
                if (viewModel.isVideoExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = viewModel.videoExportProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(viewModel.videoExportStatus, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("${(viewModel.videoExportProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Export Button
                Button(
                    onClick = {
                        viewModel.generateVerseVideo(
                            context = context,
                            onFinished = { uri ->
                                Toast.makeText(context, "اكتمل تصدير فيديو روائع القرآن بنجاح!", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "فشل تصدير الفيديو: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !viewModel.isVideoExporting
                ) {
                    if (viewModel.isVideoExporting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.MovieCreation, contentDescription = "Movie creation icon")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (viewModel.exportRangeMode) "رندرة وتوليد فيديو السلسلة متكامل 🎬" else "رندرة وتفصيل الفيديو كـ MP4 مذهل 🎬",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Image exporting secondary options (only relevant when not doing multiple consecutive verses)
                if (!viewModel.exportRangeMode) {
                    OutlinedButton(
                        onClick = {
                            isSavingInProgress = true
                            viewModel.generateAndShareVerseCard(
                                viewModel.designerArabicText,
                                viewModel.designerEnglishText,
                                viewModel.designerSurah.name,
                                viewModel.designerAyahNumber,
                                viewModel.activeCardStyle
                            ) { uri ->
                                isSavingInProgress = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "تصدير الآية كبطاقة مصممة"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSavingInProgress && !viewModel.isVideoExporting
                    ) {
                        if (isSavingInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Image, contentDescription = "Image preview icon")
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("تصدير ومشاركة كصورة ثابتة ومريحة 🖼️", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Video Player Preview Block
        if (viewModel.exportedVideoUri != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "👀 شاهد وتحقق من المقطع القرآني العذب الناتج:",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                )

                // Video player component
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(2.dp, Color(0xFFECCB7D), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(viewModel.exportedVideoUri)
                                val mc = MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        update = { view ->
                            view.setVideoURI(viewModel.exportedVideoUri)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Share / Save Button
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, viewModel.exportedVideoUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفيديو الشريف 📲"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2B441)), // Gold accent button for sharing
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share video", tint = Color.Black)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("مشاركة الفيديو فوراً ونشر عذوبته الشريفة 📲", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
