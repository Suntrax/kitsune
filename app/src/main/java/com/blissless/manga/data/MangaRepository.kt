package com.blissless.manga.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class MangaSearchResult(
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val mangaId: String? = null
)

data class MangaDetail(
    val id: String,
    val title: String,
    val englishTitle: String?,
    val otherNames: List<String>,
    val synopsis: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val genres: List<String>,
    val status: String,
    val type: String,
    val avgRating: Double,
    val totalChapterCount: Int,
    val authors: List<String>
)

data class HomeSection(
    val key: String,
    val layout: String,
    val title: String,
    val items: List<MangaSearchResult>
)

data class ChapterInfo(
    val url: String,
    val title: String? = null
)

data class ChapterNavigation(
    val currentUrl: String,
    val nextChapterUrl: String?,
    val previousChapterUrl: String?
)

data class ChapterImages(
    val chapterUrl: String,
    val images: List<String>
)

class MangaRepository(private val context: Context) {

    private val baseUrl = "https://atsu.moe"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun log(tag: String, msg: String) {
        Log.d("MangaRepo", "[$tag] $msg")
    }

    suspend fun search(query: String): Result<List<MangaSearchResult>> = withContext(Dispatchers.Main) {
        val searchUrl = "$baseUrl/search?query=${query.replace(" ", "+")}"
        log("SEARCH", "URL: $searchUrl")

        suspendCancellableCoroutine { continuation ->
            val webView = createWebView()
            var completed = false

            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onResults(json: String) {
                    mainHandler.post {
                        if (completed) return@post
                        completed = true
                        try {
                            val results = parseMangaResults(json)
                            log("SEARCH", "Found ${results.size} manga, raw: ${json.take(200)}")
                            destroyWebView(webView)
                            continuation.resume(Result.success(results))
                        } catch (e: Exception) {
                            log("SEARCH", "Error: ${e.message}, raw: ${json.take(200)}")
                            destroyWebView(webView)
                            continuation.resume(Result.success(emptyList()))
                        }
                    }
                }
            }, "Android")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    mainHandler.postDelayed({
                        if (completed) return@postDelayed
                        val js = """
                            (function() {
                                var results = [];
                                var items = document.querySelectorAll('a[href^="/manga/"]');
                                items.forEach(function(item) {
                                    var href = item.getAttribute('href');
                                    var title = '';
                                    var cover = '';
                                    var img = item.querySelector('img');
                                    if (img) {
                                        title = img.alt || img.getAttribute('title') || item.textContent.trim();
                                        cover = img.getAttribute('src') || img.getAttribute('data-src') || '';
                                    }
                                    if (!title) title = item.textContent.trim();
                                    title = title.replace(/\\s+/g, ' ').trim().substring(0, 200);
                                    if (title && href) {
                                        results.push({ title: title, url: href, coverUrl: cover });
                                    }
                                });
                                Android.onResults(JSON.stringify(results));
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }, 2000)
                }
            }

            continuation.invokeOnCancellation {
                completed = true
                destroyWebView(webView)
            }

            webView.loadUrl(searchUrl)
        }
    }

    private fun parseMangaResults(json: String): List<MangaSearchResult> {
        if (json.isBlank() || json == "[]") return emptyList()
        
        val results = mutableListOf<MangaSearchResult>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "")
                val url = obj.optString("url", "")
                val cover = obj.optString("coverUrl", "")
                
                if (title.isNotBlank() && url.isNotBlank()) {
                    results.add(MangaSearchResult(
                        title = title,
                        url = baseUrl + url,
                        coverUrl = cover.takeIf { it.isNotBlank() }?.let { baseUrl + it }
                    ))
                }
            }
        } catch (e: Exception) {
            log("PARSE", "JSON parse error: ${e.message}")
        }
        return results
    }

    suspend fun getHomePage(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        val apiUrl = "$baseUrl/api/home/page"
        log("HOME", "API URL: $apiUrl")

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""
            response.close()

            log("HOME", "Response: ${json.take(500)}")

            val sections = mutableListOf<HomeSection>()
            val jsonObject = JSONObject(json)
            val homePage = jsonObject.getJSONObject("homePage")
            val sectionsArray = homePage.getJSONArray("sections")

            for (i in 0 until sectionsArray.length()) {
                val sectionObj = sectionsArray.getJSONObject(i)
                val key = sectionObj.optString("key", "")
                val layout = sectionObj.optString("layout", "")
                val title = sectionObj.optString("title", "")

                val items = mutableListOf<MangaSearchResult>()
                val itemsArray = sectionObj.optJSONArray("items")
                if (itemsArray != null) {
                    for (j in 0 until itemsArray.length()) {
                        val item = itemsArray.getJSONObject(j)
                        val itemId = item.optString("id", "")
                        val itemTitle = item.optString("title", "")

                        if (itemTitle.isNotBlank() && itemId.isNotBlank()) {
                            val imagePath = item.optString("largeImage") ?: 
                                           item.optString("mediumImage") ?: 
                                           item.optString("smallImage") ?: 
                                           item.optString("image", "")
                            val coverUrl = if (imagePath.isNotEmpty()) "$baseUrl/static/$imagePath" else null
                            log("HOME", "Image URL: $coverUrl")
                            
                            items.add(MangaSearchResult(
                                title = itemTitle,
                                url = "$baseUrl/manga/$itemId",
                                coverUrl = coverUrl,
                                mangaId = itemId
                            ))
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    sections.add(HomeSection(
                        key = key,
                        layout = layout,
                        title = title,
                        items = items
                    ))
                }
            }

            log("HOME", "Found ${sections.size} sections")
            Result.success(sections)
        } catch (e: Exception) {
            log("HOME", "Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getChapters(mangaUrl: String): Result<List<ChapterInfo>> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfter("/manga/").substringBefore("?")
        val apiUrl = "$baseUrl/api/manga/allChapters?mangaId=$mangaId"
        log("CHAPTERS", "API URL: $apiUrl")

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""
            response.close()

            log("CHAPTERS", "Response: ${json.take(200)}")

            val chapters = mutableListOf<ChapterInfo>()
            val jsonObject = JSONObject(json)
            val jsonArray = jsonObject.getJSONArray("chapters")
            for (i in 0 until jsonArray.length()) {
                val chapter = jsonArray.getJSONObject(i)
                val id = chapter.optString("id", "")
                val title = chapter.optString("title", "Chapter ${i + 1}")
                if (id.isNotBlank()) {
                    chapters.add(ChapterInfo(
                        url = "$baseUrl/read/$mangaId/$id",
                        title = title
                    ))
                }
            }

            // Reverse so Chapter 1 is at top
            val reversedChapters = chapters.reversed()
            
            log("CHAPTERS", "Found ${reversedChapters.size} chapters (reversed)")
            Result.success(reversedChapters)
        } catch (e: Exception) {
            log("CHAPTERS", "Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getMangaDetails(mangaUrl: String): Result<MangaDetail> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfter("/manga/").substringBefore("?")
        val apiUrl = "$baseUrl/api/manga/page?id=$mangaId"
        log("DETAILS", "API URL: $apiUrl")

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""
            response.close()

            log("DETAILS", "Response: ${json.take(500)}")

            val jsonObject = JSONObject(json)
            val mangaPage = jsonObject.getJSONObject("mangaPage")

            val id = mangaPage.optString("id", "")
            val title = mangaPage.optString("title", "")
            val englishTitleRaw = mangaPage.optString("englishTitle")
            val englishTitle = if (englishTitleRaw.isNotEmpty() && englishTitleRaw != "null") englishTitleRaw else null
            val synopsis = mangaPage.optString("synopsis", "")

            val otherNamesList = mutableListOf<String>()
            val otherNamesArray = mangaPage.optJSONArray("otherNames")
            if (otherNamesArray != null) {
                for (i in 0 until otherNamesArray.length()) {
                    otherNamesList.add(otherNamesArray.getString(i))
                }
            }

            val posterObj = mangaPage.optJSONObject("poster") ?: JSONObject()
            val posterUrl = when {
                posterObj.has("mediumImage") -> "$baseUrl/${posterObj.getString("mediumImage")}"
                posterObj.has("image") -> "$baseUrl/${posterObj.getString("image")}"
                posterObj.has("largeImage") -> "$baseUrl/${posterObj.getString("largeImage")}"
                else -> ""
            }
            
            val bannerUrl = if (mangaPage.has("banner") && !mangaPage.isNull("banner")) {
                val bannerStr = mangaPage.optString("banner", "")
                if (bannerStr.isNotEmpty() && bannerStr != "null") {
                    "$baseUrl/$bannerStr"
                } else null
            } else null
            
            log("DETAILS", "Poster URL: $posterUrl")
            log("DETAILS", "Banner URL: $bannerUrl")

            val genresList = mutableListOf<String>()
            val genresArray = mangaPage.optJSONArray("genres")
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) {
                    val genreObj = genresArray.getJSONObject(i)
                    genresList.add(genreObj.optString("name", ""))
                }
            }

            val status = mangaPage.optString("status", "")
            val type = mangaPage.optString("type", "")
            val avgRating = mangaPage.optDouble("avgRating", 0.0)
            val totalChapterCount = mangaPage.optInt("totalChapterCount", 0)

            val authorsList = mutableListOf<String>()
            val authorsArray = mangaPage.optJSONArray("authors")
            if (authorsArray != null) {
                for (i in 0 until authorsArray.length()) {
                    authorsList.add(authorsArray.getString(i))
                }
            }

            log("DETAILS", "Parsed: $title, $type, $status")
            Result.success(MangaDetail(
                id = id,
                title = title,
                englishTitle = englishTitle,
                otherNames = otherNamesList,
                synopsis = synopsis,
                coverUrl = posterUrl,
                bannerUrl = bannerUrl,
                genres = genresList,
                status = status,
                type = type,
                avgRating = avgRating,
                totalChapterCount = totalChapterCount,
                authors = authorsList
            ))
        } catch (e: Exception) {
            log("DETAILS", "Error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseChapterResults(json: String): List<ChapterInfo> {
        if (json.isBlank() || json == "[]") return emptyList()
        
        val chapters = mutableListOf<ChapterInfo>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url", "")
                val title = obj.optString("title", "")
                
                if (url.isNotBlank()) {
                    chapters.add(ChapterInfo(
                        url = baseUrl + url,
                        title = title.takeIf { it.isNotBlank() }
                    ))
                }
            }
        } catch (e: Exception) {
            log("PARSE", "Chapter JSON error: ${e.message}")
        }
        return chapters
    }

    suspend fun navigateChapter(chapterUrl: String): Result<ChapterNavigation> = withContext(Dispatchers.Main) {
        log("NAVIGATE", "Starting from: $chapterUrl")

        suspendCancellableCoroutine { continuation ->
            val webView = createWebView()
            var completed = false
            var step = 0
            var nextUrl: String? = null
            var prevUrl: String? = null

            fun complete(result: ChapterNavigation) {
                if (completed) return
                completed = true
                destroyWebView(webView)
                continuation.resume(Result.success(result))
            }

            fun getCurrentUrl(): String? {
                return try {
                    webView.url
                } catch (e: Exception) {
                    null
                }
            }

            fun runStep() {
                when (step) {
                    0 -> {
                        log("NAVIGATE", "Step 0: Activating UI")
                        activateUI(webView)
                        step = 1
                        mainHandler.postDelayed({ runStep() }, 2000)
                    }
                    1 -> {
                        log("NAVIGATE", "Step 1: Clicking next")
                        clickNextChapter(webView)
                        step = 2
                        mainHandler.postDelayed({ runStep() }, 4000)
                    }
                    2 -> {
                        nextUrl = getCurrentUrl()
                        log("NAVIGATE", "Step 2: Next URL = $nextUrl")
                        step = 3
                        mainHandler.postDelayed({ runStep() }, 1000)
                    }
                    3 -> {
                        log("NAVIGATE", "Step 3: Clicking prev")
                        clickPreviousChapter(webView)
                        step = 4
                        mainHandler.postDelayed({ runStep() }, 4000)
                    }
                    4 -> {
                        prevUrl = getCurrentUrl()
                        log("NAVIGATE", "Step 4: Prev URL = $prevUrl")
                        val result = ChapterNavigation(
                            currentUrl = chapterUrl,
                            nextChapterUrl = nextUrl?.takeIf { it != chapterUrl && it.contains("/read/") },
                            previousChapterUrl = prevUrl?.takeIf { it != chapterUrl && it.contains("/read/") }
                        )
                        log("NAVIGATE", "Complete: current=$chapterUrl, next=$nextUrl, prev=$prevUrl")
                        complete(result)
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (completed) return
                    if (step == 0) {
                        mainHandler.postDelayed({ runStep() }, 3000)
                    }
                }
            }

            continuation.invokeOnCancellation {
                completed = true
                destroyWebView(webView)
            }

            webView.loadUrl(chapterUrl)
        }
    }

    private fun activateUI(webView: WebView) {
        val js = """
            (function() {
                console.log('Activating UI...');
                var event = new MouseEvent('click', {
                    view: window,
                    bubbles: true,
                    cancelable: true
                });
                
                // Try clicking in the center to show reader controls
                var center = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                if (center) {
                    center.dispatchEvent(event);
                    console.log('Clicked center element');
                }
                
                // Also try clicking multiple spots
                var spots = [
                    [window.innerWidth / 2, window.innerHeight / 2],
                    [100, 300],
                    [300, 300],
                    [500, 300]
                ];
                spots.forEach(function(spot, i) {
                    setTimeout(function() {
                        var el = document.elementFromPoint(spot[0], spot[1]);
                        if (el) {
                            el.dispatchEvent(new MouseEvent('click', {
                                view: window,
                                bubbles: true,
                                cancelable: true
                            }));
                        }
                    }, i * 100);
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun clickNextChapter(webView: WebView) {
        val js = """
            (function() {
                var buttons = document.querySelectorAll('button');
                var leftButtons = [];
                buttons.forEach(function(btn) {
                    if (btn.offsetParent === null) return;
                    var svg = btn.querySelector('svg[data-icon="chevron-left"]');
                    if (svg) leftButtons.push(btn);
                });
                console.log('Left buttons found:', leftButtons.length);
                if (leftButtons.length >= 2) {
                    leftButtons[1].click();
                    console.log('Clicked second left button');
                } else if (leftButtons.length >= 1) {
                    leftButtons[0].click();
                    console.log('Clicked first left button');
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun clickPreviousChapter(webView: WebView) {
        val js = """
            (function() {
                var buttons = document.querySelectorAll('button');
                var rightButtons = [];
                buttons.forEach(function(btn) {
                    if (btn.offsetParent === null) return;
                    var svg = btn.querySelector('svg[data-icon="chevron-right"]');
                    if (svg) rightButtons.push(btn);
                });
                console.log('Right buttons found:', rightButtons.length);
                if (rightButtons.length >= 2) {
                    rightButtons[1].click();
                    console.log('Clicked second right button');
                } else if (rightButtons.length >= 1) {
                    rightButtons[0].click();
                    console.log('Clicked first right button');
                }
            })();
        """.trimIndent()
            webView.evaluateJavascript(js, null)
    }

    suspend fun getChapterImages(chapterUrl: String): Result<ChapterImages> = withContext(Dispatchers.Main) {
        log("IMAGES", "Fetching: $chapterUrl")

        suspendCancellableCoroutine { continuation ->
            val webView = createWebView()
            var completed = false
            var step = 0

            fun runImageExtraction(view: WebView?) {
                if (completed || view == null) return
                
                val js = """
                    (function() {
                        var images = [];
                        var seen = {};
                        
                        function getImgSrc(img) {
                            return img.currentSrc || img.src || 
                                   img.getAttribute('data-src') || 
                                   img.getAttribute('data-lazy') ||
                                   img.getAttribute('data-original') ||
                                   (img.srcset ? img.srcset.split(',')[0].trim().split(' ')[0] : '') || '';
                        }
                        
                        function isMangaImage(src) {
                            if (!src) return false;
                            var ext = src.split('?')[0].split('.').pop().toLowerCase();
                            return ext === 'webp' || ext === 'jpg' || ext === 'jpeg' || ext === 'png' || ext === 'gif';
                        }
                        
                        function isBannerOrAvatar(src) {
                            if (!src) return false;
                            var lower = src.toLowerCase();
                            return lower.includes('/icon/') || lower.includes('/logo') || 
                                   lower.includes('/banner') || lower.includes('/avatar/') || 
                                   lower.includes('/cover') || lower.includes('/thumbnail') ||
                                   lower.includes('avatar') || lower.includes('profile');
                        }
                        
                        function filenameIsNumber(src) {
                            if (!src) return false;
                            var filename = src.split('/').pop().split('?')[0];
                            var nameWithoutExt = filename.replace(/\.[^.]+$/, '');
                            return /^\d+$/.test(nameWithoutExt);
                        }
                        
                        function scanImages() {
                            var allImgs = document.querySelectorAll('img, [data-src], [data-lazy], [data-original], .page img, .chapter img, figure img, picture source');
                            allImgs.forEach(function(item) {
                                var src = '';
                                if (item.tagName === 'SOURCE') {
                                    src = item.srcset || item.getAttribute('srcset') || '';
                                    src = src.split(',')[0].trim().split(' ')[0];
                                } else {
                                    src = getImgSrc(item);
                                }
                                
                                if (!isMangaImage(src)) return;
                                if (isBannerOrAvatar(src)) return;
                                if (!filenameIsNumber(src)) return;
                                
                                var w = item.naturalWidth || item.getAttribute('width') || 0;
                                var h = item.naturalHeight || item.getAttribute('height') || 0;
                                if (w && h && w < 100 || h < 100) return;
                                
                                if (!src.startsWith('http')) {
                                    src = 'https://atsu.moe' + src;
                                }
                                
                                if (seen[src]) return;
                                seen[src] = true;
                                
                                images.push(src);
                            });
                        }
                        
                        scanImages();
                        console.log('Initial scan found: ' + images.length);
                        
                        Android.onImages(JSON.stringify(images));
                    })();
                """.trimIndent()
                view.evaluateJavascript(js, null)
            }

            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onImages(json: String) {
                    mainHandler.post {
                        if (completed) return@post
                        try {
                            val images = parseImageResults(json)
                            log("IMAGES", "Found ${images.size} images")
                            if (images.size > 0) {
                                completed = true
                                destroyWebView(webView)
                                continuation.resume(Result.success(ChapterImages(chapterUrl, images)))
                            } else if (step < 3) {
                                step++
                                log("IMAGES", "No images yet, step $step, scrolling...")
                                val scrollJs = """
                                    (function() {
                                        var totalH = document.body.scrollHeight;
                                        var chunk = totalH / 5;
                                        var current = (window.scrollY || 0) + chunk;
                                        if (current >= totalH) current = 0;
                                        window.scrollTo(0, current);
                                    })();
                                """.trimIndent()
                                webView.evaluateJavascript(scrollJs, null)
                                mainHandler.postDelayed({ runImageExtraction(webView) }, 1500)
                            } else {
                                completed = true
                                destroyWebView(webView)
                                continuation.resume(Result.success(ChapterImages(chapterUrl, images)))
                            }
                        } catch (e: Exception) {
                            log("IMAGES", "Error: ${e.message}")
                            completed = true
                            destroyWebView(webView)
                            continuation.resume(Result.success(ChapterImages(chapterUrl, emptyList())))
                        }
                    }
                }
            }, "Android")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (completed) return
                    mainHandler.postDelayed({
                        if (completed) return@postDelayed
                        activateUI(webView)
                        mainHandler.postDelayed({
                            if (completed) return@postDelayed
                            runImageExtraction(webView)
                        }, 1500)
                    }, 3000)
                }
            }

            continuation.invokeOnCancellation {
                completed = true
                destroyWebView(webView)
            }

            webView.loadUrl(chapterUrl)
        }
    }

    private fun parseImageResults(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        
        val images = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val src = array.getString(i)
                if (src.isNotBlank()) {
                    images.add(src)
                }
            }
        } catch (e: Exception) {
            log("PARSE", "Image JSON error: ${e.message}")
        }
        return images
    }

    private fun createWebView(): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }
    }

    private fun destroyWebView(webView: WebView) {
        try {
            mainHandler.post { webView.destroy() }
        } catch (_: Exception) {}
    }
}
