package dev.pulso.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.pulso.app.domain.TaskRules

/** SPEC 011: shown once before the first dictation and always in Settings → Privacidade. */
const val VOICE_PRIVACY_NOTICE =
    "A transcrição é feita pelo serviço de voz do Android do seu aparelho. Dependendo do provedor, " +
        "o áudio pode ser processado online. O Pulso recebe só o texto."

const val VOICE_ERROR_MESSAGE = "Não entendi. Tente de novo ou digite."

/** Extra read by CaptureActivity to open the system recognizer right away (launcher shortcut). */
const val EXTRA_START_VOICE = "dev.pulso.app.extra.START_VOICE"

private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua anotação")
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // only a preference; the provider decides
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}

/** True when some app on the device handles speech recognition (needs the <queries> entry on API 30+). */
fun isVoiceCaptureAvailable(context: Context): Boolean =
    recognizerIntent().resolveActivity(context.packageManager) != null

/** Appends dictated text to what is already typed, respecting the capture limit. */
fun appendDictation(current: String, dictated: String): String {
    val clean = dictated.trim()
    val joined = if (current.isBlank()) clean else current.trimEnd() + " " + clean
    return joined.take(TaskRules.MAX_CAPTURE)
}

@Stable
class VoiceCaptureState internal constructor(
    val available: Boolean,
    private val noticeShown: Boolean,
    private val launch: () -> Unit,
    private val markNotice: () -> Unit,
) {
    internal var noticeOpen by mutableStateOf(false)

    /** Opens the recognizer, or the one-time privacy notice first. */
    fun start() {
        if (!available) return
        if (noticeShown) launch() else noticeOpen = true
    }

    internal fun confirmNotice() { noticeOpen = false; markNotice(); launch() }
}

/**
 * Wraps the system recognizer. [onText] gets the recognized text (never saved by this layer; callers only put it in
 * the field); [onError] gets a discreet message. Cancelling shows nothing. The recognized text is never logged.
 */
@Composable
fun rememberVoiceCapture(
    noticeShown: Boolean,
    onNoticeShown: () -> Unit,
    onText: (String) -> Unit,
    onError: (String) -> Unit,
): VoiceCaptureState {
    val context = LocalContext.current
    val text by rememberUpdatedState(onText)
    val error by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_CANCELED -> Unit
            Activity.RESULT_OK -> {
                val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (heard.isNullOrBlank()) error(VOICE_ERROR_MESSAGE) else text(heard)
            }
            else -> error(VOICE_ERROR_MESSAGE)
        }
    }
    val available = remember(context) { isVoiceCaptureAvailable(context) }
    val markNotice by rememberUpdatedState(onNoticeShown)
    val launch = {
        try { launcher.launch(recognizerIntent()) } catch (_: ActivityNotFoundException) { error(VOICE_ERROR_MESSAGE) }
    }
    return remember(available, noticeShown, launcher) {
        VoiceCaptureState(available, noticeShown, launch, { markNotice() })
    }
}

/** Mic button ("Ditar anotação"). Renders nothing without a recognizer; the host must also call [VoiceNoticeDialog]. */
@Composable
fun VoiceCaptureButton(voice: VoiceCaptureState, enabled: Boolean = true) {
    if (!voice.available) return
    IconButton(onClick = voice::start, enabled = enabled) {
        Icon(Icons.Rounded.Mic, contentDescription = "Ditar anotação")
    }
}

@Composable
fun VoiceNoticeDialog(voice: VoiceCaptureState) {
    if (!voice.noticeOpen) return
    AlertDialog(
        onDismissRequest = { voice.noticeOpen = false },
        title = { Text("Ditar anotação") },
        text = { Text(VOICE_PRIVACY_NOTICE) },
        confirmButton = { TextButton(onClick = voice::confirmNotice) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = { voice.noticeOpen = false }) { Text("Cancelar") } },
    )
}
