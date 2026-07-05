package com.vektor.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import java.time.Instant

private val VektorGreen = Color(0xFF2AE500)
private val VektorText = Color(0xFFE5E2E1)
private val VektorMuted = Color(0xFFCFC4C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val viewModel: MainViewModel = viewModel()
                LaunchedEffect(Unit) { viewModel.bootstrap() }
                VektorApp(viewModel)
            }
        }
    }
}

@Composable
private fun VektorApp(viewModel: MainViewModel) {
    val screen by viewModel.screen.collectAsState()
    val loading by viewModel.loadingState.collectAsState()

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                AppScreen.Scan -> ScanScreen(viewModel)
                is AppScreen.Playback -> PlaybackScreen(viewModel)
            }
            if (loading is LoadingState.Loading) {
                StatusOverlay((loading as LoadingState.Loading).message)
            }
        }
    }
}

@Composable
private fun ScanScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val history by viewModel.scanHistory.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(onCode = viewModel::handleScannedText)
        }
        GridOverlay()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            Spacer(Modifier.weight(1f))
            ScannerFrame(hasPermission)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                        .primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    viewModel.loadClipboard(clip)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Text("LOAD CLIPBOARD M3U/HLS URL", color = VektorText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            if (history.isNotEmpty()) {
                Button(
                    onClick = { showHistory = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text("HISTORY", color = VektorGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Text(
                "ALIGN QR CODE OR PASTE M3U/HLS URL",
                color = VektorMuted.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        if (showHistory) {
            ScanHistoryPanel(
                items = history,
                onDismiss = { showHistory = false },
                onSelect = {
                    showHistory = false
                    viewModel.loadFromHistory(it)
                },
                onDelete = viewModel::deleteHistoryItem,
                onClear = {
                    showHistory = false
                    viewModel.clearHistory()
                }
            )
        }
    }
}

@Composable
private fun ScanHistoryPanel(
    items: List<ScanHistoryItem>,
    onDismiss: () -> Unit,
    onSelect: (ScanHistoryItem) -> Unit,
    onDelete: (ScanHistoryItem) -> Unit,
    onClear: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.64f)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f))
                .clickable {}
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SCAN HISTORY", color = VektorText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "CLEAR",
                    color = VektorGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onClear).padding(8.dp)
                )
            }

            LazyColumn(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                items(items) { item ->
                    ScanHistoryRow(item = item, onSelect = onSelect, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryRow(
    item: ScanHistoryItem,
    onSelect: (ScanHistoryItem) -> Unit,
    onDelete: (ScanHistoryItem) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable { onSelect(item) }
        ) {
            Text(item.title, color = VektorText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scanHistorySummary(item.url), color = VektorMuted.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Text(scanHistoryRelativeTime(item.lastUsedAt), color = VektorGreen.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Text(
            "DEL",
            color = VektorMuted.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(start = 12.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f))
                .clickable { onDelete(item) }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun PlaybackScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val selected by viewModel.selectedChannel.collectAsState()
    val group by viewModel.selectedGroup.collectAsState()
    val videoShape by viewModel.playerController.videoShape.collectAsState()
    val videoAspectRatio by viewModel.playerController.videoAspectRatio.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    val groups = remember(channels) { listOf("All") + channels.map { it.group }.distinct().sorted() }
    val visible = remember(channels, group) { if (group == "All") channels else channels.filter { it.group == group } }

    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    DisposableEffect(fullscreen, videoShape) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        if (fullscreen) {
            activity?.requestedOrientation = when (videoShape) {
                VideoShape.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                VideoShape.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                VideoShape.SquareOrNeutral,
                VideoShape.Unknown -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSection(
                playerController = viewModel.playerController,
                fullscreen = true,
                onFullscreenChange = { fullscreen = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            PlaybackHeader(onReset = viewModel::resetToScan)
            AdaptiveVideoContainer(
                videoShape = videoShape,
                aspectRatio = videoAspectRatio
            ) { videoModifier ->
                VideoSection(
                    playerController = viewModel.playerController,
                    fullscreen = false,
                    onFullscreenChange = { fullscreen = it },
                    modifier = videoModifier
                )
            }
            if (videoShape == VideoShape.Portrait) {
                CurrentChannelCompact(
                    selected = selected,
                    onPrevious = viewModel::previousChannel,
                    onNext = viewModel::nextChannel
                )
            } else {
                CurrentChannelPanel(
                    selected = selected,
                    onPrevious = viewModel::previousChannel,
                    onNext = viewModel::nextChannel
                )
            }
            ChannelListPanel(
                groups = groups,
                selectedGroup = group,
                channels = visible,
                selected = selected,
                onGroup = viewModel::setSelectedGroup,
                onChannel = viewModel::select,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detected = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { codes ->
                                    val value = codes.firstOrNull()?.rawValue
                                    if (value != null && detected.compareAndSet(false, true)) {
                                        ProcessCameraProvider.getInstance(ctx).get().unbindAll()
                                        onCode(value)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            scanner.close()
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VektorLogo(Modifier.size(40.dp))
        Text("VEKTOR", color = VektorText, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun PlaybackHeader(onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VektorLogo(Modifier.size(36.dp))
        Text("VEKTOR", color = VektorText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Text("SCAN", color = VektorMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AdaptiveVideoContainer(
    videoShape: VideoShape,
    aspectRatio: Float?,
    content: @Composable (Modifier) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val ratio = when (videoShape) {
            VideoShape.Portrait -> aspectRatio?.coerceIn(0.46f, 0.70f) ?: (9f / 16f)
            VideoShape.SquareOrNeutral -> aspectRatio?.coerceIn(0.85f, 1.25f) ?: 1f
            VideoShape.Landscape -> aspectRatio?.coerceIn(1.25f, 2.40f) ?: (16f / 9f)
            VideoShape.Unknown -> 16f / 9f
        }
        val maxVideoHeight = if (videoShape == VideoShape.Portrait) screenHeight * 0.60f else maxWidth / ratio
        val videoModifier = if (videoShape == VideoShape.Portrait) {
            Modifier
                .heightIn(max = maxVideoHeight)
                .widthIn(max = maxWidth)
                .aspectRatio(ratio)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        }
        content(videoModifier)
    }
}

@Composable
private fun VideoSection(
    playerController: PlayerController,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = playerController.player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                controllerShowTimeoutMs = 3000
                setShowFastForwardButton(false)
                setShowRewindButton(false)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowShuffleButton(false)
                setShowSubtitleButton(false)
                setShowVrButton(false)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setFullscreenButtonClickListener { isFullscreen ->
                    onFullscreenChange(isFullscreen)
                }
                setFullscreenButtonState(fullscreen)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = {
            it.player = playerController.player
            it.useController = true
            it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            it.controllerShowTimeoutMs = 3000
            it.setShowFastForwardButton(false)
            it.setShowRewindButton(false)
            it.setShowNextButton(false)
            it.setShowPreviousButton(false)
            it.setShowShuffleButton(false)
            it.setShowSubtitleButton(false)
            it.setShowVrButton(false)
            it.setFullscreenButtonClickListener { isFullscreen ->
                onFullscreenChange(isFullscreen)
            }
            it.setFullscreenButtonState(fullscreen)
        },
        modifier = modifier.background(Color.Black)
    )
}

@Composable
private fun CurrentChannelCompact(
    selected: Channel?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text((selected?.group ?: "LIVE").uppercase(), color = VektorGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(
                selected?.title ?: "Loading Channel",
                color = VektorText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelActionButton("PREV", onPrevious, Modifier.widthIn(min = 66.dp), active = false)
            ChannelActionButton("NEXT", onNext, Modifier.widthIn(min = 66.dp), active = true)
        }
    }
}

@Composable
private fun CurrentChannelPanel(
    selected: Channel?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((selected?.group ?: "LIVE").uppercase(), color = VektorGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("  •  Now Playing", color = VektorMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text(
            selected?.title ?: "Loading Channel",
            color = VektorText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChannelActionButton("PREV", onPrevious, Modifier.weight(1f), active = false)
            ChannelActionButton("NEXT", onNext, Modifier.weight(1f), active = true)
        }
    }
}

@Composable
private fun ChannelActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean
) {
    Box(
        modifier
            .height(36.dp)
            .clickable(onClick = onClick)
            .background(if (active) VektorGreen else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (active) VektorGreen else Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.Black else VektorText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ScannerFrame(hasPermission: Boolean) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val offset by transition.animateFloat(
        initialValue = -140f,
        targetValue = 140f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "scanLine"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(290.dp).background(VektorGreen.copy(alpha = 0.06f))) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(VektorGreen, Offset(0f, size.height / 2 + offset), Offset(size.width, size.height / 2 + offset), strokeWidth = 2f)
                val l = 42.dp.toPx()
                val stroke = Stroke(width = 4f, cap = StrokeCap.Square)
                drawLine(VektorGreen, Offset.Zero, Offset(l, 0f), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset.Zero, Offset(0f, l), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(size.width, 0f), Offset(size.width - l, 0f), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(size.width, 0f), Offset(size.width, l), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(0f, size.height), Offset(l, size.height), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(0f, size.height), Offset(0f, size.height - l), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(size.width, size.height), Offset(size.width - l, size.height), strokeWidth = stroke.width)
                drawLine(VektorGreen, Offset(size.width, size.height), Offset(size.width, size.height - l), strokeWidth = stroke.width)
            }
        }
        Text(
            if (hasPermission) "● SCANNING..." else "● CAMERA ACCESS NEEDED",
            color = VektorGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
    }
}

@Composable
private fun ChannelListPanel(
    groups: List<String>,
    selectedGroup: String,
    channels: List<Channel>,
    selected: Channel?,
    onGroup: (String) -> Unit,
    onChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF101010))
            .border(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PLAYLIST", color = VektorText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("${channels.size} ITEMS", color = VektorMuted.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            items(groups) { group ->
                val active = group == selectedGroup
                Box(
                    Modifier
                        .clickable { onGroup(group) }
                        .background(if (active) VektorGreen else Color.White.copy(alpha = 0.06f))
                        .border(1.dp, if (active) VektorGreen else Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        group,
                        color = if (active) Color.Black else VektorMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        LazyColumn(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            items(channels) { channel ->
                ChannelRow(channel, channel == selected, onClick = { onChannel(channel) })
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .border(1.dp, if (selected) VektorGreen.copy(alpha = 0.5f) else Color.Transparent)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelCover(channel)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text("${channel.displayNumber} ${channel.title}", color = if (selected) VektorGreen else VektorText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.group, color = VektorMuted.copy(alpha = 0.55f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        if (selected) Text("▶", color = VektorGreen)
    }
}

@Composable
private fun ChannelCover(channel: Channel) {
    Box(
        Modifier
            .size(width = 70.dp, height = 44.dp)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            channel.fallbackLogoText,
            color = VektorMuted.copy(alpha = 0.75f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = "${channel.title} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun scanHistorySummary(url: String): String {
    val uri = Uri.parse(url)
    val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
    return "${uri.host ?: uri.scheme ?: "link"}$path"
}

private fun scanHistoryRelativeTime(lastUsedAt: Long): String {
    val rawDuration = Duration.between(Instant.ofEpochMilli(lastUsedAt), Instant.now())
    val duration = if (rawDuration.isNegative) Duration.ZERO else rawDuration
    return when {
        duration.toMinutes() < 1 -> "now"
        duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
        duration.toDays() < 1 -> "${duration.toHours()}h ago"
        else -> "${duration.toDays()}d ago"
    }
}

@Composable
private fun StatusOverlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color.Black.copy(alpha = 0.78f)).border(1.dp, VektorGreen.copy(alpha = 0.28f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = VektorGreen, modifier = Modifier.size(24.dp))
            Text(message.uppercase(), color = VektorGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun VektorLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.18f)
            lineTo(size.width * 0.5f, size.height * 0.84f)
            lineTo(size.width * 0.82f, size.height * 0.18f)
        }
        drawPath(path, color = VektorGreen, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Square))
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f))) {
        val spacing = 40.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(VektorGreen.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height))
            x += spacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(VektorGreen.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y))
            y += spacing
        }
    }
}
