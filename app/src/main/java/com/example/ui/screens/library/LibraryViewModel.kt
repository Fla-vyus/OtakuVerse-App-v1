package com.example.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibraryEntity
import com.example.data.model.WorkCard
import com.example.data.repository.LibraryRepository
import com.example.data.repository.LibraryStats
import com.example.data.repository.LibraryTab
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibrarySort(val title: String) {
    RECENT("Mais recentes"),
    TITLE("A – Z"),
    RATING("Melhor nota")
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.ALL, // NOVA aba padrão (Etapa 3.2)
    val searchQuery: String = "",
    val selectedTypeFilter: String = "Todos",
    val selectedSort: LibrarySort = LibrarySort.RECENT
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val workRepository: WorkRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _recentlyDeleted = MutableSharedFlow<LibraryEntity>()
    val recentlyDeleted: SharedFlow<LibraryEntity> = _recentlyDeleted.asSharedFlow()

    private val _topSuggestions = MutableStateFlow<List<WorkCard>>(emptyList())
    val topSuggestions: StateFlow<List<WorkCard>> = _topSuggestions.asStateFlow()

    val stats: StateFlow<LibraryStats> = libraryRepository.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    val tabCounts: StateFlow<Map<LibraryTab, Int>> = libraryRepository.getTabCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Load top 3 suggestions for empty state (Etapa 3.5)
        viewModelScope.launch {
            try {
                val res = workRepository?.exploreWorks(limit = 3)?.getOrNull()
                if (!res.isNullOrEmpty()) {
                    _topSuggestions.value = res
                }
            } catch (_: Exception) {}
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val displayedWorks: StateFlow<List<LibraryEntity>> = combine(
        _uiState,
        _uiState.flatMapLatest { state -> libraryRepository.getWorksForTab(state.selectedTab) }
    ) { state, works ->
        var filtered = works

        // Type filter
        if (state.selectedTypeFilter != "Todos") {
            filtered = filtered.filter {
                it.workType.equals(state.selectedTypeFilter, ignoreCase = true)
            }
        }

        // Search query filter
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.titleEn?.lowercase()?.contains(q) == true
            }
        }

        // Sorting
        when (state.selectedSort) {
            LibrarySort.RECENT -> filtered.sortedByDescending { it.updatedAt }
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            LibrarySort.RATING -> filtered.sortedByDescending { it.userRating ?: it.avgScore ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSearchQueryChanged(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun onSelectTypeFilter(type: String) {
        _uiState.update { it.copy(selectedTypeFilter = type) }
    }

    fun onSelectSort(sort: LibrarySort) {
        _uiState.update { it.copy(selectedSort = sort) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(id)
        }
    }

    fun setStatus(id: String, status: String?) {
        viewModelScope.launch {
            libraryRepository.setStatus(id, status)
        }
    }

    fun updateProgress(id: String, newProgress: Int) {
        viewModelScope.launch {
            libraryRepository.updateProgress(id, newProgress)
        }
    }

    fun updateRating(id: String, rating: Int?) {
        viewModelScope.launch {
            libraryRepository.updateRating(id, rating)
        }
    }

    fun removeWork(entity: LibraryEntity) {
        viewModelScope.launch {
            libraryRepository.removeFromLibrary(entity.id)
            _recentlyDeleted.emit(entity)
        }
    }

    fun undoRemove(entity: LibraryEntity) {
        viewModelScope.launch {
            libraryRepository.restoreWork(entity)
        }
    }
}
