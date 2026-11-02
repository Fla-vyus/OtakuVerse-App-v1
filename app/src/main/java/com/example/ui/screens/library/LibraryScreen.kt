package com.example.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.data.local.LibraryEntity
import com.example.data.model.ImageUrlUtils
import com.example.data.model.WorkCard
import com.example.data.repository.LibraryStats
import com.example.data.repository.LibraryTab
import com.example.ui.components.WorkCardItem
import com.example.ui.theme.ScoreStarGold
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onWorkClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val works by viewModel.displayedWorks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val tabCounts by viewModel.tabCounts.collectAsStateWithLifecycle()
    val topSuggestions by viewModel.topSuggestions.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var ratingEntity by remember { mutableStateOf<LibraryEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.recentlyDeleted.collect { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = "\"${deleted.title}\" removido da biblioteca.",
                actionLabel = "Desfazer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoRemove(deleted)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columnsCount = if (maxWidth < 340.dp) 2 else 3

            Column(modifier = Modifier.fillMaxSize()) {
                // Header unificado OtakuScreenHeader
                com.example.ui.components.OtakuScreenHeader(
                    title = "Biblioteca"
                )

                // Subtitle as supporting text (12sp)
                val totalStr = if (stats.totalCount == 1) "1 obra" else "${stats.totalCount} obras"
                val completedStr = if (stats.completedCount == 1) "1 concluída" else "${stats.completedCount} concluídas"
                val avgRatingStr = stats.averagePersonalRating?.let {
                    "média ${String.format(java.util.Locale.getDefault(), "%.1f", it)} ★"
                } ?: "média —"
                Text(
                    text = "$totalStr · $completedStr · $avgRatingStr",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )

                // 3.2 Abas com contadores (Todos, Favoritos, Quero ver/ler, Em andamento, Concluídos)
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 14.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    LibraryTab.entries.forEach { tab ->
                        val isSelected = tab == state.selectedTab
                        val count = tabCounts[tab] ?: 0
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                Text(
                                    text = "${tab.title} ($count)",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("library_tab_${tab.name}")
                        )
                    }
                }

                // Search & Sort bar (48dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Buscar na biblioteca...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Ordenar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sort.title,
                                            fontWeight = if (sort == state.selectedSort) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sort == state.selectedSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSelectSort(sort)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3.5 Empty State with 3 popular suggestions if library is empty
                if (works.isEmpty()) {
                    EmptyLibraryView(
                        selectedTab = state.selectedTab,
                        topSuggestions = topSuggestions,
                        onExploreClick = onExploreClick,
                        onWorkClick = onWorkClick
                    )
                } else {
                    // Content Grid (3 columns, 2:3 cards)
                    if (state.selectedTab == LibraryTab.ALL && state.searchQuery.isBlank()) {
                        // 3.3 Na aba "Todos", organize em seções com cabeçalho simples:
                        // "Em andamento" primeiro, depois "Planejo ver/ler", "Concluídos", "Outros favoritados"
                        val reading = works.filter { it.status == "READING" }
                        val planning = works.filter { it.status == "PLANNING" }
                        val completed = works.filter { it.status == "COMPLETED" }
                        val favoritesNoStatus = works.filter { it.isFavorite && (it.status.isNullOrBlank() || it.status == "null") }
                        val others = works.filter { it !in reading && it !in planning && it !in completed && it !in favoritesNoStatus }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnsCount),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (reading.isNotEmpty()) {
                                item(span = { GridItemSpan(columnsCount) }) {
                                    SectionHeader("Em andamento", reading.size)
                                }
                                items(reading, key = { it.id }) { item ->
                                    LibraryGridCard(
                                        item = item,
                                        onClick = { onWorkClick(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                        onSetStatus = { viewModel.setStatus(item.id, it) },
                                        onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                        onOpenRating = { ratingEntity = item },
                                        onRemove = { viewModel.removeWork(item) }
                                    )
                                }
                            }

                            if (planning.isNotEmpty()) {
                                item(span = { GridItemSpan(columnsCount) }) {
                                    SectionHeader("Planejo ver/ler", planning.size)
                                }
                                items(planning, key = { it.id }) { item ->
                                    LibraryGridCard(
                                        item = item,
                                        onClick = { onWorkClick(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                        onSetStatus = { viewModel.setStatus(item.id, it) },
                                        onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                        onOpenRating = { ratingEntity = item },
                                        onRemove = { viewModel.removeWork(item) }
                                    )
                                }
                            }

                            if (completed.isNotEmpty()) {
                                item(span = { GridItemSpan(columnsCount) }) {
                                    SectionHeader("Concluídos", completed.size)
                                }
                                items(completed, key = { it.id }) { item ->
                                    LibraryGridCard(
                                        item = item,
                                        onClick = { onWorkClick(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                        onSetStatus = { viewModel.setStatus(item.id, it) },
                                        onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                        onOpenRating = { ratingEntity = item },
                                        onRemove = { viewModel.removeWork(item) }
                                    )
                                }
                            }

                            if (favoritesNoStatus.isNotEmpty()) {
                                item(span = { GridItemSpan(columnsCount) }) {
                                    SectionHeader("Favoritos sem lista", favoritesNoStatus.size)
                                }
                                items(favoritesNoStatus, key = { it.id }) { item ->
                                    LibraryGridCard(
                                        item = item,
                                        onClick = { onWorkClick(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                        onSetStatus = { viewModel.setStatus(item.id, it) },
                                        onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                        onOpenRating = { ratingEntity = item },
                                        onRemove = { viewModel.removeWork(item) }
                                    )
                                }
                            }

                            if (others.isNotEmpty()) {
                                item(span = { GridItemSpan(columnsCount) }) {
                                    SectionHeader("Outros", others.size)
                                }
                                items(others, key = { it.id }) { item ->
                                    LibraryGridCard(
                                        item = item,
                                        onClick = { onWorkClick(item.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                        onSetStatus = { viewModel.setStatus(item.id, it) },
                                        onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                        onOpenRating = { ratingEntity = item },
                                        onRemove = { viewModel.removeWork(item) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard tab or search filter
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnsCount),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(works, key = { it.id }) { item ->
                                LibraryGridCard(
                                    item = item,
                                    onClick = { onWorkClick(item.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                    onSetStatus = { viewModel.setStatus(item.id, it) },
                                    onUpdateProgress = { delta -> viewModel.updateProgress(item.id, item.currentProgress + delta) },
                                    onOpenRating = { ratingEntity = item },
                                    onRemove = { viewModel.removeWork(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rating Dialog
    ratingEntity?.let { entity ->
        var score by remember { mutableIntStateOf(entity.userRating ?: 8) }
        AlertDialog(
            onDismissRequest = { ratingEntity = null },
            title = { Text("Nota Pessoal (1 a 10 ★)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(text = entity.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (i * 2 <= score) ScoreStarGold else Color.Gray.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { score = i * 2 }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "$score / 10", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ScoreStarGold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRating(entity.id, score)
                    ratingEntity = null
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { ratingEntity = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CompactLibraryHeader(stats: LibraryStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // <= 64dp de altura (Etapa 3.1)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Minha Biblioteca",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            val totalStr = if (stats.totalCount == 1) "1 obra" else "${stats.totalCount} obras"
            val completedStr = if (stats.completedCount == 1) "1 concluída" else "${stats.completedCount} concluídas"
            val avgRatingStr = stats.averagePersonalRating?.let {
                "nota média ${String.format(java.util.Locale.getDefault(), "%.1f", it)} ★"
            } ?: "nota média —"
            Text(
                text = "$totalStr · $completedStr · $avgRatingStr",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 3.4 Card da Biblioteca (grid de 3 colunas, proporção 2:3):
 * - Capa com selo de status no canto que ao tocar abre menu rápido
 * - Barra de progresso discreta na base da capa e botões rápidos "+1" e "-1"
 */
@Composable
private fun LibraryGridCard(
    item: LibraryEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetStatus: (String?) -> Unit,
    onUpdateProgress: (Int) -> Unit,
    onOpenRating: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val mediumUrl = ImageUrlUtils.getOptimizedCoverUrl(item.cover, isListThumbnail = true)
    val placeholderColor = ImageUrlUtils.parseColorHex(item.coverColor)
    var showMenu by remember { mutableStateOf(false) }

    val statusLabel = when (item.status) {
        "READING" -> if (item.workType.equals("anime", ignoreCase = true)) "Assistindo" else "Lendo"
        "PLANNING" -> "Quero ver/ler"
        "COMPLETED" -> "Concluído"
        else -> if (item.isFavorite) "Favorito" else "Salvo"
    }

    val statusColor = when (item.status) {
        "READING" -> MaterialTheme.colorScheme.primary
        "COMPLETED" -> Color(0xFF10B981)
        "PLANNING" -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("library_card_${item.id}")
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
                // Initial
                Text(
                    text = item.title.take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Cover Image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediumUrl)
                        .crossfade(true)
                        .scale(Scale.FILL)
                        .size(360, 540)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Status badge on top start with dropdown menu
                Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.90f))
                            .clickable { showMenu = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Em andamento (Lendo/Assistindo)") },
                            onClick = { onSetStatus("READING"); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Quero ver/ler (Planejando)") },
                            onClick = { onSetStatus("PLANNING"); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Concluído") },
                            onClick = { onSetStatus("COMPLETED"); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Dar nota (1 a 10 ★)") },
                            onClick = { onOpenRating(); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Remover da biblioteca", color = MaterialTheme.colorScheme.error) },
                            onClick = { onRemove(); showMenu = false }
                        )
                    }
                }

                // Favorite heart on top end
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (item.isFavorite) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Discrete Progress Bar at base of cover (e.g. 12/24 ep or 45/120 cap)
                val total = item.totalProgress
                val current = item.currentProgress
                if (total != null && total > 0) {
                    val progressFraction = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp
        )

        // Quick progress update row: "-1", "45/120 cap", "+1"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalStr = item.totalProgress?.let { "/$it" } ?: ""
            val unit = if (item.workType.equals("anime", ignoreCase = true)) "ep" else "cap"

            Text(
                text = "${item.currentProgress}$totalStr $unit",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // -1 button
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onUpdateProgress(-1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Menos 1",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // +1 button
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onUpdateProgress(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Mais 1",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    selectedTab: LibraryTab,
    topSuggestions: List<WorkCard>,
    onExploreClick: () -> Unit,
    onWorkClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CollectionsBookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sua biblioteca está vazia",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Acompanhe seus animes e mangás favoritos, registre seu progresso e marque obras para ler mais tarde.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onExploreClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Explorar catálogo")
        }

        if (topSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Sugestões populares do momento",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topSuggestions.take(3).forEach { work ->
                    WorkCardItem(
                        work = work,
                        cardWidth = 100.dp,
                        onClick = { onWorkClick(work.id) }
                    )
                }
            }
        }
    }
}
