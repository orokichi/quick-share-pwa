package com.orokichi.quickshare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingText: String? = null
    private var pendingFiles: List<Triple<java.io.File, String, String>> = emptyList()

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
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { deliverShareToPage() }
        }
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

    private fun copyIncoming(uri: Uri): Triple<java.io.File, String, String>? = try {
        val resolver = contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { c ->
            val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && ix >= 0) c.getString(ix) else "shared-file"
        } ?: "shared-file"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val file = java.io.File(cacheDir, "shared_${System.currentTimeMillis()}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        Triple(file, name, mime)
    } catch (_: Exception) { null }

    private fun deliverShareToPage() {
        if (pendingText.isNullOrBlank() && pendingFiles.isEmpty()) return
        try {
            val payload = JSONObject().put("text", pendingText ?: "").put("files", JSONArray().apply {
                pendingFiles.forEach { (file, name, mime) ->
                    put(JSONObject().put("name", name).put("type", mime).put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)))
                }
            })
            web.evaluateJavascript("window.receiveNativeShare && window.receiveNativeShare(${JSONObject.quote(payload.toString())})", null)
            pendingText = null
            pendingFiles = emptyList()
        } catch (_: Exception) {
            Toast.makeText(this, "共有データを読み込めませんでした", Toast.LENGTH_SHORT).show()
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
