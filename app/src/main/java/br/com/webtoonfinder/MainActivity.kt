package br.com.webtoonfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var info: TextView
    private var immersive = false
    private var mode = "Rolagem vertical"
    private val prefs by lazy { getSharedPreferences("webtoon_finder", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16,16,24)
        window.navigationBarColor = Color.rgb(16,16,24)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(16,16,24)) }
        val header = TextView(this).apply { text = "📚  WEBTOON FINDER"; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(18,18,18,10) }
        root.addView(header)
        info = TextView(this).apply { text = "Encontre webtoons para ler em português"; textSize=13f; setTextColor(Color.LTGRAY); setPadding(18,0,18,10) }
        root.addView(info)
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10,4,10,8) }
        address = EditText(this).apply { hint = "Buscar título ou colar endereço"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setBackgroundColor(Color.rgb(39,39,52)); setPadding(12,4,12,4) }
        searchRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(button("BUSCAR") { search(address.text.toString()) })
        root.addView(searchRow)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(6,0,6,8) }
        tools.addView(button("Início") { web.loadUrl("https://www.google.com/search?q=webtoon+em+portugu%C3%AAs+ler+gr%C3%A1tis") })
        tools.addView(button("Formato") { chooseMode() })
        tools.addView(button("Favoritos") { showSaved("favorites") })
        tools.addView(button("Histórico") { showSaved("history") })
        tools.addView(button("Tela cheia") { toggleImmersive() })
        root.addView(tools)
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    address.setText(url ?: "")
                    info.text = "Leitor • $mode • Use o zoom para ajustar a página"
                    applyMode()
                    if (!url.isNullOrBlank() && !url.startsWith("https://www.google.com/search")) saveHistory(url)
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(web, LinearLayout.LayoutParams(-1,0,1f))
        val bottom = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(6,4,6,6) }
        bottom.addView(button("Voltar") { if (web.canGoBack()) web.goBack() })
        bottom.addView(button("Salvar página") { saveFavorite() })
        bottom.addView(button("Recarregar") { web.reload() })
        root.addView(bottom)
        setContentView(root)
        web.loadUrl("https://www.google.com/search?q=webtoon+em+portugu%C3%AAs+ler+gr%C3%A1tis")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(75,55,130)); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2),0,dp(2),0) }
    }
    private fun search(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) { address.error = "Digite um título"; return }
        val url = if (q.startsWith("https://") || q.startsWith("http://")) q else "https://www.google.com/search?q=" + java.net.URLEncoder.encode("$q webtoon português ler grátis", "UTF-8")
        web.loadUrl(url)
    }
    private fun chooseMode() {
        val modes = arrayOf("Rolagem vertical", "Página por página", "Duas páginas lado a lado", "Leitura horizontal")
        AlertDialog.Builder(this).setTitle("Formato de leitura").setItems(modes) { _, which -> mode = modes[which]; info.text = "Formato: $mode"; applyMode() }.show()
    }
    private fun applyMode() {
        val css = when(mode) {
            "Página por página" -> "img{max-width:100vw!important;max-height:96vh!important;object-fit:contain!important;display:block!important;margin:auto!important} body{overflow-x:hidden!important}"
            "Duas páginas lado a lado" -> "img{max-width:49vw!important;max-height:94vh!important;object-fit:contain!important;display:inline-block!important;vertical-align:middle!important} body{text-align:center!important}"
            "Leitura horizontal" -> "body{display:flex!important;flex-direction:row!important;overflow-x:auto!important;overflow-y:hidden!important;height:96vh!important} img{height:94vh!important;width:auto!important;max-width:none!important;object-fit:contain!important}"
            else -> "img{max-width:100%!important;height:auto!important} body{overflow-x:hidden!important}"
        }
        web.evaluateJavascript("(function(){var s=document.getElementById('wf-reader-style');if(!s){s=document.createElement('style');s.id='wf-reader-style';document.head.appendChild(s);}s.innerHTML=" + org.json.JSONObject.quote(css) + ";})();", null)
    }
    private fun toggleImmersive() {
        immersive = !immersive
        window.decorView.systemUiVisibility = if (immersive) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else View.SYSTEM_UI_FLAG_VISIBLE
    }
    private fun saveFavorite() {
        val url = web.url ?: return
        val list = prefs.getStringSet("favorites", emptySet())!!.toMutableSet(); list.add(url)
        prefs.edit().putStringSet("favorites", list).apply()
        Toast.makeText(this, "Página salva nos favoritos", Toast.LENGTH_SHORT).show()
    }
    private fun saveHistory(url: String) {
        val list = prefs.getStringSet("history", emptySet())!!.toMutableSet(); list.remove(url); list.add(url)
        prefs.edit().putStringSet("history", list.toList().takeLast(40).toSet()).apply()
    }
    private fun showSaved(key: String) {
        val list = prefs.getStringSet(key, emptySet())!!.toList().reversed()
        if (list.isEmpty()) { Toast.makeText(this, "Nada salvo ainda", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle(if(key=="favorites") "Favoritos" else "Histórico").setItems(list.toTypedArray()) { _, which -> web.loadUrl(list[which]) }.show()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (immersive) { toggleImmersive(); return }
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
