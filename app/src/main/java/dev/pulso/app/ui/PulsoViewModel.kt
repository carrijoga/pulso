package dev.pulso.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pulso.app.AppContainer
import dev.pulso.app.data.AppSettings
import dev.pulso.app.data.BackupService
import dev.pulso.app.data.ImportSummary
import dev.pulso.app.data.Preferences
import dev.pulso.app.data.TaskRepository
import dev.pulso.app.data.ThemeMode
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.InboxItem
import dev.pulso.app.domain.RescheduleEntry
import dev.pulso.app.domain.TaskDraft
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

data class PulsoUiState(
    val inbox: List<InboxItem> = emptyList(),
    /** Pending + archived captures, for global search. */
    val searchableInbox: List<InboxItem> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
)

/** One-shot feedback with an optional undo, shown as a snackbar. */
data class UiMessage(val text: String, val undoLabel: String? = null, val undo: (() -> Unit)? = null)

class PulsoViewModel(
    private val repository: TaskRepository,
    private val preferences: Preferences,
    private val backup: BackupService,
) : ViewModel() {
    val state: StateFlow<PulsoUiState> = combine(repository.observeInbox(), repository.observeSearchableInbox(),
        repository.observeTasks(), repository.observeCategories(), preferences.settings) { inbox, searchable, tasks, categories, settings ->
        PulsoUiState(inbox, searchable, tasks, categories, settings, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PulsoUiState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun rescheduleHistory(taskId: String): Flow<List<RescheduleEntry>> = repository.observeRescheduleHistory(taskId)

    fun capture(text: String, done: (Throwable?) -> Unit = {}) = execute(done) { repository.capture(text) }

    fun archive(id: String) = execute(failureMessage = true) {
        repository.archiveInbox(id)
        _messages.tryEmit(UiMessage("Anotação arquivada.", "Desfazer") { execute { repository.restoreInbox(id) } })
    }

    fun restoreInbox(id: String) = execute(failureMessage = true) { repository.restoreInbox(id) }

    fun organize(id: String, draft: TaskDraft, done: (Throwable?) -> Unit = {}) = execute(done) {
        repository.organizeInbox(id, draft)
    }
    fun create(draft: TaskDraft, done: (Throwable?) -> Unit = {}) = execute(done) { repository.createTask(draft) }
    fun edit(id: String, draft: TaskDraft, done: (Throwable?) -> Unit = {}) = execute(done) {
        repository.editTask(id, draft)
    }
    fun complete(id: String, completed: Boolean) = execute(failureMessage = true) {
        val next = repository.setCompleted(id, completed)
        if (next != null) {
            val due = next.dueAt?.let { TaskRules.dueLabel(it, System.currentTimeMillis(), ZoneId.systemDefault()).text }
            val text = if (due == null) "Concluída." else "Concluída. Próxima: $due"
            _messages.tryEmit(UiMessage(text, "Desfazer") { execute(failureMessage = true) { repository.setCompleted(id, false) } })
        }
    }

    fun delete(id: String) = execute(failureMessage = true) {
        repository.deleteTask(id)
        _messages.tryEmit(UiMessage("Tarefa excluída.", "Desfazer") { execute { repository.restoreTask(id) } })
    }

    fun endRecurrence(id: String) = execute(failureMessage = true) {
        repository.endRecurrence(id)
        _messages.tryEmit(UiMessage("Repetição encerrada."))
    }

    fun reschedule(id: String, newDueAt: Long) = execute(failureMessage = true) { repository.reschedule(id, newDueAt) }
    fun clearDue(task: TaskItem) = execute(failureMessage = true) { repository.editTask(task.id, task.toDraft().copy(dueAt = null)) }

    fun addCategory(name: String, done: (Throwable?) -> Unit = {}) = execute(done) { repository.addCategory(name) }
    fun renameCategory(id: String, name: String, done: (Throwable?) -> Unit = {}) = execute(done) {
        repository.renameCategory(id, name)
    }
    fun deleteCategory(category: Category) = execute(failureMessage = true) {
        repository.deleteCategory(category.id)
        _messages.tryEmit(UiMessage("Categoria \"${category.name}\" removida da lista. As tarefas mantêm o nome."))
    }

    fun themeMode(mode: ThemeMode) = execute { preferences.setThemeMode(mode) }
    fun dynamicColor(enabled: Boolean) = execute { preferences.setDynamicColor(enabled) }
    fun defaultDueTime(time: LocalTime) = execute { preferences.setDefaultDueTime(time) }
    fun notificationPromptShown() = execute { preferences.markNotificationPromptShown() }
    fun voiceNoticeShown() = execute { preferences.markVoiceNoticeShown() }
    fun markReviewDone() = execute {
        preferences.markReviewDone(System.currentTimeMillis())
        _messages.tryEmit(UiMessage("Revisão semanal concluída. Até a próxima!"))
    }

    suspend fun exportBackup(): String = backup.exportJson()

    fun importBackup(json: String, done: (Result<ImportSummary>) -> Unit) {
        viewModelScope.launch {
            val result = try { Result.success(backup.importJson(json)) }
                catch (cancel: CancellationException) { throw cancel }
                catch (exception: Exception) { Result.failure(exception) }
            done(result)
        }
    }

    private fun execute(done: (Throwable?) -> Unit = {}, failureMessage: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            val failure = try { action(); null }
                catch (cancel: CancellationException) { throw cancel }
                catch (exception: Exception) { exception }
            if (failure != null && failureMessage) {
                _messages.tryEmit(UiMessage(failure.message ?: "Não foi possível concluir a ação."))
            }
            done(failure)
        }
    }
}

fun TaskItem.toDraft(): TaskDraft = TaskDraft(title, notes, category, priority, dueAt, links, recurrence)

class PulsoViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PulsoViewModel(
        container.repository, container.preferences, container.backup
    ) as T
}
