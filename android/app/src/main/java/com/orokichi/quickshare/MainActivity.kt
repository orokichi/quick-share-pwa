package com.orokichi.quickshare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.net.URLEncoder

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingText: String? = null
    private var pendingFiles: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "QuickShareNative")
        setContentView(web)
        handleShareIntent(intent)
        web.loadUrl("file:///android_asset/site/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        if (::web.isInitialized) web.post { deliverShareToPage() }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        pendingText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val uris = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> (intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: arrayListOf())
            else -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        pendingFiles = uris.mapNotNull { uri -> copyIncoming(uri) }
        if (pendingText == null && pendingFiles.isEmpty()) pendingText = intent.getStringExtra(Intent.EXTRA_SUBJECT)
    }

    private fun copyIncoming(uri: Uri): Pair<String, String>? = try {
        val resolver = contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { c ->
            val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && ix >= 0) c.getString(ix) else "共有ファイル"
        } ?: "共有ファイル"
        val file = java.io.File(cacheDir, "shared_${System.currentTimeMillis()}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file.absolutePath to name
    } catch (_: Exception) { null }

    private fun deliverShareToPage() {
        val text = pendingText ?: ""
        val names = pendingFiles.joinToString("、") { it.second }
        val msg = listOf(text, names).filter { it.isNotBlank() }.joinToString("\n")
        if (msg.isNotBlank()) {
            val escaped = org.json.JSONObject.quote(msg)
            web.evaluateJavascript("window.receiveNativeShare && window.receiveNativeShare($escaped)", null)
            pendingText = null
            pendingFiles = emptyList()
        }
    }

    inner class Bridge {
        @JavascriptInterface fun share(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                startActivity(Intent.createChooser(i, "共有先を選択"))
            }
        }
        @JavascriptInterface fun toast(text: String) { runOnUiThread { Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show() } }
    }
}
