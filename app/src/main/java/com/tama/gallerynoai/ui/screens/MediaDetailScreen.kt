package com.tama.gallerynoai.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.model.MediaItem
import com.tama.gallerynoai.ui.components.ZoomableBox

@OptIn(ExperimentalMaterial3Api::class)
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
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Media not found", color = Color.White)
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // MI Gallery style pure dark background
    ) {
        // 1. BASE LAYER: Photo Viewer
        // Size Hamesha Screen par Fixed Rahega (Bina Kisi Jump Ya Shift Ke)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showBars = !showBars } // Tap karne par bars toggle hongi
                    )
                }
        ) {
            ZoomableBox(
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = mediaItem.uri,
                    contentDescription = mediaItem.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. TOP BAR (MI Style Solid Dark Bar)
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = mediaItem.displayName ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onInfoClick(mediaItem) }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }

        // 3. BOTTOM BAR (MI Style Action Items)
        AnimatedVisibility(
            visible = showBars,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // System Navigation Bar ke upar properly adjust hone ke liye
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Action
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mediaItem.mimeType ?: "image/*"
                            putExtra(Intent.EXTRA_STREAM, mediaItem.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }

                    // Delete Action
                    IconButton(onClick = { onDeleteClick(mediaItem) }) {
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
