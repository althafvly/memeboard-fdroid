package com.bluefirestudios.memeboard

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bluefirestudios.memeboard.ui.theme.MemeBoardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.UUID
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemeBoardTheme {
                SoundboardScreen()
            }
        }
    }
}

data class SoundItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val resId: Int? = null,
    val filePath: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = remember(context) { context.resources }
    val soundsFile = remember(context) { File(context.filesDir, "sounds_list.txt") }

    val defaultSounds = remember {
        listOf(
            SoundItem(name = "ACK", resId = R.raw.ack),
            SoundItem(name = "Dexter", resId = R.raw.dexter),
            SoundItem(name = "Bruh", resId = R.raw.bruh),
            SoundItem(name = "Bone Crack", resId = R.raw.bone_crack),
            SoundItem(name = "Bazooka", resId = R.raw.rip_my_granny),
            SoundItem(name = "Chicken Scream", resId = R.raw.chicken_tree),
            SoundItem(name = "Dial Up", resId = R.raw.dial_up),
            SoundItem(name = "The end", resId = R.raw.end),
            SoundItem(name = "Galaxy Meme", resId = R.raw.galaxy_meme),
            SoundItem(name = "Gay Echo", resId = R.raw.gay_echo),
            SoundItem(name = "Gop Gop Gop", resId = R.raw.gopgopgop),
            SoundItem(name = "Lego Breaking", resId = R.raw.lego_breaking),
            SoundItem(name = "FAHHHHH", resId = R.raw.fah),
            SoundItem(name = "FNAF jumpscare", resId = R.raw.fnaf),
            SoundItem(name = "Rizz", resId = R.raw.rizz),
            SoundItem(name = "Max Verstappen", resId = R.raw.max_verstrappen),
            SoundItem(name = "PLZ SPEED I NEED THISS", resId = R.raw.my_mom_is_kinda_homeless),
            SoundItem(name = "SIXX SEVENN", resId = R.raw.six_seven),
            SoundItem(name = "Vine Boom", resId = R.raw.vine_boom),
            SoundItem(name = "Oh My God", resId = R.raw.oh_my_god),
            SoundItem(name = "500 Cigarettes", resId = R.raw.fivehundred_cigarettes),
            SoundItem(name = "GET OUT", resId = R.raw.get_out),
            SoundItem(name = "Wat da HAILLL", resId = R.raw.wait_what_the_hail),
            SoundItem(name = "Wobbly Wiggly", resId = R.raw.wobbly_wiggly),
            SoundItem(name = "John CENAA!", resId = R.raw.john_cena),
            SoundItem(name = "Yo Phone Linging", resId = R.raw.yo_phone_linging),
            SoundItem(name = "Kim Jong Goon", resId = R.raw.kim_jong_goon),
            SoundItem(name = "Prowler", resId = R.raw.prowler)
        )
    }

    var sounds by remember { mutableStateOf(listOf<SoundItem>()) }
    var isEditMode by remember { mutableStateOf(false) }
    var isAnyItemDragging by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var soundToRename by remember { mutableStateOf<SoundItem?>(null) }
    var soundToDelete by remember { mutableStateOf<SoundItem?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var inputName by remember { mutableStateOf("") }

    fun saveSounds(list: List<SoundItem>) {
        scope.launch(Dispatchers.IO) {
            val content = list.joinToString("\n") {
                "${it.id}|${it.name}|${it.resId ?: "null"}|${it.filePath ?: "null"}"
            }
            soundsFile.writeText(content)
        }
    }

    LaunchedEffect(soundsFile) {
        withContext(Dispatchers.IO) {
            if (soundsFile.exists()) {
                val lines = soundsFile.readLines()
                val loaded = lines.mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size == 4) {
                        SoundItem(
                            id = parts[0],
                            name = parts[1],
                            resId = parts[2].toIntOrNull(),
                            filePath = parts[3].takeIf { it != "null" }
                        )
                    } else null
                }
                withContext(Dispatchers.Main) { sounds = loaded }
            } else {
                withContext(Dispatchers.Main) { sounds = defaultSounds }
            }
        }
    }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }
    }

    val playSound = { sound: SoundItem ->
        try {
            mediaPlayer.reset()
            if (sound.resId != null) {
                val afd = resources.openRawResourceFd(sound.resId)
                mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else if (sound.filePath != null) {
                mediaPlayer.setDataSource(sound.filePath)
            }
            mediaPlayer.setOnCompletionListener { isPlaying = false }
            mediaPlayer.prepare()
            mediaPlayer.start()
            isPlaying = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    val stopSound = {
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        isPlaying = false
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    val pickAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingUri = uri
            inputName = ""
            showAddDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "MemeBoard", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    AnimatedVisibility(visible = isEditMode, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFFFF5252))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit, contentDescription = "Toggle Edit", tint = Color(0xFF90CAF9))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = isEditMode, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(onClick = { pickAudioLauncher.launch("audio/*") }, containerColor = Color(0xFF4CAF50), contentColor = Color.White, shape = CircleShape) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sound")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                userScrollEnabled = !isAnyItemDragging,
                contentPadding = PaddingValues(16.dp, 8.dp, 24.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sounds, key = { _, it -> it.id }) { index, sound ->
                    Box(modifier = Modifier.animateItem()) {
                        SoundButton(
                            sound = sound,
                            isEditMode = isEditMode,
                            onRemove = { soundToDelete = sound; showDeleteDialog = true },
                            onRename = { soundToRename = sound; inputName = sound.name; showRenameDialog = true },
                            onClick = { if (!isEditMode) playSound(sound) },
                            onDrag = { from, to ->
                                val newList = sounds.toMutableList()
                                Collections.swap(newList, from, to)
                                sounds = newList
                                saveSounds(newList)
                            },
                            onDragStateChange = { isAnyItemDragging = it },
                            index = index,
                            totalCount = sounds.size
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isPlaying, enter = scaleIn(), exit = scaleOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                Button(onClick = { stopSound() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)), shape = CircleShape, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
                    Box(modifier = Modifier.size(24.dp).background(Color.White, RoundedCornerShape(4.dp)))
                }
            }
        }
    }

    // Dialogs remain largely the same, but use 'saveSounds' safely
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Sound Name") },
            text = { TextField(value = inputName, onValueChange = { inputName = it }) },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingUri
                    if (uri != null && inputName.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "mp3"
                            val file = File(context.filesDir, "sound_${System.currentTimeMillis()}.$extension")
                            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
                            withContext(Dispatchers.Main) {
                                val newList = sounds + SoundItem(name = inputName, filePath = file.absolutePath)
                                sounds = newList; saveSounds(newList); showAddDialog = false
                            }
                        }
                    }
                }) { Text("Add") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = { TextField(value = inputName, onValueChange = { inputName = it }) },
            confirmButton = {
                Button(onClick = {
                    sounds = sounds.map { if (it.id == soundToRename?.id) it.copy(name = inputName) else it }
                    saveSounds(sounds); showRenameDialog = false
                }) { Text("Rename") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Sound") },
            text = { Text("Delete \"${soundToDelete?.name}\"?") },
            confirmButton = {
                Button(onClick = {
                    sounds = sounds.filter { it.id != soundToDelete?.id }
                    saveSounds(sounds); showDeleteDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) { Text("Delete") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset") },
            text = { Text("This will delete custom sounds. Continue?") },
            confirmButton = {
                Button(onClick = {
                    sounds = defaultSounds; saveSounds(defaultSounds); showResetDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) { Text("Reset") }
            }
        )
    }
}

@Composable
fun SoundButton(
    sound: SoundItem, isEditMode: Boolean, onRemove: () -> Unit, onRename: () -> Unit, onClick: () -> Unit,
    onDrag: (Int, Int) -> Unit, onDragStateChange: (Boolean) -> Unit, index: Int, totalCount: Int
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "shake")

    // Consistent random seed for each item to avoid warnings and "jumping" animations
    val seed = remember(sound.id) { sound.id.hashCode() }
    val random = remember(seed) { java.util.Random(seed.toLong()) }
    val duration = remember(seed) { random.nextInt(30) + 100 }

    val rotation by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(duration, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "rot"
    )

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp).zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isEditMode && !isDragging) { rotationZ = rotation }
                if (isDragging) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.1f; scaleY = 1.1f }
            }
            .pointerInput(isEditMode) {
                if (!isEditMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); isDragging = true; onDragStateChange(true) },
                    onDragEnd = { isDragging = false; onDragStateChange(false); dragOffset = Offset.Zero },
                    onDragCancel = { isDragging = false; onDragStateChange(false); dragOffset = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        val itemW = size.width.toFloat(); val itemH = size.height.toFloat(); val spacing = 12.dp.toPx()
                        val threshX = itemW / 2f + spacing / 2f; val threshY = itemH / 2f + spacing / 2f

                        if (dragOffset.x > threshX && index % 2 == 0 && index + 1 < totalCount) {
                            onDrag(index, index + 1); dragOffset = dragOffset.copy(x = dragOffset.x - (itemW + spacing))
                        } else if (dragOffset.x < -threshX && index % 2 != 0) {
                            onDrag(index, index - 1); dragOffset = dragOffset.copy(x = dragOffset.x + (itemW + spacing))
                        }
                        if (dragOffset.y > threshY && index + 2 < totalCount) {
                            onDrag(index, index + 2); dragOffset = dragOffset.copy(y = dragOffset.y - (itemH + spacing))
                        } else if (dragOffset.y < -threshY && index - 2 >= 0) {
                            onDrag(index, index - 2); dragOffset = dragOffset.copy(y = dragOffset.y + (itemH + spacing))
                        }
                    }
                )
            }
    ) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9), contentColor = Color.Black), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxSize()) {
            Text(text = sound.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
        if (isEditMode) {
            Box(modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(24.dp).clip(CircleShape).background(Color(0xFFFF5252)).clickable { onRemove() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(24.dp).clip(CircleShape).background(Color(0xFF1976D2)).clickable { onRename() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
