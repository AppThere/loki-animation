package app.loki.anithere

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.loki.kit.ArtCanvas
import app.loki.kit.CanvasInput
import app.loki.kit.CanvasPhase
import app.loki.kit.CanvasView
import app.loki.kit.LaneTimeline
import app.loki.kit.LocalWorkspace
import app.loki.kit.TimelineClip
import app.loki.kit.TimelineLane
import app.loki.kit.WorkspaceContext
import app.loki.kit.renderScaleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.hypot

/** The most the engine is asked to render per stage unit: the engine caps the bitmap itself. */
const val MAX_RENDER_SCALE = 2f

/** The keyframes of each layer as spans of frames: a clip from the keyframe to the next. */
private fun lanesOf(state: kotlinx.serialization.json.JsonElement?): List<TimelineLane> {
    val d = docOf(state)
    val current = d.long("layer")
    return d.obj()?.get("layers").arr().map { l ->
        TimelineLane(l.long("id") ?: -1L, l.str("name") ?: "", l.obj()?.get("keyframes").arr().mapIndexed { i, k ->
            val start = k.long("start") ?: 0L
            TimelineClip(start, start.toDouble(), start + (k.long("duration") ?: 1L).toDouble(), k.str("tween") ?: "", false, dim = k.obj()?.get("elements").let { it == null || k.long("elements") == 0L })
        }, selected = l.long("id") == current, dim = l.bool("visible") == false)
    }
}

private val FRAME_STEPS = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 12.0, 24.0, 48.0, 96.0, 240.0, 720.0)

/**
 * The stage over the timeline. The stage is the engine's picture of the current frame under the
 * tool in hand: Select picks (a drag moves what was picked), Rect, Oval, Star and Line draw; the
 * timeline has a lane for each layer, a tap on the ruler goes to that frame, a tap on a span
 * selects its layer there.
 */
@OptIn(FlowPreview::class)
@Composable
fun Editor(native: SurfaceEngine, revision: Int, modifier: Modifier) {
    val ws = LocalWorkspace.current
    val d = docOf(ws?.state)
    val client = remember(native) { app.loki.kit.EngineClient(native.transport) }
    val view = remember { CanvasView() }
    val tools = remember { StageTools() }
    var page by remember { mutableStateOf<Rect?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    LaunchedEffect(view) { snapshotFlow { view.zoom }.debounce(150).collect { z -> if (z > 0f) scale = renderScaleFor(z, MAX_RENDER_SCALE) } }
    LaunchedEffect(revision, scale) {
        withContext(Dispatchers.Default) {
            val info = runCatching { client.call("loki.surface") }.getOrNull()
            val w = info.num("width"); val h = info.num("height")
            if (w != null && h != null && w > 0 && h > 0) page = Rect(0f, 0f, w.toFloat(), h.toFloat())
            image = native.renderPage(0, scale)?.let { rgbaToImageBitmap(it.width, it.height, it.rgba) } ?: image
        }
    }
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(true) }
    val frames = (d.long("frames") ?: 24L).coerceAtLeast(1L)
    val frame = d.long("frame") ?: 0L
    val fps = d.obj()?.get("stage").num("fps") ?: 24.0
    // Playback: each frame is gone to and waited for, so a slow render slows the playback and never queues up.
    LaunchedEffect(playing, fps, frames, loop) {
        val w = ws ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        var f = frame
        val per = frameMillis(fps)
        while (playing) {
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            f = nextFrame(f, frames, loop) ?: run { playing = false; return@LaunchedEffect }
            w.goto(f)
            kotlinx.coroutines.delay((per - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(1L))
        }
    }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().weight(0.58f)) {
            ArtCanvas(
                page, image, view, onInput = { input -> ws?.let { tools.onInput(input, it) } }, modifier = Modifier.fillMaxSize(),
                panTool = ws?.tool == "Hand", overlay = { v -> with(tools) { draw(v) } },
            )
        }
        if (ws != null) TimelineBar(ws, frame, frames, fps, playing, loop, { playing = it }, { loop = it })
        LaneTimeline(
            lanesOf(ws?.state), total = (d.long("frames") ?: 24L).toDouble().coerceAtLeast(24.0), playhead = (d.long("frame") ?: 0L).toDouble() + 0.5,
            modifier = Modifier.fillMaxWidth().weight(0.42f), steps = FRAME_STEPS, snap = 1.0,
            onDragClip = { lane, clip, kind, delta, _ ->
                val start = clip.start.toLong()
                when (kind) {
                    app.loki.kit.ClipDrag.Move -> ws?.command("timeline.move_spans", buildJsonObject { put("layer", lane.id); put("from", start); put("by", delta.toLong()) })
                    app.loki.kit.ClipDrag.ResizeEnd -> ws?.command("timeline.resize_span", buildJsonObject { put("layer", lane.id); put("frame", start); put("frames", (clip.end - clip.start + delta).toLong().coerceAtLeast(1L)) })
                }
            }, ruler = { t, _ -> "${t.toLong() + 1}" },
            onSeek = { t -> ws?.run?.invoke("timeline.goto", buildJsonObject { put("frame", t.toLong().coerceAtLeast(0L)) }) {} },
            onTap = { lane, _, at ->
                val w = ws ?: return@LaneTimeline
                w.run("layer.select", buildJsonObject { put("layer", lane.id) }) {
                    w.run("timeline.goto", buildJsonObject { put("frame", at.toLong().coerceAtLeast(0L)) }) {}
                }
            },
        )
    }
}

