package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.database.AudioNote
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSummarizerApp(viewModel: AudioViewModel) {
    val context = LocalContext.current
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val currentlyPlayingPath by viewModel.currentlyPlayingPath.collectAsStateWithLifecycle()
    val processingStatus by viewModel.processingStatus.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val userSavedApiKey by viewModel.userApiKey.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember(userSavedApiKey) { mutableStateOf(userSavedApiKey) }

    var customTitle by remember { mutableStateOf("") }
    var activeExpandedNoteId by remember { mutableStateOf<Int?>(null) }
    var showPermissionError by remember { mutableStateOf(false) }

    // Audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.processPickedAudio(context, it, customTitle)
            customTitle = "" // Reset title input
        }
    }

    // Permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            showPermissionError = true
        }
    }

    // Force RTL local layout direction for native Persian aesthetics
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                "صدانگار هوشمند",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "تبدیل گفتگو به متن و خلاصه هوشمند",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("action_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "تنظیمات کلید API"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("app_top_bar")
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                // Settings API key configuration dialog
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "تنظیمات کلید API هوش مصنوعی",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "به طور پیش‌فرض، برنامه از کلید سیستمی Gemini API استفاده می‌کند. اگر مایل هستید می‌توانید کلید اختصاصی خود را در زیر وارد کنید:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("کلید اختصاصی Gemini API") },
                                    placeholder = { Text("AIzaSy...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = {
                                        if (apiKeyInput.isNotEmpty()) {
                                            IconButton(onClick = { apiKeyInput = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                                            }
                                        }
                                    }
                                )
                                Text(
                                    "کلید وارد شده شما به طور کاملاً امن در حافظه دستگاه ذخیره می‌شود.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.updateApiKey(apiKeyInput.trim())
                                    showSettingsDialog = false
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ذخیره کلید")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showSettingsDialog = false
                                }
                            ) {
                                Text("انصراف")
                            }
                        },
                        modifier = Modifier.testTag("settings_dialog")
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp)
                ) {
                    // Control Box (Recording & File Upload)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("control_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Title Input
                                OutlinedTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    label = { Text("عنوان گفتگو (اختیاری)") },
                                    placeholder = { Text("مثال: جلسه کاری دوشنبه") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("title_input"),
                                    leadingIcon = {
                                        Icon(Icons.Default.Title, contentDescription = null)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                if (isRecording) {
                                    RecordingPanel(
                                        onStop = {
                                            viewModel.stopAndSaveRecording(customTitle)
                                            customTitle = ""
                                        },
                                        onCancel = {
                                            viewModel.cancelRecording()
                                        }
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Microphone Button
                                        Button(
                                            onClick = {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    viewModel.startRecording()
                                                } else {
                                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .testTag("record_button"),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("شروع ضبط", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }

                                        // File Picker Button
                                        OutlinedButton(
                                            onClick = {
                                                audioPickerLauncher.launch("audio/*")
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .testTag("pick_file_button"),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.AudioFile, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("بارگذاری کلاسیک", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Background process loader
                    processingStatus?.let { status ->
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("processing_loader"),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // List Section Title
                    item {
                        Text(
                            text = "لیست گفتگوها و خلاصه ها",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    // Empty State vs List content
                    if (notes.isEmpty()) {
                        item {
                            EmptyState()
                        }
                    } else {
                        items(notes, key = { it.id }) { note ->
                            AudioNoteCard(
                                note = note,
                                isPlaying = currentlyPlayingPath == note.filePath,
                                isExpanded = activeExpandedNoteId == note.id,
                                onTogglePlay = {
                                    note.filePath?.let { viewModel.togglePlayAudio(it) }
                                },
                                onToggleExpand = {
                                    activeExpandedNoteId = if (activeExpandedNoteId == note.id) null else note.id
                                },
                                onDelete = {
                                    viewModel.deleteNote(note)
                                }
                            )
                        }
                    }
                }

                // Floating Error Alert SnackBar
                errorMessage?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .testTag("error_snackbar"),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("تایید", color = MaterialTheme.colorScheme.inversePrimary)
                            }
                        }
                    ) {
                        Text(error)
                    }
                }

                // Dynamic Audio Permission instructions dialogue
                if (showPermissionError) {
                    AlertDialog(
                        onDismissRequest = { showPermissionError = false },
                        confirmButton = {
                            TextButton(onClick = { showPermissionError = false }) {
                                Text("تایید")
                            }
                        },
                        title = { Text("دسترسی به میکروفون") },
                        text = { Text("برنامه برای ضبط صدا به دسترسی میکروفون نیاز دارد. لطفا دسترسی را در تنظیمات گوشی تایید کنید.") },
                        icon = { Icon(Icons.Default.MicOff, contentDescription = null) },
                        modifier = Modifier.testTag("permission_dialog")
                    )
                }
            }
        }
    }
}

// Custom pulsing recording feedback
@Composable
fun RecordingPanel(
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    var rawSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            rawSeconds += 1
        }
    }

    val minutes = rawSeconds / 60
    val seconds = rawSeconds % 60
    val durationText = String.format("%02d:%02d", minutes, seconds)

    // Breathing pulse effect for mic
    val infiniteTransition = rememberInfiniteTransition(label = "mic_breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteTransitionSpec(),
        label = "pulse_scale"
    )
    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteTransitionSpec(),
        label = "pulse_opacity"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_panel"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .alpha(opacity)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f), ShapeDefaults.Large)
            )
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .alpha(scale)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f), CircleShape)
            )
            FloatingActionButton(
                onClick = onStop,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "در حال ضبط صدا...",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = durationText,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ذخیره و پردازش گفتگو")
            }

            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("لغو")
            }
        }
    }
}

