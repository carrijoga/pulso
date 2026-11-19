package dev.pulso.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulso.app.ui.PulsoScreens
import dev.pulso.app.ui.PulsoViewModel
import dev.pulso.app.ui.PulsoViewModelFactory
import dev.pulso.app.ui.theme.PulsoTheme
import dev.pulso.app.ui.theme.SyncSystemBars

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val factory = PulsoViewModelFactory((application as PulsoApplication).container)
        setContent {
            val vm: PulsoViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            PulsoTheme(state.settings.appearance) {
                SyncSystemBars(state.settings.appearance)
                PulsoScreens(vm) }
        }
    }
}
