package com.example

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private var activeWebView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.ui.graphics.Color(0xFF0D1510)
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .windowInsetsPadding(WindowInsets.safeDrawing)
          ) {
            GingerDashGameView(
              modifier = Modifier.fillMaxSize(),
              onWebViewCreated = { activeWebView = it },
              onWebViewReleased = { if (activeWebView === it) activeWebView = null }
            )
          }
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    activeWebView?.apply {
      onPause()
      pauseTimers()
    }
  }

  override fun onResume() {
    super.onResume()
    activeWebView?.apply {
      onResume()
      resumeTimers()
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GingerDashGameView(
  modifier: Modifier = Modifier,
  onWebViewCreated: (WebView) -> Unit = {},
  onWebViewReleased: (WebView) -> Unit = {}
) {
  var reloadKey by remember { mutableIntStateOf(0) }
  var hasError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val crashTimestamps = remember { mutableListOf<Long>() }

  if (hasError) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(androidx.compose.ui.graphics.Color(0xFF0D1510))
        .padding(24.dp)
        .testTag("error_fallback_screen"),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = "⚠️ Something went wrong",
          color = androidx.compose.ui.graphics.Color(0xFFFBBF24),
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = errorMessage ?: "The game renderer encountered an issue.",
          color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
          fontSize = 14.sp,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = {
            hasError = false
            errorMessage = null
            crashTimestamps.clear()
            reloadKey++
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFFF59E0B),
            contentColor = androidx.compose.ui.graphics.Color(0xFF1E293B)
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("restart_game_button")
        ) {
          Text(
            text = "TAP TO RESTART",
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  } else {
    key(reloadKey) {
      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0D1510"))
            keepScreenOn = true

            // Set software layer type to avoid Mesa DRM rendernode GPU driver crashes
            // in container/emulator environments while keeping smooth 60fps rendering
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              useWideViewPort = true
              loadWithOverviewMode = true
              cacheMode = WebSettings.LOAD_DEFAULT
              mediaPlaybackRequiresUserGesture = false
              allowFileAccess = true
              allowContentAccess = true
            }

            webViewClient = object : WebViewClient() {
              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                Log.w("GingerDash", "WebView resource notice: ${error?.description}")
                if (request?.isForMainFrame == true) {
                  hasError = true
                  errorMessage = "Failed to load game assets: ${error?.description}"
                }
              }

              override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val didCrash = detail?.didCrash() ?: false
                Log.w("GingerDash", "Render process gone (didCrash=$didCrash). Recovering...")
                try {
                  view?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    it.destroy()
                  }
                } catch (t: Throwable) {
                  Log.e("GingerDash", "Error cleaning up destroyed webview", t)
                }

                val now = System.currentTimeMillis()
                crashTimestamps.removeAll { now - it > 10_000L }
                crashTimestamps.add(now)

                if (crashTimestamps.size >= 4) {
                  hasError = true
                  errorMessage = "Game process stopped unexpectedly. Tap to restart."
                } else {
                  // Transparent auto-recovery: recreate WebView smoothly
                  hasError = false
                  reloadKey++
                }
                return true
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val level = consoleMessage?.messageLevel()
                val msg = consoleMessage?.message() ?: ""
                val line = consoleMessage?.lineNumber() ?: 0
                val src = consoleMessage?.sourceId() ?: ""
                when (level) {
                  ConsoleMessage.MessageLevel.ERROR -> Log.e("GingerDashJS", "$msg -- line $line [$src]")
                  ConsoleMessage.MessageLevel.WARNING -> Log.w("GingerDashJS", "$msg -- line $line [$src]")
                  else -> Log.d("GingerDashJS", "$msg -- line $line [$src]")
                }
                return true
              }
            }

            loadUrl("file:///android_asset/index.html")
            onWebViewCreated(this)
          }
        },
        update = { webView ->
          onWebViewCreated(webView)
        },
        onRelease = { webView ->
          onWebViewReleased(webView)
          webView.stopLoading()
          webView.destroy()
        },
        modifier = modifier.testTag("game_webview")
      )
    }
  }
}

