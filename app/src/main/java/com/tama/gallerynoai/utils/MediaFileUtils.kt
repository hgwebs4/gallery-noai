package com.tama.gallerynoai.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

data class DetailedMetadata(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val shutterSpeed: String? = null,
    val focalLength: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val software: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
)

object MediaFileUtils {
    fun getDetailedMetadata(context: Context, uri: Uri, isVideo: Boolean): DetailedMetadata {
        return if (isVideo) {
            getVideoMetadata(context, uri)
        } else {
            getImageMetadata(context, uri)
        }
    }

    private fun getImageMetadata(context: Context, uri: Uri): DetailedMetadata {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = exif.latLong
                
                DetailedMetadata(
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                    shutterSpeed = formatShutterSpeed(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "${it}mm" },
                    flash = formatFlash(exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)),
                    whiteBalance = formatWhiteBalance(exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)),
                    software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
                    latitude = latLong?.get(0),
                    longitude = latLong?.get(1),
                )
            } ?: DetailedMetadata()
        } catch (_: Exception) {
            DetailedMetadata()
        }
    }

    private fun getVideoMetadata(context: Context, uri: Uri): DetailedMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val (lat, lon) = parseLocation(location)
            
            val model = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // METADATA_KEY_MODEL is 36
                retriever.extractMetadata(36)
            } else {
                null
            }

            DetailedMetadata(
                cameraModel = model,
                latitude = lat,
                longitude = lon,
            )
        } catch (_: Exception) {
            DetailedMetadata()
        } finally {
            retriever.release()
        }
    }

    private fun formatShutterSpeed(exposureTime: String?): String? {
        if (exposureTime == null) return null
        return try {
            val exposure = exposureTime.toDouble()
            if (exposure < 1.0) {
                val denominator = (1.0 / exposure).toInt()
                "1/${denominator}s"
            } else {
                "${exposure}s"
            }
        } catch (_: Exception) {
            exposureTime
        }
    }

    private fun formatFlash(flash: Int): String? {
        if (flash == -1) return null
        return if ((flash and 1) != 0) "Flash fired" else "No flash"
    }

    private fun formatWhiteBalance(whiteBalance: Int): String? {
        return when (whiteBalance.toShort()) {
            ExifInterface.WHITE_BALANCE_AUTO -> "Auto"
            ExifInterface.WHITE_BALANCE_MANUAL -> "Manual"
            else -> null
        }
    }

    private fun parseLocation(location: String?): Pair<Double?, Double?> {
        if (location == null) return null to null
        // Format is usually "+25.1234+121.1234/" or similar
        return try {
            val regex = """([+-]\d+\.\d+)([+-]\d+\.\d+)/""".toRegex()
            val match = regex.find(location)
            if (match != null) {
                match.groupValues[1].toDouble() to match.groupValues[2].toDouble()
            } else {
                null to null
            }
        } catch (_: Exception) {
            null to null
        }
    }
}

