package br.org.abrace.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun AbraceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors =
        when {
            Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(LocalContext.current)
            Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(LocalContext.current)
            dark ->
                darkColorScheme(
                    primary = Color(0xFFA0D0B7),
                    secondary = Color(0xFFBACCBF),
                    tertiary = Color(0xFFA8CCDA),
                )
            else ->
                lightColorScheme(
                    primary = Color(0xFF2F6850),
                    secondary = Color(0xFF516457),
                    tertiary = Color(0xFF366574),
                )
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Private photos are never cached on disk. */
@Composable
fun photoRequest(data: Any?): coil3.request.ImageRequest =
    coil3.request.ImageRequest.Builder(LocalContext.current)
        .data(data)
        .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
        .build()
