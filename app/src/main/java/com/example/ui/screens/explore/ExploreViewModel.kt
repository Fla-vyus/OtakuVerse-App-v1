package com.example.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibraryEntity
import com.example.data.model.WorkCard
import com.example.data.repository.LibraryRepository
import com.example.data.repository.WorkRepository
import com.example.data.turso.TursoAuthException
import com.example.data.turso.TursoQueryBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

data class ExploreUiState(
    val criteria: TursoQueryBuilder.ExploreFilterCriteria = TursoQueryBuilder.ExploreFilterCriteria(),
    val works: List<WorkCard> = emptyList(),
    val totalCount: Int? = null,
    val isLoadingCount: Boolean = false,
    val availableGenres: List<String> = emptyList(),
    val libraryMap: Map<String, LibraryEntity> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val hasLoadMoreError: Boolean = false,
    val isTokenError: Boolean = false,
    val isTimeoutError: Boolean = false,
    val errorMessage: String? = null,
    val isFilterSheetOpen: Boolean = false
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (criteria.types.isNotEmpty() && !criteria.types.contains("Todos")) count += criteria.types.size
            if (criteria.formats.isNotEmpty()) count += criteria.formats.size
            if (criteria.statuses.isNotEmpty()) count += criteria.statuses.size
            if (criteria.genres.isNotEmpty()) count += criteria.genres.size
            if (criteria.excludedGenres.isNotEmpty()) count += criteria.excludedGenres.size
            if (criteria.tags.isNotEmpty()) count += criteria.tags.size
            if (criteria.yearMin != null || criteria.yearMax != null) count += 1
            if (criteria.minScore != null && criteria.minScore > 0) count += 1
            if (!criteria.episodeRange.isNullOrBlank()) count += 1
            if (!criteria.country.isNullOrBlank()) count += 1
            if (!criteria.magazine.isNullOrBlank()) count += 1
            return count
        }
}

