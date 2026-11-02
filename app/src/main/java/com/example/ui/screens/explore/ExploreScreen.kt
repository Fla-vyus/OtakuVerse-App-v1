package com.example.ui.screens.explore

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.turso.TursoQueryBuilder
import com.example.ui.components.ExploreWorkCardItem
import com.example.ui.components.ShimmerBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onWorkClick: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blurAdultCovers by viewModel.blurAdultCovers.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Infinite scroll: 30 in 30
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.canLoadMore && !state.isLoadingMore && !state.hasLoadMoreError) {
            viewModel.loadMore()
        }
    }

    var sortDropdownExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("explore_screen")
    ) {
        // Grid columns: 3 columns in standard widths (>= 340dp), 2 in very narrow screens (Etapa 2.6)
        val columnsCount = if (maxWidth < 340.dp) 2 else 3

        Column(modifier = Modifier.fillMaxSize()) {
            com.example.ui.components.OtakuScreenHeader(
                title = "Explorar"
            )

            // Linha 1: Campo de busca de 48dp (texto 14sp) + Botão "Filtros" ao lado com selo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.criteria.query,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            text = "Buscar por título, autor…",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.criteria.query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChanged("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar busca",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("explore_search_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { viewModel.openFilterSheet() },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("explore_filter_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.activeFilterCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        contentColor = if (state.activeFilterCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.activeFilterCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (state.activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Filtros",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.activeFilterCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.activeFilterCount}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Linha 2: Chips de tipo de obra (36dp, 13sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.typeOptions.forEach { type ->
                    val isSelected = if (type == "Todos") {
                        state.criteria.types.isEmpty() || state.criteria.types.contains("Todos")
                    } else {
                        state.criteria.types.contains(type)
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onToggleType(type) },
                        label = { Text(type, fontSize = 13.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            // Linha 2.1: Chips rápidos de temas / demografias (múltipla escolha)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.quickTagOptions.forEach { tag ->
                    val isSelected = state.criteria.tags.contains(tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onToggleQuickTag(tag) },
                        label = { Text(tag, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Linha 3: Contagem real à esquerda e seletor de ordenação à direita
            val numberFormat = remember { java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("pt-BR")) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isLoadingCount) {
                        "Calculando..."
                    } else {
                        val count = state.totalCount ?: state.works.size
                        val formatted = numberFormat.format(count)
                        if (count == 1) "$formatted obra" else "$formatted obras"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sort Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sortDropdownExpanded = true }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentSortLabel = viewModel.sortOptions.firstOrNull { it.first == state.criteria.sortBy }?.second ?: "Mais populares"
                        Text(
                            text = currentSortLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sortDropdownExpanded,
                        onDismissRequest = { sortDropdownExpanded = false }
                    ) {
                        viewModel.sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (key == state.criteria.sortBy) FontWeight.Bold else FontWeight.Normal,
                                        color = if (key == state.criteria.sortBy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.onSelectSort(key)
                                    sortDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Linha 4: Chips dos filtros ativos em linha própria, com "Limpar tudo" no fim
            val activeTags = buildList {
                addAll(state.criteria.types.filter { it != "Todos" })
                addAll(state.criteria.tags)
                addAll(state.criteria.genres.filter { it != "Todos" })
                addAll(state.criteria.excludedGenres.map { "Não: $it" })
                addAll(state.criteria.formats)
                addAll(state.criteria.statuses.map { status ->
                    viewModel.statusOptions.firstOrNull { it.first == status }?.second ?: status
                })
                if (state.criteria.yearMin != null || state.criteria.yearMax != null) {
                    add("Ano: ${state.criteria.yearMin ?: "..."} - ${state.criteria.yearMax ?: "..."}")
                }
                if (state.criteria.minScore != null && state.criteria.minScore!! > 0) {
                    add("Nota: >= ${state.criteria.minScore}")
                }
                if (!state.criteria.episodeRange.isNullOrBlank()) {
                    add("Ep: ${state.criteria.episodeRange}")
                }
                if (!state.criteria.country.isNullOrBlank()) {
                    val cName = viewModel.countryOptions.firstOrNull { it.first == state.criteria.country }?.second ?: state.criteria.country
                    add("País: $cName")
                }
                if (!state.criteria.magazine.isNullOrBlank()) {
                    add("Revista: ${state.criteria.magazine}")
                }
            }

            if (activeTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeTags.forEach { tag ->
                        RemovableChip(
                            text = tag,
                            onRemove = { viewModel.removeActiveFilter(tag) }
                        )
                    }
                    TextButton(
                        onClick = { viewModel.clearAllFilters() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "Limpar tudo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Error or Timeout State
            if (state.isTimeoutError || (state.errorMessage != null && state.works.isEmpty())) {
                val errorKind = when {
                    state.errorMessage?.contains("não configurad", ignoreCase = true) == true -> com.example.ui.components.OtakuErrorKind.NoToken
                    state.errorMessage?.contains("autorizado", ignoreCase = true) == true || state.errorMessage?.contains("Token", ignoreCase = true) == true || state.errorMessage?.contains("inválid", ignoreCase = true) == true || state.errorMessage?.contains("expirad", ignoreCase = true) == true -> com.example.ui.components.OtakuErrorKind.InvalidToken
                    else -> com.example.ui.components.OtakuErrorKind.NetworkOrServer(state.errorMessage)
                }
                com.example.ui.components.OtakuErrorState(
                    kind = errorKind,
                    onRetry = { viewModel.loadWorks(reset = true) },
                    onOpenProfile = onNavigateToProfile
                )
                return@BoxWithConstraints
            }

            // Results Grid (3 columns, 2:3 cards)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columnsCount),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("explore_works_grid")
            ) {
                if (state.isLoading && state.works.isEmpty()) {
                    items(12) {
                        Column {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                shape = RoundedCornerShape(12.dp)
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
                } else {
                    items(
                        items = state.works,
                        key = { it.id }
                    ) { work ->
                        val libItem = state.libraryMap[work.id]
                        val isFavorite = libItem?.isFavorite == true
                        val isPlanningOrReading = libItem?.status != null

                        ExploreWorkCardItem(
                            work = work,
                            isFavorite = isFavorite,
                            isPlanningOrReading = isPlanningOrReading,
                            blurIfAdult = blurAdultCovers,
                            showAdultGenres = (state.criteria.adultMode != com.example.data.model.AdultMode.OFF),
                            onToggleFavorite = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                viewModel.toggleFavorite(work)
                            },
                            onToggleBookmark = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                viewModel.toggleBookmark(work)
                            },
                            onClick = { onWorkClick(work.id) }
                        )
                    }

                    // Loading more footer
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(columnsCount) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        // Advanced Filter ModalBottomSheet (Etapa 2.4)
        if (state.isFilterSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeFilterSheet() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AdvancedFiltersSheet(
                    currentCriteria = state.criteria,
                    availableGenres = state.availableGenres,
                    typeOptions = viewModel.typeOptions.filter { it != "Todos" },
                    formatOptions = viewModel.formatOptions,
                    statusOptions = viewModel.statusOptions,
                    episodeRanges = viewModel.episodeRangeOptions,
                    countryOptions = viewModel.countryOptions,
                    totalCount = state.totalCount,
                    onApply = { newCriteria -> viewModel.applyAdvancedCriteria(newCriteria) },
                    onReset = { viewModel.clearAllFilters() },
                    onClose = { viewModel.closeFilterSheet() }
                )
            }
        }
    }
}

@Composable
private fun RemovableChip(
    text: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onRemove)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remover filtro",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFiltersSheet(
    currentCriteria: TursoQueryBuilder.ExploreFilterCriteria,
    availableGenres: List<String>,
    typeOptions: List<String>,
    formatOptions: List<String>,
    statusOptions: List<Pair<String, String>>,
    episodeRanges: List<String>,
    countryOptions: List<Pair<String, String>>,
    totalCount: Int?,
    onApply: (TursoQueryBuilder.ExploreFilterCriteria) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    var selectedTypes by remember { mutableStateOf(currentCriteria.types.toSet()) }
    var selectedFormats by remember { mutableStateOf(currentCriteria.formats.toSet()) }
    var selectedStatuses by remember { mutableStateOf(currentCriteria.statuses.toSet()) }
    var selectedGenres by remember { mutableStateOf(currentCriteria.genres.toSet()) }
    var excludedGenres by remember { mutableStateOf(currentCriteria.excludedGenres.toSet()) }
    var genresModeAnd by remember { mutableStateOf(currentCriteria.genresModeAnd) }
    var genreSearchQuery by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(currentCriteria.tags.toSet()) }
    var minScore by remember { mutableFloatStateOf((currentCriteria.minScore ?: 0).toFloat()) }
    var selectedEpisodeRange by remember { mutableStateOf(currentCriteria.episodeRange) }
    var selectedCountry by remember { mutableStateOf(currentCriteria.country) }
    var magazineInput by remember { mutableStateOf(currentCriteria.magazine ?: "") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabeçalho da folha com título e fechar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filtros Avançados",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar filtros",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Conteúdo rolável de seções
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Tipo
            Text("Tipo de obra", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                typeOptions.forEach { type ->
                    val isSelected = selectedTypes.contains(type)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTypes = if (isSelected) selectedTypes - type else selectedTypes + type
                        },
                        label = { Text(type, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Status
            Text("Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                statusOptions.forEach { (code, label) ->
                    val isSelected = selectedStatuses.contains(code)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedStatuses = if (isSelected) selectedStatuses - code else selectedStatuses + code
                        },
                        label = { Text(label, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Formato
            Text("Formato", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formatOptions.forEach { format ->
                    val isSelected = selectedFormats.contains(format)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedFormats = if (isSelected) selectedFormats - format else selectedFormats + format
                        },
                        label = { Text(format, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Gêneros (com busca e modo E / OU)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gêneros", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = if (genresModeAnd) "Modo: E (todos)" else "Modo: OU (qualquer)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { genresModeAnd = !genresModeAnd }
                        .padding(4.dp)
                )
            }
            Text(
                text = "Toque para incluir. Toque novamente para excluir.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Campo de busca de gêneros
            OutlinedTextField(
                value = genreSearchQuery,
                onValueChange = { genreSearchQuery = it },
                placeholder = { Text("Buscar gênero...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (genreSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { genreSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val standardGenres = availableGenres.filter { !TursoQueryBuilder.SENSITIVE_GENRES.contains(it) }
            val adultGenres = availableGenres.filter { TursoQueryBuilder.SENSITIVE_GENRES.contains(it) }

            val filteredStandard = if (genreSearchQuery.isBlank()) standardGenres
            else standardGenres.filter { it.contains(genreSearchQuery.trim(), ignoreCase = true) }

            val filteredAdult = if (genreSearchQuery.isBlank()) adultGenres
            else adultGenres.filter { it.contains(genreSearchQuery.trim(), ignoreCase = true) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filteredStandard.forEach { genre ->
                    val isIncluded = selectedGenres.contains(genre)
                    val isExcluded = excludedGenres.contains(genre)
                    FilterChip(
                        selected = isIncluded || isExcluded,
                        onClick = {
                            when {
                                isIncluded -> {
                                    selectedGenres = selectedGenres - genre
                                    excludedGenres = excludedGenres + genre
                                }
                                isExcluded -> {
                                    excludedGenres = excludedGenres - genre
                                }
                                else -> {
                                    selectedGenres = selectedGenres + genre
                                }
                            }
                        },
                        label = {
                            Text(
                                text = if (isExcluded) "✕ $genre" else genre,
                                fontSize = 13.sp,
                                color = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }

            if (currentCriteria.adultMode != com.example.data.model.AdultMode.OFF && filteredAdult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gêneros 18+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Adulto",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredAdult.forEach { genre ->
                        val isIncluded = selectedGenres.contains(genre)
                        val isExcluded = excludedGenres.contains(genre)
                        FilterChip(
                            selected = isIncluded || isExcluded,
                            onClick = {
                                when {
                                    isIncluded -> {
                                        selectedGenres = selectedGenres - genre
                                        excludedGenres = excludedGenres + genre
                                    }
                                    isExcluded -> {
                                        excludedGenres = excludedGenres - genre
                                    }
                                    else -> {
                                        selectedGenres = selectedGenres + genre
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = if (isExcluded) "✕ $genre" else genre,
                                    fontSize = 13.sp,
                                    color = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Nota Mínima (Slider de 0 a 10)
            Text("Nota mínima: ${String.format(java.util.Locale.getDefault(), "%.1f", minScore / 10.0)} ★", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Slider(
                value = minScore,
                onValueChange = { minScore = it },
                valueRange = 0f..100f,
                steps = 20,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Episódios / Capítulos
            Text("Episódios / Capítulos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                episodeRanges.forEach { range ->
                    val isSelected = selectedEpisodeRange == range
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedEpisodeRange = if (isSelected) null else range },
                        label = { Text(range, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 7. País de Origem
            Text("País de origem", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                countryOptions.forEach { (code, name) ->
                    val isSelected = selectedCountry == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCountry = if (isSelected) null else code },
                        label = { Text("$code - $name", fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Barra fixa na base: "Limpar / Aplicar"
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        selectedTypes = emptySet()
                        selectedFormats = emptySet()
                        selectedStatuses = emptySet()
                        selectedGenres = emptySet()
                        excludedGenres = emptySet()
                        selectedTags = emptySet()
                        minScore = 0f
                        selectedEpisodeRange = null
                        selectedCountry = null
                        magazineInput = ""
                        genreSearchQuery = ""
                        onReset()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Limpar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val newCriteria = currentCriteria.copy(
                            types = selectedTypes.toList(),
                            formats = selectedFormats.toList(),
                            statuses = selectedStatuses.toList(),
                            genres = selectedGenres.toList(),
                            excludedGenres = excludedGenres.toList(),
                            genresModeAnd = genresModeAnd,
                            tags = selectedTags.toList(),
                            minScore = if (minScore > 0f) minScore.toInt() else null,
                            episodeRange = selectedEpisodeRange,
                            country = selectedCountry,
                            magazine = magazineInput.takeIf { it.isNotBlank() }
                        )
                        onApply(newCriteria)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp)
                ) {
                    Text(
                        text = totalCount?.let { "Aplicar ($it resultados)" } ?: "Aplicar filtros",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
