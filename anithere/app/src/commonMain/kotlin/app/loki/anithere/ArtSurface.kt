package app.loki.anithere

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The stage over the timeline (Editor.kt). */
@Composable
fun ArtSurface(native: SurfaceEngine, revision: Int, modifier: Modifier) = Editor(native, revision, modifier)