class ExploreViewModel(
    private val workRepository: WorkRepository,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: com.example.data.repository.UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var hideInExplore: Boolean = false
    private var currentLoadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var countJob: Job? = null

    val typeOptions = listOf("Todos", "Anime", "Mangá", "Manhwa", "Manhua", "Light Novel", "Web Novel")

    val quickTagOptions = listOf(
        "Isekai", "Shounen", "Shoujo", "Seinen", "Josei", "Escola",
        "Reencarnação", "Magia", "Vingança", "Romance", "Comédia", "Ação"
    )

    val sortOptions = listOf(
        "popularity" to "Mais populares",
        "avgScore" to "Melhores notas",
        "trending" to "Em alta",
        "startDate" to "Mais recentes",
        "favourites" to "Mais favoritados",
        "lastUpdate" to "Atualizados",
        "titleAsc" to "A–Z"
    )

    val formatOptions = listOf("TV", "Movie", "OVA", "ONA", "Special", "Webtoon", "Manga", "One-shot", "Light Novel")
    val statusOptions = listOf("RELEASING" to "Em lançamento", "FINISHED" to "Concluído", "CANCELLED" to "Cancelado", "HIATUS" to "Pausado", "NOT_YET_RELEASED" to "A estrear")
    val episodeRangeOptions = listOf("1-12", "13-24", "25-50", "50-100", "100+")
    val countryOptions = listOf("JP" to "Japão", "KR" to "Coreia do Sul", "CN" to "China", "TW" to "Taiwan")

    val blurAdultCovers: StateFlow<Boolean> = preferencesRepository?.blurAdultCovers
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
        ?: MutableStateFlow(true)

    init {
        observeLibrary()
        observeHidePreference()
        observeAdultMode()
        loadGenres()
        loadWorks(reset = true)
    }

    private fun observeAdultMode() {
        if (preferencesRepository != null) {
            viewModelScope.launch {
                preferencesRepository.adultMode.collect { mode ->
                    if (_uiState.value.criteria.adultMode != mode) {
                        _uiState.update { it.copy(criteria = it.criteria.copy(adultMode = mode)) }
                        loadGenres(mode)
                        loadWorks(reset = true)
                    }
                }
            }
        }
    }

    private fun observeHidePreference() {
        if (preferencesRepository != null) {
            viewModelScope.launch {
                preferencesRepository.hideLibraryInExplore.collect { hide ->
                    hideInExplore = hide
                }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.libraryMap.collect { map ->
                _uiState.update { it.copy(libraryMap = map) }
            }
        }
    }

    private fun loadGenres(mode: com.example.data.model.AdultMode = _uiState.value.criteria.adultMode) {
        viewModelScope.launch {
            val genres = workRepository.getGenres(mode)
            _uiState.update { it.copy(availableGenres = genres) }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(criteria = it.criteria.copy(query = newQuery)) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(400L) // 400ms debounce (Etapa 2.7)
            loadWorks(reset = true)
        }
    }

    fun onToggleType(type: String) {
        val currentTypes = _uiState.value.criteria.types.toMutableList()
        if (type == "Todos") {
            currentTypes.clear()
        } else {
            if (currentTypes.contains(type)) {
                currentTypes.remove(type)
            } else {
                currentTypes.add(type)
            }
        }
        _uiState.update { it.copy(criteria = it.criteria.copy(types = currentTypes)) }
        loadWorks(reset = true)
    }

    fun onToggleQuickTag(tag: String) {
        val currentTags = _uiState.value.criteria.tags.toMutableList()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(tag)
        }
        _uiState.update { it.copy(criteria = it.criteria.copy(tags = currentTags)) }
        loadWorks(reset = true)
    }

    fun onSelectSort(sortKey: String) {
        if (_uiState.value.criteria.sortBy == sortKey) return
        _uiState.update { it.copy(criteria = it.criteria.copy(sortBy = sortKey)) }
        loadWorks(reset = true)
    }

    fun openFilterSheet() {
        _uiState.update { it.copy(isFilterSheetOpen = true) }
    }

    fun closeFilterSheet() {
        _uiState.update { it.copy(isFilterSheetOpen = false) }
    }

    fun applyAdvancedCriteria(newCriteria: TursoQueryBuilder.ExploreFilterCriteria) {
        _uiState.update { it.copy(criteria = newCriteria, isFilterSheetOpen = false) }
        loadWorks(reset = true)
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                criteria = TursoQueryBuilder.ExploreFilterCriteria(
                    query = it.criteria.query,
                    sortBy = it.criteria.sortBy
                )
            )
        }
        loadWorks(reset = true)
    }

    fun removeActiveFilter(filterTag: String) {
        val c = _uiState.value.criteria
        val newCriteria = when {
            c.types.contains(filterTag) -> c.copy(types = c.types - filterTag)
            c.tags.contains(filterTag) -> c.copy(tags = c.tags - filterTag)
            c.genres.contains(filterTag) -> c.copy(genres = c.genres - filterTag)
            c.excludedGenres.contains(filterTag) -> c.copy(excludedGenres = c.excludedGenres - filterTag)
            filterTag.startsWith("Não: ") -> c.copy(excludedGenres = c.excludedGenres - filterTag.removePrefix("Não: "))
            c.formats.contains(filterTag) -> c.copy(formats = c.formats - filterTag)
            c.statuses.contains(filterTag) -> c.copy(statuses = c.statuses - filterTag)
            statusOptions.any { it.second == filterTag } -> {
                val code = statusOptions.first { it.second == filterTag }.first
                c.copy(statuses = c.statuses - code)
            }
            filterTag.startsWith("Ano:") -> c.copy(yearMin = null, yearMax = null)
            filterTag.startsWith("Nota:") -> c.copy(minScore = null)
            filterTag.startsWith("Ep:") -> c.copy(episodeRange = null)
            filterTag.startsWith("País:") -> c.copy(country = null)
            filterTag.startsWith("Revista:") -> c.copy(magazine = null)
            else -> c
        }
        _uiState.update { it.copy(criteria = newCriteria) }
        loadWorks(reset = true)
    }

    fun loadWorks(reset: Boolean) {
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            if (reset) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        isTimeoutError = false,
                        hasLoadMoreError = false,
                        errorMessage = null,
                        canLoadMore = true
                    )
                }
                loadTotalCount()
            } else {
                _uiState.update { it.copy(isLoadingMore = true, hasLoadMoreError = false) }
            }

            val currentCriteria = _uiState.value.criteria
            val offset = if (reset) 0 else _uiState.value.works.size
            val pageSize = 30 // Paginação de 30 em 30 itens (Etapa 2.7)

            // 12s timeout (Etapa 2.7)
            val result = withTimeoutOrNull(12000L) {
                workRepository.exploreWorks(
                    criteria = currentCriteria,
                    limit = pageSize,
                    offset = offset
                )
            }

            if (result == null) {
                // Timeout
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isTimeoutError = true,
                        errorMessage = "A consulta demorou mais que o esperado (tempo limite de 12s)."
                    )
                }
                return@launch
            }

            result.fold(
                onSuccess = { newItems ->
                    val filteredItems = if (hideInExplore) {
                        val libKeys = _uiState.value.libraryMap.keys
                        newItems.filter { it.id !in libKeys }
                    } else newItems

                    _uiState.update { current ->
                        val combined = if (reset) filteredItems else current.works + filteredItems
                        current.copy(
                            works = combined,
                            isLoading = false,
                            isLoadingMore = false,
                            canLoadMore = newItems.size >= pageSize,
                            hasLoadMoreError = false,
                            isTokenError = false,
                            isTimeoutError = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    val isMissing = err is com.example.data.turso.TursoMissingTokenException || err.message?.contains("não configurad", ignoreCase = true) == true
                    val isAuth = err is com.example.data.turso.TursoAuthException || err.message?.contains("expirad", ignoreCase = true) == true || err.message?.contains("inválid", ignoreCase = true) == true
                    val msg = when {
                        isMissing -> "Chave de acesso não configurada"
                        isAuth -> "Chave inválida ou expirada"
                        else -> err.message ?: "Falha ao carregar catálogo"
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasLoadMoreError = !reset,
                            isTokenError = isAuth || isMissing,
                            errorMessage = msg
                        )
                    }
                }
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoading && !state.isLoadingMore && state.canLoadMore && !state.hasLoadMoreError) {
            loadWorks(reset = false)
        }
    }

    private fun loadTotalCount() {
        countJob?.cancel()
        countJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCount = true) }
            val countResult = workRepository.getExploreCount(_uiState.value.criteria)
            countResult.onSuccess { count ->
                _uiState.update { it.copy(totalCount = count, isLoadingCount = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingCount = false) }
            }
        }
    }

    fun toggleFavorite(work: WorkCard) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(work)
        }
    }

    fun toggleBookmark(work: WorkCard) {
        viewModelScope.launch {
            libraryRepository.togglePlanning(work)
        }
    }

    fun applyPreset(presetName: String) {
        val currentMode = _uiState.value.criteria.adultMode
        val baseCriteria = when (presetName) {
            "recent_releases" -> TursoQueryBuilder.ExploreFilterCriteria(sortBy = "startDate")
            "new_hits" -> TursoQueryBuilder.ExploreFilterCriteria(
                types = listOf("Manga", "Manhwa", "Manhua"),
                statuses = listOf("RELEASING"),
                sortBy = "trending"
            )
            "top_anime" -> TursoQueryBuilder.ExploreFilterCriteria(types = listOf("Anime"), sortBy = "popularity")
            "trending_manhwa" -> TursoQueryBuilder.ExploreFilterCriteria(types = listOf("Manhwa"), sortBy = "trending")
            "popular_manga" -> TursoQueryBuilder.ExploreFilterCriteria(types = listOf("Manga"), sortBy = "popularity")
            "top_rated_manga_manhwa" -> TursoQueryBuilder.ExploreFilterCriteria(
                types = listOf("Manga", "Manhwa"),
                sortBy = "avgScore"
            )
            "binge" -> TursoQueryBuilder.ExploreFilterCriteria(
                types = listOf("Manga", "Manhwa"),
                statuses = listOf("FINISHED"),
                minScore = 80,
                sortBy = "popularity"
            )
            "isekai" -> TursoQueryBuilder.ExploreFilterCriteria(
                types = listOf("Manga", "Manhwa", "Manhua", "Light Novel"),
                tags = listOf("Isekai"),
                sortBy = "popularity"
            )
            "featured_manhua" -> TursoQueryBuilder.ExploreFilterCriteria(types = listOf("Manhua"), sortBy = "popularity")
            else -> TursoQueryBuilder.ExploreFilterCriteria()
        }
        val newCriteria = baseCriteria.copy(adultMode = currentMode)
        _uiState.update { it.copy(criteria = newCriteria) }
        loadWorks(reset = true)
    }
}
