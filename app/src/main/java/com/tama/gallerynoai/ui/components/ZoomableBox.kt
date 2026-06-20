package com.tama.gallerynoai.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import kotlin.math.abs

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    onZoomChange: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    resetTrigger: Any? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when disabled
    LaunchedEffect(enabled) {
        if (!enabled) {
            scale = 1f
            offset = Offset.Zero
            onZoomChange(false)
        }
    }

    // Reset zoom when resetTrigger changes (e.g., page change)
    LaunchedEffect(resetTrigger) {
        scale = 1f
        offset = Offset.Zero
        onZoomChange(false)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape)
    ) {
        val currentConstraints = constraints

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentConstraints, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        // Wait for first touch
                        awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            
                            if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    pan += panChange
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1 - zoom) * centroidSize
                                    val panMotion = pan.getDistance()

                                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, maxScale)
                                    scale = newScale

                                    val maxX = (currentConstraints.maxWidth.toFloat() * (scale - 1)) / 2
                                    val maxY = (currentConstraints.maxHeight.toFloat() * (scale - 1)) / 2

                                    val newOffset = offset + panChange
                                    val clampedOffset = Offset(
                                        newOffset.x.coerceIn(-maxX, maxX),
                                        newOffset.y.coerceIn(-maxY, maxY)
                                    )

                                    // Gesture Hand-off Logic:
                                    // If we are at the horizontal edge and the user tries to pan further out,
                                    // we DON'T consume the horizontal pan.
                                    val atLeftEdge = offset.x >= maxX && panChange.x > 0
                                    val atRightEdge = offset.x <= -maxX && panChange.x < 0
                                    
                                    val shouldConsumeHorizontal = scale > 1.01f && !atLeftEdge && !atRightEdge
                                    val shouldConsumeZoom = zoomChange != 1f

                                    if (shouldConsumeHorizontal || shouldConsumeZoom) {
                                        event.changes.forEach { 
                                            if (it.positionChanged()) {
                                                it.consume()
                                            }
                                        }
                                    }
                                    
                                    offset = clampedOffset
                                    onZoomChange(scale > 1.01f)
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                                onZoomChange(false)
                            } else {
                                scale = 3f
                                onZoomChange(true)
                            }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            content()
        }
    }
}

