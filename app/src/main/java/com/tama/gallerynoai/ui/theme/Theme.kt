package com.tama.gallerynoai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tama.gallerynoai.R
import com.tama.gallerynoai.data.settings.AppThemeColor

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val OceanBlueLightColorScheme = lightColorScheme(
    primary = OceanBlueLightPrimary,
    secondary = OceanBlueLightSecondary,
    tertiary = OceanBlueLightTertiary
)

private val OceanBlueDarkColorScheme = darkColorScheme(
    primary = OceanBlueDarkPrimary,
    secondary = OceanBlueDarkSecondary,
    tertiary = OceanBlueDarkTertiary
)

private val ForestGreenLightColorScheme = lightColorScheme(
    primary = ForestGreenLightPrimary,
    secondary = ForestGreenLightSecondary,
    tertiary = ForestGreenLightTertiary
)

private val ForestGreenDarkColorScheme = darkColorScheme(
    primary = ForestGreenDarkPrimary,
    secondary = ForestGreenDarkSecondary,
    tertiary = ForestGreenDarkTertiary
)

private val SunsetOrangeLightColorScheme = lightColorScheme(
    primary = SunsetOrangeLightPrimary,
    secondary = SunsetOrangeLightSecondary,
    tertiary = SunsetOrangeLightTertiary
)

private val SunsetOrangeDarkColorScheme = darkColorScheme(
    primary = SunsetOrangeDarkPrimary,
    secondary = SunsetOrangeDarkSecondary,
    tertiary = SunsetOrangeDarkTertiary
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: AppThemeColor = AppThemeColor.DEFAULT,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    val colorScheme = when (themeColor) {
        AppThemeColor.DYNAMIC_COLOR -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        AppThemeColor.OCEAN_BLUE -> {
            if (darkTheme) OceanBlueDarkColorScheme else OceanBlueLightColorScheme
        }
        AppThemeColor.FOREST_GREEN -> {
            if (darkTheme) ForestGreenDarkColorScheme else ForestGreenLightColorScheme
        }
        AppThemeColor.SUNSET_ORANGE -> {
            if (darkTheme) SunsetOrangeDarkColorScheme else SunsetOrangeLightColorScheme
        }
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    // Apply AMOLED mode if enabled and in dark theme
    val finalColorScheme = if (amoledMode && darkTheme) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0D0D0D),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        colorScheme
    }

    val typography = remember {
        getTypography(FontFamily.Default)
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = typography,
        content = content
    )
}
