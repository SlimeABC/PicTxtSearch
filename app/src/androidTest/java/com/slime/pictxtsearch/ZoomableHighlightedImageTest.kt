package com.slime.pictxtsearch

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slime.pictxtsearch.ui.components.ZoomableHighlightedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZoomableHighlightedImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

    private fun showImage() {
        composeRule.setContent {
            ZoomableHighlightedImage(
                bitmap = bitmap,
                matches = emptyList(),
                highlightColor = Color.Yellow,
                highlightAlpha = 0.3f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun zoomLabel(): String =
        composeRule.onNodeWithTag("imageViewer")
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]

    @Test
    fun pinchGestureZoomsImage() {
        showImage()
        assertEquals("原始大小", zoomLabel())

        composeRule.onNodeWithTag("imageViewer").performTouchInput {
            pinch(
                center - Offset(40f, 0f),
                center + Offset(40f, 0f),
                center - Offset(120f, 0f),
                center + Offset(120f, 0f),
            )
        }
        composeRule.waitForIdle()

        assertNotEquals("原始大小", zoomLabel())
    }

    @Test
    fun doubleTapTogglesZoom() {
        showImage()
        val viewer = composeRule.onNodeWithTag("imageViewer")

        viewer.performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertNotEquals("原始大小", zoomLabel())

        viewer.performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals("原始大小", zoomLabel())
    }
}
