package com.wordly.app.ui.importwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.wordly.app.data.AnkiPackageReader
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
    private val ankiReader: AnkiPackageReader,
    private val profileId: Long,
) : ViewModel() {

    /**
     * One entry point for any picked file: what it is is decided by its content, not by which
     * button was used — a zip (".apkg") goes to the Anki import, readable text to the CSV import,
     * and anything else is refused. (Reading an .apkg as CSV once filled a dictionary with
     * hundreds of rows of binary garbage.)
     */
    fun importFile(uri: Uri, resolver: ContentResolver, enrichExisting: Boolean) {
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        when {
            bytes == null -> reportReadError()
            ImportFormat.isZip(bytes) -> importApkg(uri, resolver, enrichExisting)
            else -> ImportFormat.decodeText(bytes)?.let(::importCsv)
                ?: _state.update { ImportUiState(error = "Это не CSV и не файл Anki (.apkg) — такой файл не импортировать") }
        }
    }

    /** Anki package (.apkg), e.g. a 2Books export — see [com.wordly.app.domain.AnkiImport]. */
    private fun importApkg(uri: Uri, resolver: ContentResolver, enrichExisting: Boolean) {
        _state.update { ImportUiState(importing = true) }
        viewModelScope.launch {
            runCatching { ankiReader.read(uri, resolver) }.fold(
                onSuccess = { notes -> _state.update { toUiState(repository.importAnki(profileId, notes, enrichExisting)) } },
                onFailure = { error -> _state.update { ImportUiState(error = error.message ?: "Не удалось прочитать файл") } },
            )
        }
    }

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

/** Content sniffing for imported files. */
internal object ImportFormat {

    /** Zip local-file header "PK\u0003\u0004" — what every .apkg starts with. */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * [bytes] as UTF-8 text, or `null` if it isn't text: NUL bytes or undecodable sequences
     * mean a binary file, which must never be parsed as CSV rows.
     */
    fun decodeText(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()?.removePrefix("\uFEFF")
    }
}
