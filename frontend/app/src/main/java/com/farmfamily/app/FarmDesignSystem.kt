package com.farmfamily.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FarmPalette(val forest: Color, val green: Color, val leaf: Color, val cream: Color, val white: Color, val ink: Color, val muted: Color, val line: Color, val sun: Color, val error: Color, val errorSurface: Color, val warning: Color)
private val LightFarmPalette = FarmPalette(Color(0xFF174A35), Color(0xFF2F6B4F), Color(0xFFE7F1EA), Color(0xFFF8F6EF), Color.White, Color(0xFF1F2924), Color(0xFF68736D), Color(0xFFDCE3DE), Color(0xFFE6BA63), Color(0xFF9E4031), Color(0xFFFCEDE8), Color(0xFFFFF3D6))
private val DarkFarmPalette = FarmPalette(Color(0xFFB2D7BE), Color(0xFF80B895), Color(0xFF263D31), Color(0xFF121A16), Color(0xFF1C2821), Color(0xFFE7EEE9), Color(0xFFB0BDB5), Color(0xFF3A4A40), Color(0xFFE6BA63), Color(0xFFFFB4A5), Color(0xFF4B2926), Color(0xFF45391F))
private val LocalFarmPalette = staticCompositionLocalOf { LightFarmPalette }
internal val FarmForest: Color @Composable get() = LocalFarmPalette.current.forest
internal val FarmGreen: Color @Composable get() = LocalFarmPalette.current.green
internal val FarmLeaf: Color @Composable get() = LocalFarmPalette.current.leaf
internal val FarmCream: Color @Composable get() = LocalFarmPalette.current.cream
internal val FarmWhite: Color @Composable get() = LocalFarmPalette.current.white
internal val FarmInk: Color @Composable get() = LocalFarmPalette.current.ink
internal val FarmMuted: Color @Composable get() = LocalFarmPalette.current.muted
internal val FarmLine: Color @Composable get() = LocalFarmPalette.current.line
internal val FarmSun: Color @Composable get() = LocalFarmPalette.current.sun
internal val FarmError: Color @Composable get() = LocalFarmPalette.current.error
internal val FarmErrorSurface: Color @Composable get() = LocalFarmPalette.current.errorSurface
internal val FarmWarning: Color @Composable get() = LocalFarmPalette.current.warning

private val FarmInter = FontFamily(Font(R.font.inter))
private val BaseTypography = Typography()
private val FarmTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FarmInter, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FarmInter, fontWeight = FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FarmInter, fontSize = 16.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FarmInter, fontSize = 14.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = FarmInter, fontSize = 12.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FarmInter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FarmInter, fontSize = 12.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FarmInter, fontSize = 11.sp)
)

@Composable
internal fun FarmDesignSystem(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (darkMode) DarkFarmPalette else LightFarmPalette
    CompositionLocalProvider(LocalFarmPalette provides palette) {
    val colors = if (darkMode) androidx.compose.material3.darkColorScheme(
        primary = FarmForest, onPrimary = Color(0xFF123222), primaryContainer = FarmLeaf, onPrimaryContainer = FarmForest,
        secondary = FarmGreen, onSecondary = Color(0xFF10251A), tertiary = FarmSun, background = FarmCream, onBackground = FarmInk,
        surface = FarmWhite, onSurface = FarmInk, surfaceVariant = FarmLeaf, onSurfaceVariant = FarmMuted, outline = FarmLine,
        error = FarmError, onError = Color(0xFF3B0904), errorContainer = FarmErrorSurface, onErrorContainer = FarmError
    ) else lightColorScheme(
        primary = FarmForest,
        onPrimary = FarmWhite,
        primaryContainer = FarmLeaf,
        onPrimaryContainer = FarmForest,
        secondary = FarmGreen,
        onSecondary = FarmWhite,
        tertiary = FarmSun,
        background = FarmCream,
        onBackground = FarmInk,
        surface = FarmWhite,
        onSurface = FarmInk,
        surfaceVariant = FarmLeaf,
        onSurfaceVariant = FarmMuted,
        outline = FarmLine,
        error = FarmError,
        onError = FarmWhite,
        errorContainer = FarmErrorSurface,
        onErrorContainer = FarmError
    )
    MaterialTheme(
        colorScheme = colors,
        typography = FarmTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp)
        ),
        content = content
    )
    }
}