// Unbeaten infinite pulse helper
private fun infiniteTransitionSpec() = infiniteRepeatable<Float>(
    animation = tween(1100, easing = FastOutSlowInEasing),
    repeatMode = RepeatMode.Reverse
)

// Polished onboarding empty illustration with custom image
@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .testTag("empty_state_panel"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val painter = painterResource(id = R.drawable.img_waveform_empty_1782145525960)
        Image(
            painter = painter,
            contentDescription = "No audio history",
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "هنوز هیچ گفتگویی ثبت نشده است",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "شما می‌توانید صدای محیط را ضبط کنید یا یک فایل صوتی ذخیره شده را بارگذاری نمایید تا خلاصه حرفه‌ای جلسه را دریافت کنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// Mastercard representing single transcribed audio
@Composable
fun AudioNoteCard(
    note: AudioNote,
    isPlaying: Boolean,
    isExpanded: Boolean,
    onTogglePlay: () -> Unit,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("audio_note_card_${note.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row (Title, Status Badge and Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTimestamp(note.timestamp)} • ${formatDuration(note.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(status = note.status)
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete record")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player control row (only show if local path exists)
            if (note.filePath != null) {
                val fileExists = File(note.filePath).exists()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePlay,
                            enabled = fileExists,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play original file",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isPlaying) "در حال پخش فایل صوتی..." else if (fileExists) "فایل صوتی اصلی" else "فایل صوتی یافت نشد",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fileExists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded details (Transcript and summary outputs)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Summary Block
                    Text(
                        text = "خلاصه گفتگو",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note.summary ?: "در حال انتظار برای پردازش...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                textDirection = TextDirection.ContentOrRtl
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transcript Block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "متن کامل گفتگو",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!note.transcript.isNullOrBlank() && note.status == "success") {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(note.transcript))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("کپی متن", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note.transcript ?: "در حال انتظار برای پردازش...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                textDirection = TextDirection.ContentOrRtl
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// Status Badging
@Composable
fun StatusBadge(status: String) {
    val colorPair = when (status) {
        "success" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "processing" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "failed" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    val persianLabel = when (status) {
        "success" -> "پایان موفق"
        "processing" -> "در حال پردازش"
        "failed" -> "خطای پردازش"
        else -> "در انتظار"
    }

    Surface(
        color = colorPair.first,
        contentColor = colorPair.second,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = persianLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// Helpers for formatted date and time
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    return sdf.format(Date(timestamp))
}
