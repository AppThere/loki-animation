package app.loki.anithere

// This file is the app's own (egui2compose writes it once). AniCraft is a 2D animation studio: the
// surface is the stage (the engine renders the frame at the playhead) over the timeline (Editor.kt,
// from `doc.info`); the tools draw and select on the stage; the dock has Layers, Library and Scenes.
// Written ONCE by egui2compose for an app with the art workspace and never written again. It says what the generic workspace cannot know: which
// command describes the document, which panels the dock has and what they draw, how a tool is
// picked, and what the status bar says. The defaults below work for any engine; replace them.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.loki.kit.ColorPickerDialog
import app.loki.kit.ColorWell
import app.loki.kit.DockPanel
import app.loki.kit.EngineClient
import app.loki.kit.ModifierChips
import app.loki.kit.RailTool
import app.loki.kit.hexOf
import app.loki.kit.parseHex
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronsLeft
import com.composables.icons.lucide.ChevronsRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Trash2
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Read after every change: the document's frame, layers with their keyframes, library, scenes and selection. */
val STATE_COMMANDS: List<String> = listOf("doc.info")

internal fun JsonElement?.obj() = this as? JsonObject
internal fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
internal fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonElement?.num(key: String): Double? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull
internal fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonElement?.bool(key: String): Boolean? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

fun docOf(state: JsonElement?): JsonElement? = (state as? JsonObject)?.get("doc.info")

val PANEL_COMMANDS: Set<String> = setOf(
    "file.open", "file.save", "file.save_as", "file.export_png", "layer.select", "layer.toggle_visible", "layer.toggle_lock", "layer.new", "layer.delete", "timeline.goto",
    "timeline.first_frame", "timeline.prev_frame", "timeline.next_frame", "timeline.last_frame", "timeline.insert_keyframe", "element.pick", "element.translate",
    "element.set_fill", "draw.rect", "draw.ellipse", "draw.star", "draw.line", "library.place", "scene.new", "control.test_movie", "doc.set_stage",
)

val FIRST_PANEL: String? = "layers"

val PANEL_GROUPS: List<List<String>> = listOf(listOf("layers", "library", "scenes"))

fun documentTitle(state: JsonElement?): String = docOf(state).obj()?.get("scenes").arr().getOrNull((docOf(state).long("scene") ?: 0L).toInt()).str("name") ?: APP_NAME

fun documentDirty(state: JsonElement?): Boolean = docOf(state).bool("dirty") == true

fun statusSegments(state: JsonElement?): List<String> {
    val d = docOf(state)
    val st = d.obj()?.get("stage")
    return listOfNotNull(
        "frame ${(d.long("frame") ?: 0) + 1} / ${d.long("frames") ?: 0}",
        st?.num("fps")?.let { "${it.toInt()} fps" },
        st?.let { "${it.long("width")} × ${it.long("height")}" }?.takeIf { st.long("width") != null },
        d.obj()?.get("selection").arr().size.takeIf { it > 0 }?.let { "$it selected" },
    )
}

/** The tools that act on the stage: select, the shapes and the hand. */
private val RAIL: List<String> = listOf("Select", "Rect", "Oval", "Star", "Line", "Hand")

fun loadTools(engine: EngineClient): List<RailTool> = RAIL.mapNotNull { id -> TOOLS.firstOrNull { it.id == id } }.map { RailTool(it.id, it.title, icon = toolIcon(it.id)) }

/** The tools are the front end's own: the stage reads the one in hand (Editor.kt). */
fun toolCommand(id: String): Pair<String, JsonObject>? = null

fun toolInHand(state: JsonElement?): String? = null

/** The colours new shapes get: held here, as the desktop's tool settings hold them. */
object Paint {
    var fill by mutableStateOf("#4C9AFF")
    var stroke by mutableStateOf("#1F2937")
}

private fun exec(run: (String, JsonObject) -> Unit, command: String, params: JsonObject = JsonObject(emptyMap())) =
    run("engine.execute", buildJsonObject { put("command", command); put("params", params) })

/** The playhead and the keyframe and layer actions, and the colours new shapes get. */
@Composable
fun OptionsBar(tool: String, tools: List<RailTool>, state: JsonElement?, run: (String, JsonObject) -> Unit) {
    val d = docOf(state)
    var picking by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ModifierChips()
        IconButton({ exec(run, "timeline.first_frame") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronsLeft, "First frame", Modifier.size(18.dp)) }
        IconButton({ exec(run, "timeline.prev_frame") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronLeft, "Previous frame", Modifier.size(18.dp)) }
        Text("${(d.long("frame") ?: 0) + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton({ exec(run, "timeline.next_frame") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronRight, "Next frame", Modifier.size(18.dp)) }
        IconButton({ exec(run, "timeline.last_frame") }, Modifier.size(36.dp)) { Icon(Lucide.ChevronsRight, "Last frame", Modifier.size(18.dp)) }
        IconButton({ exec(run, "timeline.insert_keyframe") }, Modifier.size(36.dp)) { Icon(Lucide.Plus, "Insert a keyframe", Modifier.size(18.dp)) }
        IconButton({ exec(run, "edit.delete") }, Modifier.size(36.dp)) { Icon(Lucide.Trash2, "Delete the selection", Modifier.size(18.dp)) }
        ColorWell(parseHex(Paint.fill), "Fill colour", Modifier.size(28.dp)) { picking = "fill" }
        Row(Modifier.padding(start = 4.dp)) { ColorWell(parseHex(Paint.stroke), "Stroke colour", Modifier.size(28.dp)) { picking = "stroke" } }
    }
    picking?.let { which ->
        ColorPickerDialog(if (which == "fill") "Fill" else "Stroke", parseHex(if (which == "fill") Paint.fill else Paint.stroke), onPick = { c ->
            picking = null
            if (c != null) {
                if (which == "fill") { Paint.fill = hexOf(c); exec(run, "element.set_fill", buildJsonObject { put("color", hexOf(c)) }) } else Paint.stroke = hexOf(c)
            }
        }, onDismiss = { picking = null })
    }
}

