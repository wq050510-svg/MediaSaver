package com.personal.mediasaver.ui.share

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.domain.model.AppearanceSettings
import com.personal.mediasaver.domain.sanitizer.SanitizedLink
import com.personal.mediasaver.domain.sanitizer.UrlSanitizer
import com.personal.mediasaver.ui.theme.MediaSaverTheme
import com.personal.mediasaver.ui.theme.resolveDarkTheme

class ShareReceiverActivity : ComponentActivity() {

    private var activeLink by mutableStateOf<SanitizedLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)
        if (activeLink == null) {
            finishWithoutAnimation()
            return
        }

        val app = application as MediaSaverApp

        setContent {
            val appearance by app.container.settingsDataStore.appearanceSettingsFlow
                .collectAsState(initial = AppearanceSettings())
            val isDark = resolveDarkTheme(appearance.themeMode)

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                )
            }

            MediaSaverTheme(
                darkTheme = isDark,
                dynamicColor = appearance.dynamicColor,
            ) {
                val link = activeLink
                if (link != null) {
                    ParsePreviewBottomSheet(
                        sanitizedLink = link,
                        isDarkTheme = isDark,
                        onDismiss = { finishWithoutAnimation() },
                        onEnqueued = { finishWithoutAnimation() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        if (activeLink == null) {
            finishWithoutAnimation()
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) {
            activeLink = null
            return
        }
        val sharedText = buildString {
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { append(it).append('\n') }
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { append(it) }
        }
        val sanitized = UrlSanitizer.extractAndSanitize(sharedText)
        if (sanitized == null) {
            Toast.makeText(this, "未在分享内容中识别到有效链接", Toast.LENGTH_SHORT).show()
            activeLink = null
        } else {
            activeLink = sanitized
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
