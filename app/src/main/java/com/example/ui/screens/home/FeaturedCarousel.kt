package com.example.ui.screens.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ImageUrlUtils
import com.example.data.model.WorkCard
import com.example.ui.components.ShimmerBox
import com.example.ui.theme.ScoreStarGold
import kotlinx.coroutines.delay

@Composable
fun FeaturedCarousel(
    items: List<WorkCard>,
    onItemClick: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLoading && items.isEmpty()) {
        ShimmerBox(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(20.dp)
        )
        return
    }

    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current

    // Pause autoplay when not resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Autoplay 6s, paused while user is scrolling (isScrollInProgress) and when screen not RESUMED
    LaunchedEffect(pagerState, items.size, isResumed) {
        while (true) {
            delay(6000L)
            if (items.size > 1 && isResumed && !pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .testTag("featured_carousel_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentWork = items[page]
                val placeholderColor = ImageUrlUtils.parseColorHex(currentWork.coverColor)
                val hasBanner = !currentWork.banner.isNullOrBlank()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(placeholderColor)
                        .clickable { onItemClick(currentWork.id) }
                ) {
                    if (hasBanner) {
                        // Banner exists: display full banner with Crop
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentWork.banner)
                                .crossfade(true)
                                .size(1080, 720)
                                .build(),
                            contentDescription = currentWork.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Only cover exists: full cover Fit on darkened background (alpha ~0.4)
                        // blur() only on Android 12+ (API 31); on below, only darkened image without blur
                        val bgBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier
                                .fillMaxSize()
                                .blur(20.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentWork.cover)
                                    .crossfade(true)
                                    .size(360, 540)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = bgBlurModifier
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.40f))
                            )
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentWork.cover)
                                    .crossfade(true)
                                    .size(720, 1080)
                                    .build(),
                                contentDescription = currentWork.displayTitle,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // Dark gradient ONLY in bottom 45%
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.45f to Color.Transparent,
                                        0.65f to Color.Black.copy(alpha = 0.55f),
                                        1.0f to Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Content in bottom: Title (max 2 lines, bold) + chips (type, score, year)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 16.dp, bottom = 26.dp)
                    ) {
                        Text(
                            text = currentWork.displayTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Up to 3 small chips: tipo, nota, ano
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Chip 1: Tipo
                            HeroSmallChip(text = currentWork.typeLabel, isHighlight = true)

                            // Chip 2: Nota
                            if (currentWork.avgScore != null) {
                                HeroSmallChip(
                                    text = "★ ${currentWork.scoreFormatted}",
                                    isScore = true
                                )
                            }

                            // Chip 3: Ano
                            val year = currentWork.releaseYear
                            if (!year.isNullOrBlank()) {
                                HeroSmallChip(text = year)
                            }
                        }
                    }
                }
            }

            // Indicator dots centered at bottom of container
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.indices.forEach { index ->
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSmallChip(
    text: String,
    isHighlight: Boolean = false,
    isScore: Boolean = false
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isScore -> Color(0xFF1E293B).copy(alpha = 0.85f)
                    isHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.20f)
                }
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isScore) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = ScoreStarGold,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = if (isScore) text.removePrefix("★ ") else text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
