package com.music.zenith.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGO_CSP =
    "<meta http-equiv=\"Content-Security-Policy\" " +
        "content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:\">"

/**
 * Renders a community-supplied HTML/CSS logo in a locked-down transparent
 * WebView: no JavaScript, no network, no file access, so it can animate with
 * CSS but cannot run code or reach anything.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlLogo(html: String, modifier: Modifier = Modifier) {
    val page = remember(html) {
        "<!doctype html><html><head><meta charset=\"utf-8\">$LOGO_CSP" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}</style>" +
            "</head><body>$html</body></html>"
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.blockNetworkLoads = true
                loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}