package dev.pulso.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pulso.app.data.HabitRepository
import dev.pulso.app.domain.Habit
import dev.pulso.app.domain.HabitCheckin
import dev.pulso.app.domain.HabitDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [habits] includes archived ones, ordered by position; screens decide what to show. */
data class HabitUiState(
    val habits: List<Habit> = emptyList(),
    val checkins: List<HabitCheckin> = emptyList(),
    val loaded: Boolean = false,
)

/** Own ViewModel for habits (SPEC 009), so the task ViewModel stays unchanged. */
class HabitViewModel(private val repository: HabitRepository) : ViewModel() {
    val state: StateFlow<HabitUiState> = combine(
        repository.observeHabits(includeArchived = true),
        repository.observeCheckins(HISTORY_START, HISTORY_END),
    ) { habits, checkins -> HabitUiState(habits, checkins, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitUiState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun create(draft: HabitDraft, done: (Throwable?) -> Unit = {}) = execute(done) { repository.createHabit(draft) }

    fun edit(id: String, draft: HabitDraft, done: (Throwable?) -> Unit = {}) = execute(done) {
        repository.editHabit(id, draft)
    }

    fun setArchived(habit: Habit, archived: Boolean) = execute(failureMessage = true) {
        repository.setArchived(habit.id, archived)
        _messages.tryEmit(UiMessage(
            if (archived) "Hábito arquivado." else "Hábito reativado.", "Desfazer",
        ) { execute { repository.setArchived(habit.id, !archived) } })
    }

    fun delete(habit: Habit) = execute(failureMessage = true) {
        repository.deleteHabit(habit.id)
        _messages.tryEmit(UiMessage("Hábito excluído.", "Desfazer") { execute { repository.restoreHabit(habit.id) } })
    }

    fun move(id: String, newPosition: Int) = execute(failureMessage = true) { repository.move(id, newPosition) }

    /** Checks today (the day is read when the action runs). Messages only when something was written. */
    fun setCheckedToday(habit: Habit, checked: Boolean) = execute(failureMessage = true) {
        val date = repository.setCheckedToday(habit.id, checked) ?: return@execute
        _messages.tryEmit(UiMessage(
            if (checked) "${habit.name} marcado como feito." else "${habit.name} desmarcado.", "Desfazer",
        ) { execute { repository.setChecked(habit.id, date, !checked) } })
    }

    /** Checks a day of the week view (SPEC 010). The repository refuses days outside [HabitRules.canCheck]. */
    fun setChecked(habit: Habit, date: LocalDate, checked: Boolean) = execute(failureMessage = true) {
        if (!repository.setChecked(habit.id, date, checked)) return@execute
        _messages.tryEmit(UiMessage(
            if (checked) "${habit.name} marcado como feito." else "${habit.name} desmarcado.", "Desfazer",
        ) { execute { repository.setChecked(habit.id, date, !checked) } })
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

    companion object {
        /** Wide enough to hold every check-in: streaks look back over the whole history. */
        private val HISTORY_START: LocalDate = LocalDate.of(2000, 1, 1)
        private val HISTORY_END: LocalDate = LocalDate.of(9999, 12, 31)
    }
}

class HabitViewModelFactory(private val repository: HabitRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HabitViewModel(repository) as T
}
