package dev.pulso.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import dev.pulso.app.ui.components.PulseMark
import dev.pulso.app.ui.theme.PulsoMotion
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulso.app.ui.PulsoViewModel
import dev.pulso.app.ui.PulsoViewModelFactory
import dev.pulso.app.ui.theme.PulsoTheme
import dev.pulso.app.ui.theme.SyncSystemBars

/** Translucent quick capture: a bottom panel over whatever the user was doing (launcher/widget). */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val factory = PulsoViewModelFactory((application as PulsoApplication).container)
        setContent {
            val vm: PulsoViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            PulsoTheme(state.settings.appearance) {
                SyncSystemBars(state.settings.appearance)
                QuickCapture(onSave = { text, done -> vm.capture(text, done) }, onClose = ::finish) }
        }
    }
}

@Composable
private fun QuickCapture(onSave: (String, (Throwable?) -> Unit) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    fun submit() {
        if (text.isBlank() || busy) return
        busy = true
        onSave(text) { failure ->
            busy = false
            if (failure == null) onClose() else error = failure.message
        }
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(if (shown) 0.40f else 0f, tween(PulsoMotion.MEDIUM), label = "scrim")
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Fechar captura") { if (!busy) onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(shown, enter = slideInVertically(PulsoMotion.gentle()) { it } + fadeIn()) {
        Surface(
            Modifier.fillMaxWidth().imePadding()
                // Swallow taps on the panel so they don't dismiss the capture.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulseMark(size = 40.dp, animate = true)
                    Column {
                        Text("Captura rápida", style = MaterialTheme.typography.titleLarge)
                        Text("Só escreva. Você organiza depois.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextField(text, onValueChange = { if (it.length <= 500) text = it },
                    placeholder = { Text("O que não pode esquecer?") },
                    supportingText = { Text("${text.length}/500") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 3, maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onClose, enabled = !busy) { Text("Cancelar") }
                    Button(onClick = { submit() }, enabled = text.isNotBlank() && !busy) {
                        Icon(Icons.Rounded.Inbox, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Salvando…" else "Salvar na entrada")
                    }
                }
            }
        }
        }
    }
}
