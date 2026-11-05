package media.whitewhale.iduo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A widget on Home's left page; the same list shows on both screens. */
data class LeftWidget(val id: Int, val heightDp: Float)

/**
 * Marks a widget setup in [WidgetController] as bound for the left page rather than a Home cell.
 * The placement's spanX and spanY carry its width and height in dp.
 */
internal const val LEFT_PAGE_SLOT = -1
internal const val LEFT_PAGE_MARKER = -100

internal const val MIN_LEFT_WIDGET_HEIGHT = 80f
internal const val MAX_LEFT_WIDGET_HEIGHT = 520f
internal const val LEFT_WIDGET_HEIGHT_STEP = 40f
private const val LEFT_WIDGET_CELL_HEIGHT = 90f

/** A new widget's height: what its provider asks for, in cells where it says so. */
internal fun defaultLeftWidgetHeight(minHeightDp: Float, targetCellHeight: Int): Float =
    maxOf(minHeightDp, targetCellHeight * LEFT_WIDGET_CELL_HEIGHT, 2 * LEFT_WIDGET_CELL_HEIGHT)
        .coerceIn(MIN_LEFT_WIDGET_HEIGHT, MAX_LEFT_WIDGET_HEIGHT)

/** [widgets] with [id] moved [by] places, staying within the list. */
internal fun List<LeftWidget>.movingLeftWidget(id: Int, by: Int): List<LeftWidget> {
    val from = indexOfFirst { it.id == id }
    if (from < 0) return this
    val to = (from + by).coerceIn(indices)
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** [widgets] with [id] a [by] height steps taller, staying within the limits. */
internal fun List<LeftWidget>.resizingLeftWidget(id: Int, by: Int): List<LeftWidget> = map {
    if (it.id != id) it else it.copy(heightDp = (it.heightDp + by * LEFT_WIDGET_HEIGHT_STEP)
        .coerceIn(MIN_LEFT_WIDGET_HEIGHT, MAX_LEFT_WIDGET_HEIGHT))
}

/**
 * Home's left page as a column of widgets on the bare wallpaper, for those who want neither news
 * nor widgets on Home. A press and hold opens a widget's options; its own taps and scrolling stay
 * with the widget.
 */
@Composable
internal fun LeftWidgetsPage(
    leftWidgets: List<LeftWidget>,
    controller: WidgetController,
    onAdd: () -> Unit,
    onOptions: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (leftWidgets.isEmpty()) {
        Column(modifier.testTag("left-widgets-empty"), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Widgets, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = .8f))
            Text(stringResource(R.string.info_card_detail), Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            AddLeftWidgetButton(onAdd)
        }
        return
    }
    Column(modifier.verticalScroll(rememberScrollState()).testTag("left-widgets"),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        leftWidgets.forEach { widget ->
            androidx.compose.runtime.key(widget.id) {
                val open by rememberUpdatedState { onOptions(widget.id) }
                Box(Modifier.fillMaxWidth().height(widget.heightDp.dp).testTag("left-widget-${widget.id}")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            awaitWidgetLongPressOrCancellation(down) ?: return@awaitEachGesture
                            open()
                            // The rest of the press belongs to the menu, not the widget under it.
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }) {
                    WidgetSlot(widget.id, LEFT_PAGE_SLOT, controller, Modifier.fillMaxSize(), { onOptions(widget.id) }) {
                        when (widget.id) {
                            CLOCK_WIDGET -> ClockCard { onOptions(widget.id) }
                            DATE_WIDGET -> DateCard { onOptions(widget.id) }
                            INFO_WIDGET -> GlassCard(onClick = { onOptions(widget.id) }) {
                                Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Text(stringResource(R.string.your_widgets), color = Color.White)
                            }
                            else -> GlassCard(onClick = { onOptions(widget.id) }) {
                                Text(stringResource(R.string.widget_unavailable), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        AddLeftWidgetButton(onAdd)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AddLeftWidgetButton(onAdd: () -> Unit) {
    FilledTonalButton(onClick = onAdd, Modifier.testTag("left-widgets-add")) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.add_widget))
    }
}

/** What a widget on the left page can do: its place in the list, its height, settings and removal. */
@Composable
internal fun LeftWidgetActions(
    title: String,
    index: Int,
    count: Int,
    heightDp: Float,
    canConfigure: Boolean,
    onMove: (Int) -> Unit,
    onResize: (Int) -> Unit,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("left-widget-actions")) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text(stringResource(R.string.move_earlier)) }
        TextButton(onClick = { onMove(1) }, enabled = index < count - 1) { Text(stringResource(R.string.move_later)) }
        HorizontalDivider()
        TextButton(onClick = { onResize(1) }, enabled = heightDp < MAX_LEFT_WIDGET_HEIGHT) { Text(stringResource(R.string.increase_height)) }
        TextButton(onClick = { onResize(-1) }, enabled = heightDp > MIN_LEFT_WIDGET_HEIGHT) { Text(stringResource(R.string.decrease_height)) }
        HorizontalDivider()
        if (canConfigure) TextButton(onClick = onConfigure) { Text(stringResource(R.string.widget_settings)) }
        TextButton(onClick = onRemove, modifier = Modifier.testTag("left-widget-remove")) { Text(stringResource(R.string.remove_widget)) }
    }
}
