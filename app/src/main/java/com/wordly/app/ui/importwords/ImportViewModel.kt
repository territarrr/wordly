package com.wordly.app.ui.importwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.ImportResult
import com.wordly.app.data.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val importing: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null,
)

class ImportViewModel(
    private val repository: WordRepository,
    private val profileId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun importCsv(csv: String) {
        _state.update { ImportUiState(importing = true) }
        viewModelScope.launch {
            _state.update { toUiState(repository.importCsv(profileId, csv)) }
        }
    }

    fun importFromUrl(url: String) {
        _state.update { ImportUiState(importing = true) }
        viewModelScope.launch {
            repository.importFromGoogleSheetsUrl(profileId, url).fold(
                onSuccess = { result -> _state.update { toUiState(result) } },
                onFailure = { error ->
                    _state.update { ImportUiState(error = error.message ?: "Не удалось импортировать по ссылке") }
                },
            )
        }
    }

    private fun toUiState(result: ImportResult): ImportUiState =
        with(result) {
            if (imported == 0 && duplicates == 0 && missingWord == 0 && missingTranslation == 0) {
                ImportUiState(error = "Не нашлось ни одной строки со словом и переводом")
            } else {
                ImportUiState(result = result)
            }
        }

    fun reportReadError() = _state.update { ImportUiState(error = "Не удалось прочитать файл") }
}
