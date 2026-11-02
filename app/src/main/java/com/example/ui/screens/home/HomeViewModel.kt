package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExtraHomeSection
import com.example.data.model.WorkCard
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val heroItems: List<WorkCard> = emptyList(),
    // 10 sections in exact order (Etapa 1.3):
    val recentReleases: List<WorkCard> = emptyList(),
    val newHits: List<WorkCard> = emptyList(),
    val monthSchedule: List<WorkCard> = emptyList(),
    val topPopularity: List<WorkCard> = emptyList(),
    val trendingManhwas: List<WorkCard> = emptyList(),
    val popularMangas: List<WorkCard> = emptyList(),
    val topRatedMangaManhwa: List<WorkCard> = emptyList(),
    val bingeItems: List<WorkCard> = emptyList(),
    val isekaiReincarnation: List<WorkCard> = emptyList(),
    val featuredManhuas: List<WorkCard> = emptyList(),
    // Extra dynamic sections from HomeSection (Etapa Seções Extras)
    val extraSections: List<ExtraHomeSection> = emptyList(),
    // Diagnostics & Errors
    val isOffline: Boolean = false,
    val isTokenError: Boolean = false,
    val errorMessage: String? = null,
    val batch1DurationMs: Long = 0L,
    val batch2DurationMs: Long = 0L,
    val bombandoCriterion: String = ""
)

class HomeViewModel(
    private val workRepository: WorkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentLoadJob: Job? = null
    private var extraSectionsJob: Job? = null
    private var rawExtraSections: List<ExtraHomeSection> = emptyList()

    init {
        loadCachedExtraSections()
        loadData(refresh = false)
    }

    private fun recomputeExtraSections(
        raw: List<ExtraHomeSection>,
        state: HomeUiState
    ): List<ExtraHomeSection> {
        if (raw.isEmpty()) return emptyList()
        val fixedLists = listOf(
            state.heroItems,
            state.recentReleases,
            state.newHits,
            state.monthSchedule,
            state.topPopularity,
            state.trendingManhwas,
            state.popularMangas,
            state.topRatedMangaManhwa,
            state.bingeItems,
            state.isekaiReincarnation,
            state.featuredManhuas
        )
        return workRepository.filterAndDeduplicateExtraSections(raw, fixedLists)
    }

    private fun loadCachedExtraSections() {
        viewModelScope.launch {
            val cached = workRepository.getCachedExtraSections()
            if (cached.isNotEmpty()) {
                rawExtraSections = cached
                _uiState.update { current ->
                    current.copy(extraSections = recomputeExtraSections(rawExtraSections, current))
                }
            }
        }
    }

    fun loadExtraSections() {
        extraSectionsJob?.cancel()
        extraSectionsJob = viewModelScope.launch {
            try {
                val sections = workRepository.fetchExtraSections()
                rawExtraSections = sections
                _uiState.update { current ->
                    current.copy(extraSections = recomputeExtraSections(rawExtraSections, current))
                }
            } catch (_: Exception) {
                // Silently ignore any error
            }
        }
    }

    fun loadData(refresh: Boolean = false) {
        currentLoadJob?.cancel()
        if (refresh) {
            loadExtraSections()
        }
        currentLoadJob = viewModelScope.launch {
            if (refresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            }

            workRepository.getHomeDataStream().collect { result ->
                _uiState.update { current ->
                    val updated = current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        heroItems = result.heroItems,
                        recentReleases = result.recentReleases,
                        newHits = result.newHits,
                        monthSchedule = result.monthSchedule,
                        topPopularity = result.topPopularity,
                        trendingManhwas = result.trendingManhwas,
                        popularMangas = result.popularMangas,
                        topRatedMangaManhwa = result.topRatedMangaManhwa,
                        bingeItems = result.bingeItems,
                        isekaiReincarnation = result.isekaiReincarnation,
                        featuredManhuas = result.featuredManhuas,
                        isOffline = result.isOffline,
                        isTokenError = result.isTokenError,
                        errorMessage = result.errorMessage,
                        batch1DurationMs = result.batch1DurationMs,
                        batch2DurationMs = result.batch2DurationMs,
                        bombandoCriterion = result.bombandoCriterion
                    )
                    updated.copy(extraSections = recomputeExtraSections(rawExtraSections, updated))
                }
            }
        }
    }
}
