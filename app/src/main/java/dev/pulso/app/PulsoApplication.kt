package dev.pulso.app

import android.app.Application
import androidx.room.Room
import dev.pulso.app.data.LocalTaskRepository
import dev.pulso.app.data.Preferences
import dev.pulso.app.data.PulsoDatabase
import dev.pulso.app.data.SeedCallback
import dev.pulso.app.data.BackupService
import dev.pulso.app.reminders.AndroidTaskChangeListener
import dev.pulso.app.reminders.createNotificationChannel

class PulsoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: PulsoDatabase = Room.databaseBuilder(
        application, PulsoDatabase::class.java, "pulso.db"
    ).addMigrations(*PulsoDatabase.ALL_MIGRATIONS).addCallback(SeedCallback).build()
    val preferences = Preferences(application)
    val changeListener = AndroidTaskChangeListener(application)
    val repository = LocalTaskRepository(database, changeListener = changeListener)
    val backup = BackupService(database, changeListener)
}
