package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WorkCard
import com.example.ui.components.ShimmerBox
import com.example.ui.components.WorkCardItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onWorkClick: (String) -> Unit,
    onViewAllSection: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadExtraSections()
    }

    if (state.isTokenError || state.errorMessage?.contains("não configurad", ignoreCase = true) == true) {
        val errorKind = if (state.errorMessage?.contains("não configurad", ignoreCase = true) == true) {
            com.example.ui.components.OtakuErrorKind.NoToken
        } else {
            com.example.ui.components.OtakuErrorKind.InvalidToken
        }
        com.example.ui.components.OtakuErrorState(
            kind = errorKind,
            onRetry = { viewModel.loadData(refresh = true) },
            onOpenProfile = onNavigateToProfile,
            modifier = modifier
        )
        return
    }

    val hasAnyContent = state.heroItems.isNotEmpty() || state.recentReleases.isNotEmpty() || state.topPopularity.isNotEmpty()

    if (!hasAnyContent && !state.isLoading) {
        com.example.ui.components.OtakuErrorState(
            kind = com.example.ui.components.OtakuErrorKind.NetworkOrServer(state.errorMessage),
            onRetry = { viewModel.loadData(refresh = true) },
            onOpenProfile = onNavigateToProfile,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Offline / Cache notice banner
        if (state.isOffline && hasAnyContent) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exibindo dados offline do catálogo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = { viewModel.loadData(refresh = true) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Tentar reconectar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero Banner (~260dp, 8 works, clean styling)
        item {
            FeaturedCarousel(
                items = state.heroItems,
                onItemClick = onWorkClick,
                isLoading = state.isLoading,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 1. "Lançamentos recentes"
        item {
            HomeSectionRow(
                title = "Lançamentos recentes",
                items = state.recentReleases,
                isLoading = state.isLoading && state.recentReleases.isEmpty(),
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("recent_releases") }
            )
        }

        // 2. "Bombando: novos hits"
        item {
            HomeSectionRow(
                title = "Bombando: novos hits",
                items = state.newHits,
                isLoading = state.isLoading && state.newHits.isEmpty(),
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("new_hits") }
            )
        }

        // 3. "Agenda do mês"
        item {
            HomeSectionRow(
                title = "Agenda do mês",
                items = state.monthSchedule,
                isLoading = state.isLoading && state.monthSchedule.isEmpty(),
                showTypeBadge = true,
                showAgendaBadge = true,
                viewAllLabel = "Ver agenda completa",
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("schedule") }
            )
        }

        // 4. "Top popularidade"
        item {
            HomeSectionRow(
                title = "Top popularidade",
                items = state.topPopularity,
                isLoading = state.isLoading && state.topPopularity.isEmpty(),
                showTypeBadge = false,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("top_anime") }
            )
        }

        // 5. "Manhwas em alta"
        item {
            HomeSectionRow(
                title = "Manhwas em alta",
                items = state.trendingManhwas,
                isLoading = state.isLoading && state.trendingManhwas.isEmpty(),
                showTypeBadge = false,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("trending_manhwa") }
            )
        }

        // 6. "Mangás mais populares"
        item {
            HomeSectionRow(
                title = "Mangás mais populares",
                items = state.popularMangas,
                isLoading = state.isLoading && state.popularMangas.isEmpty(),
                showTypeBadge = false,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("popular_manga") }
            )
        }

        // 7. "Melhores notas em mangás e manhwas"
        item {
            HomeSectionRow(
                title = "Melhores notas em mangás e manhwas",
                items = state.topRatedMangaManhwa,
                isLoading = state.isLoading && state.topRatedMangaManhwa.isEmpty(),
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("top_rated_manga_manhwa") }
            )
        }

        // 8. "Para maratonar"
        item {
            HomeSectionRow(
                title = "Para maratonar",
                items = state.bingeItems,
                isLoading = state.isLoading && state.bingeItems.isEmpty(),
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("binge") }
            )
        }

        // 9. "Isekai e reencarnação"
        item {
            HomeSectionRow(
                title = "Isekai e reencarnação",
                items = state.isekaiReincarnation,
                isLoading = state.isLoading && state.isekaiReincarnation.isEmpty(),
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("isekai") }
            )
        }

        // 10. "Manhuas em destaque"
        item {
            HomeSectionRow(
                title = "Manhuas em destaque",
                items = state.featuredManhuas,
                isLoading = state.isLoading && state.featuredManhuas.isEmpty(),
                showTypeBadge = false,
                onWorkClick = onWorkClick,
                onViewAll = { onViewAllSection("featured_manhua") }
            )
        }

        // Seções Extras da Início (HomeSection da nuvem / cache)
        items(
            items = state.extraSections,
            key = { "extra_sec_${it.key}_${it.position}" }
        ) { section ->
            HomeSectionRow(
                title = section.title,
                items = section.items,
                isLoading = false,
                showTypeBadge = true,
                onWorkClick = onWorkClick,
                onViewAll = null
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    title: String,
    items: List<WorkCard>,
    isLoading: Boolean,
    showTypeBadge: Boolean = false,
    showAgendaBadge: Boolean = false,
    viewAllLabel: String = "Ver todos",
    onWorkClick: (String) -> Unit,
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isLoading && items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (onViewAll != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewAll)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = viewAllLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal items or Shimmer
        if (isLoading && items.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) {
                    Column(modifier = Modifier.width(120.dp)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { work ->
                    WorkCardItem(
                        work = work,
                        cardWidth = 120.dp,
                        showTypeBadge = showTypeBadge,
                        showAgendaBadge = showAgendaBadge,
                        onClick = { onWorkClick(work.id) }
                    )
                }
            }
        }
    }
}
