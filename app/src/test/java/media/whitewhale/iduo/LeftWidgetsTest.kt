package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class LeftWidgetsTest {
    private val widgets = listOf(LeftWidget(1, 120f), LeftWidget(2, 200f), LeftWidget(3, 300f))

    @Test fun `a widget moves within the list and stops at its ends`() {
        assertEquals(listOf(2, 1, 3), widgets.movingLeftWidget(2, -1).map { it.id })
        assertEquals(listOf(1, 3, 2), widgets.movingLeftWidget(2, 1).map { it.id })
        assertEquals(listOf(1, 2, 3), widgets.movingLeftWidget(1, -1).map { it.id })
        assertEquals(listOf(1, 2, 3), widgets.movingLeftWidget(3, 1).map { it.id })
        assertEquals(widgets, widgets.movingLeftWidget(9, 1))
    }

    @Test fun `height changes in steps and stays within its limits`() {
        assertEquals(240f, widgets.resizingLeftWidget(2, 1)[1].heightDp, 0f)
        assertEquals(160f, widgets.resizingLeftWidget(2, -1)[1].heightDp, 0f)
        assertEquals(MIN_LEFT_WIDGET_HEIGHT, listOf(LeftWidget(1, 90f)).resizingLeftWidget(1, -1)[0].heightDp, 0f)
        assertEquals(MAX_LEFT_WIDGET_HEIGHT, listOf(LeftWidget(1, 510f)).resizingLeftWidget(1, 1)[0].heightDp, 0f)
    }

    @Test fun `a new widget takes what its provider asks for, within limits`() {
        assertEquals(180f, defaultLeftWidgetHeight(40f, 0), 0f)
        assertEquals(270f, defaultLeftWidgetHeight(40f, 3), 0f)
        assertEquals(300f, defaultLeftWidgetHeight(300f, 2), 0f)
        assertEquals(MAX_LEFT_WIDGET_HEIGHT, defaultLeftWidgetHeight(900f, 2), 0f)
    }
}
