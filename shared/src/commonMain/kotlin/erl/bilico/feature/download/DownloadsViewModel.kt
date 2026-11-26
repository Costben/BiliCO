package erl.bilico.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DownloadsUiState(
    val activeTasks: List<DownloadTask> = emptyList(),
    val completedTasks: List<DownloadTask> = emptyList(),
    val totalCachedBytes: Long = 0L,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    val hasAnyTask: Boolean get() = activeTasks.isNotEmpty() || completedTasks.isNotEmpty()
    val hasRunning: Boolean get() = activeTasks.any { it.isActive }
    val hasPausedOrFailed: Boolean get() = activeTasks.any {
        it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED
    }
    val allIds: Set<String>
        get() = (activeTasks + completedTasks).mapTo(HashSet()) { it.id }
}

class DownloadsViewModel(
    private val repository: DownloadRepository,
) : ViewModel() {

    private data class SelectionState(
        val active: Boolean = false,
        val ids: Set<String> = emptySet(),
    )

    private val selection = MutableStateFlow(SelectionState())

    val state: StateFlow<DownloadsUiState> = combine(
        repository.tasks,
        repository.totalCachedBytes,
        selection,
    ) { tasks, usedBytes, sel ->
        val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.completedAtMs }
        val active = tasks.filter { it.status != DownloadStatus.COMPLETED }
        val validIds = tasks.mapTo(HashSet()) { it.id }
        val sanitizedIds = sel.ids.intersect(validIds)
        val activeSelection = sel.active && validIds.isNotEmpty()
        DownloadsUiState(
            activeTasks = active,
            completedTasks = completed,
            totalCachedBytes = usedBytes,
            selectionMode = activeSelection,
            selectedIds = if (activeSelection) sanitizedIds else emptySet(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState(),
    )

    fun enterSelection(initialId: String? = null) {
        selection.update {
            SelectionState(
                active = true,
                ids = if (initialId != null) setOf(initialId) else emptySet(),
            )
        }
    }

    fun exitSelection() {
        selection.value = SelectionState()
    }

    fun toggleSelect(taskId: String) {
        selection.update { current ->
            val next = if (taskId in current.ids) current.ids - taskId else current.ids + taskId
            current.copy(ids = next)
        }
    }

    fun toggleSelectAll() {
        val all = state.value.allIds
        selection.update { current ->
            val allPicked = all.isNotEmpty() && current.ids.containsAll(all)
            current.copy(ids = if (allPicked) emptySet() else all)
        }
    }

    fun pause(taskId: String) = repository.pause(taskId)

    fun resume(taskId: String) = repository.resume(taskId)

    fun pauseAll() = repository.pauseAll()

    fun resumeAll() = repository.resumeAll()

    fun deleteTask(taskId: String) = repository.delete(listOf(taskId))

    fun deleteSelected() {
        val ids = state.value.selectedIds
        if (ids.isEmpty()) return
        repository.delete(ids)
        exitSelection()
    }

    fun deleteAll() {
        val ids = state.value.allIds
        if (ids.isNotEmpty()) repository.delete(ids)
        exitSelection()
    }
}
