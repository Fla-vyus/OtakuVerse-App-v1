package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.data.model.ImageUrlUtils
import com.example.data.model.WorkCard
import com.example.ui.theme.ScoreStarGold

/**
 * Clean Home Work Card (Etapa 1.5):
 * - Aspect ratio 2:3 (~120dp wide, ~180dp tall)
 * - 14dp rounded corners
 * - Small score badge in top right
 * - Title (1-2 lines) below in small font (12-13sp)
 * - NO genres line, NO synopsis
 * - Optional type badge for sections that mix types
 * - Optional agenda badge for "Agenda do mês"
 */
@Composable
fun WorkCardItem(
    work: WorkCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 120.dp,
    showTypeBadge: Boolean = false,
    showAgendaBadge: Boolean = false
) {
    val context = LocalContext.current
    val mediumUrl = ImageUrlUtils.getOptimizedCoverUrl(work.cover, isListThumbnail = true)
    val placeholderColor = ImageUrlUtils.parseColorHex(work.coverColor)

    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
            .testTag("home_work_card_${work.id}")
    ) {
        // Cover container (aspect ratio 2:3, corners 14dp)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor)
            ) {
                // Initial letter placeholder
                Text(
                    text = work.displayTitle.take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Optimized AsyncImage
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediumUrl)
                        .crossfade(true)
                        .scale(Scale.FILL)
                        .size(360, 540)
                        .build(),
                    contentDescription = work.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top left date badge for "Agenda do mês"
                if (showAgendaBadge) {
                    val badge = work.agendaBadge
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge.dateText,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Top right small score badge
                if (work.avgScore != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = ScoreStarGold,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = work.scoreFormatted,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Type badge on bottom start
                if (showTypeBadge || showAgendaBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = work.typeLabel,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title: 1-2 lines, small font (12-13sp), no genres, no synopsis
        Text(
            text = work.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // Tag text below title for agenda items (never on image)
        if (showAgendaBadge) {
            val badge = work.agendaBadge
            if (badge != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge.tagText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

/**
 * PWA Pattern Explore Work Card (Etapa 2.6):
 * - Capa 2:3 com cantos arredondados (12dp)
 * - Coração (favorito) e marcador (lista de leitura) no canto superior direito sobre a imagem
 * - Faixa inferior sobre a capa com tipo e nota (estrela amarela + nota)
 * - Título abaixo (1 a 2 linhas, peso médio)
 * - Gêneros principais (máx. 2) e popularidade (ex.: "♥ 14.2k")
 */
@Composable
fun ExploreWorkCardItem(
    work: WorkCard,
    isFavorite: Boolean,
    isPlanningOrReading: Boolean,
    blurIfAdult: Boolean = true,
    showAdultGenres: Boolean = false,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediumUrl = ImageUrlUtils.getOptimizedCoverUrl(work.cover, isListThumbnail = true)
    val placeholderColor = ImageUrlUtils.parseColorHex(work.coverColor)
    var isRevealed by remember(work.id) { mutableStateOf(false) }
    val shouldBlur = work.is18Plus && blurIfAdult && !isRevealed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("explore_work_card_${work.id}")
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor)
            ) {
                // Initial fallback
                Text(
                    text = work.displayTitle.take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Cover Image (Blurred if 18+ and setting is on)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediumUrl)
                        .crossfade(true)
                        .scale(Scale.FILL)
                        .size(360, 540)
                        .build(),
                    contentDescription = work.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (shouldBlur) Modifier.blur(16.dp) else Modifier)
                )

                if (shouldBlur) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDC2626).copy(alpha = 0.92f))
                                .clickable { isRevealed = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "18+",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Top right overlay: Heart (Favorite) and Bookmark (Planning/Reading)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Bookmark
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .clickable(onClick = onToggleBookmark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlanningOrReading) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Salvar na biblioteca",
                            tint = if (isPlanningOrReading) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Heart
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favoritar",
                            tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Bottom strip over cover with type and score
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = work.typeLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (work.avgScore != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = ScoreStarGold,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = work.scoreFormatted,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title below (minLines = 2 for uniform card height, medium weight)
        Text(
            text = work.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom row: Main genres (max 2) + popularity count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val genresText = (if (showAdultGenres) work.genres else work.genres.filter { it !in listOf("Adult", "Hentai", "Erotica", "Smut") })
                .take(2).joinToString(" • ")

            Text(
                text = genresText.ifEmpty { work.status },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            val popText = work.popularCountFormatted
            if (popText.isNotEmpty()) {
                Text(
                    text = "♥ $popText",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
