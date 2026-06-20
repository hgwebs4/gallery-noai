package com.tama.gallerynoai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tama.gallerynoai.ui.viewmodel.GalleryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FastDateScroller(
    gridState: LazyGridState,
    items: List<GalleryItem>,
    modifier: Modifier = Modifier,
    dateFormat: String = "MMM yyyy",
    fullMediaList: List<com.tama.gallerynoai.data.model.MediaItem>? = null, // Optional raw list for perfect auto-scaling
    masterGridCount: Int = 0 // Parameter: Calculated lightly from ViewModel
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isScrolling by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf<Float?>(null) }
    var horizontalDragOffsetPx by remember { mutableFloatStateOf(0f) }

    // localProgress is used to drive the UI (handle/bubble) during drag,
    // decoupling it from the grid's loading state.
    var localDragProgress by remember { mutableFloatStateOf(0f) }

    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val showSlider by remember { derivedStateOf { isDraggingSlider || isScrolling } }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // To track month changes for haptic feedback
    var lastBubbleText by remember { mutableStateOf("") }

    // Use fallback count minimal 1 to prevent division by zero
    val safeMasterCount = remember(masterGridCount, items.size) {
        masterGridCount.coerceAtLeast(items.size).coerceAtLeast(1)
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        isScrolling = gridState.isScrollInProgress
        if (!gridState.isScrollInProgress) {
            delay(1500.milliseconds)
            isScrolling = false
        }
    }

    // Scroll progress derived from the grid state (used when not dragging)
    val gridProgress by remember(safeMasterCount) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalGridItems = layoutInfo.totalItemsCount
            if (totalGridItems == 0 || layoutInfo.visibleItemsInfo.isEmpty()) 0f else {
                val firstVisible = layoutInfo.visibleItemsInfo.first().index
                val lastVisible = layoutInfo.visibleItemsInfo.last().index
                val visibleCount = lastVisible - firstVisible + 1

                val maxScrollIndex = (safeMasterCount - visibleCount).coerceAtLeast(1)

                if (firstVisible >= maxScrollIndex) 1f
                else (firstVisible.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    // Sync local progress when not dragging
    LaunchedEffect(gridProgress, isDraggingSlider) {
        if (!isDraggingSlider) {
            localDragProgress = gridProgress
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat()

        val handleWidth = if (isDraggingSlider) 12.dp else 4.dp
        val handleHeight = 48.dp
        val handleHeightPx = with(density) { handleHeight.toPx() }

        val currentMaxHeightPx by rememberUpdatedState(maxHeightPx)
        val currentHandleHeightPx by rememberUpdatedState(handleHeightPx)

        // The visual Y offset is ALWAYS driven by localDragProgress when dragging
        // This is the "Zero-Lag" decoupling.
        val currentYOffsetPx = (localDragProgress * (maxHeightPx - handleHeightPx)).coerceIn(
            0f,
            (maxHeightPx - handleHeightPx).coerceAtLeast(0f),
        )

        // Capture latest YOffset value to avoid stale data when pointer is clicked again
        val currentYOffsetState by rememberUpdatedState(currentYOffsetPx)

        val animatedHandleWidth by animateDpAsState(
            targetValue = handleWidth,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "width",
        )

        val handleAlpha by animateFloatAsState(
            targetValue = if (showSlider) 1f else 0f,
            label = "handleAlpha",
        )

        // Invisible touch area - wide enough to grab easily
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .align(Alignment.TopEnd)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { _ ->
                            isDraggingSlider = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragOffsetPx = currentYOffsetState
                            horizontalDragOffsetPx = 0f
                        },
                        onDragEnd = {
                            isDraggingSlider = false
                            dragOffsetPx = null
                            horizontalDragOffsetPx = 0f
                        },
                        onDragCancel = {
                            isDraggingSlider = false
                            dragOffsetPx = null
                            horizontalDragOffsetPx = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()

                            horizontalDragOffsetPx = (48.dp.toPx() - change.position.x).coerceAtLeast(0f)
                            val precisionMultiplier = 1f / (1f + (horizontalDragOffsetPx / 150f))
                            val velocityBoost = if (abs(dragAmount) > 40f) 1.5f else 1.0f

                            val effectiveDrag = dragAmount * precisionMultiplier * velocityBoost

                            val newOffset = (dragOffsetPx!! + effectiveDrag)
                                .coerceIn(0f, (currentMaxHeightPx - currentHandleHeightPx).coerceAtLeast(0f))
                            dragOffsetPx = newOffset

                            // Update local progress INSTANTLY for fluid UI
                            localDragProgress = (newOffset / (currentMaxHeightPx - currentHandleHeightPx).coerceAtLeast(1f))
                                .coerceIn(0f, 1f)

                            // Logic: If we are at the absolute bottom pixel, FORCE last index.
                            val targetGridIndex = if (localDragProgress >= 0.999f) {
                                (safeMasterCount - 1).coerceAtLeast(0)
                            } else {
                                (localDragProgress * (safeMasterCount - 1))
                                    .toInt()
                                    .coerceIn(0, (safeMasterCount - 1).coerceAtLeast(0))
                            }

                            // Async grid sync - doesn't block localDragProgress updates
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                if (localDragProgress >= 0.999f) {
                                    gridState.scrollToItem(targetGridIndex, scrollOffset = 10000)
                                } else {
                                    gridState.scrollToItem(targetGridIndex)
                                }
                            }
                        },
                    )
                },
        )

        // The Scroll Handle (Thumb)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, currentYOffsetPx.roundToInt()) }
                .padding(end = 4.dp)
                .size(animatedHandleWidth, handleHeight)
                .graphicsLayer { alpha = handleAlpha }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            if (isDraggingSlider) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 1.dp)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
        }

        // Date Bubble
        AnimatedVisibility(
            visible = isDraggingSlider,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Bubble uses localDragProgress for Zero-Lag feedback
            val currentIndex = (localDragProgress * (safeMasterCount - 1))
                .toInt()
                .coerceIn(0, (safeMasterCount - 1).coerceAtLeast(0))

            // Use fullMediaList for perfect mapping if available
            val timestamp = remember(currentIndex, items, fullMediaList, safeMasterCount) {
                var foundTimestamp = 0L

                // Strategy 1: Check fullMediaList (Master Map)
                if (!fullMediaList.isNullOrEmpty()) {
                    val photoRatio = currentIndex.toFloat() / safeMasterCount.toFloat()
                    val photoIndex = (photoRatio * (fullMediaList.size - 1)).toInt().coerceIn(0, fullMediaList.size - 1)
                    foundTimestamp = fullMediaList[photoIndex].dateModified
                }

                // Strategy 2: Fallback to loaded items
                if (foundTimestamp == 0L) {
                    for (i in currentIndex downTo 0) {
                        val item = items.getOrNull(i)
                        if (item is GalleryItem.Header) {
                            foundTimestamp = item.timestamp
                            break
                        }
                    }
                    if (foundTimestamp == 0L) {
                        val currentItem = items.getOrNull(currentIndex)
                        if (currentItem is GalleryItem.Media) {
                            foundTimestamp = currentItem.item.dateModified
                        }
                    }
                }
                foundTimestamp
            }

            val bubbleText = remember(timestamp, dateFormat) {
                if (timestamp > 0) {
                    try {
                        DateTimeFormatter.ofPattern(dateFormat, Locale.getDefault())
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochSecond(timestamp))
                    } catch (_: Exception) {
                        try {
                            DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochSecond(timestamp))
                        } catch (_: Exception) {
                            ""
                        }
                    }
                } else ""
            }

            LaunchedEffect(bubbleText) {
                if (bubbleText != lastBubbleText && bubbleText.isNotEmpty()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastBubbleText = bubbleText
                }
            }

            if (bubbleText.isNotEmpty()) {
                val bubbleHeightPx = with(density) { 48.dp.toPx() }
                val paddingPx = with(density) { 16.dp.toPx() }
                val bubbleY = (currentYOffsetPx + (handleHeightPx / 2) - (bubbleHeightPx / 2))
                    .coerceIn(paddingPx, maxHeightPx - bubbleHeightPx - paddingPx)

                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (-handleWidth - 64.dp).toPx().toInt(),
                                y = bubbleY.roundToInt(),
                            )
                        }
                        .height(48.dp)
                        .widthIn(min = 100.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = bubbleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FastDateScrollerPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            FastDateScroller(
                gridState = rememberLazyGridState(),
                items = emptyList(),
                masterGridCount = 100
            )
        }
    }
}