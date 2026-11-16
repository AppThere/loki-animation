package app.loki.anithere

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.loki.kit.ChoiceChip
import app.loki.kit.ToggleChip
import app.loki.kit.WorkspaceContext
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/** One engine command with these params, as the workspace runs it (by its own id: `engine.execute` is not a command). */
fun WorkspaceContext.command(command: String, params: JsonObject = JsonObject(emptyMap()), then: () -> Unit = {}) = run(command, params) { then() }

/** Goes to [frame] and returns once the engine has: playback waits for each frame, so it cannot fall behind itself. */
suspend fun WorkspaceContext.goto(frame: Long) = suspendCancellableCoroutine { c: CancellableContinuation<Unit> ->
    command("timeline.goto", buildJsonObject { put("frame", frame.coerceAtLeast(0L)) }) { if (c.isActive) c.resume(Unit) }
}

/** The frame after [frame] when playing a movie of [frames] frames: it wraps to the first if [loop], else it stops (null) at the end. */
fun nextFrame(frame: Long, frames: Long, loop: Boolean): Long? = when {
    frames <= 0L -> null
    frame + 1 < frames -> frame + 1
    loop -> 0L
    else -> null
}

/** How long a frame lasts at [fps], in milliseconds; a nonsense rate is the usual 24. */
fun frameMillis(fps: Double): Long = (1000.0 / (if (fps.isFinite() && fps in 1.0..240.0) fps else 24.0)).toLong().coerceAtLeast(1L)

private val EASES = listOf("Linear", "QuadIn", "QuadOut", "QuadInOut", "CubicIn", "CubicOut", "CubicInOut", "SineInOut", "BackOut", "ElasticOut", "BounceOut").map { it to it.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") }
private val TWEENS = listOf("timeline.create_motion_tween" to "Motion tween", "timeline.create_tween" to "Classic tween", "timeline.create_shape_tween" to "Shape tween", "timeline.remove_tween" to "Remove tween")

/**
 * The transport (first, previous, play, next, last, loop) and the timeline's editing commands (frames,
 * keyframes, tweens, easing), which act on the layer and the frame in hand.
 */
@Composable
fun TimelineBar(ws: WorkspaceContext, frame: Long, frames: Long, fps: Double, playing: Boolean, loop: Boolean, onPlaying: (Boolean) -> Unit, onLoop: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val size = 36.dp
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onPlaying(false); ws.command("timeline.first_frame") }, Modifier.size(size)) { Icon(Lucide.SkipBack, "First frame", Modifier.size(18.dp)) }
        IconButton({ onPlaying(false); ws.command("timeline.prev_frame") }, Modifier.size(size)) { Icon(Lucide.ChevronLeft, "Previous frame", Modifier.size(18.dp)) }
        IconButton({ onPlaying(!playing) }, Modifier.size(size)) { Icon(if (playing) Lucide.Pause else Lucide.Play, if (playing) "Pause" else "Play", Modifier.size(18.dp)) }
        IconButton({ onPlaying(false); ws.command("timeline.next_frame") }, Modifier.size(size)) { Icon(Lucide.ChevronRight, "Next frame", Modifier.size(18.dp)) }
        IconButton({ onPlaying(false); ws.command("timeline.last_frame") }, Modifier.size(size)) { Icon(Lucide.SkipForward, "Last frame", Modifier.size(18.dp)) }
        ToggleChip("Loop", loop, onLoop)
        Text("  Frame ${frame + 1} / $frames  ·  ${if (fps == fps.toLong().toDouble()) fps.toLong().toString() else fps.toString()} fps  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((command, label) in listOf("timeline.insert_frame" to "+ Frame", "timeline.remove_frame" to "− Frame", "timeline.insert_keyframe" to "Keyframe", "timeline.insert_blank_keyframe" to "Blank key", "timeline.clear_keyframe" to "Clear key")) {
            OutlinedButton({ ws.command(command) }, Modifier.padding(end = 4.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
        }
        ChoiceChip("Tween", null, TWEENS, onChange = { ws.command(it) })
        Text(" ")
        ChoiceChip("Ease", null, EASES, onChange = { e -> ws.command("timeline.set_easing", buildJsonObject { put("easing", e) }) })
    }
}
