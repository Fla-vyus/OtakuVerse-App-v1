package com.example.ui.screens.profile

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.OtakuScreenHeader
import com.example.util.CropMath
import com.example.util.ImageCompressionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onCropSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.clearCrop()
        onBack()
    }

    val context = LocalContext.current
    val cropUri by viewModel.pendingCropUri.collectAsStateWithLifecycle()
    val cropType by viewModel.pendingCropType.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingCrop.collectAsStateWithLifecycle()

    // Persistent transforms stored in ViewModel so device rotation/configuration changes survive
    val userZoom by viewModel.cropUserZoom.collectAsStateWithLifecycle()
    val panOffsetX by viewModel.cropPanOffsetX.collectAsStateWithLifecycle()
    val panOffsetY by viewModel.cropPanOffsetY.collectAsStateWithLifecycle()

    // Profile info for live preview
    val nickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val bio by viewModel.userBio.collectAsStateWithLifecycle()
    val avatarPath by viewModel.avatarImagePath.collectAsStateWithLifecycle()
    val bannerPath by viewModel.bannerImagePath.collectAsStateWithLifecycle()
    val profileAccentHex by viewModel.profileAccentColor.collectAsStateWithLifecycle()

    var decodedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(true) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var showPreviewModal by remember { mutableStateOf(false) }

    // Decode image on background thread with downsampling (max 2048px) and EXIF rotation
    LaunchedEffect(cropUri) {
        val uri = cropUri
        if (uri == null) {
            decodeError = "Nenhuma imagem selecionada."
            isLoadingBitmap = false
            return@LaunchedEffect
        }
        isLoadingBitmap = true
        decodeError = null
        val bitmap = withContext(Dispatchers.IO) {
            try {
                ImageCompressionUtils.decodeSampledBitmap(context, uri, ImageCompressionUtils.MAX_DECODE_DIMENSION)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            decodedBitmap = bitmap
            isLoadingBitmap = false
        } else {
            decodeError = "Não foi possível carregar a imagem. O arquivo pode ser inválido ou a memória é insuficiente."
            isLoadingBitmap = false
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_crop_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Header with back navigation
            OtakuScreenHeader(
                title = if (cropType == CropType.AVATAR) "Recortar Foto de Perfil" else "Recortar Capa do Perfil",
                onBack = {
                    viewModel.clearCrop()
                    onBack()
                }
            )

            if (isLoadingBitmap) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Otimizando imagem...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (decodeError != null || decodedBitmap == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = decodeError ?: "Erro ao processar imagem.",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                viewModel.clearCrop()
                                onBack()
                            }
                        ) {
                            Text("Voltar")
                        }
                    }
                }
            } else {
                val bitmap = decodedBitmap!!

                // Viewport area with gestures and cutout frame
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidthPx = constraints.maxWidth.toFloat()
                    val containerHeightPx = constraints.maxHeight.toFloat()

                    // Compute frame dimensions: Avatar = Circle 1:1, Banner = 16:9
                    val (frameWidthPx, frameHeightPx) = remember(containerWidthPx, containerHeightPx, cropType) {
                        if (cropType == CropType.AVATAR) {
                            val side = min(containerWidthPx * 0.82f, containerHeightPx * 0.82f)
                            Pair(side, side)
                        } else {
                            val targetW = containerWidthPx - 32.dp.value * 2
                            val targetH = targetW / (16f / 9f)
                            if (targetH > containerHeightPx * 0.82f) {
                                val adjustedH = containerHeightPx * 0.82f
                                Pair(adjustedH * (16f / 9f), adjustedH)
                            } else {
                                Pair(targetW, targetH)
                            }
                        }
                    }

                    // Minimum scale ensures image ALWAYS covers the frame completely on all sides
                    val minScale = remember(bitmap.width, bitmap.height, frameWidthPx, frameHeightPx) {
                        CropMath.calculateMinScale(bitmap.width, bitmap.height, frameWidthPx, frameHeightPx)
                    }

                    val currentScale = minScale * userZoom

                    // Max panning offsets calculated based on current scale
                    val (maxOffsetX, maxOffsetY) = remember(bitmap.width, bitmap.height, frameWidthPx, frameHeightPx, currentScale) {
                        CropMath.calculateMaxOffsets(bitmap.width, bitmap.height, frameWidthPx, frameHeightPx, currentScale)
                    }

                    val clampedOffsetX = panOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
                    val clampedOffsetY = panOffsetY.coerceIn(-maxOffsetY, maxOffsetY)

                    // Touch gestures handling pan and pinch-to-zoom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(minScale, maxOffsetX, maxOffsetY) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    val newZoom = (userZoom * zoomChange).coerceIn(1f, 4f)
                                    val newOffsetX = panOffsetX + pan.x
                                    val newOffsetY = panOffsetY + pan.y
                                    viewModel.updateCropTransform(newZoom, newOffsetX, newOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Render full image with pan and scale transform
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Visualização da Imagem",
                            modifier = Modifier
                                .size(
                                    width = (bitmap.width / LocalContext.current.resources.displayMetrics.density).dp,
                                    height = (bitmap.height / LocalContext.current.resources.displayMetrics.density).dp
                                )
                                .graphicsLayer {
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    translationX = clampedOffsetX
                                    translationY = clampedOffsetY
                                }
                        )

                        // Dark mask overlay with transparent cutout frame
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = 0.99f) // enables BlendMode.Clear for the cutout
                        ) {
                            // Dark surrounding scrim
                            drawRect(color = Color.Black.copy(alpha = 0.72f))

                            if (cropType == CropType.AVATAR) {
                                val radius = frameWidthPx / 2f
                                // Cutout transparent circle
                                drawCircle(
                                    color = Color.Transparent,
                                    radius = radius,
                                    center = center,
                                    blendMode = BlendMode.Clear
                                )
                                // Crisp white border
                                drawCircle(
                                    color = Color.White,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            } else {
                                val left = (size.width - frameWidthPx) / 2f
                                val top = (size.height - frameHeightPx) / 2f
                                // Cutout transparent 16:9 rectangle
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(left, top),
                                    size = Size(frameWidthPx, frameHeightPx),
                                    blendMode = BlendMode.Clear
                                )
                                // Crisp white border
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(left, top),
                                    size = Size(frameWidthPx, frameHeightPx),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }

                    // Bottom controls panel inside the viewport
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f), Color.Black)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        // Zoom Slider with ZoomOut / ZoomIn icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Diminuir Zoom",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = userZoom,
                                onValueChange = { newZoom ->
                                    viewModel.updateCropTransform(newZoom, panOffsetX, panOffsetY)
                                },
                                valueRange = 1f..4f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.weight(1f).testTag("crop_zoom_slider")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Aumentar Zoom",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action buttons row: Redefinir, Pré-visualizar, Usar esta imagem
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetCropTransform() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("crop_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Redefinir", color = Color.White, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showPreviewModal = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("crop_preview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Preview,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prévia", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val cropRect = CropMath.calculateCropRect(
                                        imageWidth = bitmap.width,
                                        imageHeight = bitmap.height,
                                        frameWidth = frameWidthPx,
                                        frameHeight = frameHeightPx,
                                        scale = currentScale,
                                        offsetX = clampedOffsetX,
                                        offsetY = clampedOffsetY
                                    )
                                    viewModel.applyCrop(bitmap, cropRect) { success ->
                                        if (success) {
                                            onCropSuccess()
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("crop_apply_button")
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Usar esta imagem", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp).navigationBarsPadding())
                    }

                    // Modal de Pré-visualização do perfil final antes de salvar
                    if (showPreviewModal) {
                        val cropRect = remember(userZoom, clampedOffsetX, clampedOffsetY) {
                            CropMath.calculateCropRect(
                                imageWidth = bitmap.width,
                                imageHeight = bitmap.height,
                                frameWidth = frameWidthPx,
                                frameHeight = frameHeightPx,
                                scale = currentScale,
                                offsetX = clampedOffsetX,
                                offsetY = clampedOffsetY
                            )
                        }

                        // Generate a temporary cropped preview bitmap
                        val previewCroppedBitmap = remember(cropRect) {
                            try {
                                val safeX = cropRect.left.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                                val safeY = cropRect.top.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                                val safeW = cropRect.width.coerceIn(1, bitmap.width - safeX)
                                val safeH = cropRect.height.coerceIn(1, bitmap.height - safeY)
                                Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        ModalBottomSheet(
                            onDismissRequest = { showPreviewModal = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Pré-visualização do Perfil",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Veja como sua foto e capa ficarão no perfil antes de confirmar.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Live Simulated Profile Header Card
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        // Banner 16:9
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF3B82F6), Color(0xFF0F172A))
                                                    )
                                                )
                                        ) {
                                            if (cropType == CropType.BANNER && previewCroppedBitmap != null) {
                                                Image(
                                                    bitmap = previewCroppedBitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else if (bannerPath != null && File(bannerPath!!).exists()) {
                                                AsyncImage(
                                                    model = File(bannerPath!!),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            // Dark gradient overlay
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                                        )
                                                    )
                                            )

                                            // Avatar 88dp
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(start = 14.dp, bottom = 10.dp)
                                                    .size(88.dp)
                                                    .shadow(6.dp, CircleShape)
                                                    .clip(CircleShape)
                                                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (cropType == CropType.AVATAR && previewCroppedBitmap != null) {
                                                    Image(
                                                        bitmap = previewCroppedBitmap.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else if (avatarPath != null && File(avatarPath!!).exists()) {
                                                    AsyncImage(
                                                        model = File(avatarPath!!),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    val initial = nickname.trim().take(1).uppercase().ifBlank { "O" }
                                                    Text(
                                                        text = initial,
                                                        fontSize = 36.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        // Nickname and Bio
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = nickname,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (bio.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = bio,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { showPreviewModal = false },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Voltar ao ajuste")
                                }
                                Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
                            }
                        }
                    }
                }
            }
        }
    }
}
