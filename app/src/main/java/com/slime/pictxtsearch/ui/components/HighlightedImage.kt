package com.slime.pictxtsearch.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.slime.pictxtsearch.R
import com.slime.pictxtsearch.model.MatchBox
import com.slime.pictxtsearch.model.isAxisAlignedPolygon
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


@Composable
fun HighlightedImage(
    bitmap: Bitmap,
    matches: List<MatchBox>,
    highlightColor: Color,
    highlightAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Box(modifier = modifier) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (matches.isEmpty()) return@Canvas
            val scale = min(size.width / bitmap.width, size.height / bitmap.height)
            val offsetX = (size.width - bitmap.width * scale) / 2f
            val offsetY = (size.height - bitmap.height * scale) / 2f
            val corner = 4.dp.toPx()

            matches.forEach { match ->
                if (match.polygon.size >= 3 && !match.polygon.isAxisAlignedPolygon()) {
                    val path = Path()
                    match.polygon.forEachIndexed { index, point ->
                        val x = offsetX + point.x * scale
                        val y = offsetY + point.y * scale
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = highlightColor.copy(alpha = highlightAlpha),
                    )
                    drawPath(
                        path = path,
                        color = highlightColor.copy(alpha = (highlightAlpha + 0.4f).coerceAtMost(1f)),
                        style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round),
                    )
                    return@forEach
                }

                val left = offsetX + match.box.left * scale
                val top = offsetY + match.box.top * scale
                val width = (match.box.width * scale).coerceAtLeast(1f)
                val height = (match.box.height * scale).coerceAtLeast(1f)
                val topLeft = Offset(left, top)
                val boxSize = Size(width, height)

                drawRoundRect(
                    color = highlightColor.copy(alpha = highlightAlpha),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(corner, corner),
                )
                drawRoundRect(
                    color = highlightColor.copy(alpha = (highlightAlpha + 0.4f).coerceAtMost(1f)),
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}


@Composable
fun ZoomableHighlightedImage(
    bitmap: Bitmap,
    matches: List<MatchBox>,
    highlightColor: Color,
    highlightAlpha: Float,
    focusMatch: MatchBox? = null,
    focusToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val flingScope = rememberCoroutineScope()
    val flingJob = remember { mutableStateOf<Job?>(null) }
    val focusJob = remember { mutableStateOf<Job?>(null) }
    val zoomLabel = if (scale <= 1f) {
        stringResource(R.string.original_size)
    } else {
        stringResource(R.string.zoom_format, ((scale * 10).roundToInt() / 10f).toString())
    }
    val imageDescription = stringResource(R.string.image_description)

    fun startFling(vx: Float, vy: Float, containerWidth: Float, containerHeight: Float) {
        if (containerWidth <= 0f || containerHeight <= 0f) return
        val fit = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
        val contentWidth = bitmap.width * fit
        val contentHeight = bitmap.height * fit
        val currentScale = scale
        val maxX = ((contentWidth * currentScale - containerWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((contentHeight * currentScale - containerHeight) / 2f).coerceAtLeast(0f)
        if (maxX <= 0f && maxY <= 0f) return

        flingJob.value?.cancel()
        flingJob.value = flingScope.launch {
            val start = offset
            val xAnimation = Animatable(start.x)
            val yAnimation = Animatable(start.y)
            coroutineScope {
                if (maxX > 0f) {
                    launch {
                        xAnimation.animateDecay(
                            initialVelocity = vx,
                            animationSpec = exponentialDecay<Float>(frictionMultiplier = 2f),
                        ) {
                            val clamped = value.coerceIn(-maxX, maxX)
                            offset = offset.copy(x = clamped)
                        }
                    }
                }
                if (maxY > 0f) {
                    launch {
                        yAnimation.animateDecay(
                            initialVelocity = vy,
                            animationSpec = exponentialDecay<Float>(frictionMultiplier = 2f),
                        ) {
                            val clamped = value.coerceIn(-maxY, maxY)
                            offset = offset.copy(y = clamped)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(bitmap) {
        flingJob.value?.cancel()
        focusJob.value?.cancel()
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .testTag("imageViewer")
            .semantics {
                contentDescription = imageDescription
                stateDescription = zoomLabel
            }
            .background(Color.Black)
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob.value?.cancel()
                    focusJob.value?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pastSlop = false
                    var accumulatedPan = Offset.Zero
                    var accumulatedZoom = 1f
                    var hadZoom = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        val containerWidth = size.width.toFloat()
                        val containerHeight = size.height.toFloat()

                        if (!pastSlop) {
                            accumulatedPan += panChange
                            accumulatedZoom *= zoomChange
                            if (accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                                abs(accumulatedZoom - 1f) > 0.01f
                            ) {
                                pastSlop = true
                            }
                        }

                        if (pastSlop && containerWidth > 0f && containerHeight > 0f) {
                            val fit = min(
                                containerWidth / bitmap.width,
                                containerHeight / bitmap.height,
                            )
                            val contentWidth = bitmap.width * fit
                            val contentHeight = bitmap.height * fit
                            val oldScale = scale
                            val newScale = (oldScale * zoomChange).coerceIn(1f, 8f)
                            val ratio = newScale / oldScale
                            val pivot = if (centroid == Offset.Unspecified) {
                                Offset(containerWidth / 2f, containerHeight / 2f)
                            } else {
                                centroid
                            }
                            val targetX = (1f - ratio) * (pivot.x - containerWidth / 2f) +
                                ratio * offset.x + panChange.x
                            val targetY = (1f - ratio) * (pivot.y - containerHeight / 2f) +
                                ratio * offset.y + panChange.y
                            val maxX = ((contentWidth * newScale - containerWidth) / 2f)
                                .coerceAtLeast(0f)
                            val maxY = ((contentHeight * newScale - containerHeight) / 2f)
                                .coerceAtLeast(0f)

                            scale = newScale
                            offset = Offset(
                                x = targetX.coerceIn(-maxX, maxX),
                                y = targetY.coerceIn(-maxY, maxY),
                            )
                            hadZoom = hadZoom || abs(zoomChange - 1f) > 0.01f

                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) change.consume()
                            }
                            val primary = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.first()
                            tracker.addPosition(primary.uptimeMillis, primary.position)
                        }

                        if (event.changes.none { it.pressed }) break
                    }

                    if (pastSlop && !hadZoom) {
                        val velocity = tracker.calculateVelocity()
                        startFling(
                            vx = velocity.x,
                            vy = velocity.y,
                            containerWidth = size.width.toFloat(),
                            containerHeight = size.height.toFloat(),
                        )
                    }
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        flingJob.value?.cancel()
                        focusJob.value?.cancel()
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                )
            },
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        if (constraints.hasBoundedWidth &&
            constraints.hasBoundedHeight &&
            containerWidth > 0f &&
            containerHeight > 0f
        ) {
            val fit = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
            val contentWidth = bitmap.width * fit
            val contentHeight = bitmap.height * fit
            val widthDp = with(density) { contentWidth.toDp() }
            val heightDp = with(density) { contentHeight.toDp() }

            LaunchedEffect(widthDp, heightDp) {
                val maxX = ((contentWidth * scale - containerWidth) / 2f).coerceAtLeast(0f)
                val maxY = ((contentHeight * scale - containerHeight) / 2f).coerceAtLeast(0f)
                offset = Offset(
                    x = offset.x.coerceIn(-maxX, maxX),
                    y = offset.y.coerceIn(-maxY, maxY),
                )
            }

            LaunchedEffect(focusToken, focusMatch) {
                val match = focusMatch
                if (match == null) {
                    focusJob.value?.cancel()
                    return@LaunchedEffect
                }
                if (focusToken <= 0) return@LaunchedEffect
                val matchWidth = (match.box.width * fit).coerceAtLeast(1f)
                val matchHeight = (match.box.height * fit).coerceAtLeast(1f)
                val desiredScale = min(
                    (containerWidth * 0.55f) / matchWidth,
                    (containerHeight * 0.30f) / matchHeight,
                ).coerceIn(1.5f, 4f)
                val maxX = ((contentWidth * desiredScale - containerWidth) / 2f)
                    .coerceAtLeast(0f)
                val maxY = ((contentHeight * desiredScale - containerHeight) / 2f)
                    .coerceAtLeast(0f)
                val matchCenterX = (match.box.left + match.box.right) / 2f
                val matchCenterY = (match.box.top + match.box.bottom) / 2f
                val targetX = (
                    -desiredScale * (matchCenterX - bitmap.width / 2f) * fit
                    ).coerceIn(-maxX, maxX)
                val targetY = (
                    -desiredScale * (matchCenterY - bitmap.height / 2f) * fit
                    ).coerceIn(-maxY, maxY)
                val startScale = scale
                val startOffset = offset
                focusJob.value?.cancel()
                flingJob.value?.cancel()
                focusJob.value = flingScope.launch {
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 360,
                            easing = FastOutSlowInEasing,
                        ),
                    ) { fraction, _ ->
                        scale = startScale + (desiredScale - startScale) * fraction
                        offset = Offset(
                            x = startOffset.x + (targetX - startOffset.x) * fraction,
                            y = startOffset.y + (targetY - startOffset.y) * fraction,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(widthDp, heightDp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                HighlightedImage(
                    bitmap = bitmap,
                    matches = matches,
                    highlightColor = highlightColor,
                    highlightAlpha = highlightAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
