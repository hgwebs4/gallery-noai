package com.tama.gallerynoai.ui.screens

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.ui.components.ZoomableBox

@Composable
fun MediaDetailScreen(
    mediaItem: MediaItem?,
    onBackClick: () -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
    onInfoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBars by remember { mutableStateOf(true) }
    val context = LocalContext.current

    if (mediaItem == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Center
        ) {
            Text(
                text = "Media not found",
                color = Color.White
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        /*
         * ---------------------------------------------------------
         * PHOTO VIEWER
         * ---------------------------------------------------------
         *
         * This layer always occupies the complete screen.
         * Top/bottom bars are drawn over it and therefore do not
         * change the image's available size when they appear/disappear.
         */
        ZoomableBox(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false
                        )

                        var moved = false
                        var multiTouch = false

                        while (true) {
                            val event = awaitPointerEvent(
                                pass = PointerEventPass.Final
                            )

                            val pointers = event.changes

                            if (pointers.count { it.pressed } > 1) {
                                multiTouch = true
                            }

                            if (event.changes.any { it.positionChanged() }) {
                                moved = true
                            }

                            if (pointers.none { it.pressed }) {
                                if (!moved && !multiTouch) {
                                    showBars = !showBars
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            AsyncImage(
                model = mediaItem.uri,
                contentDescription = mediaItem.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        /*
         * ---------------------------------------------------------
         * TOP BAR
         * ---------------------------------------------------------
         */
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(
                animationSpec = tween(200)
            ),
            exit = fadeOut(
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = mediaItem.displayName.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onInfoClick(mediaItem)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }

        /*
         * ---------------------------------------------------------
         * BOTTOM ACTION BAR
         * ---------------------------------------------------------
         */
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(
                animationSpec = tween(200)
            ),
            exit = fadeOut(
                animationSpec = tween(200)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                        .SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    /*
                     * SHARE
                     */
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(
                                Intent.ACTION_SEND
                            ).apply {
                                type = mediaItem.mimeType ?: "image/*"

                                putExtra(
                                    Intent.EXTRA_STREAM,
                                    mediaItem.uri
                                )

                                clipData = ClipData.newRawUri(
                                    "Media",
                                    mediaItem.uri
                                )

                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }

                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    "Share Media"
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    /*
                     * DELETE
                     */
                    IconButton(
                        onClick = {
                            onDeleteClick(mediaItem)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}