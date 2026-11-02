package com.example.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdultMode
import com.example.data.model.ScheduledEpisode
import com.example.data.model.WorkCard
import com.example.data.repository.LibraryRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val episodes: List<ScheduledEpisode> = emptyList(),
    val filteredEpisodes: List<ScheduledEpisode> = emptyList(),
    val grouped: Map<String, List<ScheduledEpisode>> = emptyMap(),
    val days: List<String> = listOf("Todos"),
    val selectedDay: String = "Todos",
    val savedWorkIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val adultMode: AdultMode = AdultMode.OFF,
    val blurAdultCovers: Boolean = true
)

class ScheduleViewModel(
    private val workRepository: WorkRepository,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        observeSavedWorks()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.adultMode.collect { mode ->
                _uiState.update { it.copy(adultMode = mode) }
                loadSchedule(mode)
            }
        }
        viewModelScope.launch {
            preferencesRepository.blurAdultCovers.collect { blur ->
                _uiState.update { it.copy(blurAdultCovers = blur) }
            }
        }
    }

    private fun observeSavedWorks() {
        viewModelScope.launch {
            libraryRepository.getAllSavedIdsFlow().collect { ids ->
                _uiState.update { it.copy(savedWorkIds = ids) }
            }
        }
    }

    fun selectDay(day: String) {
        _uiState.update { state ->
            val filtered = if (day == "Todos") {
                state.episodes
            } else {
                state.episodes.filter { it.dayGroup == day }
            }
            state.copy(
                selectedDay = day,
                filteredEpisodes = filtered,
                grouped = filtered.groupBy { it.dayGroup }
            )
        }
    }

    fun toggleBookmark(work: WorkCard) {
        viewModelScope.launch {
            val isSaved = _uiState.value.savedWorkIds.contains(work.id)
            if (isSaved) {
                libraryRepository.deleteById(work.id)
            } else {
                libraryRepository.saveOrUpdateCard(work, status = "PLANNING")
            }
        }
    }

    fun loadSchedule(mode: AdultMode = _uiState.value.adultMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = workRepository.getScheduleWorks(mode)
            result.fold(
                onSuccess = { list ->
                    val distinctDays = listOf("Todos") + list.map { it.dayGroup }.distinct()
                    val selected = if (_uiState.value.selectedDay in distinctDays) _uiState.value.selectedDay else "Todos"
                    val filtered = if (selected == "Todos") list else list.filter { it.dayGroup == selected }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            episodes = list,
                            filteredEpisodes = filtered,
                            days = distinctDays,
                            selectedDay = selected,
                            grouped = filtered.groupBy { ep -> ep.dayGroup },
                            errorMessage = if (list.isEmpty()) "Nenhum episódio agendado para os próximos dias." else null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Erro ao carregar agenda."
                        )
                    }
                }
            )
        }
    }
}