@Composable
fun ToolFooter(state: JsonElement?, run: (String, JsonObject) -> Unit) {}

// Open, save and export are the desktop's file dialogs; the engine takes paths.
val APP_ACTIONS: List<app.loki.kit.RibbonItem> = listOf(
    app.loki.kit.RibbonItem("app.open", "Open…", ""),
    app.loki.kit.RibbonItem("app.save", "Save", ""),
    app.loki.kit.RibbonItem("app.saveAs", "Save As…", ""),
    app.loki.kit.RibbonItem("app.exportPng", "Export Frame as PNG…", ""),
)
val APP_ACTION_IDS: Set<String> = APP_ACTIONS.map { it.id }.toSet()

fun withAppActions(tabs: List<app.loki.kit.RibbonTab>): List<app.loki.kit.RibbonTab> = app.loki.kit.addToFileMenu(tabs, APP_ACTIONS)

suspend fun appAction(id: String, state: JsonElement?, io: app.loki.kit.FileIo) {
    fun cmd(command: String, params: JsonObject) = buildJsonObject { put("command", command); put("params", params) }
    suspend fun saveTo(p: String, command: String, params: JsonObject, then: String) {
        if (io.call("engine.execute", cmd(command, params)) != null) { io.publish(p); io.refresh(); io.status("$then ${p.substringAfterLast('/')}") }
    }
    when (id) {
        "app.open" -> {
            val p = io.pick(false, "") ?: return
            if (io.call("engine.execute", cmd("file.open", buildJsonObject { put("path", p) })) != null) io.refresh()
        }
        "app.save" -> appAction("app.saveAs", state, io)
        "app.saveAs" -> {
            val p = io.pick(true, "Animation.anicraft") ?: return
            saveTo(p, "file.save_as", buildJsonObject { put("path", p) }, "Saved")
        }
        "app.exportPng" -> {
            val p = io.pick(true, "Frame.png") ?: return
            saveTo(p, "file.export_png", buildJsonObject { put("path", p); put("frame", docOf(state).long("frame") ?: 0L) }, "Exported")
        }
    }
}

fun dockPanels(state: JsonElement?, revision: Int, run: (String, JsonObject) -> Unit): List<DockPanel> = listOf(
    DockPanel("layers", "Layers", Lucide.Layers) { m -> LayersPanel(state, run, m) },
    DockPanel("library", "Library", Lucide.Library) { m -> LibraryPanel(state, run, m) },
    DockPanel("scenes", "Scenes", Lucide.Film) { m -> ScenesPanel(state, m) },
)

@Composable
private fun LayersPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val d = docOf(state)
    val layers = d.obj()?.get("layers").arr()
    val current = d.long("layer")
    androidx.compose.foundation.layout.Column(modifier) {
        Row(Modifier.padding(horizontal = 8.dp)) {
            androidx.compose.material3.TextButton({ exec(run, "layer.new") }) { Text("New layer") }
            androidx.compose.material3.TextButton({ exec(run, "layer.delete") }) { Text("Delete") }
        }
        LazyColumn {
            items(layers) { l ->
                val id = l.long("id") ?: return@items
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { exec(run, "layer.select", buildJsonObject { put("layer", id) }) }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.str("name") ?: "Layer", Modifier.weight(1f), maxLines = 1, fontWeight = if (id == current) FontWeight.Bold else FontWeight.Normal)
                    IconButton({ exec(run, "layer.toggle_visible", buildJsonObject { put("layer", id) }) }, Modifier.size(36.dp)) { Icon(if (l.bool("visible") == false) Lucide.EyeOff else Lucide.Eye, "Show or hide", Modifier.size(18.dp)) }
                    IconButton({ exec(run, "layer.toggle_lock", buildJsonObject { put("layer", id) }) }, Modifier.size(36.dp)) { Icon(if (l.bool("locked") == true) Lucide.Lock else Lucide.LockOpen, "Lock or unlock", Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LibraryPanel(state: JsonElement?, run: (String, JsonObject) -> Unit, modifier: Modifier) {
    val items = docOf(state).obj()?.get("library").arr()
    if (items.isEmpty()) { Text("The library is empty: Modify › Convert to Symbol adds to it.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    LazyColumn(modifier) {
        items(items) { s ->
            val id = s.long("id") ?: return@items
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { exec(run, "library.place", buildJsonObject { put("id", id) }) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.str("name") ?: "Symbol", Modifier.weight(1f), maxLines = 1)
                Text((s.str("kind") ?: "").lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScenesPanel(state: JsonElement?, modifier: Modifier) {
    val d = docOf(state)
    val current = d.long("scene")
    LazyColumn(modifier) {
        items(d.obj()?.get("scenes").arr().withIndex().toList()) { (i, s) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.str("name") ?: "Scene", Modifier.weight(1f), fontWeight = if (i.toLong() == current) FontWeight.Bold else FontWeight.Normal)
                Text("${s.long("frames") ?: 0} frames", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