/** The stage's pointer: what each tool does with a press, a drag and a lift (stage coordinates). */
class StageTools {
    private var down: Offset? = null
    private var now: Offset? = null
    private var picked = false

    fun onInput(i: CanvasInput, ws: WorkspaceContext) {
        val p = i.points.lastOrNull() ?: return
        val at = Offset(p.x.toFloat(), p.y.toFloat())
        fun run(command: String, params: kotlinx.serialization.json.JsonObject, then: () -> Unit = {}) =
            ws.run(command, params) { then() }
        when (i.phase) {
            CanvasPhase.Down -> { down = at; now = at; picked = false }
            CanvasPhase.Drag -> now = at
            CanvasPhase.Up -> {
                val a = down ?: return
                down = null; now = null
                val dx = at.x - a.x; val dy = at.y - a.y
                val dragged = hypot(dx, dy) > 4f
                val fill = Paint.fill; val stroke = Paint.stroke
                when (ws.tool) {
                    "Select" -> if (!dragged) run("element.pick", buildJsonObject { put("x", a.x.toDouble()); put("y", a.y.toDouble()); put("add", i.mods.shift) })
                        else run("element.pick", buildJsonObject { put("x", a.x.toDouble()); put("y", a.y.toDouble()) }) {
                            run("element.translate", buildJsonObject { put("dx", dx.toDouble()); put("dy", dy.toDouble()) })
                        }
                    "Rect" -> if (dragged) run("draw.rect", buildJsonObject {
                        put("x", minOf(a.x, at.x).toDouble()); put("y", minOf(a.y, at.y).toDouble()); put("w", abs(dx).toDouble()); put("h", abs(dy).toDouble()); put("fill", fill); put("stroke", stroke)
                    })
                    "Oval" -> if (dragged) run("draw.ellipse", buildJsonObject {
                        put("x", minOf(a.x, at.x).toDouble()); put("y", minOf(a.y, at.y).toDouble()); put("w", abs(dx).toDouble()); put("h", abs(dy).toDouble()); put("fill", fill); put("stroke", stroke)
                    })
                    "Star" -> if (dragged) run("draw.star", buildJsonObject { put("cx", a.x.toDouble()); put("cy", a.y.toDouble()); put("outer", hypot(dx, dy).toDouble()); put("fill", fill); put("stroke", stroke) })
                    "Line" -> if (dragged) run("draw.line", buildJsonObject { put("x0", a.x.toDouble()); put("y0", a.y.toDouble()); put("x1", at.x.toDouble()); put("y1", at.y.toDouble()); put("stroke", stroke) })
                }
            }
            CanvasPhase.Cancel -> { down = null; now = null }
            CanvasPhase.Hover -> {}
        }
    }

    /** The rubber band of a shape being dragged out. */
    fun DrawScope.draw(view: CanvasView) {
        val a = down ?: return; val b = now ?: return
        val pa = view.toScreen(a.x.toDouble(), a.y.toDouble()); val pb = view.toScreen(b.x.toDouble(), b.y.toDouble())
        val accent = Color(0xFF4C9AFF)
        drawRect(accent, Offset(minOf(pa.x, pb.x), minOf(pa.y, pb.y)), Size(abs(pb.x - pa.x), abs(pb.y - pa.y)), style = Stroke(1.5f))
    }
}
