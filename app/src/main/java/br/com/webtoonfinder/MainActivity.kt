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
import java.net.URLEncoder
import java.util.concurrent.Executors

data class ToonResult(val title: String, val url: String, val chapters: Int, val detail: String)

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var info: TextView
    private lateinit var results: LinearLayout
    private lateinit var resultsScroll: ScrollView
    private lateinit var readerPanel: LinearLayout
    private var immersive = false
    private var mode = "Rolagem vertical"
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("webtoon_finder", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16,16,24)
        window.navigationBarColor = Color.rgb(16,16,24)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(16,16,24)) }
        root.addView(TextView(this).apply { text = "📚  WEBTOON FINDER"; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(18,18,18,10) })
        info = TextView(this).apply { text = "Pesquise pelo nome e compare capítulos em várias fontes"; textSize=13f; setTextColor(Color.LTGRAY); setPadding(18,0,18,10) }
        root.addView(info)
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10,4,10,8) }
        address = EditText(this).apply { hint = "Nome do webtoon"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setBackgroundColor(Color.rgb(39,39,52)); setPadding(12,4,12,4) }
        searchRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchRow.addView(button("PESQUISAR") { search(address.text.toString()) })
        root.addView(searchRow)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(6,0,6,8) }
        tools.addView(button("Resultados") { showResults() })
        tools.addView(button("Formato") { chooseMode() })
        tools.addView(button("Favoritos") { showSaved("favorites") })
        tools.addView(button("Histórico") { showSaved("history") })
        tools.addView(button("Tela cheia") { toggleImmersive() })
        root.addView(tools)
        resultsScroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,4,12,12) }
        resultsScroll.addView(results)
        results.addView(TextView(this).apply { text = "Digite o nome de um webtoon para encontrar versões e comparar a quantidade de capítulos."; setTextColor(Color.LTGRAY); textSize=15f; setPadding(8,20,8,20) })
        root.addView(resultsScroll, LinearLayout.LayoutParams(-1,0,1f))
        readerPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val readerBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        readerBar.addView(button("← Resultados") { showResults() })
        readerBar.addView(button("Salvar") { saveFavorite() })
        readerBar.addView(button("Voltar página") { if (web.canGoBack()) web.goBack() })
        readerPanel.addView(readerBar)
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
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    info.text = "Leitor interno • $mode • Zoom disponível"
                    applyMode()
                    if (!url.isNullOrBlank()) saveHistory(url)
                }
            }
            webChromeClient = WebChromeClient()
        }
        readerPanel.addView(web, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(readerPanel, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(75,55,130)); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2),0,dp(2),0) }
    }
    private fun search(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) { address.error = "Digite o nome do webtoon"; return }
        results.removeAllViews()
        results.addView(TextView(this).apply { text = "Pesquisando em várias fontes…"; setTextColor(Color.WHITE); textSize=16f; setPadding(8,20,8,20) })
        showResults()
        executor.execute {
            val found = linkedMapOf<String, ToonResult>()
            // Busca aproximada por nome, termos relacionados e fontes conhecidas.
            // Usa apenas resultados públicos; não contorna bloqueios ou paywalls.
            val queries = listOf(
                "\"$q\" webtoon português capítulos",
                "$q webtoon ler em português capítulos",
                "$q manhwa português episódios",
                "$q webtoon chapter episodes",
                "site:webtoons.com $q português",
                "site:tapas.io $q português"
            )
            for (term in queries) {
                try {
                    val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(term, "UTF-8")
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val resultBlocks = Regex("(?is)<div[^>]*class=[\\\"'][^\\\"']*result[^\\\"']*[\\\"'][^>]*>.*?</div>\\s*</div>").findAll(html)
                    for (blockMatch in resultBlocks) {
                        val block = blockMatch.value
                        val linkMatch = Regex("(?is)<a[^>]*class=[\\\"'][^\\\"']*result__a[^\\\"']*[\\\"'][^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>").find(block) ?: continue
                        val href = linkMatch.groupValues[1].replace("&amp;", "&")
                        val title = linkMatch.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                        val snippet = Regex("(?is)class=[\\\"'][^\\\"']*result__snippet[^\\\"']*[\\\"'][^>]*>(.*?)</").find(block)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
                        val all = "$title $snippet"
                        val count = Regex("(?i)(?:cap[ií]tulos?|chapters?|eps(?:is[oó]dios?)?)\\s*[:#-]?\\s*(\\d{1,4})").findAll(all).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
                        if (href.startsWith("http") && title.isNotBlank() && !href.contains("duckduckgo.com")) {
                            val key = href.substringBefore("#").trimEnd('/')
                            found[key] = ToonResult(title, href, count, snippet)
                        }
                    }
                } catch (_: Exception) { }
            }
            val ordered = found.values.sortedWith(compareByDescending<ToonResult> { it.chapters }.thenBy { it.title })
            runOnUiThread { showSearchResults(ordered, q) }
        }
    }
    private fun showSearchResults(items: List<ToonResult>, query: String) {
        results.removeAllViews()
        if (items.isEmpty()) {
            results.addView(TextView(this).apply { text = "Não encontrei resultados agora. Verifique a conexão ou tente outro nome."; setTextColor(Color.WHITE); textSize=15f; setPadding(8,20,8,20) })
            info.text = "Pesquisa concluída"
            return
        }
        results.addView(TextView(this).apply { text = "Resultados para “$query” • ordenados pela quantidade de capítulos identificada"; setTextColor(Color.WHITE); textSize=15f; setPadding(8,12,8,16) })
        items.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(Color.rgb(34,34,47)) }
            card.addView(TextView(this).apply { text = "${index+1}. ${item.title}"; setTextColor(Color.WHITE); textSize=16f; typeface=Typeface.DEFAULT_BOLD })
            card.addView(TextView(this).apply { text = if(item.chapters>0) "Até ${item.chapters} capítulos mencionados na página" else "Quantidade de capítulos não identificada"; setTextColor(Color.rgb(190,180,230)); textSize=13f; setPadding(0,6,0,4) })
            if (item.detail.isNotBlank()) card.addView(TextView(this).apply { text=item.detail; setTextColor(Color.LTGRAY); textSize=12f })
            card.addView(button("ABRIR NO LEITOR DO APP") { openReader(item.url) })
            results.addView(card, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,dp(10)) })
        }
        info.text = "${items.size} resultados encontrados • maior contagem primeiro"
    }
    private fun openReader(url: String) {
        resultsScroll.visibility = View.GONE
        readerPanel.visibility = View.VISIBLE
        address.setText(url)
        web.loadUrl(url)
    }
    private fun showResults() {
        readerPanel.visibility = View.GONE
        resultsScroll.visibility = View.VISIBLE
        info.text = "Resultados de pesquisa • escolha uma fonte para ler dentro do app"
    }
    private fun chooseMode() {
        val modes = arrayOf("Rolagem vertical", "Página por página", "Duas páginas lado a lado", "Leitura horizontal")
        AlertDialog.Builder(this).setTitle("Formato de leitura").setItems(modes) { _, which -> mode = modes[which]; info.text = "Formato: $mode"; applyMode() }.show()
    }
    private fun applyMode() {
        if (!::web.isInitialized) return
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
        AlertDialog.Builder(this).setTitle(if(key=="favorites") "Favoritos" else "Histórico").setItems(list.toTypedArray()) { _, which -> openReader(list[which]) }.show()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (immersive) { toggleImmersive(); return }
        if (readerPanel.visibility == View.VISIBLE) { if (web.canGoBack()) web.goBack() else showResults(); return }
        super.onBackPressed()
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
