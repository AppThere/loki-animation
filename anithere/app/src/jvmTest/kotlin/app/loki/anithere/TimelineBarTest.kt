package app.loki.anithere

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineBarTest {
    @Test fun playbackAdvancesWrapsWhenLoopingAndStopsAtTheEndWhenNot() {
        assertEquals(5L, nextFrame(4, 48, true)); assertEquals(0L, nextFrame(47, 48, true)); assertNull(nextFrame(47, 48, false))
        assertNull(nextFrame(0, 0, true), "a movie of no frames does not play"); assertEquals(1L, nextFrame(0, 2, false))
    }

    @Test fun aFrameLastsAsLongAsTheRateSays() {
        assertEquals(41L, frameMillis(24.0)); assertEquals(33L, frameMillis(30.0)); assertEquals(1000L, frameMillis(1.0))
        assertEquals(41L, frameMillis(0.0), "a nonsense rate is 24"); assertEquals(41L, frameMillis(Double.NaN)); assertEquals(41L, frameMillis(1e9))
    }

    /** Every command the bar and the timeline send is in the engine's list, and the keys sent are in its doc. */
    @Test fun everyTimelineCallIsOnACommandTheEngineHasWithKeysItDocuments() {
        val docs = (Json.parseToJsonElement(java.io.File("src/jvmTest/resources/engine-commands.json").readText()) as JsonArray)
            .associate { c -> val o = c as JsonObject; (o["id"] as JsonPrimitive).content to (o["params"] as JsonPrimitive).content }
        val sent = mapOf(
            "timeline.insert_frame" to emptyList(), "timeline.remove_frame" to emptyList(), "timeline.insert_keyframe" to emptyList(), "timeline.insert_blank_keyframe" to emptyList(),
            "timeline.clear_keyframe" to emptyList(), "timeline.create_motion_tween" to emptyList(), "timeline.create_tween" to emptyList(), "timeline.create_shape_tween" to emptyList(),
            "timeline.remove_tween" to emptyList(), "timeline.set_easing" to listOf("easing"), "timeline.goto" to listOf("frame"),
            "timeline.move_spans" to listOf("layer", "from", "by"), "timeline.resize_span" to listOf("layer", "frame", "frames"),
            "timeline.first_frame" to emptyList(), "timeline.prev_frame" to emptyList(), "timeline.next_frame" to emptyList(), "timeline.last_frame" to emptyList(),
        )
        for ((command, keys) in sent) {
            val doc = docs[command] ?: error("the engine has no $command")
            for (k in keys) assertTrue(Regex("[{, ]$k[?:,}]").containsMatchIn(doc), "$command does not document $k: $doc")
        }
    }
}
