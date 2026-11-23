package com.example.fridatestbench

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.graphics.Typeface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class GoogleWebViewActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val javascriptTextView = TextView(this).apply {
            text = RANDOM_JAVASCRIPT.trimIndent()
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val webView = WebView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                javascriptTextView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                webView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
        setContentView(content)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(RANDOM_JAVASCRIPT) { result ->
                    Log.d(LOG_TAG, "evaluateJavascript result: $result")
                }
            }
        }
        webView.loadUrl("https://www.google.com")
    }

    private companion object {
        const val LOG_TAG = "FridaTestBenchWebView"
        const val RANDOM_JAVASCRIPT = """
            (() => JSON.stringify({
                title: document.title,
                url: window.location.href,
                randomNumber: Math.floor(Math.random() * 1000),
                reversedText: "test bench".split("").reverse().join("")
            }))()
        """
    }
}
