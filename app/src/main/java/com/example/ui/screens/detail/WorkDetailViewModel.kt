package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LibraryEntity
import com.example.data.model.AdultMode
import com.example.data.model.WorkCard
import com.example.data.model.WorkDetail
import com.example.data.repository.LibraryRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkDetailUiState(
    val isLoading: Boolean = true,
    val work: WorkDetail? = null,
    val similarWorks: List<WorkCard> = emptyList(),
    val libraryStatus: LibraryEntity? = null,
    val isSynopsisExpanded: Boolean = false,
    val errorMessage: String? = null
)

class WorkDetailViewModel(
    private val workId: String,
    private val workRepository: WorkRepository,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkDetailUiState())
    val uiState: StateFlow<WorkDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        observeLibrary()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val mode = preferencesRepository?.adultMode?.first() ?: AdultMode.OFF
            val result = workRepository.getWorkDetail(workId, mode)
            result.fold(
                onSuccess = { (detail, similar) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            work = detail,
                            similarWorks = similar
                        )
                    }
                    // C9: Update saved snapshot in library
                    libraryRepository.updateSnapshot(detail)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Não foi possível carregar os detalhes da obra."
                        )
                    }
                }
            )
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeWorkStatus(workId).collect { status ->
                _uiState.update { it.copy(libraryStatus = status) }
            }
        }
    }

    fun toggleSynopsis() {
        _uiState.update { it.copy(isSynopsisExpanded = !it.isSynopsisExpanded) }
    }

    fun setLibraryStatus(status: String) {
        val currentWork = _uiState.value.work ?: return
        viewModelScope.launch {
            libraryRepository.saveOrUpdateWork(currentWork, status = status)
        }
    }

    fun toggleFavorite() {
        val currentWork = _uiState.value.work ?: return
        val existing = _uiState.value.libraryStatus
        viewModelScope.launch {
            if (existing != null) {
                libraryRepository.toggleFavorite(workId)
            } else {
                libraryRepository.saveOrUpdateWork(currentWork, status = "PLANNING", isFavorite = true)
            }
        }
    }

    fun removeFromLibrary() {
        viewModelScope.launch {
            libraryRepository.removeFromLibrary(workId)
        }
    }
}
