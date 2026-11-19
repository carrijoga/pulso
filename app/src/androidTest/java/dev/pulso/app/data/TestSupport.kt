package dev.pulso.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** Records side effects instead of touching WorkManager/widgets. */
class RecordingListener : TaskChangeListener {
    val calls = mutableListOf<Pair<String, Long?>>()
    override suspend fun taskChanged(taskId: String, reminderAt: Long?) { calls += taskId to reminderAt }
}

class FakeClock(var millis: Long = 1_763_550_000_000L) {
    fun advance(by: Long) { millis += by }
}

fun inMemoryDatabase(): PulsoDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(), PulsoDatabase::class.java,
).addCallback(SeedCallback).build()
