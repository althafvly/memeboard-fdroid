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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import java.util.UUID

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

// All coordinates in ROOT space so both onGloballyPositioned and pointerInput agree.
data class ItemBounds(
    val index: Int,
    val rootX: Float,
    val rootY: Float,
    val w: Float,
    val h: Float
) {
    val centerX get() = rootX + w / 2f
    val centerY get() = rootY + h / 2f
    fun contains(rootPx: Float, rootPy: Float) =
        rootPx in rootX..(rootX + w) && rootPy in rootY..(rootY + h)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoundboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resources = remember { context.resources }
    val soundsFile = remember { File(context.filesDir, "sounds_list.txt") }

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
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var soundToRename by remember { mutableStateOf<SoundItem?>(null) }
    var soundToDelete by remember { mutableStateOf<SoundItem?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var inputName by remember { mutableStateOf("") }

    // Plain HashMap — writes don't trigger recomposition, which is intentional.
    // We only need these values during active drag hit-testing, not for rendering.
    val itemBoundsMap = remember { HashMap<Int, ItemBounds>() }

    // Root-coordinate offset of the grid, so we can convert pointerInput's
    // local coordinates to root coordinates for hit-testing against itemBoundsMap.
    var gridRootOffset by remember { mutableStateOf(Offset.Zero) }

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val stopSound = {
        if (mediaPlayer.isPlaying) mediaPlayer.stop()
        isPlaying = false
    }

    DisposableEffect(mediaPlayer) {
        onDispose { mediaPlayer.release() }
    }

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
                title = {
                    Text(
                        text = "MemeBoard",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    AnimatedVisibility(
                        visible = isEditMode,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Toggle Edit",
                            tint = Color(0xFF90CAF9)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isEditMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { pickAudioLauncher.launch("audio/*") },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
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
                userScrollEnabled = draggedIndex == null,
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Record the grid's own position in root space once per layout pass.
                    // pointerInput gives us LOCAL coords; adding this offset gives root coords.
                    .onGloballyPositioned { coords ->
                        gridRootOffset = coords.positionInRoot()
                    }
                    // The gesture detector lives on the GRID, not on each Button.
                    // This means the long-press is seen here before any child can consume it,
                    // which fixes the "sometimes won't grab" issue.
                    .pointerInput(isEditMode) {
                        if (!isEditMode) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                // local → root
                                val rootPx = localOffset.x + gridRootOffset.x
                                val rootPy = localOffset.y + gridRootOffset.y
                                val hit = itemBoundsMap.values.firstOrNull { it.contains(rootPx, rootPy) }
                                draggedIndex = hit?.index
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentDragged = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                val rootPx = change.position.x + gridRootOffset.x
                                val rootPy = change.position.y + gridRootOffset.y

                                // Only swap when the finger has crossed the CENTER of the
                                // target cell — not just its edge. This stops jittery rapid-fire
                                // swaps while moving slowly across a border.
                                val target = itemBoundsMap.values.firstOrNull { bounds ->
                                    if (bounds.index == currentDragged) return@firstOrNull false
                                    if (!bounds.contains(rootPx, rootPy)) return@firstOrNull false
                                    // Must cross midpoint in the direction of travel
                                    if (bounds.index > currentDragged) {
                                        rootPy > bounds.centerY || rootPx > bounds.centerX
                                    } else {
                                        rootPy < bounds.centerY || rootPx < bounds.centerX
                                    }
                                }
                                if (target != null) {
                                    val newList = sounds.toMutableList().apply {
                                        val item = removeAt(currentDragged)
                                        add(target.index, item)
                                    }
                                    sounds = newList
                                    saveSounds(newList)
                                    draggedIndex = target.index
                                }
                            },
                            onDragEnd = { draggedIndex = null },
                            onDragCancel = { draggedIndex = null }
                        )
                    }
            ) {
                itemsIndexed(sounds, key = { _, sound -> sound.id }) { index, sound ->
                    Box(modifier = Modifier.animateItem()) {
                        SoundButton(
                            sound = sound,
                            isEditMode = isEditMode,
                            isDragged = draggedIndex == index,
                            onRemove = { soundToDelete = sound; showDeleteDialog = true },
                            onRename = { soundToRename = sound; inputName = sound.name; showRenameDialog = true },
                            onClick = { if (!isEditMode) playSound(sound) },
                            onBoundsReported = { rootX, rootY, w, h ->
                                itemBoundsMap[index] = ItemBounds(index, rootX, rootY, w, h)
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isPlaying,
                enter = scaleIn(),
                exit = scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Button(
                    onClick = { stopSound() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }

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
                            val extension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "mp3"
                            val file = File(context.filesDir, "sound_${System.currentTimeMillis()}.$extension")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(file).use { output -> input.copyTo(output) }
                            }
                            withContext(Dispatchers.Main) {
                                val newList = sounds + SoundItem(name = inputName, filePath = file.absolutePath)
                                sounds = newList
                                saveSounds(newList)
                                showAddDialog = false
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
                    saveSounds(sounds)
                    showRenameDialog = false
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
                Button(
                    onClick = {
                        sounds = sounds.filter { it.id != soundToDelete?.id }
                        saveSounds(sounds)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Delete") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset") },
            text = { Text("This will delete custom sounds. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        sounds = defaultSounds
                        saveSounds(defaultSounds)
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text("Reset") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundButton(
    sound: SoundItem,
    isEditMode: Boolean,
    isDragged: Boolean,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onClick: () -> Unit,
    onBoundsReported: (rootX: Float, rootY: Float, w: Float, h: Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isDragged) {
        if (isDragged) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shake_${sound.id}")
    val seed = remember(sound.id) { sound.id.hashCode() }
    val duration = remember(seed) { java.util.Random(seed.toLong()).nextInt(30) + 100 }

    val rotation by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rot_${sound.id}"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .zIndex(if (isDragged) 1f else 0f)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                onBoundsReported(pos.x, pos.y, coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            .graphicsLayer {
                if (isEditMode && !isDragged) rotationZ = rotation
                if (isDragged) {
                    scaleX = 1.12f
                    scaleY = 1.12f
                }
            }
    ) {
        // In edit mode, replace Button with a plain Box so that Button's internal
        // touch handling can't intercept the long-press before the grid sees it.
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isDragged) Color(0xFF64B5F6) else Color(0xFF90CAF9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sound.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    maxLines = 1
                )
            }
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF90CAF9),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = sound.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        if (isEditMode && !isDragged) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(4.dp, (-4).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5252))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(4.dp, 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1976D2))
                    .clickable { onRename() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
