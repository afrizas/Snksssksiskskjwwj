package id.sch.smkn14kabupatentangerang.cbtbeta

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

/**
 * CBT SMKN 14 Beta
 * A deliberately simple WebView browser shell. There is no kiosk mode,
 * proctor PIN, screenshot blocking, forced task lock, or domain allowlist.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var homePanel: View
    private lateinit var addressInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView

    private var uploadCallback: ValueCallback<Array<Uri>>? = null

    private val studentUrl = "https://cbt.smkn14kabupatentangerang.sch.id/siswa/login.php"
    private val staffUrl = "https://cbt.smkn14kabupatentangerang.sch.id/admin/login.php"
    private val siteRoot = "https://cbt.smkn14kabupatentangerang.sch.id/"
    private val fileChooserRequest = 2407

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 33, 61)
        window.navigationBarColor = Color.rgb(16, 33, 61)
        window.decorView.systemUiVisibility = 0

        buildInterface()
        setupBrowser()
        showHome()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 246, 250))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Normal app header. The Android status and navigation bars remain visible.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundColor(Color.rgb(16, 33, 61))
        }
        val badge = TextView(this).apply {
            text = "14"
            textSize = 16f
            setTextColor(Color.rgb(16, 33, 61))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 12)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val brandColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandColumn.addView(TextView(this).apply {
            text = "CBT SMKN 14"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        brandColumn.addView(TextView(this).apply {
            text = "BROWSER BETA  •  v0.1.0"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(174, 194, 224))
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(badge)
        header.addView(brandColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        // Address/search bar.
        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(232, 237, 245))
        }
        addressInput = EditText(this).apply {
            textSize = 14f
            singleLine = true
            hint = "Masukkan alamat website"
            setTextColor(Color.rgb(25, 37, 55))
            setHintTextColor(Color.rgb(125, 139, 159))
            setPadding(dp(14), 0, dp(10), 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(Color.WHITE, 14)
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    openAddress()
                    true
                } else false
            }
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(44), 1f))
        val goButton = textButton("BUKA", Color.rgb(37, 99, 235), Color.WHITE).apply {
            setOnClickListener { openAddress() }
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(44)).apply {
                marginStart = dp(8)
            }
        }
        addressRow.addView(goButton)
        root.addView(addressRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(222, 229, 239))
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        val content = FrameLayout(this)
        homePanel = createHomePanel()
        webView = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
        }
        content.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        content.addView(homePanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
        }
        backButton = toolbarButton("‹", "Kembali") { if (webView.canGoBack()) webView.goBack() }
        forwardButton = toolbarButton("›", "Maju") { if (webView.canGoForward()) webView.goForward() }
        toolbar.addView(backButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(forwardButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(toolbarButton("↻", "Muat ulang") {
            if (webView.visibility == View.VISIBLE) webView.reload() else showHome()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(toolbarButton("⌂", "Beranda") { showHome() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))

        setContentView(root)
        updateNavigationButtons()
    }

    private fun createHomePanel(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(244, 246, 250))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        column.addView(TextView(this).apply {
            text = "Selamat datang"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 35, 57))
        })
        column.addView(TextView(this).apply {
            text = "Pilih portal CBT yang ingin dibuka. Aplikasi ini sedang dalam tahap beta."
            textSize = 14f
            setTextColor(Color.rgb(100, 116, 139))
            setPadding(0, dp(7), 0, dp(22))
        })

        column.addView(portalCard(
            icon = "S",
            title = "Portal Siswa",
            description = "Masuk untuk melihat jadwal dan mengerjakan ujian.",
            urlLabel = "cbt.smkn14kabupatentangerang.sch.id/siswa",
            onClick = { loadWebsite(studentUrl) }
        ))
        column.addView(portalCard(
            icon = "G",
            title = "Portal Guru / Proktor",
            description = "Masuk ke halaman pengelolaan dan pengawasan CBT.",
            urlLabel = "cbt.smkn14kabupatentangerang.sch.id/admin",
            onClick = { loadWebsite(staffUrl) }
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        val note = TextView(this).apply {
            text = "Alamat utama: $siteRoot\nKamu juga bisa mengetik alamat website lain pada kolom di atas."
            textSize = 12f
            setTextColor(Color.rgb(112, 126, 147))
            setPadding(dp(2), dp(22), dp(2), 0)
        }
        column.addView(note)
        scroll.addView(column)
        return scroll
    }

    private fun portalCard(
        icon: String,
        title: String,
        description: String,
        urlLabel: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = rounded(Color.WHITE, 18)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            elevation = dp(2).toFloat()
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(37, 99, 235), 14)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(4), 0)
        }
        details.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(27, 42, 65))
        })
        details.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.rgb(100, 116, 139))
            setPadding(0, dp(4), 0, dp(5))
        })
        details.addView(TextView(this).apply {
            text = urlLabel
            textSize = 10f
            setTextColor(Color.rgb(37, 99, 235))
        })
        card.addView(iconView)
        card.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(this).apply {
            text = "›"
            textSize = 28f
            setTextColor(Color.rgb(148, 163, 184))
        })
        return card
    }

    private fun setupBrowser() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)

        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleSpecialScheme(request.url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleSpecialScheme(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                homePanel.visibility = View.GONE
                webView.visibility = View.VISIBLE
                addressInput.setText(url)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 5
                updateNavigationButtons()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                addressInput.setText(url)
                progressBar.visibility = View.GONE
                updateNavigationButtons()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                updateNavigationButtons()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.uploadCallback?.onReceiveValue(null)
                this@MainActivity.uploadCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    startActivityForResult(intent, fileChooserRequest)
                    true
                } catch (_: ActivityNotFoundException) {
                    this@MainActivity.uploadCallback?.onReceiveValue(null)
                    this@MainActivity.uploadCallback = null
                    Toast.makeText(this@MainActivity, "Tidak ada aplikasi pemilih file.", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val uri = Uri.parse(url)
                if (uri.scheme != "https" && uri.scheme != "http") {
                    Toast.makeText(this, "Tautan unduhan tidak didukung.", Toast.LENGTH_SHORT).show()
                    return@DownloadListener
                }
                val request = DownloadManager.Request(uri)
                    .setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                    .setDescription("Mengunduh dari CBT SMKN 14")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                    )
                if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
                if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "Unduhan dimulai. Cek folder Downloads.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak dapat memulai unduhan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun handleSpecialScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun openAddress() {
        val raw = addressInput.text.toString().trim()
        if (raw.isEmpty()) return
        val target = when {
            raw.contains(" ") -> "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8")
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            else -> "https://$raw"
        }
        hideKeyboard()
        loadWebsite(target)
    }

    private fun loadWebsite(url: String) {
        homePanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
        addressInput.setText(url)
        webView.loadUrl(url)
    }

    private fun showHome() {
        webView.visibility = View.GONE
        homePanel.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        addressInput.setText("")
        addressInput.hint = "Masukkan alamat website"
        updateNavigationButtons()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(addressInput.windowToken, 0)
        addressInput.clearFocus()
    }

    private fun updateNavigationButtons() {
        if (!::backButton.isInitialized || !::forwardButton.isInitialized || !::webView.isInitialized) return
        val active = webView.visibility == View.VISIBLE
        backButton.alpha = if (active && webView.canGoBack()) 1f else 0.45f
        forwardButton.alpha = if (active && webView.canGoForward()) 1f else 0.45f
    }

    private fun toolbarButton(symbol: String, label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = "$symbol\n$label"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(65, 82, 107))
            isClickable = true
            isFocusable = true
            setOnClickListener { action(); updateNavigationButtons() }
        }
    }

    private fun textButton(label: String, backgroundColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = rounded(backgroundColor, 14)
            isClickable = true
            isFocusable = true
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android API; kept for compatibility with the simple beta shell")
    override fun onBackPressed() {
        when {
            webView.visibility == View.VISIBLE && webView.canGoBack() -> webView.goBack()
            webView.visibility == View.VISIBLE -> showHome()
            else -> super.onBackPressed()
        }
    }

    @Deprecated("Activity result callback for WebView file uploads")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequest) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadCallback?.onReceiveValue(result)
            uploadCallback = null
        }
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }
}
