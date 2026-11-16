package com.farmfamily.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val FarmForest = Color(0xFF174A35)
internal val FarmGreen = Color(0xFF2F6B4F)
internal val FarmLeaf = Color(0xFFE7F1EA)
internal val FarmCream = Color(0xFFF8F6EF)
internal val FarmWhite = Color(0xFFFFFFFF)
internal val FarmInk = Color(0xFF1F2924)
internal val FarmMuted = Color(0xFF68736D)
internal val FarmLine = Color(0xFFDCE3DE)
internal val FarmSun = Color(0xFFE6BA63)
internal val FarmError = Color(0xFF9E4031)
internal val FarmErrorSurface = Color(0xFFFCEDE8)
internal val FarmWarning = Color(0xFFFFF3D6)

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
internal fun FarmDesignSystem(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
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
