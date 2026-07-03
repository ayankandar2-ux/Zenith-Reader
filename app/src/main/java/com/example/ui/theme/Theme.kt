package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun getCustomColorScheme(
    accentColor: String,
    themeSetting: String,
    isDark: Boolean
): ColorScheme {
    val primaryColor = when (accentColor) {
        "Blue" -> Color(0xFF3B82F6)
        "Red" -> Color(0xFFEF4444)
        "Green" -> Color(0xFF10B981)
        "Purple" -> Color(0xFF8B5CF6)
        "Orange" -> Color(0xFFF59E0B)
        "Yellow" -> Color(0xFFEAB308)
        "Pink" -> Color(0xFFEC4899)
        "Cyan" -> Color(0xFF06B6D4)
        "Teal" -> Color(0xFF14B8A6)
        "Indigo" -> Color(0xFF6366F1)
        else -> Color(0xFF14B8A6) // Default Teal
    }
    
    val primaryContainer = primaryColor.copy(alpha = 0.2f)
    
    return when (themeSetting) {
        "light" -> lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.12f),
            onPrimaryContainer = primaryColor,
            secondary = Color(0xFF64748B),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F5F9),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF64748B)
        )
        "amoled" -> darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryColor,
            secondary = Color(0xFF94A3B8),
            onSecondary = Color.Black,
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF0B1320),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
        else -> { // "dark"
            darkColorScheme(
                primary = primaryColor,
                onPrimary = Color(0xFF022C22),
                primaryContainer = primaryContainer,
                onPrimaryContainer = primaryColor,
                secondary = Color(0xFF94A3B8),
                onSecondary = Color(0xFF0F172A),
                background = Color(0xFF0F172A),
                surface = Color(0xFF1E293B),
                surfaceVariant = Color(0xFF334155),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        }
    }
}

fun getShapesForStyle(style: String): Shapes {
    return when (style) {
        "square" -> Shapes(
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp)
        )
        "medium" -> Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp)
        )
        else -> Shapes( // "rounded"
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        )
    }
}

fun getTypographyForScale(scaleSetting: String): Typography {
    val scale = when (scaleSetting) {
        "small" -> 0.85f
        "large" -> 1.15f
        "extra_large" -> 1.3f
        else -> 1.0f
    }
    val defaultTypo = Typography
    return Typography(
        displayLarge = defaultTypo.displayLarge.copy(fontSize = defaultTypo.displayLarge.fontSize * scale),
        displayMedium = defaultTypo.displayMedium.copy(fontSize = defaultTypo.displayMedium.fontSize * scale),
        displaySmall = defaultTypo.displaySmall.copy(fontSize = defaultTypo.displaySmall.fontSize * scale),
        headlineLarge = defaultTypo.headlineLarge.copy(fontSize = defaultTypo.headlineLarge.fontSize * scale),
        headlineMedium = defaultTypo.headlineMedium.copy(fontSize = defaultTypo.headlineMedium.fontSize * scale),
        headlineSmall = defaultTypo.headlineSmall.copy(fontSize = defaultTypo.headlineSmall.fontSize * scale),
        titleLarge = defaultTypo.titleLarge.copy(fontSize = defaultTypo.titleLarge.fontSize * scale),
        titleMedium = defaultTypo.titleMedium.copy(fontSize = defaultTypo.titleMedium.fontSize * scale),
        titleSmall = defaultTypo.titleSmall.copy(fontSize = defaultTypo.titleSmall.fontSize * scale),
        bodyLarge = defaultTypo.bodyLarge.copy(fontSize = defaultTypo.bodyLarge.fontSize * scale),
        bodyMedium = defaultTypo.bodyMedium.copy(fontSize = defaultTypo.bodyMedium.fontSize * scale),
        bodySmall = defaultTypo.bodySmall.copy(fontSize = defaultTypo.bodySmall.fontSize * scale),
        labelLarge = defaultTypo.labelLarge.copy(fontSize = defaultTypo.labelLarge.fontSize * scale),
        labelMedium = defaultTypo.labelMedium.copy(fontSize = defaultTypo.labelMedium.fontSize * scale),
        labelSmall = defaultTypo.labelSmall.copy(fontSize = defaultTypo.labelSmall.fontSize * scale)
    )
}

@Composable
fun MyApplicationTheme(
    themeSetting: String = "dark",
    accentColor: String = "Teal",
    materialYou: Boolean = false,
    cornerStyle: String = "rounded",
    uiFontSize: String = "default",
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val resolvedTheme = if (themeSetting == "system") {
        if (darkTheme) "dark" else "light"
    } else themeSetting

    val colorScheme = if (materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (resolvedTheme == "light") dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
    } else {
        getCustomColorScheme(accentColor, resolvedTheme, resolvedTheme == "dark" || resolvedTheme == "amoled")
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = getShapesForStyle(cornerStyle),
        typography = getTypographyForScale(uiFontSize),
        content = content
    )
}
